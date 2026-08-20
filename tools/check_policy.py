#!/usr/bin/env python3
"""
Enforces the all-inclusion data policy.

DataPolicy.kt claims that every piece of user data can be exported and
imported. That claim decays silently: someone adds a preference, never
touches DataPolicy, and a restore quietly loses it. Nothing catches that,
because Prefs cannot be instantiated off-device (SharedPreferences throws
against android.jar) so the round trip is not unit-testable.

This checks the claim at the source level instead. Every public `var` in
Prefs.kt must be referenced in both exportPreferences and importPreferences,
or be listed in EXEMPT below with a reason. Adding a preference without
handling it fails the build; deliberately excluding one requires saying why.

Exit code 1 on any violation.
"""

import re
import sys

PREFS = "app/src/main/kotlin/com/superflow/data/Prefs.kt"
POLICY = "app/src/main/kotlin/com/superflow/data/DataPolicy.kt"

# Preferences deliberately outside the export, each with the reason. These
# mirror the KDoc on exportPreferences; if you change one, change both.
EXEMPT = {
    "apiKey": "credential, lives in the secrets file and never in a plaintext export",
    "onboarded": "describes this install, not the user",
    "callsThisMonth": "per-device usage meter; importing would corrupt budget accounting",
    "tokensThisMonth": "per-device usage meter; importing would corrupt budget accounting",
    "costThisMonthCents": "per-device usage meter; importing would corrupt budget accounting",
    "aiAdvancedMode": "boolean alias over aiSetupMode, which is exported",
}

# Exported for reference but intentionally not imported.
EXPORT_ONLY = {
    "fullControlActivated": "destructive autonomy must be re-granted per device",
}


def fail(problems):
    print("==> DATA POLICY VIOLATIONS\n")
    for p in problems:
        print("   ", p)
    print(
        "\nEvery public `var` in Prefs.kt must round-trip through DataPolicy,"
        "\nor be listed in EXEMPT/EXPORT_ONLY in tools/check_policy.py with a"
        "\nreason. See the KDoc on DataPolicy.exportPreferences."
    )
    sys.exit(1)


def main():
    try:
        prefs_src = open(PREFS).read()
        policy_src = open(POLICY).read()
    except OSError as e:
        print(f"cannot read source: {e}")
        sys.exit(1)

    names = re.findall(r"\n    var ([A-Za-z0-9_]+)\s*:", prefs_src)
    if not names:
        print("no public vars found in Prefs.kt - has the file moved?")
        sys.exit(1)

    try:
        e_start = policy_src.index("fun exportPreferences")
        i_start = policy_src.index("fun importPreferences")
    except ValueError:
        print("cannot locate exportPreferences/importPreferences in DataPolicy.kt")
        sys.exit(1)

    export = policy_src[e_start:i_start]
    imp = policy_src[i_start:]

    problems = []
    covered = 0
    for name in names:
        ref = f"prefs.{name}"
        in_export = ref in export
        in_import = ref in imp

        if name in EXEMPT:
            # An exempt preference must stay out; if someone starts exporting
            # it, the exemption is stale and the reason needs revisiting.
            if in_export or in_import:
                problems.append(
                    f"{name}: listed EXEMPT ({EXEMPT[name]}) but is referenced in DataPolicy"
                )
            continue

        if name in EXPORT_ONLY:
            if not in_export:
                problems.append(f"{name}: listed EXPORT_ONLY but is not exported")
            if in_import:
                problems.append(
                    f"{name}: listed EXPORT_ONLY ({EXPORT_ONLY[name]}) but is imported"
                )
            covered += 1
            continue

        if not in_export:
            problems.append(f"{name}: not exported")
        if not in_import:
            problems.append(f"{name}: not imported")
        if in_export and in_import:
            covered += 1

    total = len(names)
    if problems:
        print(f"==> checked {total} preferences, {covered} round-trip cleanly")
        fail(problems)

    exempt = len(EXEMPT)
    print(f"==> checked {total} preferences in Prefs.kt")
    print(f"    {covered} round-trip through export/import")
    print(f"    {exempt} deliberately exempt, each with a documented reason")
    print("==> DATA POLICY OK: all-inclusion holds")


if __name__ == "__main__":
    main()
