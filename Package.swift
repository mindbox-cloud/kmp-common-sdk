// swift-tools-version: 5.6
// The swift-tools-version declares the minimum version of Swift
// required to build this package.
import PackageDescription

let package = Package(
    name: "mindbox_common",
    platforms: [.iOS(.v12)],
    products: [
        .library(
            name: "mindbox_common",
            targets: ["mindbox_common"]
        ),
    ],
    dependencies: [],
    targets: [
        .binaryTarget(
            name: "mindbox_common",
            url: "https://github.com/mindbox-cloud/kmp-common-sdk/releases/download/1.0.0/mindbox_common.xcframework.zip",
            checksum:"ea10bc7a03daf8599ee1d37392914124f043e3384fc472d323804ad368f5939f"),
    ]
)