import SwiftUI
import Charts

private actor TechnitiumActionOutcomeBox {
    private var outcome: TechnitiumActionOutcome?
    func store(_ outcome: TechnitiumActionOutcome) { self.outcome = outcome }
    func value() -> TechnitiumActionOutcome? { outcome }
}

private enum PendingTechnitiumAction {
    case disableBlocking
    case temporaryDisable(minutes: Int)
    case addBlockedDomain(domain: String)
    case removeBlockedDomain(domain: String)

    var controlledAction: TechnitiumControlledAction {
        switch self {
        case .disableBlocking: return .disableBlocking
        case .temporaryDisable: return .temporaryDisable
        case .addBlockedDomain: return .addBlockedDomain
        case .removeBlockedDomain: return .removeBlockedDomain
        }
    }

    var targetRef: String {
        switch self {
        case .disableBlocking, .temporaryDisable:
            return "protection/global"
        case .addBlockedDomain(let domain), .removeBlockedDomain(let domain):
            return "blocked-domain/\(domain.trimmingCharacters(in: .whitespacesAndNewlines).lowercased())"
        }
    }

    var minutes: Int? {
        if case .temporaryDisable(let minutes) = self { return minutes }
        return nil
    }

    var domain: String? {
        switch self {
        case .addBlockedDomain(let domain), .removeBlockedDomain(let domain): return domain
        default: return nil
        }
    }

    var isDestructive: Bool {
        switch self {
        case .disableBlocking, .temporaryDisable, .addBlockedDomain: return true
        case .removeBlockedDomain: return false
        }
    }

    func label(using translations: Translations) -> String {
        switch self {
        case .disableBlocking:
            return translations.technitiumDisableUntilManual
        case .temporaryDisable(let minutes):
            return "\(translations.technitiumDisableBlocking) (\(minutes) \(translations.technitiumMinutes))"
        case .addBlockedDomain(let domain):
            return "\(translations.technitiumBlockDomain): \(domain)"
        case .removeBlockedDomain(let domain):
            return "\(translations.delete): \(domain)"
        }
    }
}

private enum TechnitiumPanel: String, CaseIterable, Identifiable {
    case clients
    case domains
    case blocked
    case zones

    var id: String { rawValue }
}

private struct TechnitiumChartPoint: Identifiable {
    let index: Int
    let value: Double
    var id: Int { index }
}

private struct TechnitiumChartSnapshot {
    let labels: [String]
    let allowedLabel: String
    let blockedLabel: String?
    let allowedValues: [Double]
    let blockedValues: [Double]
    let count: Int
    let yValues: [Int]
    let xValues: [Int]
}

struct TechnitiumDashboard: View {
    let instanceId: UUID

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer

    @State private var selectedInstanceId: UUID
    @State private var state: LoadableState<Void> = .idle
    @State private var dashboard: TechnitiumDashboardData?
    @State private var selectedRange: TechnitiumStatsRange = .lastHour
    @State private var selectedPanel: TechnitiumPanel = .clients
    @State private var isRunningAction = false
    @State private var actionMessage: String?

    @State private var showDisableOptions = false
    @State private var showCustomDisablePrompt = false
    @State private var customDisableMinutes = "15"
    @State private var showAddDomainPrompt = false
    @State private var domainToBlock = ""
    @State private var pendingAction: PendingTechnitiumAction?

    private let technitiumColor = ServiceType.technitium.colors.primary

    init(instanceId: UUID) {
        self.instanceId = instanceId
        _selectedInstanceId = State(initialValue: instanceId)
    }

    var body: some View {
        ServiceDashboardLayout(
            serviceType: .technitium,
            instanceId: selectedInstanceId,
            state: state,
            onRefresh: { await fetchDashboard(forceLoading: false) }
        ) {
            instancePicker
            rangeSelector
            blockingCard
            summarySection
            chartSection
            panelSelector
            panelContent
        }
        .navigationTitle(ServiceType.technitium.displayName)
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                Button {
                    Task { await fetchDashboard(forceLoading: false) }
                } label: {
                    Image(systemName: "arrow.clockwise")
                }
                .disabled(state.isLoading || isRunningAction)

                Menu {
                    Button {
                        Task { await runForceUpdate() }
                    } label: {
                        Label(localizer.t.technitiumUpdateBlockLists, systemImage: "arrow.triangle.2.circlepath")
                    }

                    Button {
                        showDisableOptions = true
                    } label: {
                        Label(localizer.t.technitiumDisableBlocking, systemImage: "shield.slash")
                    }

                    Button {
                        showAddDomainPrompt = true
                    } label: {
                        Label(localizer.t.technitiumBlockDomain, systemImage: "hand.raised.fill")
                    }
                } label: {
                    Image(systemName: "ellipsis.circle")
                }
                .disabled(isRunningAction)
            }
        }
        .task(id: fetchTaskKey) {
            await fetchDashboard(forceLoading: true)
        }
        .confirmationDialog(localizer.t.technitiumDisableBlocking, isPresented: $showDisableOptions, titleVisibility: .visible) {
            Button(localizer.t.technitiumDisableFor5Minutes) {
                pendingAction = .temporaryDisable(minutes: 5)
            }
            Button(localizer.t.technitiumDisableFor30Minutes) {
                pendingAction = .temporaryDisable(minutes: 30)
            }
            Button(localizer.t.piholeDisableCustom) {
                showCustomDisablePrompt = true
            }
            Button(localizer.t.technitiumDisableUntilManual, role: .destructive) {
                pendingAction = .disableBlocking
            }
            Button(localizer.t.cancel, role: .cancel) { }
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
                Button(
                    pendingAction.label(using: localizer.translations),
                    role: pendingAction.isDestructive ? .destructive : nil
                ) {
                    let action = pendingAction
                    self.pendingAction = nil
                    Task { await runPendingAction(action) }
                }
            }
            Button(localizer.t.cancel, role: .cancel) { pendingAction = nil }
        } message: {
            Text(localizer.t.actionConfirmMessage)
        }
        .alert(localizer.t.technitiumCustomDisableTimer, isPresented: $showCustomDisablePrompt) {
            TextField(localizer.t.technitiumMinutes, text: $customDisableMinutes)
                .keyboardType(.numberPad)
            Button(localizer.t.cancel, role: .cancel) { }
            Button(localizer.t.confirm) {
                if let minutes = Int(customDisableMinutes.trimmingCharacters(in: .whitespacesAndNewlines)), minutes > 0 {
                    pendingAction = .temporaryDisable(minutes: minutes)
                }
            }
        } message: {
            Text(localizer.t.technitiumCustomDisableDescription)
        }
        .alert(localizer.t.technitiumBlockDomain, isPresented: $showAddDomainPrompt) {
            TextField("example.com", text: $domainToBlock)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
            Button(localizer.t.cancel, role: .cancel) { domainToBlock = "" }
            Button(localizer.t.confirm) {
                let trimmed = domainToBlock.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !trimmed.isEmpty else { return }
                domainToBlock = ""
                pendingAction = .addBlockedDomain(domain: trimmed)
            }
        } message: {
            Text(localizer.t.technitiumBlockDomainDescription)
        }
        .alert(
            ServiceType.technitium.displayName,
            isPresented: Binding(
                get: { actionMessage != nil },
                set: { isPresented in if !isPresented { actionMessage = nil } }
            )
        ) {
            Button(localizer.t.done) { actionMessage = nil }
        } message: {
            Text(actionMessage ?? "")
        }
    }

    private var fetchTaskKey: String {
        "\(selectedInstanceId.uuidString)-\(selectedRange.rawValue)"
    }

    private var instancePicker: some View {
        let instances = servicesStore.instances(for: .technitium)
        return Group {
            if instances.count > 1 {
                VStack(alignment: .leading, spacing: 12) {
                    Text(localizer.t.dashboardInstances.sentenceCased())
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(AppTheme.textMuted)

                    ForEach(instances) { instance in
                        Button {
                            HapticManager.light()
                            selectedInstanceId = instance.id
                            servicesStore.setPreferredInstance(id: instance.id, for: .technitium)
                            dashboard = nil
                            selectedPanel = .clients
                        } label: {
                            HStack(spacing: 10) {
                                Circle()
                                    .fill(instance.id == selectedInstanceId ? technitiumColor : AppTheme.textMuted.opacity(0.4))
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
                            .glassCard(tint: instance.id == selectedInstanceId ? technitiumColor.opacity(0.1) : nil)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    private var rangeSelector: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(localizer.t.technitiumRange.sentenceCased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            HStack(spacing: 8) {
                ForEach(TechnitiumStatsRange.allCases, id: \.rawValue) { range in
                    rangeChip(range)
                }
            }
        }
    }

    private func rangeChip(_ range: TechnitiumStatsRange) -> some View {
        let selected = selectedRange == range
        return Button {
            guard selectedRange != range else { return }
            HapticManager.light()
            selectedRange = range
        } label: {
            Text(rangeTitle(range))
                .font(.caption.weight(.semibold))
                .foregroundStyle(selected ? technitiumColor : AppTheme.textSecondary)
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(
                    Capsule()
                        .fill(selected ? technitiumColor.opacity(0.16) : AppTheme.surface.opacity(0.9))
                )
                .overlay(
                    Capsule()
                        .stroke(selected ? technitiumColor.opacity(0.55) : .white.opacity(0.05), lineWidth: 1)
                )
        }
        .buttonStyle(.plain)
    }

    private var blockingCard: some View {
        let enabled = dashboard?.settings.enableBlocking ?? true
        return Button {
            if enabled {
                showDisableOptions = true
            } else {
                Task { await runSetBlocking(enabled: true) }
            }
        } label: {
            HStack(spacing: 14) {
                Image(systemName: enabled ? "shield.fill" : "shield.slash.fill")
                    .font(.title2)
                    .foregroundStyle(enabled ? AppTheme.running : AppTheme.stopped)
                    .frame(width: 54, height: 54)
                    .background(
                        (enabled ? AppTheme.running : AppTheme.stopped).opacity(0.12),
                        in: RoundedRectangle(cornerRadius: 14, style: .continuous)
                    )

                VStack(alignment: .leading, spacing: 3) {
                    Text(localizer.t.technitiumDnsBlocking)
                        .font(.subheadline.weight(.semibold))
                    Text(enabled ? localizer.t.technitiumEnabled : localizer.t.technitiumDisabled)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(enabled ? AppTheme.running : AppTheme.stopped)
                    if let until = dashboard?.settings.temporaryDisableBlockingTill, !until.isEmpty {
                        Text(String(format: localizer.t.technitiumDisabledUntil, until))
                            .font(.caption2)
                            .foregroundStyle(AppTheme.textMuted)
                            .lineLimit(1)
                    }
                }

                Spacer()

                Text(enabled ? localizer.t.statusOn : localizer.t.statusOff)
                    .font(.caption.bold())
                    .foregroundStyle(.white)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 7)
                    .background((enabled ? AppTheme.running : AppTheme.stopped), in: Capsule())
            }
            .padding(16)
            .glassCard(tint: (enabled ? AppTheme.running : AppTheme.stopped).opacity(0.05))
        }
        .buttonStyle(.plain)
        .disabled(isRunningAction)
    }

    private var summarySection: some View {
        let summary = dashboard?.summary ?? .init(
            totalQueries: 0,
            totalBlocked: 0,
            totalClients: 0,
            blockedZones: 0,
            cacheEntries: 0,
            zones: 0,
            blockListZones: 0,
            totalNoError: 0,
            totalNxDomain: 0,
            totalServerFailure: 0
        )

        return VStack(alignment: .leading, spacing: 10) {
            Text(localizer.t.summaryTitle.sentenceCased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            LazyVGrid(columns: twoColumnGrid, spacing: 10) {
                summaryCard(
                    icon: "magnifyingglass",
                    tint: technitiumColor,
                    title: localizer.t.summaryQueryTotal,
                    value: Formatters.formatNumber(summary.totalQueries)
                )

                summaryCard(
                    icon: "hand.raised.fill",
                    tint: AppTheme.stopped,
                    title: localizer.t.technitiumBlockedQueries,
                    value: Formatters.formatNumber(summary.totalBlocked)
                )

                summaryCard(
                    icon: "person.3.fill",
                    tint: AppTheme.running,
                    title: localizer.t.piholeClients,
                    value: Formatters.formatNumber(summary.totalClients)
                )

                summaryCard(
                    icon: "globe.badge.chevron.backward",
                    tint: AppTheme.warning,
                    title: localizer.t.technitiumBlockedZones,
                    value: Formatters.formatNumber(summary.blockedZones)
                )
            }
        }
    }

    private func summaryCard(
        icon: String,
        tint: Color,
        title: String,
        value: String
    ) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Image(systemName: icon)
                .font(.body)
                .foregroundStyle(tint)
                .frame(width: 34, height: 34)
                .background(tint.opacity(0.12), in: RoundedRectangle(cornerRadius: 10, style: .continuous))

            Text(value)
                .font(.title3.bold())
                .lineLimit(1)
                .minimumScaleFactor(0.75)

            Text(title)
                .font(.caption2.weight(.medium))
                .foregroundStyle(AppTheme.textSecondary)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .frame(minHeight: 104, alignment: .topLeading)
        .padding(12)
        .glassCard()
    }

    @ViewBuilder
    private var chartSection: some View {
        if let dashboard, let chart = makeChartSnapshot(from: dashboard) {
            VStack(alignment: .leading, spacing: 10) {
                Text(localizer.t.piholeQueryActivity.sentenceCased())
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(AppTheme.textMuted)

                VStack(spacing: 12) {
                    if chart.count > 1 {
                        Chart {
                            ForEach(0..<chart.count, id: \.self) { idx in
                                let allowed = chart.allowedValues[idx]
                                let blocked = chart.blockedValues[idx]

                                // Allowed bar (bottom)
                                BarMark(
                                    x: .value("Time", idx),
                                    y: .value("Queries", allowed),
                                    width: .fixed(8)
                                )
                                .foregroundStyle(AppTheme.running.opacity(0.72))

                                // Blocked bar stacked on top
                                if blocked > 0 {
                                    BarMark(
                                        x: .value("Time", idx),
                                        yStart: .value("Allowed", allowed),
                                        yEnd: .value("Total", allowed + blocked),
                                        width: .fixed(8)
                                    )
                                    .foregroundStyle(AppTheme.stopped.opacity(0.82))
                                }
                            }
                        }
                        .chartXAxis {
                            AxisMarks(values: chart.xValues) { value in
                                AxisValueLabel {
                                    if let idx = value.as(Int.self),
                                       idx >= 0,
                                       idx < chart.labels.count {
                                        Text(chart.labels[idx])
                                            .font(.caption2)
                                            .foregroundStyle(AppTheme.textMuted)
                                    }
                                }
                                AxisGridLine().foregroundStyle(AppTheme.textMuted.opacity(0.15))
                            }
                        }
                        .chartYAxis {
                            AxisMarks(position: .leading, values: chart.yValues) { value in
                                AxisGridLine().foregroundStyle(AppTheme.textMuted.opacity(0.15))
                                AxisValueLabel {
                                    if let number = value.as(Int.self) {
                                        Text(Formatters.formatNumber(number))
                                            .font(.caption2)
                                            .foregroundStyle(AppTheme.textMuted)
                                    }
                                }
                            }
                        }
                        .frame(height: 140)
                    } else {
                        Text(localizer.t.noData)
                            .font(.subheadline)
                            .foregroundStyle(AppTheme.textMuted)
                            .frame(maxWidth: .infinity, alignment: .center)
                            .padding(.vertical, 22)
                    }

                    HStack(spacing: 16) {
                        Spacer()
                        chartLegendDot(chart.allowedLabel, color: AppTheme.running.opacity(0.72))
                        if let blockedLabel = chart.blockedLabel {
                            chartLegendDot(blockedLabel, color: AppTheme.stopped.opacity(0.82))
                        }
                        Spacer()
                    }
                }
                .padding(14)
                .glassCard()
            }
        }
    }

    private func makeChartSnapshot(from dashboard: TechnitiumDashboardData) -> TechnitiumChartSnapshot? {
        guard let allowedSeries = dashboard.chartSeries.first else { return nil }
        let blockedSeries = dashboard.chartSeries.dropFirst().first
        let count = min(
            allowedSeries.values.count,
            blockedSeries?.values.count ?? allowedSeries.values.count
        )
        guard count > 0 else { return nil }

        let allowedValues = Array(allowedSeries.values.prefix(count))
        let blockedValues: [Double]
        if let blockedSeries {
            blockedValues = Array(blockedSeries.values.prefix(count))
        } else {
            blockedValues = Array(repeating: 0, count: count)
        }
        let stackedValues = zip(allowedValues, blockedValues).map { Int($0 + $1) }
        let fallbackMax = Int(allowedValues.max() ?? 0)
        let maxValue = max(1, stackedValues.max() ?? fallbackMax)

        return TechnitiumChartSnapshot(
            labels: dashboard.chartLabels,
            allowedLabel: allowedSeries.label,
            blockedLabel: blockedSeries?.label,
            allowedValues: allowedValues,
            blockedValues: blockedValues,
            count: count,
            yValues: Array(Set([0, maxValue / 2, maxValue])).sorted(),
            xValues: Array(Set([0, count / 2, max(0, count - 1)])).sorted()
        )
    }

    private var panelSelector: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(localizer.t.detailInfo.sentenceCased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            HStack(spacing: 8) {
                ForEach(TechnitiumPanel.allCases) { panel in
                    Button {
                        selectedPanel = panel
                    } label: {
                        Text(panelTitle(panel))
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(selectedPanel == panel ? technitiumColor : AppTheme.textSecondary)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .background(
                                Capsule()
                                    .fill(selectedPanel == panel ? technitiumColor.opacity(0.16) : AppTheme.surface.opacity(0.9))
                            )
                            .overlay(
                                Capsule()
                                    .stroke(selectedPanel == panel ? technitiumColor.opacity(0.55) : .white.opacity(0.05), lineWidth: 1)
                            )
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    @ViewBuilder
    private var panelContent: some View {
        if let dashboard {
            switch selectedPanel {
            case .clients:
                if dashboard.topClients.isEmpty {
                    placeholderCard(localizer.t.technitiumNoClients)
                } else {
                    let maxHits = max(1, dashboard.topClients.map(\.hits).max() ?? 1)
                    ForEach(dashboard.topClients.prefix(20)) { client in
                        clientRow(client, maxHits: maxHits)
                    }
                }

            case .domains:
                if dashboard.topDomains.isEmpty {
                    placeholderCard(localizer.t.technitiumNoDomains)
                } else {
                    let maxHits = max(1, dashboard.topDomains.map(\.hits).max() ?? 1)
                    ForEach(dashboard.topDomains.prefix(20)) { domain in
                        domainRow(domain, maxHits: maxHits, accent: technitiumColor)
                    }
                }

            case .blocked:
                if dashboard.topBlockedDomains.isEmpty && dashboard.blockedDomains.isEmpty {
                    placeholderCard(localizer.t.technitiumNoBlockedDomains)
                } else {
                    if !dashboard.topBlockedDomains.isEmpty {
                        let maxHits = max(1, dashboard.topBlockedDomains.map(\.hits).max() ?? 1)
                        ForEach(dashboard.topBlockedDomains.prefix(12)) { domain in
                            domainRow(domain, maxHits: maxHits, accent: AppTheme.stopped)
                        }
                    }

                    ForEach(dashboard.blockedDomains.prefix(60), id: \.self) { domain in
                        HStack(spacing: 10) {
                            Text(domain)
                                .font(.subheadline.weight(.semibold))
                                .lineLimit(1)

                            Spacer()

                            Button(role: .destructive) {
                                pendingAction = .removeBlockedDomain(domain: domain)
                            } label: {
                                Text(localizer.t.delete)
                                    .font(.caption.bold())
                            }
                            .buttonStyle(.bordered)
                            .disabled(isRunningAction)
                        }
                        .padding(14)
                        .glassCard()
                    }
                }

            case .zones:
                zoneOverviewCard(dashboard)

                if dashboard.logFiles.isEmpty {
                    placeholderCard(localizer.t.detailNoLogs)
                } else {
                    ForEach(dashboard.logFiles.prefix(30)) { log in
                        HStack(spacing: 12) {
                            Image(systemName: "doc.text.fill")
                                .foregroundStyle(technitiumColor)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(log.fileName)
                                    .font(.subheadline.weight(.semibold))
                                    .lineLimit(1)
                                Text(log.size)
                                    .font(.caption)
                                    .foregroundStyle(AppTheme.textMuted)
                            }
                            Spacer()
                        }
                        .padding(14)
                        .glassCard()
                    }
                }
            }
        } else {
            EmptyView()
        }
    }

    private func zoneOverviewCard(_ dashboard: TechnitiumDashboardData) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            LazyVGrid(columns: twoColumnGrid, spacing: 10) {
                infoCard(title: localizer.t.technitiumZones, value: Formatters.formatNumber(dashboard.zoneCount), icon: "globe", tint: AppTheme.warning)
                infoCard(title: localizer.t.technitiumCacheEntries, value: Formatters.formatNumber(dashboard.cacheRecordCount), icon: "tray.full.fill", tint: AppTheme.running)
                infoCard(title: localizer.t.technitiumBlocklistZones, value: Formatters.formatNumber(dashboard.summary.blockListZones), icon: "shield.fill", tint: AppTheme.stopped)
                infoCard(title: localizer.t.technitiumVersion, value: dashboard.settings.version ?? "—", icon: "tag.fill", tint: technitiumColor)
            }

            Text(String(format: localizer.t.technitiumBlocklistSources, dashboard.settings.blockListUrls.count))
                .font(.caption)
                .foregroundStyle(AppTheme.textMuted)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(14)
        .glassCard()
    }

    private func infoCard(title: String, value: String, icon: String, tint: Color) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Image(systemName: icon)
                .font(.caption.bold())
                .foregroundStyle(tint)
                .frame(width: 28, height: 28)
                .background(tint.opacity(0.12), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
            Text(value)
                .font(.subheadline.bold())
                .lineLimit(1)
                .minimumScaleFactor(0.8)
            Text(title)
                .font(.caption2)
                .foregroundStyle(AppTheme.textMuted)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .glassCard()
    }

    private func clientRow(_ client: TechnitiumTopClient, maxHits: Int) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 10) {
                Text(client.name)
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(1)
                Spacer()
                Text(Formatters.formatNumber(client.hits))
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(AppTheme.textMuted)
            }
            if let domain = client.domain, !domain.isEmpty {
                Text(domain)
                    .font(.caption)
                    .foregroundStyle(AppTheme.textMuted)
                    .lineLimit(1)
            }

            ProgressView(value: Double(client.hits), total: Double(maxHits))
                .tint(technitiumColor)

            if client.rateLimited {
                Text(localizer.t.technitiumRateLimited)
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(AppTheme.warning)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(AppTheme.warning.opacity(0.14), in: Capsule())
            }
        }
        .padding(14)
        .glassCard()
    }

    private func domainRow(_ domain: TechnitiumTopDomain, maxHits: Int, accent: Color) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 10) {
                Text(domain.name)
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(1)
                Spacer()
                Text(Formatters.formatNumber(domain.hits))
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(AppTheme.textMuted)
            }
            ProgressView(value: Double(domain.hits), total: Double(maxHits))
                .tint(accent)
        }
        .padding(14)
        .glassCard()
    }

    private func chartLegendDot(_ title: String, color: Color) -> some View {
        HStack(spacing: 6) {
            RoundedRectangle(cornerRadius: 2, style: .continuous)
                .fill(color)
                .frame(width: 8, height: 8)
            Text(title)
                .font(.caption2.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)
        }
    }

    private func placeholderCard(_ text: String) -> some View {
        Text(text)
            .font(.subheadline)
            .foregroundStyle(AppTheme.textMuted)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(14)
            .glassCard()
    }

    private func rangeTitle(_ range: TechnitiumStatsRange) -> String {
        switch range {
        case .lastHour: return "1H"
        case .lastDay: return "24H"
        case .lastWeek: return "7D"
        case .lastMonth: return "30D"
        }
    }

    private func panelTitle(_ panel: TechnitiumPanel) -> String {
        switch panel {
        case .clients: return localizer.t.piholeClients
        case .domains: return localizer.t.piholeDomains
        case .blocked: return localizer.t.piholeBlocked
        case .zones: return localizer.t.technitiumZones
        }
    }

    private func buildSeriesPoints(from values: [Double]) -> [TechnitiumChartPoint] {
        values.enumerated().map { index, value in
            TechnitiumChartPoint(index: index, value: value)
        }
    }

    private func fetchDashboard(forceLoading: Bool) async {
        if forceLoading || dashboard == nil {
            state = .loading
        }

        do {
            guard let client = await servicesStore.technitiumClient(instanceId: selectedInstanceId) else {
                state = .error(.notConfigured)
                return
            }
            let data = try await client.getDashboard(range: selectedRange)
            dashboard = data
            state = .loaded(())
        } catch let apiError as APIError {
            state = .error(apiError)
        } catch {
            state = .error(.custom(error.localizedDescription))
        }
    }

    private func runSetBlocking(enabled: Bool) async {
        await runControlledAction(
            enabled ? .enableBlocking : .disableBlocking,
            targetRef: "protection/global",
            confirmed: false
        )
    }

    private func runForceUpdate() async {
        await runControlledAction(
            .refreshBlockLists,
            targetRef: "blocklist/global",
            confirmed: false
        )
    }

    private func runPendingAction(_ pending: PendingTechnitiumAction) async {
        await runControlledAction(
            pending.controlledAction,
            targetRef: pending.targetRef,
            confirmed: true,
            minutes: pending.minutes,
            domain: pending.domain
        )
    }

    private func runControlledAction(
        _ action: TechnitiumControlledAction,
        targetRef: String,
        confirmed: Bool,
        minutes: Int? = nil,
        domain: String? = nil
    ) async {
        guard !isRunningAction else { return }
        isRunningAction = true
        defer { isRunningAction = false }

        do {
            guard let client = await servicesStore.technitiumClient(instanceId: selectedInstanceId) else {
                throw APIError.notConfigured
            }
            let outcomeBox = TechnitiumActionOutcomeBox()
            let audit = await servicesStore.controlledActionCoordinator.execute(
                request: action.request(
                    instanceId: selectedInstanceId,
                    targetRef: targetRef,
                    confirmed: confirmed
                ),
                actorRole: .admin,
                providerCapabilities: ProviderRegistry.descriptor(for: .technitium).capabilities
            ) {
                do {
                    let outcome: TechnitiumActionOutcome
                    switch action {
                    case .enableBlocking:
                        outcome = try await client.setBlockingEnabled(true)
                    case .disableBlocking:
                        outcome = try await client.setBlockingEnabled(false)
                    case .temporaryDisable:
                        outcome = try await client.temporaryDisableBlocking(minutes: minutes ?? 1)
                    case .refreshBlockLists:
                        outcome = try await client.forceUpdateBlockLists()
                    case .addBlockedDomain:
                        outcome = try await client.addBlockedDomain(domain ?? "")
                    case .removeBlockedDomain:
                        outcome = try await client.removeBlockedDomain(domain ?? "")
                    }
                    guard outcome.success else {
                        throw ControlledActionOperationError(
                            reasonCode: "technitium-provider-reported-failure",
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
                        reasonCode: "technitium-outcome-indeterminate",
                        disposition: .nonRetryable
                    )
                }
            }
            guard audit.state == .succeeded else {
                HapticManager.error()
                actionMessage = audit.reasonCode
                return
            }
            HapticManager.success()
            actionMessage = await outcomeBox.value()?.message ?? ServiceType.technitium.displayName
            if action == .addBlockedDomain { selectedPanel = .blocked }
            await fetchDashboard(forceLoading: false)
        } catch {
            HapticManager.error()
            actionMessage = (error as? APIError)?.localizedDescription ?? error.localizedDescription
        }
    }

}
