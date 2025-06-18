package borg.trikeshed.net.quic

interface QuicSessionCache {
    fun getSession(serverAddress: String, port: Int): QuicSessionData?
    fun storeSession(serverAddress: String, port: Int, sessionData: QuicSessionData)
}

data class QuicSessionData(
    val sessionTicket: ByteArray,
    val expirationTime: Long,
    val transportParams: Map<String, Any> = emptyMap()
)