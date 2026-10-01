#!/bin/bash
# Run on macOS after build-ios-framework.sh. Validate the actual archived binary.
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
version="$(sed -n 's/^collageVersion=//p' "$root/gradle.properties" | tr -d '\r')"
assets="$root/dist/ios"
archive="$assets/GorberryCollage-$version-release.xcframework.zip"
repository="${GITHUB_REPOSITORY:-wildberries-tech/gorberry-collage}"
python3 "$root/scripts/swift_package.py" prepare "$assets" --repository "$repository"

# dump-package evaluates the remote manifest without requiring a published release.
swift package --package-path "$assets" dump-package > "$assets/package-description.json"
checksum="$(swift package compute-checksum "$archive")"
python3 - "$assets/package-description.json" "$checksum" <<'PY'
import json
import sys
from pathlib import Path
package = json.loads(Path(sys.argv[1]).read_text())
target, = package['targets']
assert target['type'] == 'binary' and target['name'] == 'GorberryCollage'
assert target['checksum'] == sys.argv[2], 'SwiftPM checksum must match the uploaded ZIP'
PY

temporary="$(mktemp -d)"
trap 'rm -rf "$temporary"' EXIT
ditto -x -k "$archive" "$temporary"
frameworks="$(python3 - "$temporary" "$(uname -m)" <<'PY'
from pathlib import Path
import plistlib
import sys
root = Path(sys.argv[1]) / 'GorberryCollage.xcframework'
info = plistlib.loads((root / 'Info.plist').read_bytes())
for library in info['AvailableLibraries']:
    if (library['SupportedPlatform'] == 'ios'
            and library.get('SupportedPlatformVariant') == 'simulator'
            and sys.argv[2] in library['SupportedArchitectures']):
        print(root / library['LibraryIdentifier'])
        break
else:
    raise SystemExit('Missing simulator slice for the runner architecture')
PY
)"
cat > "$temporary/Smoke.swift" <<'SWIFT'
import GorberryCollage

func makeEngine() -> CollageEngine {
    let configuration = CollageConfiguration()
    configuration.spacing = 2
    return CollageEngine(configuration: configuration)
}
SWIFT
xcrun swiftc -typecheck "$temporary/Smoke.swift" \
  -sdk "$(xcrun --sdk iphonesimulator --show-sdk-path)" \
  -target "$(uname -m)-apple-ios14.0-simulator" -F "$frameworks"
echo "Swift package manifest, archive checksum and simulator import verified"
