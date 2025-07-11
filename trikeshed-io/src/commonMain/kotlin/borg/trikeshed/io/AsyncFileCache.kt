@file:OptIn(kotlin.ExperimentalStdlibApi::class, kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.io

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.Clock

/**
 * Cache entry for file content
 */
data class CacheEntry(
    val content: ByteArray,
    val timestamp: Long,
    val ttl: Duration
) {
    fun isExpired(): Boolean = Clock.System.now().toEpochMilliseconds() - timestamp > ttl.inWholeMilliseconds
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CacheEntry) return false
        other as CacheEntry
        return content.contentEquals(other.content) && timestamp == other.timestamp && ttl == other.ttl
    }
    
    override fun hashCode(): Int {
        var result = content.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + ttl.hashCode()
        return result
    }
}

/**
 * Async file cache with TTL support
 */
class AsyncFileCache(
    private val defaultTtl: Duration = 5.minutes,
    private val maxSize: Int = 100
) {
    private val mutex = Mutex()
    private val cache = mutableMapOf<String, CacheEntry>()
    
    suspend fun get(path: String): ByteArray? = mutex.withLock {
        val entry = cache[path] ?: return@withLock null
        if (entry.isExpired()) {
            cache.remove(path)
            return@withLock null
        }
        entry.content
    }
    
    suspend fun put(path: String, content: ByteArray, ttl: Duration = defaultTtl) = mutex.withLock {
        if (cache.size >= maxSize) {
            // Remove oldest entry
            val oldest = cache.minByOrNull { it.value.timestamp }
            oldest?.let { cache.remove(it.key) }
        }
        
        cache[path] = CacheEntry(content, Clock.System.now().toEpochMilliseconds(), ttl)
    }
    
    suspend fun invalidate(path: String) = mutex.withLock {
        cache.remove(path)
    }
    
    suspend fun clear() = mutex.withLock {
        cache.clear()
    }
    
    suspend fun cleanup() = mutex.withLock {
        val expired = cache.entries.filter { it.value.isExpired() }.map { it.key }
        expired.forEach { cache.remove(it) }
    }
    
    companion object {
        val instance = AsyncFileCache()
    }
} 