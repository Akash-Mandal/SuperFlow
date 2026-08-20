#!/usr/bin/env python3
"""
Static checks for the Compose sources.

Compose cannot be compiled in this environment - the AndroidX artifacts are
unreachable - so ui/theme and ui/components get no compiler feedback at all.
These are the mistakes that a compiler would have caught immediately and
that a human reviewer reliably misses:

  1. `val x by ...` without importing runtime.getValue. Extremely common,
     and the error message is unhelpful even when you do have a compiler.
  2. A member function whose name shadows an unaliased import it calls -
     infinite recursion. Hit for real in SfMotion (spring/tween).
  3. Unbalanced braces or parens.
  4. Unused imports, which usually mean a leftover from a refactor.
  5. `@Composable` functions named in lowerCamelCase that return Unit, or
     PascalCase functions that return a value - the Compose naming rule
     exists because tooling keys off it.
  6. Referencing an R resource that does not exist.

Not a substitute for compiling. It is the difference between catching six
classes of error and catching none.

Exit code 1 on any finding.
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DIRS = [
    ROOT / "app/src/main/kotlin/com/superflow/ui/theme",
    ROOT / "app/src/main/kotlin/com/superflow/ui/components",
    ROOT / "app/src/main/kotlin/com/superflow/ui/screens",
]
# Individual Compose files that live outside those directories, because
# they are bridges into the View world and belong beside their neighbours.
FILES = [
    ROOT / "app/src/main/kotlin/com/superflow/ui/common/ComposeHost.kt",
    ROOT / "app/src/main/kotlin/com/superflow/ui/onboarding/OnboardingActivity.kt",
]
RES = ROOT / "app/src/main/res"

problems = []


def strip_strings_and_comments(src):
    """Blank out string literals and comments so scans do not trip on prose."""
    out = []
    i = 0
    n = len(src)
    while i < n:
        two = src[i : i + 2]
        if two == "//":
            j = src.find("\n", i)
            j = n if j < 0 else j
            out.append(" " * (j - i))
            i = j
        elif two == "/*":
            j = src.find("*/", i + 2)
            j = n if j < 0 else j + 2
            out.append(" " * (j - i))
            i = j
        elif src[i : i + 3] == '"""':
            j = src.find('"""', i + 3)
            j = n if j < 0 else j + 3
            out.append(" " * (j - i))
            i = j
        elif src[i] == '"':
            j = i + 1
            while j < n and src[j] != '"':
                if src[j] == "\\":
                    j += 1
                j += 1
            j = min(j + 1, n)
            out.append(" " * (j - i))
            i = j
        else:
            out.append(src[i])
            i += 1
    return "".join(out)


def resource_names():
    """Every resource name aapt2 will generate, by type."""
    names = {}
    for path in RES.rglob("*.xml"):
        kind = path.parent.name.split("-")[0]
        if kind in ("layout", "drawable", "font", "menu", "xml", "raw", "anim", "animator"):
            names.setdefault(kind, set()).add(path.stem)
        elif kind == "values":
            text = path.read_text()
            for m in re.finditer(
                r'<(string|color|dimen|style|integer|bool|array|string-array|attr)\s+name="([^"]+)"',
                text,
            ):
                t = m.group(1)
                t = {"string-array": "array", "array": "array"}.get(t, t)
                names.setdefault(t, set()).add(m.group(2).replace(".", "_"))
    for path in RES.rglob("*"):
        if path.is_file() and path.suffix in (".png", ".webp", ".jpg", ".ttf", ".otf", ".ogg", ".mp3"):
            kind = path.parent.name.split("-")[0]
            names.setdefault(kind, set()).add(path.stem)
    return names


def check_file(path, res_names):
    raw = path.read_text()
    src = strip_strings_and_comments(raw)
    rel = path.relative_to(ROOT)

    # --- balance
    if src.count("{") != src.count("}"):
        problems.append(f"{rel}: unbalanced braces ({src.count('{')} open, {src.count('}')} close)")
    if src.count("(") != src.count(")"):
        problems.append(f"{rel}: unbalanced parens ({src.count('(')} open, {src.count(')')} close)")

    imports = {}
    for m in re.finditer(r"^import ([\w.]+?)(?: as (\w+))?$", src, re.M):
        alias = m.group(2) or m.group(1).split(".")[-1]
        imports[alias] = (m.group(1), m.group(0))

    body_start = 0
    for m in re.finditer(r"^import .*$", src, re.M):
        body_start = m.end()
    body = src[body_start:]

    # --- 1. `by` delegation needs getValue
    if re.search(r"\bval\s+\w+\s+by\s+", body) or re.search(r"\bvar\s+\w+\s+by\s+", body):
        has_get = "getValue" in imports or any(
            full.endswith(".getValue") for full, _ in imports.values()
        )
        if not has_get:
            problems.append(
                f"{rel}: uses `by` delegation but does not import androidx.compose.runtime.getValue"
            )
        if re.search(r"\bvar\s+\w+\s+by\s+", body):
            has_set = "setValue" in imports
            if not has_set:
                problems.append(
                    f"{rel}: uses `var ... by` but does not import "
                    f"androidx.compose.runtime.setValue"
                )

    # --- 2. member shadows an import it calls
    for m in re.finditer(r"fun\s+(?:<[^>]+>\s+)?(\w+)\s*\(", body):
        fn = m.group(1)
        if fn in imports:
            problems.append(
                f"{rel}: fun {fn}() shadows `{imports[fn][0]}` - "
                f"a call to {fn}(...) inside it will recurse. Alias the import."
            )

    # --- 4. unused imports
    #
    # getValue/setValue are used implicitly by `by` delegation and never
    # appear by name, so they are exempt when a delegation is present.
    uses_by = bool(re.search(r"\b(?:val|var)\s+\w+\s+by\s+", body))
    implicit = {"getValue", "setValue"} if uses_by else set()
    for alias, (full, line) in imports.items():
        if alias == "*" or alias in implicit:
            continue
        if not re.search(r"\b" + re.escape(alias) + r"\b", body):
            problems.append(f"{rel}: unused import `{full}`")

    # --- 5. composable naming
    for m in re.finditer(r"@Composable\s*(?:\n\s*)*(?:internal\s+|private\s+|public\s+)?fun\s+(\w+)\s*\(", src):
        name = m.group(1)
        tail = src[m.end() :]
        depth = 1
        k = 0
        while k < len(tail) and depth:
            if tail[k] == "(":
                depth += 1
            elif tail[k] == ")":
                depth -= 1
            k += 1
        after = tail[k : k + 80]
        returns_value = bool(re.match(r"\s*:\s*(?!Unit\b)", after))
        # Getters and Modifier factories legitimately return values in
        # lowerCamelCase; emitting composables must be PascalCase.
        if not returns_value and name[0].islower():
            problems.append(
                f"{rel}: @Composable fun {name}() emits UI but is lowerCamelCase; "
                f"Compose requires PascalCase"
            )
        if returns_value and name[0].isupper():
            problems.append(
                f"{rel}: @Composable fun {name}() returns a value but is PascalCase; "
                f"value-returning composables should be lowerCamelCase"
            )

    # --- 5b. common Compose symbols used without their import
    #
    # Missing imports are the single most likely error in a file nobody can
    # compile, and they are invisible to a brace-and-name checker. This is a
    # deliberately small allowlist of symbols that are easy to use by habit
    # and easy to forget to import.
    NEEDS_IMPORT = {
        "IntOffset": "androidx.compose.ui.unit.IntOffset",
        "IntSize": "androidx.compose.ui.unit.IntSize",
        "Offset": "androidx.compose.ui.geometry.Offset",
        "Size": "androidx.compose.ui.geometry.Size",
        "CircleShape": "androidx.compose.foundation.shape.CircleShape",
        "RoundedCornerShape": "androidx.compose.foundation.shape.RoundedCornerShape",
        "Stroke": "androidx.compose.ui.graphics.drawscope.Stroke",
        "StrokeCap": "androidx.compose.ui.graphics.StrokeCap",
        "Brush": "androidx.compose.ui.graphics.Brush",
        "Color": "androidx.compose.ui.graphics.Color",
        "Alignment": "androidx.compose.ui.Alignment",
        "Arrangement": "androidx.compose.foundation.layout.Arrangement",
        "Modifier": "androidx.compose.ui.Modifier",
    }
    imported_fqns = {full for full, _ in imports.values()}
    for symbol, fqn in NEEDS_IMPORT.items():
        if not re.search(r"\b" + symbol + r"\b", body):
            continue
        if fqn in imported_fqns:
            continue
        # A fully qualified use at the call site is fine.
        if re.search(re.escape(fqn), body):
            continue
        # Or it may be declared in this file.
        if re.search(r"\b(?:class|object|enum class|fun)\s+" + symbol + r"\b", body):
            continue
        problems.append(f"{rel}: uses `{symbol}` but does not import {fqn}")

    # Modifier extensions that must be imported to be callable in a chain.
    MODIFIER_EXT = {
        "offset": "androidx.compose.foundation.layout.offset",
        "padding": "androidx.compose.foundation.layout.padding",
        "size": "androidx.compose.foundation.layout.size",
        "fillMaxWidth": "androidx.compose.foundation.layout.fillMaxWidth",
        "fillMaxSize": "androidx.compose.foundation.layout.fillMaxSize",
        "fillMaxHeight": "androidx.compose.foundation.layout.fillMaxHeight",
        "height": "androidx.compose.foundation.layout.height",
        "width": "androidx.compose.foundation.layout.width",
        "clip": "androidx.compose.ui.draw.clip",
        "alpha": "androidx.compose.ui.draw.alpha",
        "background": "androidx.compose.foundation.background",
        "clickable": "androidx.compose.foundation.clickable",
        "semantics": "androidx.compose.ui.semantics.semantics",
        "pointerInput": "androidx.compose.ui.input.pointer.pointerInput",
        "defaultMinSize": "androidx.compose.foundation.layout.defaultMinSize",
        "clearAndSetSemantics": "androidx.compose.ui.semantics.clearAndSetSemantics",
    }
    for ext, fqn in MODIFIER_EXT.items():
        if not re.search(r"\.\s*" + ext + r"\s*[({]", body):
            continue
        if fqn in imported_fqns:
            continue
        if re.search(r"\bfun\s+Modifier\." + ext + r"\b", body):
            continue
        problems.append(f"{rel}: chains `.{ext}(...)` but does not import {fqn}")

    # --- 6. R references
    for m in re.finditer(r"\bR\.(\w+)\.(\w+)\b", src):
        kind, name = m.group(1), m.group(2)
        known = res_names.get(kind)
        if known is None:
            continue
        if name not in known:
            problems.append(f"{rel}: R.{kind}.{name} does not exist")


def main():
    res_names = resource_names()
    files = []
    for d in DIRS:
        if d.is_dir():
            files.extend(sorted(d.rglob("*.kt")))
    files.extend(f for f in FILES if f.is_file())
    if not files:
        print("no Compose sources found")
        return
    for f in files:
        check_file(f, res_names)

    if problems:
        print("==> COMPOSE STATIC CHECK FAILED\n")
        for p in problems:
            print("   ", p)
        print(f"\n{len(problems)} problem(s) in {len(files)} file(s)")
        sys.exit(1)

    print(f"==> COMPOSE STATIC CHECK OK: {len(files)} files, no findings")


if __name__ == "__main__":
    main()
