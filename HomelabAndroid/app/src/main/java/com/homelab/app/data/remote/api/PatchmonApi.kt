package com.homelab.app.data.remote.api

import com.homelab.app.data.remote.dto.patchmon.PatchmonAgentQueueResponse
import com.homelab.app.data.remote.dto.patchmon.PatchmonDeleteResponse
import com.homelab.app.data.remote.dto.patchmon.PatchmonHostInfo
import com.homelab.app.data.remote.dto.patchmon.PatchmonHostNetwork
import com.homelab.app.data.remote.dto.patchmon.PatchmonHostStats
import com.homelab.app.data.remote.dto.patchmon.PatchmonHostSystem
import com.homelab.app.data.remote.dto.patchmon.PatchmonHostsResponse
import com.homelab.app.data.remote.dto.patchmon.PatchmonIntegrationsResponse
import com.homelab.app.data.remote.dto.patchmon.PatchmonNotesResponse
import com.homelab.app.data.remote.dto.patchmon.PatchmonPackagesResponse
import com.homelab.app.data.remote.dto.patchmon.PatchmonReportsResponse
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface PatchmonApi {

    @GET("api/v1/api/hosts")
    suspend fun getHosts(
        @Header("X-Homelab-Service") service: String = "PatchMon",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Query("include") include: String = "stats",
        @Query("hostgroup") hostGroup: String? = null
    ): PatchmonHostsResponse

    @GET("api/v1/api/hosts/{id}/info")
    suspend fun getHostInfo(
        @Header("X-Homelab-Service") service: String = "PatchMon",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("id") hostId: String
    ): PatchmonHostInfo

    @GET("api/v1/api/hosts/{id}/stats")
    suspend fun getHostStats(
        @Header("X-Homelab-Service") service: String = "PatchMon",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("id") hostId: String
    ): PatchmonHostStats

    @GET("api/v1/api/hosts/{id}/system")
    suspend fun getHostSystem(
        @Header("X-Homelab-Service") service: String = "PatchMon",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("id") hostId: String
    ): PatchmonHostSystem

    @GET("api/v1/api/hosts/{id}/network")
    suspend fun getHostNetwork(
        @Header("X-Homelab-Service") service: String = "PatchMon",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("id") hostId: String
    ): PatchmonHostNetwork

    @GET("api/v1/api/hosts/{id}/packages")
    suspend fun getHostPackages(
        @Header("X-Homelab-Service") service: String = "PatchMon",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("id") hostId: String,
        @Query("updates_only") updatesOnly: Boolean? = null
    ): PatchmonPackagesResponse

    @GET("api/v1/api/hosts/{id}/package_reports")
    suspend fun getHostReports(
        @Header("X-Homelab-Service") service: String = "PatchMon",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("id") hostId: String,
        @Query("limit") limit: Int = 10
    ): PatchmonReportsResponse

    @GET("api/v1/api/hosts/{id}/agent_queue")
    suspend fun getHostAgentQueue(
        @Header("X-Homelab-Service") service: String = "PatchMon",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("id") hostId: String,
        @Query("limit") limit: Int = 10
    ): PatchmonAgentQueueResponse

    @GET("api/v1/api/hosts/{id}/notes")
    suspend fun getHostNotes(
        @Header("X-Homelab-Service") service: String = "PatchMon",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("id") hostId: String
    ): PatchmonNotesResponse

    @GET("api/v1/api/hosts/{id}/integrations")
    suspend fun getHostIntegrations(
        @Header("X-Homelab-Service") service: String = "PatchMon",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("id") hostId: String
    ): PatchmonIntegrationsResponse

    @DELETE("api/v1/api/hosts/{id}")
    suspend fun deleteHost(
        @Header("X-Homelab-Service") service: String = "PatchMon",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Header("X-Homelab-No-Fallback") noFallback: Boolean = true,
        @Path("id") hostId: String
    ): PatchmonDeleteResponse
}
