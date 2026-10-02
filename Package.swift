// swift-tools-version:5.3
import PackageDescription

// For local development, first run: bash scripts/build-ios-framework.sh
// The release workflow replaces the local path with a versioned URL and checksum.
let package = Package(
    name: "GorberryCollage",
    platforms: [.iOS(.v14)],
    products: [
        .library(name: "GorberryCollage", targets: ["GorberryCollage"])
    ],
    targets: [
        .binaryTarget(
            name: "GorberryCollage",
            url: "https://github.com/wildberries-tech/gorberry-collage/releases/download/v0.1.3/GorberryCollage-0.1.3-release.xcframework.zip",
            checksum: "cb4dc16538e828a8ca76051449f466982fa978424b76537a38cef4a4b94a969c"
        )
    ]
)
