// swift-tools-version: 6.0
// SpotLink iOS - Swift Package Manager konfiguracija
import PackageDescription

let testingSwiftSettings: [SwiftSetting] = [
    .unsafeFlags(
        ["-F", "/Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/Library/Frameworks"],
        .when(platforms: [.macOS])
    )
]

let testingLinkerSettings: [LinkerSetting] = [
    .unsafeFlags(
        [
            "-F", "/Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/Library/Frameworks",
            "-Xlinker", "-rpath",
            "-Xlinker", "/Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/Library/Frameworks"
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
        // Zadrzavamo naziv proizvoda "SpotLink" ali menjamo naziv modula/targeta u "SpotLinkCore"
        .library(name: "SpotLink", targets: ["SpotLinkCore"]),
        .executable(name: "SpotLinkTestRunner", targets: ["SpotLinkTestRunner"])
    ],
    dependencies: [
        // Mapbox Maps SDK – potreban token u ~/.netrc (api.mapbox.com) za resolving
        .package(url: "https://github.com/mapbox/mapbox-maps-ios.git", from: "11.0.0")
    ],
    targets: [
        .target(
            name: "SpotLinkCore",
            dependencies: [
                // Mapbox se linkuje samo na iOS; macOS test runner ne koristi Mapbox
                .product(name: "MapboxMaps", package: "mapbox-maps-ios", condition: .when(platforms: [.iOS]))
            ],
            path: "Sources/SpotLink",
            exclude: ["App/SpotLinkApp.swift"],
            swiftSettings: [
                .enableExperimentalFeature("StrictConcurrency")
            ]
        ),
        .target(
            name: "SpotLinkTestSupport",
            dependencies: ["SpotLinkCore"],
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
