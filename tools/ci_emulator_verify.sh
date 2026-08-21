#!/usr/bin/env bash
# On-device verification, run inside the emulator session started by the CI
# workflow (.github/workflows/ci.yml).
#
# This is the `--device` half of tools/verify.sh, factored out so the emulator
# runner has a single entry point and so the same sequence can be reproduced
# locally against a connected device:
#
#   ./tools/ci_emulator_verify.sh
#
# It assumes an APK-producing Gradle build is available and that exactly one
# device/emulator is connected.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

PKG=com.superflow
APK=app/build/outputs/apk/debug/app-debug.apk

echo "==> 1/3 assembleDebug"
./gradlew --no-daemon assembleDebug
[ -f "$APK" ] || { echo "FATAL: APK not produced at $APK" >&2; exit 1; }

echo "==> 2/3 launch smoke test"
adb wait-for-device
adb devices | grep -q "device$" || { echo "FATAL: no device connected" >&2; exit 1; }
adb uninstall "$PKG" >/dev/null 2>&1 || true
adb install -r "$APK"

# A launch crash is the failure this repository exists to guard against
# (see docs/BUILD.md), so check a cold start and an existing-data restart.
launch_and_check() {
  local label="$1" pattern="$2"
  # `logcat -c` fails on some emulator images with "failed to clear the 'main'
  # log" (seen on API 26). It is not fatal -- fall back to clearing every
  # buffer, and if that fails too just note the timestamp and carry on, since
  # the crash scan below only needs output from this launch onwards.
  local since=""
  if ! adb logcat -c >/dev/null 2>&1 && ! adb logcat -b all -c >/dev/null 2>&1; then
    echo "    note: could not clear logcat; filtering by time instead"
    since="-T $(adb shell date '+%m-%d %H:%M:%S.000' | tr -d '\r')"
  fi
  adb shell am force-stop "$PKG" || true
  adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null
  sleep 6
  local crashes
  # shellcheck disable=SC2086
  crashes="$(adb logcat -d -v threadtime $since | grep -E "$pattern" || true)"
  if [ -n "$crashes" ]; then
    echo "FATAL: crash/ANR during $label launch:" >&2
    echo "$crashes" >&2
    # Surface it as a job annotation so the reason is visible without
    # downloading the run log.
    if [ "${GITHUB_ACTIONS:-}" = "true" ]; then
      echo "::error title=$label launch crashed::$(echo "$crashes" | head -5 | tr '\n' '~' | sed 's/~/%0A/g')"
    fi
    adb logcat -d -v threadtime | grep -B5 -A40 "FATAL EXCEPTION" | head -80 >&2 || true
    exit 1
  fi
  echo "    $label launch: clean"
}

launch_and_check "clean-install" \
  "FATAL EXCEPTION|NoClassDefFoundError|InflateException|Resources\\\$NotFoundException|ANR in"
launch_and_check "existing-data" "FATAL EXCEPTION|ANR in"

if adb shell dumpsys activity activities | grep -q "$PKG"; then
  echo "    $PKG activity is on top"
fi

echo "==> 3/3 instrumented tests"
./gradlew --no-daemon connectedDebugAndroidTest

echo
echo "==> EMULATOR VERIFICATION COMPLETE"
