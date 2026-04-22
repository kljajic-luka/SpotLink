import SwiftUI

// MARK: - SpotLink Design Tokens

/// Centralni dizajn sistem SpotLink aplikacije.
/// Svi pogledi koriste ove tokene – nema scattered hardcoded boja/fontova.
public enum SpotLinkDesign {

    // MARK: - Colors

    public enum Colors {
        // Primarna paleta – hardcoded dok asset catalog nije kreiran
        public static let brand       = Color(red: 0.00, green: 0.48, blue: 1.00)
        public static let brandLight  = Color(red: 0.40, green: 0.73, blue: 1.00)
        public static let brandDark   = Color(red: 0.00, green: 0.30, blue: 0.75)

        // Semanticke boje
        public static let success     = Color(red: 0.13, green: 0.68, blue: 0.44)
        public static let warning     = Color(red: 1.00, green: 0.62, blue: 0.04)
        public static let error       = Color(red: 0.86, green: 0.19, blue: 0.18)
        public static let info        = Color(red: 0.22, green: 0.60, blue: 0.86)

        // Semanticke boje sistema (adaptivne za dark mode)
        public static let primary     = Color.accentColor
        public static let tint        = Color.blue
        public static let destructive = Color.red

        // Pozadina i tekst – cross-platform SwiftUI semantic colors
        public static let background: Color = {
#if os(iOS)
            return Color(UIColor.systemBackground)
#else
            return Color(NSColor.windowBackgroundColor)
#endif
        }()

        public static let secondaryBG: Color = {
#if os(iOS)
            return Color(UIColor.secondarySystemBackground)
#else
            return Color(NSColor.controlBackgroundColor)
#endif
        }()

        public static let label: Color       = Color.primary
        public static let secondaryLabel: Color = Color.secondary

        public static let separator: Color = {
#if os(iOS)
            return Color(UIColor.separator)
#else
            return Color(NSColor.separatorColor)
#endif
        }()
    }

    // MARK: - Typography

    public enum Typography {
        public static let largeTitle   = Font.largeTitle
        public static let title        = Font.title
        public static let title2       = Font.title2
        public static let title3       = Font.title3
        public static let headline     = Font.headline
        public static let body         = Font.body
        public static let callout      = Font.callout
        public static let subheadline  = Font.subheadline
        public static let footnote     = Font.footnote
        public static let caption      = Font.caption
        public static let caption2     = Font.caption2
    }

    // MARK: - Spacing

    public enum Spacing {
        public static let xs:  CGFloat = 4
        public static let sm:  CGFloat = 8
        public static let md:  CGFloat = 16
        public static let lg:  CGFloat = 24
        public static let xl:  CGFloat = 32
        public static let xxl: CGFloat = 48
        public static let xxxl: CGFloat = 64
    }

    // MARK: - Corner Radius

    public enum Radius {
        public static let sm:    CGFloat = 8
        public static let md:    CGFloat = 12
        public static let lg:    CGFloat = 16
        public static let xl:    CGFloat = 24
        public static let full:  CGFloat = 9999
    }

    // MARK: - Shadow

    public enum Shadow {
        public static let card = ShadowStyle(color: .black.opacity(0.08), radius: 8, x: 0, y: 2)
        public static let sheet = ShadowStyle(color: .black.opacity(0.12), radius: 16, x: 0, y: 4)
    }
}

public struct ShadowStyle: Sendable {
    public let color: Color
    public let radius: CGFloat
    public let x: CGFloat
    public let y: CGFloat
}

// MARK: - View Modifiers

public extension View {

    func spotlinkCard() -> some View {
        self
            .background(SpotLinkDesign.Colors.secondaryBG)
            .clipShape(RoundedRectangle(cornerRadius: SpotLinkDesign.Radius.md))
            .shadow(
                color: SpotLinkDesign.Shadow.card.color,
                radius: SpotLinkDesign.Shadow.card.radius,
                x: SpotLinkDesign.Shadow.card.x,
                y: SpotLinkDesign.Shadow.card.y)
    }

    func spotlinkSection() -> some View {
        self.padding(SpotLinkDesign.Spacing.md)
    }
}

// MARK: - Color Extension (system color fallback)

#if !canImport(UIKit)
import AppKit
public typealias PlatformColor = NSColor
extension Color {
    static var systemBackground: Color { Color(NSColor.windowBackgroundColor) }
    static var secondarySystemBackground: Color { Color(NSColor.controlBackgroundColor) }
    static var label: Color { Color(NSColor.labelColor) }
    static var secondaryLabel: Color { Color(NSColor.secondaryLabelColor) }
    static var separator: Color { Color(NSColor.separatorColor) }
}
extension Color {
    init(uiColor: NSColor) {
        self.init(nsColor: uiColor)
    }
}
#endif
