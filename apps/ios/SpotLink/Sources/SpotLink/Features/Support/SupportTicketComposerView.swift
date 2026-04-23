import SwiftUI

@MainActor
public final class SupportTicketComposerViewModel: ObservableObject {
    @Published public var category: TicketCategory
    @Published public var subject: String
    @Published public var body: String
    @Published public private(set) var isSubmitting = false
    @Published public private(set) var errorMessage: String?

    private let reservationId: String?
    private let locationId: String?
    private let service: SupportService

    public init(
        service: SupportService,
        defaultCategory: TicketCategory,
        defaultSubject: String,
        initialBody: String,
        reservationId: String?,
        locationId: String?
    ) {
        self.service = service
        self.category = defaultCategory
        self.subject = defaultSubject
        self.body = initialBody
        self.reservationId = reservationId
        self.locationId = locationId
    }

    public func submit() async throws -> SupportTicket {
        let trimmedSubject = subject.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedBody = body.trimmingCharacters(in: .whitespacesAndNewlines)

        guard !trimmedSubject.isEmpty else {
            errorMessage = "Unesite naslov zahteva."
            throw APIError.validation(APIErrorContext(message: "Naslov je obavezan."))
        }

        guard !trimmedBody.isEmpty else {
            errorMessage = "Opisite problem ili pitanje."
            throw APIError.validation(APIErrorContext(message: "Opis je obavezan."))
        }

        isSubmitting = true
        errorMessage = nil
        defer { isSubmitting = false }

        do {
            return try await service.createTicket(
                CreateTicketRequest(
                    category: category,
                    subject: trimmedSubject,
                    body: trimmedBody,
                    reservationId: reservationId,
                    locationId: locationId
                )
            )
        } catch let error as APIError {
            errorMessage = error.userFacingMessage
            throw error
        } catch {
            errorMessage = "Neuspesno kreiranje tiketa. Pokusajte ponovo."
            throw error
        }
    }
}

public struct SupportTicketComposerView: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var viewModel: SupportTicketComposerViewModel

    private let onCreated: ((SupportTicket) -> Void)?

    public init(
        service: SupportService,
        defaultCategory: TicketCategory = .locationAccess,
        defaultSubject: String = "",
        initialBody: String = "",
        reservationId: String? = nil,
        locationId: String? = nil,
        onCreated: ((SupportTicket) -> Void)? = nil
    ) {
        _viewModel = StateObject(wrappedValue: SupportTicketComposerViewModel(
            service: service,
            defaultCategory: defaultCategory,
            defaultSubject: defaultSubject,
            initialBody: initialBody,
            reservationId: reservationId,
            locationId: locationId
        ))
        self.onCreated = onCreated
    }

    public var body: some View {
        Form {
            if let error = viewModel.errorMessage {
                ErrorBanner(error)
                    .listRowBackground(Color.clear)
            }

            Section("Kategorija") {
                Picker("Tema", selection: $viewModel.category) {
                    ForEach(TicketCategory.allCases, id: \.self) { category in
                        Text(category.displayName)
                            .tag(category)
                    }
                }
                .pickerStyle(.menu)
            }

            Section("Naslov") {
                TextField("Kratko opisite problem", text: $viewModel.subject)
            }

            Section("Poruka") {
                TextEditor(text: $viewModel.body)
                    .frame(minHeight: 180)
            }
        }
        .navigationTitle("Novi tiket")
        .spotlinkInlineNavigationTitle()
        .toolbar {
            ToolbarItem(placement: SpotLinkToolbarPlacement.trailing) {
                Button {
                    Task {
                        do {
                            let ticket = try await viewModel.submit()
                            onCreated?(ticket)
                            dismiss()
                        } catch {
                            // Greska je vec prikazana kroz view model.
                        }
                    }
                } label: {
                    if viewModel.isSubmitting {
                        ProgressView()
                    } else {
                        Text("Posalji")
                    }
                }
                .disabled(viewModel.isSubmitting)
            }
        }
    }
}