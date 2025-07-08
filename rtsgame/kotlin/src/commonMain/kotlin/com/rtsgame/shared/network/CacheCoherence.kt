package com.rtsgame.shared.network

import kotlin.math.*

/**
 * Cache Coherence System - Absorbed from JavaScript gem
 * 
 * Key Features:
 * - MESI protocol implementation (Modified, Exclusive, Shared, Invalid)
 * - Cache line management with L1/L2/L3 simulation
 * - Cross-entity cache consistency validation
 * - Deterministic cache behavior
 */
class CacheCoherence {
    
    companion object {
        // Cache hierarchy constants
        const val L1_CACHE_LATENCY_NS = 1
        const val L2_CACHE_LATENCY_NS = 10
        const val L3_CACHE_LATENCY_NS = 50
        const val MAIN_MEMORY_LATENCY_NS = 100
        const val REMOTE_MEMORY_LATENCY_NS = 300
        
        // Cache sizes
        const val L1_CACHE_SIZE_KB = 64
        const val L2_CACHE_SIZE_KB = 256
        const val L3_CACHE_SIZE_KB = 8192 // 8MB
        
        // Cache line size
        const val CACHE_LINE_SIZE_BYTES = 64
    }
    
    // MESI cache states
    enum class CacheState {
        MODIFIED,   // Exclusive, dirty
        EXCLUSIVE,  // Exclusive, clean
        SHARED,     // Shared, clean
        INVALID     // Invalid
    }
    
    // Cache line
    data class CacheLine(
        val address: Long,
        var state: CacheState,
        var data: ByteArray,
        var lastAccess: Long,
        var accessCount: Int = 0,
        val ownerId: Long? = null
    ) {
        fun transitionTo(newState: CacheState) {
            state = newState
            lastAccess = System.nanoTime()
        }
        
        fun incrementAccess() {
            accessCount++
            lastAccess = System.nanoTime()
        }
        
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            return address == (other as CacheLine).address
        }
        
        override fun hashCode(): Int {
            return address.hashCode()
        }
    }
    
    // Cache level
    enum class CacheLevel(val latency: Int, val sizeKB: Int) {
        L1(L1_CACHE_LATENCY_NS, L1_CACHE_SIZE_KB),
        L2(L2_CACHE_LATENCY_NS, L2_CACHE_SIZE_KB),
        L3(L3_CACHE_LATENCY_NS, L3_CACHE_SIZE_KB)
    }
    
    // Cache hierarchy
    data class CacheHierarchy(
        val entityId: Long,
        val l1Cache: MutableMap<Long, CacheLine> = mutableMapOf(),
        val l2Cache: MutableMap<Long, CacheLine> = mutableMapOf(),
        val l3Cache: MutableMap<Long, CacheLine> = mutableMapOf(),
        val cacheStats: CacheStats = CacheStats()
    ) {
        fun getCacheLine(address: Long): CacheLine? {
            return l1Cache[address] ?: l2Cache[address] ?: l3Cache[address]
        }
        
        fun addCacheLine(address: Long, cacheLine: CacheLine, level: CacheLevel) {
            when (level) {
                CacheLevel.L1 -> l1Cache[address] = cacheLine
                CacheLevel.L2 -> l2Cache[address] = cacheLine
                CacheLevel.L3 -> l3Cache[address] = cacheLine
            }
        }
        
        fun removeCacheLine(address: Long, level: CacheLevel) {
            when (level) {
                CacheLevel.L1 -> l1Cache.remove(address)
                CacheLevel.L2 -> l2Cache.remove(address)
                CacheLevel.L3 -> l3Cache.remove(address)
            }
        }
        
        fun evictLRU(level: CacheLevel): CacheLine? {
            val cache = when (level) {
                CacheLevel.L1 -> l1Cache
                CacheLevel.L2 -> l2Cache
                CacheLevel.L3 -> l3Cache
            }
            
            if (cache.size < level.sizeKB * 1024 / CACHE_LINE_SIZE_BYTES) {
                return null // Cache not full
            }
            
            // Find least recently used cache line
            val lruEntry = cache.minByOrNull { it.value.lastAccess }
            return lruEntry?.value?.also { cache.remove(lruEntry.key) }
        }
    }
    
    // Cache statistics
    data class CacheStats(
        var hits: Int = 0,
        var misses: Int = 0,
        var evictions: Int = 0,
        var coherenceMessages: Int = 0
    ) {
        fun hitRatio(): Double {
            val total = hits + misses
            return if (total > 0) hits.toDouble() / total else 0.0
        }
    }
    
    // Coherence message
    data class CoherenceMessage(
        val id: Long,
        val sourceId: Long,
        val targetId: Long,
        val messageType: MessageType,
        val address: Long,
        val data: ByteArray? = null,
        val timestamp: Long = System.nanoTime()
    ) {
        enum class MessageType {
            READ_REQUEST,
            READ_RESPONSE,
            WRITE_REQUEST,
            WRITE_RESPONSE,
            INVALIDATE,
            INVALIDATE_ACK,
            SHARED_REQUEST,
            SHARED_RESPONSE
        }
    }
    
    // Cache coherence manager
    data class CacheCoherenceManager(
        val entityId: Long,
        val hierarchy: CacheHierarchy,
        val pendingMessages: MutableList<CoherenceMessage> = mutableListOf(),
        val messageQueue: MutableList<CoherenceMessage> = mutableListOf()
    ) {
        fun read(address: Long): ReadResult {
            val cacheLine = hierarchy.getCacheLine(address)
            
            if (cacheLine != null) {
                // Cache hit
                hierarchy.cacheStats.hits++
                cacheLine.incrementAccess()
                
                return when (cacheLine.state) {
                    CacheState.MODIFIED, CacheState.EXCLUSIVE, CacheState.SHARED -> {
                        ReadResult.Success(cacheLine.data, cacheLine.state.latency)
                    }
                    CacheState.INVALID -> {
                        // Invalid cache line, need to fetch
                        hierarchy.cacheStats.misses++
                        ReadResult.Miss(address, CacheLevel.L1.latency)
                    }
                }
            } else {
                // Cache miss
                hierarchy.cacheStats.misses++
                return ReadResult.Miss(address, CacheLevel.L1.latency)
            }
        }
        
        fun write(address: Long, data: ByteArray): WriteResult {
            val cacheLine = hierarchy.getCacheLine(address)
            
            if (cacheLine != null) {
                // Cache hit
                hierarchy.cacheStats.hits++
                cacheLine.incrementAccess()
                
                return when (cacheLine.state) {
                    CacheState.MODIFIED -> {
                        // Already exclusive and dirty, just update
                        cacheLine.data = data
                        WriteResult.Success(cacheLine.state.latency)
                    }
                    CacheState.EXCLUSIVE -> {
                        // Exclusive but clean, mark as modified
                        cacheLine.data = data
                        cacheLine.transitionTo(CacheState.MODIFIED)
                        WriteResult.Success(cacheLine.state.latency)
                    }
                    CacheState.SHARED -> {
                        // Shared, need to invalidate other copies
                        cacheLine.data = data
                        cacheLine.transitionTo(CacheState.MODIFIED)
                        val invalidateMessage = CoherenceMessage(
                            id = System.nanoTime(),
                            sourceId = entityId,
                            targetId = -1, // Broadcast
                            messageType = CoherenceMessage.MessageType.INVALIDATE,
                            address = address
                        )
                        messageQueue.add(invalidateMessage)
                        WriteResult.Success(cacheLine.state.latency)
                    }
                    CacheState.INVALID -> {
                        // Invalid, need to fetch and then write
                        hierarchy.cacheStats.misses++
                        WriteResult.Miss(address, CacheLevel.L1.latency)
                    }
                }
            } else {
                // Cache miss
                hierarchy.cacheStats.misses++
                return WriteResult.Miss(address, CacheLevel.L1.latency)
            }
        }
        
        fun handleCoherenceMessage(message: CoherenceMessage): List<CoherenceMessage> {
            hierarchy.cacheStats.coherenceMessages++
            val responses = mutableListOf<CoherenceMessage>()
            
            when (message.messageType) {
                CoherenceMessage.MessageType.READ_REQUEST -> {
                    val cacheLine = hierarchy.getCacheLine(message.address)
                    if (cacheLine != null && cacheLine.state == CacheState.MODIFIED) {
                        // Write back to memory
                        cacheLine.transitionTo(CacheState.SHARED)
                        responses.add(CoherenceMessage(
                            id = System.nanoTime(),
                            sourceId = entityId,
                            targetId = message.sourceId,
                            messageType = CoherenceMessage.MessageType.READ_RESPONSE,
                            address = message.address,
                            data = cacheLine.data
                        ))
                    }
                }
                CoherenceMessage.MessageType.INVALIDATE -> {
                    val cacheLine = hierarchy.getCacheLine(message.address)
                    if (cacheLine != null) {
                        // Invalidate cache line
                        hierarchy.l1Cache.remove(message.address)
                        hierarchy.l2Cache.remove(message.address)
                        hierarchy.l3Cache.remove(message.address)
                        
                        responses.add(CoherenceMessage(
                            id = System.nanoTime(),
                            sourceId = entityId,
                            targetId = message.sourceId,
                            messageType = CoherenceMessage.MessageType.INVALIDATE_ACK,
                            address = message.address
                        ))
                    }
                }
                CoherenceMessage.MessageType.SHARED_REQUEST -> {
                    val cacheLine = hierarchy.getCacheLine(message.address)
                    if (cacheLine != null && cacheLine.state == CacheState.EXCLUSIVE) {
                        // Transition to shared
                        cacheLine.transitionTo(CacheState.SHARED)
                    }
                }
                else -> {
                    // Handle other message types
                }
            }
            
            return responses
        }
        
        fun update(deltaTime: Double) {
            // Process message queue
            val messagesToProcess = messageQueue.toList()
            messageQueue.clear()
            
            messagesToProcess.forEach { message ->
                val responses = handleCoherenceMessage(message)
                pendingMessages.addAll(responses)
            }
            
            // Update cache statistics
            updateCacheStats()
        }
        
        internal fun updateCacheStats() {
            // Update hit/miss ratios and other metrics
            val totalAccesses = hierarchy.cacheStats.hits + hierarchy.cacheStats.misses
            if (totalAccesses > 1000) {
                // Reset stats periodically
                hierarchy.cacheStats.hits = 0
                hierarchy.cacheStats.misses = 0
            }
        }
    }
    
    // Read result
    sealed class ReadResult {
        data class Success(val data: ByteArray, val latency: Int) : ReadResult()
        data class Miss(val address: Long, val latency: Int) : ReadResult()
    }
    
    // Write result
    sealed class WriteResult {
        data class Success(val latency: Int) : WriteResult()
        data class Miss(val address: Long, val latency: Int) : WriteResult()
    }
    
    /**
     * Calculate cache latency for a given level
     */
    fun calculateCacheLatency(level: CacheLevel): Int {
        return level.latency
    }
    
    /**
     * Calculate memory latency based on cache miss
     */
    fun calculateMemoryLatency(cacheLevel: CacheLevel): Int {
        return when (cacheLevel) {
            CacheLevel.L1 -> L2_CACHE_LATENCY_NS
            CacheLevel.L2 -> L3_CACHE_LATENCY_NS
            CacheLevel.L3 -> MAIN_MEMORY_LATENCY_NS
        }
    }
    
    /**
     * Check if cache line is valid
     */
    fun isCacheLineValid(cacheLine: CacheLine?): Boolean {
        return cacheLine?.state != CacheState.INVALID
    }
    
    /**
     * Get cache line state
     */
    fun getCacheLineState(cacheLine: CacheLine?): CacheState {
        return cacheLine?.state ?: CacheState.INVALID
    }
    
    /**
     * Calculate cache efficiency
     */
    fun calculateCacheEfficiency(stats: CacheStats): Double {
        return stats.hitRatio()
    }
    
    /**
     * Simulate cache coherence protocol
     */
    fun simulateCoherenceProtocol(
        managers: List<CacheCoherenceManager>,
        deltaTime: Double
    ) {
        // Update all cache managers
        managers.forEach { manager ->
            manager.update(deltaTime)
        }
        
        // Process coherence messages between managers
        managers.forEach { sourceManager ->
            val messagesToSend = sourceManager.pendingMessages.toList()
            sourceManager.pendingMessages.clear()
            
            messagesToSend.forEach { message ->
                if (message.targetId == -1L) {
                    // Broadcast message
                    managers.forEach { targetManager ->
                        if (targetManager.entityId != sourceManager.entityId) {
                            targetManager.handleCoherenceMessage(message)
                        }
                    }
                } else {
                    // Direct message
                    val targetManager = managers.find { it.entityId == message.targetId }
                    targetManager?.handleCoherenceMessage(message)
                }
            }
        }
    }
    
    /**
     * Validate cache coherence across all entities
     */
    fun validateCoherence(managers: List<CacheCoherenceManager>): Boolean {
        val addressStates = mutableMapOf<Long, MutableSet<CacheState>>()
        
        // Collect all cache line states for each address
        managers.forEach { manager ->
            manager.hierarchy.l1Cache.forEach { (address, cacheLine) ->
                addressStates.getOrPut(address) { mutableSetOf() }.add(cacheLine.state)
            }
            manager.hierarchy.l2Cache.forEach { (address, cacheLine) ->
                addressStates.getOrPut(address) { mutableSetOf() }.add(cacheLine.state)
            }
            manager.hierarchy.l3Cache.forEach { (address, cacheLine) ->
                addressStates.getOrPut(address) { mutableSetOf() }.add(cacheLine.state)
            }
        }
        
        // Validate MESI protocol rules
        addressStates.forEach { (address, states) ->
            val modifiedCount = states.count { it == CacheState.MODIFIED }
            val exclusiveCount = states.count { it == CacheState.EXCLUSIVE }
            
            // Only one cache line can be in MODIFIED state
            if (modifiedCount > 1) return false
            
            // Only one cache line can be in EXCLUSIVE state
            if (exclusiveCount > 1) return false
            
            // Cannot have both MODIFIED and EXCLUSIVE for same address
            if (modifiedCount > 0 && exclusiveCount > 0) return false
        }
        
        return true
    }
} 