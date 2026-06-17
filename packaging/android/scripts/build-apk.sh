#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

fail() {
  echo "error: $*" >&2
  exit 1
}

if [[ -z "${JAVA_HOME:-}" && -x /opt/homebrew/opt/openjdk@17/bin/java ]]; then
  export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

java -version >/dev/null 2>&1 || fail "JDK 17+ is required. Install it first, then run npm run init:project."
command -v npm >/dev/null 2>&1 || fail "npm is required."

if [[ -f .android-signing.env ]]; then
  # shellcheck disable=SC1091
  source .android-signing.env
fi

if [[ ! -f twa-manifest.json ]]; then
  fail "twa-manifest.json is missing."
fi

if [[ ! -f gradlew || ! -d app ]]; then
  fail "Bubblewrap Android project is not initialized. Run: npm install && npm run init:project"
fi

: "${BUBBLEWRAP_KEYSTORE_PASSWORD:?Set BUBBLEWRAP_KEYSTORE_PASSWORD before building.}"
: "${BUBBLEWRAP_KEY_PASSWORD:?Set BUBBLEWRAP_KEY_PASSWORD before building.}"

VERSION_NAME="$(node -p 'require("./twa-manifest.json").appVersionName || "1.0.0"')"
OUT_DIR="dist"
OUT_APK="$OUT_DIR/forestmate-android-v${VERSION_NAME}.apk"
OUT_WEAR_APK="$OUT_DIR/forestmate-wear-v${VERSION_NAME}.apk"

npx bubblewrap build --skipPwaValidation --manifest=./twa-manifest.json "$@"

if [[ ! -f app-release-signed.apk ]]; then
  fail "Bubblewrap finished but app-release-signed.apk was not found."
fi

mkdir -p "$OUT_DIR"
cp app-release-signed.apk "$OUT_APK"
shasum -a 256 "$OUT_APK" > "$OUT_APK.sha256"

./gradlew --no-daemon :wear:assembleRelease

WEAR_SIGNED_APK="wear/build/outputs/apk/release/wear-release.apk"
WEAR_UNSIGNED_APK="wear/build/outputs/apk/release/wear-release-unsigned.apk"
if [[ -f "$WEAR_SIGNED_APK" ]]; then
  cp "$WEAR_SIGNED_APK" "$OUT_WEAR_APK"
elif [[ -f "$WEAR_UNSIGNED_APK" ]]; then
  fail "Wear release APK was built unsigned. Check android-signing.keystore and signing passwords."
else
  fail "Wear build finished but no release APK was found."
fi
shasum -a 256 "$OUT_WEAR_APK" > "$OUT_WEAR_APK.sha256"

# APK-only distribution: keep the signed APK and remove Play Console bundle output.
rm -f app-release-bundle.aab

echo "APK ready: $OUT_APK"
cat "$OUT_APK.sha256"
echo "Wear APK ready: $OUT_WEAR_APK"
cat "$OUT_WEAR_APK.sha256"
