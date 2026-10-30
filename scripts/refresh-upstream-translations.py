#!/usr/bin/env python3
"""Copy translations only for unchanged English contracts. Dry-run unless --apply."""
from __future__ import annotations

import argparse
import collections
import copy
import re
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ENGLISH = "app/src/main/res/values/strings.xml"
KINDS = {"string", "plurals", "string-array"}
TOOLS = "{http://schemas.android.com/tools}"
FORMAT = re.compile(r"%(?:(\d+)\$)?[-#+ 0,(<]*(?:\d+)?(?:\.\d+)?([a-zA-Z%])")
BRAND = re.compile(r"rethink(?:dns)?|celzero|bravedns", re.IGNORECASE)
ENTRY = re.compile(r"<(string-array|plurals|string)(?=\s)[^>]*\bname=[\"']([^\"']+)[\"'][^>]*(?:/>|>.*?</\1\s*>)", re.DOTALL)
ET.register_namespace("xliff", "urn:oasis:names:tc:xliff:document:1.2")


def git_text(ref: str, path: str) -> str:
    return subprocess.check_output(["git", "show", f"{ref}:{path}"], cwd=ROOT, text=True)


def entries(text: str) -> dict[tuple[str, str], ET.Element]:
    root = ET.fromstring(text)
    result = {}
    for item in root:
        name = item.get("name")
        if item.tag in KINDS and name:
            key = (item.tag, name)
            if key in result:
                raise ValueError(f"Duplicate resource key: {key}")
            result[key] = item
    return result


def signature(item: ET.Element) -> tuple:
    attributes = tuple(sorted((k, v) for k, v in item.attrib.items() if k != "name" and not k.startswith(TOOLS)))
    text = item.text or ""
    if len(item) and not text.strip():
        text = ""
    children = tuple((signature(child), child.tail or "") for child in item)
    # Indentation around resource item children does not change the Android value.
    if item.tag in {"plurals", "string-array"}:
        children = tuple((child, "") for child, _ in children)
    return item.tag, attributes, text, children


def formats(item: ET.Element) -> collections.Counter:
    arguments = collections.Counter()
    implicit = 0
    for match in FORMAT.finditer("".join(item.itertext())):
        position, kind = match.groups()
        if kind == "%":
            continue
        if position is None:
            implicit += 1
        arguments[(int(position) if position else implicit, kind.lower())] += 1
    return arguments


def compatible(english: ET.Element, translated: ET.Element, known: set[str]) -> bool:
    if english.tag != translated.tag:
        return False
    original = "".join(english.itertext())
    text = "".join(translated.itertext())
    if BRAND.search(text) and not BRAND.search(original):
        return False
    if set(re.findall(r"@string/([\w.]+)", text)) - known:
        return False
    source_links = {node.get("href") for node in english.iter() if node.get("href")}
    if any(node.get("href") not in source_links for node in translated.iter() if node.get("href")):
        return False
    if english.tag == "plurals":
        items = {node.get("quantity"): node for node in english}
        return all(formats(node) == formats(items.get(node.get("quantity"), items["other"])) for node in translated)
    if english.tag == "string-array":
        return len(english) == len(translated) and all(formats(a) == formats(b) for a, b in zip(english, translated))
    return formats(english) == formats(translated)


def serialize(item: ET.Element) -> str:
    value = copy.deepcopy(item)
    value.tail = None
    return ET.tostring(value, encoding="unicode", short_empty_elements=True)


def replace_entries(current: str, replacements: dict[tuple[str, str], ET.Element]) -> str:
    remaining = dict(replacements)

    def replace(match: re.Match) -> str:
        key = (match.group(1), match.group(2))
        return serialize(remaining.pop(key)) if key in remaining else match.group(0)

    updated = ENTRY.sub(replace, current)
    additions = "".join(f"    {serialize(item)}\n" for item in remaining.values())
    return updated.replace("</resources>", additions + "</resources>")


def refresh(ref: str, apply: bool = False) -> dict[str, int]:
    current_english = entries((ROOT / ENGLISH).read_text())
    upstream_english = entries(git_text(ref, ENGLISH))
    eligible = {
        key for key, value in current_english.items()
        if key in upstream_english and value.get("translatable") != "false"
        and signature(value) == signature(upstream_english[key])
    }
    paths = subprocess.check_output(["git", "ls-tree", "-r", "--name-only", ref, "app/src/main/res"], cwd=ROOT, text=True).splitlines()
    stats = collections.Counter(eligible_english=len(eligible))
    for name in paths:
        if not re.fullmatch(r"app/src/main/res/values-[^/]+/strings\.xml", name):
            continue
        target = ROOT / name
        localized = entries(git_text(ref, name))
        existing = target.read_text() if target.exists() else '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n</resources>\n'
        before = entries(existing)
        replacements = {}
        for key in sorted(eligible & localized.keys()):
            value = localized[key]
            if not compatible(current_english[key], value, {name for kind, name in current_english if kind == "string"}):
                stats["rejected_contract"] += 1
                continue
            if key not in before or signature(value) != signature(before[key]):
                replacements[key] = value
        if not replacements:
            continue
        updated = replace_entries(existing, replacements)
        entries(updated)  # Reject malformed output before touching the file.
        stats["changed_files"] += 1
        stats["updated_values"] += len(replacements)
        stats["added_locales"] += not target.exists()
        if apply:
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(updated)
    return dict(stats)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ref", default="upstream/main")
    parser.add_argument("--apply", action="store_true")
    arguments = parser.parse_args()
    print(refresh(arguments.ref, arguments.apply))


if __name__ == "__main__":
    main()
