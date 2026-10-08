#!/usr/bin/env bash
# WiFiShield Android — hand-rolled APK build (no Gradle, no network).
# Pipeline: aapt2 compile -> aapt2 link -> javac -> d8 -> zipalign -> apksigner
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SDK="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}"
BT="$SDK/build-tools/36.0.0"
AJAR="$SDK/platforms/android-37.0/android.jar"
JBR="/opt/android-studio/jbr"
PKG_SRC="$ROOT/src"
BUILD="$ROOT/build"
DIST="$ROOT/dist"

rm -rf "$BUILD"
mkdir -p "$BUILD/gen" "$BUILD/classes" "$DIST"
export JAVA_HOME="$JBR"

echo "[1/8] aapt2 compile (res)"
"$BT/aapt2" compile --dir "$ROOT/res" -o "$BUILD/res.zip"

echo "[2/8] aapt2 link (manifest + resources + assets)"
"$BT/aapt2" link \
    -I "$AJAR" \
    --manifest "$ROOT/AndroidManifest.xml" \
    --min-sdk-version 26 --target-sdk-version 34 \
    --version-code 13 --version-name 1.4.6 \
    --java "$BUILD/gen" \
    -A "$ROOT/assets" \
    -o "$BUILD/linked.apk" \
    "$BUILD/res.zip"

echo "[3/8] javac (release 8, bootclasspath=android.jar)"
# fb/ needs the Firebase SDK jars — Gradle-only, excluded from this fallback
find "$PKG_SRC" "$BUILD/gen" -name '*.java' | grep -v '/fb/' > "$BUILD/sources.txt"
"$JBR/bin/javac" --release 8 -nowarn \
    -cp "$AJAR" \
    -d "$BUILD/classes" \
    @"$BUILD/sources.txt"

echo "[4/8] jar classes"
(cd "$BUILD/classes" && "$JBR/bin/jar" cf "$BUILD/classes.jar" .)

echo "[5/8] d8 -> classes.dex"
"$BT/d8" --release --min-api 26 \
    --lib "$AJAR" \
    --output "$BUILD" \
    "$BUILD/classes.jar"

echo "[6/8] inject classes.dex + native libs"
python3 - "$BUILD/linked.apk" "$BUILD/classes.dex" "$BUILD/unsigned.apk" "$ROOT/src/cpp/prebuilt" <<'PY'
import sys, zipfile, os
src, dex, dst, jni = sys.argv[1:5]
libs = []
for abi in ("arm64-v8a", "armeabi-v7a", "x86_64"):
    p = os.path.join(jni, abi, "libwifishield.so")
    if os.path.isfile(p):
        libs.append((p, "lib/%s/libwifishield.so" % abi))
if not libs:
    sys.exit("native libs missing — run src/cpp/build-native.sh first")
with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED) as zout:
    for it in zin.infolist():
        zout.writestr(it, zin.read(it.filename))
    zout.write(dex, "classes.dex")
    for path, arc in libs:
        zout.write(path, arc)
PY

echo "[7/8] zipalign"
"$BT/zipalign" -f -p 4 "$BUILD/unsigned.apk" "$BUILD/aligned.apk"

echo "[8/8] sign"
KS="$ROOT/wifishield.jks"
if [ ! -f "$KS" ]; then
    "$JBR/bin/keytool" -genkeypair -keystore "$KS" -alias wifishield \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass wifishield -keypass wifishield \
        -dname "CN=WiFiShield, OU=CyberSafetyCS, O=CyberSafetyCS, L=Internet, ST=World, C=ZZ"
fi
"$BT/apksigner" sign --ks "$KS" --ks-key-alias wifishield \
    --ks-pass pass:wifishield --key-pass pass:wifishield \
    --out "$DIST/WiFiShield.apk" "$BUILD/aligned.apk"

echo
"$BT/apksigner" verify --print-certs "$DIST/WiFiShield.apk" | head -5
ls -la "$DIST/WiFiShield.apk"
echo "OK: $DIST/WiFiShield.apk"
