#!/usr/bin/env python3
"""Verify the shipped native ABI and exact offline artwork, not just build settings."""
import argparse
import hashlib
import json
import struct
import zipfile
from collections import defaultdict
from pathlib import Path


def verify(apk, welcome_dir):
    expected = {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in welcome_dir.glob("*.jpg")}
    if not expected:
        raise ValueError("No welcome source images found")
    machines = {"arm64-v8a": (2, 183), "armeabi-v7a": (1, 40)}
    sections = defaultdict(int)
    found, abis, libraries = {}, set(), 0
    with zipfile.ZipFile(apk) as archive:
        for entry in archive.infolist():
            name = entry.filename
            if name.startswith("lib/") and name.endswith(".so"):
                abi = name.split("/")[1]
                if abi not in machines:
                    raise ValueError(f"Unexpected shipped ABI: {name}")
                abis.add(abi)
                with archive.open(entry) as source:
                    header = source.read(64)
                if len(header) < 20 or header[:4] != b"\x7fELF" or header[5] not in (1, 2):
                    raise ValueError(f"Invalid ELF header: {name}")
                machine = struct.unpack(("<" if header[5] == 1 else ">") + "H", header[18:20])[0]
                if (header[4], machine) != machines[abi]:
                    raise ValueError(f"Native ABI mismatch: {name}, ELF class {header[4]}, machine {machine}")
                libraries += 1
                section = "native_engines"
            elif name.startswith("res/") and "ghajar_welcome_" in Path(name).name:
                basename = Path(name).name
                if basename not in expected or basename in found:
                    raise ValueError(f"Unknown or duplicated welcome image: {name}")
                digest = hashlib.sha256(archive.read(entry)).hexdigest()
                if digest != expected[basename]:
                    raise ValueError(f"Welcome bytes differ from reviewed JPEG: {name}")
                found[basename] = digest
                section = "welcome"
            elif name.startswith("classes") and name.endswith(".dex"):
                section = "dex"
            elif name.startswith("assets/"):
                section = "assets"
            elif name.startswith("res/"):
                section = "other_resources"
            else:
                section = "other"
            sections[section] += entry.compress_size
    if len(abis) != 1:
        raise ValueError(f"Expected one ABI per APK, got {sorted(abis)}")
    if found.keys() != expected.keys():
        raise ValueError(f"Welcome images missing: {sorted(expected.keys() - found.keys())}")
    return {"apk": apk.name, "apk_bytes": apk.stat().st_size,
            "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(), "abi": next(iter(abis)),
            "native_libraries": libraries, "welcome_count": len(found), "compressed_bytes": dict(sections)}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--welcome-dir", type=Path, default=Path(__file__).resolve().parent.parent / "branding/welcome")
    args = parser.parse_args()
    print(json.dumps(verify(args.apk, args.welcome_dir), sort_keys=True))
