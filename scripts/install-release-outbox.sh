#!/usr/bin/env bash
# Install M1racleReader release from .external_outbox when ADB is connected (USB or Wi‑Fi).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK="${1:-$ROOT/.external_outbox/m1racle-reader-release-latest.apk}"

if ! command -v adb >/dev/null 2>&1; then
  echo "adb not found in PATH"
  exit 1
fi

mapfile -t DEVS < <(adb devices | awk 'NR>1 && $2=="device" {print $1}')
if [ "${#DEVS[@]}" -eq 0 ]; then
  echo "No adb device online."
  echo "Wi‑Fi: on phone enable 无线调试, then: adb connect <ip>:<port>"
  echo "USB: plug in and allow debugging."
  exit 2
fi

DEV="${DEVS[0]}"
echo "Using device: $DEV"
if [ ! -f "$APK" ]; then
  echo "APK missing: $APK"
  echo "Build first: ./gradlew :app:assembleRelease"
  exit 3
fi

echo "Installing $APK ..."
adb -s "$DEV" install -r "$APK"
echo "Launching..."
adb -s "$DEV" shell am start -n com.m1racle.reader/.MainActivity || \
  adb -s "$DEV" shell monkey -p com.m1racle.reader -c android.intent.category.LAUNCHER 1
echo "Done."
