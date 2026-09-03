package com.homelab.app.data.remote.api

import com.homelab.app.data.remote.dto.crafty.CraftyActionResponse
import com.homelab.app.data.remote.dto.crafty.CraftyResponse
import com.homelab.app.data.remote.dto.crafty.CraftyServer
import com.homelab.app.data.remote.dto.crafty.CraftyServerStats
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface CraftyApi {
    @GET("api/v2/servers")
    suspend fun getServers(
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): CraftyResponse<List<CraftyServer>>

    @GET("api/v2/servers/{serverId}/stats")
    suspend fun getServerStats(
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("serverId") serverId: String
    ): CraftyResponse<CraftyServerStats>

    @GET("api/v2/servers/{serverId}/logs")
    suspend fun getServerLogs(
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("serverId") serverId: String,
        @Query("file") file: Boolean = false,
        @Query("raw") raw: Boolean = false
    ): CraftyResponse<List<String>>

    @POST("api/v2/servers/{serverId}/action/{action}")
    suspend fun sendAction(
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Header("X-Homelab-No-Fallback") noFallback: Boolean = true,
        @Path("serverId") serverId: String,
        @Path("action") action: String
    ): CraftyActionResponse

    @Headers("Content-Type: text/plain")
    @POST("api/v2/servers/{serverId}/stdin")
    suspend fun sendCommand(
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Header("X-Homelab-No-Fallback") noFallback: Boolean = true,
        @Path("serverId") serverId: String,
        @Body command: RequestBody
    ): CraftyActionResponse
}
