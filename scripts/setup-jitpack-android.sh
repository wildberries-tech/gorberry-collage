#!/bin/bash
set -euo pipefail

sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$sdk_root" ]]; then
  echo "error: JitPack must provide ANDROID_HOME or ANDROID_SDK_ROOT." >&2
  exit 1
fi
sdk_manager=""
if [[ -x "$sdk_root/cmdline-tools/latest/bin/sdkmanager" ]]; then
  sdk_manager="$sdk_root/cmdline-tools/latest/bin/sdkmanager"
fi
sdk_manager="${sdk_manager:-$(command -v sdkmanager || true)}"
if [[ -z "$sdk_manager" ]] || ! "$sdk_manager" --version >/dev/null 2>&1; then
  # Older JitPack images may contain SDK tools that cannot run on Java 21.
  # Pinned Linux command-line tools and SHA-256 from developer.android.com/studio.
  root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
  tools_dir="$root/build/jitpack-command-line-tools/15859902"
  mkdir -p "$tools_dir"
  tools_zip="$tools_dir/tools.zip"
  curl --fail --location --retry 3 \
    'https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip' \
    --output "$tools_zip"
  printf '%s  %s\n' '4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583' "$tools_zip" \
    | sha256sum --check -
  unzip -q -o "$tools_zip" -d "$tools_dir"
  sdk_manager="$tools_dir/cmdline-tools/bin/sdkmanager"
fi

# sdkmanager stops reading once licenses are accepted; ignore yes's SIGPIPE,
# while still failing if sdkmanager itself reports an error.
set +o pipefail
yes | "$sdk_manager" --sdk_root="$sdk_root" --licenses >/dev/null
set -o pipefail
"$sdk_manager" --sdk_root="$sdk_root" 'platforms;android-36' 'build-tools;35.0.0'
