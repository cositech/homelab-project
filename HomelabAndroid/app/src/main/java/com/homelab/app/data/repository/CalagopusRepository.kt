package com.homelab.app.data.repository

import com.homelab.app.data.remote.TlsClientSelector
import com.homelab.app.data.remote.api.CalagopusApi
import com.homelab.app.data.remote.dto.calagopus.CalagopusPowerRequest
import com.homelab.app.data.remote.dto.calagopus.CalagopusResources
import com.homelab.app.data.remote.dto.calagopus.CalagopusServer
import com.homelab.app.domain.action.ActionRisk
import com.homelab.app.domain.action.ControlledActionRequest
import java.time.Instant
import java.util.Locale
import java.util.UUID
import kotlinx.serialization.SerializationException
import okhttp3.Request
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class CalagopusPowerAction(val signal: String, val risk: ActionRisk) {
    START("start", ActionRisk.LOW),
    STOP("stop", ActionRisk.MEDIUM),
    RESTART("restart", ActionRisk.MEDIUM),
    KILL("kill", ActionRisk.HIGH);

    val requiresConfirmation: Boolean get() = risk != ActionRisk.LOW

    fun controlledRequest(
        instanceId: String,
        uuidShort: String,
        confirmed: Boolean,
        requestId: String = UUID.randomUUID().toString(),
        requestedAt: String = Instant.now().toString(),
        idempotencyKey: String = UUID.randomUUID().toString()
    ) = ControlledActionRequest(
        id = requestId,
        providerRef = "calagopus:${instanceId.trim().lowercase(Locale.ROOT)}",
        action = "server.power.$signal",
        targetRef = "server/${uuidShort.trim().lowercase(Locale.ROOT)}",
        risk = risk,
        requestedAt = requestedAt,
        idempotencyKey = idempotencyKey,
        confirmed = confirmed
    )
}

class CalagopusApiException(
    val kind: Kind,
    override val cause: Throwable? = null
) : Exception(kind.name, cause) {
    enum class Kind {
        INVALID_CREDENTIALS,
        SERVER_ERROR,
        CONNECTION_ERROR
    }
}

@Singleton
class CalagopusRepository @Inject constructor(
    private val api: CalagopusApi,
    private val tlsClientSelector: TlsClientSelector
) {

    suspend fun authenticate(
        url: String,
        apiKey: String,
        fallbackUrl: String? = null,
        allowSelfSigned: Boolean = false
    ) {
        return withContext(Dispatchers.IO) {
            val candidates = listOf(url.trim().trimEnd('/'), fallbackUrl?.trim()?.trimEnd('/'))
                .filterNotNull()
                .filter { it.isNotBlank() }
                .distinct()

            var lastError: CalagopusApiException? = null
            for (baseUrl in candidates) {
                try {
                    authenticateAgainst(baseUrl, apiKey, allowSelfSigned)
                    return@withContext
                } catch (e: CalagopusApiException) {
                    lastError = e
                    if (e.kind == CalagopusApiException.Kind.INVALID_CREDENTIALS) throw e
                }
            }
            throw lastError ?: CalagopusApiException(CalagopusApiException.Kind.CONNECTION_ERROR)
        }
    }

    suspend fun getServers(instanceId: String): List<CalagopusServer> {
        return try {
            api.getServers(instanceId).servers.data
        } catch (e: Exception) {
            throw handleException(e)
        }
    }

    suspend fun getServerResources(instanceId: String, uuidShort: String): CalagopusResources {
        return try {
            api.getServerResources(instanceId, uuidShort).resources
        } catch (e: Exception) {
            throw handleException(e)
        }
    }

    suspend fun sendPowerSignal(instanceId: String, uuidShort: String, action: CalagopusPowerAction) {
        try {
            api.sendPowerSignal(instanceId, uuidShort, CalagopusPowerRequest(action.signal))
        } catch (error: CancellationException) {
            throw error
        } catch (e: Exception) {
            throw handleException(e)
        }
    }

    private fun authenticateAgainst(baseUrl: String, apiKey: String, allowSelfSigned: Boolean) {
        val request = Request.Builder()
            .url("$baseUrl/api/client/servers")
            .get()
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Accept", "application/json")
            .build()

        try {
            tlsClientSelector.forAllowSelfSigned(allowSelfSigned).newCall(request).execute().use { response ->
                when {
                    response.code == 401 || response.code == 403 ->
                        throw CalagopusApiException(CalagopusApiException.Kind.INVALID_CREDENTIALS)
                    !response.isSuccessful ->
                        throw CalagopusApiException(CalagopusApiException.Kind.SERVER_ERROR)
                }
            }
        } catch (e: IOException) {
            throw CalagopusApiException(CalagopusApiException.Kind.CONNECTION_ERROR, e)
        } catch (e: CalagopusApiException) {
            throw e
        } catch (e: Exception) {
            throw CalagopusApiException(CalagopusApiException.Kind.CONNECTION_ERROR, e)
        }
    }

    private fun handleException(e: Throwable): CalagopusApiException {
        return when (e) {
            is HttpException -> when (e.code()) {
                401, 403 -> CalagopusApiException(CalagopusApiException.Kind.INVALID_CREDENTIALS, e)
                else -> CalagopusApiException(CalagopusApiException.Kind.SERVER_ERROR, e)
            }
            is SerializationException -> CalagopusApiException(CalagopusApiException.Kind.SERVER_ERROR, e)
            is IOException -> CalagopusApiException(CalagopusApiException.Kind.CONNECTION_ERROR, e)
            is CalagopusApiException -> e
            else -> CalagopusApiException(CalagopusApiException.Kind.SERVER_ERROR, e)
        }
    }
}
