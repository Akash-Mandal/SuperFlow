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

# The real androidx artifacts come from the pre-exploded AAR set (docs/BUILD.md),
# which is fetched from Google Maven. Where that set is unavailable, compile the
# signature-only androidx shims so the logic suites can still build and run.
# Real jars, when present, are earlier on the classpath and win.
HAVE_SQLITE=0
compgen -G "$TOOLCHAIN/libjars/sqlite*.jar" >/dev/null 2>&1 && HAVE_SQLITE=1
mkdir -p "$OUT/axshim"
if [ "$HAVE_SQLITE" -eq 0 ]; then
  echo "==> compiling androidx test shims (real AARs not present)"
  "$KOTLINC" -nowarn -jvm-target 1.8 -classpath "$ANDROID_JAR" -d "$OUT/axshim" \
    tools/test/AndroidXShim.kt tools/test/AndroidXShimFramework.kt \
    tools/test/AndroidXShimCore.kt 2>&1 | grep -i "error:" || true
fi

CP="$ANDROID_JAR:$OUT/shim"
for j in "$TOOLCHAIN"/libjars/*.jar; do [ -e "$j" ] && CP="$CP:$j"; done
# Coroutines: prefer the vendored artifact, else the one bundled with kotlinc.
if ! compgen -G "$TOOLCHAIN/libjars/kotlinx-coroutines-core*.jar" >/dev/null 2>&1; then
  BUNDLED_COROUTINES="$TOOLCHAIN/kotlinc/lib/kotlinx-coroutines-core-jvm.jar"
  [ -f "$BUNDLED_COROUTINES" ] && CP="$CP:$BUNDLED_COROUTINES"
fi
CP="$CP:$OUT/axshim"

SRC=$(find app/src/main/kotlin -name '*.kt' | grep -vE "/ui/|/notify/|/widget/|/work/|SuperFlowApp" | sort)
FAILED=0
for suite in CoreTest LogicTest ParseTest AiTest DesignTest RoleTest; do
  echo "==> $suite"
  mkdir -p "$OUT/$suite"
  "$KOTLINC" -nowarn -jvm-target 1.8 -classpath "$CP" -d "$OUT/$suite" \
    $SRC "tools/test/$suite.kt" 2>&1 | grep -i "error:" || true
  if java -cp "$OUT/$suite:$OUT/shim:$OUT/axshim:$CP:$STDLIB" "${suite}Kt"; then
    echo "    $suite PASSED"
  else
    echo "    $suite FAILED"; FAILED=1
  fi
  echo
done
# Source-level gates. These stand in for compilers the environment does not
# have: Compose and widget/ are both outside the JVM test source set.
echo "==> compose static check"
if python3 tools/check_compose.py; then
  echo "    compose PASSED"
else
  echo "    compose FAILED"; FAILED=1
fi
echo

echo "==> generated files"
if python3 tools/check_generated.py; then
  echo "    generated PASSED"
else
  echo "    generated FAILED"; FAILED=1
fi
echo

echo "==> widget"
if python3 tools/check_widget.py; then
  echo "    widget PASSED"
else
  echo "    widget FAILED"; FAILED=1
fi
echo

echo "==> data policy"
if python3 tools/check_policy.py; then
  echo "    policy PASSED"
else
  echo "    policy FAILED"; FAILED=1
fi
echo

[ "$FAILED" -eq 0 ] && echo "==> ALL SUITES PASSED" || { echo "==> SOME SUITES FAILED"; exit 1; }
