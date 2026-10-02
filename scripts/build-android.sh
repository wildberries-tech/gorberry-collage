#!/bin/bash
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/build-env.sh"
bash ./gradlew :collage:packageAndroidMaven --stacktrace
shopt -s nullglob
archives=("$KMP_ROOT"/collage/build/outputs/aar/*.aar)
if [[ ${#archives[@]} -ne 1 ]]; then
  echo "error: Expected one Android AAR, found ${#archives[@]}." >&2
  exit 1
fi
output_dir="$KMP_ROOT/dist/android"
mkdir -p "$output_dir"
cp "${archives[0]}" "$output_dir/gorberry-collage-$LIBRARY_VERSION.aar"
cp "$KMP_ROOT/collage/build/distributions/gorberry-collage-$LIBRARY_VERSION-maven.zip" "$output_dir/"
python3 scripts/check-android-maven.py "$output_dir/gorberry-collage-$LIBRARY_VERSION-maven.zip"
write_build_info "$output_dir"
python3 - "$output_dir" "$LIBRARY_VERSION" <<'PY'
import hashlib
from pathlib import Path
import sys

output, version = Path(sys.argv[1]), sys.argv[2]
files = [f'gorberry-collage-{version}.aar', f'gorberry-collage-{version}-maven.zip']
(output / 'SHA256SUMS').write_text(''.join(
    f'{hashlib.sha256((output / name).read_bytes()).hexdigest()}  {name}\n' for name in files))
PY
echo "Android artifact: $output_dir/gorberry-collage-$LIBRARY_VERSION.aar"
echo "Android Maven repository: $output_dir/gorberry-collage-$LIBRARY_VERSION-maven.zip"
