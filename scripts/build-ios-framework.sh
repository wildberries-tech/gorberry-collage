#!/bin/bash
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/build-env.sh"
if [[ "$(uname -s)" != Darwin ]]; then
  echo "error: Building an XCFramework requires macOS and Xcode." >&2
  exit 1
fi
case "${KOTLIN_DEBUG:-0}" in
  0) kotlin_configuration=Release; output_variant=release ;;
  1) kotlin_configuration=Debug; output_variant=debug ;;
  *) echo "error: KOTLIN_DEBUG must be 0 or 1." >&2; exit 1 ;;
esac
tasks=(":collage:assembleGorberryCollage${kotlin_configuration}XCFramework")
if [[ -n "${XCODE_VERSION_ACTUAL:-}" ]]; then
  : "${SDK_NAME:?Xcode must provide SDK_NAME}"
  : "${ARCHS:?Xcode must provide ARCHS}"
  : "${BUILT_PRODUCTS_DIR:?Xcode must provide BUILT_PRODUCTS_DIR}"
  : "${TARGET_BUILD_DIR:?Xcode must provide TARGET_BUILD_DIR}"
  : "${FRAMEWORKS_FOLDER_PATH:?Xcode must provide FRAMEWORKS_FOLDER_PATH}"
  tasks+=(":collage:embedAndSignAppleFrameworkForXcode")
fi
echo "Xcode configuration: ${CONFIGURATION:-not running in Xcode}"
echo "Kotlin configuration: $kotlin_configuration"
# Kotlin 2.3.21 prioritizes CONFIGURATION over KOTLIN_FRAMEWORK_BUILD_TYPE.
# Override only the Gradle process; Swift retains Xcode's Debug/Release settings.
CONFIGURATION="$kotlin_configuration" bash ./gradlew "${tasks[@]}" --stacktrace
framework="$KMP_ROOT/collage/build/XCFrameworks/$output_variant/GorberryCollage.xcframework"
du -sh "$framework"
# Xcode only needs the linked framework. Distribution archives are built from a terminal/CI.
if [[ -z "${XCODE_VERSION_ACTUAL:-}" ]]; then
  output_dir="$KMP_ROOT/dist/ios"
  mkdir -p "$output_dir"
  archive="GorberryCollage-$LIBRARY_VERSION-$output_variant.xcframework.zip"
  rm -f "$output_dir/$archive"
  ditto -c -k --sequesterRsrc --keepParent "$framework" "$output_dir/$archive"
  write_build_info "$output_dir"
  (cd "$output_dir" && shasum -a 256 "$archive" > SHA256SUMS)
  echo "iOS artifact: $output_dir/$archive"
fi
