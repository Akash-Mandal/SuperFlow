#!/usr/bin/env python3
"""
Verifies checked-in generated files are current.

Ramps.kt is generated from the colour XML by tools/gen_palettes.py and
checked in, because the build has no code generation step. A stale copy
would mean the Compose layer renders last week's palette while the View
layer renders this week's - so regenerate into a temporary file and compare.
"""
import subprocess, sys, tempfile, shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TARGET = ROOT / "app/src/main/kotlin/com/superflow/design/Ramps.kt"

def main():
    if not TARGET.exists():
        print(f"{TARGET.relative_to(ROOT)} is missing; run tools/gen_palettes.py")
        sys.exit(1)
    before = TARGET.read_text()
    with tempfile.NamedTemporaryFile(suffix=".kt", delete=False) as tmp:
        backup = tmp.name
    shutil.copy(TARGET, backup)
    try:
        subprocess.run([sys.executable, str(ROOT / "tools/gen_palettes.py")],
                       check=True, capture_output=True)
        after = TARGET.read_text()
    finally:
        shutil.copy(backup, TARGET)
        Path(backup).unlink(missing_ok=True)
    if before != after:
        print("==> Ramps.kt is STALE")
        print("    The colour XML changed but the generated Kotlin was not updated.")
        print("    Run: python3 tools/gen_palettes.py")
        sys.exit(1)
    print("==> GENERATED FILES OK: Ramps.kt matches the colour XML")

if __name__ == "__main__":
    main()
