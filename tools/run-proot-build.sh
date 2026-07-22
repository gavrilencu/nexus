#!/usr/bin/env bash
set -euo pipefail
export PATH="$HOME/.local/bin:$PATH"
WORK=/mnt/c/Users/Developer/AndroidStudioProjects/toolkit/tools/.proot-build
ROOT=/mnt/c/Users/Developer/AndroidStudioProjects/toolkit
sed -i 's/\r$//' "$ROOT/tools/build-proot-16k.sh" "$ROOT/tools/make-talloc-static-simple.sh" "$ROOT/tools/run-proot-build.sh"
# Keep sources; only clear build products so we don't re-download forever
rm -rf "$WORK/build-proot-android/build/root-"* "$WORK/build-proot-android/build/static-"* \
  "$WORK/build-proot-android/build/talloc-"*/.talloc-obj-* 2>/dev/null || true
# Skip get-talloc/get-proot if already present
bash "$ROOT/tools/build-proot-16k.sh"
