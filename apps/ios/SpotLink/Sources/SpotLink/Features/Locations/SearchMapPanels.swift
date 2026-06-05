import Foundation
import SwiftUI

enum SearchMapPanelMode: Equatable {
    case compact
    case sidebar
}

enum SearchMapPanelDensity: Equatable {
    case regular
    case condensed
}

struct SearchMapSidebar: View {
    @Binding var query: String
    @Binding var searchStartsAt: Date
    @Binding var searchEndsAt: Date

    let state: SearchMapViewModel.State
    let selectedResultID: String?
    let onSubmitQuery: () -> Void
    let onSearchArea: () -> Void
    let onNearMe: () -> Void
    let onSelectResult: (LocationSearchResult) -> Void

    var body: some View {
        VStack(spacing: SpotLinkDesign.Spacing.sm + 4) {
            SearchMapCommandPanel(
                mode: .sidebar,
                query: $query,
                searchStartsAt: $searchStartsAt,
                searchEndsAt: $searchEndsAt,
                state: state,
                density: .regular,
                onSubmitQuery: onSubmitQuery,
                onSearchArea: onSearchArea,
                onNearMe: onNearMe
            )

            SearchMapResultsPanel(
                mode: .sidebar,
                state: state,
                selectedResultID: selectedResultID,
                density: .regular,
                compactPresentation: nil,
                onRetry: onSearchArea,
                onSelectResult: onSelectResult,
                onExpand: nil,
                onCollapse: nil,
                onHide: nil
            )
            .frame(maxHeight: .infinity)
        }
    }
}

struct SearchMapCompactCommandBar: View {
    let query: String
    let searchStartsAt: Date
    let searchEndsAt: Date
    let state: SearchMapViewModel.State
    let onExpandSearch: () -> Void
    let onSearchArea: () -> Void
    let onNearMe: () -> Void

    var body: some View {
        ViewThatFits(in: .horizontal) {
            HStack(alignment: .center, spacing: SpotLinkDesign.Spacing.sm) {
                searchButton
                quickActionButtons
            }

            VStack(spacing: SpotLinkDesign.Spacing.sm) {
                searchButton
                HStack(spacing: SpotLinkDesign.Spacing.sm) {
                    quickActionButtons
                }
            }
        }
    }

    private var searchButton: some View {
        Button(action: onExpandSearch) {
            HStack(spacing: SpotLinkDesign.Spacing.sm) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(SpotLinkDesign.Colors.brand)

                VStack(alignment: .leading, spacing: 2) {
                    Text(searchTitle)
                        .font(SpotLinkDesign.Typography.subheadline.weight(.semibold))
                        .foregroundStyle(SpotLinkDesign.Colors.label)
                        .lineLimit(1)

                    Text("\(timeSummary) • \(statusSummary)")
                        .font(SpotLinkDesign.Typography.caption)
                        .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                        .lineLimit(1)
                }

                Spacer(minLength: SpotLinkDesign.Spacing.sm)

                Image(systemName: "slider.horizontal.3")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
            }
            .padding(.horizontal, SpotLinkDesign.Spacing.md)
            .padding(.vertical, SpotLinkDesign.Spacing.sm + 2)
            .frame(minHeight: 54)
        }
        .buttonStyle(.plain)
        .searchMapSurface(cornerRadius: 20)
        .accessibilityLabel("Otvori pretragu parkinga")
        .accessibilityHint("Prikazuje polje za lokaciju, termin i akcije pretrage.")
        .accessibilityIdentifier("search.openControls.button")
    }

    private var quickActionButtons: some View {
        Group {
            SearchMapCompactActionButton(
                title: "Pretrazi oblast",
                systemImage: "scope",
                action: onSearchArea
            )

            SearchMapCompactActionButton(
                title: "Blizu mene",
                systemImage: "location.fill",
                action: onNearMe
            )
        }
    }

    private var searchTitle: String {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? "Pretrazi parkinge" : trimmed
    }

    private var statusSummary: String {
        switch state {
        case .idle:
            return "Mapa je spremna"
        case .loading:
            return "Azuriranje"
        case .results(let items):
            return "\(items.count) lokacija"
        case .empty:
            return "Bez rezultata"
        case .error:
            return "Servis zahteva proveru"
        case .offline:
            return "Bez mreze"
        }
    }

    private var timeSummary: String {
        Self.timeFormatter.string(from: searchStartsAt, to: searchEndsAt)
    }

    private static let timeFormatter: DateIntervalFormatter = {
        let formatter = DateIntervalFormatter()
        formatter.locale = Locale(identifier: "sr_RS")
        formatter.dateTemplate = "dd MMM HH:mm"
        return formatter
    }()
}

struct SearchMapCompactResultsLauncher: View {
    let state: SearchMapViewModel.State
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: SpotLinkDesign.Spacing.sm) {
                Image(systemName: launcherIcon)
                    .font(.system(size: 15, weight: .semibold))

                Text(launcherTitle)
                    .font(SpotLinkDesign.Typography.subheadline.weight(.semibold))
                    .lineLimit(1)

                if case .results(let items) = state {
                    Text("\(items.count)")
                        .font(SpotLinkDesign.Typography.caption.weight(.bold))
                        .padding(.horizontal, SpotLinkDesign.Spacing.sm)
                        .padding(.vertical, SpotLinkDesign.Spacing.xs + 2)
                        .background(SpotLinkDesign.Colors.brand.opacity(0.12), in: Capsule())
                }
            }
            .foregroundStyle(SpotLinkDesign.Colors.label)
            .padding(.horizontal, SpotLinkDesign.Spacing.md)
            .padding(.vertical, SpotLinkDesign.Spacing.sm + 2)
        }
        .buttonStyle(.plain)
        .searchMapSurface(cornerRadius: 999)
        .accessibilityLabel(launcherTitle)
        .accessibilityHint("Otvori donju povrsinu sa statusom i rezultatima pretrage.")
    }

    private var launcherIcon: String {
        switch state {
        case .idle:
            return "map"
        case .loading:
            return "arrow.triangle.2.circlepath"
        case .results:
            return "list.bullet.below.rectangle"
        case .empty:
            return "magnifyingglass"
        case .error:
            return "exclamationmark.triangle.fill"
        case .offline:
            return "wifi.slash"
        }
    }

    private var launcherTitle: String {
        switch state {
        case .idle:
            return "Mapa je spremna"
        case .loading:
            return "Prikazi osvezavanje pretrage"
        case .results:
            return "Prikazi lokacije"
        case .empty:
            return "Prikazi status bez rezultata"
        case .error:
            return "Prikazi gresku pretrage"
        case .offline:
            return "Prikazi status mreze"
        }
    }
}

struct SearchMapCommandPanel: View {
    let mode: SearchMapPanelMode

    @Binding var query: String
    @Binding var searchStartsAt: Date
    @Binding var searchEndsAt: Date

    let state: SearchMapViewModel.State
    let density: SearchMapPanelDensity
    let onSubmitQuery: () -> Void
    let onSearchArea: () -> Void
    let onNearMe: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: panelSpacing) {
            if mode == .sidebar {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Pretraga parkinga")
                        .font(SpotLinkDesign.Typography.title3.weight(.bold))
                        .foregroundStyle(SpotLinkDesign.Colors.label)

                    Text("Inventar po oblasti, terminu i dostupnosti.")
                        .font(SpotLinkDesign.Typography.subheadline)
                        .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                }
            }

            searchField
            TimeWindowControl(
                startsAt: $searchStartsAt,
                endsAt: $searchEndsAt,
                density: density,
                onChanged: onSearchArea
            )
            actionRow
            commandSummary
        }
        .padding(panelPadding)
        .searchMapSurface(cornerRadius: mode == .sidebar ? 24 : 20)
    }

    private var panelSpacing: CGFloat {
        density == .condensed ? SpotLinkDesign.Spacing.sm + 2 : SpotLinkDesign.Spacing.md
    }

    private var panelPadding: CGFloat {
        if mode == .sidebar { return SpotLinkDesign.Spacing.lg }
        return density == .condensed ? SpotLinkDesign.Spacing.sm + 2 : SpotLinkDesign.Spacing.md
    }

    private var searchField: some View {
        HStack(spacing: SpotLinkDesign.Spacing.sm) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)

            TextField("Adresa, kvart ili lokacija", text: $query)
                .nameInputStyle()
#if os(iOS)
                .submitLabel(.search)
#endif
                .onSubmit(onSubmitQuery)
                .accessibilityIdentifier("search.query.field")

            if !query.isEmpty {
                Button {
                    query = ""
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Obrisi upit")
            }
        }
        .padding(.horizontal, SpotLinkDesign.Spacing.md)
        .padding(.vertical, density == .condensed ? SpotLinkDesign.Spacing.sm : SpotLinkDesign.Spacing.sm + 2)
        .background(SpotLinkDesign.Colors.background.opacity(0.94), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(SpotLinkDesign.Colors.separator.opacity(0.18), lineWidth: 1)
        }
    }

    private var actionRow: some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: SpotLinkDesign.Spacing.sm) {
                searchAreaButton
                nearMeButton
            }

            VStack(spacing: SpotLinkDesign.Spacing.sm) {
                searchAreaButton
                nearMeButton
            }
        }
    }

    private var searchAreaButton: some View {
        Button(action: onSearchArea) {
            Label(searchAreaTitle, systemImage: "scope")
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(.borderedProminent)
        .tint(SpotLinkDesign.Colors.brand)
        .controlSize(controlSize)
    }

    private var nearMeButton: some View {
        Button(action: onNearMe) {
            Label(nearMeTitle, systemImage: "location.fill")
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(.bordered)
        .tint(SpotLinkDesign.Colors.brand)
        .controlSize(controlSize)
    }

    private var controlSize: ControlSize {
        if mode == .sidebar { return .large }
        return density == .condensed ? .small : .regular
    }

    private var searchAreaTitle: String {
        density == .condensed ? "Oblast" : "Pretrazi oblast"
    }

    private var nearMeTitle: String {
        density == .condensed ? "Blizu" : "Blizu mene"
    }

    private var commandSummary: some View {
        Group {
            if density == .condensed {
                statusChip
            } else {
                ViewThatFits(in: .horizontal) {
                    HStack(alignment: .top, spacing: SpotLinkDesign.Spacing.sm) {
                        statusChip

                        Spacer(minLength: SpotLinkDesign.Spacing.sm)

                        helperText
                            .multilineTextAlignment(.trailing)
                    }

                    VStack(alignment: .leading, spacing: SpotLinkDesign.Spacing.xs + 2) {
                        statusChip
                        helperText
                    }
                }
            }
        }
    }

    private var statusChip: some View {
        StatusChip(
            icon: statusIcon,
            label: statusLabel,
            prominence: statusProminence
        )
    }

    private var helperText: some View {
        Text("Radijus 10 km oko centra mape.")
            .font(SpotLinkDesign.Typography.caption)
            .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
            .fixedSize(horizontal: false, vertical: true)
    }

    private var statusIcon: String {
        switch state {
        case .idle:
            return "map"
        case .loading:
            return "arrow.triangle.2.circlepath"
        case .results:
            return "checkmark.circle.fill"
        case .empty:
            return "magnifyingglass"
        case .error:
            return "exclamationmark.triangle.fill"
        case .offline:
            return "wifi.slash"
        }
    }

    private var statusProminence: StatusChip.Prominence {
        switch state {
        case .results:
            return .brand
        case .empty:
            return .neutral
        case .error, .offline:
            return .warning
        case .idle, .loading:
            return .neutral
        }
    }

    private var statusLabel: String {
        switch state {
        case .idle:
            return "Spremno za pretragu"
        case .loading:
            return "Osvezavanje rezultata"
        case .results(let items):
            return "\(items.count) lokacija u prikazu"
        case .empty:
            return "Bez rezultata"
        case .error:
            return "Greska u ucitavanju"
        case .offline:
            return "Rad bez mreze"
        }
    }
}

struct SearchMapResultsPanel: View {
    let mode: SearchMapPanelMode
    let state: SearchMapViewModel.State
    let selectedResultID: String?
    let density: SearchMapPanelDensity
    let compactPresentation: SearchMapViewModel.CompactPanelPresentation?
    let onRetry: () -> Void
    let onSelectResult: (LocationSearchResult) -> Void
    let onExpand: (() -> Void)?
    let onCollapse: (() -> Void)?
    let onHide: (() -> Void)?

    var body: some View {
        VStack(spacing: 0) {
            if mode == .compact {
                Capsule()
                    .fill(SpotLinkDesign.Colors.separator.opacity(0.4))
                    .frame(width: 42, height: 5)
                    .padding(.top, density == .condensed ? SpotLinkDesign.Spacing.xs + 2 : SpotLinkDesign.Spacing.sm)
            }

            header
            Divider()
                .padding(.horizontal, SpotLinkDesign.Spacing.md)

            content
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        }
        .searchMapSurface(cornerRadius: mode == .compact ? 28 : 24)
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .center, spacing: SpotLinkDesign.Spacing.sm) {
                Text(title)
                    .font(SpotLinkDesign.Typography.headline.weight(.semibold))
                    .foregroundStyle(SpotLinkDesign.Colors.label)

                Spacer()

                if case .results(let items) = state {
                    StatusChip(
                        icon: "building.2.fill",
                        label: "\(items.count)",
                        prominence: .brand
                    )
                }

                if case .loading = state {
                    ProgressView()
                        .controlSize(.small)
                }

                if mode == .compact {
                    compactHeaderActions
                }
            }

            if density == .regular || mode == .sidebar || compactPresentation == .resultsExpanded {
                Text(subtitle)
                    .font(SpotLinkDesign.Typography.footnote)
                    .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
            }
        }
        .padding(.horizontal, SpotLinkDesign.Spacing.md)
        .padding(.top, density == .condensed ? SpotLinkDesign.Spacing.sm : SpotLinkDesign.Spacing.md)
        .padding(.bottom, density == .condensed ? SpotLinkDesign.Spacing.xs + 2 : SpotLinkDesign.Spacing.sm)
        .contentShape(Rectangle())
        .onTapGesture(perform: toggleCompactPresentation)
    }

    @ViewBuilder
    private var content: some View {
        switch state {
        case .idle:
            SearchResultsMessageView(
                icon: "map",
                title: "Mapa je spremna",
                description: "Pokrenite pretragu za oblast koja je trenutno u fokusu.",
                density: density
            )
        case .loading:
            if isCompactPeek {
                VStack(spacing: SpotLinkDesign.Spacing.sm) {
                    ForEach(0..<min(placeholderCount, 2), id: \.self) { _ in
                        SearchResultPlaceholderCard(density: density)
                    }
                }
                .padding(contentPadding)
            } else {
                ScrollView(.vertical, showsIndicators: false) {
                    VStack(spacing: SpotLinkDesign.Spacing.sm) {
                        ForEach(0..<placeholderCount, id: \.self) { _ in
                            SearchResultPlaceholderCard(density: density)
                        }
                    }
                    .padding(contentPadding)
                }
            }
        case .results(let items):
            if isCompactPeek {
                VStack(spacing: SpotLinkDesign.Spacing.sm) {
                    ForEach(Array(items.prefix(peekResultLimit))) { result in
                        SearchResultCard(
                            result: result,
                            isSelected: selectedResultID == result.id,
                            density: density,
                            onSelect: {
                                onSelectResult(result)
                            }
                        )
                    }

                    if items.count > peekResultLimit {
                        Button(action: { onExpand?() }) {
                            Label("Prikazi sve lokacije", systemImage: "chevron.up")
                                .font(SpotLinkDesign.Typography.footnote.weight(.semibold))
                                .foregroundStyle(SpotLinkDesign.Colors.brand)
                        }
                        .buttonStyle(.plain)
                        .padding(.top, 2)
                    }
                }
                .padding(contentPadding)
            } else {
                ScrollView(.vertical, showsIndicators: false) {
                    LazyVStack(spacing: SpotLinkDesign.Spacing.sm) {
                        ForEach(items) { result in
                            SearchResultCard(
                                result: result,
                                isSelected: selectedResultID == result.id,
                                density: density,
                                onSelect: {
                                    onSelectResult(result)
                                }
                            )
                        }
                    }
                    .padding(contentPadding)
                }
            }
        case .empty:
            SearchResultsMessageView(
                icon: "magnifyingglass",
                title: "Nema dostupnih mesta",
                description: "Promenite oblast, termin ili priblizite mapu da biste dobili vise ponuda.",
                actionTitle: "Osvezi oblast",
                density: density,
                action: onRetry
            )
        case .error(let message):
            SearchResultsMessageView(
                icon: "exclamationmark.triangle.fill",
                title: "Greska pri ucitavanju",
                description: message,
                actionTitle: "Pokusaj ponovo",
                density: density,
                action: onRetry
            )
        case .offline:
            SearchResultsMessageView(
                icon: "wifi.slash",
                title: "Nema internet veze",
                description: "Povezite uredjaj na mrezu pa ponovo pokrenite pretragu za ovu oblast.",
                actionTitle: "Pokusaj ponovo",
                density: density,
                action: onRetry
            )
        }
    }

    private var contentPadding: CGFloat {
        density == .condensed ? SpotLinkDesign.Spacing.sm + 2 : SpotLinkDesign.Spacing.md
    }

    private var placeholderCount: Int {
        density == .condensed ? 2 : 3
    }

    private var peekResultLimit: Int {
        density == .condensed ? 1 : 2
    }

    private var isCompactPeek: Bool {
        mode == .compact && compactPresentation == .resultsPeek
    }

    private var title: String {
        switch state {
        case .idle:
            return "Pregled rezultata"
        case .loading:
            return "Azuriranje prikaza"
        case .results:
            return "Aktivne lokacije"
        case .empty:
            return "Bez rezultata"
        case .error:
            return "Potrebna je intervencija"
        case .offline:
            return "Veza je prekinuta"
        }
    }

    private var subtitle: String {
        switch state {
        case .idle:
            return "Rezultati ce biti povezani sa centrom mape i izabranim terminom."
        case .loading:
            return "Pretraga se osvezava prema trenutnoj oblasti."
        case .results:
            return "Dodirnite lokaciju da otvorite detalje i nastavite ka rezervaciji."
        case .empty:
            return "Sistem nije nasao otvoren inventar za ovaj termin u aktuelnoj oblasti."
        case .error:
            return "Servis je odgovorio greskom. Pokusajte ponovo iz istog prikaza."
        case .offline:
            return "Rezultati se ne mogu osveziti dok uredjaj nije na mrezi."
        }
    }

    @ViewBuilder
    private var compactHeaderActions: some View {
        if compactPresentation == .resultsPeek {
            SearchMapInlineIconButton(
                systemImage: "chevron.up",
                label: "Prosiri rezultate",
                action: { onExpand?() }
            )
        } else if compactPresentation == .resultsExpanded {
            SearchMapInlineIconButton(
                systemImage: "chevron.down",
                label: "Vrati rezultate na pregled",
                action: { onCollapse?() }
            )
        }

        SearchMapInlineIconButton(
            systemImage: "xmark",
            label: "Sakrij rezultate",
            action: { onHide?() }
        )
    }

    private func toggleCompactPresentation() {
        guard mode == .compact else { return }

        switch compactPresentation {
        case .resultsPeek:
            onExpand?()
        case .resultsExpanded:
            onCollapse?()
        case .mapOnly, .searchExpanded, .selectedResultPeek, nil:
            break
        }
    }
}

struct SearchMapSelectedResultPeekCard: View {
    let result: LocationSearchResult
    let density: SearchMapPanelDensity
    let showsResultsButton: Bool
    let showsDragHandle: Bool
    let onShowResults: () -> Void
    let onShowDetails: () -> Void
    let onClearSelection: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            if showsDragHandle {
                Capsule()
                    .fill(SpotLinkDesign.Colors.separator.opacity(0.4))
                    .frame(width: 42, height: 5)
                    .padding(.top, density == .condensed ? SpotLinkDesign.Spacing.xs + 2 : SpotLinkDesign.Spacing.sm)
            }

            VStack(alignment: .leading, spacing: SpotLinkDesign.Spacing.sm + 2) {
                header
                metadata
                actions
            }
            .padding(density == .condensed ? SpotLinkDesign.Spacing.md : SpotLinkDesign.Spacing.lg)
        }
        .searchMapSurface(cornerRadius: showsDragHandle ? 28 : 24)
    }

    private var header: some View {
        HStack(alignment: .top, spacing: SpotLinkDesign.Spacing.sm) {
            VStack(alignment: .leading, spacing: 6) {
                Text("Izabrana lokacija")
                    .font(SpotLinkDesign.Typography.caption.weight(.semibold))
                    .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                    .textCase(.uppercase)

                Text(result.location.name)
                    .font(SpotLinkDesign.Typography.headline.weight(.semibold))
                    .foregroundStyle(SpotLinkDesign.Colors.label)
                    .lineLimit(2)

                Text(result.location.address.displayAddress)
                    .font(SpotLinkDesign.Typography.footnote)
                    .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                    .lineLimit(density == .condensed ? 2 : 3)
            }

            Spacer(minLength: SpotLinkDesign.Spacing.sm)

            Button(action: onClearSelection) {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Zatvori pregled lokacije")
        }
    }

    private var metadata: some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: SpotLinkDesign.Spacing.xs + 2) {
                metadataChips
            }

            VStack(alignment: .leading, spacing: SpotLinkDesign.Spacing.xs + 2) {
                HStack(spacing: SpotLinkDesign.Spacing.xs + 2) {
                    primaryMetadata
                }

                HStack(spacing: SpotLinkDesign.Spacing.xs + 2) {
                    secondaryMetadata
                }
            }
        }
    }

    @ViewBuilder
    private var metadataChips: some View {
        primaryMetadata
        secondaryMetadata
    }

    @ViewBuilder
    private var primaryMetadata: some View {
        if let price = result.formattedStartingPrice {
            StatusChip(icon: "dollarsign.circle.fill", label: "od \(price)", prominence: .brand)
        }

        if let distance = result.formattedDistance {
            MetadataChip(icon: "location.fill", label: distance)
        }
    }

    @ViewBuilder
    private var secondaryMetadata: some View {
        if let resource = result.resources.first {
            MetadataChip(icon: resource.type.systemIcon, label: resource.type.displayName)
            MetadataChip(icon: "checkmark.seal.fill", label: resource.confirmationMode.displayName)
        }

        MetadataChip(icon: "car.2.fill", label: "\(result.availableResourceCount) dostupno")
    }

    private var actions: some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: SpotLinkDesign.Spacing.sm) {
                if showsResultsButton {
                    Button(action: onShowResults) {
                        Label("Rezultati", systemImage: "list.bullet")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                    .tint(SpotLinkDesign.Colors.brand)
                }

                detailButton
            }

            VStack(spacing: SpotLinkDesign.Spacing.sm) {
                if showsResultsButton {
                    Button(action: onShowResults) {
                        Label("Rezultati", systemImage: "list.bullet")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                    .tint(SpotLinkDesign.Colors.brand)
                }

                detailButton
            }
        }
    }

    private var detailButton: some View {
        Button(action: onShowDetails) {
            Label("Detalji", systemImage: "arrow.up.right.square")
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(.borderedProminent)
        .tint(SpotLinkDesign.Colors.brand)
    }
}

struct SearchResultCard: View {
    let result: LocationSearchResult
    let isSelected: Bool
    let density: SearchMapPanelDensity
    let onSelect: () -> Void

    var body: some View {
        Button(action: onSelect) {
            VStack(alignment: .leading, spacing: SpotLinkDesign.Spacing.sm + 2) {
                HStack(alignment: .top, spacing: SpotLinkDesign.Spacing.sm + 2) {
                    iconTile
                    titleBlock
                    Spacer(minLength: SpotLinkDesign.Spacing.sm)
                    priceBlock
                }

                metadataBlock
            }
            .padding(cardPadding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(cardBackground, in: RoundedRectangle(cornerRadius: cardRadius, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: cardRadius, style: .continuous)
                    .stroke(cardBorder, lineWidth: isSelected ? 1.5 : 1)
            }
        }
        .buttonStyle(.plain)
        .accessibilityHint("Izaberi lokaciju da otvoris pregled bez zatvaranja mape.")
    }

    private var iconTile: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(SpotLinkDesign.Colors.brand.opacity(0.12))
                .frame(width: iconSize, height: iconSize)

            Image(systemName: leadingIcon)
                .font(.system(size: density == .condensed ? 16 : 18, weight: .semibold))
                .foregroundStyle(SpotLinkDesign.Colors.brand)
        }
    }

    private var titleBlock: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(result.location.name)
                .font(SpotLinkDesign.Typography.subheadline.weight(.semibold))
                .foregroundStyle(SpotLinkDesign.Colors.label)
                .lineLimit(density == .condensed ? 1 : 2)
                .multilineTextAlignment(.leading)

            Text(result.location.address.displayAddress)
                .font(SpotLinkDesign.Typography.caption)
                .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                .lineLimit(density == .condensed ? 1 : 2)
                .multilineTextAlignment(.leading)
        }
    }

    private var priceBlock: some View {
        VStack(alignment: .trailing, spacing: 4) {
            Text(result.formattedStartingPrice ?? "Na upit")
                .font(SpotLinkDesign.Typography.subheadline.weight(.bold))
                .foregroundStyle(SpotLinkDesign.Colors.brand)
                .multilineTextAlignment(.trailing)

            if let distance = result.formattedDistance {
                Text(distance)
                    .font(SpotLinkDesign.Typography.caption)
                    .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
            }
        }
        .frame(minWidth: 74, alignment: .trailing)
    }

    @ViewBuilder
    private var metadataBlock: some View {
        if density == .condensed {
            ViewThatFits(in: .horizontal) {
                HStack(spacing: SpotLinkDesign.Spacing.xs + 2) {
                    primaryMetadata
                    Spacer(minLength: 0)
                }

                HStack(spacing: SpotLinkDesign.Spacing.xs + 2) {
                    condensedMetadata
                }
            }
        } else {
            ViewThatFits(in: .horizontal) {
                HStack(spacing: SpotLinkDesign.Spacing.xs + 2) {
                    metadataChips
                    Spacer(minLength: 0)
                }

                VStack(alignment: .leading, spacing: SpotLinkDesign.Spacing.xs + 2) {
                    HStack(spacing: SpotLinkDesign.Spacing.xs + 2) {
                        primaryMetadata
                    }

                    HStack(spacing: SpotLinkDesign.Spacing.xs + 2) {
                        secondaryMetadata
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var metadataChips: some View {
        primaryMetadata
        secondaryMetadata
    }

    @ViewBuilder
    private var primaryMetadata: some View {
        if let resource = result.resources.first {
            MetadataChip(icon: resource.type.systemIcon, label: resource.type.displayName)
        }

        MetadataChip(icon: result.location.accessType.systemIcon, label: result.location.accessType.displayName)
    }

    @ViewBuilder
    private var secondaryMetadata: some View {
        MetadataChip(icon: "car.2.fill", label: "\(result.availableResourceCount) dostupno")

        if let resource = result.resources.first {
            MetadataChip(icon: "checkmark.seal.fill", label: resource.confirmationMode.displayName)
        }
    }

    @ViewBuilder
    private var condensedMetadata: some View {
        MetadataChip(icon: "car.2.fill", label: "\(result.availableResourceCount)")

        if let resource = result.resources.first {
            MetadataChip(icon: "checkmark.seal.fill", label: resource.confirmationMode.displayName)
        }
    }

    private var leadingIcon: String {
        result.resources.first?.type.systemIcon ?? result.location.accessType.systemIcon
    }

    private var cardBackground: some ShapeStyle {
        isSelected ? SpotLinkDesign.Colors.brand.opacity(0.09) : SpotLinkDesign.Colors.background.opacity(0.94)
    }

    private var cardBorder: Color {
        isSelected ? SpotLinkDesign.Colors.brand.opacity(0.36) : SpotLinkDesign.Colors.separator.opacity(0.12)
    }

    private var cardPadding: CGFloat {
        density == .condensed ? SpotLinkDesign.Spacing.sm + 2 : SpotLinkDesign.Spacing.md
    }

    private var cardRadius: CGFloat {
        density == .condensed ? 16 : 18
    }

    private var iconSize: CGFloat {
        density == .condensed ? 42 : 48
    }
}

private struct SearchResultsMessageView: View {
    let icon: String
    let title: String
    let description: String
    var actionTitle: String?
    var density: SearchMapPanelDensity = .regular
    var action: (() -> Void)?

    var body: some View {
        VStack(spacing: SpotLinkDesign.Spacing.sm + 2) {
            Image(systemName: icon)
                .font(.system(size: density == .condensed ? 24 : 32, weight: .semibold))
                .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)

            VStack(spacing: 6) {
                Text(title)
                    .font(SpotLinkDesign.Typography.subheadline.weight(.semibold))
                    .foregroundStyle(SpotLinkDesign.Colors.label)

                Text(description)
                    .font(SpotLinkDesign.Typography.footnote)
                    .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                    .multilineTextAlignment(.center)
            }

            if let actionTitle, let action {
                Button(actionTitle, action: action)
                    .buttonStyle(.borderedProminent)
                    .tint(SpotLinkDesign.Colors.brand)
            }
        }
        .padding(density == .condensed ? SpotLinkDesign.Spacing.md : SpotLinkDesign.Spacing.lg)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

private struct SearchResultPlaceholderCard: View {
    let density: SearchMapPanelDensity

    var body: some View {
        VStack(alignment: .leading, spacing: SpotLinkDesign.Spacing.sm + 2) {
            HStack(alignment: .top, spacing: SpotLinkDesign.Spacing.sm + 2) {
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(SpotLinkDesign.Colors.secondaryBG)
                    .frame(width: density == .condensed ? 42 : 48, height: density == .condensed ? 42 : 48)

                VStack(alignment: .leading, spacing: 8) {
                    RoundedRectangle(cornerRadius: 5, style: .continuous)
                        .fill(SpotLinkDesign.Colors.secondaryBG)
                        .frame(height: 12)
                    RoundedRectangle(cornerRadius: 5, style: .continuous)
                        .fill(SpotLinkDesign.Colors.secondaryBG)
                        .frame(width: 160, height: 10)
                }

                Spacer()

                RoundedRectangle(cornerRadius: 5, style: .continuous)
                    .fill(SpotLinkDesign.Colors.secondaryBG)
                    .frame(width: 56, height: 12)
            }

            HStack(spacing: SpotLinkDesign.Spacing.xs + 2) {
                ForEach(0..<3, id: \.self) { _ in
                    RoundedRectangle(cornerRadius: 999, style: .continuous)
                        .fill(SpotLinkDesign.Colors.secondaryBG)
                        .frame(width: density == .condensed ? 72 : 96, height: density == .condensed ? 22 : 24)
                }
            }
        }
        .padding(density == .condensed ? SpotLinkDesign.Spacing.sm + 2 : SpotLinkDesign.Spacing.md)
        .background(SpotLinkDesign.Colors.background.opacity(0.94), in: RoundedRectangle(cornerRadius: density == .condensed ? 16 : 18, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: density == .condensed ? 16 : 18, style: .continuous)
                .stroke(SpotLinkDesign.Colors.separator.opacity(0.12), lineWidth: 1)
        }
        .redacted(reason: .placeholder)
    }
}

private struct SearchMapCompactActionButton: View {
    let title: String
    let systemImage: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(SpotLinkDesign.Colors.brand)
                .frame(width: 48, height: 48)
        }
        .buttonStyle(.plain)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(SpotLinkDesign.Colors.separator.opacity(0.18), lineWidth: 1)
        }
        .shadow(color: .black.opacity(0.08), radius: 10, x: 0, y: 4)
        .accessibilityLabel(title)
    }
}

private struct SearchMapInlineIconButton: View {
    let systemImage: String
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                .frame(width: 30, height: 30)
        }
        .buttonStyle(.plain)
        .background(SpotLinkDesign.Colors.secondaryBG, in: Circle())
        .accessibilityLabel(label)
    }
}

private struct MetadataChip: View {
    let icon: String
    let label: String

    var body: some View {
        Label(label, systemImage: icon)
            .font(SpotLinkDesign.Typography.caption.weight(.medium))
            .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
            .lineLimit(1)
            .truncationMode(.tail)
            .minimumScaleFactor(0.85)
            .padding(.horizontal, SpotLinkDesign.Spacing.sm + 1)
            .padding(.vertical, SpotLinkDesign.Spacing.xs + 2)
            .background(SpotLinkDesign.Colors.secondaryBG, in: Capsule())
    }
}

private struct StatusChip: View {
    enum Prominence {
        case neutral
        case brand
        case warning
    }

    let icon: String
    let label: String
    let prominence: Prominence

    var body: some View {
        Label(label, systemImage: icon)
            .font(SpotLinkDesign.Typography.caption.weight(.semibold))
            .foregroundStyle(foregroundColor)
            .lineLimit(1)
            .padding(.horizontal, SpotLinkDesign.Spacing.sm + 2)
            .padding(.vertical, SpotLinkDesign.Spacing.xs + 2)
            .background(backgroundColor, in: Capsule())
    }

    private var foregroundColor: Color {
        switch prominence {
        case .neutral:
            return SpotLinkDesign.Colors.secondaryLabel
        case .brand:
            return SpotLinkDesign.Colors.brand
        case .warning:
            return SpotLinkDesign.Colors.warning
        }
    }

    private var backgroundColor: Color {
        switch prominence {
        case .neutral:
            return SpotLinkDesign.Colors.secondaryBG
        case .brand:
            return SpotLinkDesign.Colors.brand.opacity(0.12)
        case .warning:
            return SpotLinkDesign.Colors.warning.opacity(0.14)
        }
    }
}

private struct TimeWindowControl: View {
    @Binding var startsAt: Date
    @Binding var endsAt: Date

    let density: SearchMapPanelDensity
    let onChanged: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: SpotLinkDesign.Spacing.sm) {
            HStack {
                Label(density == .condensed ? "Termin" : "Termin rezervacije", systemImage: "calendar.badge.clock")
                    .font(SpotLinkDesign.Typography.footnote.weight(.semibold))
                    .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)

                Spacer()

                Text(durationSummary)
                    .font(SpotLinkDesign.Typography.caption.weight(.medium))
                    .foregroundStyle(SpotLinkDesign.Colors.brand)
            }

            ViewThatFits(in: .horizontal) {
                HStack(spacing: SpotLinkDesign.Spacing.sm) {
                    timeField(title: "Od", selection: $startsAt)

                    Image(systemName: "arrow.right")
                        .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)

                    timeField(title: "Do", selection: $endsAt, range: startsAt...)
                }

                VStack(spacing: SpotLinkDesign.Spacing.sm) {
                    timeField(title: "Od", selection: $startsAt)
                    timeField(title: "Do", selection: $endsAt, range: startsAt...)
                }
            }
        }
        .padding(density == .condensed ? SpotLinkDesign.Spacing.sm + 2 : SpotLinkDesign.Spacing.md)
        .background(SpotLinkDesign.Colors.background.opacity(0.94), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(SpotLinkDesign.Colors.separator.opacity(0.18), lineWidth: 1)
        }
        .onChange(of: startsAt) { _, _ in
            guard endsAt > startsAt else {
                endsAt = startsAt.addingTimeInterval(7200)
                return
            }

            onChanged()
        }
        .onChange(of: endsAt) { _, _ in
            guard endsAt > startsAt else {
                endsAt = startsAt.addingTimeInterval(3600)
                return
            }

            onChanged()
        }
    }

    private func timeField(title: String, selection: Binding<Date>, range: PartialRangeFrom<Date>? = nil) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(SpotLinkDesign.Typography.caption.weight(.semibold))
                .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)

            Group {
                if let range {
                    DatePicker(
                        title,
                        selection: selection,
                        in: range,
                        displayedComponents: [.date, .hourAndMinute]
                    )
                } else {
                    DatePicker(
                        title,
                        selection: selection,
                        displayedComponents: [.date, .hourAndMinute]
                    )
                }
            }
            .labelsHidden()
            .datePickerStyle(.compact)
            .controlSize(density == .condensed ? .small : .regular)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var durationSummary: String {
        let components = Calendar.current.dateComponents([.hour, .minute], from: startsAt, to: endsAt)
        let hours = components.hour ?? 0
        let minutes = components.minute ?? 0

        if hours > 0, minutes > 0 {
            return "\(hours)h \(minutes)m"
        }
        if hours > 0 {
            return "\(hours)h"
        }
        return "\(max(minutes, 0))m"
    }
}

struct LocationPreviewSheet: View {
    let result: LocationSearchResult
    let searchStartsAt: Date
    let searchEndsAt: Date

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: SpotLinkDesign.Spacing.md) {
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(result.location.name)
                            .font(SpotLinkDesign.Typography.title3.weight(.bold))

                        Text(result.location.address.displayAddress)
                            .font(SpotLinkDesign.Typography.subheadline)
                            .foregroundStyle(SpotLinkDesign.Colors.secondaryLabel)
                    }

                    Spacer()

                    if let distance = result.formattedDistance {
                        Text(distance)
                            .font(SpotLinkDesign.Typography.callout.weight(.semibold))
                            .foregroundStyle(SpotLinkDesign.Colors.brand)
                    }
                }

                Divider()

                ViewThatFits(in: .horizontal) {
                    HStack(spacing: SpotLinkDesign.Spacing.lg) {
                        infoStrip
                    }

                    VStack(alignment: .leading, spacing: SpotLinkDesign.Spacing.sm) {
                        HStack(spacing: SpotLinkDesign.Spacing.lg) {
                            primaryInfoStrip
                        }

                        HStack(spacing: SpotLinkDesign.Spacing.lg) {
                            secondaryInfoStrip
                        }
                    }
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

                if !result.resources.isEmpty {
                    VStack(alignment: .leading, spacing: SpotLinkDesign.Spacing.sm) {
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
                }
            }
            .padding(SpotLinkDesign.Spacing.lg)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .navigationTitle("Detalji lokacije")
        .spotlinkInlineNavigationTitle()
        .safeAreaInset(edge: .bottom) {
            reserveAction
        }
    }

    @ViewBuilder
    private var infoStrip: some View {
        primaryInfoStrip
        secondaryInfoStrip
    }

    @ViewBuilder
    private var primaryInfoStrip: some View {
        if let price = result.formattedStartingPrice {
            infoChip(icon: "dollarsign.circle", label: "od \(price)")
        }

        infoChip(
            icon: result.location.accessType.systemIcon,
            label: result.location.accessType.displayName
        )
    }

    @ViewBuilder
    private var secondaryInfoStrip: some View {
        if let resource = result.resources.first {
            infoChip(icon: "checkmark.seal", label: resource.confirmationMode.displayName)
        }

        infoChip(icon: "car.2", label: "\(result.availableResourceCount) dostupno")
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
        .frame(maxWidth: .infinity)
    }

    private var reserveAction: some View {
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
        .padding(.horizontal, SpotLinkDesign.Spacing.lg)
        .padding(.vertical, SpotLinkDesign.Spacing.md)
        .background(.regularMaterial)
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
        .background(SpotLinkDesign.Colors.secondaryBG, in: RoundedRectangle(cornerRadius: SpotLinkDesign.Radius.sm, style: .continuous))
    }
}

private extension View {
    func searchMapSurface(cornerRadius: CGFloat) -> some View {
        self
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .stroke(SpotLinkDesign.Colors.separator.opacity(0.18), lineWidth: 1)
            }
            .shadow(color: .black.opacity(0.12), radius: 18, x: 0, y: 8)
    }
}
