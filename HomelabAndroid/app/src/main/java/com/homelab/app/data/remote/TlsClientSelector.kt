package com.homelab.app.data.remote

import com.homelab.app.data.repository.ServiceInstancesRepository
import com.homelab.app.domain.model.TlsMode
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Singleton
class TlsClientSelector @Inject constructor(
    private val serviceInstancesRepository: ServiceInstancesRepository,
    private val secureClient: OkHttpClient,
    @param:Named("insecure") private val insecureClient: OkHttpClient
) {
    private val policyClients = ConcurrentHashMap<String, OkHttpClient>()

    fun forAllowSelfSigned(allowSelfSigned: Boolean): OkHttpClient {
        return if (allowSelfSigned) insecureClient else secureClient
    }

    suspend fun forInstance(instanceId: String): OkHttpClient {
        val instance = serviceInstancesRepository.getInstance(instanceId) ?: return secureClient
        return forPolicy(
            mode = instance.effectiveTlsMode,
            hostname = instance.url.toHttpUrlOrNull()?.host,
            customCaPem = instance.customCaPem,
            certificatePin = instance.certificatePin
        )
    }

    fun forPolicy(
        mode: TlsMode,
        hostname: String?,
        customCaPem: String? = null,
        certificatePin: String? = null
    ): OkHttpClient = when (mode) {
        TlsMode.SYSTEM -> secureClient
        TlsMode.INSECURE_COMPATIBILITY -> insecureClient
        TlsMode.CUSTOM_CA -> {
            val pem = requireNotNull(customCaPem?.takeIf { it.isNotBlank() }) {
                "CUSTOM_CA requires a configured CA certificate"
            }
            policyClients.getOrPut("ca:${pem.hashCode()}") { customCaClient(pem) }
        }
        TlsMode.CERTIFICATE_PIN -> {
            val host = requireNotNull(hostname?.takeIf { it.isNotBlank() }) {
                "CERTIFICATE_PIN requires a request hostname"
            }
            val pin = requireNotNull(certificatePin?.takeIf { it.startsWith("sha256/") }) {
                "CERTIFICATE_PIN requires a sha256/ pin"
            }
            policyClients.getOrPut("pin:$host:$pin") {
                secureClient.newBuilder()
                    .certificatePinner(CertificatePinner.Builder().add(host, pin).build())
                    .build()
            }
        }
    }

    private fun customCaClient(pem: String): OkHttpClient {
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(pem.toByteArray(Charsets.UTF_8)))
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null)
            setCertificateEntry("homelab-custom-ca", certificate)
        }
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore)
        }
        val trustManager = trustManagerFactory.trustManagers
            .filterIsInstance<X509TrustManager>()
            .single()
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustManager), null)
        }
        return secureClient.newBuilder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .build()
    }
}
