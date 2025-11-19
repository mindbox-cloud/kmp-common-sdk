// swift-tools-version: 5.6
// The swift-tools-version declares the minimum version of Swift
// required to build this package.
import PackageDescription

let package = Package(
    name: "MindboxCommon",
    platforms: [.iOS(.v12)],
    products: [
        .library(
            name: "MindboxCommon",
            targets: ["MindboxCommon"]
        ),
    ],
    dependencies: [],
    targets: [
        .binaryTarget(
            name: "MindboxCommon",
            path: "mindbox-common/build/XCFrameworks/release/MindboxCommon.xcframework"
        )
    ]
)