import SwiftUI

@MainActor
public final class AddVehicleViewModel: ObservableObject {
    @Published public var type: VehicleType
    @Published public var licensePlate: String = ""
    @Published public var nickname: String = ""
    @Published public var make: String = ""
    @Published public var model: String = ""
    @Published public var color: String = ""
    @Published public var heightMeters: String = ""
    @Published public var lengthMeters: String = ""
    @Published public var evCapable: Bool = false
    @Published public private(set) var isSubmitting = false
    @Published public private(set) var errorMessage: String?

    private let service: VehicleService

    public init(service: VehicleService, defaultType: VehicleType = .car) {
        self.service = service
        self.type = defaultType
    }

    public func clearError() {
        errorMessage = nil
    }

    func buildRequest() throws -> VehicleUpsertRequest {
        let normalizedLicensePlate = Self.normalizeLicensePlate(licensePlate)
        guard let normalizedLicensePlate else {
            throw APIError.validation(APIErrorContext(message: "Unesite vazecu registraciju."))
        }

        return VehicleUpsertRequest(
            type: type,
            nickname: Self.normalizeText(nickname),
            make: Self.normalizeText(make),
            model: Self.normalizeText(model),
            color: Self.normalizeText(color),
            licensePlate: normalizedLicensePlate,
            heightMeters: try Self.parseDimension(heightMeters, fieldName: "Visina"),
            lengthMeters: try Self.parseDimension(lengthMeters, fieldName: "Duzina"),
            evCapable: evCapable
        )
    }

    public func submit() async throws -> VehicleProfile {
        guard !isSubmitting else {
            throw CancellationError()
        }

        errorMessage = nil

        let request: VehicleUpsertRequest
        do {
            request = try buildRequest()
        } catch let error as APIError {
            errorMessage = error.userFacingMessage
            throw error
        }

        isSubmitting = true
        defer { isSubmitting = false }

        do {
            return try await service.createVehicle(request)
        } catch let error as APIError {
            errorMessage = error.userFacingMessageWithReference
            throw error
        } catch {
            errorMessage = "Neuspesno dodavanje vozila. Pokusajte ponovo."
            throw error
        }
    }

    private static func normalizeText(_ value: String) -> String? {
        let normalized = value
            .split(whereSeparator: \.isWhitespace)
            .map(String.init)
            .joined(separator: " ")
            .trimmingCharacters(in: .whitespacesAndNewlines)

        return normalized.isEmpty ? nil : normalized
    }

    private static func normalizeLicensePlate(_ value: String) -> String? {
        guard let normalized = normalizeText(value)?.uppercased() else {
            return nil
        }

        let allowedScalars = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: " -/."))
        guard normalized.unicodeScalars.allSatisfy({ allowedScalars.contains($0) }) else {
            return nil
        }

        let alphanumericCount = normalized.unicodeScalars.filter { CharacterSet.alphanumerics.contains($0) }.count
        guard alphanumericCount >= 2 else {
            return nil
        }

        return normalized
    }

    private static func parseDimension(_ rawValue: String, fieldName: String) throws -> Double? {
        guard let normalized = normalizeText(rawValue) else {
            return nil
        }

        let decimalValue = normalized.replacingOccurrences(of: ",", with: ".")
        guard let parsedValue = Double(decimalValue), parsedValue > 0 else {
            let error = APIError.validation(APIErrorContext(message: "\(fieldName) mora biti pozitivan broj."))
            throw error
        }

        return parsedValue
    }
}

public struct AddVehicleView: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var viewModel: AddVehicleViewModel

    private let onCreated: ((VehicleProfile) -> Void)?

    public init(
        service: VehicleService,
        defaultType: VehicleType = .car,
        onCreated: ((VehicleProfile) -> Void)? = nil
    ) {
        _viewModel = StateObject(wrappedValue: AddVehicleViewModel(service: service, defaultType: defaultType))
        self.onCreated = onCreated
    }

    public var body: some View {
        Form {
            if let error = viewModel.errorMessage {
                ErrorBanner(error) { viewModel.clearError() }
                    .listRowBackground(Color.clear)
            }

            Section {
                Picker("Tip vozila", selection: $viewModel.type) {
                    ForEach(VehicleType.allCases, id: \.self) { vehicleType in
                        Text(vehicleType.displayName)
                            .tag(vehicleType)
                    }
                }
                .pickerStyle(.menu)
                .accessibilityLabel("Tip vozila")

                TextField("Registracija", text: $viewModel.licensePlate)
                    .spotlinkVehicleTextInput(capitalization: .characters)
                    .accessibilityLabel("Registracija vozila")
            } header: {
                Text("Spremno za rezervaciju")
            } footer: {
                Text("Tip vozila i registracija su dovoljni da nastavite do rezervacije.")
            }

            Section("Detalji za laksi ulaz") {
                TextField("Nadimak", text: $viewModel.nickname)
                    .spotlinkVehicleTextInput(capitalization: .words)
                    .accessibilityLabel("Nadimak vozila")

                TextField("Marka", text: $viewModel.make)
                    .spotlinkVehicleTextInput(capitalization: .words)
                    .accessibilityLabel("Marka vozila")

                TextField("Model", text: $viewModel.model)
                    .spotlinkVehicleTextInput(capitalization: .words)
                    .accessibilityLabel("Model vozila")

                TextField("Boja", text: $viewModel.color)
                    .spotlinkVehicleTextInput(capitalization: .words)
                    .accessibilityLabel("Boja vozila")

                Toggle("EV vozilo", isOn: $viewModel.evCapable)
                    .accessibilityLabel("EV vozilo")
            }

            Section {
                TextField("Visina u metrima", text: $viewModel.heightMeters)
                    .spotlinkDecimalInput()
                    .accessibilityLabel("Visina u metrima")

                TextField("Duzina u metrima", text: $viewModel.lengthMeters)
                    .spotlinkDecimalInput()
                    .accessibilityLabel("Duzina u metrima")
            } header: {
                Text("Dimenzije za fit proveru")
            } footer: {
                Text("Dodajte dimenzije ako lokacija ima limit visine ili parkirate vece vozilo.")
            }
        }
        .navigationTitle("Dodaj vozilo")
        .spotlinkInlineNavigationTitle()
        .interactiveDismissDisabled(viewModel.isSubmitting)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button("Odustani") {
                    dismiss()
                }
                .disabled(viewModel.isSubmitting)
                .accessibilityLabel("Odustani od dodavanja vozila")
            }

            ToolbarItem(placement: SpotLinkToolbarPlacement.trailing) {
                Button {
                    Task {
                        do {
                            let createdVehicle = try await viewModel.submit()
                            onCreated?(createdVehicle)
                            dismiss()
                        } catch {
                            // Greska je vec prikazana kroz view model.
                        }
                    }
                } label: {
                    if viewModel.isSubmitting {
                        ProgressView()
                    } else {
                        Text("Sacuvaj")
                    }
                }
                .disabled(viewModel.isSubmitting)
                .accessibilityLabel("Sacuvaj vozilo")
            }
        }
    }
}

private extension View {
    @ViewBuilder
    func spotlinkVehicleTextInput(capitalization: SpotLinkVehicleTextCapitalization) -> some View {
        #if os(iOS)
        self
            .textInputAutocapitalization(capitalization == .characters ? .characters : .words)
            .autocorrectionDisabled()
        #else
        self
        #endif
    }

    @ViewBuilder
    func spotlinkDecimalInput() -> some View {
        #if os(iOS)
        self.keyboardType(.decimalPad)
        #else
        self
        #endif
    }
}

private enum SpotLinkVehicleTextCapitalization {
    case words
    case characters
}