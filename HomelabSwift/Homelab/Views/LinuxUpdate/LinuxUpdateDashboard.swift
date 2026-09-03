import SwiftUI

private actor LinuxUpdateActionOutcomeBox {
    private var outcome: LinuxUpdateActionOutcome?
    func store(_ outcome: LinuxUpdateActionOutcome) { self.outcome = outcome }
    func value() -> LinuxUpdateActionOutcome? { outcome }
}

private struct PendingLinuxUpdateAction: Sendable {
    let action: LinuxUpdateActionKind
    let systemId: Int
}

struct LinuxUpdateDashboard: View {
    let instanceId: UUID

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer

    @State private var selectedInstanceId: UUID
    @State private var state: LoadableState<Void> = .idle
    @State private var stats: LinuxUpdateDashboardStats?
    @State private var systems: [LinuxUpdateSystem] = []
    @State private var selectedFilter: LinuxUpdateFilter = .all

    @State private var selectedSystemId: Int?
    @State private var selectedSystemDetail: LoadableState<LinuxUpdateSystemDetailResponse> = .idle
    @State private var isRunningAction = false
    @State private var isRunningDashboardAction = false
    @State private var actionMessage: String?
    @State private var pendingAction: PendingLinuxUpdateAction?

    private let linuxUpdateColor = ServiceType.linuxUpdate.colors.primary

    init(instanceId: UUID) {
        self.instanceId = instanceId
        _selectedInstanceId = State(initialValue: instanceId)
    }

    var body: some View {
        ServiceDashboardLayout(
            serviceType: .linuxUpdate,
            instanceId: selectedInstanceId,
            state: state,
            onRefresh: { await fetchDashboard(forceLoading: false) }
        ) {
            instancePicker
            overviewSection

            if filteredSystems.isEmpty && !state.isLoading {
                emptyState
            } else {
                ForEach(filteredSystems) { system in
                    Button {
                        openSystemDetail(system)
                    } label: {
                        systemCard(system)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .navigationTitle(ServiceType.linuxUpdate.displayName)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    Button {
                        Task { await runDashboardAction(.checkAll) }
                    } label: {
                        Label(localizer.t.linuxUpdateActionCheckAll, systemImage: "checklist")
                    }

                    Button {
                        Task { await runDashboardAction(.refreshCache) }
                    } label: {
                        Label(localizer.t.linuxUpdateActionRefreshCache, systemImage: "arrow.triangle.2.circlepath")
                    }

                    Button {
                        Task { await fetchDashboard(forceLoading: false) }
                    } label: {
                        Label(localizer.t.refresh, systemImage: "arrow.clockwise")
                    }
                } label: {
                    if isRunningDashboardAction {
                        ProgressView()
                            .controlSize(.small)
                    } else {
                        Image(systemName: "ellipsis.circle")
                    }
                }
                .disabled(isRunningDashboardAction)
            }
        }
        .task(id: selectedInstanceId) {
            await fetchDashboard(forceLoading: true)
        }
        .sheet(isPresented: detailSheetPresented) {
            detailSheet
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
        }
        .alert(
            ServiceType.linuxUpdate.displayName,
            isPresented: Binding(
                get: { actionMessage != nil },
                set: { isPresented in
                    if !isPresented { actionMessage = nil }
                }
            )
        ) {
            Button(localizer.t.done) {
                actionMessage = nil
            }
        } message: {
            Text(actionMessage ?? "")
        }
        .confirmationDialog(
            localizer.t.actionConfirm,
            isPresented: Binding(
                get: { pendingAction != nil },
                set: { if !$0 { pendingAction = nil } }
            ),
            titleVisibility: .visible
        ) {
            if let pendingAction {
                Button(actionLabel(pendingAction.action), role: .destructive) {
                    let pending = pendingAction
                    self.pendingAction = nil
                    Task {
                        await runAction(pending.action, systemId: pending.systemId, confirmed: true)
                    }
                }
            }
            Button(localizer.t.cancel, role: .cancel) { pendingAction = nil }
        } message: {
            Text(localizer.t.actionConfirmMessage)
        }
    }

    private var filteredSystems: [LinuxUpdateSystem] {
        switch selectedFilter {
        case .all:
            return systems
        case .updates:
            return systems.filter { $0.updateCount > 0 }
        case .issues:
            return systems.filter { $0.hasCheckIssue || $0.isReachableFlag == false }
        case .reboot:
            return systems.filter { $0.needsRebootFlag }
        }
    }

    private var detailSheetPresented: Binding<Bool> {
        Binding(
            get: { selectedSystemId != nil },
            set: { newValue in
                if !newValue {
                    selectedSystemId = nil
                    selectedSystemDetail = .idle
                }
            }
        )
    }

    @ViewBuilder
    private var detailSheet: some View {
        if let systemId = selectedSystemId {
            ScrollView {
                VStack(alignment: .leading, spacing: AppTheme.gridSpacing) {
                    switch selectedSystemDetail {
                    case .idle, .loading:
                        VStack(spacing: 12) {
                            ProgressView()
                            Text(localizer.t.loading)
                                .font(.caption)
                                .foregroundStyle(AppTheme.textMuted)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 36)

                    case .error(let error):
                        VStack(spacing: 10) {
                            Image(systemName: "exclamationmark.triangle")
                                .font(.title3)
                                .foregroundStyle(AppTheme.warning)
                            Text(error.localizedDescription)
                                .font(.subheadline)
                                .foregroundStyle(AppTheme.textSecondary)
                                .multilineTextAlignment(.center)
                            Button(localizer.t.retry) {
                                Task { await fetchSystemDetail(systemId: systemId, forceLoading: true) }
                            }
                            .buttonStyle(.borderedProminent)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 24)

                    case .offline:
                        VStack(spacing: 10) {
                            Image(systemName: "wifi.slash")
                                .font(.title3)
                                .foregroundStyle(AppTheme.warning)
                            Text(localizer.t.error)
                                .font(.subheadline)
                                .foregroundStyle(AppTheme.textSecondary)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 24)

                    case .loaded(let detail):
                        detailContent(detail)
                    }
                }
                .padding(AppTheme.padding)
            }
        }
    }

    private var instancePicker: some View {
        let instances = servicesStore.instances(for: .linuxUpdate)
        return Group {
            if instances.count > 1 {
                VStack(alignment: .leading, spacing: 12) {
                    Text(localizer.t.dashboardInstances)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(AppTheme.textMuted)
                        .textCase(.uppercase)

                    ForEach(instances) { instance in
                        Button {
                            HapticManager.light()
                            selectedInstanceId = instance.id
                            servicesStore.setPreferredInstance(id: instance.id, for: .linuxUpdate)
                            selectedFilter = .all
                            systems = []
                            stats = nil
                            selectedSystemId = nil
                            selectedSystemDetail = .idle
                        } label: {
                            HStack(spacing: 10) {
                                Circle()
                                    .fill(instance.id == selectedInstanceId ? linuxUpdateColor : AppTheme.textMuted.opacity(0.4))
                                    .frame(width: 10, height: 10)

                                VStack(alignment: .leading, spacing: 2) {
                                    Text(instance.displayLabel)
                                        .font(.subheadline.weight(.semibold))
                                    Text(instance.url)
                                        .font(.caption)
                                        .foregroundStyle(AppTheme.textMuted)
                                        .lineLimit(1)
                                }

                                Spacer()
                            }
                            .padding(14)
                            .glassCard(tint: instance.id == selectedInstanceId ? linuxUpdateColor.opacity(0.1) : nil)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    private var overviewSection: some View {
        let currentStats = stats ?? LinuxUpdateDashboardStats(total: 0, upToDate: 0, needsUpdates: 0, unreachable: 0, checkIssues: 0, totalUpdates: 0, needsReboot: 0)

        return VStack(alignment: .leading, spacing: 12) {
            Text(localizer.t.summaryTitle)
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)
                .textCase(.uppercase)

            LazyVGrid(
                columns: [GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8)],
                spacing: 8
            ) {
                summaryCard(
                    title: localizer.t.summarySystemsOnline,
                    value: "\(currentStats.total)",
                    icon: "server.rack",
                    iconColor: linuxUpdateColor,
                    filter: .all
                )

                summaryCard(
                    title: localizer.t.patchmonUpdates,
                    value: Formatters.formatNumber(currentStats.totalUpdates),
                    icon: "arrow.triangle.2.circlepath",
                    iconColor: AppTheme.warning,
                    filter: .updates
                )

                summaryCard(
                    title: localizer.t.statusUnreachable,
                    value: "\(currentStats.unreachable)",
                    icon: "wifi.exclamationmark",
                    iconColor: AppTheme.danger,
                    filter: .issues
                )

                summaryCard(
                    title: localizer.t.patchmonReboot,
                    value: "\(currentStats.needsReboot)",
                    icon: "arrow.clockwise.circle",
                    iconColor: linuxUpdateColor,
                    filter: .reboot
                )
            }
        }
    }

    private func summaryCard(
        title: String,
        value: String,
        icon: String,
        iconColor: Color,
        filter: LinuxUpdateFilter
    ) -> some View {
        let isSelected = selectedFilter == filter

        return Button {
            HapticManager.light()
            withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) {
                selectedFilter = (selectedFilter == filter && filter != .all) ? .all : filter
            }
        } label: {
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 6) {
                    Image(systemName: icon)
                        .font(.caption.bold())
                        .foregroundStyle(iconColor)

                    Text(title)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(AppTheme.textSecondary)
                        .lineLimit(2)
                        .minimumScaleFactor(0.78)
                        .fixedSize(horizontal: false, vertical: true)
                }

                Text(value)
                    .font(.title2.bold())
                    .foregroundStyle(.primary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.75)
            }
            .frame(maxWidth: .infinity, minHeight: 92, alignment: .topLeading)
            .padding(AppTheme.innerPadding)
            .background {
                RoundedRectangle(cornerRadius: AppTheme.cardRadius, style: .continuous)
                    .fill(Color.clear)
                    .glassEffect(
                        Glass.regular.tint(isSelected ? linuxUpdateColor.opacity(0.14) : .clear),
                        in: RoundedRectangle(cornerRadius: AppTheme.cardRadius, style: .continuous)
                    )
            }
            .overlay {
                RoundedRectangle(cornerRadius: AppTheme.cardRadius, style: .continuous)
                    .stroke(
                        isSelected ? linuxUpdateColor.opacity(0.55) : .white.opacity(0.04),
                        lineWidth: isSelected ? 1.8 : 1
                    )
            }
            .contentShape(RoundedRectangle(cornerRadius: AppTheme.cardRadius, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    private func systemCard(_ system: LinuxUpdateSystem) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text(system.name)
                    .font(.headline)
                    .lineLimit(1)

                Spacer()

                if system.securityCount > 0 {
                    metricBadge(value: system.securityCount, tint: AppTheme.danger)
                }
                if system.updateCount > 0 {
                    metricBadge(value: system.updateCount, tint: AppTheme.warning)
                }

                Image(systemName: "chevron.right")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(AppTheme.textMuted)
            }

            Text(system.osSummary)
                .font(.subheadline)
                .foregroundStyle(AppTheme.textSecondary)
                .lineLimit(2)

            HStack(spacing: 8) {
                miniMetric(title: localizer.t.patchmonSecurity, value: system.securityCount, tint: AppTheme.danger)
                miniMetric(title: localizer.t.patchmonUpdates, value: system.updateCount, tint: AppTheme.warning)
                miniMetric(title: localizer.t.patchmonHostGroups, value: system.keptBackCount, tint: AppTheme.info)
            }

            HStack(spacing: 8) {
                statusPill(for: system)

                if system.needsRebootFlag {
                    Label(localizer.t.patchmonReboot, systemImage: "arrow.clockwise.circle")
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(linuxUpdateColor)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(linuxUpdateColor.opacity(0.12), in: Capsule())
                }

                Spacer()

                if let age = system.cacheAge, !age.isEmpty {
                    Text(age)
                        .font(.caption2)
                        .foregroundStyle(AppTheme.textMuted)
                }
            }

            if let op = system.activeOperation {
                HStack(spacing: 6) {
                    ProgressView()
                        .controlSize(.small)
                    Text(opLabel(op))
                        .font(.caption2)
                        .foregroundStyle(AppTheme.textMuted)
                }
            }
        }
        .padding(16)
        .glassCard(tint: tintForSystem(system))
    }

    private func detailContent(_ detail: LinuxUpdateSystemDetailResponse) -> some View {
        VStack(alignment: .leading, spacing: AppTheme.gridSpacing) {
            VStack(alignment: .leading, spacing: 4) {
                Text(detail.system.name)
                    .font(.title2.weight(.bold))
                Text(detail.system.osSummary)
                    .font(.subheadline)
                    .foregroundStyle(AppTheme.textMuted)
            }

            LazyVGrid(columns: twoColumnGrid, spacing: 10) {
                actionButton(title: localizer.t.refresh, icon: "arrow.clockwise", tint: linuxUpdateColor, enabled: !isRunningAction) {
                    Task { await fetchSystemDetail(systemId: detail.system.id, forceLoading: false) }
                }

                actionButton(title: localizer.t.linuxUpdateActionCheck, icon: "checkmark.seal", tint: AppTheme.running, enabled: !isRunningAction) {
                    requestAction(.check, systemId: detail.system.id)
                }

                actionButton(title: localizer.t.linuxUpdateActionUpgrade, icon: "arrow.up.circle", tint: AppTheme.info, enabled: !isRunningAction) {
                    requestAction(.upgradeAll, systemId: detail.system.id)
                }

                if detail.system.supportsFullUpgrade {
                    actionButton(title: localizer.t.linuxUpdateActionFullUpgrade, icon: "arrow.triangle.2.circlepath", tint: AppTheme.warning, enabled: !isRunningAction) {
                        requestAction(.fullUpgrade, systemId: detail.system.id)
                    }
                }

                actionButton(title: localizer.t.patchmonReboot, icon: "power", tint: linuxUpdateColor, enabled: !isRunningAction) {
                    requestAction(.reboot, systemId: detail.system.id)
                }
            }

            if isRunningAction {
                HStack(spacing: 8) {
                    ProgressView()
                    Text(localizer.t.loading)
                        .font(.caption)
                        .foregroundStyle(AppTheme.textMuted)
                }
            }

            detailInfoSection(detail)
            updatesSection(detail)
            hiddenUpdatesSection(detail)
            historySection(detail)
        }
    }

    private func detailInfoSection(_ detail: LinuxUpdateSystemDetailResponse) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            sectionTitle(localizer.t.patchmonSystem, trailing: nil)

            VStack(spacing: 8) {
                detailRow(localizer.t.detailHostname, detail.system.hostname)
                detailRow(localizer.t.patchmonArchitecture, detail.system.arch ?? localizer.t.notAvailable)
                detailRow(localizer.t.patchmonUpdates, "\(detail.system.updateCount)")
                detailRow(localizer.t.patchmonSecurity, "\(detail.system.securityCount)")
                detailRow(localizer.t.patchmonHostGroups, "\(detail.system.keptBackCount)")
                detailRow(localizer.t.patchmonLastUpdate, detail.system.cacheAge ?? localizer.t.notAvailable)
                detailRow(localizer.t.statusOnline, statusTitle(for: detail.system))
                detailRow(localizer.t.healthchecksLastPing, detail.system.lastCheck?.completedAt ?? detail.system.lastCheck?.startedAt ?? localizer.t.notAvailable)
            }
            .padding(14)
            .glassCard()
        }
    }

    private func updatesSection(_ detail: LinuxUpdateSystemDetailResponse) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            sectionTitle(localizer.t.patchmonUpdates, trailing: "\(detail.updates.count)")

            if detail.updates.isEmpty {
                placeholder(localizer.t.patchmonNoPackages)
            } else {
                ForEach(detail.updates) { update in
                    packageRow(update, allowUpgrade: true, systemId: detail.system.id)
                }
            }
        }
    }

    private func hiddenUpdatesSection(_ detail: LinuxUpdateSystemDetailResponse) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            sectionTitle(localizer.t.linuxUpdateHiddenUpdates, trailing: "\(detail.hiddenUpdates.count)")

            if detail.hiddenUpdates.isEmpty {
                placeholder(localizer.t.linuxUpdateNoHiddenUpdates)
            } else {
                ForEach(detail.hiddenUpdates) { update in
                    packageRow(update, allowUpgrade: false, systemId: detail.system.id)
                }
            }
        }
    }

    private func historySection(_ detail: LinuxUpdateSystemDetailResponse) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            sectionTitle(localizer.t.patchmonReports, trailing: "\(detail.history.count)")

            if detail.history.isEmpty {
                placeholder(localizer.t.patchmonNoReports)
            } else {
                ForEach(detail.history) { entry in
                    historyRow(entry)
                }
            }
        }
    }

    private func packageRow(_ update: LinuxUpdatePackageUpdate, allowUpgrade: Bool, systemId: Int) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(update.packageName)
                        .font(.subheadline.weight(.semibold))
                        .lineLimit(1)

                    Text("\(update.currentVersion ?? "-") -> \(update.newVersion ?? "-")")
                        .font(.caption)
                        .foregroundStyle(AppTheme.textMuted)
                }

                Spacer()

                if update.isSecurityFlag {
                    metricBadge(value: 1, tint: AppTheme.danger)
                }
                if update.isKeptBackFlag {
                    Text(localizer.t.linuxUpdateKeptBack)
                        .font(.caption2.bold())
                        .foregroundStyle(AppTheme.warning)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(AppTheme.warning.opacity(0.15), in: Capsule())
                }
            }

            HStack(spacing: 8) {
                Text(update.pkgManager.isEmpty ? localizer.t.dockhandSystemLabel.lowercased() : update.pkgManager)
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(AppTheme.textMuted)

                Spacer()

                if allowUpgrade {
                    Button {
                        requestAction(.upgradePackage(update.packageName), systemId: systemId)
                    } label: {
                        Text(localizer.t.linuxUpdateActionUpgrade)
                            .font(.caption2.weight(.semibold))
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(AppTheme.info.opacity(0.14), in: Capsule())
                    }
                    .buttonStyle(.plain)
                    .disabled(isRunningAction)
                } else {
                    Text(localizer.t.linuxUpdateHidden)
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(AppTheme.textMuted)
                }
            }
        }
        .padding(14)
        .glassCard()
    }

    private func historyRow(_ entry: LinuxUpdateHistoryEntry) -> some View {
        let color = historyStatusColor(entry.status)

        return VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Text(historyActionTitle(entry.action))
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(1)

                Spacer()

                Text(entry.status.capitalized)
                    .font(.caption2.bold())
                    .foregroundStyle(color)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(color.opacity(0.14), in: Capsule())
            }

            if let completedAt = entry.completedAt, !completedAt.isEmpty {
                Text(completedAt)
                    .font(.caption2)
                    .foregroundStyle(AppTheme.textMuted)
            }

            if let body = firstNonEmpty(entry.error, entry.output) {
                Text(body)
                    .font(.caption)
                    .foregroundStyle(AppTheme.textSecondary)
                    .lineLimit(6)
            }
        }
        .padding(14)
        .glassCard()
    }

    private func actionButton(
        title: String,
        icon: String,
        tint: Color,
        enabled: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 7) {
                Image(systemName: icon)
                    .font(.subheadline.weight(.bold))
                Text(title)
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.75)
                    .allowsTightening(true)
            }
            .foregroundStyle(enabled ? tint : AppTheme.textMuted)
            .frame(maxWidth: .infinity, minHeight: 48)
            .padding(.horizontal, 10)
            .background(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(enabled ? tint.opacity(0.14) : Color.gray.opacity(0.12))
            )
            .contentShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.65)
    }

    private func sectionTitle(_ title: String, trailing: String?) -> some View {
        HStack {
            Text(title)
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)
                .textCase(.uppercase)
            Spacer()
            if let trailing {
                Text(trailing)
                    .font(.caption2)
                    .foregroundStyle(AppTheme.textMuted)
            }
        }
    }

    private func detailRow(_ label: String, _ value: String) -> some View {
        HStack(alignment: .top) {
            Text(label)
                .font(.caption)
                .foregroundStyle(AppTheme.textMuted)
            Spacer()
            Text(value)
                .font(.caption)
                .foregroundStyle(AppTheme.textSecondary)
                .multilineTextAlignment(.trailing)
        }
    }

    private func placeholder(_ text: String) -> some View {
        Text(text)
            .font(.caption)
            .foregroundStyle(AppTheme.textMuted)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(14)
            .glassCard()
    }

    private func metricBadge(value: Int, tint: Color) -> some View {
        Text("\(value)")
            .font(.caption2.bold())
            .foregroundStyle(tint)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(tint.opacity(0.15), in: Capsule())
    }

    private func miniMetric(title: String, value: Int, tint: Color) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text("\(value)")
                .font(.subheadline.bold())
                .foregroundStyle(tint)
            Text(title)
                .font(.caption2)
                .foregroundStyle(AppTheme.textMuted)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(10)
        .background(AppTheme.surface.opacity(0.7), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
    }

    private func statusPill(for system: LinuxUpdateSystem) -> some View {
        let color: Color
        let title: String

        if system.hasCheckIssue {
            color = AppTheme.warning
            title = localizer.t.error
        } else if system.isReachableFlag == false {
            color = AppTheme.danger
            title = localizer.t.statusUnreachable
        } else if system.updateCount > 0 {
            color = AppTheme.warning
            title = localizer.t.patchmonUpdates
        } else {
            color = AppTheme.running
            title = localizer.t.statusOnline
        }

        return HStack(spacing: 5) {
            Circle().fill(color).frame(width: 6, height: 6)
            Text(title)
                .font(.caption2.bold())
                .lineLimit(1)
        }
        .foregroundStyle(color)
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(color.opacity(0.12), in: Capsule())
    }

    private func tintForSystem(_ system: LinuxUpdateSystem) -> Color? {
        if system.hasCheckIssue {
            return AppTheme.warning.opacity(0.08)
        }
        if system.isReachableFlag == false {
            return AppTheme.danger.opacity(0.08)
        }
        if system.updateCount > 0 {
            return AppTheme.warning.opacity(0.05)
        }
        return nil
    }

    private func openSystemDetail(_ system: LinuxUpdateSystem) {
        selectedSystemId = system.id
        selectedSystemDetail = .loading
        Task {
            await fetchSystemDetail(systemId: system.id, forceLoading: true)
        }
    }

    @MainActor
    private func fetchSystemDetail(systemId: Int, forceLoading: Bool) async {
        if forceLoading || selectedSystemDetail.value == nil {
            selectedSystemDetail = .loading
        }

        do {
            guard let client = await servicesStore.linuxUpdateClient(instanceId: selectedInstanceId) else {
                selectedSystemDetail = .error(.notConfigured)
                return
            }

            let detail = try await client.getSystemDetail(systemId: systemId)
            guard selectedSystemId == systemId else { return }
            selectedSystemDetail = .loaded(detail)
        } catch let error as APIError {
            selectedSystemDetail = .error(error)
        } catch {
            selectedSystemDetail = .error(.networkError(error))
        }
    }

    private func requestAction(_ action: LinuxUpdateActionKind, systemId: Int) {
        if action.controlledAction.requiresConfirmation {
            pendingAction = PendingLinuxUpdateAction(action: action, systemId: systemId)
        } else {
            Task { await runAction(action, systemId: systemId, confirmed: false) }
        }
    }

    @MainActor
    private func runAction(_ action: LinuxUpdateActionKind, systemId: Int, confirmed: Bool) async {
        await runControlledAction(
            action.controlledAction,
            systemId: systemId,
            packageName: action.packageName,
            confirmed: confirmed,
            dashboardAction: false
        )
    }

    @MainActor
    private func runDashboardAction(_ action: LinuxUpdateDashboardActionKind) async {
        await runControlledAction(
            action.controlledAction,
            systemId: nil,
            packageName: nil,
            confirmed: false,
            dashboardAction: true
        )
    }

    @MainActor
    private func runControlledAction(
        _ action: LinuxUpdateControlledAction,
        systemId: Int?,
        packageName: String?,
        confirmed: Bool,
        dashboardAction: Bool
    ) async {
        guard !isRunningAction && !isRunningDashboardAction else { return }
        guard let client = await servicesStore.linuxUpdateClient(instanceId: selectedInstanceId) else {
            actionMessage = APIError.notConfigured.localizedDescription
            return
        }

        if dashboardAction {
            isRunningDashboardAction = true
        } else {
            isRunningAction = true
        }
        defer {
            if dashboardAction {
                isRunningDashboardAction = false
            } else {
                isRunningAction = false
            }
        }

        let outcomeBox = LinuxUpdateActionOutcomeBox()
        let audit = await servicesStore.controlledActionCoordinator.execute(
            request: action.request(
                instanceId: selectedInstanceId,
                targetRef: action.targetRef(systemId: systemId, packageName: packageName),
                confirmed: confirmed
            ),
            actorRole: .admin,
            providerCapabilities: ProviderRegistry.descriptor(for: .linuxUpdate).capabilities
        ) {
            do {
                let outcome: LinuxUpdateActionOutcome
                switch action {
                case .checkAll:
                    outcome = try await client.runCheckAll()
                case .refreshCache:
                    outcome = try await client.runRefreshCache()
                case .checkSystem:
                    outcome = try await client.runCheck(systemId: systemId ?? 0)
                case .upgradePackage:
                    outcome = try await client.runUpgradePackage(
                        systemId: systemId ?? 0,
                        packageName: packageName ?? ""
                    )
                case .upgradeAll:
                    outcome = try await client.runUpgradeAll(systemId: systemId ?? 0)
                case .fullUpgrade:
                    outcome = try await client.runFullUpgrade(systemId: systemId ?? 0)
                case .reboot:
                    outcome = try await client.runReboot(systemId: systemId ?? 0)
                }
                guard outcome.success else {
                    throw ControlledActionOperationError(
                        reasonCode: "linux-update-provider-reported-failure",
                        disposition: .nonRetryable
                    )
                }
                await outcomeBox.store(outcome)
            } catch is CancellationError {
                throw CancellationError()
            } catch let error as ControlledActionOperationError {
                throw error
            } catch {
                throw ControlledActionOperationError(
                    reasonCode: "linux-update-outcome-indeterminate",
                    disposition: .nonRetryable
                )
            }
        }

        guard audit.state == .succeeded else {
            actionMessage = audit.reasonCode
            return
        }

        actionMessage = await outcomeBox.value()?.message ?? ServiceType.linuxUpdate.displayName
        await fetchDashboard(forceLoading: false)
        if let systemId {
            await fetchSystemDetail(systemId: systemId, forceLoading: false)
        } else if let selectedSystemId {
            await fetchSystemDetail(systemId: selectedSystemId, forceLoading: false)
        }
    }

    private func actionLabel(_ action: LinuxUpdateActionKind) -> String {
        switch action {
        case .check:
            return localizer.t.linuxUpdateActionCheck
        case .upgradeAll:
            return localizer.t.linuxUpdateActionUpgrade
        case .fullUpgrade:
            return localizer.t.linuxUpdateActionFullUpgrade
        case .reboot:
            return localizer.t.patchmonReboot
        case .upgradePackage(let packageName):
            return "\(localizer.t.linuxUpdateActionPackageUpgrade): \(packageName)"
        }
    }

    private func statusTitle(for system: LinuxUpdateSystem) -> String {
        if system.hasCheckIssue {
            return localizer.t.error
        }
        if system.isReachableFlag == false {
            return localizer.t.statusUnreachable
        }
        if system.updateCount > 0 {
            return localizer.t.patchmonUpdates
        }
        return localizer.t.statusOnline
    }

    private func historyActionTitle(_ action: String) -> String {
        switch action {
        case "check":
            return localizer.t.linuxUpdateActionCheck
        case "upgrade_all":
            return localizer.t.linuxUpdateActionUpgrade
        case "full_upgrade_all":
            return localizer.t.linuxUpdateActionFullUpgrade
        case "upgrade_package":
            return localizer.t.linuxUpdateActionPackageUpgrade
        case "reboot":
            return localizer.t.patchmonReboot
        default:
            return action.replacingOccurrences(of: "_", with: " ").capitalized
        }
    }

    private func historyStatusColor(_ status: String) -> Color {
        switch status.lowercased() {
        case "success", "done":
            return AppTheme.running
        case "warning":
            return AppTheme.warning
        case "failed", "error":
            return AppTheme.danger
        default:
            return AppTheme.textMuted
        }
    }

    private func firstNonEmpty(_ values: String?...) -> String? {
        for value in values {
            let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            if !trimmed.isEmpty {
                return trimmed
            }
        }
        return nil
    }

    private var emptyState: some View {
        VStack(spacing: 10) {
            Image(systemName: "tray")
                .font(.title2)
                .foregroundStyle(AppTheme.textMuted)
            Text(localizer.t.noData)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(AppTheme.textSecondary)
            Text(selectedFilter == .all ? localizer.t.launcherNotConfigured : localizer.t.healthchecksNoChecks)
                .font(.caption)
                .foregroundStyle(AppTheme.textMuted)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 32)
        .glassCard()
    }

    @MainActor
    private func fetchDashboard(forceLoading: Bool) async {
        if forceLoading || stats == nil {
            state = .loading
        }

        do {
            guard let client = await servicesStore.linuxUpdateClient(instanceId: selectedInstanceId) else {
                state = .error(.notConfigured)
                return
            }

            async let statsTask = client.getDashboardStats()
            async let systemsTask = client.getDashboardSystems()

            let (fetchedStats, fetchedSystems) = try await (statsTask, systemsTask)
            stats = fetchedStats
            systems = fetchedSystems
            state = .loaded(())

            if let selectedSystemId,
               fetchedSystems.contains(where: { $0.id == selectedSystemId }) {
                await fetchSystemDetail(systemId: selectedSystemId, forceLoading: false)
            } else {
                self.selectedSystemId = nil
                selectedSystemDetail = .idle
            }
        } catch let error as APIError {
            state = .error(error)
        } catch {
            state = .error(.networkError(error))
        }
    }

    private func opLabel(_ operation: LinuxUpdateActiveOperation) -> String {
        switch operation.type {
        case "check":
            return localizer.t.linuxUpdateActionCheck
        case "upgrade_all", "full_upgrade_all", "upgrade_package":
            return localizer.t.linuxUpdateActionUpgrade
        case "reboot":
            return localizer.t.patchmonReboot
        default:
            return operation.type.replacingOccurrences(of: "_", with: " ").capitalized
        }
    }
}

private enum LinuxUpdateFilter {
    case all
    case updates
    case issues
    case reboot
}

private enum LinuxUpdateActionKind: Sendable {
    case check
    case upgradeAll
    case fullUpgrade
    case reboot
    case upgradePackage(String)

    var controlledAction: LinuxUpdateControlledAction {
        switch self {
        case .check: return .checkSystem
        case .upgradeAll: return .upgradeAll
        case .fullUpgrade: return .fullUpgrade
        case .reboot: return .reboot
        case .upgradePackage: return .upgradePackage
        }
    }

    var packageName: String? {
        if case .upgradePackage(let packageName) = self { return packageName }
        return nil
    }
}

private enum LinuxUpdateDashboardActionKind: Sendable {
    case checkAll
    case refreshCache

    var controlledAction: LinuxUpdateControlledAction {
        switch self {
        case .checkAll: return .checkAll
        case .refreshCache: return .refreshCache
        }
    }
}
