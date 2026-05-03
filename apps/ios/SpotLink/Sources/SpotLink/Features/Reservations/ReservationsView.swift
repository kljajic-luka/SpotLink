import SwiftUI

// MARK: - Reservations View Model

@MainActor
public final class ReservationsViewModel: ObservableObject {

    @Published public private(set) var reservations: [Reservation] = []
    @Published public private(set) var isLoading: Bool = false
    @Published public private(set) var errorMessage: String? = nil
    @Published public private(set) var hasMore: Bool = false
    @Published public private(set) var selectedStatus: ReservationStatus? = nil

    private let service: ReservationService
    private var currentPage = 0

    public init(service: ReservationService) {
        self.service = service
    }

    public func loadReservations(reset: Bool = false) async {
        if reset { currentPage = 0; reservations = [] }
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        do {
            let page = try await service.listMine(page: currentPage)
            if reset {
                reservations = page.content
            } else {
                reservations.append(contentsOf: page.content)
            }
            hasMore = page.hasMore
            currentPage += 1
        } catch let error as APIError {
            errorMessage = error.userFacingMessage
        } catch {
            errorMessage = "Greska pri ucitavanju rezervacija."
        }
    }

    public func cancel(_ reservationId: String) async {
        do {
            let updated = try await service.cancel(reservationId)
            if let idx = reservations.firstIndex(where: { $0.id == reservationId }) {
                reservations[idx] = updated
            }
        } catch let error as APIError {
            errorMessage = error.userFacingMessage
        } catch {
            errorMessage = "Greska pri otkazivanju rezervacije."
        }
    }

    public func clearError() { errorMessage = nil }

    public var filteredReservations: [Reservation] {
        guard let filter = selectedStatus else { return reservations }
        return reservations.filter { $0.status == filter }
    }
}

// MARK: - Reservations View

public struct ReservationsView: View {
    @StateObject private var viewModel: ReservationsViewModel
    private let service: ReservationService
    private let locationService: LocationService
    private let supportService: SupportService

    public init(service: ReservationService, locationService: LocationService, supportService: SupportService) {
        _viewModel = StateObject(wrappedValue: ReservationsViewModel(service: service))
        self.service = service
        self.locationService = locationService
        self.supportService = supportService
    }

    public var body: some View {
        Group {
            if viewModel.isLoading && viewModel.reservations.isEmpty {
                VStack { ForEach(0..<4, id: \.self) { _ in SkeletonRow() } }.padding()
            } else if viewModel.filteredReservations.isEmpty {
                EmptyStateView(
                    icon: "calendar",
                    title: "Nema rezervacija",
                    message: "Rezervacije ce se prikazati ovde nakon prve rezervacije.")
            } else {
                List {
                    if let error = viewModel.errorMessage {
                        ErrorBanner(error) { viewModel.clearError() }
                            .listRowBackground(Color.clear)
                    }
                    ForEach(viewModel.filteredReservations) { reservation in
                        NavigationLink {
                            ReservationDetailView(
                                reservationId: reservation.id,
                                reservationService: service,
                                locationService: locationService,
                                supportService: supportService,
                                reservation: reservation
                            )
                        } label: {
                            ReservationRow(reservation: reservation)
                        }
                    }
                    if viewModel.hasMore {
                        ProgressView().frame(maxWidth: .infinity)
                            .task { await viewModel.loadReservations() }
                    }
                }
                .spotlinkListStyle()
                .refreshable { await viewModel.loadReservations(reset: true) }
            }
        }
        .navigationTitle("Rezervacije")
        .task { await viewModel.loadReservations(reset: true) }
    }
}

struct ReservationRow: View {
    let reservation: Reservation

    var body: some View {
        VStack(alignment: .leading, spacing: SpotLinkDesign.Spacing.sm) {
            HStack {
                StatusBadge(status: reservation.status)
                Spacer()
                Text(reservation.totalAmountFormatted)
                    .font(SpotLinkDesign.Typography.headline)
                    .foregroundStyle(SpotLinkDesign.Colors.label)
            }
            HStack(spacing: SpotLinkDesign.Spacing.xs) {
                Image(systemName: "calendar")
                    .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                    .accessibilityHidden(true)
                Text(dateRange)
                    .font(SpotLinkDesign.Typography.caption)
                    .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
            }
            Text("Booking code: \(reservation.displayBookingCode)")
                .font(SpotLinkDesign.Typography.caption)
                .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
            Text(reservation.paymentMode.displayName)
                .font(SpotLinkDesign.Typography.caption)
                .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
        }
        .padding(.vertical, SpotLinkDesign.Spacing.sm)
        .accessibilityElement(children: .combine)
    }

    private var dateRange: String {
        "\(formatReservationDateTime(reservation.startsAt, timezone: reservation.timezone)) - \(formatReservationDateTime(reservation.endsAt, timezone: reservation.timezone))"
    }
}

struct StatusBadge: View {
    let status: ReservationStatus

    var body: some View {
        Text(status.displayName)
            .font(SpotLinkDesign.Typography.caption2.bold())
            .padding(.horizontal, SpotLinkDesign.Spacing.sm)
            .padding(.vertical, 4)
            .background(statusColor.opacity(0.15))
            .foregroundStyle(statusColor)
            .clipShape(Capsule())
            .accessibilityLabel("Status: \(status.displayName)")
    }

    private var statusColor: Color {
        switch status {
        case .draft:          return .gray
        case .pendingPayment: return .orange
        case .pendingOperatorConfirmation: return .orange
        case .confirmed:      return .blue
        case .active:         return .green
        case .completed:      return .gray
        case .cancelled:      return .red
        case .rejected:       return .red
        case .expired:        return .gray
        case .disputed:       return .red
        case .noShow:         return .orange
        }
    }
}
