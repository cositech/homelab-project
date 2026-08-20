package com.homelab.app.data.remote.api

import com.homelab.app.data.remote.dto.linux_update.LinuxUpdateDashboardStatsResponse
import com.homelab.app.data.remote.dto.linux_update.LinuxUpdateDashboardSystemsResponse
import com.homelab.app.data.remote.dto.linux_update.LinuxUpdateJobStartResponse
import com.homelab.app.data.remote.dto.linux_update.LinuxUpdateJobStatusResponse
import com.homelab.app.data.remote.dto.linux_update.LinuxUpdateRebootResponse
import com.homelab.app.data.remote.dto.linux_update.LinuxUpdateSystemDetailResponse
import com.homelab.app.data.remote.dto.linux_update.LinuxUpdateUpgradePackagesRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface LinuxUpdateApi {

    @GET("api/dashboard/stats")
    suspend fun getDashboardStats(
        @Header("X-Homelab-Service") service: String = "Linux Update",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): LinuxUpdateDashboardStatsResponse

    @GET("api/dashboard/systems")
    suspend fun getDashboardSystems(
        @Header("X-Homelab-Service") service: String = "Linux Update",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): LinuxUpdateDashboardSystemsResponse

    @POST("api/systems/check-all")
    suspend fun checkAllSystems(
        @Header("X-Homelab-Service") service: String = "Linux Update",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Header("X-Homelab-No-Fallback") noFallback: String = "true"
    ): LinuxUpdateJobStartResponse

    @POST("api/cache/refresh")
    suspend fun refreshCache(
        @Header("X-Homelab-Service") service: String = "Linux Update",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Header("X-Homelab-No-Fallback") noFallback: String = "true"
    ): LinuxUpdateJobStartResponse

    @GET("api/systems/{systemId}")
    suspend fun getSystemDetail(
        @Path("systemId") systemId: Int,
        @Header("X-Homelab-Service") service: String = "Linux Update",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): LinuxUpdateSystemDetailResponse

    @POST("api/systems/{systemId}/check")
    suspend fun checkSystem(
        @Path("systemId") systemId: Int,
        @Header("X-Homelab-Service") service: String = "Linux Update",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Header("X-Homelab-No-Fallback") noFallback: String = "true"
    ): LinuxUpdateJobStartResponse

    @POST("api/systems/{systemId}/upgrade")
    suspend fun upgradeSystem(
        @Path("systemId") systemId: Int,
        @Header("X-Homelab-Service") service: String = "Linux Update",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Header("X-Homelab-No-Fallback") noFallback: String = "true"
    ): LinuxUpdateJobStartResponse

    @POST("api/systems/{systemId}/full-upgrade")
    suspend fun fullUpgradeSystem(
        @Path("systemId") systemId: Int,
        @Header("X-Homelab-Service") service: String = "Linux Update",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Header("X-Homelab-No-Fallback") noFallback: String = "true"
    ): LinuxUpdateJobStartResponse

    @POST("api/systems/{systemId}/upgrade-packages")
    suspend fun upgradePackages(
        @Path("systemId") systemId: Int,
        @Body request: LinuxUpdateUpgradePackagesRequest,
        @Header("X-Homelab-Service") service: String = "Linux Update",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Header("X-Homelab-No-Fallback") noFallback: String = "true"
    ): LinuxUpdateJobStartResponse

    @POST("api/systems/{systemId}/upgrade/{packageName}")
    suspend fun upgradePackage(
        @Path("systemId") systemId: Int,
        @Path("packageName") packageName: String,
        @Header("X-Homelab-Service") service: String = "Linux Update",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Header("X-Homelab-No-Fallback") noFallback: String = "true"
    ): LinuxUpdateJobStartResponse

    @POST("api/systems/{systemId}/reboot")
    suspend fun rebootSystem(
        @Path("systemId") systemId: Int,
        @Header("X-Homelab-Service") service: String = "Linux Update",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Header("X-Homelab-No-Fallback") noFallback: String = "true"
    ): LinuxUpdateRebootResponse

    @GET("api/jobs/{jobId}")
    suspend fun getJobStatus(
        @Path("jobId") jobId: String,
        @Header("X-Homelab-Service") service: String = "Linux Update",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): LinuxUpdateJobStatusResponse
}
