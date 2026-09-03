package com.homelab.app.data.remote.api

import kotlinx.serialization.json.JsonElement
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface DockhandApi {

    @GET("api/dashboard/stats")
    suspend fun getDashboardStats(
        @Header("X-Homelab-Service") service: String = "Dockhand",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Query("env") env: String? = null
    ): JsonElement

    @GET("api/environments")
    suspend fun getEnvironments(
        @Header("X-Homelab-Service") service: String = "Dockhand",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonElement

    @GET("api/containers")
    suspend fun getContainers(
        @Header("X-Homelab-Service") service: String = "Dockhand",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Query("env") env: String? = null
    ): JsonElement

    @GET("api/containers/{id}")
    suspend fun getContainerDetail(
        @Path("id") containerId: String,
        @Header("X-Homelab-Service") service: String = "Dockhand",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Query("env") env: String? = null
    ): JsonElement

    @GET("api/containers/{id}/logs")
    suspend fun getContainerLogs(
        @Path("id") containerId: String,
        @Header("X-Homelab-Service") service: String = "Dockhand",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Query("env") env: String? = null,
        @Query("tail") tail: Int = 140
    ): ResponseBody

    @POST("api/containers/{id}/start")
    suspend fun startContainer(
        @Path("id") containerId: String,
        @Header("Accept") accept: String = "application/json",
        @Header("X-Homelab-No-Fallback") noFallback: String = "true",
        @Header("X-Homelab-Service") service: String = "Dockhand",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Query("env") env: String? = null
    ): JsonElement

    @POST("api/containers/{id}/stop")
    suspend fun stopContainer(
        @Path("id") containerId: String,
        @Header("Accept") accept: String = "application/json",
        @Header("X-Homelab-No-Fallback") noFallback: String = "true",
        @Header("X-Homelab-Service") service: String = "Dockhand",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Query("env") env: String? = null
    ): JsonElement

    @POST("api/containers/{id}/restart")
    suspend fun restartContainer(
        @Path("id") containerId: String,
        @Header("Accept") accept: String = "application/json",
        @Header("X-Homelab-No-Fallback") noFallback: String = "true",
        @Header("X-Homelab-Service") service: String = "Dockhand",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Query("env") env: String? = null
    ): JsonElement

    @GET("api/stacks")
    suspend fun getStacks(
        @Header("X-Homelab-Service") service: String = "Dockhand",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Query("env") env: String? = null
    ): JsonElement

    @GET("api/stacks/{name}")
    suspend fun getStackDetail(
        @Path("name", encoded = true) stackName: String,
        @Header("X-Homelab-Service") service: String = "Dockhand",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Query("env") env: String? = null
    ): JsonElement

    @GET("api/stacks/{name}/compose")
    suspend fun getStackCompose(
        @Path("name", encoded = true) stackName: String,
        @Header("X-Homelab-Service") service: String = "Dockhand",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Query("env") env: String? = null
    ): ResponseBody

    @POST("api/stacks/{name}/start")
    suspend fun startStack(
        @Path("name", encoded = true) stackName: String,
        @Header("Accept") accept: String = "application/json",
        @Header("X-Homelab-No-Fallback") noFallback: String = "true",
        @Header("X-Homelab-Service") service: String = "Dockhand",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Query("env") env: String? = null
    ): JsonElement

    @POST("api/stacks/{name}/stop")
    suspend fun stopStack(
        @Path("name", encoded = true) stackName: String,
        @Header("Accept") accept: String = "application/json",
        @Header("X-Homelab-No-Fallback") noFallback: String = "true",
        @Header("X-Homelab-Service") service: String = "Dockhand",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Query("env") env: String? = null
    ): JsonElement

    @POST("api/stacks/{name}/restart")
    suspend fun restartStack(
        @Path("name", encoded = true) stackName: String,
        @Header("Accept") accept: String = "application/json",
        @Header("X-Homelab-No-Fallback") noFallback: String = "true",
        @Header("X-Homelab-Service") service: String = "Dockhand",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Query("env") env: String? = null
    ): JsonElement

    @GET("api/images")
    suspend fun getImages(
        @Header("X-Homelab-Service") service: String = "Dockhand",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Query("env") env: String? = null
    ): JsonElement

    @GET("api/volumes")
    suspend fun getVolumes(
        @Header("X-Homelab-Service") service: String = "Dockhand",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Query("env") env: String? = null
    ): JsonElement

    @GET("api/networks")
    suspend fun getNetworks(
        @Header("X-Homelab-Service") service: String = "Dockhand",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Query("env") env: String? = null
    ): JsonElement

    @GET("api/activity")
    suspend fun getActivity(
        @Header("X-Homelab-Service") service: String = "Dockhand",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Query("env") env: String? = null
    ): JsonElement

    @GET("api/schedules")
    suspend fun getSchedules(
        @Header("X-Homelab-Service") service: String = "Dockhand",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Query("env") env: String? = null
    ): JsonElement

    @GET("api/schedules/{id}")
    suspend fun getScheduleDetail(
        @Path("id", encoded = true) scheduleId: String,
        @Header("X-Homelab-Service") service: String = "Dockhand",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Query("env") env: String? = null
    ): JsonElement

    @GET("api/jobs/{jobId}")
    suspend fun getJobStatus(
        @Path("jobId") jobId: String,
        @Header("X-Homelab-Service") service: String = "Dockhand",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonElement
}
