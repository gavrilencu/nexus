#!/usr/bin/env python3
import struct
import sys
from pathlib import Path

def check(path: Path) -> bool:
    data = path.read_bytes()
    if data[:4] != b"\x7fELF":
        print(f"{path}: not ELF")
        return False
    if data[4] != 2:  # 32-bit: 16KB rule applies to 64-bit ABIs
        print(f"{path.name}: 32-bit (no 16KB requirement)")
        return True
    e_phoff = struct.unpack_from("<Q", data, 32)[0]
    e_phentsize = struct.unpack_from("<H", data, 54)[0]
    e_phnum = struct.unpack_from("<H", data, 56)[0]
    ok = True
    for i in range(e_phnum):
        off = e_phoff + i * e_phentsize
        p_type, _, _, _, _, _, _, p_align = struct.unpack_from("<IIQQQQQQ", data, off)
        if p_type == 1:
            status = "OK" if p_align >= 16384 else "FAIL"
            if p_align < 16384:
                ok = False
            print(f"  LOAD align={p_align} ({status})")
    print(f"{path}: {'PASS' if ok else 'FAIL'}")
    return ok

root = Path(sys.argv[1])
all_ok = True
for p in sorted(root.rglob("*.so")):
    print(p.relative_to(root))
    all_ok &= check(p)
sys.exit(0 if all_ok else 1)
