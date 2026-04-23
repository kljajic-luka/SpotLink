// swift-tools-version: 6.0
// SpotLink iOS - Swift Package Manager konfiguracija
import PackageDescription

let package = Package(
    name: "SpotLink",
    platforms: [
        .iOS(.v17),
        .macOS(.v14)
    ],
    products: [
        // Zadrzavamo naziv proizvoda "SpotLink" ali menjamo naziv modula/targeta u "SpotLinkCore"
        .library(name: "SpotLink", targets: ["SpotLinkCore"])
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
        .testTarget(
            name: "SpotLinkTestSupport",
            dependencies: ["SpotLinkCore"],
            path: "TestSupport/SpotLinkTestSupport"
        )
    ]
)
