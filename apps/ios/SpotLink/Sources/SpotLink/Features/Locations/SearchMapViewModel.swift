import Combine
import CoreLocation
import Foundation

// MARK: - Search Map ViewModel

@MainActor
public final class SearchMapViewModel: ObservableObject {

    // Podrazumevani centar kada lokacija korisnika nije dostupna – Beograd
    public static let defaultCenter = GeoCoordinates(latitude: 44.8125, longitude: 20.4612)

    // MARK: - State

    public enum State: Sendable {
        case idle
        case loading
        case results([LocationSearchResult])
        case empty
        case error(String)
        case offline
    }

    public enum CompactPanelPresentation: String, Equatable, Sendable {
        case mapOnly
        case searchExpanded
        case resultsPeek
        case resultsExpanded
        case selectedResultPeek
    }

    @Published public private(set) var state: State = .idle
    @Published public private(set) var compactPresentation: CompactPanelPresentation = .mapOnly
    @Published public var query: String = ""
    @Published public var selectedResult: LocationSearchResult?
    @Published public var presentedDetailResult: LocationSearchResult?
    @Published public var mapCenter: GeoCoordinates = defaultCenter
    @Published public var searchStartsAt: Date
    @Published public var searchEndsAt: Date
    @Published public var locationPermissionDenied: Bool = false

    private let locationService: LocationService
    private let locationManager: SpotLinkLocationManager
    private var searchTask: Task<Void, Never>?
    private var lastResultsPresentation: CompactPanelPresentation = .resultsPeek

    public init(locationService: LocationService, locationManager: SpotLinkLocationManager) {
        self.locationService = locationService
        self.locationManager = locationManager

        // Podrazumevano vremensko okno: sledeci zaokruzeni sat za 2 sata
        let now = Date()
        let nextHour = Calendar.current.nextDate(
            after: now,
            matching: DateComponents(minute: 0),
            matchingPolicy: .nextTimePreservingSmallerComponents
        ) ?? now.addingTimeInterval(3600)
        self.searchStartsAt = nextHour
        self.searchEndsAt = nextHour.addingTimeInterval(7200)
    }

    // MARK: - Pretraga

    public func searchWithCurrentCenter() {
        let center = mapCenter
        performSearch(latitude: center.latitude, longitude: center.longitude, query: query.isEmpty ? nil : query)
    }

    public func searchManual() {
        let trimmed = query.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return }
        performSearch(latitude: nil, longitude: nil, query: trimmed)
    }

    public func searchNearMe() {
        guard locationManager.permissionStatus.isAuthorized else {
            if locationManager.permissionStatus == .notDetermined {
                locationManager.requestPermission()
            } else {
                locationPermissionDenied = true
            }
            return
        }
        if let loc = locationManager.currentLocation {
            let coords = GeoCoordinates(latitude: loc.coordinate.latitude, longitude: loc.coordinate.longitude)
            mapCenter = coords
            performSearch(latitude: coords.latitude, longitude: coords.longitude, query: query.isEmpty ? nil : query)
        } else {
            // Zatrazimo jedno ocitavanje, pretraga ce biti pokrenuta nakon sto pristigne
            Task {
                if let loc = try? await locationManager.requestOneTimeLocation() {
                    let coords = GeoCoordinates(latitude: loc.coordinate.latitude, longitude: loc.coordinate.longitude)
                    mapCenter = coords
                    performSearch(latitude: coords.latitude, longitude: coords.longitude, query: query.isEmpty ? nil : query)
                } else {
                    performSearch(latitude: nil, longitude: nil, query: query.isEmpty ? nil : query)
                }
            }
        }
    }

    public func showSearchControls() {
        compactPresentation = .searchExpanded
    }

    public func dismissSearchControls() {
        compactPresentation = fallbackCompactPresentation()
    }

    public func showResultsPeek() {
        guard state.supportsCompactSurface else {
            compactPresentation = .mapOnly
            return
        }

        setCompactPresentation(.resultsPeek)
    }

    public func expandResults() {
        guard state.supportsCompactSurface else { return }
        setCompactPresentation(.resultsExpanded)
    }

    public func hideResultsSurface() {
        compactPresentation = .mapOnly
    }

    public func showLastResultsPresentation() {
        guard state.supportsCompactSurface else {
            compactPresentation = .mapOnly
            return
        }

        setCompactPresentation(lastResultsPresentation)
    }

    public func selectResult(_ result: LocationSearchResult) {
        selectedResult = result
        mapCenter = result.location.coordinates
        compactPresentation = .selectedResultPeek
    }

    public func showSelectedResultDetails() {
        guard let selectedResult else { return }
        presentedDetailResult = selectedResult
    }

    public func dismissPresentedDetails() {
        presentedDetailResult = nil
    }

    public func clearSelection() {
        selectedResult = nil
        presentedDetailResult = nil
        compactPresentation = fallbackCompactPresentation()
    }

    // MARK: - Privatna implementacija

    private func performSearch(latitude: Double?, longitude: Double?, query: String?) {
        searchTask?.cancel()
        let previousSelectedID = selectedResult?.id
        selectedResult = nil
        presentedDetailResult = nil
        state = .loading
        setCompactPresentation(lastResultsPresentation)

        var filters = LocationSearchFilters()
        filters.query = query
        filters.latitude = latitude
        filters.longitude = longitude
        if latitude != nil { filters.radiusKm = 10 }
        filters.startsAt = ISO8601DateFormatter().string(from: searchStartsAt)
        filters.endsAt = ISO8601DateFormatter().string(from: searchEndsAt)
        filters.size = 50

        searchTask = Task {
            do {
                let page = try await locationService.search(filters)
                guard !Task.isCancelled else { return }
                if page.content.isEmpty {
                    state = .empty
                    setCompactPresentation(lastResultsPresentation)
                } else {
                    state = .results(page.content)
                    if let previousSelectedID,
                       let preservedSelection = page.content.first(where: { $0.id == previousSelectedID }) {
                        selectedResult = preservedSelection
                        compactPresentation = .selectedResultPeek
                    } else {
                        setCompactPresentation(lastResultsPresentation)
                    }
                }
            } catch let error as APIError {
                guard !Task.isCancelled else { return }
                switch error {
                case .offline:
                    state = .offline
                    setCompactPresentation(lastResultsPresentation)
                default:
                    state = .error(error.userFacingMessage)
                    setCompactPresentation(lastResultsPresentation)
                }
            } catch {
                guard !Task.isCancelled else { return }
                state = .error("Doslo je do neocekivane greske.")
                setCompactPresentation(lastResultsPresentation)
            }
        }
    }

    private func setCompactPresentation(_ presentation: CompactPanelPresentation) {
        compactPresentation = presentation

        switch presentation {
        case .resultsPeek, .resultsExpanded:
            lastResultsPresentation = presentation
        case .mapOnly, .searchExpanded, .selectedResultPeek:
            break
        }
    }

    private func fallbackCompactPresentation() -> CompactPanelPresentation {
        if selectedResult != nil {
            return .selectedResultPeek
        }

        if state.supportsCompactSurface {
            return lastResultsPresentation
        }

        return .mapOnly
    }
}

private extension SearchMapViewModel.State {
    var supportsCompactSurface: Bool {
        switch self {
        case .idle:
            return false
        case .loading, .results, .empty, .error, .offline:
            return true
        }
    }
}
