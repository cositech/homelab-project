import SwiftUI

// Maps to app/portainer/[containerId].tsx

struct ContainerDetailView: View {
    let instanceId: UUID
    let endpointId: Int
    let containerId: String

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer

    @State private var detail: ContainerDetail?
    @State private var stats: ContainerStats?
    @State private var logs: String?
    @State private var stacks: [PortainerStack] = []
    @State private var stackFile: String?
    @State private var composeContent = ""
    @State private var composeEdited = false
    @State private var activeTab: TabType = .info
    @State private var isLoading = true
    @State private var isEditing = false
    @State private var editName = ""
    @State private var isSavingCompose = false
    @State private var actionError: String?
    @State private var showActionError = false
    @State private var pendingAction: (
        action: PortainerControlledContainerAction,
        apiAction: ContainerAction?
    )?

    private let portainerColor = ServiceType.portainer.colors.primary

    enum TabType: String, CaseIterable {
        case info, stats, logs, env, compose
    }

    private var containerName: String {
        detail?.Name?.replacingOccurrences(of: "^/", with: "", options: .regularExpression) ?? localizer.t.loading
    }

    private var isRunning: Bool { detail?.State?.Running ?? false }
    private var isPaused: Bool { detail?.State?.Paused ?? false }

    private var matchedStack: PortainerStack? {
        guard let detail, let config = detail.Config else { return nil }
        let projectName = config.Labels?["com.docker.compose.project"]
        guard let projectName, !projectName.isEmpty else { return nil }
        return stacks.first { $0.Name.lowercased() == projectName.lowercased() }
    }

    var body: some View {
        ScrollView {
            if isLoading && detail == nil {
                VStack { Spacer(minLength: 200); ProgressView().tint(portainerColor); Spacer() }
            } else if let detail {
                LazyVStack(spacing: 16) {
                    // Block 1: Header and Actions
                    VStack(spacing: 0) {
                        headerCard(detail)
                        Divider().background(Color.white.opacity(0.1))
                        actionsRow
                    }
                    .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                    .glassCard()
                    
                    // Block 2: Tabs and Content
                    VStack(spacing: 0) {
                        tabBar
                        Divider().background(Color.white.opacity(0.1))
                        tabContent
                    }
                    .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                    .glassCard()
                }
                .padding(.horizontal, 16)
                .padding(.top, 16)
                .padding(.bottom, 30)
            } else {
                ContentUnavailableView(localizer.t.detailNotFound, systemImage: "exclamationmark.triangle")
            }
        }
        .background(AppTheme.background)
        .navigationTitle(containerName)
        .refreshable { await refreshAll() }
        .task { await fetchDetail() }
        .onChange(of: activeTab) { _, newTab in
            Task { await fetchTabData(newTab) }
        }
        .alert(localizer.t.error, isPresented: $showActionError) {
            Button(localizer.t.confirm, role: .cancel) {}
        } message: {
            Text(actionError ?? localizer.t.errorUnknown)
        }
        .confirmationDialog(
            localizer.t.actionConfirm,
            isPresented: Binding(
                get: { pendingAction != nil },
                set: { if !$0 { pendingAction = nil } }
            ),
            titleVisibility: .visible
        ) {
            Button(
                pendingAction?.action.rawValue.capitalized ?? "",
                role: pendingAction?.action.risk == .high ? .destructive : nil
            ) {
                guard let pendingAction else { return }
                self.pendingAction = nil
                Task {
                    await executeControlledAction(
                        pendingAction.action,
                        apiAction: pendingAction.apiAction,
                        confirmed: true
                    )
                }
            }
            Button(localizer.t.cancel, role: .cancel) { pendingAction = nil }
        } message: {
            Text(pendingAction?.action == .remove ? localizer.t.actionRemoveMessage : localizer.t.actionConfirmMessage)
        }
    }

    // MARK: - Header Card

    private func headerCard(_ detail: ContainerDetail) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top) {
                if isEditing {
                    HStack(spacing: 6) {
                        TextField(containerName, text: $editName)
                            .font(.body.weight(.semibold))
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .padding(8)
                            .background(Color(.tertiarySystemFill), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                            .overlay(RoundedRectangle(cornerRadius: 8, style: .continuous).stroke(portainerColor, lineWidth: 1))
                        Button {
                            if !editName.trimmingCharacters(in: .whitespaces).isEmpty, editName.trimmingCharacters(in: .whitespaces) != containerName {
                                Task { await renameContainer(editName.trimmingCharacters(in: .whitespaces)) }
                            }
                            isEditing = false
                        } label: {
                            Image(systemName: "checkmark").foregroundStyle(AppTheme.running)
                        }
                        .accessibilityLabel(localizer.t.confirm)
                        Button { isEditing = false } label: {
                            Image(systemName: "xmark").foregroundStyle(AppTheme.stopped)
                        }
                        .accessibilityLabel(localizer.t.cancel)
                    }
                } else {
                    HStack(spacing: 8) {
                        Text(containerName)
                            .font(.title3.bold())
                            .lineLimit(1)
                        Button {
                            HapticManager.light()
                            editName = containerName
                            isEditing = true
                        } label: {
                            Image(systemName: "pencil")
                                .font(.caption)
                                .foregroundStyle(AppTheme.textMuted)
                        }
                        .accessibilityLabel(localizer.t.actionEdit)
                    }
                }
                Spacer()
                StatusBadge(status: detail.State?.Status ?? "")
            }

            Text(detail.Config?.Image ?? "")
                .font(.caption)
                .foregroundStyle(AppTheme.textMuted)

            if isRunning, let startedAt = detail.State?.StartedAt, !startedAt.isEmpty {
                Text("\(localizer.t.detailUptime): \(Formatters.formatUptime(from: startedAt))")
                    .font(.caption)
                    .foregroundStyle(AppTheme.textSecondary)
            }
        }
        .padding(18)
    }

    // MARK: - Actions Row

    private var actionsRow: some View {
        HStack(spacing: 0) {
            if isRunning {
                containerActionButton(.stop, color: AppTheme.stopped)
                Divider().frame(height: 30).background(Color.white.opacity(0.1))
                containerActionButton(.restart, color: AppTheme.warning)
                Divider().frame(height: 30).background(Color.white.opacity(0.1))
                if isPaused {
                    containerActionButton(.unpause, color: AppTheme.running)
                } else {
                    containerActionButton(.pause, color: AppTheme.info)
                }
            } else {
                containerActionButton(.start, color: AppTheme.running)
                Divider().frame(height: 30).background(Color.white.opacity(0.1))
                removeButton
            }
        }
    }

    private func containerActionButton(_ action: ContainerAction, color: Color) -> some View {
        Button {
            HapticManager.medium()
            if action.controlledAction.requiresConfirmation {
                pendingAction = (action.controlledAction, action)
            } else {
                Task {
                    await executeControlledAction(
                        action.controlledAction,
                        apiAction: action,
                        confirmed: false
                    )
                }
            }
        } label: {
            HStack(spacing: 8) {
                Image(systemName: action.symbolName)
                    .font(.subheadline.bold())
                Text(action.displayName)
                    .font(.subheadline.weight(.semibold))
            }
            .foregroundStyle(color)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(color.opacity(0.08))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private var removeButton: some View {
        Button {
            HapticManager.medium()
            pendingAction = (.remove, nil)
        } label: {
            HStack(spacing: 8) {
                Image(systemName: "trash.fill")
                    .font(.subheadline.bold())
                Text(localizer.t.actionRemove)
                    .font(.subheadline.weight(.semibold))
            }
            .foregroundStyle(AppTheme.danger)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(AppTheme.danger.opacity(0.08))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    // MARK: - Tab Bar

    private var tabBar: some View {
        HStack(spacing: 0) {
            ForEach(TabType.allCases, id: \.self) { tab in
                Button {
                    HapticManager.light()
                    withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                        activeTab = tab
                    }
                } label: {
                    Text(tabLabel(tab))
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(activeTab == tab ? portainerColor : AppTheme.textSecondary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(activeTab == tab ? portainerColor.opacity(0.12) : Color.clear)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                
                if tab != TabType.allCases.last {
                    Divider().frame(height: 30).background(Color.white.opacity(0.1))
                }
            }
        }
        .background(Color.white.opacity(0.02))
    }

    private func tabLabel(_ tab: TabType) -> String {
        switch tab {
        case .info:    return localizer.t.detailInfo
        case .stats:   return localizer.t.detailStats
        case .logs:    return localizer.t.detailLogs
        case .env:     return localizer.t.detailEnv
        case .compose: return localizer.t.detailCompose
        }
    }

    // MARK: - Tab Content

    @ViewBuilder
    private var tabContent: some View {
        switch activeTab {
        case .info:    infoTab
        case .stats:   statsTab
        case .logs:    logsTab
        case .env:     envTab
        case .compose: composeTab
        }
    }

    // MARK: - Info Tab

    @ViewBuilder
    private var infoTab: some View {
        if let detail {
            VStack(alignment: .leading, spacing: 20) {
                // Container info
                VStack(alignment: .leading, spacing: 4) {
                    Label(localizer.t.detailContainer, systemImage: "shippingbox.fill")
                        .font(.subheadline.bold())
                        .foregroundStyle(portainerColor)
                        .padding(.bottom, 4)
                    
                    VStack(spacing: 0) {
                        infoRow(label: localizer.t.detailId, value: String(detail.Id.prefix(12)))
                        infoRow(label: localizer.t.detailCreated, value: Formatters.formatDate(detail.Created ?? ""))
                        infoRow(label: localizer.t.detailHostname, value: detail.Config?.Hostname ?? "")
                        if let workDir = detail.Config?.WorkingDir, !workDir.isEmpty {
                            infoRow(label: localizer.t.detailWorkDir, value: workDir)
                        }
                        if let cmd = detail.Config?.Cmd, !cmd.isEmpty {
                            infoRow(label: localizer.t.detailCommand, value: cmd.joined(separator: " "))
                        }
                    }
                }

                Divider().background(Color.white.opacity(0.05))

                // Network info
                VStack(alignment: .leading, spacing: 4) {
                    Label(localizer.t.detailNetwork, systemImage: "network")
                        .font(.subheadline.bold())
                        .foregroundStyle(portainerColor)
                        .padding(.bottom, 4)
                    
                    VStack(spacing: 0) {
                        infoRow(label: localizer.t.detailMode, value: detail.HostConfig?.NetworkMode ?? "")
                        if let networks = detail.NetworkSettings?.Networks {
                            ForEach(Array(networks.keys.sorted()), id: \.self) { name in
                                if let net = networks[name] {
                                    infoRow(label: name, value: net.IPAddress.isEmpty ? localizer.t.notAvailable : net.IPAddress)
                                }
                            }
                        }
                    }
                }

                if let mounts = detail.Mounts, !mounts.isEmpty {
                    Divider().background(Color.white.opacity(0.05))
                    
                    // Mounts
                    VStack(alignment: .leading, spacing: 8) {
                        Label(localizer.t.detailMounts, systemImage: "externaldrive.fill")
                            .font(.subheadline.bold())
                            .foregroundStyle(portainerColor)
                            .padding(.bottom, 4)
                        
                        ForEach(Array(mounts.enumerated()), id: \.offset) { index, mount in
                            VStack(alignment: .leading, spacing: 6) {
                                HStack(spacing: 6) {
                                    Text(mount.mountType)
                                        .font(.system(size: 10, weight: .bold))
                                        .foregroundStyle(AppTheme.info)
                                        .padding(.horizontal, 6)
                                        .padding(.vertical, 2)
                                        .background(AppTheme.info.opacity(0.1), in: RoundedRectangle(cornerRadius: 4, style: .continuous))
                                    
                                    Text(mount.Destination)
                                        .font(.caption.bold())
                                        .lineLimit(1)
                                }
                                
                                Text(mount.Source)
                                    .font(.system(size: 10))
                                    .foregroundStyle(AppTheme.textSecondary)
                                    .lineLimit(1)
                            }
                            .padding(.vertical, 4)
                            
                            if index < mounts.count - 1 {
                                Divider().padding(.vertical, 2).opacity(0.3)
                            }
                        }
                    }
                }

                Divider().background(Color.white.opacity(0.05))

                // Restart Policy
                VStack(alignment: .leading, spacing: 4) {
                    Label(localizer.t.detailRestartPolicy, systemImage: "arrow.clockwise.circle.fill")
                        .font(.subheadline.bold())
                        .foregroundStyle(portainerColor)
                        .padding(.bottom, 4)
                    
                    VStack(spacing: 0) {
                        let policy = detail.HostConfig?.RestartPolicy
                        infoRow(label: localizer.t.detailPolicy, value: (policy?.Name ?? "").isEmpty ? localizer.t.none : policy?.Name ?? "")
                        infoRow(label: localizer.t.detailMaxRetries, value: "\(policy?.MaximumRetryCount ?? 0)")
                    }
                }
            }
            .padding(18)
        }
    }

    private func infoRow(label: String, value: String) -> some View {
        HStack(alignment: .top) {
            Text(label)
                .font(.caption)
                .foregroundStyle(AppTheme.textSecondary)
                .frame(minWidth: 80, alignment: .leading)
            Spacer()
            Text(value)
                .font(.caption.weight(.medium))
                .multilineTextAlignment(.trailing)
                .lineLimit(2)
        }
        .padding(.vertical, 8)
    }

    // MARK: - Stats Tab

    @ViewBuilder
    private var statsTab: some View {
        if !isRunning {
            Text(localizer.t.detailNotRunning)
                .font(.subheadline)
                .foregroundStyle(AppTheme.textMuted)
                .frame(maxWidth: .infinity)
                .padding(.top, 30)
        } else if let stats, let cpuStats = stats.cpu_stats, let preCpuStats = stats.precpu_stats, let memStats = stats.memory_stats {
            let cpuDelta = Double((cpuStats.cpu_usage.total_usage) - (preCpuStats.cpu_usage.total_usage))
            let systemDelta = Double((cpuStats.system_cpu_usage ?? 0) - (preCpuStats.system_cpu_usage ?? 0))
            let cpuPercent = Formatters.calculateCpuPercent(cpuDelta: cpuDelta, systemDelta: systemDelta, cpuCount: cpuStats.online_cpus ?? 1)
            let memPercent = (memStats.limit ?? 0) > 0 ? (Double(memStats.usage ?? 0) / Double(memStats.limit ?? 0)) * 100 : 0
 
            VStack(spacing: 16) {
                // CPU
                premiumStatCard(
                    title: localizer.t.detailCpu,
                    value: String(format: "%.2f%%", cpuPercent),
                    percent: cpuPercent,
                    icon: "cpu",
                    color: AppTheme.info
                )
 
                // Memory
                premiumStatCard(
                    title: localizer.t.detailMemory,
                    value: "\(Formatters.formatBytes(Double(memStats.usage ?? 0))) / \(Formatters.formatBytes(Double(memStats.limit ?? 0)))",
                    percent: memPercent,
                    icon: "memorychip",
                    color: AppTheme.warning
                )

                // Network I/O
                if let networks = stats.networks {
                    let rx = networks.values.reduce(0) { $0 + $1.rx_bytes }
                    let tx = networks.values.reduce(0) { $0 + $1.tx_bytes }
                    VStack(alignment: .leading, spacing: 14) {
                        Label(localizer.t.detailNetworkIO, systemImage: "network")
                            .font(.subheadline.bold())
                            .foregroundStyle(AppTheme.running)
                        
                        HStack(spacing: 12) {
                            networkStatItem(
                                label: localizer.t.detailRx,
                                value: Formatters.formatBytes(Double(rx)),
                                icon: "arrow.down",
                                color: AppTheme.running
                            )
                            networkStatItem(
                                label: localizer.t.detailTx,
                                value: Formatters.formatBytes(Double(tx)),
                                icon: "arrow.up",
                                color: AppTheme.info
                            )
                        }
                    }
                    .padding(18)
                    .background(Color.white.opacity(0.04), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
            }
            .padding(18)
        } else {
            ProgressView().tint(portainerColor).padding(.top, 30)
        }
    }

    private func premiumStatCard(title: String, value: String, percent: Double, icon: String, color: Color) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Label(title, systemImage: icon)
                    .font(.subheadline.bold())
                    .foregroundStyle(color)
                Spacer()
                Text(value)
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(.primary)
            }
            
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 4)
                        .fill(color.opacity(0.1))
                    RoundedRectangle(cornerRadius: 4)
                        .fill(color.gradient)
                        .frame(width: geo.size.width * CGFloat(min(max(percent / 100, 0), 1)))
                }
            }
            .frame(height: 6)
            
            Text("\(String(format: "%.1f", percent))% \(localizer.t.detailUsed)")
                .font(.caption2)
                .foregroundStyle(AppTheme.textMuted)
        }
        .padding(18)
        .background(Color.white.opacity(0.04), in: RoundedRectangle(cornerRadius: 15, style: .continuous))
    }

    private func networkStatItem(label: String, value: String, icon: String, color: Color) -> some View {
        HStack(spacing: 8) {
            Image(systemName: icon)
                .font(.caption.bold())
                .foregroundStyle(color)
                .padding(6)
                .background(color.opacity(0.1), in: Circle())
            
            VStack(alignment: .leading, spacing: 2) {
                Text(label)
                    .font(.caption2)
                    .foregroundStyle(AppTheme.textSecondary)
                Text(value)
                    .font(.subheadline.weight(.semibold))
            }
            Spacer()
        }
        .frame(maxWidth: .infinity)
        .padding(10)
        .background(Color.white.opacity(0.04), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
    }

    // MARK: - Logs Tab

    @ViewBuilder
    private var logsTab: some View {
        if let logs {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 8) {
                    Image(systemName: "terminal.fill")
                        .font(.subheadline)
                        .foregroundStyle(portainerColor)
                    Text(localizer.t.detailContainerLogs)
                        .font(.subheadline.weight(.semibold))
                }
                VStack(spacing: 0) {
                    ScrollView(.horizontal) {
                        Text(logs.isEmpty ? localizer.t.detailNoLogs : logs)
                            .font(.system(size: 11, design: .monospaced))
                            .foregroundStyle(.primary)
                            .padding(14)
                            .textSelection(.enabled)
                    }
                }
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(Color.white.opacity(0.1), lineWidth: 1))
            }
            .padding(18)
        } else {
            ProgressView().tint(portainerColor).padding(.top, 30)
        }
    }

    // MARK: - Env Tab

    @ViewBuilder
    private var envTab: some View {
        if let detail {
            VStack(alignment: .leading, spacing: 16) {
                Label(localizer.t.detailEnvVars, systemImage: "key.fill")
                    .font(.subheadline.bold())
                    .foregroundStyle(portainerColor)
                
                if let envs = detail.Config?.Env {
                    VStack(spacing: 0) {
                        ForEach(Array(envs.enumerated()), id: \.offset) { index, env in
                            let parts = env.split(separator: "=", maxSplits: 1)
                            let key = String(parts.first ?? "")
                            let val = parts.count > 1 ? String(parts[1]) : ""
                            
                            VStack(alignment: .leading, spacing: 4) {
                                Text(key)
                                    .font(.system(size: 11, weight: .bold, design: .monospaced))
                                    .foregroundStyle(portainerColor)
                                Text(val)
                                    .font(.system(size: 11, design: .monospaced))
                                    .foregroundStyle(AppTheme.textSecondary)
                                    .lineLimit(2)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.vertical, 12)
                            
                            if index < envs.count - 1 {
                                Divider().background(Color.white.opacity(0.1))
                            }
                        }
                    }
                    .padding(.horizontal, 16)
                    .background(Color.primary.opacity(0.04), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(Color.white.opacity(0.1), lineWidth: 1))
                }
            }
            .padding(18)
        }
    }

    // MARK: - Compose Tab

    @ViewBuilder
    private var composeTab: some View {
        if let stack = matchedStack {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    Label(localizer.t.detailComposeFile, systemImage: "doc.text.fill")
                        .font(.subheadline.bold())
                        .foregroundStyle(portainerColor)
                    Spacer()
                    Text(stack.Name)
                        .font(.system(size: 10, weight: .bold, design: .monospaced))
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(portainerColor.opacity(0.2), in: Capsule())
                }
                
                VStack(spacing: 0) {
                    TextEditor(text: $composeContent)
                        .font(.system(size: 11, design: .monospaced))
                        .scrollContentBackground(.hidden)
                        .background(Color.primary.opacity(0.04))
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                        .frame(minHeight: 400)
                        .padding(8)
                        .onChange(of: composeContent) { _, _ in
                            composeEdited = true
                        }
                }
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(Color.white.opacity(0.1), lineWidth: 1))

                if composeEdited {
                    Button {
                        Task { await saveCompose(stack) }
                    } label: {
                        HStack(spacing: 8) {
                            if isSavingCompose {
                                ProgressView().tint(.white)
                            } else {
                                Image(systemName: "square.and.arrow.down")
                                Text(localizer.t.detailComposeSave)
                                    .fontWeight(.semibold)
                            }
                        }
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(portainerColor.gradient, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .disabled(isSavingCompose)
                }
            }
            .padding(18)
        } else if stacks.isEmpty && activeTab == .compose {
            VStack(spacing: 12) {
                Image(systemName: "doc.text")
                    .font(.largeTitle)
                    .foregroundStyle(AppTheme.textMuted)
                Text(localizer.t.detailComposeNotAvailable)
                    .font(.subheadline)
                    .foregroundStyle(AppTheme.textMuted)
            }
            .frame(maxWidth: .infinity)
            .padding(.top, 40)
        }
    }

    // MARK: - Controlled Actions

    private func executeControlledAction(
        _ action: PortainerControlledContainerAction,
        apiAction: ContainerAction?,
        confirmed: Bool
    ) async {
        guard let client = await servicesStore.portainerClient(instanceId: instanceId) else {
            HapticManager.error()
            actionError = APIError.notConfigured.localizedDescription
            showActionError = true
            return
        }

        let result = await servicesStore.controlledActionCoordinator.execute(
            request: action.request(
                instanceId: instanceId,
                endpointId: endpointId,
                containerId: containerId,
                confirmed: confirmed
            ),
            actorRole: .admin,
            providerCapabilities: ProviderRegistry.descriptor(for: .portainer).capabilities
        ) {
            if let apiAction {
                try await client.containerAction(
                    endpointId: endpointId,
                    containerId: containerId,
                    action: apiAction
                )
            } else if action == .remove {
                try await client.removeContainer(
                    endpointId: endpointId,
                    containerId: containerId,
                    force: true
                )
            }
        }

        guard result.state == .succeeded else {
            HapticManager.error()
            actionError = result.reasonCode
            showActionError = true
            return
        }
        HapticManager.success()
        if action != .remove { await fetchDetail() }
    }

    // MARK: - Data Fetching

    private func fetchDetail() async {
        isLoading = true
        defer { isLoading = false }
        do {
            guard let client = await servicesStore.portainerClient(instanceId: instanceId) else {
                throw APIError.notConfigured
            }
            detail = try await client.getContainerDetail(endpointId: endpointId, containerId: containerId)
        } catch {
            // silent
        }
    }

    private func fetchTabData(_ tab: TabType) async {
        guard let client = await servicesStore.portainerClient(instanceId: instanceId) else { return }
        switch tab {
        case .stats:
            do { stats = try await client.getContainerStats(endpointId: endpointId, containerId: containerId) } catch {}
        case .logs:
            do { logs = try await client.getContainerLogs(endpointId: endpointId, containerId: containerId, tail: 200) } catch {}
        case .compose:
            do {
                stacks = try await client.getStacks(endpointId: endpointId)
                if let stack = matchedStack {
                    let file = try await client.getStackFile(stackId: stack.Id)
                    stackFile = file
                    if !composeEdited { composeContent = file }
                }
            } catch {}
        default: break
        }
    }

    private func refreshAll() async {
        await fetchDetail()
        await fetchTabData(activeTab)
    }

    private func renameContainer(_ newName: String) async {
        do {
            guard let client = await servicesStore.portainerClient(instanceId: instanceId) else {
                throw APIError.notConfigured
            }
            try await client.renameContainer(endpointId: endpointId, containerId: containerId, newName: newName)
            HapticManager.success()
            await fetchDetail()
        } catch {
            HapticManager.error()
            actionError = error.localizedDescription
            showActionError = true
        }
    }

    private func saveCompose(_ stack: PortainerStack) async {
        isSavingCompose = true
        defer { isSavingCompose = false }
        do {
            guard let client = await servicesStore.portainerClient(instanceId: instanceId) else {
                throw APIError.notConfigured
            }
            try await client.updateStackFile(stackId: stack.Id, endpointId: endpointId, stackFileContent: composeContent)
            HapticManager.success()
            composeEdited = false
        } catch {
            HapticManager.error()
            actionError = error.localizedDescription
            showActionError = true
        }
    }
}
