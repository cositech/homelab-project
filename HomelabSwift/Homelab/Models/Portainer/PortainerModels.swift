import Foundation

// MARK: - Auth

struct PortainerAuthResponse: Codable {
    let jwt: String
}

// MARK: - Endpoint

struct PortainerEndpoint: Codable, Identifiable {
    let Id: Int
    let Name: String
    let endpointType: Int?
    let URL: String?
    let Status: Int?
    let Snapshots: [EndpointSnapshot]?
    let PublicURL: String?
    let GroupId: Int?
    let TagIds: [Int]?

    var id: Int { Id }

    var isOnline: Bool { Status == 1 }

    enum CodingKeys: String, CodingKey {
        case Id, Name, URL, Status, Snapshots, PublicURL, GroupId, TagIds
        case endpointType = "Type"
    }
}

struct EndpointSnapshot: Codable {
    let DockerVersion: String?
    let TotalCPU: Int?
    let TotalMemory: Int?
    let RunningContainerCount: Int?
    let StoppedContainerCount: Int?
    let HealthyContainerCount: Int?
    let UnhealthyContainerCount: Int?
    let VolumeCount: Int?
    let ImageCount: Int?
    let ServiceCount: Int?
    let StackCount: Int?
    let NodeCount: Int?
    let Time: Int?
    let DockerSnapshotRaw: DockerSnapshotRaw?
}

struct DockerSnapshotRaw: Codable {
    /// Portainer's DockerSnapshotRaw schema varies across versions:
    /// it can be a lightweight summary OR a full docker info payload (with containers array).
    /// We only need the host name, so keep a minimal, tolerant model.
    let Name: String?
    let Info: DockerInfo?

    struct DockerInfo: Codable {
        let Name: String?
    }

    var hostName: String? {
        return Name ?? Info?.Name
    }
}

// MARK: - Container

struct PortainerContainer: Codable, Identifiable {
    let Id: String
    let Names: [String]?
    let Image: String?
    let ImageID: String?
    let Command: String?
    let Created: Int?
    let State: String?
    let Status: String?
    let Ports: [ContainerPort]?
    let Labels: [String: String]?
    let SizeRw: Int?
    let SizeRootFs: Int?
    let HostConfig: ContainerHostConfig?
    let NetworkSettings: ContainerNetworkSettings?
    let Mounts: [ContainerMount]?

    var id: String { Id }

    var displayName: String {
        guard let names = Names, !names.isEmpty else { return "Unknown" }
        return names[0].replacingOccurrences(of: "^/", with: "", options: .regularExpression)
    }
}

struct ContainerPort: Codable {
    let IP: String?
    let PrivatePort: Int
    let PublicPort: Int?
    let portType: String

    enum CodingKeys: String, CodingKey {
        case IP, PrivatePort, PublicPort
        case portType = "Type"
    }
}

struct ContainerHostConfig: Codable {
    let NetworkMode: String
    let RestartPolicy: RestartPolicy?
}

struct RestartPolicy: Codable {
    let Name: String
    let MaximumRetryCount: Int
}

struct ContainerNetworkSettings: Codable {
    let Networks: [String: ContainerNetwork]
}

struct ContainerNetwork: Codable {
    let IPAddress: String
    let Gateway: String
    let MacAddress: String
    let NetworkID: String
}

struct ContainerMount: Codable {
    let mountType: String
    let Name: String?
    let Source: String
    let Destination: String
    let Mode: String
    let RW: Bool

    enum CodingKeys: String, CodingKey {
        case Name, Source, Destination, Mode, RW
        case mountType = "Type"
    }
}

// MARK: - Container Detail

struct ContainerDetail: Codable {
    let Id: String
    let Name: String?
    let Created: String?
    let State: ContainerState?
    let Image: String?
    let Config: ContainerConfig?
    let HostConfig: ContainerDetailHostConfig?
    let NetworkSettings: ContainerDetailNetworkSettings?
    let Mounts: [ContainerMount]?
}

struct ContainerState: Codable {
    let Status: String?
    let Running: Bool?
    let Paused: Bool?
    let Restarting: Bool?
    let OOMKilled: Bool?
    let Dead: Bool?
    let Pid: Int?
    let ExitCode: Int?
    let Error: String?
    let StartedAt: String?
    let FinishedAt: String?
}

struct ContainerConfig: Codable {
    let Hostname: String?
    let Env: [String]?
    let Image: String?
    let Labels: [String: String]?
    let Cmd: [String]?
    let Entrypoint: [String]?
    let WorkingDir: String?
}

struct ContainerDetailHostConfig: Codable {
    let NetworkMode: String?
    let RestartPolicy: RestartPolicy?
    let Memory: Int?
    let NanoCpus: Int?
    let CpuShares: Int?
    let Binds: [String]?
}

struct ContainerDetailNetworkSettings: Codable {
    let Networks: [String: ContainerNetwork]
}

// MARK: - Container Stats

struct ContainerStats: Codable {
    let cpu_stats: CpuStats?
    let precpu_stats: CpuStats?
    let memory_stats: MemoryStats?
    let networks: [String: NetworkStats]?
    let blkio_stats: BlkioStats?
    let pids_stats: PidsStats?
}

struct PidsStats: Codable {
    let current: Int?
}

struct CpuStats: Codable {
    let cpu_usage: CpuUsage
    let system_cpu_usage: Int?
    let online_cpus: Int?
}

struct CpuUsage: Codable {
    let total_usage: Int
    let percpu_usage: [Int]?
}

struct MemoryStats: Codable {
    let usage: Int?
    let limit: Int?
    let stats: MemoryCacheStats?
}

struct MemoryCacheStats: Codable {
    let cache: Int?
}

struct NetworkStats: Codable {
    let rx_bytes: Int
    let tx_bytes: Int
}

struct BlkioStats: Codable {
    let io_service_bytes_recursive: [BlkioEntry]?
}

struct BlkioEntry: Codable {
    let op: String
    let value: Int
}

// MARK: - Stack

struct PortainerStack: Codable, Identifiable {
    let Id: Int
    let Name: String
    let stackType: Int
    let EndpointId: Int
    let Status: Int
    let CreationDate: Int?
    let UpdateDate: Int?

    var id: Int { Id }
    var isActive: Bool { Status == 1 }

    enum CodingKeys: String, CodingKey {
        case Id, Name, EndpointId, Status, CreationDate, UpdateDate
        case stackType = "Type"
    }
}

struct PortainerStackFile: Codable {
    let StackFileContent: String
}

// MARK: - Actions

enum ContainerAction: String, CaseIterable {
    case start
    case stop
    case restart
    case kill
    case pause
    case unpause

    var displayName: String {
        switch self {
        case .start:   return "Start"
        case .stop:    return "Stop"
        case .restart: return "Restart"
        case .kill:    return "Kill"
        case .pause:   return "Pause"
        case .unpause: return "Resume"
        }
    }

    var symbolName: String {
        switch self {
        case .start:   return "play.fill"
        case .stop:    return "stop.fill"
        case .restart: return "arrow.counterclockwise"
        case .kill:    return "xmark.circle.fill"
        case .pause:   return "pause.fill"
        case .unpause: return "play.fill"
        }
    }

    var isDestructive: Bool {
        self == .kill || self == .stop
    }

    var controlledAction: PortainerControlledContainerAction {
        switch self {
        case .start: return .start
        case .stop: return .stop
        case .restart: return .restart
        case .kill: return .kill
        case .pause: return .pause
        case .unpause: return .resume
        }
    }
}
