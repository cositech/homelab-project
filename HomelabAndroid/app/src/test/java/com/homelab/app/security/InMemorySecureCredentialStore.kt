package com.homelab.app.security

class InMemorySecureCredentialStore : SecureCredentialStore {
    private val storage = linkedMapOf<String, CredentialEnvelope>()
    private var sequence = 0

    override fun put(reference: String, envelope: CredentialEnvelope): Boolean {
        storage[reference] = envelope
        return true
    }

    override fun get(reference: String): CredentialEnvelope? = storage[reference]

    override fun delete(reference: String): Boolean = storage.remove(reference) != null

    override fun newReference(instanceId: String): String = "credential:test:$instanceId:${sequence++}"

    fun contains(reference: String): Boolean = storage.containsKey(reference)
}
