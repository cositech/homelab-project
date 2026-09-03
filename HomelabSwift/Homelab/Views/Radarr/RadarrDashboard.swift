import SwiftUI

struct RadarrDashboard: View {
    let instanceId: UUID
    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.openURL) private var openURL
    
    @State private var client: RadarrAPIClient?
    @State private var systemStatus: RadarrSystemStatus?
    @State private var queue: [RadarrQueueRecord] = []
    @State private var latestMovies: [RadarrMovie] = []
    @State private var history: [RadarrHistoryRecord] = []
    @State private var healthMessages: [String] = []
    @State private var upcomingTitles: [String] = []
    
    @State private var state: LoadableState<Void> = .idle
    @State private var isFetching = false
    @State private var silentLibrarySkips = 0
    @State private var silentExtrasSkips = 0
    @State private var isViewVisible = false
    @State private var isRunningCommand = false
    @State private var commandMessage: String?
    @State private var moviesExpanded = false
    @State private var historyExpanded = false
    @State private var lookupQuery = ""
    @State private var lookupResults: [RadarrLookupMovie] = []
    @State private var isSearchingCatalog = false
    @State private var searchErrorMessage: String?
    @State private var selectedLookupResult: RadarrLookupMovie?
    @State private var selectedLibraryResult: MediaSearchResultPresentation?
    @State private var pendingRequestConfiguration: RadarrPendingRequestConfiguration?
    @State private var pendingLookupRequestIds: Set<String> = []
    private let timer = Timer.publish(every: 30, on: .main, in: .common).autoconnect()
    private let listPreviewCount = 4

    private var arr: ArrStrings { localizer.arr }
    private var serviceInstance: ServiceInstance? { servicesStore.instance(id: instanceId) }

    var body: some View {
        ServiceDashboardLayout(
            serviceType: .radarr,
            instanceId: instanceId,
            state: state,
            onRefresh: { await fetchData(silent: false) }
        ) {
            if let systemStatus {
                systemSection(systemStatus)
                quickActionsSection
                contentSearchSection
            }
            
            if !queue.isEmpty {
                queueSection
            }
            
            if !latestMovies.isEmpty {
                moviesSection
            }

            if !history.isEmpty {
                historySection
            }

            if !healthMessages.isEmpty {
                healthSection
            }

            if !upcomingTitles.isEmpty {
                upcomingSection
            }
            
            if queue.isEmpty && latestMovies.isEmpty && history.isEmpty && healthMessages.isEmpty && upcomingTitles.isEmpty {
                if case .loaded = state {
                    Text(localizer.t.noData)
                        .font(.headline)
                        .foregroundStyle(AppTheme.textSecondary)
                        .frame(maxWidth: .infinity, alignment: .center)
                        .padding(.top, 40)
                }
            }
        }
        .task {
            self.client = await servicesStore.radarrClient(instanceId: instanceId)
            await fetchData(silent: false)
        }
        .onAppear { isViewVisible = true }
        .onDisappear { isViewVisible = false }
        .onReceive(timer) { _ in
            guard scenePhase == .active, isViewVisible else { return }
            Task { await fetchData(silent: true) }
        }
        .sheet(item: $selectedLookupResult) { result in
            MediaSearchResultDetailSheet(
                result: MediaSearchResultPresentation(
                    id: result.id,
                    title: result.title,
                    subtitle: result.subtitle,
                    supporting: result.supporting,
                    status: result.status.map(localizedSearchStatus),
                    posterURL: resolvedServiceArtworkURL(result.posterURL, instance: serviceInstance),
                    artworkHeaders: serviceArtworkHeaders(for: resolvedServiceArtworkURL(result.posterURL, instance: serviceInstance), instance: serviceInstance),
                    detailsURL: result.detailsURL,
                    details: result.details,
                    canRequest: result.requestTmdbId != nil
                ),
                openDetailsTitle: arr.openDetails,
                requestTitle: arr.requestContent,
                isRequesting: pendingLookupRequestIds.contains(result.id),
                onOpenDetails: result.detailsURL.map { url in
                    { if let parsed = URL(string: url) { openURL(parsed) } }
                },
                onRequest: result.requestTmdbId != nil ? {
                    Task { await runContentRequest(result) }
                } : nil
            )
            .presentationDetents([.medium, .large])
        }
        .sheet(item: $selectedLibraryResult) { result in
            MediaSearchResultDetailSheet(
                result: result,
                openDetailsTitle: arr.openDetails,
                requestTitle: arr.requestContent,
                isRequesting: false,
                onOpenDetails: result.detailsURL.map { url in
                    { if let parsed = URL(string: url) { openURL(parsed) } }
                },
                onRequest: nil
            )
            .presentationDetents([.medium, .large])
        }
        .sheet(item: $pendingRequestConfiguration) { pending in
            ArrRequestConfigurationSheet(
                configuration: pending.configuration,
                titleText: arr.requestConfigurationTitle(pending.configuration.title),
                messageText: arr.requestConfigurationMessage,
                qualityProfileText: arr.requestQualityProfile,
                rootFolderText: arr.requestRootFolder,
                languageProfileText: arr.requestLanguageProfile,
                metadataProfileText: arr.requestMetadataProfile,
                confirmTitle: arr.requestContent
            ) { selection in
                Task { await runContentRequest(pending.result, selection: selection) }
            }
            .presentationDetents([.medium, .large])
        }
    }
    
    @MainActor
    private func fetchData(silent: Bool) async {
        guard servicesStore.instance(id: instanceId) != nil else {
            if !silent { state = .error(.notConfigured) }
            return
        }
        guard let client else { return }
        if isFetching { return }
        if silent {
            guard isViewVisible, servicesStore.reachability(for: instanceId) != false else { return }
        }

        isFetching = true
        defer { isFetching = false }

        if !silent { state = .loading }
        do {
            async let fetchStatus = client.getSystemStatus()
            async let fetchQueue = client.getQueue()
            async let fetchHistory = client.getHistory()

            let (sys, q, h) = try await (fetchStatus, fetchQueue, fetchHistory)
            self.systemStatus = sys
            self.queue = q.records
            self.history = Array(h.records.prefix(12))

            let shouldRefreshLibrary = !silent || silentLibrarySkips >= 3
            if shouldRefreshLibrary {
                let mList = try await client.getMovies()
                let sorted = mList.sorted { $0.id > $1.id }
                self.latestMovies = Array(sorted.prefix(15))
                silentLibrarySkips = 0
            } else if silent {
                silentLibrarySkips += 1
            }

            let shouldRefreshExtras = !silent || silentExtrasSkips >= 3
            if shouldRefreshExtras {
                async let healthTask = client.getHealthMessages()
                async let upcomingTask = client.getUpcomingTitles(limit: 8)
                self.healthMessages = await healthTask
                self.upcomingTitles = await upcomingTask
                silentExtrasSkips = 0
            } else if silent {
                silentExtrasSkips += 1
            }
            
            state = .loaded(())
        } catch let apiError as APIError {
            if silent {
                await servicesStore.checkReachability(for: instanceId)
            } else {
                state = .error(apiError)
            }
        } catch {
            if silent {
                await servicesStore.checkReachability(for: instanceId)
            } else {
                state = .error(.custom(error.localizedDescription))
            }
        }
    }
    
    private func systemSection(_ status: RadarrSystemStatus) -> some View {
        HStack {
            ServiceIconView(type: .radarr, size: 22)
                .frame(width: 42, height: 42)
                .background(ServiceType.radarr.colors.bg, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                
            VStack(alignment: .leading, spacing: 4) {
                Text(arr.radarrVersion)
                    .font(.caption.bold())
                    .foregroundStyle(AppTheme.textSecondary)
                Text(status.version)
                    .font(.subheadline.bold())
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 4) {
                Text(arr.branch)
                    .font(.caption.bold())
                    .foregroundStyle(AppTheme.textSecondary)
                Text(status.displayBranch)
                    .font(.caption.weight(.heavy))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(AppTheme.info.opacity(0.15), in: Capsule())
                    .foregroundStyle(AppTheme.info)
            }
        }
        .padding(16)
        .glassCard()
    }

    private var quickActionsSection: some View {
        VStack(spacing: 10) {
            if let commandMessage {
                HStack(spacing: 8) {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundStyle(AppTheme.running)
                    Text(commandMessage)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(AppTheme.running)
                    Spacer()
                }
                .padding(10)
                .background(AppTheme.running.opacity(0.12), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
            }

            HStack(spacing: 10) {
                Button {
                    Task { await runCommand(.searchMissing) }
                } label: {
                    Label(arr.searchMissing, systemImage: "magnifyingglass")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .disabled(isRunningCommand)

                Button {
                    Task { await runCommand(.refreshIndex) }
                } label: {
                    Label(arr.refreshIndex, systemImage: "arrow.clockwise")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .disabled(isRunningCommand)
            }
            .font(.caption.bold())
            .lineLimit(1)
            .minimumScaleFactor(0.72)

            HStack(spacing: 10) {
                Button {
                    Task { await runCommand(.rssSync) }
                } label: {
                    Label(arr.rssSync, systemImage: "dot.radiowaves.left.and.right")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .disabled(isRunningCommand)

                Button {
                    Task { await runCommand(.rescanFolders) }
                } label: {
                    Label(arr.rescan, systemImage: "folder.badge.gearshape")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .disabled(isRunningCommand)
            }
            .font(.caption.bold())
            .lineLimit(1)
            .minimumScaleFactor(0.72)

            HStack(spacing: 10) {
                Button {
                    Task { await runCommand(.downloadedScan) }
                } label: {
                    Label(arr.downloadedScan, systemImage: "tray.and.arrow.down")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .disabled(isRunningCommand)

                Button {
                    Task { await runCommand(.healthCheck) }
                } label: {
                    Label(arr.healthCheck, systemImage: "checkmark.shield")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .disabled(isRunningCommand)
            }
            .font(.caption.bold())
            .lineLimit(1)
            .minimumScaleFactor(0.72)
        }
        .padding(16)
        .glassCard()
    }

    private var contentSearchSection: some View {
        let accent = ServiceType.radarr.colors.primary
        let trimmedQuery = lookupQuery.trimmingCharacters(in: .whitespacesAndNewlines)
        let canRunSearch = !isSearchingCatalog && trimmedQuery.count >= 2
        let canClearSearch = !isSearchingCatalog && (!trimmedQuery.isEmpty || !lookupResults.isEmpty || searchErrorMessage != nil)

        return VStack(alignment: .leading, spacing: 14) {
            Text(arr.contentSearchTitle)
                .font(.headline.bold())

            HStack(spacing: 10) {
                Image(systemName: "magnifyingglass")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(accent)

                TextField(arr.contentSearchPlaceholder(ServiceType.radarr.displayName), text: $lookupQuery)
                    .textInputAutocapitalization(.never)
                    .disableAutocorrection(true)
                    .submitLabel(.search)
                    .onSubmit {
                        Task { await runContentSearch() }
                    }

                if !lookupQuery.isEmpty {
                    Button {
                        lookupQuery = ""
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(AppTheme.textMuted)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(arr.clearSearch)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .glassCard(cornerRadius: 14, tint: accent.opacity(0.08))

            HStack(spacing: 10) {
                Button {
                    Task { await runContentSearch() }
                } label: {
                    Label(arr.searchNow, systemImage: isSearchingCatalog ? "hourglass" : "sparkle.magnifyingglass")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.glassProminent)
                .disabled(!canRunSearch)

                Button {
                    lookupQuery = ""
                    lookupResults = []
                    searchErrorMessage = nil
                } label: {
                    Label(arr.clearSearch, systemImage: "xmark")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.glass)
                .disabled(!canClearSearch)
            }
            .font(.subheadline.weight(.semibold))
            .lineLimit(1)
            .minimumScaleFactor(0.75)

            if isSearchingCatalog {
                ProgressView()
                    .controlSize(.small)
                    .tint(accent)
            } else if let searchErrorMessage {
                Text(searchErrorMessage)
                    .font(.caption)
                    .foregroundStyle(AppTheme.danger)
                    .lineLimit(2)
            } else if !trimmedQuery.isEmpty && lookupResults.isEmpty {
                Text(arr.searchNoResults)
                    .font(.caption)
                    .foregroundStyle(AppTheme.textSecondary)
            }

            if !lookupResults.isEmpty {
                VStack(spacing: 8) {
                    ForEach(lookupResults.prefix(8)) { result in
                        Button {
                            selectedLookupResult = result
                        } label: {
                            MediaSearchResultRow(
                                result: MediaSearchResultPresentation(
                                    id: result.id,
                                    title: result.title,
                                    subtitle: result.subtitle,
                                    supporting: result.supporting,
                                    status: result.status.map(localizedSearchStatus),
                                    posterURL: resolvedServiceArtworkURL(result.posterURL, instance: serviceInstance),
                                    artworkHeaders: serviceArtworkHeaders(for: resolvedServiceArtworkURL(result.posterURL, instance: serviceInstance), instance: serviceInstance),
                                    detailsURL: result.detailsURL,
                                    details: result.details,
                                    canRequest: result.requestTmdbId != nil
                                )
                            )
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
        .padding(16)
        .glassCard()
    }
    
    private var queueSection: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(arr.downloadingWithCount(queue.count))
                .font(.title2.bold())
                .padding(.bottom, 4)
                .padding(.leading, 4)
            
            ForEach(queue) { record in
                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        Image(systemName: record.isWarning ? "exclamationmark.triangle.fill" : "arrow.down.circle.fill")
                            .foregroundStyle(record.isWarning ? AppTheme.warning : AppTheme.running)
                            .font(.title3)
                            
                        Text(record.title)
                            .font(.subheadline.bold())
                            .lineLimit(1)
                        Spacer()
                        if let timeleft = record.timeleft, !timeleft.isEmpty, timeleft != "00:00:00" {
                            Text(timeleft)
                                .font(.caption2.bold())
                                .foregroundStyle(AppTheme.info)
                        }
                    }
                    
                    ProgressView(value: record.progress)
                        .tint(record.isWarning ? AppTheme.warning : AppTheme.running)
                        
                    HStack {
                        Text("\(Formatters.formatBytes(record.size - record.sizeleft)) / \(Formatters.formatBytes(record.size))")
                            .font(.caption2.weight(.medium))
                            .foregroundStyle(AppTheme.textSecondary)
                        Spacer()
                    }
                }
                .padding(16)
                .glassCard()
            }
        }
        .padding(.top, 16)
    }
    
    private var moviesSection: some View {
        let visible = moviesExpanded ? latestMovies : Array(latestMovies.prefix(listPreviewCount))
        let remaining = latestMovies.count - min(latestMovies.count, listPreviewCount)
        return VStack(alignment: .leading, spacing: 14) {
            Text(arr.latestAdditions)
                .font(.title2.bold())
                .padding(.bottom, 4)
                .padding(.leading, 4)

            ForEach(visible) { movie in
                Button {
                    selectedLibraryResult = latestMoviePresentation(movie)
                } label: {
                    MediaSearchResultRow(result: latestMoviePresentation(movie))
                }
                .buttonStyle(.plain)
            }

            if remaining > 0 || moviesExpanded {
                Button {
                    withAnimation(.easeInOut(duration: 0.2)) { moviesExpanded.toggle() }
                } label: {
                    HStack(spacing: 6) {
                        Text(moviesExpanded ? arr.showLess : arr.showMore(remaining))
                            .font(.caption.bold())
                        Image(systemName: moviesExpanded ? "chevron.up" : "chevron.down")
                            .font(.caption2.bold())
                    }
                    .foregroundStyle(AppTheme.accent)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.top, 16)
    }

    private func latestMoviePresentation(_ movie: RadarrMovie) -> MediaSearchResultPresentation {
        let subtitle = movie.year.map(String.init)
        let supporting = movie.studio ?? movie.status.capitalized
        let detailsURL: String?
        if let tmdbId = movie.tmdbId {
            detailsURL = "https://www.themoviedb.org/movie/\(tmdbId)"
        } else if let imdbId = movie.imdbId, !imdbId.isEmpty {
            detailsURL = "https://www.imdb.com/title/\(imdbId)/"
        } else {
            detailsURL = nil
        }
        let details = mediaDetailDictionary([
            ("Year", movie.year.map(String.init)),
            ("Status", movie.status.capitalized),
            ("Studio", movie.studio),
            ("Runtime", movie.runtime.map { "\($0) min" }),
            ("Size", movie.sizeOnDisk > 0 ? Formatters.formatBytes(Double(movie.sizeOnDisk)) : nil),
            ("Added", movie.added),
            ("Overview", movie.overview)
        ])
        return MediaSearchResultPresentation(
            id: "movie-\(movie.id)",
            title: movie.title,
            subtitle: subtitle,
            supporting: supporting,
            status: movie.isDownloaded ? arr.searchStatusInLibrary : nil,
            posterURL: resolvedServiceArtworkURL(movie.posterUrl, instance: serviceInstance),
            artworkHeaders: serviceArtworkHeaders(for: resolvedServiceArtworkURL(movie.posterUrl, instance: serviceInstance), instance: serviceInstance),
            detailsURL: detailsURL,
            details: details,
            canRequest: false
        )
    }

    private var historySection: some View {
        let visible = historyExpanded ? history : Array(history.prefix(listPreviewCount))
        let remaining = history.count - min(history.count, listPreviewCount)
        return VStack(alignment: .leading, spacing: 14) {
            Text(arr.recentHistory)
                .font(.title2.bold())
                .padding(.bottom, 4)
                .padding(.leading, 4)

            ForEach(visible) { item in
                let eventType = item.eventType ?? arr.eventFallback
                let sourceTitle = item.sourceTitle ?? "-"
                HStack(spacing: 12) {
                    Image(systemName: eventType.lowercased().contains("download") ? "arrow.down.circle.fill" : "clock.fill")
                        .foregroundStyle(eventType.lowercased().contains("download") ? AppTheme.running : AppTheme.warning)
                        .frame(width: 24)

                    VStack(alignment: .leading, spacing: 4) {
                        Text(sourceTitle)
                            .font(.subheadline.weight(.semibold))
                            .lineLimit(1)
                        Text(eventType)
                            .font(.caption)
                            .foregroundStyle(AppTheme.textSecondary)
                    }
                    Spacer()
                }
                .padding(14)
                .glassCard()
            }

            if remaining > 0 || historyExpanded {
                Button {
                    withAnimation(.easeInOut(duration: 0.2)) { historyExpanded.toggle() }
                } label: {
                    HStack(spacing: 6) {
                        Text(historyExpanded ? arr.showLess : arr.showMore(remaining))
                            .font(.caption.bold())
                        Image(systemName: historyExpanded ? "chevron.up" : "chevron.down")
                            .font(.caption2.bold())
                    }
                    .foregroundStyle(AppTheme.accent)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.top, 16)
    }

    private var healthSection: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(arr.health)
                .font(.title2.bold())
                .padding(.bottom, 4)
                .padding(.leading, 4)

            ForEach(healthMessages.prefix(5), id: \.self) { message in
                HStack(alignment: .top, spacing: 10) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundStyle(AppTheme.warning)
                    Text(message)
                        .font(.subheadline)
                        .foregroundStyle(AppTheme.textSecondary)
                    Spacer()
                }
                .padding(14)
                .glassCard()
            }
        }
        .padding(.top, 16)
    }

    private var upcomingSection: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(arr.upcoming)
                .font(.title2.bold())
                .padding(.bottom, 4)
                .padding(.leading, 4)

            if upcomingTitles.isEmpty {
                Text(arr.noUpcoming)
                    .font(.subheadline)
                    .foregroundStyle(AppTheme.textSecondary)
                    .padding(.horizontal, 4)
            } else {
                ForEach(upcomingTitles, id: \.self) { title in
                    HStack(spacing: 10) {
                        Image(systemName: "calendar")
                            .foregroundStyle(AppTheme.info)
                        Text(title)
                            .font(.subheadline.weight(.semibold))
                            .lineLimit(1)
                        Spacer()
                    }
                    .padding(14)
                    .glassCard()
                }
            }
        }
        .padding(.top, 16)
    }

    @MainActor
    private func runCommand(_ action: RadarrAction) async {
        guard let client else { return }
        if isRunningCommand { return }

        isRunningCommand = true
        defer { isRunningCommand = false }

        do {
            let controlled: MediaServiceControlledAction
            let message: String
            switch action {
            case .searchMissing: controlled = .commandSearchMissing; message = arr.movieSearchQueued
            case .refreshIndex: controlled = .commandRefresh; message = arr.movieRefreshQueued
            case .rssSync: controlled = .commandRssSync; message = arr.rssSyncQueued
            case .rescanFolders: controlled = .commandRescan; message = arr.rescanQueued
            case .downloadedScan: controlled = .commandDownloadedScan; message = arr.downloadedScanQueued
            case .healthCheck: controlled = .commandHealthCheck; message = arr.healthCheckQueued
            }
            try await executeControlledMediaAction(
                controlled,
                serviceType: .radarr,
                instanceId: instanceId,
                targetRef: "command/all",
                coordinator: servicesStore.controlledActionCoordinator
            ) {
                switch action {
                case .searchMissing: try await client.triggerMoviesSearch()
                case .refreshIndex: try await client.refreshMovieIndex()
                case .rssSync: try await client.triggerRSSSync()
                case .rescanFolders: try await client.rescanMovieFolders()
                case .downloadedScan: try await client.triggerDownloadedMoviesScan()
                case .healthCheck: try await client.triggerHealthCheck()
                }
            }
            commandMessage = message
            HapticManager.success()
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                commandMessage = nil
            }
            await fetchData(silent: true)
        } catch {
            state = .error(.custom(error.localizedDescription))
            HapticManager.error()
        }
    }

    @MainActor
    private func runContentSearch() async {
        guard let client else { return }
        let term = lookupQuery.trimmingCharacters(in: .whitespacesAndNewlines)
        guard term.count >= 2 else {
            lookupResults = []
            searchErrorMessage = nil
            return
        }
        if isSearchingCatalog { return }

        isSearchingCatalog = true
        defer { isSearchingCatalog = false }
        searchErrorMessage = nil

        do {
            lookupResults = try await client.searchMovies(term: term, limit: 20)
        } catch {
            lookupResults = []
            searchErrorMessage = error.localizedDescription
        }
    }

    @MainActor
    private func runContentRequest(_ result: RadarrLookupMovie, selection: ArrRequestSelection? = nil) async {
        guard let client else { return }
        if pendingLookupRequestIds.contains(result.id) { return }
        pendingLookupRequestIds.insert(result.id)
        defer { pendingLookupRequestIds.remove(result.id) }

        do {
            try await client.requestMovieFromLookup(result, selection: selection)
            commandMessage = arr.requestQueued
            HapticManager.success()
            selectedLookupResult = nil
            pendingRequestConfiguration = nil
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                commandMessage = nil
            }
            await fetchData(silent: false)
        } catch APIError.requestConfigurationRequired(let configuration) {
            let pending = RadarrPendingRequestConfiguration(result: result, configuration: configuration)
            selectedLookupResult = nil
            DispatchQueue.main.async {
                pendingRequestConfiguration = pending
            }
        } catch {
            state = .error(.custom(error.localizedDescription))
            HapticManager.error()
        }
    }

    private func localizedSearchStatus(_ status: String) -> String {
        switch status.lowercased() {
        case "in library":
            return arr.searchStatusInLibrary
        case "monitored":
            return arr.searchStatusMonitored
        case "unmonitored":
            return arr.searchStatusUnmonitored
        case "ended":
            return arr.searchStatusEnded
        case "pending":
            return arr.searchStatusPending
        case "approved":
            return arr.searchStatusApproved
        case "available":
            return arr.searchStatusAvailable
        case "processing":
            return arr.searchStatusProcessing
        default:
            return status
        }
    }
}

private enum RadarrAction {
    case searchMissing
    case refreshIndex
    case rssSync
    case rescanFolders
    case downloadedScan
    case healthCheck
}

private struct RadarrPendingRequestConfiguration: Identifiable {
    let id = UUID()
    let result: RadarrLookupMovie
    let configuration: ArrRequestConfiguration
}
