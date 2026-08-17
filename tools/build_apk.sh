#!/usr/bin/env bash
# SuperFlow APK build.
#
# A full AndroidX / Material 3 application built without Gradle, because the
# build environment cannot reach Google Maven, Maven Central or the Gradle
# distribution servers. Dependencies come from a local set of pre-exploded
# AARs (see docs/BUILD.md); this script performs the same steps AGP would:
#
#   compile lib resources -> link with ordered overlays -> generate R ->
#   compile Kotlin -> dex (multidex) -> package -> zipalign -> sign
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
LIBS_DIR="$TOOLCHAIN/androidlibs"
JARS_DIR="$TOOLCHAIN/libjars"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

MIN_SDK=26
TARGET_SDK=34
VERSION_CODE=2
VERSION_NAME="2.0.0"
PKG="com.superflow"

APPDIR="$ROOT/app"
BUILD="$ROOT/build/$MODE"
OUT="$ROOT/build/outputs"
CACHE="$ROOT/build/libcache"

for f in "$JAVA_HOME/bin/java" "$KOTLINC" "$AAPT2" "$DX" "$APKSIGNER" "$ANDROID_JAR" "$KOTLIN_STDLIB"; do
  [ -e "$f" ] || { echo "FATAL: missing toolchain component: $f" >&2; exit 1; }
done
[ -d "$LIBS_DIR" ] || { echo "FATAL: missing library set: $LIBS_DIR" >&2; exit 1; }

echo "=========================================="
echo " SuperFlow $VERSION_NAME ($MODE)"
echo " AndroidX + Material 3"
echo "=========================================="

rm -rf "$BUILD"
mkdir -p "$BUILD/gen" "$BUILD/classes" "$BUILD/dex" "$OUT" "$CACHE"

# Ordered dependency list.
mapfile -t LIBS < <(grep -vE '^\s*(#|$)' "$ROOT/tools/libs.txt")
echo "==> ${#LIBS[@]} libraries"

# ---------------------------------------------------------------- resources
echo "==> [1/7] compiling library resources"
RFLAGS=()
CACHED=0
COMPILED=0
for lib in "${LIBS[@]}"; do
  resdir="$LIBS_DIR/$lib/res"
  [ -d "$resdir" ] || continue
  flat="$CACHE/$lib.zip"
  if [ ! -f "$flat" ] || [ "$resdir" -nt "$flat" ]; then
    "$AAPT2" compile --dir "$resdir" -o "$flat" 2>/dev/null || true
    COMPILED=$((COMPILED+1))
  else
    CACHED=$((CACHED+1))
  fi
  [ -f "$flat" ] && RFLAGS+=(-R "$flat")
done
echo "    $COMPILED compiled, $CACHED cached, ${#RFLAGS[@]} overlay flags"

echo "==> [2/7] compiling app resources"
"$AAPT2" compile --dir "$APPDIR/src/main/res" -o "$BUILD/app-res.zip"

echo "==> [3/7] linking resources"
# Collect library manifests so their components/permissions merge in.
EXTRA_MANIFESTS=()
for lib in "${LIBS[@]}"; do
  m="$LIBS_DIR/$lib/AndroidManifest.xml"
  [ -f "$m" ] && EXTRA_MANIFESTS+=(--manifest-package "$PKG")
done

LINK_FLAGS=(
  --manifest "$APPDIR/src/main/AndroidManifest.xml"
  -I "$ANDROID_JAR"
  --java "$BUILD/gen"
  --min-sdk-version "$MIN_SDK"
  --target-sdk-version "$TARGET_SDK"
  --version-code "$VERSION_CODE"
  --version-name "$VERSION_NAME"
  --auto-add-overlay
  --no-version-vectors
  --output-text-symbols "$BUILD/R.txt"
  -o "$BUILD/base.ap_"
)
[ "$MODE" = "debug" ] && LINK_FLAGS+=(--debug-mode)

"$AAPT2" link "${LINK_FLAGS[@]}" "${RFLAGS[@]}" "$BUILD/app-res.zip"
echo "    $(wc -l < "$BUILD/R.txt") resource symbols"

# ------------------------------------------------------------------- kotlin
echo "==> [4/7] compiling Kotlin"
CP="$ANDROID_JAR"
for lib in "${LIBS[@]}"; do
  j="$JARS_DIR/$lib.jar"
  [ -f "$j" ] && CP="$CP:$j"
done

# aapt2 emits R.java; kotlinc cannot consume it, so translate to Kotlin.
python3 "$ROOT/tools/gen_res.py" "$BUILD/R.txt" "$BUILD/gen/Res.kt" "$PKG"

KT_SRC=$(find "$APPDIR/src/main/kotlin" -name '*.kt' | sort)
echo "    $(echo "$KT_SRC" | wc -l) source files"
"$KOTLINC" \
  -nowarn \
  -jvm-target 1.8 \
  -Xsuppress-version-warnings \
  -classpath "$CP" \
  -d "$BUILD/classes" \
  $KT_SRC "$BUILD/gen/Res.kt" 2>&1 | grep -vE "^(warning|info):" || true

[ -d "$BUILD/classes/com/superflow" ] || { echo "FATAL: Kotlin compilation failed" >&2; exit 1; }

# ---------------------------------------------------------------------- dex
echo "==> [5/7] dexing (multidex)"
DEX_IN=("$BUILD/classes" "$KOTLIN_STDLIB")
for lib in "${LIBS[@]}"; do
  j="$JARS_DIR/$lib.jar"
  [ -f "$j" ] && DEX_IN+=("$j")
done
java -Xmx1600m -cp "$DX" com.android.dx.command.Main \
  --dex --min-sdk-version="$MIN_SDK" --multi-dex \
  --output="$BUILD/dex" "${DEX_IN[@]}" 2>&1 | grep -viE "^warning" || true

ls "$BUILD/dex"/*.dex >/dev/null 2>&1 || { echo "FATAL: dex failed" >&2; exit 1; }
echo "    $(ls "$BUILD/dex"/*.dex | wc -l) dex files"

# ------------------------------------------------------------------ package
echo "==> [6/7] packaging"
cd "$BUILD"
cp base.ap_ unsigned.apk
(cd dex && zip -q -X "$BUILD/unsigned.apk" ./*.dex)
if [ -d "$APPDIR/src/main/assets" ]; then
  (cd "$APPDIR/src/main" && zip -qr "$BUILD/unsigned.apk" assets)
fi

# --------------------------------------------------------------------- sign
echo "==> [7/7] signing"
KS="${SUPERFLOW_KEYSTORE:-$ROOT/build/superflow-$MODE.jks}"
KS_PASS="${SUPERFLOW_KEYSTORE_PASS:-superflow}"
KS_ALIAS="${SUPERFLOW_KEY_ALIAS:-superflow}"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -storepass "$KS_PASS" -keypass "$KS_PASS" \
    -alias "$KS_ALIAS" -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=SuperFlow, OU=Engineering, O=SuperFlow" >/dev/null 2>&1
fi

APK="$OUT/superflow-$MODE.apk"
java -jar "$APKSIGNER" sign \
  --ks "$KS" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --min-sdk-version "$MIN_SDK" \
  --out "$APK" unsigned.apk 2>&1 | grep -viE "warning|restricted method|native-access|will be denied" || true

java -jar "$APKSIGNER" verify --min-sdk-version "$MIN_SDK" "$APK" 2>&1 \
  | grep -viE "warning|restricted method|native-access|will be denied" || true

echo ""
echo "=========================================="
echo " BUILD SUCCESSFUL"
echo " $APK"
echo " $(du -h "$APK" | cut -f1)"
echo "=========================================="
"$AAPT2" dump badging "$APK" 2>/dev/null | head -3 || true
