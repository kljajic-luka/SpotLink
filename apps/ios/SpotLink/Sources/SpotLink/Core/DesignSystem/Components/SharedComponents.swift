import SwiftUI

// MARK: - SpotLink Button Style

public struct SpotLinkButtonStyle: ButtonStyle {
    public enum Variant {
        case primary, secondary, destructive, ghost
    }

    let variant: Variant

    public init(_ variant: Variant = .primary) {
        self.variant = variant
    }

    public func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline)
            .frame(maxWidth: .infinity)
            .padding(.vertical, SpotLinkDesign.Spacing.md)
            .padding(.horizontal, SpotLinkDesign.Spacing.lg)
            .background(backgroundColor(pressed: configuration.isPressed))
            .foregroundStyle(foregroundColor)
            .clipShape(RoundedRectangle(cornerRadius: SpotLinkDesign.Radius.md))
            .opacity(configuration.isPressed ? 0.85 : 1)
            .animation(.easeInOut(duration: 0.1), value: configuration.isPressed)
    }

    private func backgroundColor(pressed: Bool) -> Color {
        switch variant {
        case .primary:     return pressed ? SpotLinkDesign.Colors.tint.opacity(0.85) : SpotLinkDesign.Colors.tint
        case .secondary:   return SpotLinkDesign.Colors.secondaryBG
        case .destructive: return pressed ? SpotLinkDesign.Colors.destructive.opacity(0.85) : SpotLinkDesign.Colors.destructive
        case .ghost:       return .clear
        }
    }

    private var foregroundColor: Color {
        switch variant {
        case .primary:     return .white
        case .secondary:   return SpotLinkDesign.Colors.label
        case .destructive: return .white
        case .ghost:       return SpotLinkDesign.Colors.tint
        }
    }
}

// MARK: - Loading Button

/// Dugme koje prikazuje loading indicator dok je u toku async akcija.
public struct LoadingButton: View {
    let title: String
    let isLoading: Bool
    let variant: SpotLinkButtonStyle.Variant
    let action: () async -> Void

    public init(
        _ title: String,
        isLoading: Bool = false,
        variant: SpotLinkButtonStyle.Variant = .primary,
        action: @escaping () async -> Void
    ) {
        self.title = title
        self.isLoading = isLoading
        self.variant = variant
        self.action = action
    }

    public var body: some View {
        Button {
            Task { await action() }
        } label: {
            if isLoading {
                ProgressView()
                    .progressViewStyle(.circular)
                    .tint(variant == .primary ? .white : SpotLinkDesign.Colors.tint)
            } else {
                Text(title)
            }
        }
        .buttonStyle(SpotLinkButtonStyle(variant))
        .disabled(isLoading)
    }
}

// MARK: - Error Banner

public struct ErrorBanner: View {
    let message: String
    let onDismiss: (() -> Void)?

    public init(_ message: String, onDismiss: (() -> Void)? = nil) {
        self.message = message
        self.onDismiss = onDismiss
    }

    public var body: some View {
        HStack(alignment: .top, spacing: SpotLinkDesign.Spacing.sm) {
            Image(systemName: "exclamationmark.circle.fill")
                .foregroundStyle(SpotLinkDesign.Colors.destructive)
                .accessibilityHidden(true)
            Text(message)
                .font(SpotLinkDesign.Typography.footnote)
                .foregroundStyle(SpotLinkDesign.Colors.label)
                .multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, alignment: .leading)
            if let onDismiss {
                Button(action: onDismiss) {
                    Image(systemName: "xmark")
                        .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                }
                .accessibilityLabel("Zatvori gresku")
            }
        }
        .padding(SpotLinkDesign.Spacing.md)
        .background(SpotLinkDesign.Colors.destructive.opacity(0.1))
        .clipShape(RoundedRectangle(cornerRadius: SpotLinkDesign.Radius.sm))
        .accessibilityElement(children: .combine)
    }
}

// MARK: - Loading State View

public struct LoadingView: View {
    let message: String

    public init(_ message: String = "Ucitava se...") {
        self.message = message
    }

    public var body: some View {
        VStack(spacing: SpotLinkDesign.Spacing.md) {
            ProgressView()
                .progressViewStyle(.circular)
                .scaleEffect(1.2)
            Text(message)
                .font(SpotLinkDesign.Typography.subheadline)
                .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(message)
    }
}

// MARK: - Empty State View

public struct EmptyStateView: View {
    let icon: String
    let title: String
    let message: String
    let actionTitle: String?
    let action: (() -> Void)?

    public init(
        icon: String,
        title: String,
        message: String,
        actionTitle: String? = nil,
        action: (() -> Void)? = nil
    ) {
        self.icon = icon
        self.title = title
        self.message = message
        self.actionTitle = actionTitle
        self.action = action
    }

    public var body: some View {
        VStack(spacing: SpotLinkDesign.Spacing.lg) {
            Image(systemName: icon)
                .font(.system(size: 52))
                .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                .accessibilityHidden(true)
            VStack(spacing: SpotLinkDesign.Spacing.sm) {
                Text(title)
                    .font(SpotLinkDesign.Typography.headline)
                    .foregroundStyle(SpotLinkDesign.Colors.label)
                Text(message)
                    .font(SpotLinkDesign.Typography.body)
                    .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                    .multilineTextAlignment(.center)
            }
            if let actionTitle, let action {
                Button(actionTitle, action: action)
                    .buttonStyle(SpotLinkButtonStyle(.secondary))
                    .padding(.horizontal, SpotLinkDesign.Spacing.xl)
            }
        }
        .padding(SpotLinkDesign.Spacing.xl)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityElement(children: .combine)
    }
}

// MARK: - Skeleton Row

/// Placeholder red za loading stanje liste.
public struct SkeletonRow: View {
    public init() {}

    public var body: some View {
        HStack(spacing: SpotLinkDesign.Spacing.md) {
            RoundedRectangle(cornerRadius: SpotLinkDesign.Radius.sm)
                .fill(SpotLinkDesign.Colors.secondaryBG)
                .frame(width: 44, height: 44)
            VStack(alignment: .leading, spacing: SpotLinkDesign.Spacing.xs) {
                RoundedRectangle(cornerRadius: 4)
                    .fill(SpotLinkDesign.Colors.secondaryBG)
                    .frame(maxWidth: .infinity)
                    .frame(height: 14)
                RoundedRectangle(cornerRadius: 4)
                    .fill(SpotLinkDesign.Colors.secondaryBG)
                    .frame(maxWidth: 200)
                    .frame(height: 12)
            }
        }
        .padding(.vertical, SpotLinkDesign.Spacing.sm)
        .redacted(reason: .placeholder)
        .shimmering()
    }
}

// MARK: - Shimmer Effect

public extension View {
    func shimmering() -> some View {
        self.modifier(ShimmerModifier())
    }
}

struct ShimmerModifier: ViewModifier {
    @State private var phase: CGFloat = 0

    func body(content: Content) -> some View {
        content
            .overlay(
                GeometryReader { geo in
                    LinearGradient(
                        gradient: Gradient(stops: [
                            .init(color: .clear, location: 0),
                            .init(color: .white.opacity(0.4), location: 0.4),
                            .init(color: .white.opacity(0.4), location: 0.6),
                            .init(color: .clear, location: 1)
                        ]),
                        startPoint: .init(x: phase - 0.5, y: 0),
                        endPoint: .init(x: phase + 0.5, y: 0)
                    )
                    .blendMode(.screen)
                }
            )
            .onAppear {
                withAnimation(.linear(duration: 1.4).repeatForever(autoreverses: false)) {
                    phase = 1.5
                }
            }
    }
}
