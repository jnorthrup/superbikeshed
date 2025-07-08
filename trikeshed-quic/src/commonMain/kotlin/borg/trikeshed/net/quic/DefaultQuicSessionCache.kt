@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.net.quic


/**
 * Default implementation of QuicSessionCache
 */
class DefaultQuicSessionCache : QuicSessionCache {
    internal val sessions = mutableMapOf<String, QuicSessionData>()
    
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
    
    // Additional methods for compatibility with QuicClient
    fun get(host: String, port: Int): QuicSessionData? = getSession(host, port)
    
    fun put(host: String, port: Int, session: QuicSessionData) = storeSession(host, port, session)
}