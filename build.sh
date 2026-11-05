#!/usr/bin/env bash
# Builds the Drehzahl APK with plain Android SDK command line tools. No Gradle, no plugins, no libraries.
# Needs: a JDK (javac), and either an Android SDK (ANDROID_HOME with build-tools + platforms)
#        or aapt2, d8 (or dx), zipalign and apksigner on the PATH.
# Signing: set KEYSTORE, KEYSTORE_PASS and KEY_ALIAS, or put them in signing/keystore.properties.
#          With neither, a throwaway key is made (fine for testing, but updates will not install over it).
set -euo pipefail
cd "$(dirname "$0")"

VERSION_NAME=$(tr -d ' \r\n' < VERSION)
VERSION_CODE=${VERSION_CODE:-$(git rev-list --count HEAD 2>/dev/null || echo 1)}
MIN_SDK=28
TARGET_SDK=35
OUT=build

say() { printf '\033[1;31m==\033[0m %s\n' "$*"; }
die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

newest_build_tools() {
    [ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME/build-tools" ] || return 0
    ls -d "$ANDROID_HOME"/build-tools/* 2>/dev/null | sort -V | tail -1
}
BT=$(newest_build_tools)
tool() {
    if [ -n "$BT" ] && [ -x "$BT/$1" ]; then echo "$BT/$1"; return; fi
    command -v "$1" || true
}
AAPT2=${AAPT2:-$(tool aapt2)}
D8=${D8:-$(tool d8)}
DX=${DX:-$(command -v dalvik-exchange || command -v dx || true)}
ZIPALIGN=${ZIPALIGN:-$(tool zipalign)}
APKSIGNER=${APKSIGNER:-$(tool apksigner)}

if [ -z "${ANDROID_JAR:-}" ] && [ -n "${ANDROID_HOME:-}" ]; then
    if [ -f "$ANDROID_HOME/platforms/android-$TARGET_SDK/android.jar" ]; then
        ANDROID_JAR="$ANDROID_HOME/platforms/android-$TARGET_SDK/android.jar"
    else
        ANDROID_JAR=$(ls -d "$ANDROID_HOME"/platforms/android-*/android.jar 2>/dev/null | sort -V | tail -1 || true)
    fi
fi

[ -n "$AAPT2" ] || die "aapt2 not found (install Android build-tools or set AAPT2)"
[ -n "$D8$DX" ] || die "d8 or dx not found (install Android build-tools or set D8)"
[ -n "$ZIPALIGN" ] || die "zipalign not found"
[ -n "$APKSIGNER" ] || die "apksigner not found"
[ -f "${ANDROID_JAR:-}" ] || die "android.jar not found (set ANDROID_HOME or ANDROID_JAR)"
RES_JAR=${RES_JAR:-$ANDROID_JAR}   # older aapt2 builds may need an older android.jar just for resources
command -v javac >/dev/null || die "javac not found (install a JDK, version 17 or newer)"

say "Drehzahl $VERSION_NAME ($VERSION_CODE) using $(basename "$(dirname "$ANDROID_JAR")")"
rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex" "$OUT/out"

say "Resources"
"$AAPT2" compile --dir app/src/main/res -o "$OUT/res.zip"
"$AAPT2" link -I "$RES_JAR" \
    --manifest app/src/main/AndroidManifest.xml \
    --min-sdk-version "$MIN_SDK" --target-sdk-version "$TARGET_SDK" \
    --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
    --java "$OUT/gen" --auto-add-overlay \
    $( [ -d app/src/main/assets ] && echo "-A app/src/main/assets" ) \
    -o "$OUT/base.apk" "$OUT/res.zip"

say "Java"
find app/src/main/java "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac -source 8 -target 8 -bootclasspath "$ANDROID_JAR" -encoding UTF-8 -Xlint:-options -nowarn \
    -d "$OUT/classes" @"$OUT/sources.txt"

say "Dex"
if [ -n "$D8" ]; then
    find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
    "$D8" --release --min-api "$MIN_SDK" --lib "$ANDROID_JAR" --output "$OUT/dex" @"$OUT/classes.txt"
else
    "$DX" --dex --min-sdk-version=26 --output="$OUT/dex/classes.dex" "$OUT/classes"
fi

say "Package"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
python3 - "$OUT/unsigned.apk" "$OUT/dex/classes.dex" <<'PY'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1], "a", zipfile.ZIP_DEFLATED) as z:
    z.write(sys.argv[2], "classes.dex")
PY
"$ZIPALIGN" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

say "Sign"
if [ -z "${KEYSTORE:-}" ] && [ -f signing/keystore.properties ]; then
    # shellcheck disable=SC1091
    . signing/keystore.properties
fi
if [ -z "${KEYSTORE:-}" ]; then
    KEYSTORE="$OUT/throwaway.jks"; KEYSTORE_PASS=throwaway; KEY_ALIAS=drehzahl
    keytool -genkeypair -keystore "$KEYSTORE" -storepass "$KEYSTORE_PASS" -keypass "$KEYSTORE_PASS" -alias "$KEY_ALIAS" \
        -keyalg RSA -keysize 2048 -validity 3650 -dname "CN=Drehzahl test" >/dev/null 2>&1
    say "No signing key configured: used a throwaway key"
fi
APK="$OUT/out/Drehzahl-$VERSION_NAME.apk"
"$APKSIGNER" sign --ks "$KEYSTORE" --ks-pass "pass:$KEYSTORE_PASS" --key-pass "pass:$KEYSTORE_PASS" \
    --ks-key-alias "$KEY_ALIAS" --min-sdk-version "$MIN_SDK" --out "$APK" "$OUT/aligned.apk"
"$APKSIGNER" verify "$APK"
say "Done: $APK ($(du -h "$APK" | cut -f1))"
