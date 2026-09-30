#!/bin/bash
# Shared by the build scripts; always resolve paths relative to the repository.
KMP_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -z "${JAVA_HOME:-}" && "$(uname -s)" == Darwin ]]; then
  JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
  JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
fi
if [[ -n "${JAVA_HOME:-}" ]]; then
  export JAVA_HOME
  export PATH="$JAVA_HOME/bin:$PATH"
fi
if ! command -v java >/dev/null; then
  echo "error: Install JDK 21 and set JAVA_HOME." >&2
  exit 1
fi
java_version="$(java -version 2>&1)"
java_major="$(printf '%s\n' "$java_version" | sed -nE 's/.*version "([0-9]+).*/\1/p' | head -1)"
if [[ ! "$java_major" =~ ^[0-9]+$ ]] || (( java_major < 17 || java_major > 24 )); then
  echo "error: This Gradle/Android setup needs Java 17–24; use JDK 21 (found ${java_major:-unknown})." >&2
  exit 1
fi
LIBRARY_VERSION="$(sed -n 's/^collageVersion=//p' "$KMP_ROOT/gradle.properties" | tr -d '\r')"
if [[ ! "$LIBRARY_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9.-]+)?$ ]]; then
  echo "error: Set collageVersion in gradle.properties to a semantic version." >&2
  exit 1
fi
cd "$KMP_ROOT"
write_build_info() {
  local output_dir="$1"
  {
    echo "version=$LIBRARY_VERSION"
    echo "commit=$(git rev-parse HEAD)"
    if [[ -n "$(git status --porcelain --untracked-files=normal)" ]]; then
      echo "working_tree=modified"
    else
      echo "working_tree=clean"
    fi
  } > "$output_dir/build-info.txt"
  cp "$KMP_ROOT/LICENSE" "$output_dir/LICENSE"
  cp "$KMP_ROOT/docs/artifacts.md" "$output_dir/INTEGRATION.md"
}
