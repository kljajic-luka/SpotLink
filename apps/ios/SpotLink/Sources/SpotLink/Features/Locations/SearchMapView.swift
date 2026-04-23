#if canImport(MapboxMaps)
import MapboxMaps
#endif
#if canImport(MapKit)
import MapKit
#endif
import SwiftUI

// MARK: - Search Map View

/// Glavni ekran za pretragu parkinga.
/// Prikazuje MapKit kartu sa pinovima i listom rezultata ispod.
/// Podrazumevani centar – Beograd (44.8125, 20.4612) kada lokacija nije dostupna.
public struct SearchMapView: View {

    @ObservedObject private var viewModel: SearchMapViewModel
    @EnvironmentObject private var appContainer: SpotLinkAppContainer

    @State private var showResultsList = false

    public init(viewModel: SearchMapViewModel) {
        _viewModel = ObservedObject(wrappedValue: viewModel)
    }

    public var body: some View {
        ZStack(alignment: .top) {
            // Karta (pozadina)
            mapLayer

            // Gornja traka sa pretragom
            VStack(spacing: 0) {
                searchBarOverlay

                Spacer()

                // Panel sa rezultatima na dnu
                resultsPanel
            }

            // Dugme Blizu mene – desno gore
            nearMeButton
        }
        .ignoresSafeArea(edges: .bottom)
        .alert(
            "Lokacija je onemogucena",
            isPresented: $viewModel.locationPermissionDenied
        ) {
            Button("Podesavanja") {
                openAppSettings()
            }
            Button("Otkazati", role: .cancel) {}
        } message: {
            Text("Omogucite pristup lokaciji u Podesavanjima da biste koristili pretragu u blizini.")
        }
        .sheet(item: $viewModel.selectedResult) { result in
            NavigationStack {
                LocationPreviewSheet(
                    result: result,
                    searchStartsAt: viewModel.searchStartsAt,
                    searchEndsAt: viewModel.searchEndsAt
                )
            }
            .presentationDetents([.medium, .large])
        }
        .onAppear {
            viewModel.searchWithCurrentCenter()
        }
    }

    // MARK: - Mapa

    private var mapLayer: some View {
        Group {
            switch appContainer.mapProvider {
            case .mapbox:
                #if canImport(MapboxMaps)
                MapboxMapLayer(viewModel: viewModel)
                #else
                MapKitMapLayer(viewModel: viewModel)
                #endif
            case .mapKitFallback:
                MapKitMapLayer(viewModel: viewModel)
            }
        }
    }

    // MARK: - Pretraga

    private var searchBarOverlay: some View {
        VStack(spacing: SpotLinkDesign.Spacing.sm) {
            HStack(spacing: SpotLinkDesign.Spacing.sm) {
                Image(systemName: "magnifyingglass")
                    .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                TextField("Pretrazi parking...", text: $viewModel.query)
                    .submitLabel(.search)
                    .onSubmit { viewModel.searchManual() }
                if !viewModel.query.isEmpty {
                    Button {
                        viewModel.query = ""
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                    }
                }
            }
            .padding(.horizontal, SpotLinkDesign.Spacing.md)
            .padding(.vertical, SpotLinkDesign.Spacing.sm + 2)
            .background(SpotLinkDesign.Colors.background)
            .clipShape(RoundedRectangle(cornerRadius: SpotLinkDesign.Radius.lg))
            .shadow(color: .black.opacity(0.10), radius: 8, x: 0, y: 2)

            // Kompaktna kontrola vremenskog okna
            TimeWindowControl(
                startsAt: $viewModel.searchStartsAt,
                endsAt: $viewModel.searchEndsAt,
                onChanged: { viewModel.searchWithCurrentCenter() }
            )
        }
        .padding(.horizontal, SpotLinkDesign.Spacing.md)
        .padding(.top, SpotLinkDesign.Spacing.sm)
    }

    // MARK: - Dugme blizu mene

    private var nearMeButton: some View {
        VStack {
            Spacer()
            HStack {
                Spacer()
                Button {
                    viewModel.searchNearMe()
                } label: {
                    Label("Blizu mene", systemImage: "location.fill")
                        .font(.subheadline.weight(.semibold))
                        .padding(.horizontal, SpotLinkDesign.Spacing.md)
                        .padding(.vertical, SpotLinkDesign.Spacing.sm + 2)
                        .background(SpotLinkDesign.Colors.background)
                        .clipShape(Capsule())
                        .shadow(color: .black.opacity(0.12), radius: 8, x: 0, y: 2)
                }
                .padding(.trailing, SpotLinkDesign.Spacing.md)
                .padding(.bottom, 310) // iznad panela sa rezultatima
            }
        }
    }

    // MARK: - Panel sa rezultatima

    private var resultsPanel: some View {
        VStack(spacing: 0) {
            // Indikator za povlacenje
            Capsule()
                .fill(Color.secondary.opacity(0.4))
                .frame(width: 36, height: 4)
                .padding(.top, SpotLinkDesign.Spacing.sm)

            // Zaglavlje panela
            HStack {
                resultsPanelTitle
                Spacer()
                if case .loading = viewModel.state {
                    ProgressView()
                        .scaleEffect(0.8)
                }
            }
            .padding(.horizontal, SpotLinkDesign.Spacing.md)
            .padding(.vertical, SpotLinkDesign.Spacing.sm)

            // Sadrzaj
            Group {
                switch viewModel.state {
                case .idle:
                    EmptyView()
                case .loading:
                    Color.clear.frame(height: 80)
                case .results(let items):
                    resultsList(items)
                case .empty:
                    emptyState
                case .error(let msg):
                    errorState(message: msg)
                case .offline:
                    offlineState
                }
            }
        }
        .background(SpotLinkDesign.Colors.background)
        .clipShape(RoundedRectangle(cornerRadius: SpotLinkDesign.Radius.xl, style: .continuous))
        .shadow(color: .black.opacity(0.12), radius: 12, x: 0, y: -4)
        .frame(maxHeight: 300)
    }

    private var resultsPanelTitle: some View {
        Group {
            switch viewModel.state {
            case .results(let items):
                Text("\(items.count) mesta u blizini")
                    .font(SpotLinkDesign.Typography.subheadline.weight(.semibold))
            case .empty:
                Text("Nema rezultata")
                    .font(SpotLinkDesign.Typography.subheadline.weight(.semibold))
            case .error:
                Text("Greska pri ucitavanju")
                    .font(SpotLinkDesign.Typography.subheadline.weight(.semibold))
            case .offline:
                Text("Nema internet veze")
                    .font(SpotLinkDesign.Typography.subheadline.weight(.semibold))
            default:
                Text("Pretraga parkinga")
                    .font(SpotLinkDesign.Typography.subheadline.weight(.semibold))
            }
        }
    }

    private func resultsList(_ items: [LocationSearchResult]) -> some View {
        ScrollView {
            LazyVStack(spacing: 0) {
                ForEach(items) { result in
                    SearchResultRow(result: result)
                        .onTapGesture {
                            viewModel.selectedResult = result
                        }
                    Divider().padding(.leading, SpotLinkDesign.Spacing.md)
                }
            }
        }
    }

    private var emptyState: some View {
        VStack(spacing: SpotLinkDesign.Spacing.sm) {
            Image(systemName: "magnifyingglass.circle")
                .font(.system(size: 40))
                .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
            Text("Nema parking mesta u ovoj oblasti.")
                .font(SpotLinkDesign.Typography.body)
                .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                .multilineTextAlignment(.center)
        }
        .padding(SpotLinkDesign.Spacing.lg)
    }

    private func errorState(message: String) -> some View {
        VStack(spacing: SpotLinkDesign.Spacing.sm) {
            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 40))
                .foregroundStyle(SpotLinkDesign.Colors.warning)
            Text(message)
                .font(SpotLinkDesign.Typography.callout)
                .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                .multilineTextAlignment(.center)
            Button("Pokusaj ponovo") {
                viewModel.searchWithCurrentCenter()
            }
            .buttonStyle(.borderedProminent)
        }
        .padding(SpotLinkDesign.Spacing.lg)
    }

    private var offlineState: some View {
        VStack(spacing: SpotLinkDesign.Spacing.sm) {
            Image(systemName: "wifi.slash")
                .font(.system(size: 40))
                .foregroundStyle(SpotLinkDesign.Colors.error)
            Text("Proverite internet vezu i pokusajte ponovo.")
                .font(SpotLinkDesign.Typography.callout)
                .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                .multilineTextAlignment(.center)
            Button("Pokusaj ponovo") {
                viewModel.searchWithCurrentCenter()
            }
            .buttonStyle(.borderedProminent)
        }
        .padding(SpotLinkDesign.Spacing.lg)
    }

    // MARK: - Pomocne funkcije

    private func openAppSettings() {
        #if os(iOS)
        if let url = URL(string: UIApplication.openSettingsURLString) {
            UIApplication.shared.open(url)
        }
        #endif
    }
}

// MARK: - Platform-specific map layer

#if canImport(MapboxMaps)
private struct MapboxMapLayer: View {
    @ObservedObject var viewModel: SearchMapViewModel
    @State private var viewport: MapboxMaps.Viewport = .camera(
        center: .init(
            latitude: SearchMapViewModel.defaultCenter.latitude,
            longitude: SearchMapViewModel.defaultCenter.longitude
        ),
        zoom: 12
    )

    var body: some View {
        MapboxMaps.Map(viewport: $viewport) {
            if case .results(let items) = viewModel.state {
                ForEvery(items) { result in
                    MapViewAnnotation(
                        coordinate: .init(
                            latitude: result.location.coordinates.latitude,
                            longitude: result.location.coordinates.longitude
                        )
                    ) {
                        MapPinView(
                            result: result,
                            isSelected: viewModel.selectedResult?.id == result.id
                        )
                        .onTapGesture {
                            viewModel.selectedResult = result
                        }
                    }
                }
            }
        }
        .mapStyle(.standard)
        .onCameraChanged { camera in
            let center = camera.cameraState.center
            viewModel.mapCenter = GeoCoordinates(latitude: center.latitude, longitude: center.longitude)
        }
        .onChange(of: viewModel.mapCenter) { _, newCenter in
            withAnimation {
                viewport = .camera(
                    center: .init(latitude: newCenter.latitude, longitude: newCenter.longitude),
                    zoom: 12
                )
            }
        }
    }
}
#endif

#if canImport(MapKit)
private struct MapKitMapLayer: View {
    @ObservedObject var viewModel: SearchMapViewModel
    @State private var mapPosition: MapCameraPosition = .region(MKCoordinateRegion(
        center: CLLocationCoordinate2D(
            latitude: SearchMapViewModel.defaultCenter.latitude,
            longitude: SearchMapViewModel.defaultCenter.longitude
        ),
        span: MKCoordinateSpan(latitudeDelta: 0.08, longitudeDelta: 0.08)
    ))

    var body: some View {
        Map(position: $mapPosition) {
            if case .results(let items) = viewModel.state {
                ForEach(items) { result in
                    let coord = CLLocationCoordinate2D(
                        latitude: result.location.coordinates.latitude,
                        longitude: result.location.coordinates.longitude
                    )
                    Annotation(result.location.name, coordinate: coord) {
                        MapPinView(
                            result: result,
                            isSelected: viewModel.selectedResult?.id == result.id
                        )
                        .onTapGesture {
                            viewModel.selectedResult = result
                        }
                    }
                }
            }
        }
        .mapStyle(.standard)
        .mapControls {
            MapCompass()
            MapScaleView()
        }
        .onMapCameraChange { context in
            let center = context.region.center
            viewModel.mapCenter = GeoCoordinates(latitude: center.latitude, longitude: center.longitude)
        }
        .onChange(of: viewModel.mapCenter) { _, newCenter in
            withAnimation {
                mapPosition = .region(MKCoordinateRegion(
                    center: CLLocationCoordinate2D(latitude: newCenter.latitude, longitude: newCenter.longitude),
                    span: MKCoordinateSpan(latitudeDelta: 0.08, longitudeDelta: 0.08)
                ))
            }
        }
    }
}
#endif

// MARK: - Map Pin View

private struct MapPinView: View {
    let result: LocationSearchResult
    let isSelected: Bool

    var body: some View {
        VStack(spacing: 2) {
            ZStack {
                Capsule()
                    .fill(isSelected ? SpotLinkDesign.Colors.brand : SpotLinkDesign.Colors.background)
                    .shadow(color: .black.opacity(0.15), radius: 4, x: 0, y: 2)

                if let price = result.formattedStartingPrice {
                    Text(price)
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(isSelected ? .white : SpotLinkDesign.Colors.brand)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                } else {
                    Image(systemName: "parkingsign")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(isSelected ? .white : SpotLinkDesign.Colors.brand)
                        .padding(6)
                }
            }
            .frame(height: 30)
            Image(systemName: "arrowtriangle.down.fill")
                .font(.system(size: 6))
                .foregroundStyle(isSelected ? SpotLinkDesign.Colors.brand : SpotLinkDesign.Colors.background)
        }
        .scaleEffect(isSelected ? 1.1 : 1.0)
        .animation(.spring(duration: 0.2), value: isSelected)
    }
}

// MARK: - Search Result Row

private struct SearchResultRow: View {
    let result: LocationSearchResult

    var body: some View {
        HStack(spacing: SpotLinkDesign.Spacing.md) {
            // Ikonica
            ZStack {
                RoundedRectangle(cornerRadius: SpotLinkDesign.Radius.sm)
                    .fill(SpotLinkDesign.Colors.brand.opacity(0.12))
                    .frame(width: 44, height: 44)
                Image(systemName: result.location.accessType.systemIcon)
                    .foregroundStyle(SpotLinkDesign.Colors.brand)
            }

            // Tekst
            VStack(alignment: .leading, spacing: 2) {
                Text(result.location.name)
                    .font(SpotLinkDesign.Typography.subheadline.weight(.semibold))
                    .lineLimit(1)
                Text(result.location.address.formattedAddress ?? result.location.address.line1)
                    .font(SpotLinkDesign.Typography.caption)
                    .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                    .lineLimit(1)
            }

            Spacer()

            // Cena i udaljenost
            VStack(alignment: .trailing, spacing: 2) {
                if let price = result.formattedStartingPrice {
                    Text(price)
                        .font(SpotLinkDesign.Typography.subheadline.weight(.bold))
                        .foregroundStyle(SpotLinkDesign.Colors.brand)
                }
                if let dist = result.formattedDistance {
                    Text(dist)
                        .font(SpotLinkDesign.Typography.caption)
                        .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                }
            }
        }
        .padding(.horizontal, SpotLinkDesign.Spacing.md)
        .padding(.vertical, SpotLinkDesign.Spacing.sm + 2)
        .contentShape(Rectangle())
    }
}

// MARK: - Time Window Control

private struct TimeWindowControl: View {
    @Binding var startsAt: Date
    @Binding var endsAt: Date
    let onChanged: () -> Void

    var body: some View {
        HStack(spacing: SpotLinkDesign.Spacing.sm) {
            Image(systemName: "clock")
                .font(.caption)
                .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)

            DatePicker("Pocetak", selection: $startsAt, displayedComponents: [.date, .hourAndMinute])
                .labelsHidden()
                .onChange(of: startsAt) { _, _ in onChanged() }

            Image(systemName: "arrow.right")
                .font(.caption)
                .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)

            DatePicker("Kraj", selection: $endsAt, displayedComponents: [.date, .hourAndMinute])
                .labelsHidden()
                .onChange(of: endsAt) { _, _ in onChanged() }
        }
        .padding(.horizontal, SpotLinkDesign.Spacing.md)
        .padding(.vertical, SpotLinkDesign.Spacing.sm)
        .background(SpotLinkDesign.Colors.background.opacity(0.95))
        .clipShape(RoundedRectangle(cornerRadius: SpotLinkDesign.Radius.md))
        .shadow(color: .black.opacity(0.08), radius: 4, x: 0, y: 1)
    }
}

// MARK: - Location Preview Sheet

private struct LocationPreviewSheet: View {
    let result: LocationSearchResult
    let searchStartsAt: Date
    let searchEndsAt: Date

    var body: some View {
        VStack(alignment: .leading, spacing: SpotLinkDesign.Spacing.md) {
            // Zaglavlje
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(result.location.name)
                        .font(SpotLinkDesign.Typography.title3.weight(.bold))
                    Text(result.location.address.formattedAddress ?? result.location.address.line1)
                        .font(SpotLinkDesign.Typography.subheadline)
                        .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                }
                Spacer()
                if let dist = result.formattedDistance {
                    Text(dist)
                        .font(SpotLinkDesign.Typography.callout.weight(.semibold))
                        .foregroundStyle(SpotLinkDesign.Colors.brand)
                }
            }

            Divider()

            // Kljucne informacije
            HStack(spacing: SpotLinkDesign.Spacing.lg) {
                if let price = result.formattedStartingPrice {
                    infoChip(icon: "dollarsign.circle", label: "od \(price)")
                }
                infoChip(
                    icon: result.location.accessType.systemIcon,
                    label: result.location.accessType.displayName
                )
                if let resource = result.resources.first {
                    infoChip(icon: "checkmark.seal", label: resource.confirmationMode.displayName)
                }
                infoChip(
                    icon: "car.2",
                    label: "\(result.availableResourceCount) dostupno"
                )
            }

            if let resource = result.resources.first {
                VStack(alignment: .leading, spacing: SpotLinkDesign.Spacing.xs) {
                    Text("Poverenje i pristup")
                        .font(SpotLinkDesign.Typography.footnote.weight(.semibold))
                        .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                        .textCase(.uppercase)
                    Text("Garantovana rezervacija kroz partner inventar. \(resource.capacitySummary).")
                        .font(SpotLinkDesign.Typography.callout)
                    Text("Kasnjenje do 15 minuta je tolerisano kao privremeni placeholder dok partner pravila ne budu finalizovana.")
                        .font(SpotLinkDesign.Typography.footnote)
                        .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                }
            }

            if let notes = result.location.publicNotes, !notes.isBlank {
                VStack(alignment: .leading, spacing: SpotLinkDesign.Spacing.xs) {
                    Text("Napomena lokacije")
                        .font(SpotLinkDesign.Typography.footnote.weight(.semibold))
                        .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                        .textCase(.uppercase)
                    Text(notes)
                        .font(SpotLinkDesign.Typography.callout)
                }
            }

            // Lista resursa
            if !result.resources.isEmpty {
                Text("Dostupna mesta")
                    .font(SpotLinkDesign.Typography.footnote.weight(.semibold))
                    .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                    .textCase(.uppercase)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: SpotLinkDesign.Spacing.sm) {
                        ForEach(result.resources) { resource in
                            ResourceChip(resource: resource)
                        }
                    }
                }
            }

            // Dugme za rezervaciju
            NavigationLink {
                ReservationBookingFlowView(
                    result: result,
                    initialStartsAt: searchStartsAt,
                    initialEndsAt: searchEndsAt
                )
            } label: {
                Text("Rezervisi")
                    .font(SpotLinkDesign.Typography.headline)
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .tint(SpotLinkDesign.Colors.brand)
        }
        .padding(SpotLinkDesign.Spacing.lg)
        .navigationTitle("Detalji lokacije")
        .spotlinkInlineNavigationTitle()
    }

    private func infoChip(icon: String, label: String) -> some View {
        VStack(spacing: 4) {
            Image(systemName: icon)
                .foregroundStyle(SpotLinkDesign.Colors.brand)
            Text(label)
                .font(SpotLinkDesign.Typography.caption.weight(.medium))
                .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                .multilineTextAlignment(.center)
        }
    }
}

private struct ResourceChip: View {
    let resource: ParkingResource

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(resource.type.displayName)
                .font(SpotLinkDesign.Typography.caption.weight(.semibold))
            Text(resource.hourlyRateFormatted)
                .font(SpotLinkDesign.Typography.footnote)
                .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
        }
        .padding(.horizontal, SpotLinkDesign.Spacing.sm + 2)
        .padding(.vertical, SpotLinkDesign.Spacing.sm)
        .background(SpotLinkDesign.Colors.secondaryBG)
        .clipShape(RoundedRectangle(cornerRadius: SpotLinkDesign.Radius.sm))
    }
}
