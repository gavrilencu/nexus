#!/usr/bin/env bash
# Build static libtalloc without waf (waf in talloc 2.1.14 is Python 2-only).
set -euo pipefail
shopt -s nullglob

. ./config

cd "$BUILD_DIR/talloc-$TALLOC_V"

DEF_CFLAGS="${CFLAGS:-}"

# Minimal replace.h — Android NDK already has the libc pieces Samba's replace layer
# would otherwise polyfill. Avoid including Samba's replace.h (needs waf config.h).
write_replace_h() {
  cat > "$1" <<'EOF'
#ifndef _LIBREPLACE_REPLACE_H
#define _LIBREPLACE_REPLACE_H
#ifndef _PUBLIC_
#define _PUBLIC_
#endif
#ifndef _PRIVATE_
#define _PRIVATE_ static
#endif
#ifndef MIN
#define MIN(a,b) (((a)<(b))?(a):(b))
#endif
#ifndef MAX
#define MAX(a,b) (((a)>(b))?(a):(b))
#endif
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <stddef.h>
#include <stdarg.h>
#include <stdbool.h>
#include <errno.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/mman.h>
#include <fcntl.h>
#include <time.h>
#include <signal.h>
#include <setjmp.h>
#include <limits.h>
#include <ctype.h>
#include <dirent.h>
#endif
EOF
}

for ARCH in $ARCHS
do
  set-arch "$ARCH"

  if [ "$SUBARCH" == 'pre5' ]; then
    export CFLAGS="$DEF_CFLAGS -D__ANDROID_API__=14 -D_FILE_OFFSET_BITS=64"
  else
    export CFLAGS="$DEF_CFLAGS -D_FILE_OFFSET_BITS=64"
  fi

  mkdir -p "$STATIC_ROOT/include" "$STATIC_ROOT/lib" ".talloc-obj-$ARCH"
  rm -f ".talloc-obj-$ARCH"/*.o
  write_replace_h ".talloc-obj-$ARCH/replace.h"

  "$CC" $CFLAGS \
    -I".talloc-obj-$ARCH" -I. \
    -DNO_CONFIG_H \
    -DTALLOC_BUILD_VERSION_MAJOR=2 \
    -DTALLOC_BUILD_VERSION_MINOR=1 \
    -DTALLOC_BUILD_VERSION_RELEASE=14 \
    -DHAVE_GETPAGESIZE=1 \
    -DHAVE_MMAP=1 \
    -DHAVE_MUNMAP=1 \
    -DHAVE_STRDUP=1 \
    -DHAVE_STRERROR=1 \
    -DHAVE_VASPRINTF=1 \
    -DHAVE_ASPRINTF=1 \
    -DHAVE_SNPRINTF=1 \
    -DHAVE_VSNPRINTF=1 \
    -DHAVE_C99_VSNPRINTF=1 \
    -DHAVE_SHARED_MMAP=1 \
    -DHAVE_MREMAP=1 \
    -DHAVE_SECURE_MKSTEMP=1 \
    -DHAVE_CONSTRUCTOR_ATTRIBUTE=1 \
    -DHAVE_VA_COPY=1 \
    -c talloc.c -o ".talloc-obj-$ARCH/talloc.o"

  "$AR" rcs "$STATIC_ROOT/lib/libtalloc.a" ".talloc-obj-$ARCH/talloc.o"
  cp -f talloc.h "$STATIC_ROOT/include/"
  echo "Built static libtalloc for $ARCH -> $STATIC_ROOT/lib/libtalloc.a"
done
