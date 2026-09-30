#!/bin/bash
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/build-env.sh"
bash ./gradlew :collage:assembleAndroidMain --stacktrace
shopt -s nullglob
archives=("$KMP_ROOT"/collage/build/outputs/aar/*.aar)
if [[ ${#archives[@]} -ne 1 ]]; then
  echo "error: Expected one Android AAR, found ${#archives[@]}." >&2
  exit 1
fi
output_dir="$KMP_ROOT/dist/android"
mkdir -p "$output_dir"
cp "${archives[0]}" "$output_dir/gorberry-collage-$LIBRARY_VERSION.aar"
write_build_info "$output_dir"
(cd "$output_dir" && shasum -a 256 "gorberry-collage-$LIBRARY_VERSION.aar" > SHA256SUMS)
echo "Android artifact: $output_dir/gorberry-collage-$LIBRARY_VERSION.aar"
