package borg.trikeshed.net.quic
@file:OptIn(ExperimentalUnsignedTypes::class)


interface QuicSessionCache {
    fun getSession(serverAddress: String, port: Int): QuicSessionData?
    fun storeSession(serverAddress: String, port: Int, sessionData: QuicSessionData)
    fun clearSession(serverAddress: String, port: Int) // Added for 0-RTT fallback
}

/**
 * Represents cached session data for 0-RTT connections.
 * This canonical definition combines fields from previous duplicates.
 */
data class QuicSessionData(
    val serverAddress: String,
    val port: Int,
    val sessionId: ByteArray,
    val ticket: ByteArray,
    val expirationTime: Long,
    val transportParams: Map<String, Any> = emptyMap() // Keep existing transportParams
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as QuicSessionData

        if (serverAddress != other.serverAddress) return false
        if (port != other.port) return false
        if (!sessionId.contentEquals(other.sessionId)) return false
        if (!ticket.contentEquals(other.ticket)) return false
        if (expirationTime != other.expirationTime) return false
        if (transportParams != other.transportParams) return false

        return true
    }

    override fun hashCode(): Int {
        var result = serverAddress.hashCode()
        result = 31 * result + port
        result = 31 * result + sessionId.contentHashCode()
        result = 31 * result + ticket.contentHashCode()
        result = 31 * result + expirationTime.hashCode()
        result = 31 * result + transportParams.hashCode()
        return result
    }
}