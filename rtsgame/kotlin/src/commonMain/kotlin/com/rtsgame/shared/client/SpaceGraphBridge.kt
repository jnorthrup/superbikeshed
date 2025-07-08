package com.rtsgame.shared.client

import com.rtsgame.shared.entity.EntityManager
import com.rtsgame.shared.systems.CommandAndControlSystem
import com.rtsgame.shared.systems.ProofOfWorkSystem
import com.rtsgame.shared.systems.ComputroniumSystem
import kotlin.math.*

/**
 * SpaceGraph Bridge - Graphics integration interface
 * 
 * Key Features:
 * - Graphics Interface: Bridge between codec and graphics
 * - State Visualization: Convert game state to visual representation
 * - Event Handling: Graphics events to game state updates
 */
class SpaceGraphBridge(
    internal val entityManager: EntityManager,
    internal val commandSystem: CommandAndControlSystem,
    internal val proofOfWorkSystem: ProofOfWorkSystem,
    internal val computroniumSystem: ComputroniumSystem
) {
    
    // Visualization types
    enum class VisualizationType {
        ENTITY_POSITIONS,
        COMMAND_HIERARCHY,
        NETWORK_TOPOLOGY,
        BREACH_ATTEMPTS,
        COMPUTRONIUM_ALLOCATION,
        CACHE_STATES,
        FORMATION_LINES,
        RESOURCE_FLOWS
    }
    
    // Graphics event types
    enum class GraphicsEventType {
        CLICK,
        DRAG,
        HOVER,
        SELECT,
        DESELECT,
        ZOOM,
        PAN,
        ROTATE
    }
    
    // Graphics event
    data class GraphicsEvent(
        val id: Long,
        val eventType: GraphicsEventType,
        val targetEntityId: Long?,
        val position: Position?,
        val parameters: Map<String, Any>,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    // Visual entity representation
    data class VisualEntity(
        val entityId: Long,
        val position: Position,
        val visualType: VisualType,
        val properties: Map<String, Any>,
        val children: MutableList<VisualEntity> = mutableListOf()
    ) {
        enum class VisualType {
            UNIT,
            BUILDING,
            PROJECTILE,
            EFFECT,
            NETWORK_NODE,
            CACHE_LINE,
            RESOURCE_NODE,
            FORMATION_MARKER
        }
    }
    
    // Scene graph
    data class SceneGraph(
        val rootNodes: MutableList<VisualEntity> = mutableListOf(),
        val camera: Camera = Camera(),
        val lighting: Lighting = Lighting(),
        val effects: MutableList<VisualEffect> = mutableListOf()
    ) {
        data class Camera(
            var position: Position = Position(0.0, 0.0),
            var zoom: Double = 1.0,
            var rotation: Double = 0.0
        )
        
        data class Lighting(
            var ambient: Double = 0.3,
            var directional: Double = 0.7,
            var shadows: Boolean = true
        )
    }
    
    // Visual effects
    data class VisualEffect(
        val id: Long,
        val effectType: EffectType,
        val position: Position,
        val duration: Double,
        val parameters: Map<String, Any>
    ) {
        enum class EffectType {
            EXPLOSION,
            LASER_BEAM,
            SHIELD_BREACH,
            DATA_STREAM,
            NETWORK_PULSE,
            CACHE_FLUSH,
            FORMATION_LINE,
            RESOURCE_FLOW
        }
    }
    
    // Bridge state
    data class BridgeState(
        val sceneGraph: SceneGraph = SceneGraph(),
        val eventQueue: MutableList<GraphicsEvent> = mutableListOf(),
        val visualEntities: MutableMap<Long, VisualEntity> = mutableMapOf(),
        val lastUpdate: Long = System.currentTimeMillis()
    )
    
    internal val bridgeState = BridgeState()
    
    /**
     * Convert game state to visual representation
     */
    fun updateVisualization(visualizationType: VisualizationType): SceneGraph {
        when (visualizationType) {
            VisualizationType.ENTITY_POSITIONS -> updateEntityPositions()
            VisualizationType.COMMAND_HIERARCHY -> updateCommandHierarchy()
            VisualizationType.NETWORK_TOPOLOGY -> updateNetworkTopology()
            VisualizationType.BREACH_ATTEMPTS -> updateBreachAttempts()
            VisualizationType.COMPUTRONIUM_ALLOCATION -> updateComputroniumAllocation()
            VisualizationType.CACHE_STATES -> updateCacheStates()
            VisualizationType.FORMATION_LINES -> updateFormationLines()
            VisualizationType.RESOURCE_FLOWS -> updateResourceFlows()
        }
        
        return bridgeState.sceneGraph
    }
    
    /**
     * Update entity positions visualization
     */
    internal fun updateEntityPositions() {
        bridgeState.sceneGraph.rootNodes.clear()
        
        entityManager.getAllEntities().forEach { entity ->
            val position = entity.getComponent<Position>()
            if (position != null) {
                val visualType = when {
                    entity.hasComponent(UnitComponent::class.java) -> VisualEntity.VisualType.UNIT
                    entity.hasComponent(BuildingComponent::class.java) -> VisualEntity.VisualType.BUILDING
                    entity.hasComponent(ProjectileComponent::class.java) -> VisualEntity.VisualType.PROJECTILE
                    entity.hasComponent(EffectComponent::class.java) -> VisualEntity.VisualType.EFFECT
                    else -> VisualEntity.VisualType.UNIT
                }
                
                val properties = mutableMapOf<String, Any>()
                entity.getComponent<HealthComponent>()?.let { health ->
                    properties["health"] = health.currentHealth
                    properties["maxHealth"] = health.maxHealth
                }
                
                entity.getComponent<TeamComponent>()?.let { team ->
                    properties["team"] = team.teamId
                }
                
                val visualEntity = VisualEntity(
                    entityId = entity.id,
                    position = position,
                    visualType = visualType,
                    properties = properties
                )
                
                bridgeState.sceneGraph.rootNodes.add(visualEntity)
                bridgeState.visualEntities[entity.id] = visualEntity
            }
        }
    }
    
    /**
     * Update command hierarchy visualization
     */
    internal fun updateCommandHierarchy() {
        val hierarchyNodes = mutableListOf<VisualEntity>()
        
        entityManager.getAllEntities().forEach { entity ->
            val hierarchy = entity.getComponent<CommandAndControlSystem.CommandHierarchy>()
            if (hierarchy != null) {
                val position = entity.getComponent<Position>() ?: Position(0.0, 0.0)
                
                val visualEntity = VisualEntity(
                    entityId = entity.id,
                    position = position,
                    visualType = VisualEntity.VisualType.UNIT,
                    properties = mapOf(
                        "rank" to hierarchy.rank.name,
                        "authority" to hierarchy.authority,
                        "subordinates" to hierarchy.subordinateIds.size
                    )
                )
                
                // Add subordinate connections
                hierarchy.subordinateIds.forEach { subordinateId ->
                    val subordinateEntity = entityManager.getEntity(subordinateId)
                    val subordinatePosition = subordinateEntity?.getComponent<Position>()
                    if (subordinatePosition != null) {
                        val subordinateVisual = VisualEntity(
                            entityId = subordinateId,
                            position = subordinatePosition,
                            visualType = VisualEntity.VisualType.UNIT,
                            properties = mapOf("subordinate" to true)
                        )
                        visualEntity.children.add(subordinateVisual)
                    }
                }
                
                hierarchyNodes.add(visualEntity)
            }
        }
        
        bridgeState.sceneGraph.rootNodes.clear()
        bridgeState.sceneGraph.rootNodes.addAll(hierarchyNodes)
    }
    
    /**
     * Update network topology visualization
     */
    internal fun updateNetworkTopology() {
        val networkNodes = mutableListOf<VisualEntity>()
        
        entityManager.getAllEntities().forEach { entity ->
            val networkState = entity.getComponent<CommandAndControlSystem.NetworkState>()
            if (networkState != null) {
                val position = entity.getComponent<Position>() ?: Position(0.0, 0.0)
                
                val visualEntity = VisualEntity(
                    entityId = entity.id,
                    position = position,
                    visualType = VisualEntity.VisualType.NETWORK_NODE,
                    properties = mapOf(
                        "connections" to networkState.connections.size,
                        "pendingCommands" to networkState.pendingCommands.size,
                        "cacheLines" to networkState.cacheLines.size
                    )
                )
                
                // Add connection lines
                networkState.connections.keys.forEach { targetId ->
                    val targetEntity = entityManager.getEntity(targetId)
                    val targetPosition = targetEntity?.getComponent<Position>()
                    if (targetPosition != null) {
                        val connectionVisual = VisualEntity(
                            entityId = targetId,
                            position = targetPosition,
                            visualType = VisualEntity.VisualType.NETWORK_NODE,
                            properties = mapOf("connection" to true)
                        )
                        visualEntity.children.add(connectionVisual)
                    }
                }
                
                networkNodes.add(visualEntity)
            }
        }
        
        bridgeState.sceneGraph.rootNodes.clear()
        bridgeState.sceneGraph.rootNodes.addAll(networkNodes)
    }
    
    /**
     * Update breach attempts visualization
     */
    internal fun updateBreachAttempts() {
        val breachEffects = mutableListOf<VisualEffect>()
        
        entityManager.getAllEntities().forEach { entity ->
            val security = entity.getComponent<ProofOfWorkSystem.SecurityComponent>()
            if (security != null && security.activeBreaches.isNotEmpty()) {
                val position = entity.getComponent<Position>() ?: Position(0.0, 0.0)
                
                val breachEffect = VisualEffect(
                    id = entity.id,
                    effectType = VisualEffect.EffectType.SHIELD_BREACH,
                    position = position,
                    duration = 2.0,
                    parameters = mapOf(
                        "intensity" to security.activeBreaches.size,
                        "threatLevel" to 0.8
                    )
                )
                
                breachEffects.add(breachEffect)
            }
        }
        
        bridgeState.sceneGraph.effects.clear()
        bridgeState.sceneGraph.effects.addAll(breachEffects)
    }
    
    /**
     * Update computronium allocation visualization
     */
    internal fun updateComputroniumAllocation() {
        val resourceNodes = mutableListOf<VisualEntity>()
        
        entityManager.getAllEntities().forEach { entity ->
            val computronium = entity.getComponent<ComputroniumSystem.ComputroniumComponent>()
            if (computronium != null && computronium.amount > 0) {
                val position = entity.getComponent<Position>() ?: Position(0.0, 0.0)
                
                val visualEntity = VisualEntity(
                    entityId = entity.id,
                    position = position,
                    visualType = VisualEntity.VisualType.RESOURCE_NODE,
                    properties = mapOf(
                        "computronium" to computronium.amount,
                        "processingPower" to computronium.processingPower,
                        "efficiency" to computronium.efficiency
                    )
                )
                
                resourceNodes.add(visualEntity)
            }
        }
        
        bridgeState.sceneGraph.rootNodes.clear()
        bridgeState.sceneGraph.rootNodes.addAll(resourceNodes)
    }
    
    /**
     * Update cache states visualization
     */
    internal fun updateCacheStates() {
        val cacheNodes = mutableListOf<VisualEntity>()
        
        entityManager.getAllEntities().forEach { entity ->
            val networkState = entity.getComponent<CommandAndControlSystem.NetworkState>()
            if (networkState != null && networkState.cacheLines.isNotEmpty()) {
                val position = entity.getComponent<Position>() ?: Position(0.0, 0.0)
                
                val cacheStates = networkState.cacheLines.values.groupBy { it.state }
                val visualEntity = VisualEntity(
                    entityId = entity.id,
                    position = position,
                    visualType = VisualEntity.VisualType.CACHE_LINE,
                    properties = mapOf(
                        "modified" to (cacheStates[CacheCoherence.CacheState.MODIFIED]?.size ?: 0),
                        "exclusive" to (cacheStates[CacheCoherence.CacheState.EXCLUSIVE]?.size ?: 0),
                        "shared" to (cacheStates[CacheCoherence.CacheState.SHARED]?.size ?: 0),
                        "invalid" to (cacheStates[CacheCoherence.CacheState.INVALID]?.size ?: 0)
                    )
                )
                
                cacheNodes.add(visualEntity)
            }
        }
        
        bridgeState.sceneGraph.rootNodes.clear()
        bridgeState.sceneGraph.rootNodes.addAll(cacheNodes)
    }
    
    /**
     * Update formation lines visualization
     */
    internal fun updateFormationLines() {
        val formationMarkers = mutableListOf<VisualEntity>()
        
        // This would integrate with the formation system
        // For now, create placeholder formation markers
        
        bridgeState.sceneGraph.rootNodes.clear()
        bridgeState.sceneGraph.rootNodes.addAll(formationMarkers)
    }
    
    /**
     * Update resource flows visualization
     */
    internal fun updateResourceFlows() {
        val resourceEffects = mutableListOf<VisualEffect>()
        
        entityManager.getAllEntities().forEach { entity ->
            val energy = entity.getComponent<ComputroniumSystem.EnergyComponent>()
            if (energy != null && energy.amount > 0) {
                val position = entity.getComponent<Position>() ?: Position(0.0, 0.0)
                
                val resourceEffect = VisualEffect(
                    id = entity.id,
                    effectType = VisualEffect.EffectType.RESOURCE_FLOW,
                    position = position,
                    duration = 1.0,
                    parameters = mapOf(
                        "energy" to energy.amount,
                        "generationRate" to energy.generationRate
                    )
                )
                
                resourceEffects.add(resourceEffect)
            }
        }
        
        bridgeState.sceneGraph.effects.clear()
        bridgeState.sceneGraph.effects.addAll(resourceEffects)
    }
    
    /**
     * Handle graphics events
     */
    fun handleGraphicsEvent(event: GraphicsEvent): List<MoveClient.PlayerAction> {
        bridgeState.eventQueue.add(event)
        
        val actions = mutableListOf<MoveClient.PlayerAction>()
        
        when (event.eventType) {
            GraphicsEventType.CLICK -> {
                event.targetEntityId?.let { entityId ->
                    val action = MoveClient.PlayerAction(
                        id = System.nanoTime(),
                        playerId = 1L, // This would come from the UI context
                        actionType = MoveClient.PlayerActionType.MOVE_UNIT,
                        targetEntityId = entityId,
                        parameters = event.parameters,
                        timestamp = event.timestamp
                    )
                    actions.add(action)
                }
            }
            GraphicsEventType.SELECT -> {
                event.targetEntityId?.let { entityId ->
                    // Handle entity selection
                    val action = MoveClient.PlayerAction(
                        id = System.nanoTime(),
                        playerId = 1L,
                        actionType = MoveClient.PlayerActionType.FORMATION_COMMAND,
                        targetEntityId = entityId,
                        parameters = event.parameters,
                        timestamp = event.timestamp
                    )
                    actions.add(action)
                }
            }
            GraphicsEventType.DRAG -> {
                event.position?.let { position ->
                    val action = MoveClient.PlayerAction(
                        id = System.nanoTime(),
                        playerId = 1L,
                        actionType = MoveClient.PlayerActionType.MOVE_UNIT,
                        targetEntityId = null,
                        parameters = mapOf(
                            "targetPosition" to mapOf("x" to position.x, "y" to position.y)
                        ),
                        timestamp = event.timestamp
                    )
                    actions.add(action)
                }
            }
            else -> {
                // Handle other event types
            }
        }
        
        return actions
    }
    
    /**
     * Get current scene graph
     */
    fun getSceneGraph(): SceneGraph {
        return bridgeState.sceneGraph
    }
    
    /**
     * Update camera
     */
    fun updateCamera(position: Position, zoom: Double, rotation: Double) {
        bridgeState.sceneGraph.camera.position = position
        bridgeState.sceneGraph.camera.zoom = zoom
        bridgeState.sceneGraph.camera.rotation = rotation
    }
    
    /**
     * Add visual effect
     */
    fun addVisualEffect(effect: VisualEffect) {
        bridgeState.sceneGraph.effects.add(effect)
    }
    
    /**
     * Remove visual effect
     */
    fun removeVisualEffect(effectId: Long) {
        bridgeState.sceneGraph.effects.removeAll { it.id == effectId }
    }
    
    /**
     * Clear all effects
     */
    fun clearEffects() {
        bridgeState.sceneGraph.effects.clear()
    }
    
    /**
     * Get bridge statistics
     */
    fun getBridgeStats(): BridgeStats {
        return BridgeStats(
            visualEntitiesCount = bridgeState.visualEntities.size,
            eventQueueSize = bridgeState.eventQueue.size,
            rootNodesCount = bridgeState.sceneGraph.rootNodes.size,
            effectsCount = bridgeState.sceneGraph.effects.size,
            lastUpdate = bridgeState.lastUpdate
        )
    }
    
    /**
     * Bridge statistics
     */
    data class BridgeStats(
        val visualEntitiesCount: Int,
        val eventQueueSize: Int,
        val rootNodesCount: Int,
        val effectsCount: Int,
        val lastUpdate: Long
    )
    
    // Placeholder component types
    data class Position(val x: Double, val y: Double)
    data class UnitComponent(val unitType: String)
    data class BuildingComponent(val buildingType: String)
    data class ProjectileComponent(val projectileType: String)
    data class EffectComponent(val effectType: String)
    data class HealthComponent(val currentHealth: Double, val maxHealth: Double)
    data class TeamComponent(val teamId: Int)
} 