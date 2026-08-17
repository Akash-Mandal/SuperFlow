#!/usr/bin/env python3
"""Translate the aapt2 text symbol table (R.txt) into a Kotlin `R` object.

The Kotlin compiler cannot consume the Java `R.java` emitted by aapt2 in this
Gradle-less build, so resource identifiers are generated as Kotlin constants.
"""
import sys
from collections import defaultdict


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: gen_res.py <R.txt> <out.kt>", file=sys.stderr)
        return 2
    src, dst = sys.argv[1], sys.argv[2]

    types = defaultdict(list)
    try:
        with open(src, encoding="utf-8") as fh:
            for line in fh:
                parts = line.strip().split(" ", 3)
                if len(parts) < 4:
                    continue
                kind, rtype, name, value = parts[0], parts[1], parts[2], parts[3]
                if kind != "int":
                    continue  # styleable arrays are not needed
                types[rtype].append((name, value))
    except FileNotFoundError:
        pass

    out = ["// Generated from aapt2 R.txt. Do not edit.", "package com.superflow", "", "object R {"]
    for rtype in sorted(types):
        out.append("    object %s {" % rtype)
        for name, value in sorted(types[rtype]):
            out.append("        const val %s: Int = %s" % (name, value))
        out.append("    }")
    out.append("}")
    out.append("")

    with open(dst, "w", encoding="utf-8") as fh:
        fh.write("\n".join(out))
    total = sum(len(v) for v in types.values())
    print("    generated %d resource ids in %d types" % (total, len(types)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
