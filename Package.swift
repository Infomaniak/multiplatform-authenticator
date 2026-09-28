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
            url: "https://github.com/Infomaniak/multiplatform-authenticator/releases/download/1.0.1/CoreAuthenticator.xcframework.zip",
            checksum: "69a644e66575a3f56cf551ddb7b7569515ae4762d8d987c9c561ced19422c174"
        ),
    ]
)
