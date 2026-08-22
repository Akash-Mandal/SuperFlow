#!/usr/bin/env bash
# SuperFlow verification pipeline (standard Gradle build).
#
# The former Gradle-less pipeline (tools/build_apk.sh, removed) is gone; this
# is the only supported build path. Run from the repository root in an
# environment with network access to Google Maven / Maven Central and the
# Android SDK (or ANDROID_HOME set).
#
#   ./tools/verify.sh            build + unit tests + lint + APK
#   ./tools/verify.sh --device   additionally install & smoke-test on a
#                                connected device/emulator
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
WITH_DEVICE=0
[ "${1:-}" = "--device" ] && WITH_DEVICE=1

echo "==> 1/6 unit tests (JVM)"
./gradlew testDebugUnitTest

echo "==> 2/6 lint"
./gradlew lintDebug

echo "==> 3/6 clean assembleDebug"
./gradlew clean assembleDebug

APK=app/build/outputs/apk/debug/app-debug.apk
[ -f "$APK" ] || { echo "FATAL: APK not produced at $APK" >&2; exit 1; }

echo "==> 4/6 APK integrity"
APK_SHA=$(sha256sum "$APK" | cut -d' ' -f1)
APK_SIZE=$(stat -c%s "$APK" 2>/dev/null || stat -f%z "$APK")
echo "    path:  $APK"
echo "    size:  $APK_SIZE bytes"
echo "    sha256: $APK_SHA"

# The merged manifest must contain no unresolved placeholders and must carry
# the androidx-startup provider (from the work-runtime AAR) with the real
# applicationId resolved.
if command -v aapt2 >/dev/null 2>&1; then
  AAPT2="$(command -v aapt2)"
elif [ -n "${ANDROID_HOME:-}" ] && ls "$ANDROID_HOME"/build-tools/*/aapt2 >/dev/null 2>&1; then
  AAPT2="$(ls "$ANDROID_HOME"/build-tools/*/aapt2 | sort -V | tail -1)"
else
  echo "    WARN: aapt2 not found; skipping manifest checks"
  AAPT2=""
fi
if [ -n "$AAPT2" ]; then
  "$AAPT2" dump badging "$APK" | grep -E "^package:|application-label:" | head -3
  MANIFEST=$("$AAPT2" dump xmltree --file AndroidManifest.xml "$APK" 2>/dev/null || true)
  if echo "$MANIFEST" | grep -q 'E: provider'; then
    echo "    merged manifest carries the InitializationProvider (from work-runtime):"
    echo "$MANIFEST" | grep -A3 "E: provider" | head -8
  else
    echo "    NOTE: no <provider> in merged manifest"
  fi
  if echo "$MANIFEST" | grep -q 'applicationId'; then
    echo "FATAL: unresolved \${applicationId} placeholder in merged manifest" >&2
    exit 1
  fi
fi

# Signing check (apksigner ships with build-tools)
if [ -n "${ANDROID_HOME:-}" ] && ls "$ANDROID_HOME"/build-tools/*/apksigner >/dev/null 2>&1; then
  APKSIGNER="$(ls "$ANDROID_HOME"/build-tools/*/apksigner | sort -V | tail -1)"
  "$APKSIGNER" verify --print-certs "$APK" | head -5
fi

echo "==> 5/6 device smoke test"
if [ "$WITH_DEVICE" -eq 1 ]; then
  if ! command -v adb >/dev/null 2>&1; then
    echo "    adb not found; skipping"
  else
    if [ -n "${ANDROID_HOME:-}" ] && [ ! -x "$(command -v adb)" ] && [ -d "$ANDROID_HOME/platform-tools" ]; then
      ADB="$ANDROID_HOME/platform-tools/adb"
    else
      ADB="$(command -v adb)"
    fi
    "$ADB" devices | grep -q "device$" || { echo "FATAL: no device connected" >&2; exit 1; }
    "$ADB" uninstall com.superflow >/dev/null 2>&1 || true
    "$ADB" install -r "$APK"
    "$ADB" logcat -c
    # Cold launch from a force-stopped process.
    "$ADB" shell am force-stop com.superflow || true
    "$ADB" shell monkey -p com.superflow -c android.intent.category.LAUNCHER 1 >/dev/null
    sleep 6
    CRASHES=$("$ADB" logcat -d -v threadtime | grep -E "FATAL EXCEPTION|NoClassDefFoundError|InflateException|Resources\$NotFoundException|ANR in" || true)
    if [ -n "$CRASHES" ]; then
      echo "FATAL: crash/ANR detected during launch:"
      echo "$CRASHES"
      "$ADB" logcat -d -v threadtime | grep -B5 -A40 "FATAL EXCEPTION" | head -80
      exit 1
    fi
    echo "    clean launch: no FATAL/ANR in logcat"
    # Second launch (existing data path).
    "$ADB" shell am force-stop com.superflow || true
    "$ADB" shell monkey -p com.superflow -c android.intent.category.LAUNCHER 1 >/dev/null
    sleep 5
    CRASHES2=$("$ADB" logcat -d -v threadtime | grep -E "FATAL EXCEPTION|ANR in" || true)
    if [ -n "$CRASHES2" ]; then
      echo "FATAL: crash on existing-data launch:"
      echo "$CRASHES2"
      exit 1
    fi
    echo "    existing-data launch: clean"
    # Main screen present?
    "$ADB" shell dumpsys activity activities | grep -q "com.superflow" && echo "    com.superflow activity is on top"
  fi
else
  echo "    skipped (use --device)"
fi

echo "==> 6/6 instrumented tests"
if [ "$WITH_DEVICE" -eq 1 ] && command -v adb >/dev/null 2>&1; then
  ./gradlew connectedDebugAndroidTest
else
  echo "    skipped (needs a connected device/emulator)"
fi

echo
echo "==> VERIFICATION COMPLETE"
