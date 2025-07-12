import kotlin.math.*
package rtsgame.pathfinding
import kotlinx.datetime.*
import kotlin.time.*

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.j
import rtsgame.terrain.TerrainSystem
import rtsgame.config.*
import kotlin.math.*
import kotlin.collections.mutableSetOf

/**
 * A* pathfinding implementation
 * Direct translation from JS with exact algorithm preservation
 */
class AStar(internal val terrainSystem: TerrainSystem) {
    
    data class PathNode(
        val x: Int,
        val y: Int,
        var gCost: Double = 0.0,      // Distance from start
        var hCost: Double = 0.0,      // Distance to target (heuristic)
        var fCost: Double = 0.0,      // Total cost (g + h)
        var parent: PathNode? = null,
        var walkable: Boolean = true
    ) {
        fun calculateFCost() {
            fCost = gCost + hCost
        }
        
        override fun equals(other: Any?): Boolean {
            return other is PathNode && other.x == x && other.y == y
        }
        
        override fun hashCode(): Int {
            return x * 31 + y
        }
    }
    
    internal val openSet = mutableSetOf<PathNode>()
    internal val closedSet = mutableSetOf<PathNode>()
    internal val nodeGrid = Array(GRID_SIZE) { x ->
        Array(GRID_SIZE) { y ->
            val tile = terrainSystem.getTile(x, y)
            PathNode(x, y, walkable = tile?.walkable ?: false)
        }
    }
    
    fun findPath(
        startX: Double, 
        startY: Double, 
        targetX: Double, 
        targetY: Double,
        unitSize: Double = 1.0
    ): Indexed<Pair<Double, Double>>? {
        
        // Convert world coordinates to grid coordinates
        val startGridX = (startX / TILE_SIZE).toInt().coerceIn(0, GRID_SIZE - 1)
        val startGridY = (startY / TILE_SIZE).toInt().coerceIn(0, GRID_SIZE - 1)
        val targetGridX = (targetX / TILE_SIZE).toInt().coerceIn(0, GRID_SIZE - 1)
        val targetGridY = (targetY / TILE_SIZE).toInt().coerceIn(0, GRID_SIZE - 1)
        
        // Clear previous search
        openSet.clear()
        closedSet.clear()
        resetNodeCosts()
        
        val startNode = nodeGrid[startGridX][startGridY]
        val targetNode = nodeGrid[targetGridX][targetGridY]
        
        // If target is not walkable, find nearest walkable node
        val actualTarget = if (!targetNode.walkable) {
            findNearestWalkableNode(targetGridX, targetGridY) ?: return null
        } else targetNode
        
        // If start is not walkable, return direct path (unit will handle it)
        if (!startNode.walkable) {
            return createDirectPath(startX, startY, targetX, targetY)
        }
        
        // Initialize start node
        startNode.gCost = 0.0
        startNode.hCost = calculateDistance(startNode, actualTarget)
        startNode.calculateFCost()
        
        openSet.add(startNode)
        
        var iterations = 0
        val maxIterations = GRID_SIZE * GRID_SIZE / 4 // Limit search to prevent infinite loops
        
        while (openSet.isNotEmpty() && iterations < maxIterations) {
            iterations++
            
            // Find node with lowest f cost
            val currentNode = openSet.minByOrNull { it.fCost } ?: break
            
            openSet.remove(currentNode)
            closedSet.add(currentNode)
            
            // Found target
            if (currentNode == actualTarget) {
                return reconstructPath(currentNode, startX, startY, targetX, targetY)
            }
            
            // Check all neighbors
            for (neighbor in getNeighbors(currentNode)) {
                if (!neighbor.walkable || neighbor in closedSet) {
                    continue
                }
                
                val newGCost = currentNode.gCost + calculateDistance(currentNode, neighbor)
                
                if (neighbor !in openSet) {
                    neighbor.gCost = newGCost
                    neighbor.hCost = calculateDistance(neighbor, actualTarget)
                    neighbor.calculateFCost()
                    neighbor.parent = currentNode
                    openSet.add(neighbor)
                } else if (newGCost < neighbor.gCost) {
                    neighbor.gCost = newGCost
                    neighbor.calculateFCost()
                    neighbor.parent = currentNode
                }
            }
        }
        
        // No path found - return direct path or null
        return if (iterations >= maxIterations) {
            createDirectPath(startX, startY, targetX, targetY)
        } else null
    }
    
    internal fun findNearestWalkableNode(x: Int, y: Int): PathNode? {
        val searchRadius = 10
        
        for (radius in 1..searchRadius) {
            for (dx in -radius..radius) {
                for (dy in -radius..radius) {
                    if (abs(dx) != radius && abs(dy) != radius) continue
                    
                    val nx = x + dx
                    val ny = y + dy
                    
                    if (nx in 0 until GRID_SIZE && ny in 0 until GRID_SIZE) {
                        val node = nodeGrid[nx][ny]
                        if (node.walkable) {
                            return node
                        }
                    }
                }
            }
        }
        
        return null
    }
    
    internal fun getNeighbors(node: PathNode): List<PathNode> {
        val neighbors = mutableListOf<PathNode>()
        
        for (dx in -1..1) {
            for (dy in -1..1) {
                if (dx == 0 && dy == 0) continue
                
                val x = node.x + dx
                val y = node.y + dy
                
                if (x in 0 until GRID_SIZE && y in 0 until GRID_SIZE) {
                    neighbors.add(nodeGrid[x][y])
                }
            }
        }
        
        return neighbors
    }
    
    internal fun calculateDistance(nodeA: PathNode, nodeB: PathNode): Double {
        val dx = abs(nodeA.x - nodeB.x)
        val dy = abs(nodeA.y - nodeB.y)
        
        // Use octile distance for more accurate pathfinding
        val diagonalCost = sqrt(2.0)
        val straightCost = 1.0
        
        return if (dx > dy) {
            diagonalCost * dy + straightCost * (dx - dy)
        } else {
            diagonalCost * dx + straightCost * (dy - dx)
        }
    }
    
    internal fun reconstructPath(
        endNode: PathNode, 
        startX: Double, 
        startY: Double,
        targetX: Double,
        targetY: Double
    ): Indexed<Pair<Double, Double>> {
        val path = mutableListOf<PathNode>()
        var currentNode: PathNode? = endNode
        
        // Trace back through parents
        while (currentNode != null) {
            path.add(currentNode)
            currentNode = currentNode.parent
        }
        
        path.reverse()
        
        // Convert to world coordinates and smooth
        val worldPath = mutableListOf<Pair<Double, Double>>()
        
        // Add start position
        worldPath.add(startX to startY)
        
        // Add path nodes (skip first if it's the start node)
        val skipFirst = path.isNotEmpty() && 
            abs(path[0].x * TILE_SIZE - startX) < TILE_SIZE && 
            abs(path[0].y * TILE_SIZE - startY) < TILE_SIZE
        
        val startIndex = if (skipFirst) 1 else 0
        
        for (i in startIndex until path.size) {
            val node = path[i]
            val worldX = node.x * TILE_SIZE + TILE_SIZE / 2.0
            val worldY = node.y * TILE_SIZE + TILE_SIZE / 2.0
            worldPath.add(worldX to worldY)
        }
        
        // Add final target position if different from last node
        val lastNode = path.lastOrNull()
        if (lastNode != null) {
            val lastWorldX = lastNode.x * TILE_SIZE + TILE_SIZE / 2.0
            val lastWorldY = lastNode.y * TILE_SIZE + TILE_SIZE / 2.0
            
            if (abs(lastWorldX - targetX) > 5.0 || abs(lastWorldY - targetY) > 5.0) {
                worldPath.add(targetX to targetY)
            }
        }
        
        // Smooth the path
        return smoothPath(worldPath)
    }
    
    internal fun smoothPath(path: List<Pair<Double, Double>>): Indexed<Pair<Double, Double>> {
        if (path.size <= 2) {
            return \1 j { \2: Int -> path[i] }
        }
        
        val smoothed = mutableListOf<Pair<Double, Double>>()
        smoothed.add(path.first())
        
        var i = 0
        while (i < path.size - 1) {
            val start = path[i]
            var end = start
            var endIndex = i
            
            // Find the furthest point we can reach in a straight line
            for (j in i + 1 until path.size) {
                if (hasLineOfSight(start.first, start.second, path[j].first, path[j].second)) {
                    end = path[j]
                    endIndex = j
                } else {
                    break
                }
            }
            
            if (endIndex > i) {
                smoothed.add(end)
                i = endIndex
            } else {
                i++
                if (i < path.size) {
                    smoothed.add(path[i])
                }
            }
        }
        
        return \1 j { \2: Int -> smoothed[i] }
    }
    
    internal fun hasLineOfSight(x1: Double, y1: Double, x2: Double, y2: Double): Boolean {
        val dx = x2 - x1
        val dy = y2 - y1
        val distance = sqrt(dx * dx + dy * dy)
        val steps = (distance / (TILE_SIZE / 2.0)).toInt().coerceAtLeast(1)
        
        for (i in 0..steps) {
            val t = i.toDouble() / steps
            val x = x1 + dx * t
            val y = y1 + dy * t
            
            if (!terrainSystem.isWalkable(x, y)) {
                return false
            }
        }
        
        return true
    }
    
    internal fun createDirectPath(startX: Double, startY: Double, targetX: Double, targetY: Double): Indexed<Pair<Double, Double>> {
        return \1 j { \2: Int ->
            if (i == 0) startX to startY else targetX to targetY
        }
    }
    
    internal fun resetNodeCosts() {
        for (x in 0 until GRID_SIZE) {
            for (y in 0 until GRID_SIZE) {
                val node = nodeGrid[x][y]
                node.gCost = 0.0
                node.hCost = 0.0
                node.fCost = 0.0
                node.parent = null
            }
        }
    }
    
    /**
     * Update walkability of terrain nodes when terrain changes
     */
    fun updateWalkability() {
        for (x in 0 until GRID_SIZE) {
            for (y in 0 until GRID_SIZE) {
                val tile = terrainSystem.getTile(x, y)
                nodeGrid[x][y].walkable = tile?.walkable ?: false
            }
        }
    }
    
    /**
     * Find path for large units that need multiple tiles
     */
    fun findPathForLargeUnit(
        startX: Double, 
        startY: Double, 
        targetX: Double, 
        targetY: Double,
        unitRadius: Double
    ): Indexed<Pair<Double, Double>>? {
        // For large units, check multiple tiles for walkability
        val gridRadius = ceil(unitRadius / TILE_SIZE).toInt()
        
        // Modify walkability check to account for unit size
        val originalWalkability = Array(GRID_SIZE) { x ->
            Array(GRID_SIZE) { y -> nodeGrid[x][y].walkable }
        }
        
        // Mark tiles as unwalkable if they don't have enough space
        for (x in 0 until GRID_SIZE) {
            for (y in 0 until GRID_SIZE) {
                if (nodeGrid[x][y].walkable) {
                    var hasSpace = true
                    
                    for (dx in -gridRadius..gridRadius) {
                        for (dy in -gridRadius..gridRadius) {
                            val nx = x + dx
                            val ny = y + dy
                            
                            if (nx in 0 until GRID_SIZE && ny in 0 until GRID_SIZE) {
                                val tile = terrainSystem.getTile(nx, ny)
                                if (tile?.walkable != true) {
                                    hasSpace = false
                                    break
                                }
                            } else {
                                hasSpace = false
                                break
                            }
                        }
                        if (!hasSpace) break
                    }
                    
                    nodeGrid[x][y].walkable = hasSpace
                }
            }
        }
        
        // Find path with modified walkability
        val path = findPath(startX, startY, targetX, targetY, unitRadius)
        
        // Restore original walkability
        for (x in 0 until GRID_SIZE) {
            for (y in 0 until GRID_SIZE) {
                nodeGrid[x][y].walkable = originalWalkability[x][y]
            }
        }
        
        return path
    }
}