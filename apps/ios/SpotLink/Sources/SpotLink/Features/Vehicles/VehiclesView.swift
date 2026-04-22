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

    public func clearError() { errorMessage = nil }
}

// MARK: - Vehicles List View

public struct VehiclesView: View {
    @StateObject private var viewModel: VehiclesViewModel

    public init(service: VehicleService) {
        _viewModel = StateObject(wrappedValue: VehiclesViewModel(service: service))
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
                    message: "Dodajte vozilo da biste ubrzali rezervaciju.")
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
            }
        }
        .navigationTitle("Vozila")
        .task { await viewModel.loadVehicles() }
        .toolbar {
            ToolbarItem(placement: SpotLinkToolbarPlacement.trailing) {
                Button {
                    // Otvara AddVehicleView – implementovati u sledecoj fazi
                } label: {
                    Image(systemName: "plus")
                }
                .accessibilityLabel("Dodaj vozilo")
            }
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
                Text(vehicle.displayName)
                    .font(SpotLinkDesign.Typography.headline)
                if let plate = vehicle.licensePlate {
                    Text(plate)
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
        .accessibilityLabel("\(vehicle.displayName)\(vehicle.licensePlate.map { ", \($0)" } ?? "")")
    }
}
