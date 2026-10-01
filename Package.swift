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
            url: "https://github.com/wildberries-tech/gorberry-collage/releases/download/v0.1.1/GorberryCollage-0.1.1-release.xcframework.zip",
            checksum: "c6b8955ade84dafc9bf5641843d0767ceb90002fb0c7caa2376babb72afe0677"
        )
    ]
)
