package com.rtsgame.shared.map

import com.rtsgame.shared.entity.Position
import kotlinx.serialization.Serializable

data class GameMap(
    val width: Int,
    val height: Int,
    val grid: List<List<Tile>>,
    val resourceNodes: List<ResourceNode> = emptyList()
) {
    init {
        require(grid.size == height) { "Map height doesn't match grid size" }
        require(grid.all { it.size == width }) { "Map width doesn't match grid size" }
    }

    fun getTile(x: Int, y: Int): Tile? {
        return if (x in 0 until width && y in 0 until height) {
            grid[y][x]
        } else {
            null
        }
    }

    fun getTile(position: Position): Tile? {
        return getTile(position.x.toInt(), position.y.toInt())
    }

    fun isWalkable(x: Int, y: Int): Boolean {
        return getTile(x, y)?.isWalkable ?: false
    }

    fun isWalkable(position: Position): Boolean {
        return isWalkable(position.x.toInt(), position.y.toInt())
    }

    fun getNeighbors(x: Int, y: Int): List<Position> {
        val neighbors = mutableListOf<Position>()
        for (dx in -1..1) {
            for (dy in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = x + dx
                val ny = y + dy
                if (isWalkable(nx, ny)) {
                    neighbors.add(Position(nx.toFloat(), ny.toFloat()))
                }
            }
        }
        return neighbors
    }

    fun getNeighbors(position: Position): List<Position> {
        return getNeighbors(position.x.toInt(), position.y.toInt())
    }

    fun getResourceNodeAt(position: Position): ResourceNode? {
        return resourceNodes.find { it.position == position }
    }

    fun getResourceNodesOfType(type: ResourceType): List<ResourceNode> {
        return resourceNodes.filter { it.type == type }
    }

    fun getResourceNodesInRange(position: Position, range: Float): List<ResourceNode> {
        return resourceNodes.filter { it.position.distanceTo(position) <= range }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as GameMap

        if (width != other.width) return false
        if (height != other.height) return false
        if (grid != other.grid) return false
        if (resourceNodes != other.resourceNodes) return false

        return true
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + grid.hashCode()
        result = 31 * result + resourceNodes.hashCode()
        return result
    }
}

data class Tile(
    val type: TileType,
    val height: Float = 0f,
    val isWalkable: Boolean = true,
    val isBuildable: Boolean = true,
    val isResourceNode: Boolean = false,
    val resourceType: ResourceType? = null,
    val resourceAmount: Float = 0f
)

enum class TileType {
    GRASS,
    DIRT,
    SAND,
    ROCK,
    WATER,
    DEEP_WATER,
    FOREST,
    MOUNTAIN,
    SNOW,
    ICE,
    LAVA,
    VOID
}

data class ResourceNode(
    val id: String,
    val position: Position,
    val type: ResourceType,
    val amount: Float,
    val maxAmount: Float,
    val respawnTime: Float,
    val isDepleted: Boolean = false,
    val respawnTimer: Float = 0f,
    val environmentalDamage: Float = 0f
) 