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
            url: "https://github.com/Infomaniak/multiplatform-authenticator/releases/download/1.0.2/CoreAuthenticator.xcframework.zip",
            checksum: "f2a45bd5c913f44798d40398624d6eae4b51c84bc183029a3ca4daf19b1b14c1"
        ),
    ]
)
