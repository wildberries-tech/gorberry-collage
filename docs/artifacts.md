# Installing and releasing Gorberry Collage

Gorberry Collage ships as an Android AAR, an iOS XCFramework, and an npm tarball.
Download the files from **Releases → version → Assets** in this GitHub repository.
You can use these packages without building the library or setting up a package
registry account. The release workflow does not publish to Maven Central, npm,
or GitHub Packages.

The examples below use version `0.1.0`. Replace it with the version you downloaded.

## Android

Copy `gorberry-collage-0.1.0.aar` to your app's `app/libs/` directory, then add
these dependencies to `app/build.gradle.kts`:

```kotlin
dependencies {
    implementation(files("libs/gorberry-collage-0.1.0.aar"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.3.21")
}
```

The library requires Android API 24 or later. It is built with Kotlin 2.3.21;
your app's Kotlin compiler must support that version's metadata. A local AAR does
not declare transitive dependencies, so Kotlin stdlib must be available in the
app. It is currently the library's only runtime dependency.

Use `ru.wildberries.collage.CollageEngine` to calculate layouts. Your app handles
rendering and image loading. To upgrade, replace the AAR and update its filename
in the dependency declaration.

## iOS

Extract `GorberryCollage-0.1.0-release.xcframework.zip`. In Xcode, add
`GorberryCollage.xcframework` to your target under **Frameworks, Libraries, and
Embedded Content**, and select **Do Not Embed**: the framework is static.

```swift
import GorberryCollage
```

The XCFramework includes iPhone arm64 and simulator arm64/x86_64 builds. Use the
same Release framework for both Debug and Release app configurations. Swift
debugging remains available; stepping through Kotlin requires a separate Debug
build, described under [Building locally](#building-locally).

If you previously built the library from source in an Xcode Run Script, remove
that build step when switching to the downloaded framework.

## Web

Copy `wildberries-gorberry-collage-0.1.0.tgz` into your web project's `vendor/`
directory and install it:

```bash
npm install ./vendor/wildberries-gorberry-collage-0.1.0.tgz
```

Commit the archive, `package.json`, and the updated lockfile to your app's
repository. This keeps the local dependency available to teammates and CI when
they run `npm ci`. No npm account is needed.

```js
import { calculateCollageLayout } from '@wildberries/gorberry-collage';

const layout = calculateCollageLayout({
  width: 360,
  images: [{ id: 'photo', width: 1200, height: 800 }],
});
```

The package includes an ES module, TypeScript declarations, and the compiled
Kotlin implementation. To upgrade, install the new tarball and commit it along
with the updated dependency files.

## Building locally

Use JDK 21 and Android SDK 36. Set the SDK location with `ANDROID_HOME` or
`sdk.dir` in `local.properties`. On macOS, the scripts look for JDK 21 when
`JAVA_HOME` is unset; otherwise, set `JAVA_HOME` yourself. The current Gradle
wrapper does not support Java 25.

Building for iOS also requires macOS and Xcode. Building for the web requires
Node.js 22 and npm. Run the appropriate script from the repository root:

```bash
bash scripts/build-android.sh
bash scripts/build-ios-framework.sh
bash scripts/build-web.sh
```

Packages are written to `dist/android`, `dist/ios`, and `dist/web`. Each directory
also contains `SHA256SUMS`, `build-info.txt`, `LICENSE`, and a copy of this guide
named `INTEGRATION.md`. Build information records the version, source commit,
and whether the working tree had local changes. Generated packages are excluded
from this library's Git repository.

All three builds take their version from `collageVersion` in `gradle.properties`.
Change it before distributing a new version.

### Debugging Kotlin on iOS

The iOS script builds an optimized Release framework by default, including when
called by the Xcode sample's Debug configuration. This keeps the Kotlin binary
smaller without changing the app's Swift build settings.

For Kotlin debugging, set the Xcode user-defined build setting `KOTLIN_DEBUG` to
`1`, or build from a terminal:

```bash
KOTLIN_DEBUG=1 bash scripts/build-ios-framework.sh
```

The Debug framework is larger. Terminal and CI builds produce an XCFramework ZIP;
when invoked by Xcode, the script links the framework without creating an archive.

## Publishing a GitHub release

The workflow in `.github/workflows/gradle.yml` builds all three platforms and
attaches the packages to a GitHub release when you push a version tag.

1. Set `collageVersion` in `gradle.properties`, for example `0.1.0`.
2. Commit and push the changes, including the workflow and build scripts.
3. Tag the commit you want to release and push the tag:

```bash
git tag v0.1.0
git push origin v0.1.0
```

The tag must match the version in `gradle.properties`. For the next release,
update the version and push a new matching tag. A version with a suffix, such as
`0.2.0-beta.1`, is published as a prerelease.

All three builds must succeed before publication. The release job checks package
checksums, versions, and source commits, uploads the files to a draft, then
publishes it. You do not need to create a release manually. Alongside the three
packages, the release includes checksums, build information, the license, and
this guide.

If an upload fails, **Re-run failed jobs** can resume the draft while the build
artifacts are still available. Already published releases are not overwritten;
use a new version for changes.

### GitHub setup

Commit the workflow to `main` to make it available under **Actions → Build library
artifacts**. The repository must allow GitHub Actions, the actions used by the
workflow (`actions/*`, `gradle/actions/setup-gradle`, and
`android-actions/setup-android`), and Linux/macOS runners.

No additional secrets are required. Build jobs use read access; the release job
requests `contents: write` for the built-in `GITHUB_TOKEN`. Organization policies
must allow that permission and release creation.

### Builds between releases

Pushes to `main`, pull requests targeting `main`, and manual runs also build the
packages. These runs only upload Actions artifacts; publication requires a tag
push.

Find these builds under **Actions → Build library artifacts → run → Artifacts**.
Artifact names include the platform and commit:
`gorberry-collage-<platform>-<commit>`. Extract the downloaded Actions ZIP to get
the package and its supporting files.

Actions artifacts are configured to expire after 30 days. Release assets do not
use that retention period, so there is no monthly rebuild requirement for
published versions.

## What CI checks

CI builds the Android, iOS, and web packages. The web build also installs the
finished tarball into a temporary project, imports its public API, and checks
layout calculations and invalid input.

The workflow does not run the full shared test suite. Some `commonTest` tests
still reference the removed `boxW` and `contentW` fields and need to be migrated
to `box.width` and `contentBox.width`. A successful package build is not a full
test-suite pass.
