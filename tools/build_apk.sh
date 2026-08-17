#!/usr/bin/env bash
# SuperFlow APK build script.
#
# This project is built with a self-contained Android toolchain (no Gradle,
# no Maven access required):
#   - JDK 25 runtime            (TOOLCHAIN/jdk)
#   - Kotlin compiler 2.4.x     (TOOLCHAIN/kotlinc)
#   - aapt2                     (TOOLCHAIN/bin/aapt2)
#   - dx dexer                  (TOOLCHAIN/lib/dx.jar)
#   - apksigner                 (TOOLCHAIN/lib/apksigner.jar)
#   - android.jar API 34        (TOOLCHAIN/platforms/android-34.jar)
#
# Usage: tools/build_apk.sh [debug|release]
set -euo pipefail

MODE="${1:-release}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLCHAIN="${SUPERFLOW_TOOLCHAIN:-$HOME/toolchain}"

JAVA_HOME="$TOOLCHAIN/jdk"
KOTLINC="$TOOLCHAIN/kotlinc/bin/kotlinc"
AAPT2="$TOOLCHAIN/bin/aapt2"
DX="$TOOLCHAIN/lib/dx.jar"
APKSIGNER="$TOOLCHAIN/lib/apksigner.jar"
ANDROID_JAR="$TOOLCHAIN/platforms/android-34.jar"
KOTLIN_STDLIB="$TOOLCHAIN/lib/kotlin-stdlib-clean.jar"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

MIN_SDK=26
TARGET_SDK=34
VERSION_CODE=1
VERSION_NAME="1.0.0"

APPDIR="$ROOT/app"
BUILD="$ROOT/build/$MODE"
OUT="$ROOT/build/outputs"

for f in "$JAVA_HOME/bin/java" "$KOTLINC" "$AAPT2" "$DX" "$APKSIGNER" "$ANDROID_JAR" "$KOTLIN_STDLIB"; do
  [ -e "$f" ] || { echo "FATAL: missing toolchain component: $f" >&2; exit 1; }
done

echo "==> SuperFlow build ($MODE)"
rm -rf "$BUILD"
mkdir -p "$BUILD/classes" "$BUILD/gen" "$BUILD/res" "$OUT"

# ---------------------------------------------------------------- resources
echo "==> [1/6] aapt2 compile resources"
"$AAPT2" compile --dir "$APPDIR/src/main/res" -o "$BUILD/res/resources.zip"

echo "==> [2/6] aapt2 link"
LINK_FLAGS=(--manifest "$APPDIR/src/main/AndroidManifest.xml"
  -I "$ANDROID_JAR"
  --java "$BUILD/gen"
  --min-sdk-version "$MIN_SDK"
  --target-sdk-version "$TARGET_SDK"
  --version-code "$VERSION_CODE"
  --version-name "$VERSION_NAME"
  --no-version-vectors
  --output-text-symbols "$BUILD/R.txt"
  -o "$BUILD/base.ap_")
if [ "$MODE" = "debug" ]; then LINK_FLAGS+=(--debug-mode); fi
"$AAPT2" link "${LINK_FLAGS[@]}" "$BUILD/res/resources.zip"

# ------------------------------------------------- generated resource ids
# kotlinc cannot compile the aapt2-generated R.java, so we translate the
# resource symbol table into a Kotlin object instead.
echo "==> [2b/6] generate Res.kt"
python3 "$ROOT/tools/gen_res.py" "$BUILD/R.txt" "$BUILD/gen/Res.kt"

# ---------------------------------------------------------------- kotlin
echo "==> [3/6] kotlinc (this takes a couple of minutes)"
KT_SRC=$(find "$APPDIR/src/main/kotlin" -name '*.kt' | sort)
KT_COUNT=$(echo "$KT_SRC" | wc -l)
echo "    compiling $KT_COUNT Kotlin files"
"$KOTLINC" \
  -nowarn \
  -jvm-target 1.8 \
  -Xsuppress-version-warnings \
  -classpath "$ANDROID_JAR" \
  -d "$BUILD/classes" \
  $KT_SRC "$BUILD/gen/Res.kt" 2>&1 | grep -v "^warning:" || true

if [ ! -d "$BUILD/classes/com/superflow" ]; then
  echo "FATAL: Kotlin compilation produced no classes" >&2; exit 1
fi

# ---------------------------------------------------------------- dex
echo "==> [4/6] dex"
java -Xmx1500m -cp "$DX" com.android.dx.command.Main \
  --dex --min-sdk-version="$MIN_SDK" \
  --output="$BUILD/classes.dex" \
  "$BUILD/classes" "$KOTLIN_STDLIB"

# ---------------------------------------------------------------- package
echo "==> [5/6] package"
cd "$BUILD"
cp base.ap_ unsigned.apk
zip -q -X unsigned.apk classes.dex
if [ -d "$APPDIR/src/main/assets" ]; then
  ( cd "$APPDIR/src/main" && zip -qr "$BUILD/unsigned.apk" assets )
fi

# ---------------------------------------------------------------- sign
echo "==> [6/6] sign"
KS="${SUPERFLOW_KEYSTORE:-$ROOT/build/superflow-$MODE.jks}"
KS_PASS="${SUPERFLOW_KEYSTORE_PASS:-superflow}"
KS_ALIAS="${SUPERFLOW_KEY_ALIAS:-superflow}"
if [ ! -f "$KS" ]; then
  echo "    generating signing key ($KS)"
  keytool -genkeypair -keystore "$KS" -storepass "$KS_PASS" -keypass "$KS_PASS" \
    -alias "$KS_ALIAS" -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=SuperFlow, OU=Engineering, O=SuperFlow, L=, ST=, C=" >/dev/null 2>&1
fi

APK_NAME="superflow-$MODE.apk"
java -jar "$APKSIGNER" sign \
  --ks "$KS" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --min-sdk-version "$MIN_SDK" \
  --out "$OUT/$APK_NAME" unsigned.apk 2>&1 | grep -viE "^warning|restricted method|native-access|will be denied" || true

java -jar "$APKSIGNER" verify --min-sdk-version "$MIN_SDK" "$OUT/$APK_NAME" 2>&1 \
  | grep -viE "^warning|restricted method|native-access|will be denied" || true

SIZE=$(du -h "$OUT/$APK_NAME" | cut -f1)
echo ""
echo "==> BUILD SUCCESSFUL"
echo "    $OUT/$APK_NAME  ($SIZE)"
"$AAPT2" dump badging "$OUT/$APK_NAME" 2>/dev/null | head -4 || true
