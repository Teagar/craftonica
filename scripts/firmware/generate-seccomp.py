#!/usr/bin/env python3
"""Emit the x86_64 cBPF syscall denylist consumed by bubblewrap."""

import struct
import sys

AUDIT_ARCH_X86_64 = 0xC000003E
SECCOMP_RET_KILL_PROCESS = 0x80000000
SECCOMP_RET_ERRNO = 0x00050000
SECCOMP_RET_ALLOW = 0x7FFF0000

# Network, namespace/mount, kernel attack-surface and privileged syscalls.
DENIED = (
    41, 42, 43, 44, 45, 46, 47, 49, 50, 53, 54, 55,
    101, 155, 165, 166, 175, 176, 246, 248, 249, 250, 272,
    298, 304, 308, 313, 321, 322, 323, 425, 426, 427,
    428, 429, 430, 431, 432, 433, 435, 442,
)


def instruction(code: int, jt: int, jf: int, value: int) -> bytes:
    return struct.pack("=HBBI", code, jt, jf, value)


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: generate-seccomp.py OUTPUT", file=sys.stderr)
        return 2
    program = bytearray()
    program += instruction(0x20, 0, 0, 4)  # LD W ABS seccomp_data.arch
    program += instruction(0x15, 1, 0, AUDIT_ARCH_X86_64)  # JEQ
    program += instruction(0x06, 0, 0, SECCOMP_RET_KILL_PROCESS)
    program += instruction(0x20, 0, 0, 0)  # LD W ABS seccomp_data.nr
    for syscall in DENIED:
        program += instruction(0x15, 0, 1, syscall)
        program += instruction(0x06, 0, 0, SECCOMP_RET_ERRNO | 1)
    program += instruction(0x06, 0, 0, SECCOMP_RET_ALLOW)
    with open(sys.argv[1], "xb") as output:
        output.write(program)
    return 0


if __name__ == "__main__":
    sys.exit(main())
