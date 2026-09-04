package com.homelab.app.security

class InMemorySecureCredentialStore : SecureCredentialStore {
    private val storage = linkedMapOf<String, CredentialEnvelope>()
    private var sequence = 0

    override fun put(reference: String, envelope: CredentialEnvelope): Boolean {
        if (failNextPut) {
            failNextPut = false
            return false
        }
        storage[reference] = envelope
        return true
    }

    override fun get(reference: String): CredentialEnvelope? = storage[reference]

    override fun delete(reference: String): Boolean = storage.remove(reference) != null

    override fun newReference(tenantRef: String): String = "credential:v2:$tenantRef:${sequence++}"

    fun contains(reference: String): Boolean = storage.containsKey(reference)

    /** Simulates a Keystore write that silently fails to verify, to exercise fail-closed migration. */
    var failNextPut: Boolean = false
}
