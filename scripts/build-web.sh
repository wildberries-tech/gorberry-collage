#!/bin/bash
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/build-env.sh"
command -v npm >/dev/null || { echo "error: Install Node.js 22 with npm." >&2; exit 1; }
bash ./gradlew :collage:prepareWebNpmPackage --stacktrace
output_dir="$KMP_ROOT/dist/web"
mkdir -p "$output_dir"
# pack creates a local archive; it never publishes to the npm registry.
(cd collage/build/npm/gorberry-collage && npm pack --ignore-scripts --pack-destination "$output_dir")
archive="$output_dir/wildberries-gorberry-collage-$LIBRARY_VERSION.tgz"
[[ -f "$archive" ]] || { echo "error: npm archive not found: $archive" >&2; exit 1; }
# Validate the actual consumer archive, with no dependency on the source checkout.
consumer_dir="$(mktemp -d)"
trap 'rm -rf "$consumer_dir"' EXIT
printf '{"private":true,"type":"module"}\n' > "$consumer_dir/package.json"
(cd "$consumer_dir" && npm install --offline --ignore-scripts --no-audit --no-fund --package-lock=false "$archive")
cp "$KMP_ROOT/scripts/check-web-package.mjs" "$consumer_dir/check.mjs"
(cd "$consumer_dir" && node check.mjs)
write_build_info "$output_dir"
(cd "$output_dir" && shasum -a 256 "$(basename "$archive")" > SHA256SUMS)
echo "Web artifact: $archive"
