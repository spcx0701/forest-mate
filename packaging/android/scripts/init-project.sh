#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if ! command -v npm >/dev/null 2>&1; then
  echo "error: npm is required." >&2
  exit 1
fi

if [ -z "${JAVA_HOME:-}" ] && [ -x /opt/homebrew/opt/openjdk@17/bin/java ]; then
  export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

echo "Initializing the Bubblewrap Android project for ForestMate."
echo "This is intentionally interactive because Bubblewrap may install JDK/Android SDK tooling and create the signing key."
echo
echo "Use these values when prompted:"
echo "  Application ID: kr.forestmate.app"
echo "  Host: forestmate.onrender.com"
echo "  Name: 숲길동무 ForestMate"
echo "  Launcher name: 숲길동무"
echo "  Signing key alias: forestmate"
echo

npx bubblewrap init --manifest https://forestmate.onrender.com/manifest.json
