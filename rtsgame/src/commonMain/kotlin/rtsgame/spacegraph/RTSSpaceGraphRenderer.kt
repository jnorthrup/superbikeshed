import kotlin.math.*
package rtsgame.spacegraph
import kotlinx.datetime.*
import kotlin.time.*

import borg.trikeshed.lib.*
import rtsgame.core.*
import rtsgame.compat.*

/**
 * SpaceGraph integration for RTS Game visualization
 * Maps game entities to spacegraph nodes and relationships to edges
 */

value class NodeId(val value: String)

value class EdgeId(val value: String)

value class VisualizationScale(val value: Float)

data class RenderingConfig(
    val scale: VisualizationScale = VisualizationScale(1.0f),
    val showConnections: Boolean = true,
    val animateMovement: Boolean = true
)

typealias NodeMapping = Join<EntityId, NodeId>
typealias PositionMapping = Join<Position, Vector3D>

data class Vector3D(
    val x: Double,
    val y: Double, 
    val z: Double = 0.0
)

/**
 * Renders RTS game state using SpaceGraph visualization
 */
class RTSSpaceGraphRenderer(
    internal val config: RenderingConfig = RenderingConfig()
) {
    internal var nodeMappings: Indexed<NodeMapping> = Indexed.of(0) { throw IndexOutOfBoundsException() }
    internal var lastGameState: GameState? = null
    
    fun renderGameState(gameState: GameState): RenderResult {
        // Convert entities to spacegraph representation
        val nodeData = gameState.entities.α { entity ->
            convertEntityToNode(entity)
        }
        
        // Convert relationships to edges
        val edgeData = if (config.showConnections) {
            generateEntityConnections(gameState.entities)
        } else {
            Indexed.of(0) { throw IndexOutOfBoundsException() }
        }
        
        lastGameState = gameState
        
        return RenderResult(
            nodes = nodeData,
            edges = edgeData as Indexed<SpaceGraphEdge>,
            metadata = RenderMetadata(
                entityCount = gameState.entities.`play`.size,
                tick = gameState.tick
            )
        )
    }
    
    internal fun convertEntityToNode(entity: Entity): SpaceGraphNode {
        val worldPos = convertToWorldPosition(entity.position)
        
        return SpaceGraphNode(
            id = NodeId("entity_${entity.id.value}"),
            position = worldPos,
            data = NodeData(
                label = entity.id.value,
                health = entity.health.value,
                playerId = entity.playerId.value,
                entityType = determineEntityType(entity)
            )
        )
    }
    
    internal fun convertToWorldPosition(gamePos: Position): Vector3D {
        return Vector3D(
            x = gamePos.x.toDouble() * config.scale.value,
            y = gamePos.y.toDouble() * config.scale.value,
            z = 0.0
        )
    }
    
    internal fun determineEntityType(entity: Entity): EntityType {
        // Simple heuristic based on entity properties
        return when {
            entity.health.value > 80f -> EntityType.COMMANDER
            entity.health.value > 50f -> EntityType.UNIT
            else -> EntityType.SCOUT
        }
    }
    
    internal fun generateEntityConnections(entities: EntitySeries): Indexed<SpaceGraphEdge> {
        val entityList = entities.`play`
        val connections = mutableListOf<SpaceGraphEdge>()
        
        // Generate proximity-based connections
        for (i in entityList.indices) {
            for (j in i + 1 until entityList.size) {
                val entity1 = entityList[i]
                val entity2 = entityList[j]
                
                if (shouldConnect(entity1, entity2)) {
                    connections.add(SpaceGraphEdge(
                        id = EdgeId("edge_${entity1.id.value}_${entity2.id.value}"),
                        source = NodeId("entity_${entity1.id.value}"),
                        target = NodeId("entity_${entity2.id.value}"),
                        connectionType = determineConnectionType(entity1, entity2)
                    ))
                }
            }
        }
        
        return Indexed.of(connections.size) { i -> connections[i] }
    }
    
    internal fun shouldConnect(entity1: Entity, entity2: Entity): Boolean {
        // Connect entities of same player within proximity
        if (entity1.playerId != entity2.playerId) return false
        
        val distance = calculateDistance(entity1.position, entity2.position)
        return distance < 150.0 // Proximity threshold
    }
    
    internal fun calculateDistance(pos1: Position, pos2: Position): Double {
        val dx = pos1.x - pos2.x
        val dy = pos1.y - pos2.y
        return kotlin.math.sqrt((dx * dx + dy * dy).toDouble())
    }
    
    internal fun determineConnectionType(entity1: Entity, entity2: Entity): ConnectionType {
        return when {
            entity1.playerId == entity2.playerId -> ConnectionType.ALLY
            else -> ConnectionType.NEUTRAL
        }
    }
}

data class SpaceGraphNode(
    val id: NodeId,
    val position: Vector3D,
    val data: NodeData
)

data class NodeData(
    val label: String,
    val health: Float,
    val playerId: Int,
    val entityType: EntityType
)

data class SpaceGraphEdge(
    val id: EdgeId,
    val source: NodeId,
    val target: NodeId,
    val connectionType: ConnectionType
)

data class RenderResult(
    val nodes: Indexed<SpaceGraphNode>,
    val edges: Indexed<SpaceGraphEdge>,
    val metadata: RenderMetadata
)

data class RenderMetadata(
    val entityCount: Int,
    val tick: GameTick
)

enum class EntityType {
    COMMANDER, UNIT, SCOUT, BUILDING
}

enum class ConnectionType {
    ALLY, ENEMY, NEUTRAL, COMMAND
}