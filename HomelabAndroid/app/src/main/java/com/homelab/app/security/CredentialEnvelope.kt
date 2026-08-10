package com.homelab.app.security

import com.homelab.app.domain.model.ServiceInstance
import kotlinx.serialization.Serializable

@Serializable
data class CredentialEnvelope(
    val token: String? = null,
    val proxmoxCsrfToken: String? = null,
    val proxmoxOtp: String? = null,
    val apiKey: String? = null,
    val piholePassword: String? = null,
    val password: String? = null,
    val customCaPem: String? = null
) {
    val isEmpty: Boolean
        get() = token == null &&
            proxmoxCsrfToken == null &&
            proxmoxOtp == null &&
            apiKey == null &&
            piholePassword == null &&
            password == null &&
            customCaPem == null

    companion object {
        fun from(instance: ServiceInstance): CredentialEnvelope = CredentialEnvelope(
            token = instance.token.secretOrNull(),
            proxmoxCsrfToken = instance.proxmoxCsrfToken.secretOrNull(),
            proxmoxOtp = instance.proxmoxOtp.secretOrNull(),
            apiKey = instance.apiKey.secretOrNull(),
            piholePassword = instance.piholePassword.secretOrNull(),
            password = instance.password.secretOrNull(),
            customCaPem = instance.customCaPem.secretOrNull()
        )
    }
}

private fun String?.secretOrNull(): String? = this?.takeIf { it.isNotEmpty() }
