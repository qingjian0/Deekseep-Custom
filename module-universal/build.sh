#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"

source ../scripts/android-tools.sh
source ../scripts/androidx-path-parser.sh

ROOT_DIR="$(cd .. && pwd)"
RUNTIME_DIR="$ROOT_DIR/module-kotlin-runtime"
GRADLE_BIN="$(bash "$ROOT_DIR/scripts/gradle-bootstrap.sh")"
"$GRADLE_BIN" --no-daemon --stacktrace -p "$RUNTIME_DIR" \
  jar stageRuntimeClasspath stageR8Tool
KOTLIN_RUNTIME_JAR="$RUNTIME_DIR/build/libs/dsh-kotlin-runtime-0.1.0.jar"
R8_JAR="$RUNTIME_DIR/build/r8-tool/r8-9.1.29.jar"
KOTLIN_STDLIB_JAR="$(find "$RUNTIME_DIR/build/runtime-classpath" -maxdepth 1 \
  -type f -name 'kotlin-stdlib-*.jar' -print | sort -V | tail -n 1)"
test -s "$KOTLIN_RUNTIME_JAR"
test -s "$KOTLIN_STDLIB_JAR"
test -s "$R8_JAR"
java -cp "$R8_JAR" com.android.tools.r8.D8 --version
RISH_DEX="../third_party/shizuku/rish_shizuku.dex"
RISH_SHA256="1953c1fd9708904f8fc1f67774843b4cc3d03e5f2a578ff4d654d0625456bc28"
GOOGLE_PLAY_BUILD="${GOOGLE_PLAY_BUILD:-false}"
if [[ "$GOOGLE_PLAY_BUILD" == "true" ]]; then
  GOOGLE_PLAY_VALUE=true
  APK_NAME="ds-probe-universal-google-play.apk"
else
  GOOGLE_PLAY_VALUE=false
  APK_NAME="ds-probe-universal.apk"
fi
OUT=build

if [ ! -f "$RISH_DEX" ] \
    || ! printf '%s  %s\n' "$RISH_SHA256" "$RISH_DEX" \
        | sha256sum -c - >/dev/null 2>&1; then
  echo "Missing or modified verified Shizuku rish payload: $RISH_DEX" >&2
  exit 1
fi
if [[ -n "${ANDROID_KEYSTORE_PATH:-}" ]]; then
  KEYSTORE="$ANDROID_KEYSTORE_PATH"
else
  KEYSTORE="$ROOT_DIR/module/debug.keystore"
fi
STORE_PASSWORD="${ANDROID_STORE_PASSWORD:-android}"
KEY_ALIAS="${ANDROID_KEY_ALIAS:-androiddebugkey}"
KEY_PASSWORD="${ANDROID_KEY_PASSWORD:-$STORE_PASSWORD}"

if [[ ! -f "$KEYSTORE" ]]; then
  if [[ -n "${ANDROID_KEYSTORE_PATH:-}" ]]; then
    echo "Configured ANDROID_KEYSTORE_PATH does not exist: $KEYSTORE" >&2
    exit 1
  fi
  keytool -genkeypair -v -keystore "$KEYSTORE" \
    -storepass "$STORE_PASSWORD" -keypass "$KEY_PASSWORD" \
    -alias "$KEY_ALIAS" \
    -dname "CN=Android Debug,O=Android,C=US" \
    -keyalg RSA -keysize 2048 -validity 10000 >/dev/null
fi

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/dex" "$OUT/generated-src/ds/dsh/DeepSeek"
ANDROIDX_PATH_PARSER_JAR="$(prepare_androidx_path_parser "$OUT")"

echo "[0/7] generate BuildInfo.java"
MODULE_VER=$(grep -oE 'android:versionName="[^"]+"' AndroidManifest.xml \
  | head -n1 | cut -d'"' -f2)
cat > "$OUT/generated-src/ds/dsh/DeepSeek/BuildInfo.java" <<EOF
package ds.dsh.DeepSeek;
public final class BuildInfo {
    public static final String API_VERSION = "universal (Xposed API 82-102 verified)";
    public static final String MODULE_VERSION = "${MODULE_VER:-unknown}";
    public static final String BUILD_EDITION = "Open";
    public static final String DISPLAY_VERSION = MODULE_VERSION + " " + BUILD_EDITION;
    public static final String BUILD_DATE = "$(date '+%Y-%m-%d %H:%M')";
    public static final boolean GOOGLE_PLAY = ${GOOGLE_PLAY_VALUE};
    public static final boolean PROTECTED_BUILD = false;
    public static final boolean LOCAL_API_INCLUDED = false;
    public static final String CLOUD_LOCAL_API_PAYLOAD_NAME = "";
    public static final boolean GOOGLE_V241_BASIC = false;
    public static final boolean SHI_V5_V241 = false;
    public static final String SHI_V5_HOST_PACKAGE = "";
    public static final String SHI_V5_HOST_VERSION_NAME = "";
    public static final long SHI_V5_HOST_VERSION_CODE = -1L;
    public static final String SHI_V5_HOST_CERT_SHA256 = "";
    public static final String SHI_V5_HOST_CERT_SHA256_ALT = "";
    public static final String SHI_V5_V236_HOST_VERSION_NAME = "";
    public static final long SHI_V5_V236_HOST_VERSION_CODE = -1L;
    public static final String PROTECTED_PAYLOAD_KEY_A = "";
    public static final String PROTECTED_PAYLOAD_KEY_B = "";
    public static final String PROTECTED_PAYLOAD_IV = "";
    public static final String PROTECTED_PAYLOAD_SHA256 = "";
    public static final String PROTECTED_PAYLOAD_NAME = "";
    public static final String LEGACY_PROTECTED_PAYLOAD_NAME = "";
    public static final String SHI_CORE_SHA256 = "";
    private BuildInfo() {}
}
EOF

cp ../module/src/ds/dsh/DeepSeek/Main.java \
  "$OUT/generated-src/ds/dsh/DeepSeek/Main.java"

echo "[1/7] collect open-source core and Xposed adapter"
find ../module/src/ds/dsh/DeepSeek -maxdepth 1 -name '*.java' \
  ! -name Main.java ! -name BuildInfo.java > "$OUT/sources.txt"
find ../module/src/ds/dsh/DeepSeek/relay -name '*.java' >> "$OUT/sources.txt"
find ../module-legacy/compat -name '*.java' >> "$OUT/sources.txt"
find ../module-legacy/src/de -name '*.java' >> "$OUT/sources.txt"
find "$OUT/generated-src" -name '*.java' >> "$OUT/sources.txt"

echo "[2/7] javac"
if ! javac --release 8 \
    -cp "$ANDROID_JAR:$ANDROIDX_PATH_PARSER_JAR:$KOTLIN_RUNTIME_JAR:$KOTLIN_STDLIB_JAR" \
    -d "$OUT/classes" @"$OUT/sources.txt" 2> "$OUT/javac.err"; then
  cat "$OUT/javac.err"
  exit 1
fi
grep -v 'warning:' "$OUT/javac.err" || true

echo "[3/7] d8"
MODCLASSES=$(find "$OUT/classes/ds/dsh/DeepSeek" -name '*.class')
java -cp "$R8_JAR" com.android.tools.r8.D8 \
  --min-api 24 --output "$OUT/dex" $MODCLASSES \
  "$KOTLIN_RUNTIME_JAR" "$KOTLIN_STDLIB_JAR" \
  "$ANDROIDX_PATH_PARSER_JAR" --lib "$ANDROID_JAR"

echo "[4/7] aapt2"
$AAPT2 compile --dir res -o "$OUT/res.zip"
$AAPT2 link -o "$OUT/base.apk" -I "$ANDROID_JAR" \
  --manifest AndroidManifest.xml -R "$OUT/res.zip" --auto-add-overlay

echo "[5/7] package Xposed metadata and Agent runtime"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
( cd "$OUT/dex" && zip -q ../unsigned.apk classes*.dex )
mkdir -p "$OUT/xstage/assets" "$OUT/xstage/META-INF/ds.dsh.DeepSeek.agent"
cp assets/xposed_init "$OUT/xstage/assets/xposed_init"
cp ../module/hostmap/hostmap28[79].txt.gz ../module/hostmap/hostmap290.txt.gz "$OUT/xstage/"
cp "$RISH_DEX" \
  "$OUT/xstage/META-INF/ds.dsh.DeepSeek.agent/.rish_shizuku_runtime_payload.dat"
( cd "$OUT/xstage" && zip -q -9 -r ../unsigned.apk META-INF assets hostmap287.txt.gz hostmap289.txt.gz hostmap290.txt.gz )

echo "[6/7] zipalign"
$ZIPALIGN -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

echo "[7/7] sign"
$APKSIGNER sign --ks "$KEYSTORE" --ks-pass "pass:$STORE_PASSWORD" \
  --ks-key-alias "$KEY_ALIAS" --key-pass "pass:$KEY_PASSWORD" \
  --out "$APK_NAME" "$OUT/aligned.apk"
echo "DONE -> $(pwd)/$APK_NAME"
