@file:OptIn(ExperimentalUnsignedTypes::class)
package borg.trikeshed.net.quic


/**
 * Default implementation of QuicSessionCache
 */
class DefaultQuicSessionCache : QuicSessionCache {
    private val sessions = mutableMapOf<String, QuicSessionData>()
    
    override fun getSession(serverAddress: String, port: Int): QuicSessionData? {
        val key = "$serverAddress:$port"
        return sessions[key]
    }
    
    override fun storeSession(serverAddress: String, port: Int, sessionData: QuicSessionData) {
        val key = "$serverAddress:$port"
        sessions[key] = sessionData
    }
    
    override fun clearSession(serverAddress: String, port: Int) {
        val key = "$serverAddress:$port"
        sessions.remove(key)
    }
}