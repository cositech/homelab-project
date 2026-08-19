import XCTest
@testable import Homelab

final class ModelDecodingTests: XCTestCase {
    func testProviderRequestRejectsCleartextBeforeCredentialsAreAttached() throws {
        let url = try XCTUnwrap(URL(string: "http://homelab.internal/api"))
        XCTAssertThrowsError(
            try BaseNetworkEngine.makeSecureRequest(
                url: url,
                method: "POST",
                headers: ["Authorization": "Bearer secret"],
                body: Data("password=secret".utf8)
            )
        )
    }

    func testProviderRequestAcceptsHTTPSAndPreservesRequestData() throws {
        let url = try XCTUnwrap(URL(string: "https://homelab.internal/api"))
        let request = try BaseNetworkEngine.makeSecureRequest(
            url: url,
            method: "POST",
            headers: ["Authorization": "Bearer secret"],
            body: Data("payload".utf8),
            timeout: 12
        )

        XCTAssertEqual(request.url, url)
        XCTAssertEqual(request.httpMethod, "POST")
        XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Bearer secret")
        XCTAssertEqual(request.httpBody, Data("payload".utf8))
        XCTAssertEqual(request.timeoutInterval, 12)
    }

    func testProviderURLRejectsCleartextBeforeCombiningSensitivePath() {
        XCTAssertThrowsError(
            try BaseNetworkEngine.makeSecureURL(
                baseURL: "http://pihole.internal",
                path: "/admin/api.php?auth=legacy-secret"
            )
        )
    }

    func testProviderURLBuildsHTTPSLegacyQueryWithoutEmbeddedAuthorityCredentials() throws {
        let url = try BaseNetworkEngine.makeSecureURL(
            baseURL: "https://pihole.internal/base/",
            path: "/admin/api.php?summaryRaw&auth=legacy-secret"
        )

        XCTAssertEqual(url.scheme, "https")
        XCTAssertEqual(url.host, "pihole.internal")
        XCTAssertEqual(url.path, "/base/admin/api.php")
        XCTAssertEqual(URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.last?.value, "legacy-secret")
    }

    func testProviderURLLoggingRedactsSensitiveQueryValues() throws {
        let url = try XCTUnwrap(URL(string: "https://pihole.internal/api?auth=secret&limit=10"))
        let logged = BaseNetworkEngine.redactedURLForLogging(url)

        XCTAssertFalse(logged.contains("secret"))
        XCTAssertTrue(logged.contains("auth=%3Credacted%3E"))
        XCTAssertTrue(logged.contains("limit=10"))
    }

    func testProviderRegistryCoversAllServiceTypesAndReferenceCapabilities() {
        XCTAssertEqual(ProviderRegistry.registeredProviders.count, ServiceType.allCases.count)
        XCTAssertTrue(ProviderRegistry.descriptor(for: .proxmox).capabilities.contains(.writeActions))
        XCTAssertTrue(ProviderRegistry.descriptor(for: .uptimeKuma).capabilities.contains(.metrics))
        XCTAssertFalse(ProviderRegistry.descriptor(for: .uptimeKuma).capabilities.contains(.writeActions))
        XCTAssertTrue(ProviderRegistry.descriptor(for: .proxmoxBackupServer).capabilities.contains(.resources))
        XCTAssertTrue(ProviderRegistry.descriptor(for: .proxmoxBackupServer).capabilities.contains(.metrics))
        XCTAssertFalse(ProviderRegistry.descriptor(for: .proxmoxBackupServer).capabilities.contains(.writeActions))
        XCTAssertTrue(ProviderRegistry.descriptor(for: .prometheus).capabilities.contains(.events))
        XCTAssertFalse(ProviderRegistry.descriptor(for: .prometheus).capabilities.contains(.writeActions))
        XCTAssertTrue(ProviderRegistry.descriptor(for: .grafana).capabilities.contains(.resources))
        XCTAssertFalse(ProviderRegistry.descriptor(for: .grafana).capabilities.contains(.events))
        XCTAssertFalse(ProviderRegistry.descriptor(for: .grafana).capabilities.contains(.writeActions))
        XCTAssertTrue(ProviderRegistry.descriptor(for: .netbox).capabilities.contains(.resources))
        XCTAssertFalse(ProviderRegistry.descriptor(for: .netbox).capabilities.contains(.writeActions))
        XCTAssertTrue(ProviderRegistry.descriptor(for: .zammad).capabilities.contains(.events))
        XCTAssertFalse(ProviderRegistry.descriptor(for: .zammad).capabilities.contains(.writeActions))
        XCTAssertTrue(ProviderRegistry.descriptor(for: .pegaprox).capabilities.contains(.metrics))
        XCTAssertFalse(ProviderRegistry.descriptor(for: .pegaprox).capabilities.contains(.writeActions))
        XCTAssertTrue(ProviderRegistry.descriptor(for: .opnsense).capabilities.contains(.metrics))
        XCTAssertFalse(ProviderRegistry.descriptor(for: .opnsense).capabilities.contains(.writeActions))
        XCTAssertTrue(ProviderRegistry.descriptor(for: .oneuptime).capabilities.contains(.events))
        XCTAssertFalse(ProviderRegistry.descriptor(for: .oneuptime).capabilities.contains(.writeActions))
    }

    func testProxmoxBackupServerDatastoreDecodingAndAliases() throws {
        let json = """
        {
            "store": "backup-zfs",
            "total": 1000,
            "used": 875,
            "avail": 125,
            "maintenance-mode": "read-only"
        }
        """.data(using: .utf8)!

        let datastore = try JSONDecoder().decode(ProxmoxBackupDatastore.self, from: json)

        XCTAssertEqual(datastore.store, "backup-zfs")
        XCTAssertEqual(try XCTUnwrap(datastore.usageRatio), 0.875, accuracy: 0.0001)
        XCTAssertEqual(datastore.maintenance, "read-only")
        XCTAssertEqual(ServiceType.fromStoredRawValue("pbs"), .proxmoxBackupServer)
        XCTAssertEqual(BackupServiceTypeMapper.serviceType(from: "proxmox-backup-server"), .proxmoxBackupServer)
    }

    func testOperationsSearchReturnsMatchingAssetsWithoutUnrelatedRecords() {
        let instanceId = UUID()
        let snapshot = OperationsSnapshot(
            alerts: [
                ProviderEvent(providerId: "uptime-kuma", instanceId: instanceId, eventId: "alert-1", severity: "critical", message: "customer portal is down", occurredAt: Date(), resourceId: "22")
            ],
            assets: [
                ProviderResource(providerId: "proxmox", instanceId: instanceId, resourceType: "virtual-machine", resourceId: "101", name: "zammad", state: "running", attributes: [:]),
                ProviderResource(providerId: "uptime-kuma", instanceId: instanceId, resourceType: "monitor", resourceId: "22", name: "grafana", state: "up", attributes: [:])
            ]
        )

        let result = snapshot.search("zammad")

        XCTAssertEqual(result.assets.map(\.resourceId), ["101"])
        XCTAssertTrue(result.alerts.isEmpty)
        XCTAssertTrue(result.health.isEmpty)
    }

    func testBlankOperationsSearchDoesNotReturnFullSnapshot() {
        let snapshot = OperationsSnapshot(
            diagnostics: [
                ProviderDiagnostic(providerId: "proxmox", instanceId: UUID(), displayName: "PVE", endpoint: "https://pve.internal", tlsMode: .system, capabilities: [], state: .healthy, message: nil, observedAt: Date())
            ]
        )

        XCTAssertTrue(snapshot.search("   ").isEmpty)
    }


    // MARK: - Portainer

    func testPortainerEndpointDecoding() throws {
        let json = """
        {
            "Id": 1,
            "Name": "local",
            "Type": 1,
            "URL": "unix:///var/run/docker.sock",
            "Status": 1,
            "PublicURL": "https://portainer.local",
            "GroupId": 1,
            "TagIds": [1, 2],
            "Snapshots": [{
                "DockerVersion": "24.0.7",
                "TotalCPU": 4,
                "TotalMemory": 8589934592,
                "RunningContainerCount": 12,
                "StoppedContainerCount": 3,
                "HealthyContainerCount": 10,
                "UnhealthyContainerCount": 0,
                "VolumeCount": 8,
                "ImageCount": 25,
                "ServiceCount": 0,
                "StackCount": 5,
                "Time": 1700000000
            }]
        }
        """.data(using: .utf8)!

        let endpoint = try JSONDecoder().decode(PortainerEndpoint.self, from: json)
        XCTAssertEqual(endpoint.Id, 1)
        XCTAssertEqual(endpoint.Name, "local")
        XCTAssertTrue(endpoint.isOnline)
        XCTAssertEqual(endpoint.Snapshots?.count, 1)
        XCTAssertEqual(endpoint.Snapshots?.first?.RunningContainerCount, 12)
        XCTAssertEqual(endpoint.Snapshots?.first?.TotalCPU, 4)
    }

    func testPortainerContainerDecoding() throws {
        let json = """
        {
            "Id": "abc123def456",
            "Names": ["/my-container"],
            "Image": "nginx:latest",
            "ImageID": "sha256:abc123",
            "Command": "nginx -g 'daemon off;'",
            "Created": 1700000000,
            "State": "running",
            "Status": "Up 3 hours",
            "Ports": [{"IP": "0.0.0.0", "PrivatePort": 80, "PublicPort": 8080, "Type": "tcp"}],
            "Labels": {"com.docker.compose.project": "homelab"},
            "HostConfig": {"NetworkMode": "bridge"},
            "NetworkSettings": {"Networks": {"bridge": {"IPAddress": "172.17.0.2", "Gateway": "172.17.0.1", "MacAddress": "02:42:ac:11:00:02", "NetworkID": "net123"}}},
            "Mounts": [{"Type": "bind", "Source": "/data", "Destination": "/var/data", "Mode": "rw", "RW": true}]
        }
        """.data(using: .utf8)!

        let container = try JSONDecoder().decode(PortainerContainer.self, from: json)
        XCTAssertEqual(container.Id, "abc123def456")
        XCTAssertEqual(container.displayName, "my-container")
        XCTAssertEqual(container.State, "running")
        let ports = try XCTUnwrap(container.Ports)
        XCTAssertEqual(ports.first?.PublicPort, 8080)
        let mounts = try XCTUnwrap(container.Mounts)
        XCTAssertEqual(mounts.count, 1)
    }

    func testContainerStatsDecoding() throws {
        let json = """
        {
            "cpu_stats": {
                "cpu_usage": {"total_usage": 5000000000, "percpu_usage": [2500000000, 2500000000]},
                "system_cpu_usage": 100000000000,
                "online_cpus": 4
            },
            "precpu_stats": {
                "cpu_usage": {"total_usage": 4000000000},
                "system_cpu_usage": 99000000000,
                "online_cpus": 4
            },
            "memory_stats": {
                "usage": 104857600,
                "limit": 2147483648,
                "stats": {"cache": 52428800}
            },
            "networks": {
                "eth0": {"rx_bytes": 1024000, "tx_bytes": 512000}
            }
        }
        """.data(using: .utf8)!

        let stats = try JSONDecoder().decode(ContainerStats.self, from: json)
        let cpuStats = try XCTUnwrap(stats.cpu_stats)
        XCTAssertEqual(cpuStats.online_cpus, 4)
        let memoryStats = try XCTUnwrap(stats.memory_stats)
        XCTAssertEqual(memoryStats.usage, 104857600)
        XCTAssertEqual(memoryStats.limit, 2147483648)
        XCTAssertEqual(stats.networks?["eth0"]?.rx_bytes, 1024000)
    }

    func testPortainerStackDecoding() throws {
        let json = """
        {"Id": 5, "Name": "homelab", "Type": 2, "EndpointId": 1, "Status": 1, "CreationDate": 1700000000}
        """.data(using: .utf8)!

        let stack = try JSONDecoder().decode(PortainerStack.self, from: json)
        XCTAssertEqual(stack.Id, 5)
        XCTAssertEqual(stack.Name, "homelab")
        XCTAssertTrue(stack.isActive)
    }

    // MARK: - Pi-hole

    func testPiholeStatsDecoding() throws {
        let json = """
        {
            "queries": {
                "total": 15000,
                "blocked": 3000,
                "percent_blocked": 20.0,
                "unique_domains": 5000,
                "forwarded": 9000,
                "cached": 3000
            },
            "gravity": {
                "domains_being_blocked": 120000,
                "last_update": 1700000000
            }
        }
        """.data(using: .utf8)!

        let stats = try JSONDecoder().decode(PiholeStats.self, from: json)
        XCTAssertEqual(stats.queries.total, 15000)
        XCTAssertEqual(stats.queries.blocked, 3000)
        XCTAssertEqual(stats.queries.percent_blocked, 20.0, accuracy: 0.01)
        XCTAssertEqual(stats.gravity.domains_being_blocked, 120000)
    }

    func testPiholeBlockingStatusDecoding() throws {
        let json = """
        {"blocking": "enabled"}
        """.data(using: .utf8)!

        let status = try JSONDecoder().decode(PiholeBlockingStatus.self, from: json)
        XCTAssertTrue(status.isEnabled)

        let json2 = """
        {"blocking": "disabled"}
        """.data(using: .utf8)!
        let status2 = try JSONDecoder().decode(PiholeBlockingStatus.self, from: json2)
        XCTAssertFalse(status2.isEnabled)
    }

    func testPiholeHistoryDecoding() throws {
        let json = """
        {
            "history": [
                {"timestamp": 1700000000, "total": 100, "blocked": 20},
                {"timestamp": 1700000600, "total": 150, "blocked": 30}
            ]
        }
        """.data(using: .utf8)!

        let history = try JSONDecoder().decode(PiholeQueryHistory.self, from: json)
        XCTAssertEqual(history.history.count, 2)
        XCTAssertEqual(history.history[0].total, 100)
        XCTAssertEqual(history.history[1].blocked, 30)
    }

    func testPiholeAuthDecoding() throws {
        let json = """
        {"session": {"sid": "abc123xyz", "valid": true, "totp": false}}
        """.data(using: .utf8)!

        let auth = try JSONDecoder().decode(PiholeAuthResponse.self, from: json)
        XCTAssertEqual(auth.session.sid, "abc123xyz")
        XCTAssertTrue(auth.session.valid)
    }

    func testPiholeDomainListResponseV6Decoding() throws {
        let json = """
        {
            "domains": [
                {"id": 11, "domain": "good.example", "kind": "exact", "list": "allow"},
                {"id": 12, "domain": "ads.example", "kind": "exact", "list": "deny"}
            ]
        }
        """.data(using: .utf8)!

        let decoded = try JSONDecoder().decode(PiholeDomainListResponse.self, from: json)
        XCTAssertEqual(decoded.domains.count, 2)
        XCTAssertEqual(decoded.domains[0].id, 11)
        XCTAssertEqual(decoded.domains[0].type, .allow)
        XCTAssertEqual(decoded.domains[1].type, .deny)
    }

    func testPiholeDomainListResponseLegacyDecoding() throws {
        let json = """
        {
            "whitelist": ["allowed.example"],
            "blacklist": ["blocked.example"],
            "regex_whitelist": ["^safe\\\\.example$"],
            "regex_blacklist": ["^ads\\\\.example$"]
        }
        """.data(using: .utf8)!

        let decoded = try JSONDecoder().decode(PiholeDomainListResponse.self, from: json)

        XCTAssertEqual(decoded.domains.count, 4)
        XCTAssertEqual(decoded.domains.filter { $0.type == .allow }.count, 2)
        XCTAssertEqual(decoded.domains.filter { $0.type == .deny }.count, 2)
        XCTAssertEqual(decoded.domains.filter { $0.kind == "regex" }.count, 2)
    }

    // MARK: - Sonarr

    func testSonarrSeriesDecodingUsesStatisticsFallbacks() throws {
        let json = """
        {
            "id": 42,
            "title": "Severance",
            "statistics": {
                "episodeFileCount": 8,
                "episodeCount": 10,
                "sizeOnDisk": 1234567890
            },
            "images": [
                {"coverType": "poster", "remoteUrl": "/MediaCover/42/poster.jpg"}
            ]
        }
        """.data(using: .utf8)!

        let decoded = try JSONDecoder().decode(SonarrSeries.self, from: json)
        XCTAssertEqual(decoded.id, 42)
        XCTAssertEqual(decoded.title, "Severance")
        XCTAssertEqual(decoded.episodeFileCount, 8)
        XCTAssertEqual(decoded.episodeCount, 10)
        XCTAssertEqual(decoded.sizeOnDisk, 1234567890)
        XCTAssertEqual(decoded.posterUrl, "/MediaCover/42/poster.jpg")
    }

    func testSonarrQueueRecordDecodingHandlesCamelCaseFallbacks() throws {
        let json = """
        {
            "page": 1,
            "records": [
                {
                    "id": 7,
                    "title": "Episode 1",
                    "size": 5000,
                    "sizeLeft": 1250,
                    "timeLeft": "00:05:00"
                }
            ]
        }
        """.data(using: .utf8)!

        let decoded = try JSONDecoder().decode(SonarrQueueResponse.self, from: json)
        XCTAssertEqual(decoded.page, 1)
        XCTAssertEqual(decoded.pageSize, 0)
        XCTAssertEqual(decoded.records.count, 1)
        XCTAssertEqual(decoded.records[0].sizeleft, 1250)
        XCTAssertEqual(decoded.records[0].timeleft, "00:05:00")
        XCTAssertEqual(decoded.records[0].status, "unknown")
    }

    func testSonarrSystemStatusDecodingSupportsAppVersionFallback() throws {
        let json = """
        {
            "appVersion": "4.0.15.2941",
            "releaseBranch": "main"
        }
        """.data(using: .utf8)!

        let decoded = try JSONDecoder().decode(SonarrSystemStatus.self, from: json)
        XCTAssertEqual(decoded.version, "4.0.15.2941")
        XCTAssertEqual(decoded.displayBranch, "Main")
    }

    // MARK: - Beszel

    func testBeszelSystemDecoding() throws {
        let json = """
        {
            "id": "sys001",
            "collectionId": "col001",
            "collectionName": "systems",
            "name": "homeserver",
            "host": "192.168.1.100",
            "port": 45876,
            "status": "up",
            "info": {
                "cpu": 23.5,
                "mp": 45.2,
                "m": 3.6,
                "mt": 7.8,
                "dp": 62.1,
                "d": 120.5,
                "dt": 194.0,
                "ns": 0.5,
                "nr": 1.2,
                "u": 864000,
                "cm": "Intel i7-12700",
                "os": "Ubuntu 24.04",
                "k": "6.5.0-35-generic",
                "h": "homeserver",
                "t": 52.0,
                "c": 12
            },
            "created": "2024-01-01T00:00:00Z",
            "updated": "2024-11-01T00:00:00Z"
        }
        """.data(using: .utf8)!

        let system = try JSONDecoder().decode(BeszelSystem.self, from: json)
        XCTAssertEqual(system.id, "sys001")
        XCTAssertEqual(system.name, "homeserver")
        XCTAssertTrue(system.isOnline)
        XCTAssertNotNil(system.info)
        let info = try XCTUnwrap(system.info)
        XCTAssertEqual(try XCTUnwrap(info.cpu), 23.5, accuracy: 0.01)
        XCTAssertEqual(try XCTUnwrap(info.mp), 45.2, accuracy: 0.01)
        XCTAssertEqual(try XCTUnwrap(info.m), 3.6, accuracy: 0.01)
        XCTAssertEqual(try XCTUnwrap(info.mt), 7.8, accuracy: 0.01)
        XCTAssertEqual(info.c, 12)
        XCTAssertEqual(info.os, "Ubuntu 24.04")
        XCTAssertEqual(info.cm, "Intel i7-12700")
    }

    func testBeszelSystemOffline() throws {
        let json = """
        {
            "id": "sys002", "collectionId": "col001", "collectionName": "systems",
            "name": "backup-server", "host": "192.168.1.101", "port": 45876,
            "status": "down",
            "info": {"cpu": 0, "mp": 0, "m": 0, "mt": 0, "dp": 0, "d": 0, "dt": 0, "ns": 0, "nr": 0, "u": 0},
            "created": "2024-01-01T00:00:00Z", "updated": "2024-11-01T00:00:00Z"
        }
        """.data(using: .utf8)!

        let system = try JSONDecoder().decode(BeszelSystem.self, from: json)
        XCTAssertFalse(system.isOnline)
    }

    func testBeszelRecordDecoding() throws {
        let json = """
        {
            "id": "rec001", "system": "sys001",
            "stats": {
                "cpu": 35.2, "mp": 50.1, "m": 4.0, "mt": 8.0,
                "dp": 65.0, "d": 125.0, "dt": 192.0,
                "ns": 1.0, "nr": 2.0,
                "dc": [
                    {"n": "nginx", "cpu": 1.2, "m": 50.5},
                    {"n": "postgres", "cpu": 5.0, "m": 200.3}
                ]
            },
            "created": "2024-11-01T12:00:00Z", "updated": "2024-11-01T12:00:00Z"
        }
        """.data(using: .utf8)!

        let record = try JSONDecoder().decode(BeszelSystemRecord.self, from: json)
        XCTAssertEqual(try XCTUnwrap(record.stats.cpu), 35.2, accuracy: 0.01)
        XCTAssertEqual(record.stats.dc?.count, 2)
        XCTAssertEqual(record.stats.dc?.first?.name, "nginx")
        XCTAssertEqual(try XCTUnwrap(record.stats.dc?.last?.m), 200.3, accuracy: 0.01)
    }

    // MARK: - Gitea

    func testGiteaUserDecoding() throws {
        let json = """
        {"id": 1, "login": "admin", "full_name": "Admin User", "email": "admin@local.host", "avatar_url": "https://gitea.local/avatar/1", "created": "2024-01-01T00:00:00Z"}
        """.data(using: .utf8)!

        let user = try JSONDecoder().decode(GiteaUser.self, from: json)
        XCTAssertEqual(user.id, 1)
        XCTAssertEqual(user.login, "admin")
        XCTAssertEqual(user.full_name, "Admin User")
    }

    func testGiteaRepoDecoding() throws {
        let json = """
        {
            "id": 10, "name": "homelab", "full_name": "admin/homelab",
            "description": "My homelab config",
            "owner": {"login": "admin", "avatar_url": "https://gitea.local/avatar/1"},
            "private": true, "fork": false,
            "stars_count": 5, "forks_count": 2,
            "open_issues_count": 3, "open_pr_counter": 1,
            "language": "TypeScript", "size": 2048,
            "updated_at": "2024-11-01T00:00:00Z", "created_at": "2024-01-01T00:00:00Z",
            "html_url": "https://gitea.local/admin/homelab",
            "default_branch": "main"
        }
        """.data(using: .utf8)!

        let repo = try JSONDecoder().decode(GiteaRepo.self, from: json)
        XCTAssertEqual(repo.name, "homelab")
        XCTAssertTrue(repo.isPrivate)
        XCTAssertEqual(repo.stars_count, 5)
        XCTAssertEqual(repo.language, "TypeScript")
        XCTAssertEqual(repo.default_branch, "main")
    }

    func testGiteaFileContentDecoding() throws {
        let json = """
        {
            "name": "README.md", "path": "README.md", "sha": "abc123",
            "type": "file", "size": 1234,
            "content": "IyBIZWxsbw==",
            "encoding": "base64",
            "url": "https://gitea.local/api/v1/repos/admin/test/contents/README.md",
            "html_url": "https://gitea.local/admin/test/src/branch/main/README.md",
            "download_url": "https://gitea.local/admin/test/raw/branch/main/README.md"
        }
        """.data(using: .utf8)!

        let file = try JSONDecoder().decode(GiteaFileContent.self, from: json)
        XCTAssertEqual(file.name, "README.md")
        XCTAssertTrue(file.isFile)
        XCTAssertFalse(file.isDirectory)
        XCTAssertTrue(file.isMarkdown)
        XCTAssertEqual(file.decodedContent, "# Hello")
    }

    func testGiteaFileDirectoryDecoding() throws {
        let json = """
        {
            "name": "src", "path": "src", "sha": "def456",
            "type": "dir", "size": 0,
            "url": "https://gitea.local/api/v1/repos/admin/test/contents/src",
            "html_url": "https://gitea.local/admin/test/src/branch/main/src"
        }
        """.data(using: .utf8)!

        let file = try JSONDecoder().decode(GiteaFileContent.self, from: json)
        XCTAssertTrue(file.isDirectory)
        XCTAssertFalse(file.isFile)
    }

    func testGiteaCommitDecoding() throws {
        let json = """
        {
            "sha": "abc123def456789012345678901234567890abcd",
            "url": "https://gitea.local/api/v1/repos/admin/test/git/commits/abc123",
            "html_url": "https://gitea.local/admin/test/commit/abc123",
            "commit": {
                "message": "feat: add new feature\\n\\nDetailed description",
                "author": {"name": "Admin", "email": "admin@local.host", "date": "2024-11-01T12:00:00Z"},
                "committer": {"name": "Admin", "email": "admin@local.host", "date": "2024-11-01T12:00:00Z"}
            },
            "author": {"login": "admin", "avatar_url": "https://gitea.local/avatar/1"}
        }
        """.data(using: .utf8)!

        let commit = try JSONDecoder().decode(GiteaCommit.self, from: json)
        XCTAssertTrue(commit.sha.hasPrefix("abc123"))
        XCTAssertEqual(commit.commit.author.name, "Admin")
        XCTAssertTrue(commit.commit.message.hasPrefix("feat: add new feature"))
    }

    func testGiteaIssueDecoding() throws {
        let json = """
        {
            "id": 1, "number": 42, "title": "Bug: login fails",
            "body": "Description of the bug",
            "state": "open",
            "user": {"login": "admin", "avatar_url": "https://gitea.local/avatar/1"},
            "labels": [{"id": 1, "name": "bug", "color": "FF0000"}],
            "comments": 3,
            "created_at": "2024-11-01T00:00:00Z",
            "updated_at": "2024-11-01T12:00:00Z",
            "closed_at": null,
            "pull_request": null
        }
        """.data(using: .utf8)!

        let issue = try JSONDecoder().decode(GiteaIssue.self, from: json)
        XCTAssertEqual(issue.number, 42)
        XCTAssertTrue(issue.isOpen)
        XCTAssertFalse(issue.isPR)
        XCTAssertEqual(issue.labels.count, 1)
        XCTAssertEqual(issue.labels.first?.name, "bug")
    }

    func testGiteaBranchDecoding() throws {
        let json = """
        {"name": "main", "commit": {"id": "abc123", "message": "Initial commit"}, "protected": true}
        """.data(using: .utf8)!

        let branch = try JSONDecoder().decode(GiteaBranch.self, from: json)
        XCTAssertEqual(branch.name, "main")
        XCTAssertTrue(branch.protected)
        XCTAssertEqual(branch.commit.message, "Initial commit")
    }

    func testGiteaHeatmapItemDecoding() throws {
        let json = """
        {"timestamp": 1700000000, "contributions": 5}
        """.data(using: .utf8)!

        let item = try JSONDecoder().decode(GiteaHeatmapItem.self, from: json)
        XCTAssertEqual(item.contributions, 5)
        XCTAssertEqual(item.timestamp, 1700000000)
    }

    // MARK: - ServiceConnection

    func testServiceConnectionEncoding() throws {
        let conn = ServiceConnection(type: .portainer, url: "https://portainer.local/", token: "jwt123", apiKey: "key456")
        let data = try JSONEncoder().encode(conn)
        let decoded = try JSONDecoder().decode(ServiceConnection.self, from: data)
        XCTAssertEqual(decoded.type, .portainer)
        XCTAssertEqual(decoded.url, "https://portainer.local")  // trailing slash stripped
        XCTAssertEqual(decoded.token, "jwt123")
        XCTAssertEqual(decoded.apiKey, "key456")
    }

    func testPiHoleServiceConnectionEncoding() throws {
        let conn = ServiceConnection(
            type: .pihole,
            url: "https://pihole.local/",
            token: "sid123",
            piholePassword: "secret",
            piholeAuthMode: .session
        )
        let data = try JSONEncoder().encode(conn)
        let decoded = try JSONDecoder().decode(ServiceConnection.self, from: data)
        XCTAssertEqual(decoded.url, "https://pihole.local")
        XCTAssertEqual(decoded.token, "sid123")
        XCTAssertEqual(decoded.piholePassword, "secret")
        XCTAssertEqual(decoded.piholeAuthMode, .session)
        XCTAssertEqual(decoded.piHoleStoredSecret, "secret")
    }

    func testPiHoleLegacyConnectionDecodingFallsBackToApiKey() throws {
        let json = """
        {
            "type": "pihole",
            "url": "https://pihole.local",
            "token": "legacy-token",
            "apiKey": "legacy-secret"
        }
        """.data(using: .utf8)!

        let decoded = try JSONDecoder().decode(ServiceConnection.self, from: json)
        XCTAssertEqual(decoded.type, .pihole)
        XCTAssertEqual(decoded.token, "legacy-token")
        XCTAssertEqual(decoded.piHoleStoredSecret, "legacy-secret")
        XCTAssertNil(decoded.piholePassword)
        XCTAssertNil(decoded.piholeAuthMode)
    }

    func testPiHoleUpdatingTokenPreservesSecretAndMode() {
        let conn = ServiceConnection(
            type: .pihole,
            url: "https://pihole.local",
            token: "old",
            piholePassword: "secret",
            piholeAuthMode: .legacy
        )

        let updated = conn.updatingToken("new", piholeAuthMode: .session)
        XCTAssertEqual(updated.token, "new")
        XCTAssertEqual(updated.piholePassword, "secret")
        XCTAssertEqual(updated.piholeAuthMode, .session)
    }

    func testServiceConnectionFallbackUrl() throws {
        let conn1 = ServiceConnection(type: .pihole, url: "https://pihole.local", token: "sid123", fallbackUrl: "")
        XCTAssertNil(conn1.fallbackUrl) // empty string should be nil

        let conn2 = ServiceConnection(type: .pihole, url: "https://pihole.local", token: "sid123", fallbackUrl: "https://pihole.backup")
        XCTAssertEqual(conn2.fallbackUrl, "https://pihole.backup")
    }

    func testServiceInstanceEncoding() throws {
        let instance = ServiceInstance(
            id: UUID(uuidString: "30000000-0000-0000-0000-000000000001")!,
            type: .proxmox,
            label: "Proxmox Lab",
            url: "https://proxmox.lab/",
            token: "ticket123",
            username: "root",
            apiKey: "csrf456",
            proxmoxAuthMode: .credentials,
            proxmoxRealm: "pve",
            proxmoxOTP: "123456",
            fallbackUrl: "https://portainer.example.com/",
            allowSelfSigned: true
        )

        let data = try JSONEncoder().encode(instance)
        let decoded = try JSONDecoder().decode(ServiceInstance.self, from: data)

        XCTAssertEqual(decoded.id, instance.id)
        XCTAssertEqual(decoded.type, ServiceType.proxmox)
        XCTAssertEqual(decoded.label, "Proxmox Lab")
        XCTAssertEqual(decoded.url, "https://proxmox.lab")
        XCTAssertEqual(decoded.apiKey, "csrf456")
        XCTAssertEqual(decoded.username, "root")
        XCTAssertEqual(decoded.proxmoxAuthMode, ProxmoxAuthMode.credentials)
        XCTAssertEqual(decoded.proxmoxRealm, "pve")
        XCTAssertEqual(decoded.proxmoxOTP, "123456")
        XCTAssertEqual(decoded.fallbackUrl, "https://portainer.example.com")
        XCTAssertTrue(decoded.allowSelfSigned)
    }

    func testLegacyServiceConnectionMigrationUsesDisplayNameLabel() {
        let connection = ServiceConnection(
            type: .beszel,
            url: "https://beszel.local",
            token: "token-1",
            username: "ops@example.com"
        )

        let migrated = connection.migratedInstance(id: UUID(uuidString: "30000000-0000-0000-0000-000000000002")!)

        XCTAssertEqual(migrated.id.uuidString, "30000000-0000-0000-0000-000000000002")
        XCTAssertEqual(migrated.type, .beszel)
        XCTAssertEqual(migrated.label, ServiceType.beszel.displayName)
        XCTAssertEqual(migrated.url, "https://beszel.local")
        XCTAssertEqual(migrated.token, "token-1")
        XCTAssertEqual(migrated.username, "ops@example.com")
    }

    func testProxmoxTaskLogDecoding() throws {
        let json = """
        {
            "data": [
                { "n": 2, "t": "second line" },
                { "n": 1, "t": "first line" }
            ]
        }
        """.data(using: .utf8)!

        let decoded = try JSONDecoder().decode(ProxmoxAPIResponse<[ProxmoxTaskLogEntry]>.self, from: json)

        XCTAssertEqual(decoded.data.count, 2)
        XCTAssertEqual(decoded.data[0].n, 2)
        XCTAssertEqual(decoded.data[1].t, "first line")
    }

    func testProxmoxRRDDataDecodingProvidesComputedMetrics() throws {
        let json = """
        {
            "time": 1710000000,
            "cpu": 0.42,
            "maxcpu": 4,
            "mem": 2147483648,
            "maxmem": 4294967296,
            "netin": 1048576,
            "netout": 524288,
            "diskread": 4096,
            "diskwrite": 2048
        }
        """.data(using: .utf8)!

        let decoded = try JSONDecoder().decode(ProxmoxRRDData.self, from: json)

        XCTAssertEqual(decoded.cpuPercent, 42, accuracy: 0.01)
        XCTAssertEqual(decoded.memoryPercent, 50, accuracy: 0.01)
        XCTAssertEqual(decoded.networkRate, 1572864, accuracy: 0.01)
        XCTAssertEqual(decoded.diskRate, 6144, accuracy: 0.01)
        XCTAssertNotNil(decoded.date)
        XCTAssertTrue(decoded.hasData)
    }

    func testProxmoxApiTokenPartsParseRawToken() {
        let parsed = ProxmoxAPITokenParts(rawValue: "root@pam!homelab=super-secret-token")

        XCTAssertEqual(parsed?.user, "root")
        XCTAssertEqual(parsed?.realm, "pam")
        XCTAssertEqual(parsed?.tokenID, "homelab")
        XCTAssertEqual(parsed?.secret, "super-secret-token")
        XCTAssertEqual(parsed?.rawValue, "root@pam!homelab=super-secret-token")
    }

    func testProxmoxApiTokenPartsRejectInvalidToken() {
        XCTAssertNil(ProxmoxAPITokenParts(rawValue: "root@pam"))
        XCTAssertNil(ProxmoxAPITokenParts(user: "root", realm: "pam", tokenID: "", secret: "secret"))
    }

    func testProxmoxGuestConfigParsesDynamicInterfacesAndDisks() throws {
        let json = """
        {
            "name": "ubuntu-vm",
            "memory": 4096,
            "cores": 4,
            "cpu": "x86-64-v2-AES",
            "net0": "virtio=BC:24:11:22:33:44,bridge=vmbr0,firewall=1,tag=20,rate=50",
            "ipconfig0": "ip=192.168.10.20/24,gw=192.168.10.1",
            "scsi0": "local-lvm:vm-101-disk-0,size=64G,backup=1,replicate=0,ssd=1",
            "rootfs": "local-zfs:subvol-101-disk-0,size=8G"
        }
        """.data(using: .utf8)!

        let decoded = try JSONDecoder().decode(ProxmoxGuestConfig.self, from: json)

        XCTAssertEqual(decoded.networkInterfaces.count, 1)
        XCTAssertEqual(decoded.displayName, "ubuntu-vm")
        XCTAssertEqual(decoded.networkInterfaces.first?.model, "virtio")
        XCTAssertEqual(decoded.networkInterfaces.first?.bridge, "vmbr0")
        XCTAssertEqual(decoded.networkInterfaces.first?.ipAddress, "192.168.10.20/24")
        XCTAssertEqual(decoded.networkInterfaces.first?.gateway, "192.168.10.1")
        XCTAssertEqual(decoded.diskDevices.count, 2)
        XCTAssertEqual(decoded.diskDevices.first(where: { $0.key == "scsi0" })?.storage, "local-lvm")
        XCTAssertEqual(decoded.diskDevices.first(where: { $0.key == "scsi0" })?.size, "64G")
        XCTAssertEqual(decoded.diskDevices.first(where: { $0.key == "rootfs" })?.mountPoint, "/")
    }

    func testProxmoxGuestConfigParsesAgentFlags() throws {
        let json = """
        {
            "name": "debian-ct",
            "agent": "enabled=1,fstrim_cloned_disks=1"
        }
        """.data(using: .utf8)!

        let decoded = try JSONDecoder().decode(ProxmoxGuestConfig.self, from: json)

        XCTAssertEqual(decoded.guestAgentEnabled, true)
    }

    func testProxmoxGuestConfigUsesHostnameForLXCDisplayName() throws {
        let json = """
        {
            "hostname": "debian-ct",
            "memory": 2048,
            "cores": 2
        }
        """.data(using: .utf8)!

        let decoded = try JSONDecoder().decode(ProxmoxGuestConfig.self, from: json)

        XCTAssertEqual(decoded.hostname, "debian-ct")
        XCTAssertEqual(decoded.displayName, "debian-ct")
    }

    func testProxmoxGuestAgentUsersAndTimezoneDecode() throws {
        let usersJSON = """
        {
            "data": [
                { "user": "root", "login-time": 1712731092.25 },
                { "user": "ops", "domain": "LAB", "login-time": 1712732092.5 }
            ]
        }
        """.data(using: .utf8)!

        let timezoneJSON = """
        {
            "data": {
                "zone": "Europe/Rome",
                "offset": 7200
            }
        }
        """.data(using: .utf8)!

        let users = try JSONDecoder().decode(ProxmoxAPIResponse<[ProxmoxGuestAgentUser]>.self, from: usersJSON).data
        let timezone = try JSONDecoder().decode(ProxmoxAPIResponse<ProxmoxGuestAgentTimezone>.self, from: timezoneJSON).data

        XCTAssertEqual(users.count, 2)
        XCTAssertEqual(users[0].displayName, "root")
        XCTAssertEqual(users[1].displayName, "LAB\\ops")
        XCTAssertNotNil(users[0].loginDate)
        XCTAssertEqual(timezone.displayName, "Europe/Rome (UTC+02:00)")
    }

    func testProxmoxGuestAgentFilesystemDecodingProvidesUsageAndDiskSummary() throws {
        let json = """
        {
            "data": [
                {
                    "name": "/dev/sda2",
                    "mountpoint": "/",
                    "type": "ext4",
                    "used-bytes": 32212254720,
                    "total-bytes": 64424509440,
                    "disk": [
                        { "bus-type": "scsi", "dev": "/dev/sda" }
                    ]
                }
            ]
        }
        """.data(using: .utf8)!

        let decoded = try JSONDecoder().decode(ProxmoxAPIResponse<[ProxmoxGuestAgentFilesystem]>.self, from: json)
        let filesystem = try XCTUnwrap(decoded.data.first)

        XCTAssertEqual(filesystem.mountpoint, "/")
        XCTAssertEqual(filesystem.capacityBytes, 64_424_509_440)
        XCTAssertEqual(filesystem.usagePercent, 0.5, accuracy: 0.0001)
        XCTAssertEqual(filesystem.diskSummary, "scsi • /dev/sda")
    }

    func testProxmoxBackupJobFallbackIdIsStable() throws {
        let json = """
        {
            "enabled": 1,
            "storage": "pbs-store",
            "schedule": "daily",
            "vmid": "100,101",
            "mode": "snapshot"
        }
        """.data(using: .utf8)!

        let decoded = try JSONDecoder().decode(ProxmoxBackupJob.self, from: json)

        XCTAssertEqual(decoded.id, decoded.id)
        XCTAssertFalse(decoded.id.isEmpty)
        XCTAssertEqual(decoded.id, "pbs-store|daily|100,101|snapshot")
    }

    func testProxmoxSnapshotDecodingKeepsCurrentMarker() throws {
        let json = """
        {
            "name": "current",
            "description": "Current running state",
            "snaptime": 1700000000,
            "vmstate": 1
        }
        """.data(using: .utf8)!

        let decoded = try JSONDecoder().decode(ProxmoxSnapshot.self, from: json)

        XCTAssertTrue(decoded.isCurrent)
        XCTAssertTrue(decoded.hasVMState)
        XCTAssertEqual(decoded.name, "current")
    }

    func testProxmoxReplicationJobFallbackIdIsStable() throws {
        let json = """
        {
            "source": "pve-1",
            "target": "pve-2",
            "guest": 100,
            "schedule": "*/15",
            "type": "local"
        }
        """.data(using: .utf8)!

        let decoded = try JSONDecoder().decode(ProxmoxReplicationJob.self, from: json)

        XCTAssertEqual(decoded.id, decoded.id)
        XCTAssertFalse(decoded.id.isEmpty)
        XCTAssertEqual(decoded.id, "pve-1|pve-2|100|*/15|local")
    }

    func testProxmoxConsoleSessionUsesTicketCookie() async throws {
        let client = ProxmoxAPIClient(instanceId: UUID())
        await client.configure(
            url: "https://proxmox.lab",
            ticket: "ticket123",
            csrfToken: "csrf456",
            username: "root",
            password: "secret",
            realm: "pam"
        )

        let session = try await client.consoleSession(node: "pve-1", vmid: 101, type: "kvm")

        XCTAssertEqual(session.cookieName, "PVEAuthCookie")
        XCTAssertEqual(session.cookieValue, "ticket123")
        XCTAssertEqual(session.cookieDomain, "proxmox.lab")
        XCTAssertTrue(session.isSecure)
        XCTAssertEqual(
            session.url.absoluteString,
            "https://proxmox.lab/?console=kvm&vmid=101&node=pve-1&resize=off"
        )
    }

    func testProxmoxConsoleSessionTracksHttpScheme() async throws {
        let client = ProxmoxAPIClient(instanceId: UUID())
        await client.configure(
            url: "http://proxmox.lab:8006",
            ticket: "ticket123",
            csrfToken: "csrf456",
            username: "root",
            password: "secret",
            realm: "pam"
        )

        let session = try await client.consoleSession(node: "pve-1", vmid: 101, type: "lxc")

        XCTAssertFalse(session.isSecure)
        XCTAssertEqual(
            session.url.absoluteString,
            "http://proxmox.lab:8006/?console=lxc&vmid=101&node=pve-1&resize=off"
        )
    }

    func testProxmoxTaskRunningStateStopsAfterEndOrExitStatus() {
        let finished = ProxmoxTask(
            upid: "UPID:test",
            type: "qmstart",
            status: "stopped",
            starttime: 1_700_000_000,
            endtime: 1_700_000_060,
            exitstatus: "OK"
        )

        let failed = ProxmoxTask(
            upid: "UPID:failed",
            type: "qmstart",
            status: nil,
            starttime: 1_700_000_000,
            endtime: nil,
            exitstatus: "TASK ERROR: failed"
        )

        XCTAssertFalse(finished.isRunning)
        XCTAssertFalse(failed.isRunning)
        XCTAssertTrue(finished.isOk)
    }

    func testServiceTypeDecodesLinuxUpdateRawValue() throws {
        let data = Data("\"linux_update\"".utf8)
        let decoded = try JSONDecoder().decode(ServiceType.self, from: data)
        XCTAssertEqual(decoded, .linuxUpdate)
    }

    func testServiceTypeEncodesLinuxUpdateWithCanonicalRawValue() throws {
        let data = try JSONEncoder().encode(ServiceType.linuxUpdate)
        XCTAssertEqual(String(decoding: data, as: UTF8.self), "\"linux_update\"")
    }

    func testServiceTypeDecodesTrueNASAliases() throws {
        let aliases = ["truenas", "truenas_scale", "truenasscale", "truenas_core", "truenascore"]
        for alias in aliases {
            let data = Data("\"\(alias)\"".utf8)
            let decoded = try JSONDecoder().decode(ServiceType.self, from: data)
            XCTAssertEqual(decoded, .truenas, "Alias \(alias) should decode as TrueNAS")
        }
    }

    func testServiceTypeEncodesTrueNASWithCanonicalRawValue() throws {
        let data = try JSONEncoder().encode(ServiceType.truenas)
        XCTAssertEqual(String(decoding: data, as: UTF8.self), "\"truenas\"")
    }

    func testTrueNASAPIKeyTransportRequiresSecureWebSocket() {
        XCTAssertTrue(TrueNASAPIClient.usesSecureAPITransport("truenas.local"))
        XCTAssertTrue(TrueNASAPIClient.usesSecureAPITransport("https://truenas.local"))
        XCTAssertTrue(TrueNASAPIClient.usesSecureAPITransport("wss://truenas.local/api/current"))
        XCTAssertFalse(TrueNASAPIClient.usesSecureAPITransport("http://truenas.local"))
        XCTAssertFalse(TrueNASAPIClient.usesSecureAPITransport("ws://truenas.local/websocket"))
    }

    func testWakapiSummaryDecodingSupportsNativeSummaryPayload() throws {
        let json = """
        {
            "user_id": "writeuser",
            "from": "2026-04-07T00:00:00Z",
            "to": "2026-04-07T23:59:59Z",
            "projects": [
                { "key": "Homelab", "total": 5400 }
            ],
            "languages": [
                { "key": "Swift", "total": 3600 },
                { "key": "Kotlin", "total": 1800 }
            ],
            "editors": [
                { "key": "Xcode", "total": 5400 }
            ],
            "operating_systems": [
                { "key": "macOS", "total": 5400 }
            ],
            "machines": [
                { "key": "mbp", "total": 5400 }
            ]
        }
        """.data(using: .utf8)!

        let decoded = try JSONDecoder().decode(WakapiSummary.self, from: json)

        XCTAssertEqual(decoded.userId, "writeuser")
        XCTAssertEqual(decoded.projects?.first?.displayName, "Homelab")
        XCTAssertEqual(decoded.projects?.first?.effectiveTotalSeconds ?? 0, 5400, accuracy: 0.001)
        XCTAssertEqual(decoded.languages?.first?.displayName, "Swift")
        XCTAssertEqual(decoded.effectiveGrandTotal.hours, 1)
        XCTAssertEqual(decoded.effectiveGrandTotal.minutes, 30)
    }

    func testWakapiSummaryDecodingKeepsLegacyCompatiblePayload() throws {
        let json = """
        {
            "grand_total": {
                "hours": 2,
                "minutes": 15,
                "text": "2 hrs 15 mins",
                "total_seconds": 8100
            },
            "projects": [
                {
                    "name": "Homelab",
                    "hours": 2,
                    "minutes": 15,
                    "text": "2 hrs 15 mins",
                    "total_seconds": 8100,
                    "percent": 100
                }
            ]
        }
        """.data(using: .utf8)!

        let decoded = try JSONDecoder().decode(WakapiSummary.self, from: json)

        XCTAssertEqual(decoded.effectiveGrandTotal.hours, 2)
        XCTAssertEqual(decoded.effectiveGrandTotal.minutes, 15)
        XCTAssertEqual(decoded.projects?.first?.displayName, "Homelab")
        XCTAssertEqual(decoded.projects?.first?.resolvedPercent(sectionTotalSeconds: 8100) ?? 0, 100, accuracy: 0.001)
    }

    func testWakapiDailySummariesDecodingSupportsCompatPayload() throws {
        let json = """
        {
            "start": "2026-03-01T00:00:00Z",
            "end": "2026-04-07T23:59:59Z",
            "cumulative_total": {
                "decimal": "11.50",
                "digital": "11:30",
                "seconds": 41400,
                "text": "11 hrs 30 mins"
            },
            "daily_average": {
                "days_including_holidays": 30,
                "days_minus_holidays": 30,
                "holidays": 0,
                "seconds": 1380,
                "seconds_including_other_language": 1380,
                "text": "23 mins",
                "text_including_other_language": "23 mins"
            },
            "data": [
                {
                    "grand_total": {
                        "hours": 1,
                        "minutes": 30,
                        "text": "1 hr 30 mins",
                        "total_seconds": 5400
                    },
                    "languages": [
                        { "name": "Swift", "percent": 100, "total_seconds": 5400, "text": "1 hr 30 mins" }
                    ],
                    "range": {
                        "date": "2026-04-06T00:00:00Z",
                        "start": "2026-04-06T00:00:00Z",
                        "end": "2026-04-06T23:59:59Z",
                        "timezone": "UTC"
                    }
                }
            ]
        }
        """.data(using: .utf8)!

        let decoded = try JSONDecoder().decode(WakapiDailySummariesResponse.self, from: json)

        XCTAssertEqual(decoded.data.count, 1)
        XCTAssertEqual(decoded.cumulativeTotal?.seconds ?? 0, 41400, accuracy: 0.001)
        XCTAssertEqual(decoded.dailyAverage?.seconds, 1380)
        XCTAssertEqual(decoded.data.first?.grandTotal?.totalSeconds ?? 0, 5400, accuracy: 0.001)
        XCTAssertEqual(decoded.data.first?.languages?.first?.displayName, "Swift")
        XCTAssertEqual(decoded.data.first?.range?.timezone, "UTC")
    }

    func testProxmoxVMCreationRequestBuildsExpectedFormParameters() {
        let request = ProxmoxVMCreationRequest(
            vmid: 321,
            name: "ubuntu-prod",
            node: "pve-01",
            diskStorage: "local-lvm",
            diskSizeGiB: 64,
            memoryMB: 8192,
            cores: 4,
            sockets: 1,
            bridge: "vmbr0",
            isoVolumeId: "local:iso/ubuntu-24.04.iso",
            osType: "l26",
            bios: "ovmf",
            machine: "q35",
            pool: "lab",
            tags: "linux,production",
            description: "Primary Ubuntu VM",
            enableGuestAgent: true,
            startAtBoot: true,
            createAsTemplate: true
        )

        let params = request.formParameters()

        XCTAssertEqual(params["vmid"], "321")
        XCTAssertEqual(params["name"], "ubuntu-prod")
        XCTAssertEqual(params["scsi0"], "local-lvm:64")
        XCTAssertEqual(params["ide2"], "local:iso/ubuntu-24.04.iso,media=cdrom")
        XCTAssertEqual(params["boot"], "order=scsi0;ide2;net0")
        XCTAssertEqual(params["net0"], "virtio,bridge=vmbr0")
        XCTAssertEqual(params["agent"], "enabled=1")
        XCTAssertEqual(params["template"], "1")
        XCTAssertEqual(params["pool"], "lab")
    }

    func testProxmoxLXCCreationRequestBuildsStaticNetworkParameters() {
        let request = ProxmoxLXCCreationRequest(
            vmid: 205,
            hostname: "debian-ct",
            node: "pve-02",
            ostemplate: "local:vztmpl/debian-12-standard.tar.zst",
            rootfsStorage: "local-lvm",
            rootfsSizeGiB: 12,
            memoryMB: 2048,
            swapMB: 512,
            cores: 2,
            bridge: "vmbr1",
            addressMode: .staticAddress,
            ipv4Address: "10.0.50.20/24",
            gateway: "10.0.50.1",
            password: "secret",
            pool: "containers",
            tags: "debian,lxc",
            description: "Debian application container",
            unprivileged: true,
            startAtBoot: false
        )

        let params = request.formParameters()

        XCTAssertEqual(params["vmid"], "205")
        XCTAssertEqual(params["hostname"], "debian-ct")
        XCTAssertEqual(params["ostemplate"], "local:vztmpl/debian-12-standard.tar.zst")
        XCTAssertEqual(params["rootfs"], "local-lvm:12")
        XCTAssertEqual(params["net0"], "name=eth0,bridge=vmbr1,ip=10.0.50.20/24,gw=10.0.50.1")
        XCTAssertEqual(params["password"], "secret")
        XCTAssertEqual(params["unprivileged"], "1")
        XCTAssertEqual(params["onboot"], "0")
    }

    func testProxmoxBackupArchiveMetadataParsesFromVolumeIdentifier() {
        let volumeId = "pbs-store:backup/vzdump-qemu-412-2026_04_10-18_20_33.vma.zst"

        let metadata = ProxmoxBackupArchiveMetadata.parse(from: volumeId)

        XCTAssertEqual(metadata?.guestType, .qemu)
        XCTAssertEqual(metadata?.vmid, 412)
        XCTAssertEqual(metadata?.archiveName, "vzdump-qemu-412-2026_04_10-18_20_33.vma.zst")
    }

    func testProxmoxVMRestoreRequestBuildsExpectedFormParameters() {
        let request = ProxmoxVMRestoreRequest(
            vmid: 901,
            archiveVolumeId: "pbs-store:backup/vzdump-qemu-412-2026_04_10-18_20_33.vma.zst",
            storage: "local-lvm",
            unique: true,
            force: false,
            pool: "lab"
        )

        let params = request.formParameters()

        XCTAssertEqual(params["vmid"], "901")
        XCTAssertEqual(params["archive"], "pbs-store:backup/vzdump-qemu-412-2026_04_10-18_20_33.vma.zst")
        XCTAssertEqual(params["storage"], "local-lvm")
        XCTAssertEqual(params["unique"], "1")
        XCTAssertNil(params["force"])
        XCTAssertEqual(params["pool"], "lab")
    }

    func testProxmoxLXCRestoreRequestBuildsExpectedFormParameters() {
        let request = ProxmoxLXCRestoreRequest(
            vmid: 902,
            archiveVolumeId: "pbs-store:backup/vzdump-lxc-221-2026_04_10-18_20_33.tar.zst",
            storage: "local-zfs",
            unique: false,
            force: true,
            pool: nil
        )

        let params = request.formParameters()

        XCTAssertEqual(params["vmid"], "902")
        XCTAssertEqual(params["ostemplate"], "pbs-store:backup/vzdump-lxc-221-2026_04_10-18_20_33.tar.zst")
        XCTAssertEqual(params["storage"], "local-zfs")
        XCTAssertEqual(params["restore"], "1")
        XCTAssertEqual(params["force"], "1")
        XCTAssertNil(params["unique"])
    }
    private func controlledActionRequest(
        risk: ControlledActionRisk = .medium,
        dryRun: Bool = false,
        confirmed: Bool = false,
        idempotencyKey: String = "0123456789abcdef"
    ) -> ControlledActionRequest {
        ControlledActionRequest(
            id: "request-1",
            providerRef: "proxmox:cluster-a",
            action: "guest.shutdown",
            targetRef: "qemu/101",
            risk: risk,
            requestedAt: "1970-01-01T00:00:01Z",
            idempotencyKey: idempotencyKey,
            dryRun: dryRun,
            confirmed: confirmed
        )
    }

    func testControlledActionViewerCannotExecuteWrites() {
        let decision = ControlledActionPolicy.evaluate(
            controlledActionRequest(confirmed: true),
            actorRole: .viewer,
            providerCapabilities: [.writeActions]
        )

        XCTAssertEqual(decision.outcome, .denied)
        XCTAssertEqual(decision.reasonCode, "insufficient-role")
    }

    func testControlledActionMediumRiskRequiresConfirmation() {
        let decision = ControlledActionPolicy.evaluate(
            controlledActionRequest(),
            actorRole: .operatorRole,
            providerCapabilities: [.writeActions]
        )

        XCTAssertEqual(decision.outcome, .confirmationRequired)
        XCTAssertFalse(decision.mayExecute)
    }

    func testControlledActionHighRiskRequiresAdministrator() {
        let decision = ControlledActionPolicy.evaluate(
            controlledActionRequest(risk: .high, confirmed: true),
            actorRole: .operatorRole,
            providerCapabilities: [.writeActions]
        )

        XCTAssertEqual(decision.outcome, .denied)
        XCTAssertEqual(decision.requiredRole, .admin)
    }

    func testControlledActionDryRunDoesNotInvokeMutation() async {
        let counter = ActionInvocationCounter()
        let coordinator = ControlledActionCoordinator(
            now: { Date(timeIntervalSince1970: 2) }
        )

        let result = await coordinator.execute(
            request: controlledActionRequest(dryRun: true),
            actorRole: .operatorRole,
            providerCapabilities: [.writeActions]
        ) {
            await counter.increment()
        }
        let invocations = await counter.value

        XCTAssertEqual(result.state, .dryRun)
        XCTAssertEqual(invocations, 0)
    }

    func testControlledActionIdempotencyReturnsTerminalResult() async {
        let counter = ActionInvocationCounter()
        let coordinator = ControlledActionCoordinator(
            now: { Date(timeIntervalSince1970: 3) }
        )
        let request = controlledActionRequest(confirmed: true)

        let first = await coordinator.execute(
            request: request,
            actorRole: .operatorRole,
            providerCapabilities: [.writeActions]
        ) {
            await counter.increment()
        }
        let second = await coordinator.execute(
            request: request,
            actorRole: .operatorRole,
            providerCapabilities: [.writeActions]
        ) {
            await counter.increment()
        }
        let invocations = await counter.value
        let auditRecords = await coordinator.auditSnapshot()
        let states = auditRecords.map(\.state)

        XCTAssertEqual(first.state, .succeeded)
        XCTAssertEqual(first, second)
        XCTAssertEqual(invocations, 1)
        XCTAssertEqual(states, [.queued, .executing, .succeeded])
    }

    func testControlledActionProviderCapabilityIsEnforced() async {
        let counter = ActionInvocationCounter()
        let coordinator = ControlledActionCoordinator()

        let result = await coordinator.execute(
            request: controlledActionRequest(confirmed: true),
            actorRole: .admin,
            providerCapabilities: []
        ) {
            await counter.increment()
        }
        let invocations = await counter.value

        XCTAssertEqual(result.state, .rejected)
        XCTAssertEqual(result.reasonCode, "provider-write-capability-required")
        XCTAssertEqual(invocations, 0)
    }


    func testControlledActionDecodesOmittedOptionalFields() throws {
        let payload = Data(#"""
        {
          "schemaVersion": "1.0",
          "id": "request-1",
          "providerRef": "proxmox:cluster-a",
          "action": "guest.start",
          "targetRef": "qemu/101",
          "risk": "low",
          "requestedAt": "1970-01-01T00:00:01Z",
          "idempotencyKey": "0123456789abcdef"
        }
        """#.utf8)

        let request = try JSONDecoder().decode(ControlledActionRequest.self, from: payload)

        XCTAssertNil(request.tenantRef)
        XCTAssertEqual(request.parameters, [:])
        XCTAssertFalse(request.dryRun)
        XCTAssertFalse(request.confirmed)
    }

    func testControlledActionSerializesConcurrentMutations() async {
        let probe = ActionConcurrencyProbe()
        let coordinator = ControlledActionCoordinator()
        let firstRequest = controlledActionRequest(confirmed: true, idempotencyKey: "first-key-0000001")
        let secondRequest = controlledActionRequest(confirmed: true, idempotencyKey: "second-key-000001")

        async let first = coordinator.execute(
            request: firstRequest,
            actorRole: .operatorRole,
            providerCapabilities: [.writeActions]
        ) {
            await probe.begin()
            try? await Task.sleep(nanoseconds: 50_000_000)
            await probe.end()
        }
        async let second = coordinator.execute(
            request: secondRequest,
            actorRole: .operatorRole,
            providerCapabilities: [.writeActions]
        ) {
            await probe.begin()
            try? await Task.sleep(nanoseconds: 50_000_000)
            await probe.end()
        }
        _ = await (first, second)

        let maximumConcurrent = await probe.maximumConcurrent
        XCTAssertEqual(maximumConcurrent, 1)
    }

    func testControlledActionDeduplicatesConcurrentSameKey() async {
        let counter = ActionInvocationCounter()
        let coordinator = ControlledActionCoordinator()
        let request = controlledActionRequest(confirmed: true)

        async let first = coordinator.execute(
            request: request,
            actorRole: .operatorRole,
            providerCapabilities: [.writeActions]
        ) {
            await counter.increment()
            try? await Task.sleep(nanoseconds: 50_000_000)
        }
        async let second = coordinator.execute(
            request: request,
            actorRole: .operatorRole,
            providerCapabilities: [.writeActions]
        ) {
            await counter.increment()
        }
        let results = await (first, second)
        let invocations = await counter.value

        XCTAssertEqual(results.0, results.1)
        XCTAssertEqual(invocations, 1)
    }

    func testControlledActionIdempotencySurvivesAuditPruning() async {
        let counter = ActionInvocationCounter()
        let ledger = ControlledActionLedger(maximumRecords: 1)
        let coordinator = ControlledActionCoordinator(ledger: ledger)
        let firstRequest = controlledActionRequest(confirmed: true, idempotencyKey: "first-key-0000001")
        let secondRequest = controlledActionRequest(confirmed: true, idempotencyKey: "second-key-000001")

        _ = await coordinator.execute(
            request: firstRequest,
            actorRole: .operatorRole,
            providerCapabilities: [.writeActions]
        ) {
            await counter.increment()
        }
        _ = await coordinator.execute(
            request: secondRequest,
            actorRole: .operatorRole,
            providerCapabilities: [.writeActions]
        ) {
            await counter.increment()
        }
        _ = await coordinator.execute(
            request: firstRequest,
            actorRole: .operatorRole,
            providerCapabilities: [.writeActions]
        ) {
            await counter.increment()
        }
        let invocations = await counter.value
        let records = await coordinator.auditSnapshot()

        XCTAssertEqual(invocations, 2)
        XCTAssertEqual(records.count, 1)
    }

    func testProxmoxControlledLifecycleRequestUsesNormalizedReferences() throws {
        let instanceId = try XCTUnwrap(UUID(uuidString: "11111111-2222-3333-4444-555555555555"))
        let requestId = try XCTUnwrap(UUID(uuidString: "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"))
        let idempotencyKey = try XCTUnwrap(UUID(uuidString: "12345678-1234-1234-1234-123456789abc"))
        let request = ProxmoxControlledGuestAction.stop.request(
            instanceId: instanceId,
            node: "pve01",
            vmid: 101,
            guestType: .qemu,
            confirmed: true,
            requestId: requestId,
            requestedAt: Date(timeIntervalSince1970: 0),
            idempotencyKey: idempotencyKey
        )

        XCTAssertEqual(request.providerRef, "proxmox:11111111-2222-3333-4444-555555555555")
        XCTAssertEqual(request.action, "guest.stop")
        XCTAssertEqual(request.targetRef, "qemu/101@pve01")
        XCTAssertEqual(request.risk, .high)
        XCTAssertTrue(request.confirmed)
    }

    func testProxmoxControlledLifecycleRiskClasses() {
        XCTAssertEqual(ProxmoxControlledGuestAction.start.risk, .low)
        XCTAssertEqual(ProxmoxControlledGuestAction.resume.risk, .low)
        XCTAssertEqual(ProxmoxControlledGuestAction.shutdown.risk, .medium)
        XCTAssertEqual(ProxmoxControlledGuestAction.reboot.risk, .medium)
        XCTAssertEqual(ProxmoxControlledGuestAction.suspend.risk, .medium)
        XCTAssertEqual(ProxmoxControlledGuestAction.stop.risk, .high)
    }

    func testControlledActionRetriesLowRiskTransportFailures() async {
        let counter = ActionInvocationCounter()
        let delays = RetryDelayRecorder()
        let coordinator = ControlledActionCoordinator(
            retryPolicy: ActionRetryPolicy(
                maximumAttempts: 3,
                initialDelayMilliseconds: 10,
                maximumDelayMilliseconds: 40
            ),
            now: { Date(timeIntervalSince1970: 5) },
            waitBeforeRetry: { await delays.append($0) }
        )

        let result = await coordinator.execute(
            request: controlledActionRequest(risk: .low, confirmed: true),
            actorRole: .operatorRole,
            providerCapabilities: [.writeActions]
        ) {
            await counter.increment()
            if await counter.value < 3 {
                throw ControlledActionOperationError(
                    reasonCode: "provider-unavailable",
                    disposition: .retryable
                )
            }
        }

        let invocationCount = await counter.value
        let recordedDelays = await delays.values
        XCTAssertEqual(result.state, .succeeded)
        XCTAssertEqual(invocationCount, 3)
        XCTAssertEqual(recordedDelays, [10, 20])
    }

    func testControlledActionHighRiskTransportFailureRequiresManualReview() async {
        let counter = ActionInvocationCounter()
        let coordinator = ControlledActionCoordinator(waitBeforeRetry: { _ in })

        let result = await coordinator.execute(
            request: controlledActionRequest(risk: .high, confirmed: true),
            actorRole: .admin,
            providerCapabilities: [.writeActions]
        ) {
            await counter.increment()
            throw ControlledActionOperationError(
                reasonCode: "transport-error",
                disposition: .retryable
            )
        }

        let invocationCount = await counter.value
        XCTAssertEqual(result.state, .manualReview)
        XCTAssertEqual(invocationCount, 1)
        XCTAssertEqual(result.reasonCode, "automatic-retry-forbidden-transport-error")
    }

    func testControlledActionTerminalResultSurvivesCoordinatorReconstruction() async {
        let counter = ActionInvocationCounter()
        let store = InMemoryDurableActionQueueStore()
        let request = controlledActionRequest(risk: .low, confirmed: true)

        _ = await ControlledActionCoordinator(durableStore: store).execute(
            request: request,
            actorRole: .operatorRole,
            providerCapabilities: [.writeActions]
        ) { await counter.increment() }

        let recovered = await ControlledActionCoordinator(durableStore: store).execute(
            request: request,
            actorRole: .operatorRole,
            providerCapabilities: [.writeActions]
        ) { await counter.increment() }

        let invocationCount = await counter.value
        XCTAssertEqual(recovered.state, .succeeded)
        XCTAssertEqual(invocationCount, 1)
    }

    func testControlledActionInterruptedExecutionRecoversAsManualReview() async {
        let request = controlledActionRequest(risk: .low, confirmed: true)
        let entry = DurableActionQueueEntry(
            request: request,
            actorRole: .operatorRole,
            state: .executing,
            attemptCount: 1,
            reasonCode: "executing",
            updatedAt: Date(timeIntervalSince1970: 1)
        )
        let coordinator = ControlledActionCoordinator(
            durableStore: InMemoryDurableActionQueueStore(initialEntries: [entry]),
            now: { Date(timeIntervalSince1970: 2) }
        )

        let recovered = await coordinator.pendingRecovery()

        XCTAssertEqual(recovered.count, 1)
        XCTAssertEqual(recovered.first?.state, .manualReview)
        XCTAssertEqual(recovered.first?.reasonCode, "interrupted-execution")
    }

    func testControlledActionDurableQueueOmitsParameters() async {
        let store = InMemoryDurableActionQueueStore()
        let coordinator = ControlledActionCoordinator(durableStore: store)
        var request = controlledActionRequest(risk: .low, confirmed: true)
        request.parameters = ["secret": "value"]

        _ = await coordinator.execute(
            request: request,
            actorRole: .operatorRole,
            providerCapabilities: [.writeActions]
        ) {}

        let persistedParameters = await store.snapshot().first?.request.parameters
        XCTAssertEqual(persistedParameters, [:])
    }

    func testControlledActionRetriesWrappedAPITransportFailures() async {
        let counter = ActionInvocationCounter()
        let coordinator = ControlledActionCoordinator(
            retryPolicy: ActionRetryPolicy(
                maximumAttempts: 2,
                initialDelayMilliseconds: 0,
                maximumDelayMilliseconds: 0
            ),
            waitBeforeRetry: { _ in }
        )

        let result = await coordinator.execute(
            request: controlledActionRequest(risk: .low, confirmed: true),
            actorRole: .operatorRole,
            providerCapabilities: [.writeActions]
        ) {
            await counter.increment()
            if await counter.value == 1 {
                throw APIError.bothURLsFailed(
                    primaryError: APIError.networkError(URLError(.timedOut)),
                    fallbackError: URLError(.cannotConnectToHost)
                )
            }
        }

        let invocationCount = await counter.value
        XCTAssertEqual(result.state, .succeeded)
        XCTAssertEqual(invocationCount, 2)
    }

    func testControlledActionRecoveredTerminalKeyRejectsDifferentIdentity() async {
        let store = InMemoryDurableActionQueueStore()
        let original = controlledActionRequest(risk: .low, confirmed: true)

        _ = await ControlledActionCoordinator(durableStore: store).execute(
            request: original,
            actorRole: .operatorRole,
            providerCapabilities: [.writeActions]
        ) {}

        var conflicting = original
        conflicting = ControlledActionRequest(
            id: conflicting.id,
            providerRef: conflicting.providerRef,
            tenantRef: conflicting.tenantRef,
            action: conflicting.action,
            targetRef: "qemu/999",
            risk: conflicting.risk,
            requestedAt: conflicting.requestedAt,
            idempotencyKey: conflicting.idempotencyKey,
            parameters: conflicting.parameters,
            dryRun: conflicting.dryRun,
            confirmed: conflicting.confirmed
        )
        let counter = ActionInvocationCounter()
        let result = await ControlledActionCoordinator(durableStore: store).execute(
            request: conflicting,
            actorRole: .operatorRole,
            providerCapabilities: [.writeActions]
        ) { await counter.increment() }

        let invocationCount = await counter.value
        XCTAssertEqual(result.state, .rejected)
        XCTAssertEqual(result.reasonCode, "idempotency-key-conflict")
        XCTAssertEqual(invocationCount, 0)
    }

    func testPortainerContainerActionsHaveStableRiskClassificationAndIdentity() {
        XCTAssertEqual(PortainerControlledContainerAction.start.risk, .low)
        XCTAssertEqual(PortainerControlledContainerAction.stop.risk, .medium)
        XCTAssertEqual(PortainerControlledContainerAction.restart.risk, .medium)
        XCTAssertEqual(PortainerControlledContainerAction.kill.risk, .high)
        XCTAssertEqual(PortainerControlledContainerAction.remove.risk, .high)
        XCTAssertTrue(PortainerControlledContainerAction.pause.requiresConfirmation)
        XCTAssertFalse(PortainerControlledContainerAction.start.requiresConfirmation)

        let request = PortainerControlledContainerAction.stop.request(
            instanceId: UUID(uuidString: "00000000-0000-0000-0000-000000000001")!,
            endpointId: 7,
            containerId: "container-42",
            confirmed: true,
            requestId: UUID(uuidString: "00000000-0000-0000-0000-000000000002")!,
            requestedAt: Date(timeIntervalSince1970: 1),
            idempotencyKey: UUID(uuidString: "00000000-0000-0000-0000-000000000003")!
        )

        XCTAssertEqual(request.providerRef, "portainer:00000000-0000-0000-0000-000000000001")
        XCTAssertEqual(request.action, "container.stop")
        XCTAssertEqual(request.targetRef, "endpoint/7/container/container-42")
        XCTAssertTrue(request.confirmed)
        XCTAssertEqual(ContainerAction.unpause.controlledAction, .resume)
    }

    func testDockhandLifecycleActionsHaveStableRiskClassificationAndIdentity() {
        XCTAssertEqual(DockhandControlledAction.containerStart.risk, .low)
        XCTAssertEqual(DockhandControlledAction.containerStop.risk, .medium)
        XCTAssertEqual(DockhandControlledAction.stackRestart.risk, .medium)
        XCTAssertFalse(DockhandControlledAction.containerStart.requiresConfirmation)
        XCTAssertTrue(DockhandControlledAction.stackStop.requiresConfirmation)

        let request = DockhandControlledAction.containerRestart.request(
            instanceId: UUID(uuidString: "00000000-0000-0000-0000-000000000001")!,
            environmentId: "Production",
            targetKind: "container",
            targetId: "Web-01",
            confirmed: true,
            requestId: UUID(uuidString: "00000000-0000-0000-0000-000000000002")!,
            requestedAt: Date(timeIntervalSince1970: 1),
            idempotencyKey: UUID(uuidString: "00000000-0000-0000-0000-000000000003")!
        )

        XCTAssertEqual(request.providerRef, "dockhand:00000000-0000-0000-0000-000000000001")
        XCTAssertEqual(request.action, "container.restart")
        XCTAssertEqual(request.targetRef, "environment/production/container/web-01")
        XCTAssertTrue(request.confirmed)
        XCTAssertTrue(ProviderRegistry.descriptor(for: .dockhand).capabilities.contains(.writeActions))
    }

    func testPiholeDomainActionsRequireConfirmationAndHaveStableIdentity() {
        XCTAssertEqual(PiholeControlledDomainAction.add.risk, .high)
        XCTAssertEqual(PiholeControlledDomainAction.remove.risk, .medium)
        XCTAssertFalse(
            ActionRetryPolicy().permitsAutomaticRetry(
                risk: PiholeControlledDomainAction.add.risk,
                completedAttempts: 0
            )
        )

        let request = PiholeControlledDomainAction.remove.request(
            instanceId: UUID(uuidString: "00000000-0000-0000-0000-000000000001")!,
            domain: "Example.COM",
            listType: .deny,
            confirmed: true,
            requestId: UUID(uuidString: "00000000-0000-0000-0000-000000000002")!,
            requestedAt: Date(timeIntervalSince1970: 1),
            idempotencyKey: UUID(uuidString: "00000000-0000-0000-0000-000000000003")!
        )

        XCTAssertEqual(request.providerRef, "pi-hole:00000000-0000-0000-0000-000000000001")
        XCTAssertEqual(request.action, "domain.remove")
        XCTAssertEqual(request.targetRef, "domain/deny/example.com")
        XCTAssertTrue(request.confirmed)
        XCTAssertTrue(ProviderRegistry.descriptor(for: .pihole).capabilities.contains(.writeActions))
    }

    func testPiholeLegacyMutationFallbackRequiresUnsupportedEndpointStatus() {
        XCTAssertTrue(PiHoleAPIClient.shouldUseLegacyDomainMutation(APIError.httpError(statusCode: 404, body: "")))
        XCTAssertTrue(PiHoleAPIClient.shouldUseLegacyDomainMutation(APIError.httpError(statusCode: 405, body: "")))
        XCTAssertTrue(PiHoleAPIClient.shouldUseLegacyDomainMutation(APIError.httpError(statusCode: 501, body: "")))
        XCTAssertFalse(PiHoleAPIClient.shouldUseLegacyDomainMutation(APIError.httpError(statusCode: 500, body: "")))
        XCTAssertFalse(PiHoleAPIClient.shouldUseLegacyDomainMutation(URLError(.timedOut)))
    }

    func testAdGuardProtectionActionsHaveStableRiskClassificationAndIdentity() {
        XCTAssertEqual(AdGuardControlledProtectionAction.enable.risk, .low)
        XCTAssertEqual(AdGuardControlledProtectionAction.disable.risk, .medium)

        let request = AdGuardControlledProtectionAction.disable.request(
            instanceId: UUID(uuidString: "00000000-0000-0000-0000-000000000001")!,
            confirmed: true,
            requestId: UUID(uuidString: "00000000-0000-0000-0000-000000000002")!,
            requestedAt: Date(timeIntervalSince1970: 1),
            idempotencyKey: UUID(uuidString: "00000000-0000-0000-0000-000000000003")!
        )

        XCTAssertEqual(request.providerRef, "adguard-home:00000000-0000-0000-0000-000000000001")
        XCTAssertEqual(request.action, "protection.disable")
        XCTAssertEqual(request.targetRef, "protection/global")
        XCTAssertTrue(request.confirmed)
        XCTAssertTrue(
            ProviderRegistry.descriptor(for: .adguardHome).capabilities.contains(.writeActions)
        )
    }
    func testHealthchecksCheckActionsHaveStableRiskClassificationAndIdentity() {
        XCTAssertEqual(HealthchecksControlledCheckAction.create.risk, .high)
        XCTAssertFalse(
            ActionRetryPolicy().permitsAutomaticRetry(
                risk: HealthchecksControlledCheckAction.create.risk,
                completedAttempts: 1
            )
        )
        XCTAssertEqual(HealthchecksControlledCheckAction.update.risk, .medium)
        XCTAssertEqual(HealthchecksControlledCheckAction.updateChannels.risk, .medium)
        XCTAssertEqual(HealthchecksControlledCheckAction.pause.risk, .medium)
        XCTAssertEqual(HealthchecksControlledCheckAction.resume.risk, .medium)
        XCTAssertEqual(HealthchecksControlledCheckAction.delete.risk, .high)

        let request = HealthchecksControlledCheckAction.delete.request(
            instanceId: UUID(uuidString: "00000000-0000-0000-0000-000000000001")!,
            checkId: "check-42",
            confirmed: true,
            requestId: UUID(uuidString: "00000000-0000-0000-0000-000000000002")!,
            requestedAt: Date(timeIntervalSince1970: 1),
            idempotencyKey: UUID(uuidString: "00000000-0000-0000-0000-000000000003")!
        )

        XCTAssertEqual(request.providerRef, "healthchecks:00000000-0000-0000-0000-000000000001")
        XCTAssertEqual(request.action, "check.delete")
        XCTAssertEqual(request.targetRef, "check/check-42")
        XCTAssertTrue(request.confirmed)
        XCTAssertEqual(
            HealthchecksControlledCheckAction.updateChannels.actionName,
            "check.channels.update"
        )
    }

    func testHealthchecksProviderDeclaresControlledWriteActions() {
        XCTAssertTrue(
            ProviderRegistry.descriptor(for: .healthchecks).capabilities.contains(.writeActions)
        )
    }

    func testPortainerProviderDeclaresControlledWriteActions() {
        XCTAssertTrue(
            ProviderRegistry.descriptor(for: .portainer).capabilities.contains(.writeActions)
        )
    }

}

private actor ActionInvocationCounter {
    private(set) var value = 0

    func increment() {
        value += 1
    }
}

private actor ActionConcurrencyProbe {
    private var active = 0
    private(set) var maximumConcurrent = 0

    func begin() {
        active += 1
        maximumConcurrent = max(maximumConcurrent, active)
    }

    func end() {
        active -= 1
    }
}


private actor RetryDelayRecorder {
    private(set) var values: [Int] = []

    func append(_ value: Int) {
        values.append(value)
    }
}
