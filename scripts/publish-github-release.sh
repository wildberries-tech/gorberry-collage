#!/bin/bash
set -euo pipefail
: "${GH_TOKEN:?GitHub token is required}"
: "${GH_REPO:?GitHub repository is required}"
: "${RELEASE_TAG:?Release tag is required}"
assets_dir="${1:?Prepared release assets directory is required}"
version="${RELEASE_TAG#v}"
files=(
  "$assets_dir/gorberry-collage-$version.aar"
  "$assets_dir/gorberry-collage-$version-maven.zip"
  "$assets_dir/GorberryCollage-$version-release.xcframework.zip"
  "$assets_dir/wildberries-gorberry-collage-$version.tgz"
  "$assets_dir/Package.swift"
  "$assets_dir/SHA256SUMS"
  "$assets_dir/build-info.txt"
  "$assets_dir/INTEGRATION.md"
  "$assets_dir/LICENSE"
)
for file in "${files[@]}"; do
  [[ -s "$file" ]] || { echo "error: Missing release asset: $file" >&2; exit 1; }
done
(cd "$assets_dir" && shasum -a 256 -c SHA256SUMS)
prerelease=false
[[ "$version" != *-* ]] || prerelease=true

if draft=$(gh release view "$RELEASE_TAG" --repo "$GH_REPO" --json isDraft --jq '.isDraft'); then
  if [[ "$draft" == false ]]; then
    # Published versions are immutable from this workflow, including on a rerun.
    names=$(gh release view "$RELEASE_TAG" --repo "$GH_REPO" --json assets --jq '.assets[].name')
    for file in "${files[@]}"; do
      printf '%s\n' "$names" | grep -Fxq "$(basename "$file")" || {
        echo "error: Published release is missing $(basename "$file"); use a new version or fix it manually." >&2
        exit 1
      }
    done
    echo "Release $RELEASE_TAG already published; keeping its existing assets."
    exit 0
  fi
else
  notes=$(mktemp)
  trap 'rm -f "$notes"' EXIT
  cat > "$notes" <<'NOTES'
For iOS, add this GitHub repository in Xcode > Add Package Dependencies and select this version.
Swift Package Manager downloads the prebuilt GorberryCollage XCFramework automatically.
For Android Maven integration, download the Maven repository ZIP (AAR, POM, Gradle metadata and sources).
For manual installation, download the Android AAR, iOS XCFramework ZIP or web npm TGZ from Assets.
Installation instructions are in INTEGRATION.md; SHA256SUMS verifies the packages.
The library source commit and version are recorded in build-info.txt.
The release tag adds the generated Package.swift to that source commit.
NOTES
  create_args=(release create "$RELEASE_TAG" --repo "$GH_REPO" --verify-tag --draft
    --title "Gorberry Collage $RELEASE_TAG" --notes-file "$notes")
  if [[ "$prerelease" == true ]]; then create_args+=(--prerelease); fi
  gh "${create_args[@]}"
fi
# A failed upload leaves a draft that can be resumed by rerunning the job.
gh release upload "$RELEASE_TAG" "${files[@]}" --repo "$GH_REPO" --clobber
gh release edit "$RELEASE_TAG" --repo "$GH_REPO" --draft=false --prerelease="$prerelease"
