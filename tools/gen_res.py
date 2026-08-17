#!/usr/bin/env python3
"""Translate the aapt2 text symbol table (R.txt) into a Kotlin `R` object.

The Kotlin compiler cannot consume the Java `R.java` emitted by aapt2 in this
Gradle-less build, so resource identifiers are generated as Kotlin constants.
Styleable int[] arrays are emitted too, since Material components and custom
views reference them.
"""
import sys
from collections import defaultdict


def main() -> int:
    if len(sys.argv) < 3:
        print("usage: gen_res.py <R.txt> <out.kt> [package]", file=sys.stderr)
        return 2
    src, dst = sys.argv[1], sys.argv[2]
    pkg = sys.argv[3] if len(sys.argv) > 3 else "com.superflow"

    ints = defaultdict(list)
    arrays = defaultdict(list)

    try:
        with open(src, encoding="utf-8") as fh:
            for line in fh:
                line = line.rstrip("\n")
                parts = line.split(" ", 3)
                if len(parts) < 4:
                    continue
                kind, rtype, name, value = parts[0], parts[1], parts[2], parts[3]
                if kind == "int":
                    ints[rtype].append((name, value))
                elif kind == "int[]":
                    body = value.strip()
                    if body.startswith("{") and body.endswith("}"):
                        body = body[1:-1]
                    items = [v.strip() for v in body.split(",") if v.strip()]
                    arrays[rtype].append((name, items))
    except FileNotFoundError:
        pass

    out = [
        "// Generated from aapt2 R.txt. Do not edit.",
        "@file:Suppress(\"unused\", \"ObjectPropertyName\", \"MayBeConstant\")",
        "",
        "package %s" % pkg,
        "",
        "object R {",
    ]

    for rtype in sorted(set(list(ints.keys()) + list(arrays.keys()))):
        out.append("    object %s {" % rtype)
        for name, value in sorted(ints.get(rtype, [])):
            out.append("        const val %s: Int = %s" % (name, value))
        for name, items in sorted(arrays.get(rtype, [])):
            joined = ", ".join(items) if items else ""
            out.append("        val %s: IntArray = intArrayOf(%s)" % (name, joined))
        out.append("    }")

    out.append("}")
    out.append("")

    with open(dst, "w", encoding="utf-8") as fh:
        fh.write("\n".join(out))

    total = sum(len(v) for v in ints.values()) + sum(len(v) for v in arrays.values())
    print("    generated %d symbols across %d types" % (total, len(set(list(ints) + list(arrays)))))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
