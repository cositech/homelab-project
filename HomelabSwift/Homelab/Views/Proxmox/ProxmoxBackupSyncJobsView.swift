import SwiftUI

struct ProxmoxBackupSyncJobsView: View {
    let instanceId: UUID

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer

    @State private var jobs: [ProxmoxBackupSyncJob] = []
    @State private var state: LoadableState<Void> = .idle
    @State private var triggeringJobId: String?
    @State private var triggerError: String?

    private let pbsColor = ServiceType.proxmoxBackupServer.colors.primary

    var body: some View {
        ServiceDashboardLayout(
            serviceType: .proxmoxBackupServer,
            instanceId: instanceId,
            state: state,
            onRefresh: fetchData
        ) {
            jobsSection
        }
        .navigationTitle(localizer.t.pbsSyncJobs)
        .navigationBarTitleDisplayMode(.inline)
        .alert(localizer.t.error, isPresented: .init(
            get: { triggerError != nil },
            set: { if !$0 { triggerError = nil } }
        )) {
            Button(localizer.t.done) { triggerError = nil }
        } message: {
            Text(triggerError ?? "")
        }
        .task { await fetchData() }
    }

    // MARK: - Jobs List

    private var jobsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            if jobs.isEmpty {
                HStack {
                    Spacer()
                    VStack(spacing: 8) {
                        Image(systemName: "arrow.triangle.2.circlepath")
                            .font(.title2)
                            .foregroundStyle(AppTheme.textMuted)
                        Text(localizer.t.pbsNoSyncJobs)
                            .font(.subheadline)
                            .foregroundStyle(AppTheme.textMuted)
                    }
                    .padding(.vertical, 30)
                    Spacer()
                }
                .glassCard()
            } else {
                ForEach(jobs) { job in
                    jobCard(job)
                }
            }
        }
    }

    private func jobCard(_ job: ProxmoxBackupSyncJob) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 10) {
                Image(systemName: "arrow.triangle.2.circlepath")
                    .font(.title3)
                    .foregroundStyle(pbsColor)

                VStack(alignment: .leading, spacing: 2) {
                    Text(job.id)
                        .font(.subheadline.bold())
                        .lineLimit(1)

                    if let schedule = job.schedule, !schedule.isEmpty {
                        HStack(spacing: 4) {
                            Image(systemName: "calendar")
                                .font(.caption2)
                            Text(schedule)
                                .font(.caption2)
                        }
                        .foregroundStyle(AppTheme.textSecondary)
                    }
                }

                Spacer()

                Button {
                    Task { await triggerJob(job) }
                } label: {
                    Image(systemName: triggeringJobId == job.id ? "arrow.clockwise" : "play.circle.fill")
                        .font(.body.bold())
                        .foregroundStyle(AppTheme.running)
                }
                .accessibilityLabel(localizer.t.pbsRunNow)
                .disabled(triggeringJobId != nil)
                .buttonStyle(.plain)
            }

            VStack(spacing: 0) {
                detailRow(icon: "externaldrive.fill", label: localizer.t.proxmoxStorage, value: job.store)
                if let remote = job.remote, !remote.isEmpty {
                    let remoteValue = job.remoteStore.map { "\(remote)/\($0)" } ?? remote
                    detailRow(icon: "network", label: localizer.t.pbsRemoteLabel, value: remoteValue)
                }
                if let comment = job.comment, !comment.isEmpty {
                    detailRow(icon: "text.alignleft", label: localizer.t.pbsCommentLabel, value: comment)
                }
            }
            .padding(8)
            .background(AppTheme.textMuted.opacity(0.05), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
        }
        .padding(12)
        .glassCard()
    }

    private func detailRow(icon: String, label: String, value: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: icon)
                .font(.caption2)
                .foregroundStyle(AppTheme.textMuted)
                .frame(width: 16)
            Text(label)
                .font(.caption2.bold())
                .foregroundStyle(AppTheme.textMuted)
                .frame(width: 70, alignment: .leading)
            Text(value)
                .font(.caption2)
                .foregroundStyle(.primary)
                .lineLimit(1)
            Spacer()
        }
        .padding(.vertical, 3)
    }

    // MARK: - Data

    private func fetchData() async {
        state = .loading
        do {
            guard let client = await servicesStore.proxmoxBackupServerClient(instanceId: instanceId) else {
                state = .error(.notConfigured)
                return
            }
            jobs = try await client.getSyncJobs()
            state = .loaded(())
        } catch let apiError as APIError {
            state = .error(apiError)
        } catch {
            state = .error(.custom(error.localizedDescription))
        }
    }

    // MARK: - Trigger Job

    private func triggerJob(_ job: ProxmoxBackupSyncJob) async {
        triggeringJobId = job.id
        triggerError = nil
        guard let client = await servicesStore.proxmoxBackupServerClient(instanceId: instanceId) else {
            triggerError = localizer.t.pbsClientNotConfigured
            triggeringJobId = nil
            return
        }
        let jobId = job.id
        // The coordinator records only a bounded reason code; keep the original provider error
        // (via an actor, since the operation closure below is @Sendable) so a definitive
        // rejection (bad token, unknown job, provider conflict) still shows its real message
        // instead of a generic one - same pattern as ProxmoxBackupJobsView.triggerJob.
        let errorBox = ControlledActionErrorBox()
        let request = ProxmoxBackupServerControlledSyncJobAction.trigger.request(
            instanceId: instanceId,
            jobId: jobId,
            confirmed: false
        )
        let capabilities = ProviderRegistry.descriptor(for: .proxmoxBackupServer).capabilities
        let result = await servicesStore.controlledActionCoordinator.execute(
            request: request,
            actorRole: .admin,
            providerCapabilities: capabilities
        ) {
            do {
                _ = try await client.triggerSyncJob(jobId: jobId)
            } catch is CancellationError {
                throw CancellationError()
            } catch let error as ControlledActionOperationError {
                throw error
            } catch {
                await errorBox.set(error)
                guard isAmbiguousProxmoxTransportFailure(error) else { throw error }
                // Triggering the job is not idempotent - a retry after a lost response could
                // fire a second, overlapping sync run for the same job, so this ambiguous
                // transport failure must not trigger an automatic coordinator-level retry.
                throw ControlledActionOperationError(
                    reasonCode: "pbs-sync-job-outcome-indeterminate",
                    disposition: .nonRetryable
                )
            }
        }
        triggeringJobId = nil
        guard result.state == ActionExecutionState.succeeded else {
            let originalError = await errorBox.value
            triggerError = originalError?.localizedDescription ?? result.reasonCode
            HapticManager.error()
            return
        }
        HapticManager.success()
        await fetchData()
    }
}
