package com.homelab.app.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import com.homelab.app.data.local.SettingsManager
import com.homelab.app.data.repository.ServiceInstancesRepository
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.util.ServiceType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

class SmartFallbackInterceptorTest {

    @Test
    fun `rewrites request using instance id even when service header is missing`() {
        val context = mockk<Context>(relaxed = true)
        val settingsManager = mockk<SettingsManager>()
        val instancesRepository = mockk<ServiceInstancesRepository>()
        val interceptor = SmartFallbackInterceptor(context, settingsManager, instancesRepository)
        val chain = mockk<Interceptor.Chain>()
        val capturedRequest = slot<Request>()
        val request = Request.Builder()
            .url("https://placeholder.local/api/system")
            .header("X-Homelab-Instance-Id", "instance-9")
            .build()

        every { settingsManager.internalSsid } returns flowOf(null)
        coEvery { instancesRepository.getInstance("instance-9") } returns ServiceInstance(
            id = "instance-9",
            type = ServiceType.BESZEL,
            label = "Lab",
            url = "https://beszel.lab.local",
            token = "token"
        )
        every { chain.request() } returns request
        every { chain.proceed(capture(capturedRequest)) } answers {
            response(capturedRequest.captured)
        }

        interceptor.intercept(chain)

        assertEquals("beszel.lab.local", capturedRequest.captured.url.host)
        assertEquals("https", capturedRequest.captured.url.scheme)
        assertEquals("/api/system", capturedRequest.captured.url.encodedPath)
    }

    @Test
    fun `preserves fallback base path when routing external requests`() {
        val context = mockk<Context>(relaxed = true)
        val settingsManager = mockk<SettingsManager>()
        val instancesRepository = mockk<ServiceInstancesRepository>()
        val interceptor = SmartFallbackInterceptor(context, settingsManager, instancesRepository)
        val chain = mockk<Interceptor.Chain>()
        val capturedRequest = slot<Request>()
        val request = Request.Builder()
            .url("https://placeholder.local/api/v3/system/status")
            .header("X-Homelab-Instance-Id", "instance-servarr")
            .build()

        every { settingsManager.internalSsid } returns flowOf(null)
        coEvery { instancesRepository.getInstance("instance-servarr") } returns ServiceInstance(
            id = "instance-servarr",
            type = ServiceType.SONARR,
            label = "Sonarr",
            url = "http://192.168.1.20:8989",
            fallbackUrl = "https://example.com/sonarr",
            apiKey = "token"
        )
        every { chain.request() } returns request
        every { chain.proceed(capture(capturedRequest)) } answers {
            response(capturedRequest.captured)
        }

        interceptor.intercept(chain)

        assertEquals("example.com", capturedRequest.captured.url.host)
        assertEquals("/sonarr/api/v3/system/status", capturedRequest.captured.url.encodedPath)
    }

    @Test
    fun `no fallback header prevents mutation replay and is not sent upstream`() {
        val context = mockk<Context>(relaxed = true)
        val connectivityManager = mockk<ConnectivityManager>()
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()
        val wifiInfo = mockk<WifiInfo>()
        val settingsManager = mockk<SettingsManager>()
        val instancesRepository = mockk<ServiceInstancesRepository>()
        val interceptor = SmartFallbackInterceptor(context, settingsManager, instancesRepository)
        val chain = mockk<Interceptor.Chain>()
        val capturedRequest = slot<Request>()
        val request = Request.Builder()
            .url("https://placeholder.local/api/domains/allow/exact")
            .header("X-Homelab-Instance-Id", "instance-pihole")
            .header("X-Homelab-No-Fallback", "true")
            .build()

        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        every { capabilities.transportInfo } returns wifiInfo
        every { wifiInfo.ssid } returns "\"Lab\""
        every { settingsManager.internalSsid } returns flowOf("Lab")
        coEvery { instancesRepository.getInstance("instance-pihole") } returns ServiceInstance(
            id = "instance-pihole",
            type = ServiceType.PIHOLE,
            label = "Pi-hole",
            url = "https://pihole.internal",
            fallbackUrl = "https://pihole.external",
            apiKey = "token"
        )
        every { chain.request() } returns request
        every { chain.proceed(capture(capturedRequest)) } answers {
            response(capturedRequest.captured, 503)
        }

        val result = interceptor.intercept(chain)

        assertEquals(503, result.code)
        assertEquals("pihole.internal", capturedRequest.captured.url.host)
        assertEquals(null, capturedRequest.captured.header("X-Homelab-No-Fallback"))
        verify(exactly = 1) { chain.proceed(any()) }
    }

    private fun response(request: Request, code: Int = 200): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code == 200) "OK" else "Unavailable")
            .body("{}".toResponseBody("application/json".toMediaType()))
            .build()
    }
}
