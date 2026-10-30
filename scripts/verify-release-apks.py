#!/usr/bin/env python3
"""Inspect release identity, version, native ABI/page alignment and optional signer."""
from __future__ import annotations

import argparse
import os
from pathlib import Path
import re
import struct
import subprocess
import zipfile

ABI_CODES = {"armeabi-v7a": 2, "arm64-v8a": 3, "x86": 8, "x86_64": 9}
APPLICATION = "com.bernaferrari.bravedns"


def sdk_tool(name: str) -> Path:
    sdk = Path(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or "")
    candidates = list(sdk.glob(f"build-tools/*/{name}"))
    if not candidates:
        raise ValueError(f"Android SDK tool unavailable: {name}")
    return max(candidates, key=lambda p: tuple(int(part) for part in re.findall(r"\d+", p.parent.name)))


def load_alignments(data: bytes) -> list[int]:
    if data[:4] != b"\x7fELF":
        raise ValueError("Native library is not ELF")
    endian = "<" if data[5] == 1 else ">"
    if data[4] == 2:
        offset = struct.unpack_from(endian + "Q", data, 32)[0]
        size, count = struct.unpack_from(endian + "HH", data, 54)
        entry = endian + "IIQQQQQQ"
    elif data[4] == 1:
        offset = struct.unpack_from(endian + "I", data, 28)[0]
        size, count = struct.unpack_from(endian + "HH", data, 42)
        entry = endian + "IIIIIIII"
    else:
        raise ValueError("Unknown ELF class")
    return [struct.unpack_from(entry, data, offset + i * size)[-1] for i in range(count)
            if struct.unpack_from(endian + "I", data, offset + i * size)[0] == 1]


def check_manifest(badging: str, tree: str, expected_version: int) -> None:
    package = re.search(r"^package: name='([^']+)' versionCode='(\d+)'", badging, re.MULTILINE)
    if not package or package.group(1) != APPLICATION or int(package.group(2)) != expected_version:
        raise ValueError("Wrong application ID or encoded APK version code")
    if "application-debuggable" in badging or re.search(r"android:(?:debuggable|testOnly)[^\n]*0xffffffff", tree):
        raise ValueError("Release APK is debuggable or test-only")
    if "LauncherAliasAppLock" not in tree:
        raise ValueError("Launcher alias identity was lost")
    lines = tree.splitlines()
    found_vpn = False
    for index, line in enumerate(lines):
        service = re.match(r"^(\s*)E: service\b", line)
        if not service:
            continue
        indent = len(service.group(1))
        end = index + 1
        while end < len(lines):
            element = re.match(r"^(\s*)E:", lines[end])
            if element and len(element.group(1)) <= indent:
                break
            end += 1
        block = "\n".join(lines[index:end])
        if "BraveVPNService" in block:
            found_vpn = True
            if "android.permission.BIND_VPN_SERVICE" not in block:
                raise ValueError("VPN service lacks platform signature permission")
    if not found_vpn:
        raise ValueError("VPN service is missing")


def inspect(apk: Path, base_code: int, expected_cert: str | None = None) -> str:
    with zipfile.ZipFile(apk) as archive:
        native = [name for name in archive.namelist() if re.fullmatch(r"lib/[^/]+/[^/]+\.so", name)]
        abis = {name.split("/")[1] for name in native}
        if abis == ABI_CODES.keys():
            kind = "universal"
        elif len(abis) == 1 and abis <= ABI_CODES.keys():
            kind = next(iter(abis))
        else:
            raise ValueError(f"Invalid native ABI contents in {apk.name}: {sorted(abis)}")
        for abi in abis:
            if f"lib/{abi}/libgojni.so" not in native:
                raise ValueError(f"Missing Firestack bridge for {abi}")
        for name in native:
            if name.split("/")[1] in {"arm64-v8a", "x86_64"}:
                alignments = load_alignments(archive.read(name))
                if not alignments or min(alignments) < 16384:
                    raise ValueError(f"Native library lacks 16KB page support: {name}")
    aapt = sdk_tool("aapt2")
    badging = subprocess.check_output([str(aapt), "dump", "badging", str(apk)], text=True)
    tree = subprocess.check_output([str(aapt), "dump", "xmltree", str(apk), "--file", "AndroidManifest.xml"], text=True)
    multiplier = ABI_CODES["arm64-v8a"] if kind == "universal" else ABI_CODES[kind]
    check_manifest(badging, tree, multiplier * 10_000_000 + base_code)
    if expected_cert:
        cert = expected_cert.replace(":", "").lower()
        if not re.fullmatch(r"[a-f0-9]{64}", cert):
            raise ValueError("Expected signing certificate must be a SHA-256 digest")
        result = subprocess.check_output([str(sdk_tool("apksigner")), "verify", "--print-certs", str(apk)], text=True)
        signers = set(re.findall(r"Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]+)", result))
        if {value.lower() for value in signers} != {cert}:
            raise ValueError("APK signing certificate does not match the supplied certificate")
        subprocess.run([str(sdk_tool("zipalign")), "-c", "-P", "16", "4", str(apk)], check=True, capture_output=True)
    return kind


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--version-code", required=True, type=int)
    parser.add_argument("--expected-cert")
    arguments = parser.parse_args()
    if not 0 < arguments.version_code < 10_000_000:
        parser.error("version code must be between 1 and 9,999,999")
    apks = sorted(arguments.directory.glob("*.apk"))
    kinds = [inspect(apk, arguments.version_code, arguments.expected_cert) for apk in apks]
    if sorted(kinds) != sorted([*ABI_CODES, "universal"]):
        raise ValueError("Expected exactly four ABI APKs and one universal APK")
    print(f"Verified {len(apks)} release APKs: identity, versions, native ABIs, and 16KB compatibility")


if __name__ == "__main__":
    main()
