package borg.trikeshed.net.quic

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory implementation of [QuicSessionCache].
 * This cache is thread-safe.
 */
class InMemoryQuicSessionCache : QuicSessionCache {

    private val cache = ConcurrentHashMap<String, QuicSessionData>()

    override fun getSession(serverAddress: String, port: Int): QuicSessionData? {
        val key = generateCacheKey(serverAddress, port)
        val sessionData = cache[key]
        // Check if the session ticket has expired
        if (sessionData != null && System.currentTimeMillis() > sessionData.expirationTime) {
            cache.remove(key) // Remove expired ticket
            return null
        }
        return sessionData
    }

    override fun storeSession(serverAddress: String, port: Int, sessionData: QuicSessionData) {
        val key = generateCacheKey(serverAddress, port)
        cache[key] = sessionData
    }

    private fun generateCacheKey(serverAddress: String, port: Int): String {
        return "$serverAddress:$port"
    }
}
