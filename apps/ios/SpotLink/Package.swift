// swift-tools-version: 6.0
// SpotLink iOS - Swift Package Manager konfiguracija
import PackageDescription

let testingSwiftSettings: [SwiftSetting] = [
    .unsafeFlags(
        ["-F", "/Library/Developer/CommandLineTools/Library/Developer/Frameworks"],
        .when(platforms: [.macOS])
    )
]

let testingLinkerSettings: [LinkerSetting] = [
    .unsafeFlags(
        [
            "-F", "/Library/Developer/CommandLineTools/Library/Developer/Frameworks",
            "-Xlinker", "-rpath",
            "-Xlinker", "/Library/Developer/CommandLineTools/Library/Developer/Frameworks"
        ],
        .when(platforms: [.macOS])
    )
]

let package = Package(
    name: "SpotLink",
    platforms: [
        .iOS(.v17),
        .macOS(.v14)
    ],
    products: [
        .library(name: "SpotLink", targets: ["SpotLink"]),
        .executable(name: "SpotLinkTestRunner", targets: ["SpotLinkTestRunner"])
    ],
    dependencies: [],
    targets: [
        .target(
            name: "SpotLink",
            path: "Sources/SpotLink",
            exclude: ["App/SpotLinkApp.swift"],
            swiftSettings: [
                .enableExperimentalFeature("StrictConcurrency")
            ]
        ),
        .target(
            name: "SpotLinkTestSupport",
            dependencies: ["SpotLink"],
            path: "TestSupport/SpotLinkTestSupport",
            swiftSettings: testingSwiftSettings,
            linkerSettings: testingLinkerSettings
        ),
        .executableTarget(
            name: "SpotLinkTestRunner",
            dependencies: ["SpotLinkTestSupport"],
            path: "Sources/SpotLinkTestRunner",
            swiftSettings: testingSwiftSettings,
            linkerSettings: testingLinkerSettings
        )
    ]
)
