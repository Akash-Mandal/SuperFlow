#!/usr/bin/env bash
# Runs SuperFlow's framework-independent logic suites on the desktop JVM.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLCHAIN="${SUPERFLOW_TOOLCHAIN:-$HOME/toolchain}"
export JAVA_HOME="$TOOLCHAIN/jdk"
export PATH="$JAVA_HOME/bin:$PATH"
KOTLINC="$TOOLCHAIN/kotlinc/bin/kotlinc"
ANDROID_JAR="$TOOLCHAIN/platforms/android-34.jar"
STDLIB="$TOOLCHAIN/lib/kotlin-stdlib-clean.jar"
cd "$ROOT"
OUT="build/tests"; rm -rf "$OUT"; mkdir -p "$OUT/shim"
echo "==> compiling org.json test shim"
"$KOTLINC" -nowarn -jvm-target 1.8 -d "$OUT/shim" tools/test/JsonShim.kt 2>&1 | grep -i "error:" || true
CP="$ANDROID_JAR:$OUT/shim"
for j in "$TOOLCHAIN"/libjars/*.jar; do CP="$CP:$j"; done
SRC=$(find app/src/main/kotlin -name '*.kt' | grep -vE "/ui/|/notify/|/widget/|/work/|SuperFlowApp" | sort)
FAILED=0
for suite in CoreTest LogicTest ParseTest AiTest GrowthTest; do
  echo "==> $suite"
  mkdir -p "$OUT/$suite"
  "$KOTLINC" -nowarn -jvm-target 1.8 -classpath "$CP" -d "$OUT/$suite" \
    $SRC "tools/test/$suite.kt" 2>&1 | grep -i "error:" || true
  if java -cp "$OUT/$suite:$OUT/shim:$STDLIB:$ANDROID_JAR" "${suite}Kt"; then
    echo "    $suite PASSED"
  else
    echo "    $suite FAILED"; FAILED=1
  fi
  echo
done
[ "$FAILED" -eq 0 ] && echo "==> ALL SUITES PASSED" || { echo "==> SOME SUITES FAILED"; exit 1; }
