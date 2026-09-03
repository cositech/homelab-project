import SwiftUI

struct QbittorrentDashboard: View {
    let instanceId: UUID
    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer
    @Environment(\.scenePhase) private var scenePhase
    
    @State private var client: QbittorrentAPIClient?
    @State private var transferInfo: QbittorrentTransferInfo?
    @State private var torrents: [QbittorrentTorrent] = []
    @State private var state: LoadableState<Void> = .idle
    @State private var selectedFilter: QbittorrentFilter = .all
    @State private var searchQuery: String = ""
    @State private var isFetching = false
    @State private var isViewVisible = false
    @State private var isRunningTorrentAction = false
    @State private var actionMessage: String?
    @State private var pendingConfirmation: QbConfirmation?
    private var arr: ArrStrings { localizer.arr }

    private struct QbConfirmation: Identifiable {
        let id = UUID()
        let title: String
        let perform: @MainActor () -> Void
    }
    
    // Keep transfer stats closer to real time while refreshing the heavier torrent list less often.
    private let transferTimer = Timer.publish(every: 8, on: .main, in: .common).autoconnect()
    private let listTimer = Timer.publish(every: 24, on: .main, in: .common).autoconnect()

    var body: some View {
        ServiceDashboardLayout(
            serviceType: .qbittorrent,
            instanceId: instanceId,
            state: state,
            onRefresh: { await fetchData(silent: false, includeTorrents: true) }
        ) {
            if let transferInfo {
                transferStatsSection(transferInfo: transferInfo)
            }

            if let actionMessage {
                actionMessageBanner(actionMessage)
            }

            filterSection
            
            if !displayedTorrents.isEmpty {
                torrentsListSection
            } else if case .loaded = state {
                Text(localizer.t.noData)
                    .font(.headline)
                    .foregroundStyle(AppTheme.textSecondary)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding(.top, 40)
            }
        }
        .task {
            self.client = await servicesStore.qbittorrentClient(instanceId: instanceId)
            await fetchData(silent: false, includeTorrents: true)
        }
        .onAppear { isViewVisible = true }
        .onDisappear { isViewVisible = false }
        .onReceive(transferTimer) { _ in
            guard scenePhase == .active, isViewVisible else { return }
            Task { await fetchData(silent: true, includeTorrents: false) }
        }
        .onReceive(listTimer) { _ in
            guard scenePhase == .active, isViewVisible else { return }
            Task { await fetchData(silent: true, includeTorrents: true) }
        }
        .confirmationDialog(
            pendingConfirmation?.title ?? localizer.t.actionConfirm,
            isPresented: Binding(
                get: { pendingConfirmation != nil },
                set: { if !$0 { pendingConfirmation = nil } }
            ),
            titleVisibility: .visible,
            presenting: pendingConfirmation
        ) { confirmation in
            Button(localizer.t.confirm, role: .destructive) {
                let perform = confirmation.perform
                pendingConfirmation = nil
                perform()
            }
            Button(localizer.t.cancel, role: .cancel) {
                pendingConfirmation = nil
            }
        } message: { _ in
            Text(localizer.t.actionConfirmMessage)
        }
    }
    
    @MainActor
    private func fetchData(silent: Bool, includeTorrents: Bool) async {
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
            self.transferInfo = try await client.getTransferInfo()
            if includeTorrents {
                self.torrents = try await client.getTorrents(filter: "all")
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
    
    private func transferStatsSection(transferInfo: QbittorrentTransferInfo) -> some View {
        VStack(spacing: 12) {
            HStack {
                Text(arr.connection)
                    .font(.caption.bold())
                    .foregroundStyle(AppTheme.textSecondary)
                Spacer()
                Text(transferInfo.connection_status.capitalized)
                    .font(.caption.weight(.heavy))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 4)
                    .background(transferInfo.connection_status.lowercased() == "connected" ? AppTheme.running.opacity(0.15) : AppTheme.warning.opacity(0.15), in: Capsule())
                    .foregroundStyle(transferInfo.connection_status.lowercased() == "connected" ? AppTheme.running : AppTheme.warning)
            }

            HStack(spacing: 16) {
                statCard(
                    title: arr.download,
                    value: Formatters.formatBytes(Double(transferInfo.dl_info_speed)) + "/s",
                    icon: "arrow.down.circle.fill",
                    color: AppTheme.running
                )
                
                statCard(
                    title: arr.upload,
                    value: Formatters.formatBytes(Double(transferInfo.up_info_speed)) + "/s",
                    icon: "arrow.up.circle.fill",
                    color: AppTheme.info
                )
            }

            HStack(spacing: 12) {
                secondaryStatCard(
                    title: arr.dhtLabel,
                    value: "\(transferInfo.dht_nodes ?? 0)",
                    icon: "point.3.connected.trianglepath.dotted",
                    color: AppTheme.info
                )
                secondaryStatCard(
                    title: arr.altSpeedLabel,
                    value: transferInfo.use_alt_speed_limits == true ? localizer.t.yes : localizer.t.no,
                    icon: transferInfo.use_alt_speed_limits == true ? "tortoise.fill" : "gauge.with.needle",
                    color: transferInfo.use_alt_speed_limits == true ? AppTheme.warning : AppTheme.running
                )
            }

            if let freeDisk = transferInfo.free_space_on_disk {
                secondaryStatCard(
                    title: arr.diskFreeLabel,
                    value: Formatters.formatBytes(Double(freeDisk)),
                    icon: "internaldrive.fill",
                    color: AppTheme.primary,
                    emphasized: true
                )
            }
        }
    }

    private func actionMessageBanner(_ text: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: "checkmark.circle.fill")
                .foregroundStyle(AppTheme.running)
            Text(text)
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.running)
            Spacer()
        }
        .padding(12)
        .background(AppTheme.running.opacity(0.12), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
    }

    private var filterSection: some View {
        VStack(spacing: 10) {
            Picker(arr.filterAll, selection: $selectedFilter) {
                ForEach(QbittorrentFilter.allCases, id: \.self) { filter in
                    Text(filterTitle(filter)).tag(filter)
                }
            }
            .pickerStyle(.segmented)

            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass")
                    .foregroundStyle(AppTheme.textMuted)
                TextField(arr.searchTorrents, text: $searchQuery)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .padding(.top, 8)
    }

    private var displayedTorrents: [QbittorrentTorrent] {
        let filtered = torrents.filter { torrent in
            let matchesFilter: Bool = switch selectedFilter {
            case .all:
                true
            case .downloading:
                torrent.isDownloading || torrent.isUploading || torrent.isChecking
            case .completed:
                torrent.progress >= 0.999 && !torrent.isDownloading && !torrent.isChecking && !torrent.isError
            case .paused:
                torrent.isPaused
            }
            guard matchesFilter else { return false }

            let query = searchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !query.isEmpty else { return true }
            return torrent.name.localizedCaseInsensitiveContains(query) ||
                torrent.hash.localizedCaseInsensitiveContains(query)
        }

        return filtered.sorted { lhs, rhs in
            let lhsRank = statusRank(lhs)
            let rhsRank = statusRank(rhs)
            if lhsRank != rhsRank { return lhsRank < rhsRank }
            if lhs.progress != rhs.progress { return lhs.progress > rhs.progress }
            return lhs.name.localizedCaseInsensitiveCompare(rhs.name) == .orderedAscending
        }
    }

    private func statusRank(_ torrent: QbittorrentTorrent) -> Int {
        if torrent.isError { return 0 }
        if torrent.isDownloading { return 1 }
        if torrent.isUploading { return 2 }
        if torrent.isChecking { return 3 }
        if torrent.isPaused { return 4 }
        return 5
    }
    
    private func statCard(title: String, value: String, icon: String, color: Color) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Image(systemName: icon)
                    .foregroundStyle(color)
                    .font(.title3)
                Text(title)
                    .font(.caption.bold())
                    .foregroundStyle(AppTheme.textSecondary)
            }
            Text(value)
                .font(.headline.weight(.heavy))
                .lineLimit(1)
                .minimumScaleFactor(0.8)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .glassCard()
    }

    private func secondaryStatCard(
        title: String,
        value: String,
        icon: String,
        color: Color,
        emphasized: Bool = false
    ) -> some View {
        HStack(alignment: .center, spacing: 12) {
            Image(systemName: icon)
                .font(.body.weight(.semibold))
                .foregroundStyle(color)
                .frame(width: 34, height: 34)
                .background(color.opacity(0.14), in: RoundedRectangle(cornerRadius: 10, style: .continuous))

            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.caption.bold())
                    .foregroundStyle(AppTheme.textMuted)
                    .lineLimit(2)
                    .minimumScaleFactor(0.8)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Text(value)
                    .font(emphasized ? .subheadline.weight(.heavy) : .subheadline.weight(.semibold))
                    .foregroundStyle(emphasized ? .primary : color)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }

            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, minHeight: 92, maxHeight: 92, alignment: .leading)
        .padding(14)
        .glassCard(tint: color.opacity(0.08))
    }
    
    private var torrentsListSection: some View {
        VStack(spacing: 14) {
            HStack {
                Text(arr.torrents)
                    .font(.title2.bold())
                Spacer()
                Button {
                    guardedAction(
                        .transferToggleAltSpeed,
                        targetRef: "transfer/all",
                        confirmTitle: arr.altLimitsToggled,
                        successMessage: arr.altLimitsToggled
                    ) { try await $0.toggleAlternativeSpeedLimits() }
                } label: {
                    Image(systemName: "speedometer")
                        .foregroundStyle(AppTheme.info)
                        .padding(8)
                        .background(AppTheme.info.opacity(0.15), in: Circle())
                }
                .buttonStyle(.plain)
                .disabled(isRunningTorrentAction)

                Button {
                    guardedAction(
                        .transferResumeAll,
                        targetRef: "transfer/all",
                        confirmTitle: arr.allResumed,
                        successMessage: arr.allResumed
                    ) { try await $0.resumeAll() }
                } label: {
                    Image(systemName: "play.fill")
                        .foregroundStyle(AppTheme.running)
                        .padding(8)
                        .background(AppTheme.running.opacity(0.15), in: Circle())
                }
                .buttonStyle(.plain)
                .disabled(isRunningTorrentAction)
                .padding(.horizontal, 4)

                Button {
                    guardedAction(
                        .transferPauseAll,
                        targetRef: "transfer/all",
                        confirmTitle: arr.allPaused,
                        successMessage: arr.allPaused
                    ) { try await $0.pauseAll() }
                } label: {
                    Image(systemName: "pause.fill")
                        .foregroundStyle(AppTheme.warning)
                        .padding(8)
                        .background(AppTheme.warning.opacity(0.15), in: Circle())
                }
                .buttonStyle(.plain)
                .disabled(isRunningTorrentAction)
            }
            .padding(.bottom, 8)
            .padding(.horizontal, 4)
            
            ForEach(displayedTorrents) { torrent in
                torrentRow(torrent)
            }
        }
        .padding(.top, 24)
    }
    
    private func torrentRow(_ torrent: QbittorrentTorrent) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top) {
                if torrent.isDownloading {
                    Image(systemName: "arrow.down.app.fill")
                        .foregroundStyle(AppTheme.running)
                } else if torrent.isUploading {
                    Image(systemName: "arrow.up.circle.fill")
                        .foregroundStyle(AppTheme.info)
                } else if torrent.isPaused {
                    Image(systemName: "pause.circle.fill")
                        .foregroundStyle(AppTheme.warning)
                } else if torrent.isChecking {
                    Image(systemName: "arrow.2.squarepath")
                        .foregroundStyle(AppTheme.primary)
                } else if torrent.isError {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundStyle(AppTheme.stopped)
                } else {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundStyle(AppTheme.running)
                }
                
                Text(torrent.name)
                    .font(.subheadline.bold())
                    .lineLimit(2)
                    .fixedSize(horizontal: false, vertical: true)
                
                Spacer()
                
                Menu {
                    let torrentTarget = "torrent/\(torrent.hash)"
                    Button(torrent.isPaused ? localizer.t.actionResume : localizer.t.actionPause) {
                        if torrent.isPaused {
                            guardedAction(
                                .torrentResume,
                                targetRef: torrentTarget,
                                confirmTitle: localizer.t.actionResume,
                                successMessage: arr.torrentResumed
                            ) { try await $0.resumeTorrent(hash: torrent.hash) }
                        } else {
                            guardedAction(
                                .torrentPause,
                                targetRef: torrentTarget,
                                confirmTitle: localizer.t.actionPause,
                                successMessage: arr.torrentPaused
                            ) { try await $0.pauseTorrent(hash: torrent.hash) }
                        }
                    }

                    Button(arr.recheck) {
                        guardedAction(
                            .torrentRecheck,
                            targetRef: torrentTarget,
                            confirmTitle: arr.recheck,
                            successMessage: arr.recheckStarted
                        ) { try await $0.recheckTorrent(hash: torrent.hash) }
                    }

                    Button(arr.reannounce) {
                        guardedAction(
                            .torrentReannounce,
                            targetRef: torrentTarget,
                            confirmTitle: arr.reannounce,
                            successMessage: arr.reannounceQueued
                        ) { try await $0.reannounceTorrent(hash: torrent.hash) }
                    }

                    Button(localizer.t.delete) {
                        guardedAction(
                            .torrentDelete,
                            targetRef: torrentTarget,
                            confirmTitle: localizer.t.delete,
                            successMessage: arr.torrentDeleted
                        ) { try await $0.deleteTorrent(hash: torrent.hash, deleteFiles: false) }
                    }

                    Button(arr.deleteWithData, role: .destructive) {
                        guardedAction(
                            .torrentDeleteWithData,
                            targetRef: torrentTarget,
                            confirmTitle: arr.deleteWithData,
                            successMessage: arr.torrentAndDataDeleted
                        ) { try await $0.deleteTorrent(hash: torrent.hash, deleteFiles: true) }
                    }
                } label: {
                    Image(systemName: "ellipsis")
                        .font(.title3)
                        .foregroundStyle(AppTheme.textMuted)
                        .padding(4)
                }
                .disabled(isRunningTorrentAction)
            }
            
            ProgressView(value: min(max(torrent.progress, 0.0), 1.0))
                .tint(torrent.isError ? AppTheme.stopped : (torrent.isPaused ? AppTheme.textMuted : AppTheme.primary))
            
            HStack {
                Text("\(Formatters.formatBytes(Double(torrent.downloaded))) / \(Formatters.formatBytes(Double(torrent.size)))")
                    .font(.caption2.weight(.medium))
                    .foregroundStyle(AppTheme.textSecondary)
                
                Spacer()

                if let ratio = torrent.ratio {
                    Text("\(arr.ratioLabel): \(String(format: "%.2f", ratio))")
                        .font(.caption2.bold())
                        .foregroundStyle(AppTheme.info)
                        .padding(.trailing, 2)
                }
                
                if torrent.dlspeed > 0 {
                    HStack(spacing: 2) {
                        Image(systemName: "arrow.down")
                        Text("\(Formatters.formatBytes(Double(torrent.dlspeed)))/s")
                    }
                    .font(.caption2.bold())
                    .foregroundStyle(AppTheme.running)
                }
                if torrent.upspeed > 0 {
                    HStack(spacing: 2) {
                        Image(systemName: "arrow.up")
                        Text("\(Formatters.formatBytes(Double(torrent.upspeed)))/s")
                    }
                    .font(.caption2.bold())
                    .foregroundStyle(AppTheme.info)
                    .padding(.leading, 6)
                }
            }

            HStack {
                if torrent.eta > 0 {
                    Text("\(arr.etaLabel): \(formatETA(seconds: torrent.eta))")
                        .font(.caption2)
                        .foregroundStyle(AppTheme.textSecondary)
                }
                Spacer()
                if let seeds = torrent.num_seeds, let leechs = torrent.num_leechs {
                    Text("\(arr.seedsLeechersLabel): \(seeds)/\(leechs)")
                        .font(.caption2)
                        .foregroundStyle(AppTheme.textSecondary)
                }
            }

            let category = torrent.category ?? ""
            let tags = torrent.tags ?? ""
            if !category.isEmpty || !tags.isEmpty {
                HStack(spacing: 6) {
                    if !category.isEmpty {
                        Text(category)
                            .font(.caption2.weight(.semibold))
                            .foregroundStyle(AppTheme.primary)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(AppTheme.primary.opacity(0.12), in: Capsule())
                    }
                    if !tags.isEmpty {
                        Text(tags)
                            .font(.caption2)
                            .foregroundStyle(AppTheme.textSecondary)
                            .lineLimit(1)
                    }
                    Spacer()
                }
            }
        }
        .padding(16)
        .glassCard()
    }

    // Gate every qBittorrent mutation on the controlled-action policy. Medium- and high-risk actions
    // present an explicit confirmation first; low-risk actions run immediately. All of them execute
    // through the shared coordinator and audit path with a normalized, payload-free target identity.
    @MainActor
    private func guardedAction(
        _ action: QbittorrentControlledAction,
        targetRef: String,
        confirmTitle: String,
        successMessage: String,
        _ apiCall: @escaping @Sendable (QbittorrentAPIClient) async throws -> Void
    ) {
        let run: @MainActor () -> Void = {
            Task {
                await performGuarded(
                    action,
                    targetRef: targetRef,
                    successMessage: successMessage,
                    apiCall
                )
            }
        }
        if action.requiresConfirmation {
            pendingConfirmation = QbConfirmation(title: confirmTitle, perform: run)
        } else {
            run()
        }
    }

    @MainActor
    private func performGuarded(
        _ action: QbittorrentControlledAction,
        targetRef: String,
        successMessage: String,
        _ apiCall: @escaping @Sendable (QbittorrentAPIClient) async throws -> Void
    ) async {
        guard !isRunningTorrentAction else { return }
        isRunningTorrentAction = true
        defer { isRunningTorrentAction = false }

        do {
            HapticManager.light()
            let client = try requireClient()
            try await executeControlledAction(
                action,
                targetRef: targetRef,
                confirmed: action.requiresConfirmation
            ) {
                try await apiCall(client)
            }
            actionMessage = successMessage
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                actionMessage = nil
            }
            await fetchData(silent: false, includeTorrents: true)
        } catch {
            state = .error(.custom(error.localizedDescription))
            HapticManager.error()
        }
    }

    private func executeControlledAction(
        _ action: QbittorrentControlledAction,
        targetRef: String,
        confirmed: Bool,
        operation: @escaping @Sendable () async throws -> Void
    ) async throws {
        let audit = await servicesStore.controlledActionCoordinator.execute(
            request: action.request(
                instanceId: instanceId,
                targetRef: targetRef,
                confirmed: confirmed
            ),
            actorRole: .admin,
            providerCapabilities: ProviderRegistry.descriptor(for: .qbittorrent).capabilities
        ) {
            do {
                try await operation()
            } catch is CancellationError {
                throw CancellationError()
            } catch let error as ControlledActionOperationError {
                throw error
            } catch {
                throw QbittorrentControlledOperationFailure.map(error)
            }
        }
        guard audit.state == .succeeded else {
            throw APIError.custom(audit.reasonCode)
        }
    }

    private func requireClient() throws -> QbittorrentAPIClient {
        guard let client else {
            throw APIError.notConfigured
        }
        return client
    }

    private func filterTitle(_ filter: QbittorrentFilter) -> String {
        switch filter {
        case .all: return arr.filterAll
        case .downloading: return arr.filterActive
        case .completed: return arr.filterDone
        case .paused: return arr.filterPaused
        }
    }

    private func formatETA(seconds: Int64) -> String {
        guard seconds > 0 else { return "--" }
        let minutes = seconds / 60
        if minutes < 60 { return "\(minutes)m" }
        let hours = minutes / 60
        if hours < 24 { return "\(hours)h \(minutes % 60)m" }
        let days = hours / 24
        return "\(days)d \(hours % 24)h"
    }
}

private enum QbittorrentFilter: CaseIterable {
    case all
    case downloading
    case completed
    case paused
}
