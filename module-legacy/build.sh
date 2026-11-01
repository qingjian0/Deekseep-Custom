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
OUT=build
rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/dex" "$OUT/generated-src/ds/dsh/DeepSeek"
ANDROIDX_PATH_PARSER_JAR="$(prepare_androidx_path_parser "$OUT")"

MODULE_VER=$(grep -oE 'android:versionName="[^"]+"' AndroidManifest.xml \
  | head -n1 | cut -d'"' -f2)
cat > "$OUT/generated-src/ds/dsh/DeepSeek/BuildInfo.java" <<EOF
package ds.dsh.DeepSeek;
public final class BuildInfo {
    public static final String API_VERSION = "82+ (legacy adapter)";
    public static final String MODULE_VERSION = "${MODULE_VER:-1.7.4}";
    public static final String BUILD_EDITION = "Legacy";
    public static final String DISPLAY_VERSION = MODULE_VERSION + " " + BUILD_EDITION;
    public static final String BUILD_DATE = "$(date '+%Y-%m-%d %H:%M')";
    public static final boolean GOOGLE_PLAY = false;
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

find ../module/src/ds/dsh/DeepSeek -maxdepth 1 -name '*.java' \
  ! -name Main.java ! -name BuildInfo.java > "$OUT/sources.txt"
find ../module/src/ds/dsh/DeepSeek/relay -name '*.java' >> "$OUT/sources.txt"
find compat -name '*.java' >> "$OUT/sources.txt"
find src/de -name '*.java' >> "$OUT/sources.txt"
find "$OUT/generated-src" -name '*.java' >> "$OUT/sources.txt"

if ! javac -source 8 -target 8 \
    -cp "$ANDROID_JAR:$ANDROIDX_PATH_PARSER_JAR:$KOTLIN_RUNTIME_JAR:$KOTLIN_STDLIB_JAR" \
    -d "$OUT/classes" @"$OUT/sources.txt" 2> "$OUT/javac.err"; then
  cat "$OUT/javac.err"
  exit 1
fi
MODCLASSES=$(find "$OUT/classes/com/dsmod" -name '*.class')
java -cp "$R8_JAR" com.android.tools.r8.D8 \
  --min-api 24 --output "$OUT/dex" $MODCLASSES \
  "$KOTLIN_RUNTIME_JAR" "$KOTLIN_STDLIB_JAR" \
  "$ANDROIDX_PATH_PARSER_JAR" --lib "$ANDROID_JAR"
$AAPT2 compile --dir res -o "$OUT/res.zip"
$AAPT2 link -o "$OUT/base.apk" -I "$ANDROID_JAR" \
  --manifest AndroidManifest.xml -R "$OUT/res.zip" --auto-add-overlay
cp "$OUT/base.apk" "$OUT/unsigned.apk"
( cd "$OUT/dex" && zip -q ../unsigned.apk classes*.dex )
mkdir -p "$OUT/xstage/assets"
cp assets/xposed_init "$OUT/xstage/assets/xposed_init"
( cd "$OUT/xstage" && zip -q -9 -r ../unsigned.apk assets )
$ZIPALIGN -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
if [ ! -f legacy.keystore ]; then
  keytool -genkeypair -keystore legacy.keystore -storepass deekseep \
    -keypass deekseep -alias deekseeplegacy \
    -dname "CN=Deekseep Legacy,O=Deekseep,C=US" \
    -keyalg RSA -keysize 2048 -validity 10000 >/dev/null
fi
$APKSIGNER sign --ks legacy.keystore --ks-pass pass:deekseep \
  --key-pass pass:deekseep --out ds-probe-legacy.apk "$OUT/aligned.apk"
echo "DONE -> $(pwd)/ds-probe-legacy.apk"
