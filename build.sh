#!/bin/sh
# Builds out/calmtablet.apk. Needs Java 17+ and the Android SDK (any platform 34+, any build-tools 34+).
#   macOS:   brew install openjdk@17 && brew install --cask android-commandlinetools
#            sdkmanager "platforms;android-34" "build-tools;34.0.0"
#   Windows: Android Studio's SDK works as is. Run this from Git Bash.
# Use a different app list with: APPS=my-apps.txt sh build.sh
set -e
cd "$(dirname "$0")"
PKG=com/tabletdetox/home; NAME=calmtablet

[ -n "$JAVA_HOME" ] || for j in /opt/homebrew/opt/openjdk@17 /usr/local/opt/openjdk@17 /usr/lib/jvm/java-17-openjdk-amd64; do [ -d "$j" ] && JAVA_HOME=$j && break; done
[ -n "$JAVA_HOME" ] && export PATH="$JAVA_HOME/bin:$PATH"
SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
[ -n "$SDK" ] || for d in /opt/homebrew/share/android-commandlinetools "$HOME/Library/Android/sdk" "$HOME/Android/Sdk" "${LOCALAPPDATA:-/nonexistent}/Android/Sdk"; do [ -d "$d" ] && SDK=$d && break; done
# The newest platform and build-tools installed.
JAR=$(ls -d "$SDK"/platforms/android-*/android.jar 2>/dev/null | sort -V | tail -1)
BT=$(ls -d "$SDK"/build-tools/*/ 2>/dev/null | sort -V | tail -1); BT=${BT%/}
[ -f "$JAR" ] && [ -d "$BT" ] || { echo "Android SDK not found. Set ANDROID_HOME, and install a platform and build-tools (34 or newer)." >&2; exit 1; }
# On Windows the tools are .exe and .bat files.
tool() { for t in "$BT/$1" "$BT/$1.exe" "$BT/$1.bat"; do [ -f "$t" ] && { echo "$t"; return; }; done; echo "$BT/$1"; }

rm -rf out; mkdir -p out/classes out/dex out/gen/$PKG

# Turn the app list into Apps.java.
{
  echo "package $(echo $PKG | tr / .);"
  echo "final class Apps { static final String[] LIST = {"
  grep -v '^[[:space:]]*#' "${APPS:-apps.txt}" | tr -d ' \t\r' | grep -v '^$' | sed 's/.*/    "&",/'
  echo "}; }"
} > out/gen/$PKG/Apps.java

"$(tool aapt2)" link -o out/base.apk -I "$JAR" --manifest AndroidManifest.xml --min-sdk-version 23 --target-sdk-version 34
# shellcheck disable=SC2046 # javac and d8 want one argument per file, so the list is split on purpose
javac -nowarn -Xlint:-options -encoding UTF-8 --release 8 -classpath "$JAR" \
  -d out/classes $(find src out/gen -name "*.java")
# shellcheck disable=SC2046 # same here
"$(tool d8)" --min-api 23 --release --lib "$JAR" --output out/dex $(find out/classes -name '*.class')
cp out/base.apk out/unsigned.apk
if command -v zip >/dev/null 2>&1; then (cd out/dex && zip -q ../unsigned.apk classes.dex)
else (cd out/dex && jar uf ../unsigned.apk classes.dex); fi
"$(tool zipalign)" -f 4 out/unsigned.apk out/aligned.apk

# A signing key is made on first build. Keep key.jks: updates must be signed with the same key.
KS=${KEYSTORE:-key.jks}; PASS=${KEYSTORE_PASS:-calmtablet123}
[ -f "$KS" ] || keytool -genkeypair -keystore "$KS" -storepass "$PASS" -keypass "$PASS" -alias $NAME \
  -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=$NAME" >/dev/null 2>&1
"$(tool apksigner)" sign --ks "$KS" --ks-pass "pass:$PASS" --out out/$NAME.apk out/aligned.apk
echo "Built out/$NAME.apk"
