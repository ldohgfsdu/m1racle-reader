"""Verify public backup bytes and restore instructions without opening signing files."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import zipfile


def verify(root: Path) -> dict:
    manifest = (root / "MANIFEST.txt").read_text(encoding="utf-8")
    instructions = (root / "M1racleReader-备份还原说明.txt").read_text(encoding="utf-8")
    report = {}
    names = [
        "git/m1racle-reader-git-bundle-1.0.0-20261002.bundle",
        "source/m1racle-reader-source-1.0.0-20261002.zip",
        "apk/m1racle-reader-release-latest.apk",
        "M1racleReader-备份还原说明.txt",
    ]
    for name in names:
        file = root / name
        with file.open("rb") as stream:
            digest = hashlib.file_digest(stream, "sha256").hexdigest()
        size = file.stat().st_size
        match = re.search(re.escape(name) + r"\s+([\d,]+)\s+([a-f0-9]{64})", manifest)
        if not match or int(match[1].replace(",", "")) != size or match[2] != digest:
            raise ValueError(f"manifest mismatch: {name}")
        if file.suffix != ".txt":
            if Path(str(file) + ".sha256").read_text(encoding="utf-8").split()[0] != digest:
                raise ValueError(f"sidecar mismatch: {name}")
            if name not in instructions or digest not in instructions:
                raise ValueError(f"restore instructions mismatch: {name}")
        report[name] = {"bytes": size, "sha256": digest}
    bundle = root / names[0]
    refs = subprocess.check_output(["git", "bundle", "list-heads", str(bundle)], text=True)
    head = next(line.split()[0] for line in refs.splitlines() if line.endswith(" refs/heads/master"))
    if f"# 应显示 {head[:7]}" not in instructions:
        raise ValueError("restore HEAD mismatch")
    report["bundle_head"] = head
    for name in names[1:3]:
        with zipfile.ZipFile(root / name) as archive:
            if archive.testzip() is not None:
                raise ValueError(f"corrupt archive: {name}")
            report[name]["files"] = sum(not entry.is_dir() for entry in archive.infolist())
    count = report[names[1]]["files"]
    if f"{count} 个文件" not in instructions:
        raise ValueError("source file count mismatch")
    report["signing"] = "excluded; no private key or password read"
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("backup_root", type=Path)
    args = parser.parse_args()
    print(json.dumps(verify(args.backup_root.resolve()), ensure_ascii=False, indent=2))
