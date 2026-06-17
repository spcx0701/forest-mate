#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

KEYSTORE="${1:-android-signing.keystore}"
ALIAS="${2:-forestmate}"

if [[ -z "${JAVA_HOME:-}" && -x /opt/homebrew/opt/openjdk@17/bin/keytool ]]; then
  export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

if [[ -f .android-signing.env ]]; then
  # shellcheck disable=SC1091
  source .android-signing.env
fi

if [[ ! -f "$KEYSTORE" ]]; then
  echo "error: signing keystore not found: $KEYSTORE" >&2
  echo "Run npm run init:project first, or pass the keystore path as the first argument." >&2
  exit 1
fi

keytool -help >/dev/null 2>&1 || {
  echo "error: keytool requires a working JDK installation." >&2
  exit 1
}

if [[ -n "${BUBBLEWRAP_KEYSTORE_PASSWORD:-}" ]]; then
  keytool -list -v -keystore "$KEYSTORE" -alias "$ALIAS" -storepass "$BUBBLEWRAP_KEYSTORE_PASSWORD" | awk -F': ' '/SHA256:/ { print $2 }'
else
  keytool -list -v -keystore "$KEYSTORE" -alias "$ALIAS" | awk -F': ' '/SHA256:/ { print $2 }'
fi
