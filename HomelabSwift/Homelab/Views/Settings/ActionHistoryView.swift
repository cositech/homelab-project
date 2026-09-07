import SwiftUI

/// Read-only Phase 3/4 consumer for the controlled-action audit ledger and durable queue: a
/// "History" tab (every recorded action, most recent first) and a "Pending" tab (queued, awaiting
/// retry, or flagged for manual review), both scoped to the active tenant - or fanned out across
/// every tenant in all-tenants mode - the same scoping rule the Operations workspace already
/// applies to instances.
struct ActionHistoryView: View {
    @Environment(ServicesStore.self) private var servicesStore
    @Environment(TenantStore.self) private var tenantStore
    @Environment(Localizer.self) private var localizer

    @State private var selectedTab = 0
    @State private var auditRecords: [ActionAuditRecord] = []
    @State private var pendingEntries: [DurableActionQueueEntry] = []
    @State private var isRefreshing = false
    @State private var refreshGeneration = 0

    /// Only in all-tenants mode, and only when more than one tenant is actually present among the
    /// records on screen right now - unlike Health/Alerts/Assets, `ActionAuditRecord`/
    /// `ControlledActionRequest` already carry `tenantRef` directly, so no instanceId indirection
    /// is needed here, just a tenantRef -> display-name lookup (same gating rule as
    /// `OperationsView.tenantLabelByInstanceId`).
    private var tenantLabelByTenantRef: [String: String] {
        let distinctTenantRefs = Set(auditRecords.map(\.tenantRef)).union(pendingEntries.map { Tenant.refOrDefault($0.request.tenantRef) })
        guard tenantStore.selection.allTenantsMode, distinctTenantRefs.count > 1 else { return [:] }
        let tenantById = Dictionary(uniqueKeysWithValues: tenantStore.selection.tenants.map { ($0.id, $0) })
        var result: [String: String] = [:]
        for tenantRef in distinctTenantRefs {
            result[tenantRef] = tenantById[tenantRef].map { tenantDisplayName($0, localizer: localizer) } ?? tenantRef
        }
        return result
    }

    var body: some View {
        ZStack {
            AppTheme.background.ignoresSafeArea()

            VStack(spacing: 12) {
                Picker("", selection: $selectedTab) {
                    Text(localizer.t.actionHistoryTabHistory).tag(0)
                    Text(localizer.t.actionHistoryTabPending).tag(1)
                }
                .pickerStyle(.segmented)
                .labelsHidden()
                .padding(.horizontal, 16)
                .padding(.top, 12)

                if isRefreshing {
                    Spacer(minLength: 0)
                    ProgressView()
                    Spacer(minLength: 0)
                } else if selectedTab == 0 {
                    if auditRecords.isEmpty {
                        emptyState(localizer.t.actionHistoryEmptyHistory)
                    } else {
                        ScrollView {
                            LazyVStack(spacing: 8) {
                                ForEach(auditRecords, id: \.auditId) { record in
                                    AuditRecordRow(record: record, instancesById: servicesStore.instancesById, tenantLabel: tenantLabelByTenantRef[record.tenantRef])
                                }
                            }
                            .padding(16)
                        }
                    }
                } else {
                    if pendingEntries.isEmpty {
                        emptyState(localizer.t.actionHistoryEmptyPending)
                    } else {
                        ScrollView {
                            LazyVStack(spacing: 8) {
                                ForEach(pendingEntries, id: \.request.idempotencyKey) { entry in
                                    PendingEntryRow(entry: entry, instancesById: servicesStore.instancesById, localizer: localizer, tenantLabel: tenantLabelByTenantRef[Tenant.refOrDefault(entry.request.tenantRef)])
                                }
                            }
                            .padding(16)
                        }
                    }
                }
            }
        }
        .navigationTitle(localizer.t.settingsActionHistory)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button {
                    Task { await refresh() }
                } label: {
                    Image(systemName: "arrow.clockwise")
                }
            }
        }
        .task(id: tenantStore.selection) {
            await refresh()
        }
    }

    @ViewBuilder
    private func emptyState(_ message: String) -> some View {
        Spacer(minLength: 0)
        VStack(spacing: 8) {
            Image(systemName: "clock.arrow.circlepath")
                .font(.title2)
                .foregroundStyle(AppTheme.textMuted)
            Text(message)
                .font(.body)
                .foregroundStyle(AppTheme.textSecondary)
                .multilineTextAlignment(.center)
        }
        .padding(.horizontal, 32)
        Spacer(minLength: 0)
    }

    /// Guarded by a generation counter, not just `.task(id:)` cancellation: a superseded refresh's
    /// awaits can still resolve after a newer one already published its results (`Task.isCancelled`
    /// is cooperative and these coordinator calls never check it), so only the *latest* call's
    /// results are ever written - the same protection `OperationsViewModel.refresh` gives its own
    /// job identity on Android.
    private func refresh() async {
        refreshGeneration += 1
        let generation = refreshGeneration
        isRefreshing = true
        let selection = tenantStore.selection
        let coordinator = servicesStore.controlledActionCoordinator
        let audit: [ActionAuditRecord]
        let pending: [DurableActionQueueEntry]
        if selection.allTenantsMode {
            audit = await coordinator.auditSnapshot()
            pending = await coordinator.pendingRecovery()
        } else {
            audit = await coordinator.auditSnapshot(tenantRef: selection.activeTenantId)
            pending = await coordinator.pendingRecovery(tenantRef: selection.activeTenantId)
        }
        guard generation == refreshGeneration else { return }
        auditRecords = audit.sorted { $0.recordedAt > $1.recordedAt }
        pendingEntries = pending.sorted { $0.updatedAt > $1.updatedAt }
        isRefreshing = false
    }
}

private func instanceLabel(for providerRef: String, instancesById: [UUID: ServiceInstance]) -> (String, ServiceType?) {
    let instanceId = providerRef.split(separator: ":", maxSplits: 1).last.map(String.init) ?? providerRef
    guard let uuid = UUID(uuidString: instanceId), let instance = instancesById[uuid] else {
        return (providerRef, nil)
    }
    return (instance.displayLabel, instance.type)
}

private func stateColor(_ state: ActionExecutionState) -> Color {
    switch state {
    case .succeeded: return AppTheme.running
    case .failed, .rejected: return AppTheme.danger
    case .manualReview: return AppTheme.warning
    case .queued, .retryWait, .executing: return AppTheme.info
    case .cancelled: return AppTheme.textMuted
    case .dryRun: return AppTheme.accent
    }
}

private let actionHistoryDateFormatter: DateFormatter = {
    let formatter = DateFormatter()
    formatter.dateStyle = .short
    formatter.timeStyle = .short
    return formatter
}()

private struct ActionHistoryRowCard<Content: View>: View {
    let color: Color
    let content: () -> Content

    init(color: Color, @ViewBuilder content: @escaping () -> Content) {
        self.color = color
        self.content = content
    }

    var body: some View {
        HStack(spacing: 0) {
            color
                .frame(width: 4)
            VStack(alignment: .leading, spacing: 4) {
                content()
            }
            .padding(12)
            Spacer(minLength: 0)
        }
        .background(AppTheme.surface, in: RoundedRectangle(cornerRadius: 14))
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }
}

private struct RowHeader: View {
    let label: String
    let type: ServiceType?
    let action: String
    let risk: ControlledActionRisk
    var tenantLabel: String? = nil

    var body: some View {
        HStack(spacing: 8) {
            if let type {
                ServiceIconView(type: type, size: 26)
            }
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(label)
                        .font(.subheadline.weight(.medium))
                        .lineLimit(1)
                    if let tenantLabel {
                        Text(tenantLabel)
                            .font(.caption2.weight(.semibold))
                            .foregroundStyle(.secondary)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 2)
                            .background(Color.secondary.opacity(0.15), in: Capsule())
                            .lineLimit(1)
                    }
                }
                Text(action)
                    .font(.caption.monospaced())
                    .foregroundStyle(AppTheme.textSecondary)
            }
            Spacer(minLength: 8)
            Text(risk.rawValue.uppercased())
                .font(.caption2.weight(.semibold))
                .foregroundStyle(AppTheme.textSecondary)
                .padding(.horizontal, 8)
                .padding(.vertical, 3)
                .background(AppTheme.surface, in: Capsule())
                .overlay(Capsule().stroke(AppTheme.textMuted.opacity(0.3), lineWidth: 1))
        }
    }
}

private struct AuditRecordRow: View {
    let record: ActionAuditRecord
    let instancesById: [UUID: ServiceInstance]
    var tenantLabel: String? = nil

    var body: some View {
        let (label, type) = instanceLabel(for: record.providerRef, instancesById: instancesById)
        ActionHistoryRowCard(color: stateColor(record.state)) {
            RowHeader(label: label, type: type, action: record.action, risk: record.risk, tenantLabel: tenantLabel)
            Text(record.targetRef)
                .font(.caption.monospaced())
                .foregroundStyle(AppTheme.textSecondary)
                .lineLimit(1)
            HStack {
                Text(record.state.rawValue.uppercased())
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(stateColor(record.state))
                Spacer()
                Text(actionHistoryDateFormatter.string(from: record.recordedAt))
                    .font(.caption)
                    .foregroundStyle(AppTheme.textSecondary)
            }
            if !record.reasonCode.isEmpty && record.reasonCode != record.state.rawValue {
                Text(record.reasonCode)
                    .font(.caption2.monospaced())
                    .foregroundStyle(AppTheme.textMuted)
            }
        }
    }
}

private struct PendingEntryRow: View {
    let entry: DurableActionQueueEntry
    let instancesById: [UUID: ServiceInstance]
    let localizer: Localizer
    var tenantLabel: String? = nil

    var body: some View {
        let (label, type) = instanceLabel(for: entry.request.providerRef, instancesById: instancesById)
        ActionHistoryRowCard(color: stateColor(entry.state)) {
            RowHeader(label: label, type: type, action: entry.request.action, risk: entry.request.risk, tenantLabel: tenantLabel)
            Text(entry.request.targetRef)
                .font(.caption.monospaced())
                .foregroundStyle(AppTheme.textSecondary)
                .lineLimit(1)
            HStack {
                Text(entry.state.rawValue.uppercased())
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(stateColor(entry.state))
                Spacer()
                Text(String(format: localizer.t.actionHistoryAttemptCount, entry.attemptCount))
                    .font(.caption)
                    .foregroundStyle(AppTheme.textSecondary)
            }
            if let nextAttemptAt = entry.nextAttemptAt {
                Text(String(format: localizer.t.actionHistoryNextAttempt, actionHistoryDateFormatter.string(from: nextAttemptAt)))
                    .font(.caption2)
                    .foregroundStyle(AppTheme.textMuted)
            }
        }
    }
}
