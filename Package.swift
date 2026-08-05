// swift-tools-version:5.10
import PackageDescription

let package = Package(
    name: "MultiplatformAuthenticator",
    platforms: [
        .iOS(.v14),
        .macOS(.v12),
    ],
    products: [
        .library(name: "CoreAuthenticator", targets: ["CoreAuthenticator"])
    ],
    targets: [
        .binaryTarget(
            name: "CoreAuthenticator",
            // Placeholder URL/checksum: automatically overwritten by the first run of
            // the "Build iOS Snapshot" workflow (see .github/workflows/publish-ios-snapshot.yml).
            url: "https://github.com/Infomaniak/multiplatform-authenticator/releases/download/0.0.0/CoreAuthenticator.xcframework.zip",
            checksum: "0000000000000000000000000000000000000000000000000000000000000000000000"
        ),
    ]
)
