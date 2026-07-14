#!/usr/bin/env sh
set -eu
GRADLE_VERSION=8.10.2
CACHE_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}/custom-wrapper/gradle-$GRADLE_VERSION"
ZIP="$CACHE_DIR/gradle.zip"
DIST="$CACHE_DIR/gradle-$GRADLE_VERSION"
if [ ! -x "$DIST/bin/gradle" ]; then
  mkdir -p "$CACHE_DIR"
  URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
  if command -v curl >/dev/null 2>&1; then curl -L "$URL" -o "$ZIP"; else wget -O "$ZIP" "$URL"; fi
  unzip -oq "$ZIP" -d "$CACHE_DIR"
fi
exec "$DIST/bin/gradle" "$@"
