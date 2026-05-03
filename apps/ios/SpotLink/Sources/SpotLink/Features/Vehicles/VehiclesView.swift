import SwiftUI

// MARK: - Vehicles View Model

@MainActor
public final class VehiclesViewModel: ObservableObject {

    @Published public private(set) var vehicles: [VehicleProfile] = []
    @Published public private(set) var isLoading: Bool = false
    @Published public private(set) var errorMessage: String? = nil

    private let service: VehicleService

    public init(service: VehicleService) {
        self.service = service
    }

    public func loadVehicles() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        do {
            vehicles = try await service.listMyVehicles()
        } catch let error as APIError {
            errorMessage = error.userFacingMessage
        } catch {
            errorMessage = "Greska pri ucitavanju vozila."
        }
    }

    public func deleteVehicle(_ vehicleId: String) async {
        do {
            try await service.deleteVehicle(vehicleId)
            vehicles.removeAll { $0.id == vehicleId }
        } catch let error as APIError {
            errorMessage = error.userFacingMessage
        } catch {
            errorMessage = "Greska pri brisanju vozila."
        }
    }

    public func didCreateVehicle(_ vehicle: VehicleProfile) {
        vehicles.removeAll { $0.id == vehicle.id }
        vehicles.insert(vehicle, at: 0)
        errorMessage = nil
    }

    public func clearError() { errorMessage = nil }
}

// MARK: - Vehicles List View

public struct VehiclesView: View {
    @StateObject private var viewModel: VehiclesViewModel
    @State private var showAddVehicleSheet = false

    private let service: VehicleService

    public init(service: VehicleService) {
        _viewModel = StateObject(wrappedValue: VehiclesViewModel(service: service))
        self.service = service
    }

    public var body: some View {
        Group {
            if viewModel.isLoading {
                VStack { ForEach(0..<3, id: \.self) { _ in SkeletonRow() } }
                    .padding()
            } else if viewModel.vehicles.isEmpty {
                EmptyStateView(
                    icon: "car.fill",
                    title: "Nema vozila",
                    message: "Dodajte vozilo da biste ubrzali rezervaciju.",
                    actionTitle: "Dodaj vozilo") {
                        showAddVehicleSheet = true
                    }
            } else {
                List {
                    if let error = viewModel.errorMessage {
                        ErrorBanner(error) { viewModel.clearError() }
                            .listRowBackground(Color.clear)
                    }
                    ForEach(viewModel.vehicles) { vehicle in
                        VehicleRow(vehicle: vehicle)
                    }
                    .onDelete { indexSet in
                        Task {
                            for idx in indexSet {
                                await viewModel.deleteVehicle(viewModel.vehicles[idx].id)
                            }
                        }
                    }
                }
                .spotlinkListStyle()
                .refreshable { await viewModel.loadVehicles() }
            }
        }
        .navigationTitle("Vozila")
        .task { await viewModel.loadVehicles() }
        .toolbar {
            ToolbarItem(placement: SpotLinkToolbarPlacement.trailing) {
                Button {
                    showAddVehicleSheet = true
                } label: {
                    Image(systemName: "plus")
                }
                .accessibilityLabel("Dodaj vozilo")
            }
        }
        .sheet(isPresented: $showAddVehicleSheet) {
            NavigationStack {
                AddVehicleView(service: service) { createdVehicle in
                    viewModel.didCreateVehicle(createdVehicle)
                    Task { await viewModel.loadVehicles() }
                }
            }
            .presentationDetents([.large])
        }
    }
}

struct VehicleRow: View {
    let vehicle: VehicleProfile

    var body: some View {
        HStack(spacing: SpotLinkDesign.Spacing.md) {
            Image(systemName: vehicle.type.systemIcon)
                .font(.title2)
                .frame(width: 44, height: 44)
                .background(SpotLinkDesign.Colors.tint.opacity(0.1))
                .foregroundStyle(SpotLinkDesign.Colors.tint)
                .clipShape(RoundedRectangle(cornerRadius: SpotLinkDesign.Radius.sm))
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(primaryText)
                    .font(SpotLinkDesign.Typography.headline)
                if let secondaryText {
                    Text(secondaryText)
                        .font(SpotLinkDesign.Typography.caption)
                        .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                }
                if vehicle.evCapable {
                    Label("EV", systemImage: "bolt.fill")
                        .font(SpotLinkDesign.Typography.caption2)
                        .foregroundStyle(.green)
                }
            }
            Spacer()
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(accessibilitySummary)
    }

    private var primaryText: String {
        if let licensePlate = vehicle.licensePlate?.trimmingCharacters(in: .whitespacesAndNewlines), !licensePlate.isEmpty {
            return licensePlate
        }
        return vehicle.displayName
    }

    private var secondaryText: String? {
        let trimmedDisplayName = vehicle.displayName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedDisplayName.isEmpty, trimmedDisplayName != primaryText else {
            return nil
        }
        return trimmedDisplayName
    }

    private var accessibilitySummary: String {
        var parts = [primaryText]
        if let secondaryText {
            parts.append(secondaryText)
        }
        if vehicle.evCapable {
            parts.append("EV vozilo")
        }
        return parts.joined(separator: ", ")
    }
}
