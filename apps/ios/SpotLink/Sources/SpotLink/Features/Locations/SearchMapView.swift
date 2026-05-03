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
    @State private var debugScenarioApplied = false
    @State private var debugBookingResult: LocationSearchResult?

    public init(viewModel: SearchMapViewModel) {
        _viewModel = ObservedObject(wrappedValue: viewModel)
    }

    public var body: some View {
        GeometryReader { geometry in
            let layout = SearchMapLayoutContext(
                size: geometry.size,
                safeAreaInsets: geometry.safeAreaInsets,
                state: viewModel.state,
                compactPresentation: viewModel.compactPresentation
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

                    if let selectedResult = viewModel.selectedResult {
                        selectedResultPeekCard(selectedResult, layout: layout)
                    }
                } else {
                    compactOverlay(layout: layout)
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
        .sheet(item: $viewModel.presentedDetailResult, onDismiss: viewModel.dismissPresentedDetails) { result in
            NavigationStack {
                LocationPreviewSheet(
                    result: result,
                    searchStartsAt: viewModel.searchStartsAt,
                    searchEndsAt: viewModel.searchEndsAt
                )
            }
            .presentationDetents([.medium, .large])
        }
        .sheet(item: $debugBookingResult) { result in
            NavigationStack {
                ReservationBookingFlowView(
                    result: result,
                    initialStartsAt: viewModel.searchStartsAt,
                    initialEndsAt: viewModel.searchEndsAt
                )
            }
        }
        .onAppear {
            guard !hasStartedInitialSearch else { return }
            hasStartedInitialSearch = true
            viewModel.searchWithCurrentCenter()
        }
        .onReceive(viewModel.$state) { _ in
            applyDebugScenarioIfNeeded()
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
        viewModel.selectResult(result)
    }

    @ViewBuilder
    private func selectedResultPeekCard(
        _ result: LocationSearchResult,
        layout: SearchMapLayoutContext
    ) -> some View {
        SearchMapSelectedResultPeekCard(
            result: result,
            density: .regular,
            showsResultsButton: false,
            showsDragHandle: false,
            onShowResults: viewModel.showLastResultsPresentation,
            onShowDetails: openSelectedResultDetails,
            onClearSelection: viewModel.clearSelection
        )
        .frame(width: layout.sidebarSelectionWidth)
        .padding(.trailing, layout.outerPadding)
        .padding(.bottom, layout.bottomPadding)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
    }

    private func openSelectedResultDetails() {
        viewModel.showSelectedResultDetails()
    }

    private func openAppSettings() {
        #if os(iOS)
        if let url = URL(string: UIApplication.openSettingsURLString) {
            UIApplication.shared.open(url)
        }
        #endif
    }

    @ViewBuilder
    private func compactOverlay(layout: SearchMapLayoutContext) -> some View {
        ZStack {
            compactTopOverlay(layout: layout)
            compactBottomOverlay(layout: layout)
        }
        .animation(.spring(response: 0.32, dampingFraction: 0.86), value: viewModel.compactPresentation)
    }

    @ViewBuilder
    private func compactTopOverlay(layout: SearchMapLayoutContext) -> some View {
        VStack(spacing: layout.compactStackSpacing) {
            if viewModel.compactPresentation == .searchExpanded {
                ZStack(alignment: .topTrailing) {
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

                    Button(action: viewModel.dismissSearchControls) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 22, weight: .semibold))
                            .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                            .padding(SpotLinkDesign.Spacing.sm + 2)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Zatvori prosirenu pretragu")
                }
                .transition(.move(edge: .top).combined(with: .opacity))
            } else {
                SearchMapCompactCommandBar(
                    query: viewModel.query,
                    searchStartsAt: viewModel.searchStartsAt,
                    searchEndsAt: viewModel.searchEndsAt,
                    state: viewModel.state,
                    onExpandSearch: viewModel.showSearchControls,
                    onSearchArea: searchCurrentArea,
                    onNearMe: searchNearMe
                )
                .transition(.move(edge: .top).combined(with: .opacity))
            }
        }
        .padding(.top, layout.topPadding)
        .padding(.horizontal, layout.outerPadding)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
    }

    @ViewBuilder
    private func compactBottomOverlay(layout: SearchMapLayoutContext) -> some View {
        switch viewModel.compactPresentation {
        case .mapOnly:
            if !matchesIdleState {
                SearchMapCompactResultsLauncher(
                    state: viewModel.state,
                    onTap: viewModel.showLastResultsPresentation
                )
                .padding(.horizontal, layout.outerPadding)
                .padding(.bottom, layout.bottomPadding)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }

        case .searchExpanded:
            EmptyView()

        case .resultsPeek, .resultsExpanded:
            SearchMapResultsPanel(
                mode: .compact,
                state: viewModel.state,
                selectedResultID: viewModel.selectedResult?.id,
                density: layout.panelDensity,
                compactPresentation: viewModel.compactPresentation,
                onRetry: searchCurrentArea,
                onSelectResult: selectResult,
                onExpand: viewModel.expandResults,
                onCollapse: viewModel.showResultsPeek,
                onHide: viewModel.hideResultsSurface
            )
            .frame(maxHeight: layout.compactResultsPanelHeight)
            .padding(.horizontal, layout.outerPadding)
            .padding(.bottom, layout.bottomPadding)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
            .transition(.move(edge: .bottom).combined(with: .opacity))

        case .selectedResultPeek:
            if let selectedResult = viewModel.selectedResult {
                SearchMapSelectedResultPeekCard(
                    result: selectedResult,
                    density: layout.panelDensity,
                    showsResultsButton: true,
                    showsDragHandle: true,
                    onShowResults: viewModel.showLastResultsPresentation,
                    onShowDetails: openSelectedResultDetails,
                    onClearSelection: viewModel.clearSelection
                )
                .padding(.horizontal, layout.outerPadding)
                .padding(.bottom, layout.bottomPadding)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
    }

    private var matchesIdleState: Bool {
        if case .idle = viewModel.state {
            return true
        }

        return false
    }

    private func applyDebugScenarioIfNeeded() {
        #if DEBUG
        guard !debugScenarioApplied,
              let scenario = SearchMapDebugScenario.current else {
            return
        }

        switch scenario {
        case .initial, .resultsPeek:
            if !matchesIdleState {
                viewModel.showResultsPeek()
                debugScenarioApplied = true
            }

        case .searchExpanded:
            if !matchesIdleState {
                viewModel.showSearchControls()
                debugScenarioApplied = true
            }

        case .selectedResultPeek, .details, .booking:
            guard case .results(let items) = viewModel.state,
                  let firstResult = items.first else {
                return
            }

            viewModel.selectResult(firstResult)

            switch scenario {
            case .selectedResultPeek:
                break
            case .details:
                viewModel.showSelectedResultDetails()
            case .booking:
                debugBookingResult = firstResult
            case .initial, .searchExpanded, .resultsPeek:
                break
            }

            debugScenarioApplied = true
        }
        #endif
    }
}

#if DEBUG
private enum SearchMapDebugScenario: String {
    case initial
    case searchExpanded
    case resultsPeek
    case selectedResultPeek
    case details
    case booking

    static var current: SearchMapDebugScenario? {
        let process = ProcessInfo.processInfo
        let scenarioValue = process.environment["SPOTLINK_DEBUG_SEARCH_SCENARIO"]
            ?? process.arguments.value(after: "--spotlink-debug-search-scenario")

        guard let rawValue = scenarioValue?.trimmingCharacters(in: .whitespacesAndNewlines),
              !rawValue.isEmpty else {
            return nil
        }

        return SearchMapDebugScenario(rawValue: rawValue)
    }
}

private extension Array where Element == String {
    func value(after flag: String) -> String? {
        guard let index = firstIndex(of: flag) else { return nil }
        let next = self.index(after: index)
        guard next < endIndex else { return nil }
        return self[next]
    }
}
#endif

// MARK: - Layout Context

struct SearchMapLayoutContext {
    let size: CGSize
    let safeAreaInsets: SwiftUI.EdgeInsets
    let state: SearchMapViewModel.State
    let compactPresentation: SearchMapViewModel.CompactPanelPresentation

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
        min(max(size.width * 0.31, 344), 416)
    }

    var compactResultsPanelHeight: CGFloat {
        let heightCap = min(size.height * (isLandscapeCompact ? 0.74 : 0.60), isLandscapeCompact ? 324 : 520)

        func clamped(_ value: CGFloat, minimum: CGFloat) -> CGFloat {
            min(max(value, minimum), heightCap)
        }

        switch compactPresentation {
        case .resultsPeek:
            switch state {
            case .idle:
                return clamped(isCondensedCompact ? 176 : 204, minimum: isCondensedCompact ? 160 : 188)
            case .loading:
                return clamped(isCondensedCompact ? 208 : 228, minimum: isCondensedCompact ? 184 : 206)
            case .results:
                return clamped(isCondensedCompact ? 214 : 242, minimum: isCondensedCompact ? 192 : 220)
            case .empty, .error, .offline:
                return clamped(isCondensedCompact ? 198 : 224, minimum: isCondensedCompact ? 184 : 206)
            }

        case .resultsExpanded:
            switch state {
            case .idle:
                return clamped(isCondensedCompact ? 260 : 320, minimum: isCondensedCompact ? 220 : 280)
            case .loading:
                return clamped(isCondensedCompact ? 286 : 352, minimum: isCondensedCompact ? 240 : 300)
            case .results(let items):
                let rowLimit = isLandscapeCompact ? 3 : 5
                let estimatedRows = CGFloat(min(max(items.count, 2), rowLimit))
                let rowHeight: CGFloat = isCondensedCompact ? 108 : 126
                let chromeHeight: CGFloat = isCondensedCompact ? 112 : 128
                return clamped(estimatedRows * rowHeight + chromeHeight, minimum: isCondensedCompact ? 244 : 312)
            case .empty, .error, .offline:
                return clamped(isCondensedCompact ? 272 : 340, minimum: isCondensedCompact ? 236 : 296)
            }

        case .mapOnly, .searchExpanded, .selectedResultPeek:
            return 0
        }
    }

    var sidebarSelectionWidth: CGFloat {
        min(max(size.width * 0.28, 300), 360)
    }

    private var isCondensedCompact: Bool {
        !usesSidebar && (size.height < 720 || size.width < 390)
    }

    private var isLandscapeCompact: Bool {
        !usesSidebar && size.width > size.height
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
                            viewModel.selectResult(result)
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
                            viewModel.selectResult(result)
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
        .padding(6)
        .contentShape(Rectangle())
        .scaleEffect(isSelected ? 1.08 : 1)
        .animation(.spring(duration: 0.2), value: isSelected)
        .accessibilityElement(children: .ignore)
        .accessibilityAddTraits(.isButton)
        .accessibilityLabel(accessibilityLabel)
        .accessibilityHint(isSelected ? "Lokacija je izabrana." : "Dodirnite da otvorite pregled lokacije.")
    }

    private var accessibilityLabel: String {
        var components = [result.location.name]

        if let price = result.formattedStartingPrice {
            components.append("Cena od \(price)")
        }

        if let distance = result.formattedDistance {
            components.append("Udaljenost \(distance)")
        }

        return components.joined(separator: ", ")
    }
}

private extension GeoCoordinates {
    func isApproximatelyEqual(to other: GeoCoordinates, tolerance: Double = 0.00001) -> Bool {
        abs(latitude - other.latitude) < tolerance &&
            abs(longitude - other.longitude) < tolerance
    }
}

