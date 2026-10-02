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
            url: "https://github.com/wildberries-tech/gorberry-collage/releases/download/v0.1.2/GorberryCollage-0.1.2-release.xcframework.zip",
            checksum: "58e587fdb7e214aba32d8db28c8d60b0ba6d7ceae6ff593f8cf81ab9d474826c"
        )
    ]
)
