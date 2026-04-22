import SwiftUI

// MARK: - Support View Model

@MainActor
public final class SupportViewModel: ObservableObject {

    @Published public private(set) var tickets: [SupportTicket] = []
    @Published public private(set) var isLoading: Bool = false
    @Published public private(set) var errorMessage: String? = nil

    private let service: SupportService

    public init(service: SupportService) {
        self.service = service
    }

    public func loadTickets() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        do {
            let page = try await service.listTickets()
            tickets = page.content
        } catch let error as APIError {
            errorMessage = error.userFacingMessage
        } catch {
            errorMessage = "Greska pri ucitavanju tiketa."
        }
    }

    public func clearError() { errorMessage = nil }
}

// MARK: - Support Tickets View

public struct SupportTicketsView: View {
    @StateObject private var viewModel: SupportViewModel
    @State private var showCreateSheet = false

    public init(service: SupportService) {
        _viewModel = StateObject(wrappedValue: SupportViewModel(service: service))
    }

    public var body: some View {
        Group {
            if viewModel.isLoading {
                VStack { ForEach(0..<3, id: \.self) { _ in SkeletonRow() } }.padding()
            } else if viewModel.tickets.isEmpty {
                EmptyStateView(
                    icon: "questionmark.circle.fill",
                    title: "Nema tiketa",
                    message: "Kreirajte tiket ako imate pitanje ili problem.",
                    actionTitle: "Novi tiket") { showCreateSheet = true }
            } else {
                List {
                    if let error = viewModel.errorMessage {
                        ErrorBanner(error) { viewModel.clearError() }.listRowBackground(Color.clear)
                    }
                    ForEach(viewModel.tickets) { ticket in
                        NavigationLink {
                            Text("Tiket detalji – dolazi u sledecoj fazi")
                                .navigationTitle(ticket.subject)
                        } label: {
                            TicketRow(ticket: ticket)
                        }
                    }
                }
                .spotlinkListStyle()
                .refreshable { await viewModel.loadTickets() }
            }
        }
        .navigationTitle("Podrska")
        .toolbar {
            ToolbarItem(placement: SpotLinkToolbarPlacement.trailing) {
                Button { showCreateSheet = true } label: {
                    Image(systemName: "plus")
                }
                .accessibilityLabel("Novi tiket")
            }
        }
        .task { await viewModel.loadTickets() }
        .sheet(isPresented: $showCreateSheet) {
            Text("Forma za kreiranje tiketa – dolazi u sledecoj fazi")
                .presentationDetents([.medium])
        }
    }
}

struct TicketRow: View {
    let ticket: SupportTicket

    var body: some View {
        VStack(alignment: .leading, spacing: SpotLinkDesign.Spacing.xs) {
            HStack {
                Text(ticket.subject)
                    .font(SpotLinkDesign.Typography.headline)
                    .lineLimit(1)
                Spacer()
                ticketStatusBadge
            }
            Text(ticket.category.displayName)
                .font(SpotLinkDesign.Typography.caption)
                .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
        }
        .padding(.vertical, SpotLinkDesign.Spacing.xs)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(ticket.subject), \(ticket.status.displayName)")
    }

    private var ticketStatusBadge: some View {
        Text(ticket.status.displayName)
            .font(SpotLinkDesign.Typography.caption2.bold())
            .padding(.horizontal, SpotLinkDesign.Spacing.sm)
            .padding(.vertical, 3)
            .background(statusColor.opacity(0.15))
            .foregroundStyle(statusColor)
            .clipShape(Capsule())
    }

    private var statusColor: Color {
        switch ticket.status {
        case .open:       return .blue
        case .inProgress: return .orange
        case .resolved:   return .green
        case .closed:     return .gray
        }
    }
}
