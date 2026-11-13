#!/usr/bin/env python3
"""Fail-closed Reader release metadata and 16 KiB ELF checks (stdlib only).

The public APK must be signed by the historical production identity. Signing
continuity, APK signature, zip alignment, package identity, remote asset
read-back and the GitHub publish action are checked in the release workflow.
"""
from __future__ import annotations

import argparse
from pathlib import Path
import re
import struct
import sys
import zipfile

BASELINE_CODE = 97  # Last documented signed candidate, not a public GitHub Release.
BASELINE_VERSION = (1, 0, 0)
HEADINGS = ("## 新增", "## 修复", "## 验证", "## 已知限制")


def version_identity(raw: str) -> tuple[int, str]:
    names = re.findall(r"^\s*versionName\s*=\s*(\S+)\s*$", raw, flags=re.M)
    codes = re.findall(r"^\s*versionCode\s*=\s*(\S+)\s*$", raw, flags=re.M)
    if len(names) != 1 or len(codes) != 1:
        raise ValueError("versionName and versionCode must occur exactly once")
    name, code = names[0], codes[0]
    if not re.fullmatch(r"(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)", name):
        raise ValueError("versionName must be three numeric SemVer components")
    if not re.fullmatch(r"[1-9]\d*", code):
        raise ValueError("versionCode must be a positive integer")
    return int(code), name


def release_ready(root: Path) -> tuple[int, str]:
    code, name = version_identity((root / "app/version.properties").read_text(encoding="utf-8"))
    if code <= BASELINE_CODE or tuple(int(x) for x in name.split(".")) <= BASELINE_VERSION:
        raise ValueError(
            f"Refusing to replace the existing 1.0.0/code {BASELINE_CODE} signed candidate. "
            "Increment both versionCode and versionName for the next public release."
        )
    notes_path = root / "docs/releases" / f"v{name}.md"
    if not notes_path.is_file():
        raise ValueError(f"Missing authored Chinese release notes: {notes_path}")
    notes = notes_path.read_text(encoding="utf-8")
    for heading in HEADINGS:
        if not re.search(r"^" + re.escape(heading) + r"\s*$", notes, flags=re.M):
            raise ValueError(f"Release notes v{name} missing heading: {heading}")
    if len(notes.strip()) < 160:
        raise ValueError("Release notes are too short to meaningfully describe verification")
    return code, name


def _unpack(source: bytes, offset: int, fmt: str):
    return struct.unpack_from(fmt, source, offset)[0]


def verify_elf(source: bytes, filename: str) -> None:
    if len(source) < 64 or source[:4] != b"\x7fELF":
        raise ValueError(f"{filename}: not a complete ELF")
    klass, data = source[4], source[5]
    if klass not in (1, 2) or data not in (1, 2):
        raise ValueError(f"{filename}: unsupported ELF class/byte order")
    endian = "<" if data == 1 else ">"
    if klass == 2:
        offset = _unpack(source, 32, endian + "Q")
        entry_size = _unpack(source, 54, endian + "H")
        count = _unpack(source, 56, endian + "H")
        size, alignment_offset = 56, 48
    else:
        offset = _unpack(source, 28, endian + "I")
        entry_size = _unpack(source, 42, endian + "H")
        count = _unpack(source, 44, endian + "H")
        size, alignment_offset = 32, 28
    if count < 1 or entry_size < size or offset + count * entry_size > len(source):
        raise ValueError(f"{filename}: malformed ELF program headers")
    loads = 0
    for i in range(count):
        ph = offset + i * entry_size
        if _unpack(source, ph, endian + "I") != 1:  # PT_LOAD
            continue
        loads += 1
        align = _unpack(source, ph + alignment_offset, endian + ("Q" if klass == 2 else "I"))
        if align < 16384 or align & (align - 1):
            raise ValueError(f"{filename}: PT_LOAD alignment {align} is below 16 KiB")
    if not loads:
        raise ValueError(f"{filename}: ELF has no PT_LOAD segment")


def verify_apk_native_libraries(apk: Path) -> int:
    count = 0
    with zipfile.ZipFile(apk) as archive:
        for member in archive.infolist():
            if not re.fullmatch(r"lib/[^/]+/[^/]+\.so", member.filename):
                continue
            count += 1
            if member.compress_type != zipfile.ZIP_STORED:
                raise ValueError(f"{member.filename}: native library is compressed")
            verify_elf(archive.read(member), member.filename)
    if count == 0:
        raise ValueError("No native libraries found; cannot validate historical native-library baseline")
    return count


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("identity", "publish", "native-libs"))
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parent.parent)
    parser.add_argument("--apk", type=Path)
    args = parser.parse_args()
    try:
        if args.action == "identity":
            code, name = version_identity((args.root / "app/version.properties").read_text(encoding="utf-8"))
            print(f"Reader source version: {name} / {code}")
        elif args.action == "publish":
            code, name = release_ready(args.root)
            print(f"Reader publish metadata ready: {name} / {code}")
        else:
            if args.apk is None:
                parser.error("--apk is required for native-libs")
            print(f"Verified 16 KiB PT_LOAD alignment for {verify_apk_native_libraries(args.apk)} native libraries")
    except (OSError, ValueError, zipfile.BadZipFile, struct.error) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
