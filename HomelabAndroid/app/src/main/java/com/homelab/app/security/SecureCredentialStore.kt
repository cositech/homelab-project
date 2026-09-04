package com.homelab.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.homelab.app.domain.model.Tenant
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface SecureCredentialStore {
    fun put(reference: String, envelope: CredentialEnvelope): Boolean
    fun get(reference: String): CredentialEnvelope?
    fun delete(reference: String): Boolean

    /**
     * A fresh, tenant-namespaced reference. The reference is an opaque lookup key (the encryption
     * key material is shared, as in Phase 1); namespacing it by [tenantRef] means a Keystore entry
     * is never addressed the same way across tenants, even for byte-identical secrets.
     */
    fun newReference(tenantRef: String = Tenant.DEFAULT_ID): String =
        "$CREDENTIAL_REF_V2_PREFIX${Tenant.refOrDefault(tenantRef)}:${UUID.randomUUID()}"

    companion object {
        /** Prefix of every reference minted by [newReference]. Pre-Phase-4 references used `credential:v1:`. */
        const val CREDENTIAL_REF_V2_PREFIX = "credential:v2:"
    }
}

@Singleton
class AndroidKeystoreCredentialStore @Inject constructor(
    @ApplicationContext context: Context
) : SecureCredentialStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    override fun put(reference: String, envelope: CredentialEnvelope): Boolean = synchronized(lock) {
        runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            cipher.updateAAD(reference.toByteArray(Charsets.UTF_8))
            val plaintext = json.encodeToString(envelope).toByteArray(Charsets.UTF_8)
            val ciphertext = cipher.doFinal(plaintext)
            val payload = ByteBuffer.allocate(1 + cipher.iv.size + ciphertext.size)
                .put(cipher.iv.size.toByte())
                .put(cipher.iv)
                .put(ciphertext)
                .array()
            preferences.edit()
                .putString(reference, Base64.encodeToString(payload, Base64.NO_WRAP))
                .commit()
        }.getOrDefault(false)
    }

    override fun get(reference: String): CredentialEnvelope? = synchronized(lock) {
        runCatching {
            val encoded = preferences.getString(reference, null) ?: return@synchronized null
            val buffer = ByteBuffer.wrap(Base64.decode(encoded, Base64.NO_WRAP))
            val ivLength = buffer.get().toInt() and 0xff
            require(ivLength in 12..16 && buffer.remaining() > ivLength)
            val iv = ByteArray(ivLength).also(buffer::get)
            val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            cipher.updateAAD(reference.toByteArray(Charsets.UTF_8))
            json.decodeFromString<CredentialEnvelope>(cipher.doFinal(ciphertext).toString(Charsets.UTF_8))
        }.getOrNull()
    }

    override fun delete(reference: String): Boolean = synchronized(lock) {
        preferences.edit().remove(reference).commit()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFERENCES_NAME = "secure_credentials_v1"
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "homelab.credentials.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
