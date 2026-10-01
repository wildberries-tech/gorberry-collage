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
            path: "collage/build/XCFrameworks/release/GorberryCollage.xcframework"
        )
    ]
)
