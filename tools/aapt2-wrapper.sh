#!/bin/sh
# Run SDK aapt2 under qemu-x86_64 on arm64 hosts.
AAPT2_BIN="${AAPT2_REAL:-/data/data/com.termux/files/home/Android/Sdk/build-tools/36.0.0/aapt2}"
if [ "$(uname -m)" = "aarch64" ] || [ "$(uname -m)" = "arm64" ]; then
  if command -v qemu-x86_64 >/dev/null 2>&1; then
    exec qemu-x86_64 "$AAPT2_BIN" "$@"
  fi
fi
exec "$AAPT2_BIN" "$@"
