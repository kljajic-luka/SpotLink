import SwiftUI

// MARK: - Platform-Specific Navigation & List Modifiers

public extension View {
    /// Sakriva navigation bar na iOS; no-op na macOS.
    func hideNavigationBar() -> some View {
#if os(iOS)
        self.toolbar(.hidden, for: .navigationBar)
#else
        self
#endif
    }

    /// Primenjuje InsetGrouped stil liste na iOS, Inset na macOS.
    func spotlinkListStyle() -> some View {
#if os(iOS)
        self.listStyle(.insetGrouped)
#else
        self.listStyle(.inset)
#endif
    }
}

// MARK: - Platform-Specific ToolbarItemPlacement

public enum SpotLinkToolbarPlacement {
    /// Odgovara .topBarTrailing na iOS i .automatic na macOS.
    public static var trailing: ToolbarItemPlacement {
#if os(iOS)
        .topBarTrailing
#else
        .automatic
#endif
    }
}

// MARK: - Platform-Specific Input Modifiers

public extension View {
    /// Primenjuje modifikatore za polje za unos email adrese.
    func emailInputStyle() -> some View {
        modifier(EmailInputModifier())
    }

    /// Primenjuje modifikatore za polje za unos lozinke.
    func passwordInputStyle() -> some View {
        modifier(PasswordInputModifier())
    }

    /// Primenjuje modifikatore za polje za unos nove lozinke.
    func newPasswordInputStyle() -> some View {
        modifier(NewPasswordInputModifier())
    }

    /// Primenjuje modifikatore za polje za unos imena (capitalized words).
    func nameInputStyle() -> some View {
        modifier(NameInputModifier())
    }
}

// MARK: - Email Input

struct EmailInputModifier: ViewModifier {
    func body(content: Content) -> some View {
#if os(iOS)
        content
            .textContentType(.emailAddress)
            .keyboardType(.emailAddress)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
#else
        content.autocorrectionDisabled()
#endif
    }
}

// MARK: - Password Input

struct PasswordInputModifier: ViewModifier {
    func body(content: Content) -> some View {
#if os(iOS)
        content.textContentType(.password)
#else
        content
#endif
    }
}

// MARK: - New Password Input

struct NewPasswordInputModifier: ViewModifier {
    func body(content: Content) -> some View {
#if os(iOS)
        content.textContentType(.newPassword)
#else
        content
#endif
    }
}

// MARK: - Name Input

struct NameInputModifier: ViewModifier {
    func body(content: Content) -> some View {
#if os(iOS)
        content.textInputAutocapitalization(.words)
#else
        content
#endif
    }
}
