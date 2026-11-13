#!/usr/bin/env python3
"""Reproducible Reader QA smoke and cold launch baseline on an Android emulator.

The benchmark numbers characterize only this emulator and an empty QA sandbox;
they cannot be used as claims about PDF, Markdown, or a physical user's device.
No production app data is touched. This captures evidence before UI refactoring.
"""
from __future__ import annotations
import argparse
import json
import os
from pathlib import Path
import re
import statistics
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PACKAGE = "com.m1racle.reader.qa"
ACTIVITY = "com.m1racle.reader.MainActivity"


def adb(*args: str, timeout: int = 75, check: bool = True) -> subprocess.CompletedProcess:
    output = subprocess.run(["adb", *args], capture_output=True, timeout=timeout, check=False)
    if check and output.returncode:
        raise RuntimeError(
            f"adb {args} exit {output.returncode}: " + output.stderr.decode("utf-8", "replace")[-600:]
        )
    return output


def launch_time(raw: str) -> int:
    m = re.search(r"^TotalTime:\s*(\d+)\s*$", raw, flags=re.M)
    if not m or int(m.group(1)) <= 0:
        raise AssertionError("am start -W did not report positive TotalTime")
    return int(m.group(1))


def inspect_hierarchy(xml: str) -> int:
    root = ET.fromstring(xml)
    matches = [
        node for node in root.iter("node")
        if node.get("package") == PACKAGE and node.get("bounds", "") != "[0,0][0,0]"
    ]
    if not matches:
        raise AssertionError("No visible Reader QA app nodes in actual UI hierarchy")
    return len(matches)


def hierarchy(evidence: Path, label: str) -> int:
    last_error = ""
    for _ in range(7):
        result = adb("shell", "uiautomator", "dump", "/sdcard/reader-qa-ui.xml", timeout=60, check=False)
        if result.returncode == 0:
            xml = adb("exec-out", "cat", "/sdcard/reader-qa-ui.xml", check=False).stdout.decode("utf-8", "replace")
            try:
                count = inspect_hierarchy(xml)
                (evidence / f"{label}.xml").write_text(xml, encoding="utf-8")
                return count
            except (AssertionError, ET.ParseError) as exc:
                last_error = str(exc)
        else:
            last_error = result.stderr.decode("utf-8", "replace")[-400:]
        time.sleep(2)
    raise AssertionError(f"Unable to validate QA screen hierarchy: {last_error}")


def screenshot(evidence: Path, label: str):
    result = adb("exec-out", "screencap", "-p", check=False)
    if result.returncode == 0 and result.stdout.startswith(b"\x89PNG"):
        (evidence / f"{label}.png").write_bytes(result.stdout)
    else:
        raise RuntimeError(f"Screenshot capture failed: {label}")


def run(evidence: Path, apk: Path):
    if not apk.is_file():
        raise FileNotFoundError(f"Missing QA APK: {apk}")
    adb("wait-for-device", timeout=120)
    adb("install", "-r", str(apk), timeout=180)
    # QA has its own applicationId; never clear, uninstall or modify production.
    adb("shell", "pm", "clear", PACKAGE)
    adb("logcat", "-c", check=False)
    timings = []
    for _ in range(3):
        adb("shell", "am", "force-stop", PACKAGE)
        response = adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/{ACTIVITY}", timeout=90)
        timings.append(launch_time(response.stdout.decode("utf-8", "replace")))
        time.sleep(2)
        pid = adb("shell", "pidof", "-s", PACKAGE, check=False).stdout.strip()
        if not pid:
            raise RuntimeError("Reader QA process exited after launch")
    light_nodes = hierarchy(evidence, "light")
    screenshot(evidence, "light")
    # Test theme changes on the disposable emulator only.
    change = adb("shell", "cmd", "uimode", "night", "yes", check=False)
    if change.returncode:
        raise RuntimeError("Emulator cannot switch to dark theme")
    time.sleep(3)
    dark_nodes = hierarchy(evidence, "dark")
    screenshot(evidence, "dark")
    adb("shell", "cmd", "uimode", "night", "no", check=False)
    (evidence / "meminfo.txt").write_bytes(adb("shell", "dumpsys", "meminfo", PACKAGE, check=False).stdout)
    report = {
        "source_commit": os.environ.get("GITHUB_SHA", "unknown"),
        "build_type": "QA (debuggable, not public APK)",
        "environment": "Android API 35 emulator; empty QA sandbox",
        "total_time_ms_three_force_stop_launches": timings,
        "median_total_time_ms": statistics.median(timings),
        "ui_nodes_light": light_nodes, "ui_nodes_dark": dark_nodes,
        "limitations": "Not a physical-device result; not a PDF/EPUB/Markdown scroll benchmark.",
    }
    (evidence / "baseline.json").write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"PASS Reader QA launch and light/dark UI. am start -W ms={timings}, median={statistics.median(timings)}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--evidence", type=Path, default=Path("output/reader-qa-baseline"))
    args = parser.parse_args()
    args.evidence.mkdir(parents=True, exist_ok=True)
    try:
        run(args.evidence, args.apk)
        return 0
    except (AssertionError, OSError, RuntimeError, subprocess.TimeoutExpired, ET.ParseError) as exc:
        print(f"FAIL Reader QA emulator baseline: {exc}", file=sys.stderr)
        return 1
    finally:
        adb("shell", "cmd", "uimode", "night", "no", check=False)
        logs = adb("logcat", "-d", "-t", "1000", check=False)
        (args.evidence / "logcat.txt").write_bytes(logs.stdout + b"\n" + logs.stderr)


if __name__ == "__main__":
    raise SystemExit(main())
