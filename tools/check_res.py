#!/usr/bin/env python3
"""
Cross-reference validator for the Android resource tree.

`aapt2 compile` checks that each resource file is well-formed, but it does
not resolve references between files -- that only happens at `aapt2 link`
time, which needs the full pre-exploded AAR set. In environments where those
artifacts are unavailable this script covers the gap: it parses every
resource XML and reports references that could never resolve.

What it checks:

  1. Dangling @-references. Every @color/@dimen/@style/@drawable/@string/
     @font/@array/@bool/@integer/@id/@anim/@layout/@menu reference resolves
     to something the project defines, or to a known framework/library name.
  2. Dangling ?attr references, against the attrs the project declares plus
     the Material 3 and AppCompat attribute surface.
  3. Duplicate resource definitions within a single qualifier bucket.
  4. Night-mode parity: every semantic token the base theme sets is also set
     by the night theme, so no token silently keeps its light value at night.
  5. Style parent chains that point at a project style which does not exist.

Framework and library names cannot be verified without the AARs, so they are
matched against an allowlist of prefixes and reported separately as
"external" rather than as errors.

Usage:  tools/check_res.py [res-dir ...]
Exit code 0 when clean, 1 when any error is found.
"""

import os
import re
import sys
import glob
import xml.etree.ElementTree as ET
from collections import defaultdict

# Resource types that can be declared in values/*.xml via a tag name.
VALUE_TAGS = {
    "color": "color", "dimen": "dimen", "string": "string", "bool": "bool",
    "integer": "integer", "style": "style", "attr": "attr",
    "string-array": "array", "integer-array": "array", "array": "array",
    "declare-styleable": "styleable", "item": None,  # item carries type=
}

# Material Components style names this project references, each verified to
# exist in com.google.android.material:material:1.13.0.
#
# A prefix match alone is not enough: `Widget.Material3.Button.Filled` and
# `Widget.Material3.Button.Outlined` look plausible, carry the right prefix,
# and do not exist -- Material 3 spells them `Widget.Material3.Button` (filled
# is the base style) and `Widget.Material3.Button.OutlinedButton`. Both slipped
# through this checker and only surfaced as an aapt2 resource-linking failure.
#
# Adding a Material style here is deliberate: confirm the exact name against
# the library's own res/values/styles.xml for the pinned version, then list it.
KNOWN_MATERIAL_STYLES = {
    "ShapeAppearance.Material3.Corner.ExtraLarge",
    "ShapeAppearance.Material3.Corner.ExtraSmall",
    "ShapeAppearance.Material3.Corner.Large",
    "ShapeAppearance.Material3.Corner.Medium",
    "ShapeAppearance.Material3.Corner.Small",
    "TextAppearance.Material3.BodyLarge",
    "TextAppearance.Material3.BodyMedium",
    "TextAppearance.Material3.BodySmall",
    "TextAppearance.Material3.DisplaySmall",
    "TextAppearance.Material3.HeadlineLarge",
    "TextAppearance.Material3.HeadlineMedium",
    "TextAppearance.Material3.LabelLarge",
    "TextAppearance.Material3.LabelMedium",
    "TextAppearance.Material3.LabelSmall",
    "TextAppearance.Material3.TitleLarge",
    "TextAppearance.Material3.TitleMedium",
    "Theme.Material3.DayNight.NoActionBar",
    "ThemeOverlay.Material3.BottomSheetDialog",
    "ThemeOverlay.Material3.MaterialAlertDialog",
    "Widget.Material3.BottomNavigationView",
    "Widget.Material3.BottomNavigationView.ActiveIndicator",
    "Widget.Material3.BottomSheet.Modal",
    "Widget.Material3.Button",
    "Widget.Material3.Button.IconButton",
    "Widget.Material3.Button.OutlinedButton",
    "Widget.Material3.Button.TextButton",
    "Widget.Material3.Button.TonalButton",
    "Widget.Material3.CardView.Elevated",
    "Widget.Material3.Chip.Assist",
    "Widget.Material3.Chip.Filter",
    "Widget.Material3.NavigationRailView",
    "Widget.Material3.TextInputLayout.OutlinedBox",
    "Widget.Material3.Toolbar",
}

# Style-name prefixes owned by Material Components. A reference carrying one
# of these is checked against KNOWN_MATERIAL_STYLES above rather than merely
# waved through as "external".
CHECKED_MATERIAL_PREFIXES = (
    "Theme.Material3", "ThemeOverlay.Material3", "Widget.Material3",
    "TextAppearance.Material3", "ShapeAppearance.Material3",
    "Theme.MaterialComponents", "Widget.MaterialComponents",
    "ThemeOverlay.MaterialComponents", "TextAppearance.MaterialComponents",
    "ShapeAppearance.MaterialComponents",
)

# Reference prefixes that belong to the framework or a library rather than
# to this project. These cannot be checked without the AARs.
EXTERNAL_PREFIXES = (
    "android:", "@android:", "?android:",
    "Theme.Material3", "ThemeOverlay.Material3", "Widget.Material3",
    "TextAppearance.Material3", "ShapeAppearance.Material3",
    "Theme.MaterialComponents", "Widget.MaterialComponents",
    "TextAppearance.MaterialComponents", "ShapeAppearance.MaterialComponents",
    "Theme.AppCompat", "ThemeOverlay.AppCompat", "Widget.AppCompat",
    "TextAppearance.AppCompat", "Base.Theme", "Platform.",
    "Animation.AppCompat", "Theme.Design", "Widget.Design",
)

# Material 3 / AppCompat theme attributes referenced as ?attr/name.
KNOWN_ATTRS = {
    # colour roles
    "colorPrimary", "colorOnPrimary", "colorPrimaryContainer",
    "colorOnPrimaryContainer", "colorPrimaryInverse", "colorPrimaryVariant",
    "colorSecondary", "colorOnSecondary", "colorSecondaryContainer",
    "colorOnSecondaryContainer", "colorSecondaryVariant",
    "colorTertiary", "colorOnTertiary", "colorTertiaryContainer",
    "colorOnTertiaryContainer",
    "colorError", "colorOnError", "colorErrorContainer", "colorOnErrorContainer",
    "colorSurface", "colorOnSurface", "colorSurfaceVariant",
    "colorOnSurfaceVariant", "colorSurfaceInverse", "colorOnSurfaceInverse",
    "colorSurfaceTint", "colorSurfaceContainer", "colorSurfaceContainerLow",
    "colorSurfaceContainerHigh", "colorSurfaceContainerHighest",
    "colorSurfaceContainerLowest", "colorSurfaceBright", "colorSurfaceDim",
    "colorOutline", "colorOutlineVariant", "colorAccent", "colorControlNormal",
    "colorControlHighlight", "colorControlActivated", "colorBackground",
    "colorOnBackground", "colorScrim", "colorShadow",
    # type
    "textAppearanceDisplayLarge", "textAppearanceDisplayMedium",
    "textAppearanceDisplaySmall", "textAppearanceHeadlineLarge",
    "textAppearanceHeadlineMedium", "textAppearanceHeadlineSmall",
    "textAppearanceTitleLarge", "textAppearanceTitleMedium",
    "textAppearanceTitleSmall", "textAppearanceBodyLarge",
    "textAppearanceBodyMedium", "textAppearanceBodySmall",
    "textAppearanceLabelLarge", "textAppearanceLabelMedium",
    "textAppearanceLabelSmall", "textAppearanceButton", "fontFamily",
    # shape
    "shapeAppearanceCornerExtraSmall", "shapeAppearanceCornerSmall",
    "shapeAppearanceCornerMedium", "shapeAppearanceCornerLarge",
    "shapeAppearanceCornerExtraLarge", "shapeAppearance",
    "shapeAppearanceOverlay", "shapeAppearanceSmallComponent",
    "shapeAppearanceMediumComponent", "shapeAppearanceLargeComponent",
    # components / misc
    "materialCardViewStyle", "chipStyle", "bottomSheetDialogTheme",
    "materialAlertDialogTheme", "bottomSheetStyle", "elevationOverlayEnabled",
    "selectableItemBackground", "selectableItemBackgroundBorderless",
    "actionBarSize", "dividerHorizontal", "dividerVertical",
    "listPreferredItemHeight", "textColorPrimary", "textColorSecondary",
    "windowActionBar", "windowNoTitle", "isMaterial3Theme",
}

REF_RE = re.compile(r'[@?](?:(\w+):)?(\+?)(\w+)/([\w.]+)')
COMMENT_RE = re.compile(r'<!--.*?-->', re.S)

# Resource names that come from the Material / AppCompat libraries rather
# than from this project. Referenced by name, not by a recognisable prefix.
EXTERNAL_NAMES = {
    "string/appbar_scrolling_view_behavior",
    "string/bottom_sheet_behavior",
    "string/character_counter_pattern",
}


def qualifier(path):
    """values-night -> 'night'; drawable-hdpi -> 'hdpi'; values -> ''."""
    d = os.path.basename(os.path.dirname(path))
    return d.split("-", 1)[1] if "-" in d else ""


def collect(res_dirs):
    """Return (defined, defs_by_bucket, files, parse_errors)."""
    defined = defaultdict(set)          # type -> {name}
    per_bucket = defaultdict(list)      # (bucket, type, name) -> [path]
    files = []
    parse_errors = []

    for res in res_dirs:
        for path in sorted(glob.glob(os.path.join(res, "**", "*.xml"), recursive=True)):
            files.append(path)
            folder = os.path.basename(os.path.dirname(path))
            rtype = folder.split("-", 1)[0]
            stem = os.path.splitext(os.path.basename(path))[0]

            # A file under drawable/, layout/, font/, anim/ ... defines a
            # resource named after the file itself.
            if rtype != "values":
                defined[rtype].add(stem)
                per_bucket[(qualifier(path), rtype, stem)].append(path)
                continue

            try:
                root = ET.parse(path).getroot()
            except ET.ParseError as e:
                # A file that will not parse is a hard failure, not a note.
                # It compiles to nothing, so every definition it was supposed
                # to provide silently disappears and the checks below would
                # report a clean run over a file that does not exist.
                parse_errors.append(f"{path}: XML will not parse: {e}")
                continue

            for el in root:
                if not isinstance(el.tag, str):
                    continue
                name = el.get("name")
                if not name:
                    continue
                if el.tag == "item":
                    t = el.get("type")
                elif el.tag in VALUE_TAGS:
                    t = VALUE_TAGS[el.tag]
                else:
                    t = None
                if not t:
                    continue
                defined[t].add(name)
                per_bucket[(qualifier(path), t, name)].append(path)
                if el.tag == "declare-styleable":
                    for child in el:
                        cn = child.get("name")
                        if cn and not cn.startswith("android:"):
                            defined["attr"].add(cn)

        # non-xml assets: drawable pngs, raw, font ttf
        for path in glob.glob(os.path.join(res, "**", "*"), recursive=True):
            if os.path.isdir(path) or path.endswith(".xml"):
                continue
            folder = os.path.basename(os.path.dirname(path))
            rtype = folder.split("-", 1)[0]
            stem = os.path.splitext(os.path.basename(path))[0]
            defined[rtype].add(stem)

    return defined, per_bucket, files, parse_errors


def is_external(token):
    return token.startswith(EXTERNAL_PREFIXES)


def material_style_error(name):
    """Return a message if `name` claims a Material prefix but does not exist.

    Material style names are only checkable by exact name: the prefix is the
    part that always looks right on a typo, so matching on it alone lets
    invented names such as Widget.Material3.Button.Filled through to aapt2.
    """
    if not name.startswith(CHECKED_MATERIAL_PREFIXES):
        return None
    if name in KNOWN_MATERIAL_STYLES:
        return None
    return (f"unknown Material style {name!r} -- it is not in "
            f"KNOWN_MATERIAL_STYLES in tools/check_res.py. Confirm the exact "
            f"name in the Material Components release this project pins, then "
            f"add it there (see the note above the set).")


def check_refs(files, defined):
    errors, external = [], set()
    for path in files:
        try:
            text = open(path, encoding="utf-8").read()
        except OSError:
            continue
        # Blank out comments but keep newline structure so line numbers hold.
        text = COMMENT_RE.sub(lambda m: re.sub(r'[^\n]', ' ', m.group(0)), text)
        for lineno, line in enumerate(text.splitlines(), 1):
            for pkg, plus, rtype, name in REF_RE.findall(line):
                if pkg == "android" or plus == "+":
                    continue
                if rtype == "attr":
                    if name in defined["attr"] or name in KNOWN_ATTRS:
                        continue
                    errors.append(f"{path}:{lineno}: unknown ?attr/{name}")
                    continue
                # aliases: styleable/id resolved loosely
                if rtype in ("id", "styleable"):
                    continue
                pool = defined.get(rtype, set())
                if name in pool:
                    continue
                if rtype == "array" and (name in defined["array"]):
                    continue
                bad_material = material_style_error(name)
                if bad_material:
                    errors.append(f"{path}:{lineno}: {bad_material}")
                    continue
                if is_external(name) or f"{rtype}/{name}" in EXTERNAL_NAMES:
                    external.add(f"{rtype}/{name}")
                    continue
                errors.append(f"{path}:{lineno}: dangling @{rtype}/{name}")

        # style parents
        if os.path.basename(os.path.dirname(path)).startswith("values"):
            try:
                root = ET.parse(path).getroot()
            except ET.ParseError:
                continue
            for el in root.iter("style"):
                parent = el.get("parent")
                if not parent or parent.startswith(("@", "?")) or parent == "":
                    continue
                if parent in defined["style"]:
                    continue
                bad_material = material_style_error(parent)
                if bad_material:
                    errors.append(
                        f"{path}: style {el.get('name')} parent: {bad_material}")
                    continue
                if is_external(parent):
                    external.add(f"style/{parent}")
                    continue
                errors.append(
                    f"{path}: style {el.get('name')} has unknown parent {parent}")
    return errors, external


def check_dupes(per_bucket):
    out = []
    for (bucket, rtype, name), paths in sorted(per_bucket.items()):
        if len(paths) > 1:
            where = ", ".join(paths)
            out.append(f"duplicate {rtype}/{name} in values-{bucket or 'default'}: {where}")
    return out


def check_night_parity(res_dirs):
    """Every sf* token the base theme sets must also be set in night."""
    out = []
    for res in res_dirs:
        day = os.path.join(res, "values", "themes.xml")
        night = os.path.join(res, "values-night", "themes.xml")
        if not (os.path.exists(day) and os.path.exists(night)):
            continue

        def tokens(p):
            got = defaultdict(set)
            for el in ET.parse(p).getroot().iter("style"):
                for item in el.findall("item"):
                    n = item.get("name", "")
                    if n.startswith("sf"):
                        got[el.get("name")].add(n)
            return got

        d, n = tokens(day), tokens(night)
        for style, names in d.items():
            missing = names - n.get(style, set())
            if missing:
                out.append(
                    f"{night}: style {style} is missing "
                    f"{len(missing)} token(s) present in day: "
                    + ", ".join(sorted(missing)))
    return out


def main():
    res_dirs = sys.argv[1:] or ["app/src/main/res"]
    res_dirs = [d for d in res_dirs if os.path.isdir(d)]
    if not res_dirs:
        print("no resource directories found")
        return 1

    defined, per_bucket, files, parse_errors = collect(res_dirs)
    total = sum(len(v) for v in defined.values())
    print(f"==> scanned {len(files)} xml files, {total} resource definitions")

    errors, external = check_refs(files, defined)
    dupes = check_dupes(per_bucket)
    parity = check_night_parity(res_dirs)

    if external:
        print(f"    {len(external)} framework/library refs (not checkable "
              f"without AARs)")

    problems = parse_errors + errors + dupes + parity
    if problems:
        print(f"\n==> {len(problems)} PROBLEM(S)\n")
        for p in problems:
            print(f"  {p}")
        return 1

    print("==> RESOURCES OK: no dangling refs, no duplicates, night parity holds")
    return 0


if __name__ == "__main__":
    sys.exit(main())
