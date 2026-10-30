#!/usr/bin/env python3
"""Build and sign manual release artifacts without publishing or exposing key material."""
from __future__ import annotations
import base64
import hashlib
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SECRETS = {"RELEASE_KEYSTORE_BASE64", "RELEASE_STORE_PASSWORD", "RELEASE_KEY_PASSWORD", "RELEASE_KEY_ALIAS"}


def tool(name: str) -> Path:
    sdk = Path(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or "")
    options = list(sdk.glob(f"build-tools/*/{name}"))
    if not options:
        raise ValueError(f"Missing Android SDK tool: {name}")
    return max(options, key=lambda p: tuple(int(x) for x in re.findall(r"\d+", p.parent.name)))


def main() -> None:
    channel = os.environ.get("RELEASE_CHANNEL", "fdroid")
    version = os.environ.get("VERSION_CODE", "")
    certificate = os.environ.get("EXPECTED_CERT_SHA256", "").replace(":", "").lower()
    if channel not in {"fdroid", "play", "website"}:
        raise ValueError("Invalid release channel")
    if not re.fullmatch(r"[1-9][0-9]{0,6}", version):
        raise ValueError("VERSION_CODE must be between 1 and 9,999,999")
    if not re.fullmatch(r"[a-f0-9]{64}", certificate):
        raise ValueError("Expected certificate must be a SHA-256 digest")
    if any(not os.environ.get(name) for name in SECRETS):
        raise ValueError("All four release signing secrets must be provided")
    build_environment = {key: value for key, value in os.environ.items() if key not in SECRETS}
    with tempfile.TemporaryDirectory(prefix="keepout-signing-", dir=os.environ.get("RUNNER_TEMP")) as temporary:
        workspace = Path(temporary)
        keystore = workspace / "release.keystore"
        try:
            keystore.write_bytes(base64.b64decode("".join(os.environ["RELEASE_KEYSTORE_BASE64"].split()), validate=True))
        except ValueError:
            raise ValueError("Release keystore is not valid base64") from None
        keystore.chmod(0o600)
        certificate_result = subprocess.run([
            "keytool", "-exportcert", "-alias", os.environ["RELEASE_KEY_ALIAS"], "-keystore", str(keystore),
            "-storepass:env", "RELEASE_STORE_PASSWORD",
        ], capture_output=True)
        if certificate_result.returncode or hashlib.sha256(certificate_result.stdout).hexdigest() != certificate:
            raise ValueError("Keystore certificate does not match the expected signer")
        build_and_sign(channel, version, certificate, workspace, keystore, build_environment)
    print(f"Signed artifacts verified at app/build/outputs/signed-release/{channel}; nothing was published")


def build_and_sign(channel, version, certificate, workspace, keystore, build_environment):
    subprocess.run([
        str(ROOT / "gradlew"), f":app:assemble{channel.title()}Release", f"-PVERSION_CODE={version}",
        "-PkeepoutArtifactsOnly=true", "--no-daemon", "--console=plain",
    ], cwd=ROOT, env=build_environment, check=True)
    source = ROOT / f"app/build/outputs/apk/{channel}/release"
    output = ROOT / f"app/build/outputs/signed-release/{channel}"
    output.mkdir(parents=True, exist_ok=True)
    if list(output.glob("*.apk")):
        raise ValueError("Signed output directory already contains APKs; use a clean checkout")
    for apk in sorted(source.glob("*-unsigned.apk")):
        aligned = workspace / apk.name
        subprocess.run([str(tool("zipalign")), "-P", "16", "-f", "4", str(apk), str(aligned)], check=True)
        signed = output / apk.name.replace("-unsigned", "")
        result = subprocess.run([
            str(tool("apksigner")), "sign", "--ks", str(keystore), "--ks-key-alias", os.environ["RELEASE_KEY_ALIAS"],
            "--ks-pass", "env:RELEASE_STORE_PASSWORD", "--key-pass", "env:RELEASE_KEY_PASSWORD",
            "--out", str(signed), str(aligned),
        ], capture_output=True)
        if result.returncode:
            raise ValueError("APK signing failed; check the supplied signing credentials")
    subprocess.run([
        sys.executable, str(ROOT / "scripts/verify-release-apks.py"), str(output),
        "--version-code", version, "--expected-cert", certificate,
    ], env=build_environment, check=True)


if __name__ == "__main__":
    main()
