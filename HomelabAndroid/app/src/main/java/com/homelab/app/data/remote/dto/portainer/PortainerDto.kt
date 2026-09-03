package com.homelab.app.data.remote.dto.portainer

import com.homelab.app.domain.action.ActionRisk
import com.homelab.app.domain.action.ControlledActionRequest
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PortainerAuthResponse(
    val jwt: String = ""
)

@Serializable
data class PortainerEndpoint(
    @SerialName("Id") val id: Int = 0,
    @SerialName("Name") val name: String = "",
    @SerialName("Type") val type: Int = 0,
    @SerialName("URL") val url: String = "",
    @SerialName("Status") val status: Int = 0,
    @SerialName("Snapshots") val snapshots: List<EndpointSnapshot>? = null,
    @SerialName("PublicURL") val publicUrl: String? = null,
    @SerialName("GroupId") val groupId: Int? = null,
    @SerialName("TagIds") val tagIds: List<Int>? = null
) {
    val isOnline: Boolean get() = status == 1
}

@Serializable
data class EndpointSnapshot(
    @SerialName("DockerVersion") val dockerVersion: String? = null,
    @SerialName("TotalCPU") val totalCpu: Int = 0,
    @SerialName("TotalMemory") val totalMemory: Long = 0,
    @SerialName("RunningContainerCount") val runningContainerCount: Int = 0,
    @SerialName("StoppedContainerCount") val stoppedContainerCount: Int = 0,
    @SerialName("HealthyContainerCount") val healthyContainerCount: Int = 0,
    @SerialName("UnhealthyContainerCount") val unhealthyContainerCount: Int = 0,
    @SerialName("VolumeCount") val volumeCount: Int = 0,
    @SerialName("ImageCount") val imageCount: Int = 0,
    @SerialName("ServiceCount") val serviceCount: Int = 0,
    @SerialName("StackCount") val stackCount: Int = 0,
    @SerialName("NodeCount") val nodeCount: Int? = null,
    @SerialName("Time") val time: Long = 0,
    @SerialName("DockerSnapshotRaw") val dockerSnapshotRaw: DockerSnapshotRaw? = null
)

@Serializable
data class DockerSnapshotRaw(
    @SerialName("Name") val name: String? = null,
    @SerialName("OperatingSystem") val operatingSystem: String? = null,
    @SerialName("Architecture") val architecture: String? = null,
    @SerialName("ServerVersion") val serverVersion: String? = null,
    @SerialName("Info") val info: DockerInfo? = null
) {
    @Serializable
    data class DockerInfo(
        @SerialName("Name") val name: String? = null,
        @SerialName("OperatingSystem") val operatingSystem: String? = null,
        @SerialName("Architecture") val architecture: String? = null,
        @SerialName("ServerVersion") val serverVersion: String? = null
    )

    val hostName: String?
        get() = name ?: info?.name

    val resolvedOperatingSystem: String?
        get() = operatingSystem ?: info?.operatingSystem

    val resolvedArchitecture: String?
        get() = architecture ?: info?.architecture

    val resolvedServerVersion: String?
        get() = serverVersion ?: info?.serverVersion
}

@Serializable
data class PortainerContainer(
    @SerialName("Id") val id: String = "",
    @SerialName("Names") val names: List<String> = emptyList(),
    @SerialName("Image") val image: String = "",
    @SerialName("ImageID") val imageId: String = "",
    @SerialName("Command") val command: String = "",
    @SerialName("Created") val created: Long = 0,
    @SerialName("State") val state: String = "",
    @SerialName("Status") val status: String = "",
    @SerialName("Ports") val ports: List<ContainerPort> = emptyList(),
    @SerialName("Labels") val labels: Map<String, String> = emptyMap(),
    @SerialName("SizeRw") val sizeRw: Long? = null,
    @SerialName("SizeRootFs") val sizeRootFs: Long? = null,
    @SerialName("HostConfig") val hostConfig: ContainerHostConfig? = null,
    @SerialName("NetworkSettings") val networkSettings: ContainerNetworkSettings? = null,
    @SerialName("Mounts") val mounts: List<ContainerMount> = emptyList()
) {
    val displayName: String
        get() = if (names.isNotEmpty()) names[0].replace(Regex("^/"), "") else "Unknown"
}

@Serializable
data class ContainerPort(
    @SerialName("IP") val ip: String? = null,
    @SerialName("PrivatePort") val privatePort: Int = 0,
    @SerialName("PublicPort") val publicPort: Int? = null,
    @SerialName("Type") val type: String = ""
)

@Serializable
data class ContainerHostConfig(
    @SerialName("NetworkMode") val networkMode: String = "",
    @SerialName("RestartPolicy") val restartPolicy: RestartPolicy? = null
)

@Serializable
data class RestartPolicy(
    @SerialName("Name") val name: String = "",
    @SerialName("MaximumRetryCount") val maximumRetryCount: Int = 0
)

@Serializable
data class ContainerNetworkSettings(
    @SerialName("Networks") val networks: Map<String, ContainerNetwork> = emptyMap()
)

@Serializable
data class ContainerNetwork(
    @SerialName("IPAddress") val ipAddress: String = "",
    @SerialName("Gateway") val gateway: String = "",
    @SerialName("MacAddress") val macAddress: String = "",
    @SerialName("NetworkID") val networkId: String = ""
)

@Serializable
data class ContainerMount(
    @SerialName("Type") val type: String = "",
    @SerialName("Name") val name: String? = null,
    @SerialName("Source") val source: String = "",
    @SerialName("Destination") val destination: String = "",
    @SerialName("Mode") val mode: String = "",
    @SerialName("RW") val rw: Boolean = false
)

@Serializable
data class ContainerDetail(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String = "",
    @SerialName("Created") val created: String = "",
    @SerialName("State") val state: ContainerState = ContainerState(),
    @SerialName("Image") val image: String = "",
    @SerialName("Config") val config: ContainerConfig = ContainerConfig(),
    @SerialName("HostConfig") val hostConfig: ContainerDetailHostConfig = ContainerDetailHostConfig(),
    @SerialName("NetworkSettings") val networkSettings: ContainerDetailNetworkSettings = ContainerDetailNetworkSettings(),
    @SerialName("Mounts") val mounts: List<ContainerMount> = emptyList()
) {
    val displayName: String
        get() = name.removePrefix("/")
}

@Serializable
data class ContainerState(
    @SerialName("Status") val status: String = "",
    @SerialName("Running") val running: Boolean = false,
    @SerialName("Paused") val paused: Boolean = false,
    @SerialName("Restarting") val restarting: Boolean = false,
    @SerialName("OOMKilled") val oomKilled: Boolean = false,
    @SerialName("Dead") val dead: Boolean = false,
    @SerialName("Pid") val pid: Int = 0,
    @SerialName("ExitCode") val exitCode: Int = 0,
    @SerialName("Error") val error: String = "",
    @SerialName("StartedAt") val startedAt: String = "",
    @SerialName("FinishedAt") val finishedAt: String = ""
)

@Serializable
data class ContainerConfig(
    @SerialName("Hostname") val hostname: String = "",
    @SerialName("Env") val env: List<String> = emptyList(),
    @SerialName("Image") val image: String = "",
    @SerialName("Labels") val labels: Map<String, String> = emptyMap(),
    @SerialName("Cmd") val cmd: List<String>? = null,
    @SerialName("Entrypoint") val entrypoint: List<String>? = null,
    @SerialName("WorkingDir") val workingDir: String? = null
)

@Serializable
data class ContainerDetailHostConfig(
    @SerialName("NetworkMode") val networkMode: String = "",
    @SerialName("RestartPolicy") val restartPolicy: RestartPolicy = RestartPolicy(),
    @SerialName("Memory") val memory: Long = 0,
    @SerialName("NanoCpus") val nanoCpus: Long = 0,
    @SerialName("CpuShares") val cpuShares: Int = 0,
    @SerialName("Binds") val binds: List<String>? = null
)

@Serializable
data class ContainerDetailNetworkSettings(
    @SerialName("Networks") val networks: Map<String, ContainerNetwork> = emptyMap()
)

@Serializable
data class ContainerStats(
    val cpu_stats: CpuStats = CpuStats(),
    val precpu_stats: CpuStats = CpuStats(),
    val memory_stats: MemoryStats = MemoryStats(),
    val pids_stats: PidsStats? = null,
    val networks: Map<String, NetworkStats>? = null,
    val blkio_stats: BlkioStats? = null
)

@Serializable
data class PidsStats(
    val current: Int = 0
)

@Serializable
data class CpuStats(
    val cpu_usage: CpuUsage = CpuUsage(),
    val system_cpu_usage: Long? = null,
    val online_cpus: Int? = null
)

@Serializable
data class CpuUsage(
    val total_usage: Long = 0,
    val percpu_usage: List<Long>? = null
)

@Serializable
data class MemoryStats(
    val usage: Long = 0,
    val limit: Long = 0,
    val stats: MemoryCacheStats? = null
)

@Serializable
data class MemoryCacheStats(
    val cache: Long? = null
)

@Serializable
data class NetworkStats(
    val rx_bytes: Long = 0,
    val tx_bytes: Long = 0
)

@Serializable
data class BlkioStats(
    val io_service_bytes_recursive: List<BlkioEntry>? = null
)

@Serializable
data class BlkioEntry(
    val op: String = "",
    val value: Long = 0
)

@Serializable
data class PortainerStack(
    @SerialName("Id") val id: Int = 0,
    @SerialName("Name") val name: String = "",
    @SerialName("Type") val type: Int = 0,
    @SerialName("EndpointId") val endpointId: Int = 0,
    @SerialName("Status") val status: Int = 0,
    @SerialName("CreationDate") val creationDate: Long? = null,
    @SerialName("UpdateDate") val updateDate: Long? = null
) {
    val isActive: Boolean get() = status == 1
}

@Serializable
data class PortainerStackFile(
    @SerialName("StackFileContent") val stackFileContent: String = ""
)

@Serializable
data class UpdateStackRequest(
    val stackFileContent: String,
    val env: List<String> = emptyList(),
    val prune: Boolean = false
)

enum class PortainerControlledConfigurationAction(
    val wireName: String,
    val risk: ActionRisk
) {
    RENAME_CONTAINER("container.rename", ActionRisk.MEDIUM),
    UPDATE_STACK("stack.update", ActionRisk.HIGH);

    val requiresConfirmation: Boolean get() = true

    fun controlledRequest(
        instanceId: String,
        endpointId: Int,
        targetId: String,
        confirmed: Boolean,
        requestId: String = UUID.randomUUID().toString(),
        requestedAt: String = Instant.now().toString(),
        idempotencyKey: String = UUID.randomUUID().toString()
    ): ControlledActionRequest {
        val normalizedTargetId = targetId.trim()
        val targetRef = when (this) {
            RENAME_CONTAINER -> "endpoint/$endpointId/container/$normalizedTargetId"
            UPDATE_STACK -> "endpoint/$endpointId/stack/$normalizedTargetId"
        }
        return ControlledActionRequest(
            id = requestId,
            providerRef = "portainer:${instanceId.trim()}",
            action = wireName,
            targetRef = targetRef,
            risk = risk,
            requestedAt = requestedAt,
            idempotencyKey = idempotencyKey,
            confirmed = confirmed
        )
    }
}

enum class ContainerAction(
    val displayName: String,
    val isDestructive: Boolean,
    val wireName: String,
    val risk: ActionRisk
) {
    start("Start", false, "container.start", ActionRisk.LOW),
    stop("Stop", true, "container.stop", ActionRisk.MEDIUM),
    restart("Restart", false, "container.restart", ActionRisk.MEDIUM),
    kill("Kill", true, "container.kill", ActionRisk.HIGH),
    pause("Pause", false, "container.pause", ActionRisk.MEDIUM),
    unpause("Resume", false, "container.resume", ActionRisk.MEDIUM);

    val requiresConfirmation: Boolean get() = risk != ActionRisk.LOW

    fun controlledRequest(
        instanceId: String,
        endpointId: Int,
        containerId: String,
        confirmed: Boolean,
        requestId: String = UUID.randomUUID().toString(),
        requestedAt: String = Instant.now().toString(),
        idempotencyKey: String = UUID.randomUUID().toString()
    ) = ControlledActionRequest(
        id = requestId,
        providerRef = "portainer:$instanceId",
        action = wireName,
        targetRef = "endpoint/$endpointId/container/$containerId",
        risk = risk,
        requestedAt = requestedAt,
        idempotencyKey = idempotencyKey,
        confirmed = confirmed
    )
}
