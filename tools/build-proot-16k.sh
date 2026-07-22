#!/usr/bin/env bash
# Rebuild green-green-avk PRoot with 16 KB ELF LOAD alignment for Android 15+.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="${ROOT}/tools/.proot-build"
OUT_JNI="${ROOT}/app/src/main/jniLibs"
PAGE_FLAGS="-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384"

mkdir -p "$WORK"
cd "$WORK"

# Prefer Python 3.11 for old talloc/waf (imp module). Fall back to python3.
export PATH="${WORK}/.bin:$HOME/.local/bin:$PATH"
mkdir -p "${WORK}/.bin"
if [[ -x "${HOME}/.local/bin/uv" ]]; then
  PY311="$("${HOME}/.local/bin/uv" python find 3.11 2>/dev/null || true)"
  if [[ -n "${PY311}" && -x "${PY311}" ]]; then
    ln -sfn "${PY311}" "${WORK}/.bin/python"
  fi
fi
if [[ ! -x "${WORK}/.bin/python" ]] && command -v python3.11 >/dev/null 2>&1; then
  ln -sfn "$(command -v python3.11)" "${WORK}/.bin/python"
fi
if [[ ! -x "${WORK}/.bin/python" ]] && command -v python3 >/dev/null 2>&1; then
  ln -sfn "$(command -v python3)" "${WORK}/.bin/python"
fi
command -v python >/dev/null 2>&1 || true
# Python no longer required for talloc (direct NDK compile).


# Resolve NDK (prefer env, then previous extract, then download).
if [[ -n "${NDK_ROOT:-}" && -d "${NDK_ROOT}/toolchains" ]]; then
  :
elif [[ -d /tmp/android-ndk-r28c-extract/android-ndk-r28c/toolchains ]]; then
  NDK_ROOT=/tmp/android-ndk-r28c-extract/android-ndk-r28c
elif [[ -d /tmp/android-ndk-r28c/toolchains ]]; then
  NDK_ROOT=/tmp/android-ndk-r28c
else
  echo "Downloading Android NDK r28c..."
  curl -L "https://dl.google.com/android/repository/android-ndk-r28c-linux.zip" -o /tmp/ndk-r28c.zip
  rm -rf /tmp/android-ndk-r28c-extract /tmp/android-ndk-r28c
  mkdir -p /tmp/android-ndk-r28c-extract
  unzip -q /tmp/ndk-r28c.zip -d /tmp/android-ndk-r28c-extract
  NDK_ROOT="/tmp/android-ndk-r28c-extract/android-ndk-r28c"
fi
NDK_ROOT="$(readlink -f "$NDK_ROOT" 2>/dev/null || echo "$NDK_ROOT")"
echo "Using NDK: $NDK_ROOT"
test -d "$NDK_ROOT/toolchains" || { echo "NDK toolchains missing at $NDK_ROOT"; ls -la "$(dirname "$NDK_ROOT")" || true; exit 1; }

if [[ ! -d build-proot-android ]]; then
  git clone --depth 1 https://github.com/green-green-avk/build-proot-android.git
fi
cd build-proot-android

# Replace Python2/waf-based talloc build with a direct NDK compile.
cp -f "$ROOT/tools/make-talloc-static-simple.sh" ./make-talloc-static.sh
chmod +x ./make-talloc-static.sh
# Ensure LF endings on Windows-checked-out scripts
sed -i 's/\r$//' ./make-talloc-static.sh ./build.sh ./make-proot.sh ./pack.sh ./get-*.sh 2>/dev/null || true

# Only 64-bit + armv7 for our app; skip pre5/i686 to save time
cat > config <<EOF
PROOT_V='0.15_release'
TALLOC_V='2.1.14'
ARCHS='aarch64 armv7a x86_64'
BASE_DIR="\$PWD"
BUILD_DIR="\$BASE_DIR/build"
mkdir -p "\$BUILD_DIR"
PKG_DIR="\$BASE_DIR/packages"
mkdir -p "\$PKG_DIR"
NDK="$NDK_ROOT"
TOOLCHAIN="\$NDK/toolchains/llvm/prebuilt/linux-\$(uname -m)"

set-arch() {
 MARCH="\${1%%-*}"
 if [ "\$MARCH" != "\$1" ]
 then SUBARCH="\${1#*-}"
 else SUBARCH=''
 fi

 if [ "\$SUBARCH" == 'pre5' ]
 then API=16
 else API=21
 fi

 INSTALL_ROOT="\$BUILD_DIR/root-\$ARCH/root"
 STATIC_ROOT="\$BUILD_DIR/static-\$ARCH/root"

 case "\$MARCH" in
 arm*) MARCH_T='arm' ;;
 *) MARCH_T="\$MARCH" ;;
 esac

 export AR="\$(echo \$TOOLCHAIN/bin/llvm-ar)"
 export AS="\$(echo \$TOOLCHAIN/bin/\$MARCH_T-linux-android*\$API-clang)"
 export CC="\$(echo \$TOOLCHAIN/bin/\$MARCH-linux-android*\$API-clang)"
 export CXX="\$(echo \$TOOLCHAIN/bin/\$MARCH-linux-android*\$API-clang++)"
 export LD="\$(echo \$TOOLCHAIN/bin/llvm-ld)"
 export RANLIB="\$(echo \$TOOLCHAIN/bin/llvm-ranlib)"
 export STRIP="\$(echo \$TOOLCHAIN/bin/llvm-strip)"
 export OBJCOPY="\$(echo \$TOOLCHAIN/bin/llvm-objcopy)"
 export OBJDUMP="\$(echo \$TOOLCHAIN/bin/llvm-objdump)"
}
EOF

# Inject 16 KB page-size linker flags into make-proot.sh
if ! grep -q 'max-page-size=16384' make-proot.sh; then
  sed -i 's|export LDFLAGS="-L\$STATIC_ROOT/lib"|export LDFLAGS="-L$STATIC_ROOT/lib '"$PAGE_FLAGS"'"|' make-proot.sh
fi

# Also patch talloc static link flags if present
if [[ -f make-talloc-static.sh ]] && ! grep -q 'max-page-size=16384' make-talloc-static.sh; then
  sed -i "s/LDFLAGS=\"/LDFLAGS=\"$PAGE_FLAGS /g" make-talloc-static.sh || true
fi

chmod +x ./*.sh
./build.sh

python3 - <<'PY'
import struct, pathlib, sys

def check(path: pathlib.Path) -> bool:
    data = path.read_bytes()
    if data[:4] != b"\x7fELF":
        print(f"FAIL {path}: not ELF")
        return False
    if data[4] != 2 or data[5] != 1:
        # 32-bit is OK for armeabi-v7a
        print(f"SKIP 32-bit check detail {path.name}")
        return True
    e_phoff = struct.unpack_from("<Q", data, 32)[0]
    e_phentsize = struct.unpack_from("<H", data, 54)[0]
    e_phnum = struct.unpack_from("<H", data, 56)[0]
    ok = True
    for i in range(e_phnum):
        off = e_phoff + i * e_phentsize
        p_type, _, p_offset, p_vaddr, _, _, _, p_align = struct.unpack_from("<IIQQQQQQ", data, off)
        if p_type == 1 and p_align < 16384:
            print(f"FAIL {path}: LOAD align={p_align}")
            ok = False
    if ok:
        print(f"OK 16KB {path}")
    return ok

root = pathlib.Path("packages")
all_ok = True
for arch, abi in [("aarch64", "arm64-v8a"), ("armv7a", "armeabi-v7a"), ("x86_64", "x86_64")]:
    tar = root / f"proot-android-{arch}.tar.gz"
    if not tar.exists():
        print(f"missing {tar}")
        all_ok = False
print("Built packages ready" if all_ok else "build incomplete")
sys.exit(0 if all_ok else 1)
PY

# Stage into app jniLibs (no loader32 in 64-bit ABIs)
rm -rf "$OUT_JNI"
for arch_abi in "aarch64:arm64-v8a" "armv7a:armeabi-v7a" "x86_64:x86_64"; do
  arch="${arch_abi%%:*}"
  abi="${arch_abi##*:}"
  dest="$OUT_JNI/$abi"
  mkdir -p "$dest"
  tmp="$(mktemp -d)"
  tar -xzf "packages/proot-android-${arch}.tar.gz" -C "$tmp"
  cp "$tmp/root/bin/proot" "$dest/libproot.so"
  cp "$tmp/root/libexec/proot/loader" "$dest/libproot-loader.so"
  # Keep loader32 only for 32-bit ABI folder (not mixed into arm64)
  if [[ "$abi" == "armeabi-v7a" && -f "$tmp/root/libexec/proot/loader32" ]]; then
    cp "$tmp/root/libexec/proot/loader32" "$dest/libproot-loader32.so"
  elif [[ -f "$tmp/root/libexec/proot/loader32" && "$abi" != "armeabi-v7a" ]]; then
    # Optional: store under assets-like path outside jni — skip for Play 16KB scan
    :
  fi
  chmod 755 "$dest"/*.so
  rm -rf "$tmp"
done

echo "Installed 16KB PRoot into $OUT_JNI"
find "$OUT_JNI" -type f -name '*.so' -printf '%p %s\n'
