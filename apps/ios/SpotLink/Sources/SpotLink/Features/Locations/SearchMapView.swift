#if canImport(MapboxMaps)
import MapboxMaps
#endif
#if canImport(MapKit)
import MapKit
#endif
import SwiftUI

// MARK: - Search Map View

/// Glavni ekran za pretragu parkinga.
/// Mapa ostaje primarni sloj, dok se komande i rezultati adaptiraju na telefon i siri ekran.
public struct SearchMapView: View {

    @ObservedObject private var viewModel: SearchMapViewModel
    @EnvironmentObject private var appContainer: SpotLinkAppContainer
    @State private var hasStartedInitialSearch = false

    public init(viewModel: SearchMapViewModel) {
        _viewModel = ObservedObject(wrappedValue: viewModel)
    }

    public var body: some View {
        GeometryReader { geometry in
            let layout = SearchMapLayoutContext(
                size: geometry.size,
                safeAreaInsets: geometry.safeAreaInsets,
                state: viewModel.state
            )

            ZStack {
                mapLayer
                    .ignoresSafeArea()

                SearchMapViewportScrim()

                if layout.usesSidebar {
                    SearchMapSidebar(
                        query: $viewModel.query,
                        searchStartsAt: $viewModel.searchStartsAt,
                        searchEndsAt: $viewModel.searchEndsAt,
                        state: viewModel.state,
                        selectedResultID: viewModel.selectedResult?.id,
                        onSubmitQuery: submitQuery,
                        onSearchArea: searchCurrentArea,
                        onNearMe: searchNearMe,
                        onSelectResult: selectResult
                    )
                    .frame(width: layout.sidebarWidth)
                    .padding(.leading, layout.outerPadding)
                    .padding(.top, layout.topPadding)
                    .padding(.bottom, layout.bottomPadding)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                } else {
                    VStack(spacing: layout.compactStackSpacing) {
                        SearchMapCommandPanel(
                            mode: .compact,
                            query: $viewModel.query,
                            searchStartsAt: $viewModel.searchStartsAt,
                            searchEndsAt: $viewModel.searchEndsAt,
                            state: viewModel.state,
                            density: layout.panelDensity,
                            onSubmitQuery: submitQuery,
                            onSearchArea: searchCurrentArea,
                            onNearMe: searchNearMe
                        )
                        .padding(.top, layout.topPadding)
                        .padding(.horizontal, layout.outerPadding)

                        Spacer(minLength: 0)

                        SearchMapResultsPanel(
                            mode: .compact,
                            state: viewModel.state,
                            selectedResultID: viewModel.selectedResult?.id,
                            density: layout.panelDensity,
                            onRetry: searchCurrentArea,
                            onSelectResult: selectResult
                        )
                        .frame(maxHeight: layout.compactPanelHeight)
                        .padding(.horizontal, layout.outerPadding)
                        .padding(.bottom, layout.bottomPadding)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                }
            }
            .background(SpotLinkDesign.Colors.secondaryBG)
            .ignoresSafeArea(edges: .bottom)
        }
        .hideNavigationBar()
        .alert(
            "Lokacija je onemogucena",
            isPresented: $viewModel.locationPermissionDenied
        ) {
            Button("Podesavanja", action: openAppSettings)
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
            guard !hasStartedInitialSearch else { return }
            hasStartedInitialSearch = true
            viewModel.searchWithCurrentCenter()
        }
    }

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

    private func submitQuery() {
        if viewModel.query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            viewModel.searchWithCurrentCenter()
        } else {
            viewModel.searchManual()
        }
    }

    private func searchCurrentArea() {
        viewModel.searchWithCurrentCenter()
    }

    private func searchNearMe() {
        viewModel.searchNearMe()
    }

    private func selectResult(_ result: LocationSearchResult) {
        viewModel.selectedResult = result
    }

    private func openAppSettings() {
        #if os(iOS)
        if let url = URL(string: UIApplication.openSettingsURLString) {
            UIApplication.shared.open(url)
        }
        #endif
    }
}

// MARK: - Layout Context

struct SearchMapLayoutContext {
    let size: CGSize
    let safeAreaInsets: SwiftUI.EdgeInsets
    let state: SearchMapViewModel.State

    var usesSidebar: Bool {
        size.width >= 820 && size.height >= 480
    }

    var outerPadding: CGFloat {
        usesSidebar ? SpotLinkDesign.Spacing.lg : compactOuterPadding
    }

    var compactOuterPadding: CGFloat {
        size.width < 390 ? SpotLinkDesign.Spacing.sm + 2 : SpotLinkDesign.Spacing.md
    }

    var topPadding: CGFloat {
        safeAreaInsets.top + (usesSidebar ? SpotLinkDesign.Spacing.lg : SpotLinkDesign.Spacing.sm)
    }

    var bottomPadding: CGFloat {
        safeAreaInsets.bottom + SpotLinkDesign.Spacing.sm
    }

    var panelDensity: SearchMapPanelDensity {
        isCondensedCompact ? .condensed : .regular
    }

    var compactStackSpacing: CGFloat {
        isCondensedCompact ? SpotLinkDesign.Spacing.sm : SpotLinkDesign.Spacing.md
    }

    var sidebarWidth: CGFloat {
        min(max(size.width * 0.34, 368), 448)
    }

    var compactPanelHeight: CGFloat {
        let heightCap = min(size.height * (isCondensedCompact ? 0.34 : 0.48), isCondensedCompact ? 312 : 468)
        let minimum: CGFloat = isCondensedCompact ? 148 : 192

        func clamped(_ value: CGFloat) -> CGFloat {
            min(max(value, minimum), heightCap)
        }

        switch state {
        case .idle:
            return clamped(isCondensedCompact ? 148 : 184)
        case .loading:
            return clamped(isCondensedCompact ? 214 : 260)
        case .results(let items):
            let rowLimit = isCondensedCompact ? 2 : 4
            let estimatedRows = CGFloat(min(max(items.count, 2), rowLimit))
            let rowHeight: CGFloat = isCondensedCompact ? 110 : 128
            let chromeHeight: CGFloat = isCondensedCompact ? 68 : 92
            return clamped(estimatedRows * rowHeight + chromeHeight)
        case .empty, .error, .offline:
            return clamped(isCondensedCompact ? 206 : 240)
        }
    }

    private var isCondensedCompact: Bool {
        !usesSidebar && (size.height < 720 || size.width < 390)
    }
}

private struct SearchMapViewportScrim: View {
    var body: some View {
        VStack(spacing: 0) {
            LinearGradient(
                colors: [
                    Color.black.opacity(0.20),
                    Color.black.opacity(0.08),
                    Color.clear
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .frame(height: 170)

            Spacer(minLength: 0)

            LinearGradient(
                colors: [
                    Color.clear,
                    Color.black.opacity(0.06),
                    Color.black.opacity(0.12)
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .frame(height: 220)
        }
        .allowsHitTesting(false)
        .ignoresSafeArea()
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
    @State private var lastCameraCenter = SearchMapViewModel.defaultCenter

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
            let newCenter = GeoCoordinates(latitude: center.latitude, longitude: center.longitude)
            lastCameraCenter = newCenter
            if !viewModel.mapCenter.isApproximatelyEqual(to: newCenter) {
                viewModel.mapCenter = newCenter
            }
        }
        .onChange(of: viewModel.mapCenter) { _, newCenter in
            guard !newCenter.isApproximatelyEqual(to: lastCameraCenter) else { return }
            lastCameraCenter = newCenter
            withAnimation(.easeInOut(duration: 0.2)) {
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
    @State private var lastCameraCenter = SearchMapViewModel.defaultCenter

    var body: some View {
        Map(position: $mapPosition) {
            if case .results(let items) = viewModel.state {
                ForEach(items) { result in
                    let coordinate = CLLocationCoordinate2D(
                        latitude: result.location.coordinates.latitude,
                        longitude: result.location.coordinates.longitude
                    )

                    Annotation(result.location.name, coordinate: coordinate) {
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
        .onMapCameraChange(frequency: .onEnd) { context in
            let center = context.region.center
            let newCenter = GeoCoordinates(latitude: center.latitude, longitude: center.longitude)
            lastCameraCenter = newCenter
            if !viewModel.mapCenter.isApproximatelyEqual(to: newCenter) {
                viewModel.mapCenter = newCenter
            }
        }
        .onChange(of: viewModel.mapCenter) { _, newCenter in
            guard !newCenter.isApproximatelyEqual(to: lastCameraCenter) else { return }
            lastCameraCenter = newCenter
            withAnimation(.easeInOut(duration: 0.2)) {
                mapPosition = .region(MKCoordinateRegion(
                    center: CLLocationCoordinate2D(
                        latitude: newCenter.latitude,
                        longitude: newCenter.longitude
                    ),
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
                    .overlay {
                        Capsule()
                            .stroke(isSelected ? Color.clear : SpotLinkDesign.Colors.separator.opacity(0.35), lineWidth: 1)
                    }
                    .shadow(color: .black.opacity(isSelected ? 0.18 : 0.12), radius: 6, x: 0, y: 3)

                if let price = result.formattedStartingPrice {
                    Text(price)
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(isSelected ? Color.white : SpotLinkDesign.Colors.brand)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                } else {
                    Image(systemName: "parkingsign")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(isSelected ? Color.white : SpotLinkDesign.Colors.brand)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                }
            }
            .frame(height: 32)

            Image(systemName: "arrowtriangle.down.fill")
                .font(.system(size: 7))
                .foregroundStyle(isSelected ? SpotLinkDesign.Colors.brand : SpotLinkDesign.Colors.background)
                .shadow(color: .black.opacity(0.08), radius: 2, x: 0, y: 1)
        }
        .scaleEffect(isSelected ? 1.08 : 1)
        .animation(.spring(duration: 0.2), value: isSelected)
    }
}

private extension GeoCoordinates {
    func isApproximatelyEqual(to other: GeoCoordinates, tolerance: Double = 0.00001) -> Bool {
        abs(latitude - other.latitude) < tolerance &&
            abs(longitude - other.longitude) < tolerance
    }
}
