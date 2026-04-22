import SwiftUI

// MARK: - Auth Flow Container

/// Container koji rutira izmedju login, registracije i password reset flow-a.
public struct AuthFlowView: View {

    @State private var currentFlow: AuthFlow = .login

    public init() {}

    public var body: some View {
        NavigationStack {
            switch currentFlow {
            case .login:
                LoginView(
                    onRegisterTapped: { currentFlow = .register },
                    onForgotPasswordTapped: { currentFlow = .passwordReset })
                .transition(.asymmetric(
                    insertion: .move(edge: .leading),
                    removal: .move(edge: .trailing)))

            case .register:
                RegisterView(onLoginTapped: { currentFlow = .login })
                    .transition(.asymmetric(
                        insertion: .move(edge: .trailing),
                        removal: .move(edge: .leading)))

            case .passwordReset:
                PasswordResetView(onBackTapped: { currentFlow = .login })
                    .transition(.asymmetric(
                        insertion: .move(edge: .trailing),
                        removal: .move(edge: .leading)))
            }
        }
        .animation(.easeInOut(duration: 0.25), value: currentFlow)
    }
}

enum AuthFlow {
    case login
    case register
    case passwordReset
}

// MARK: - Login View

public struct LoginView: View {

    @StateObject private var viewModel = LoginViewModel()
    @EnvironmentObject private var session: SessionManager
    @Environment(\.appEnvironment) private var env

    var onRegisterTapped: () -> Void
    var onForgotPasswordTapped: () -> Void

    public var body: some View {
        ScrollView {
            VStack(spacing: SpotLinkDesign.Spacing.xl) {
                // Zaglavlje
                VStack(spacing: SpotLinkDesign.Spacing.sm) {
                    Image(systemName: "parkingsign.circle.fill")
                        .font(.system(size: 52))
                        .foregroundStyle(SpotLinkDesign.Colors.tint)
                        .accessibilityHidden(true)
                    Text("Prijava")
                        .font(SpotLinkDesign.Typography.largeTitle.bold())
                    Text("Prijavite se na SpotLink nalog")
                        .font(SpotLinkDesign.Typography.subheadline)
                        .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                }
                .padding(.top, SpotLinkDesign.Spacing.xl)

                // Forma
                VStack(spacing: SpotLinkDesign.Spacing.md) {
                    if let error = viewModel.errorMessage {
                        ErrorBanner(error) { viewModel.clearError() }
                    }

                    VStack(alignment: .leading, spacing: SpotLinkDesign.Spacing.xs) {
                        Text("Email")
                            .font(SpotLinkDesign.Typography.caption.bold())
                            .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                        TextField("ime@email.com", text: $viewModel.email)
                            .emailInputStyle()
                            .padding(SpotLinkDesign.Spacing.md)
                            .background(SpotLinkDesign.Colors.secondaryBG)
                            .clipShape(RoundedRectangle(cornerRadius: SpotLinkDesign.Radius.sm))
                            .accessibilityLabel("Email adresa")
                    }

                    VStack(alignment: .leading, spacing: SpotLinkDesign.Spacing.xs) {
                        Text("Lozinka")
                            .font(SpotLinkDesign.Typography.caption.bold())
                            .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                        SecureField("Unesite lozinku", text: $viewModel.password)
                            .passwordInputStyle()
                            .padding(SpotLinkDesign.Spacing.md)
                            .background(SpotLinkDesign.Colors.secondaryBG)
                            .clipShape(RoundedRectangle(cornerRadius: SpotLinkDesign.Radius.sm))
                            .accessibilityLabel("Lozinka")
                    }

                    Button("Zaboravili ste lozinku?", action: onForgotPasswordTapped)
                        .font(SpotLinkDesign.Typography.footnote)
                        .foregroundStyle(SpotLinkDesign.Colors.tint)
                        .frame(maxWidth: .infinity, alignment: .trailing)

                    LoadingButton("Prijavi se", isLoading: viewModel.isLoading) {
                        await viewModel.login(session: session)
                    }
                    .disabled(!viewModel.isFormValid)
                }
                .padding(SpotLinkDesign.Spacing.md)
                .spotlinkCard()

                // Registracija
                HStack {
                    Text("Nemate nalog?")
                        .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                    Button("Registrujte se", action: onRegisterTapped)
                        .foregroundStyle(SpotLinkDesign.Colors.tint)
                }
                .font(SpotLinkDesign.Typography.footnote)

                Spacer()
            }
            .padding(SpotLinkDesign.Spacing.md)
        }
        .hideNavigationBar()
        .background(SpotLinkDesign.Colors.background)
    }
}

// MARK: - Login View Model

@MainActor
public final class LoginViewModel: ObservableObject {

    @Published var email: String = ""
    @Published var password: String = ""
    @Published var isLoading: Bool = false
    @Published var errorMessage: String? = nil

    private var apiClient: APIClientProtocol?

    var isFormValid: Bool {
        Validators.isValidEmail(email) && password.count >= 8
    }

    func login(session: SessionManager) async {
        guard isFormValid else { return }
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        guard let client = apiClient ?? makeDefaultClient() else {
            errorMessage = "Konfiguracija nije dostupna."
            return
        }

        let authService = AuthService(apiClient: client, session: session)
        do {
            try await authService.login(email: email, password: password)
        } catch let apiError as APIError {
            errorMessage = apiError.userFacingMessage
        } catch {
            errorMessage = "Greska pri prijavi. Pokusajte ponovo."
        }
    }

    func clearError() { errorMessage = nil }

    private func makeDefaultClient() -> APIClientProtocol? {
        let env = AppEnvironment.current()
        return APIClient(baseURL: env.apiBaseURL, tokenProvider: NullTokenProvider())
    }
}

/// Token provider koji ne vraca token (pre prijave).
struct NullTokenProvider: TokenProvider {
    func currentToken() async -> String? { nil }
}

// MARK: - Register View

public struct RegisterView: View {

    @StateObject private var viewModel = RegisterViewModel()
    @EnvironmentObject private var session: SessionManager
    var onLoginTapped: () -> Void

    public var body: some View {
        ScrollView {
            VStack(spacing: SpotLinkDesign.Spacing.xl) {
                VStack(spacing: SpotLinkDesign.Spacing.sm) {
                    Text("Registracija")
                        .font(SpotLinkDesign.Typography.largeTitle.bold())
                    Text("Kreirajte SpotLink nalog")
                        .font(SpotLinkDesign.Typography.subheadline)
                        .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                }
                .padding(.top, SpotLinkDesign.Spacing.xl)

                VStack(spacing: SpotLinkDesign.Spacing.md) {
                    if let error = viewModel.errorMessage {
                        ErrorBanner(error) { viewModel.clearError() }
                    }

                    RegisterFormView(viewModel: viewModel)

                    LoadingButton("Registruj se", isLoading: viewModel.isLoading) {
                        await viewModel.register(session: session)
                    }
                    .disabled(!viewModel.isFormValid)
                }
                .padding(SpotLinkDesign.Spacing.md)
                .spotlinkCard()

                HStack {
                    Text("Vec imate nalog?")
                        .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                    Button("Prijavite se", action: onLoginTapped)
                        .foregroundStyle(SpotLinkDesign.Colors.tint)
                }
                .font(SpotLinkDesign.Typography.footnote)

                Spacer()
            }
            .padding(SpotLinkDesign.Spacing.md)
        }
        .hideNavigationBar()
        .background(SpotLinkDesign.Colors.background)
    }
}

private struct RegisterFormView: View {
    @ObservedObject var viewModel: RegisterViewModel

    var body: some View {
        VStack(spacing: SpotLinkDesign.Spacing.md) {
            HStack(spacing: SpotLinkDesign.Spacing.sm) {
                namedField("Ime", placeholder: "Marko", text: $viewModel.firstName)
                    .nameInputStyle()
                namedField("Prezime", placeholder: "Petrovic", text: $viewModel.lastName)
                    .nameInputStyle()
            }
            namedField("Email", placeholder: "ime@email.com", text: $viewModel.email)
                .emailInputStyle()
            VStack(alignment: .leading, spacing: SpotLinkDesign.Spacing.xs) {
                Text("Lozinka")
                    .font(SpotLinkDesign.Typography.caption.bold())
                    .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                SecureField("Min. 8 karaktera", text: $viewModel.password)
                    .newPasswordInputStyle()
                    .padding(SpotLinkDesign.Spacing.md)
                    .background(SpotLinkDesign.Colors.secondaryBG)
                    .clipShape(RoundedRectangle(cornerRadius: SpotLinkDesign.Radius.sm))
                    .accessibilityLabel("Nova lozinka, minimum 8 karaktera")
            }
            Toggle("Prihvatam uslove koriscenja", isOn: $viewModel.acceptsTerms)
                .font(SpotLinkDesign.Typography.footnote)
        }
    }

    @ViewBuilder
    private func namedField(_ label: String, placeholder: String, text: Binding<String>) -> some View {
        VStack(alignment: .leading, spacing: SpotLinkDesign.Spacing.xs) {
            Text(label)
                .font(SpotLinkDesign.Typography.caption.bold())
                .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
            TextField(placeholder, text: text)
                .autocorrectionDisabled()
                .padding(SpotLinkDesign.Spacing.md)
                .background(SpotLinkDesign.Colors.secondaryBG)
                .clipShape(RoundedRectangle(cornerRadius: SpotLinkDesign.Radius.sm))
                .accessibilityLabel(label)
        }
    }
}

// MARK: - Register View Model

@MainActor
public final class RegisterViewModel: ObservableObject {
    @Published var firstName: String = ""
    @Published var lastName: String = ""
    @Published var email: String = ""
    @Published var password: String = ""
    @Published var acceptsTerms: Bool = false
    @Published var isLoading: Bool = false
    @Published var errorMessage: String? = nil

    var isFormValid: Bool {
        !firstName.isEmpty && !lastName.isEmpty
        && Validators.isValidEmail(email)
        && password.count >= 8
        && acceptsTerms
    }

    func register(session: SessionManager) async {
        guard isFormValid else { return }
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        let env = AppEnvironment.current()
        let client = APIClient(baseURL: env.apiBaseURL, tokenProvider: NullTokenProvider())
        let authService = AuthService(apiClient: client, session: session)
        let request = RegisterCustomerRequest(
            firstName: firstName, lastName: lastName,
            email: email, password: password, acceptsTerms: acceptsTerms)
        do {
            try await authService.registerCustomer(request)
        } catch let apiError as APIError {
            errorMessage = apiError.userFacingMessage
        } catch {
            errorMessage = "Greska pri registraciji. Pokusajte ponovo."
        }
    }

    func clearError() { errorMessage = nil }
}

// MARK: - Password Reset View

public struct PasswordResetView: View {
    @State private var email: String = ""
    @State private var isLoading: Bool = false
    @State private var isSuccess: Bool = false
    @State private var errorMessage: String? = nil
    var onBackTapped: () -> Void

    public var body: some View {
        ScrollView {
            VStack(spacing: SpotLinkDesign.Spacing.xl) {
                VStack(spacing: SpotLinkDesign.Spacing.sm) {
                    Image(systemName: "lock.rotation")
                        .font(.system(size: 52))
                        .foregroundStyle(SpotLinkDesign.Colors.tint)
                        .accessibilityHidden(true)
                    Text("Reset lozinke")
                        .font(SpotLinkDesign.Typography.largeTitle.bold())
                    Text("Unesite email adresu za reset link")
                        .font(SpotLinkDesign.Typography.subheadline)
                        .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                }
                .padding(.top, SpotLinkDesign.Spacing.xl)

                VStack(spacing: SpotLinkDesign.Spacing.md) {
                    if isSuccess {
                        HStack {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundStyle(SpotLinkDesign.Colors.tint)
                            Text("Link za reset lozinke je poslat na vaš email.")
                                .font(SpotLinkDesign.Typography.footnote)
                        }
                        .padding(SpotLinkDesign.Spacing.md)
                        .background(SpotLinkDesign.Colors.tint.opacity(0.1))
                        .clipShape(RoundedRectangle(cornerRadius: SpotLinkDesign.Radius.sm))
                    } else {
                        if let error = errorMessage {
                            ErrorBanner(error) { errorMessage = nil }
                        }
                        TextField("Email adresa", text: $email)
                            .emailInputStyle()
                            .padding(SpotLinkDesign.Spacing.md)
                            .background(SpotLinkDesign.Colors.secondaryBG)
                            .clipShape(RoundedRectangle(cornerRadius: SpotLinkDesign.Radius.sm))
                            .accessibilityLabel("Email adresa za reset lozinke")

                        LoadingButton("Posalji reset link", isLoading: isLoading) {
                            await requestReset()
                        }
                        .disabled(!Validators.isValidEmail(email))
                    }
                }
                .padding(SpotLinkDesign.Spacing.md)
                .spotlinkCard()

                Button("Nazad na prijavu", action: onBackTapped)
                    .font(SpotLinkDesign.Typography.footnote)
                    .foregroundStyle(SpotLinkDesign.Colors.tint)
            }
            .padding(SpotLinkDesign.Spacing.md)
        }
        .hideNavigationBar()
        .background(SpotLinkDesign.Colors.background)
    }

    private func requestReset() async {
        isLoading = true
        defer { isLoading = false }
        let env = AppEnvironment.current()
        let client = APIClient(baseURL: env.apiBaseURL, tokenProvider: NullTokenProvider())
        let authService = AuthService(apiClient: client, session: SessionManager.shared)
        do {
            try await authService.requestPasswordReset(email: email)
            isSuccess = true
        } catch let apiError as APIError {
            errorMessage = apiError.userFacingMessage
        } catch {
            errorMessage = "Greska. Pokusajte ponovo."
        }
    }
}
