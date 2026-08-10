package com.homelab.app.data.remote

import com.homelab.app.data.repository.ServiceInstancesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Request

@Singleton
class TlsRoutingCallFactory @Inject constructor(
    private val tlsClientSelector: TlsClientSelector,
    private val serviceInstancesRepository: ServiceInstancesRepository
) : Call.Factory {

    override fun newCall(request: Request): Call {
        val explicitAllowSelfSigned = request.header(ALLOW_SELF_SIGNED_HEADER)?.toBooleanStrictOrNull()
        val instance = request.header(INSTANCE_ID_HEADER)?.let { instanceId ->
            runBlocking { serviceInstancesRepository.getInstance(instanceId) }
        }
        val mode = explicitAllowSelfSigned?.let { allow ->
            if (allow) com.homelab.app.domain.model.TlsMode.INSECURE_COMPATIBILITY
            else com.homelab.app.domain.model.TlsMode.SYSTEM
        } ?: instance?.effectiveTlsMode ?: com.homelab.app.domain.model.TlsMode.SYSTEM

        val sanitizedRequest = request.newBuilder()
            .removeHeader(ALLOW_SELF_SIGNED_HEADER)
            .build()

        val client = tlsClientSelector.forPolicy(
            mode = mode,
            hostname = request.url.host,
            customCaPem = instance?.customCaPem,
            certificatePin = instance?.certificatePin
        )
        return client.newCall(sanitizedRequest)
    }

    companion object {
        const val ALLOW_SELF_SIGNED_HEADER = "X-Homelab-Allow-Self-Signed"
        private const val INSTANCE_ID_HEADER = "X-Homelab-Instance-Id"
    }
}
