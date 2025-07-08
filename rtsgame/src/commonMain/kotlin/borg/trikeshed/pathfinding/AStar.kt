import kotlin.math.*
package borg.trikeshed.pathfinding
import kotlinx.datetime.*
import kotlin.time.*

import borg.trikeshed.lib.*

/**
 * A* Pathfinding Implementation - Kotlin Port
 */

/**
 * Node for A* pathfinding
 */
data class PathNode(
    val x: Int,
    val y: Int,
    val walkable: Boolean = true,
    var g: Int = 0, // Cost from start to current node
    var h: Int = 0, // Heuristic cost from current node to end
    var f: Int = 0, // Total cost (g + h)
    var parent: PathNode? = null
)

/**
 * Find path using A* algorithm
 */
fun findPath(
    startX: Int, 
    startY: Int, 
    endX: Int, 
    endY: Int, 
    grid: Indexed<Indexed<Int>>
): List<Join<Int, Int>>? {
    val openSet = mutableSetOf<PathNode>()
    val closedSet = mutableSetOf<PathNode>()
    val startNode = PathNode(startX, startY)
    val endNode = PathNode(endX, endY)
    
    openSet.add(startNode)
    
    while (openSet.isNotEmpty()) {
        // Find node with lowest f cost
        val current = openSet.minByOrNull { it.f } ?: break
        
        // If we reached the end, reconstruct and return the path
        if (current.x == endNode.x && current.y == endNode.y) {
            return reconstructPath(current)
        }
        
        // Move current node from open to closed set
        openSet.remove(current)
        closedSet.add(current)
        
        // Check all neighbors
        val neighbors = getNeighbors(current, grid)
        for (neighbor in neighbors) {
            if (closedSet.contains(neighbor)) {
                continue
            }
            
            val tentativeG = current.g + 1
            
            if (!openSet.contains(neighbor)) {
                openSet.add(neighbor)
            } else if (tentativeG >= neighbor.g) {
                continue
            }
            
            // This path is the best until now
            neighbor.parent = current
            neighbor.g = tentativeG
            neighbor.h = heuristic(neighbor, endNode)
            neighbor.f = neighbor.g + neighbor.h
        }
    }
    
    // No path found
    return null
}

/**
 * Get valid neighbors for a node
 */
internal fun getNeighbors(node: PathNode, grid: Indexed<Indexed<Int>>): List<PathNode> {
    val neighbors = mutableListOf<PathNode>()
    val directions = listOf(
        -1 to -1, 0 to -1, 1 to -1,
        -1 to 0,           1 to 0,
        -1 to 1,  0 to 1,  1 to 1
    )
    
    for ((dx, dy) in directions) {
        val newX = node.x + dx
        val newY = node.y + dy
        
        // Check bounds
        if (newX < 0 || newX >= grid[0].size || newY < 0 || newY >= grid.size) {
            continue
        }
        
        // Check if walkable
        if (!isWalkable(grid[newY][newX])) {
            continue
        }
        
        neighbors.add(PathNode(newX, newY))
    }
    
    return neighbors
}

/**
 * Check if a tile is walkable
 */
internal fun isWalkable(tile: Int): Boolean {
    // Add your terrain walkability logic here
    return tile != 0 // Example: 0 represents unwalkable terrain
}

/**
 * Calculate heuristic (Manhattan distance)
 */
internal fun heuristic(a: PathNode, b: PathNode): Int {
    return kotlin.math.abs(a.x - b.x) + kotlin.math.abs(a.y - b.y)
}

/**
 * Reconstruct path from end node
 */
internal fun reconstructPath(node: PathNode): List<Join<Int, Int>> {
    val path = mutableListOf<Join<Int, Int>>()
    var current: PathNode? = node
    
    while (current != null) {
        path.add(0, current.x j current.y)
        current = current.parent
    }
    
    return path
} 