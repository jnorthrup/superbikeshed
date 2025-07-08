package com.rtsgame.shared.systems

import com.rtsgame.shared.entity.Entity
import com.rtsgame.shared.entity.EntityManager
import kotlin.math.*

/**
 * Command & Control System - Absorbed from JavaScript gem
 * 
 * Key Features:
 * - Network latency simulation with light speed physics
 * - Cache coherence protocol (MESI) with L1/L2/L3 modeling
 * - Command hierarchy (General → Colonel → Major → Captain → Lieutenant)
 * - Client-side prediction with server reconciliation
 * - Sophisticated tactical decision making
 */
class CommandAndControlSystem(
    internal val entityManager: EntityManager
) {
    
    // Network Physics Constants (from JS gem)
    companion object {
        const val LIGHT_SPEED_KM_S = 299792.458
        const val FIBER_OPTIC_SPEED_FACTOR = 0.67 // 67% of light speed
        const val COPPER_SPEED_FACTOR = 0.5 // 50% of light speed
        
        // Cache Coherence Constants
        const val L1_CACHE_LATENCY_NS = 1
        const val L2_CACHE_LATENCY_NS = 10
        const val L3_CACHE_LATENCY_NS = 50
        const val MAIN_MEMORY_LATENCY_NS = 100
        
        // Command Hierarchy Ranks
        enum class Rank(val authority: Int, val maxSubordinates: Int) {
            GENERAL(5, 10),
            COLONEL(4, 8),
            MAJOR(3, 6),
            CAPTAIN(2, 4),
            LIEUTENANT(1, 2),
            PRIVATE(0, 0)
        }
        
        // MESI Cache States
        enum class CacheState {
            MODIFIED, EXCLUSIVE, SHARED, INVALID
        }
    }
    
    // Network Physics Engine
    data class NetworkPhysics(
        val distanceKm: Double,
        val connectionType: ConnectionType,
        val latencyMs: Double,
        val bandwidthMbps: Double
    ) {
        enum class ConnectionType {
            FIBER_OPTIC, COPPER, WIRELESS, SATELLITE
        }
        
        fun calculateLatency(): Double {
            val speedFactor = when (connectionType) {
                ConnectionType.FIBER_OPTIC -> FIBER_OPTIC_SPEED_FACTOR
                ConnectionType.COPPER -> COPPER_SPEED_FACTOR
                ConnectionType.WIRELESS -> 0.3
                ConnectionType.SATELLITE -> 0.1
            }
            
            val effectiveSpeed = LIGHT_SPEED_KM_S * speedFactor
            return (distanceKm / effectiveSpeed) * 1000 // Convert to milliseconds
        }
    }
    
    // Cache Coherence Protocol
    data class CacheLine(
        val address: Long,
        var state: CacheState,
        var data: ByteArray,
        var lastAccess: Long
    ) {
        fun transitionTo(newState: CacheState) {
            state = newState
            lastAccess = System.nanoTime()
        }
    }
    
    // Command Hierarchy Component
    data class CommandHierarchy(
        val rank: Rank,
        val superiorId: Long?,
        val subordinateIds: MutableList<Long> = mutableListOf(),
        val authority: Int = rank.authority,
        val maxSubordinates: Int = rank.maxSubordinates
    )
    
    // Network State Component
    data class NetworkState(
        val nodeId: Long,
        val connections: MutableMap<Long, NetworkPhysics> = mutableMapOf(),
        val cacheLines: MutableMap<Long, CacheLine> = mutableMapOf(),
        val pendingCommands: MutableList<Command> = mutableListOf(),
        val predictionBuffer: MutableList<Command> = mutableListOf()
    )
    
    // Command Structure
    data class Command(
        val id: Long,
        val sourceId: Long,
        val targetId: Long,
        val commandType: CommandType,
        val parameters: Map<String, Any>,
        val timestamp: Long,
        val priority: Int,
        val requiresAuthority: Boolean = false
    ) {
        enum class CommandType {
            MOVE, ATTACK, DEFEND, FORMATION, RESOURCE_GATHER, ABILITY_USE
        }
    }
    
    // Tactical Decision Engine
    data class TacticalOption(
        val action: Command.CommandType,
        val targetId: Long?,
        val score: Double,
        val risk: Double,
        val reward: Double,
        val executionTime: Double
    )
    
    /**
     * Calculate network latency between two entities
     */
    fun calculateNetworkLatency(sourceId: Long, targetId: Long): Double {
        val source = entityManager.getEntity(sourceId) ?: return Double.MAX_VALUE
        val target = entityManager.getEntity(targetId) ?: return Double.MAX_VALUE
        
        val sourcePos = source.getComponent<Position>() ?: return Double.MAX_VALUE
        val targetPos = target.getComponent<Position>() ?: return Double.MAX_VALUE
        
        val distance = calculateDistance(sourcePos, targetPos)
        val networkPhysics = NetworkPhysics(
            distanceKm = distance / 1000.0, // Convert to km
            connectionType = NetworkPhysics.ConnectionType.FIBER_OPTIC,
            latencyMs = 0.0,
            bandwidthMbps = 1000.0
        )
        
        return networkPhysics.calculateLatency()
    }
    
    /**
     * Process cache coherence protocol
     */
    fun processCacheCoherence(command: Command): Boolean {
        val sourceNetwork = entityManager.getEntity(command.sourceId)?.getComponent<NetworkState>()
        val targetNetwork = entityManager.getEntity(command.targetId)?.getComponent<NetworkState>()
        
        if (sourceNetwork == null || targetNetwork == null) return false
        
        // Simulate cache line access
        val cacheLine = sourceNetwork.cacheLines.getOrPut(command.id) {
            CacheLine(command.id, CacheState.INVALID, ByteArray(64), System.nanoTime())
        }
        
        // MESI protocol state transitions
        when (cacheLine.state) {
            CacheState.INVALID -> {
                // Read miss - fetch from memory
                cacheLine.transitionTo(CacheState.EXCLUSIVE)
                return true
            }
            CacheState.SHARED -> {
                // Read hit - can proceed
                return true
            }
            CacheState.EXCLUSIVE -> {
                // Exclusive access - can proceed
                return true
            }
            CacheState.MODIFIED -> {
                // Write back required
                cacheLine.transitionTo(CacheState.EXCLUSIVE)
                return true
            }
        }
    }
    
    /**
     * Validate command authority in hierarchy
     */
    fun validateCommandAuthority(command: Command): Boolean {
        val sourceHierarchy = entityManager.getEntity(command.sourceId)?.getComponent<CommandHierarchy>()
        val targetHierarchy = entityManager.getEntity(command.targetId)?.getComponent<CommandHierarchy>()
        
        if (sourceHierarchy == null || targetHierarchy == null) return false
        
        // Check if source has authority over target
        return sourceHierarchy.authority >= targetHierarchy.authority
    }
    
    /**
     * Generate tactical options for an entity
     */
    fun generateTacticalOptions(entityId: Long): List<TacticalOption> {
        val entity = entityManager.getEntity(entityId) ?: return emptyList()
        val options = mutableListOf<TacticalOption>()
        
        // Get nearby entities for tactical analysis
        val nearbyEntities = getNearbyEntities(entityId, 100.0)
        
        // Analyze each potential action
        nearbyEntities.forEach { targetEntity ->
            val distance = calculateDistanceToEntity(entityId, targetEntity.id)
            val threatLevel = calculateThreatLevel(targetEntity)
            val resourceValue = calculateResourceValue(targetEntity)
            
            // Attack option
            if (threatLevel > 0.3) {
                options.add(TacticalOption(
                    action = Command.CommandType.ATTACK,
                    targetId = targetEntity.id,
                    score = threatLevel * 0.7 + resourceValue * 0.3,
                    risk = threatLevel,
                    reward = resourceValue,
                    executionTime = distance / 10.0 // Assume 10 units/second movement
                ))
            }
            
            // Defend option
            if (threatLevel > 0.5) {
                options.add(TacticalOption(
                    action = Command.CommandType.DEFEND,
                    targetId = targetEntity.id,
                    score = threatLevel * 0.8,
                    risk = threatLevel * 0.5,
                    reward = 1.0 - threatLevel,
                    executionTime = 0.0 // Immediate
                ))
            }
        }
        
        return options.sortedByDescending { it.score }
    }
    
    /**
     * Client-side prediction with server reconciliation
     */
    fun predictCommand(command: Command): Command {
        val networkState = entityManager.getEntity(command.sourceId)?.getComponent<NetworkState>()
        if (networkState == null) return command
        
        // Add to prediction buffer
        networkState.predictionBuffer.add(command)
        
        // Apply prediction based on network latency
        val latency = calculateNetworkLatency(command.sourceId, command.targetId)
        val predictedTimestamp = command.timestamp + latency.toLong()
        
        return command.copy(timestamp = predictedTimestamp)
    }
    
    /**
     * Reconcile predicted commands with actual server state
     */
    fun reconcileCommands(entityId: Long, serverCommands: List<Command>) {
        val networkState = entityManager.getEntity(entityId)?.getComponent<NetworkState>()
        if (networkState == null) return
        
        // Remove commands that have been confirmed by server
        networkState.predictionBuffer.removeAll { predicted ->
            serverCommands.any { server ->
                server.id == predicted.id && 
                server.sourceId == predicted.sourceId &&
                server.targetId == predicted.targetId
            }
        }
        
        // Apply corrections for remaining predictions
        networkState.predictionBuffer.forEach { predicted ->
            val serverCommand = serverCommands.find { it.id == predicted.id }
            if (serverCommand != null && serverCommand != predicted) {
                // Apply correction
                applyCommandCorrection(predicted, serverCommand)
            }
        }
    }
    
    // Helper functions
    internal fun calculateDistance(pos1: Position, pos2: Position): Double {
        return sqrt((pos2.x - pos1.x).pow(2) + (pos2.y - pos1.y).pow(2))
    }
    
    internal fun calculateDistanceToEntity(sourceId: Long, targetId: Long): Double {
        val source = entityManager.getEntity(sourceId)?.getComponent<Position>()
        val target = entityManager.getEntity(targetId)?.getComponent<Position>()
        return if (source != null && target != null) calculateDistance(source, target) else Double.MAX_VALUE
    }
    
    internal fun getNearbyEntities(entityId: Long, radius: Double): List<Entity> {
        val entity = entityManager.getEntity(entityId) ?: return emptyList()
        val entityPos = entity.getComponent<Position>() ?: return emptyList()
        
        return entityManager.getAllEntities().filter { other ->
            if (other.id == entityId) return@filter false
            val otherPos = other.getComponent<Position>() ?: return@filter false
            calculateDistance(entityPos, otherPos) <= radius
        }
    }
    
    internal fun calculateThreatLevel(entity: Entity): Double {
        // Simplified threat calculation
        val combat = entity.getComponent<CombatProperties>()
        return combat?.attackPower?.toDouble() ?: 0.0
    }
    
    internal fun calculateResourceValue(entity: Entity): Double {
        // Simplified resource value calculation
        val resources = entity.getComponent<ResourceComponent>()
        return resources?.amount?.toDouble() ?: 0.0
    }
    
    internal fun applyCommandCorrection(predicted: Command, actual: Command) {
        // Apply correction logic here
        // This would update entity states based on server correction
    }
    
    // Placeholder component types (these should match your actual ECS)
    data class Position(val x: Double, val y: Double)
    data class ResourceComponent(val amount: Int)
} 