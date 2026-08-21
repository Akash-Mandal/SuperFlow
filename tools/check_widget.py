#!/usr/bin/env python3
"""
Cross-checks the widget's Kotlin against its layouts.

A `RemoteViews` is not type-checked against the layout it names. Passing an
id that the layout does not contain compiles perfectly and then throws
`IllegalArgumentException: Couldn't find remote view` at bind time - in the
launcher's process, where the user sees a grey "Problem loading widget" box
and the app's own logs say nothing. It is the single easiest widget bug to
ship and the hardest to notice, because the widget is the one surface a
developer never opens.

There is no compiler here for `widget/` (it is outside the test suite's
source set, since it needs the Android framework), so this script stands in
for one. It checks four things:

  1. Every `R.id.x` used against a `RemoteViews(pkg, R.layout.y)` exists in
     layout `y` - or in `widget_row.xml`, for ids bound on rows that are
     added into it.
  2. Every `R.layout.*` and `R.drawable.*` the widget names exists.
  3. Every view class in a widget layout is on the RemoteViews allow-list.
     An unsupported class is the other silent inflation failure.
  4. The provider XML's initialLayout is one of the layouts the code can
     actually produce.

Run it after touching anything under `widget/` or `res/layout/widget_*`.
"""

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
KOTLIN = ROOT / "app/src/main/kotlin/com/superflow/widget"
LAYOUTS = ROOT / "app/src/main/res/layout"
DRAWABLES = ROOT / "app/src/main/res/drawable"
XML = ROOT / "app/src/main/res/xml"

ANDROID = "{http://schemas.android.com/apk/res/android}"

# https://developer.android.com/reference/android/widget/RemoteViews
# Only these may appear in a layout inflated by a launcher. The list is the
# API 26 baseline (this app's minSdk); later additions are deliberately not
# included, because a widget that works only on new phones is worse than one
# that is written to the floor.
ALLOWED = {
    "AdapterViewFlipper", "FrameLayout", "GridLayout", "GridView",
    "LinearLayout", "ListView", "RelativeLayout", "StackView", "ViewFlipper",
    "AnalogClock", "Button", "Chronometer", "ImageButton", "ImageView",
    "ProgressBar", "TextClock", "TextView", "View", "ViewStub", "Space",
}

problems = []


def ids_in(path):
    """Every @+id declared in a layout."""
    out = set()
    for m in re.finditer(r'android:id="@\+id/(\w+)"', path.read_text()):
        out.add(m.group(1))
    return out


def classes_in(path):
    out = set()
    for el in ET.parse(path).getroot().iter():
        tag = el.tag
        if tag.startswith("{"):
            continue
        out.add(tag)
    return out


def main():
    layout_ids = {}
    for p in sorted(LAYOUTS.glob("widget_*.xml")):
        layout_ids[p.stem] = ids_in(p)

    if not layout_ids:
        problems.append("no widget_*.xml layouts found")

    # --- 3. allow-list
    for p in sorted(LAYOUTS.glob("widget_*.xml")):
        for cls in classes_in(p):
            # Fully-qualified names are library views; none are permitted.
            if "." in cls:
                problems.append(
                    f"{p.name}: <{cls}> is not a framework view; "
                    "RemoteViews cannot inflate it"
                )
            elif cls not in ALLOWED:
                problems.append(
                    f"{p.name}: <{cls}> is not on the RemoteViews allow-list"
                )

    # Rows are inflated separately and added into a container, so ids bound
    # on a row belong to widget_row rather than to the enclosing layout.
    row_ids = layout_ids.get("widget_row", set())

    for src in sorted(KOTLIN.glob("*.kt")):
        text = src.read_text()

        # --- 2. layouts and drawables exist
        for m in re.finditer(r"R\.layout\.(\w+)", text):
            if not (LAYOUTS / f"{m.group(1)}.xml").is_file():
                problems.append(f"{src.name}: R.layout.{m.group(1)} does not exist")
        for m in re.finditer(r"R\.drawable\.(\w+)", text):
            if not (DRAWABLES / f"{m.group(1)}.xml").is_file():
                problems.append(f"{src.name}: R.drawable.{m.group(1)} does not exist")

        # --- 1. ids match the layout they are bound against
        #
        # Scoping is by function body: each helper builds exactly one
        # RemoteViews, which is the convention the file follows and the
        # reason this can be checked at all.
        for block in re.split(r"\n        private fun |\n    private fun ", text):
            layouts = re.findall(r"R\.layout\.(\w+)", block)
            if not layouts:
                continue
            allowed = set(row_ids)
            for name in layouts:
                allowed |= layout_ids.get(name, set())
            for m in re.finditer(r"R\.id\.(\w+)", block):
                if m.group(1) not in allowed:
                    where = "/".join(layouts)
                    problems.append(
                        f"{src.name}: R.id.{m.group(1)} is not in {where}"
                    )

    # --- 4. provider xml
    info = XML / "widget_today_info.xml"
    if info.is_file():
        root = ET.parse(info).getroot()
        for attr in ("initialLayout", "previewLayout"):
            value = root.get(ANDROID + attr)
            if value and value.startswith("@layout/"):
                name = value.split("/", 1)[1]
                if name not in layout_ids:
                    problems.append(f"widget_today_info.xml: {attr} -> {value} missing")
    else:
        problems.append("res/xml/widget_today_info.xml is missing")

    if problems:
        print("==> WIDGET CHECK FAILED\n")
        for p in problems:
            print(f"    {p}")
        print(f"\n{len(problems)} problem(s)")
        return 1

    print(
        f"==> WIDGET CHECK OK: {len(layout_ids)} layouts, "
        f"all ids, classes and resources resolve"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
