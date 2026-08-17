#!/usr/bin/env bash
# Runs SuperFlow's pure-logic test suites on the desktop JVM.
#
# The Android framework classes in android.jar are stubs that throw, so these
# suites cover the framework-independent logic: dates, scheduling, the habit
# ladder, contracts, day-mask parsing, JSON extraction, the natural-language
# coordinator and the Blueprint compiler. A small org.json shim (never shipped
# in the APK) stands in for Android's implementation.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLCHAIN="${SUPERFLOW_TOOLCHAIN:-$HOME/toolchain}"
export JAVA_HOME="$TOOLCHAIN/jdk"
export PATH="$JAVA_HOME/bin:$PATH"
KOTLINC="$TOOLCHAIN/kotlinc/bin/kotlinc"
ANDROID_JAR="$TOOLCHAIN/platforms/android-34.jar"
STDLIB="$TOOLCHAIN/lib/kotlin-stdlib-clean.jar"

cd "$ROOT"
OUT="build/tests"
rm -rf "$OUT"
mkdir -p "$OUT/shim"

echo "==> compiling org.json test shim"
"$KOTLINC" -nowarn -jvm-target 1.8 -d "$OUT/shim" tools/test/JsonShim.kt 2>&1 | grep -i error || true

# Framework-independent sources only.
SRC=$(find app/src/main/kotlin -name '*.kt' | grep -vE "/ui/|/notify/" | sort)

FAILED=0
for suite in LogicTest ParseTest AiTest; do
  echo "==> $suite"
  mkdir -p "$OUT/$suite"
  "$KOTLINC" -nowarn -jvm-target 1.8 \
    -classpath "$ANDROID_JAR:$OUT/shim" \
    -d "$OUT/$suite" $SRC "tools/test/$suite.kt" 2>&1 | grep -i "error:" || true
  if java -cp "$OUT/$suite:$OUT/shim:$STDLIB:$ANDROID_JAR" "${suite}Kt"; then
    echo "    $suite PASSED"
  else
    echo "    $suite FAILED"
    FAILED=1
  fi
  echo
done

if [ "$FAILED" -eq 0 ]; then
  echo "==> ALL SUITES PASSED"
else
  echo "==> SOME SUITES FAILED"
  exit 1
fi
