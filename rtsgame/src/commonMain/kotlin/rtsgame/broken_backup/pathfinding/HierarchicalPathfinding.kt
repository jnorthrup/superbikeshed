import kotlin.math.*
package rtsgame.pathfinding
import kotlinx.datetime.*
import kotlin.time.*

import rtsgame.components.*
import borg.trikeshed.lib.*
import kotlin.math.*

/**
 * Hierarchical pathfinding system for massive unit counts
 * Uses cluster-based abstraction and flow fields
 */
class HierarchicalPathfinder(
    val mapWidth: Int,
    val mapHeight: Int,
    val clusterSize: Int = 32
) {
    internal val clustersX = (mapWidth + clusterSize - 1) / clusterSize
    internal val clustersY = (mapHeight + clusterSize - 1) / clusterSize
    internal val clusters = Array(clustersX * clustersY) { Cluster(it) }
    internal val clusterGraph = ClusterGraph()
    
    // Flow field cache per destination cluster
    internal val flowFieldCache = mutableMapOf<Int, FlowField>()
    
    init {
        buildClusterGraph()
    }
    
    /**
     * Request path for multiple units to same destination (efficient)
     */
    fun requestGroupPath(
        starts: Indexed<PositionComponent>,
        destination: PositionComponent
    ): Indexed<Path> {
        val destCluster = getCluster(destination.x.toInt(), destination.y.toInt())
        
        // Get or calculate flow field for destination
        val flowField = flowFieldCache.getOrPut(destCluster.id) {
            calculateFlowField(destination)
        }
        
        // Generate paths using flow field
        return \1 j { \2: Int ->
            val start = starts[i]
            generatePathFromFlowField(start, destination, flowField)
        }
    }
    
    /**
     * Request individual path (uses A* on abstract graph)
     */
    fun requestPath(start: PositionComponent, end: PositionComponent): Path {
        val startCluster = getCluster(start.x.toInt(), start.y.toInt())
        val endCluster = getCluster(end.x.toInt(), end.y.toInt())
        
        if (startCluster == endCluster) {
            // Same cluster - use local pathfinding
            return findLocalPath(start, end)
        }
        
        // Find abstract path through clusters
        val clusterPath = findClusterPath(startCluster, endCluster)
        if (clusterPath.isEmpty()) {
            return Path(listOf()) // No path found
        }
        
        // Refine path through portals
        return refinePath(start, end, clusterPath)
    }
    
    internal fun buildClusterGraph() {
        // Build connections between adjacent clusters
        for (y in 0 until clustersY) {
            for (x in 0 until clustersX) {
                val cluster = clusters[y * clustersX + x]
                
                // Check east connection
                if (x < clustersX - 1) {
                    val eastCluster = clusters[y * clustersX + x + 1]
                    findPortals(cluster, eastCluster, true)
                }
                
                // Check south connection
                if (y < clustersY - 1) {
                    val southCluster = clusters[(y + 1) * clustersX + x]
                    findPortals(cluster, southCluster, false)
                }
            }
        }
    }
    
    internal fun findPortals(cluster1: Cluster, cluster2: Cluster, horizontal: Boolean) {
        val portals = mutableListOf<Portal>()
        var inPortal = false
        var portalStart = 0
        
        val scanLength = if (horizontal) clusterSize else clusterSize
        
        for (i in 0 until scanLength) {
            val passable = if (horizontal) {
                // Check vertical edge between clusters
                isPassable(cluster1.x + clusterSize - 1, cluster1.y + i) &&
                isPassable(cluster2.x, cluster2.y + i)
            } else {
                // Check horizontal edge between clusters
                isPassable(cluster1.x + i, cluster1.y + clusterSize - 1) &&
                isPassable(cluster2.x + i, cluster2.y)
            }
            
            if (passable && !inPortal) {
                inPortal = true
                portalStart = i
            } else if (!passable && inPortal) {
                inPortal = false
                portals.add(Portal(portalStart, i - 1, cluster1.id, cluster2.id))
            }
        }
        
        if (inPortal) {
            portals.add(Portal(portalStart, scanLength - 1, cluster1.id, cluster2.id))
        }
        
        // Add portals to cluster graph
        portals.forEach { portal ->
            clusterGraph.addConnection(cluster1.id, cluster2.id, portal)
        }
    }
    
    internal fun getCluster(x: Int, y: Int): Cluster {
        val cx = x / clusterSize
        val cy = y / clusterSize
        return clusters[cy * clustersX + cx]
    }
    
    internal fun isPassable(x: Int, y: Int): Boolean {
        // Check terrain passability
        return x >= 0 && x < mapWidth && y >= 0 && y < mapHeight
        // In real implementation, check actual terrain data
    }
    
    internal fun findClusterPath(start: Cluster, end: Cluster): List<Cluster> {
        // A* on abstract cluster graph
        val openSet = mutableSetOf(start)
        val cameFrom = mutableMapOf<Cluster, Cluster>()
        val gScore = mutableMapOf(start to 0f)
        val fScore = mutableMapOf(start to heuristic(start, end))
        
        while (openSet.isNotEmpty()) {
            val current = openSet.minByOrNull { fScore[it] ?: Float.MAX_VALUE }!!
            
            if (current == end) {
                return reconstructPath(cameFrom, current)
            }
            
            openSet.remove(current)
            
            clusterGraph.getNeighbors(current.id).forEach { (neighborId, portals) ->
                val neighbor = clusters[neighborId]
                val tentativeGScore = (gScore[current] ?: Float.MAX_VALUE) + 1f // Simplified cost
                
                if (tentativeGScore < (gScore[neighbor] ?: Float.MAX_VALUE)) {
                    cameFrom[neighbor] = current
                    gScore[neighbor] = tentativeGScore
                    fScore[neighbor] = tentativeGScore + heuristic(neighbor, end)
                    openSet.add(neighbor)
                }
            }
        }
        
        return listOf()
    }
    
    internal fun heuristic(a: Cluster, b: Cluster): Float {
        val dx = (a.x - b.x).toFloat()
        val dy = (a.y - b.y).toFloat()
        return sqrt(dx * dx + dy * dy)
    }
    
    internal fun reconstructPath(cameFrom: Map<Cluster, Cluster>, current: Cluster): List<Cluster> {
        val path = mutableListOf(current)
        var node = current
        
        while (cameFrom.containsKey(node)) {
            node = cameFrom[node]!!
            path.add(0, node)
        }
        
        return path
    }
    
    internal fun refinePath(start: PositionComponent, end: PositionComponent, 
                          clusterPath: List<Cluster>): Path {
        val waypoints = mutableListOf<PositionComponent>()
        waypoints.add(start)
        
        // Add portal waypoints
        for (i in 0 until clusterPath.size - 1) {
            val portal = clusterGraph.getBestPortal(clusterPath[i].id, clusterPath[i + 1].id)
            portal?.let {
                waypoints.add(it.getCenter())
            }
        }
        
        waypoints.add(end)
        
        // Smooth path
        return Path(smoothPath(waypoints))
    }
    
    internal fun smoothPath(waypoints: List<PositionComponent>): List<PositionComponent> {
        if (waypoints.size <= 2) return waypoints
        
        val smoothed = mutableListOf<PositionComponent>()
        smoothed.add(waypoints[0])
        
        var current = 0
        while (current < waypoints.size - 1) {
            var farthest = current + 1
            
            // Find farthest reachable waypoint
            for (i in current + 2 until waypoints.size) {
                if (hasLineOfSight(waypoints[current], waypoints[i])) {
                    farthest = i
                } else {
                    break
                }
            }
            
            smoothed.add(waypoints[farthest])
            current = farthest
        }
        
        return smoothed
    }
    
    internal fun hasLineOfSight(a: PositionComponent, b: PositionComponent): Boolean {
        // Bresenham's line algorithm to check obstacles
        val dx = abs(b.x - a.x).toInt()
        val dy = abs(b.y - a.y).toInt()
        val sx = if (a.x < b.x) 1 else -1
        val sy = if (a.y < b.y) 1 else -1
        var err = dx - dy
        
        var x = a.x.toInt()
        var y = a.y.toInt()
        
        while (x != b.x.toInt() || y != b.y.toInt()) {
            if (!isPassable(x, y)) return false
            
            val e2 = 2 * err
            if (e2 > -dy) {
                err -= dy
                x += sx
            }
            if (e2 < dx) {
                err += dx
                y += sy
            }
        }
        
        return true
    }
    
    internal fun findLocalPath(start: PositionComponent, end: PositionComponent): Path {
        // Simple A* for intra-cluster pathfinding
        return Path(listOf(start, end)) // Simplified
    }
    
    internal fun calculateFlowField(destination: PositionComponent): FlowField {
        val field = FlowField(mapWidth, mapHeight)
        
        // Dijkstra flood fill from destination
        val queue = mutableListOf(destination.x.toInt() to destination.y.toInt())
        val distances = Array(mapHeight) { IntArray(mapWidth) { Int.MAX_VALUE } }
        distances[destination.y.toInt()][destination.x.toInt()] = 0
        
        val directions = listOf(
            0 to -1, 1 to -1, 1 to 0, 1 to 1,
            0 to 1, -1 to 1, -1 to 0, -1 to -1
        )
        
        while (queue.isNotEmpty()) {
            val (x, y) = queue.removeAt(0)
            val currentDist = distances[y][x]
            
            directions.forEach { (dx, dy) ->
                val nx = x + dx
                val ny = y + dy
                
                if (nx in 0 until mapWidth && ny in 0 until mapHeight && isPassable(nx, ny)) {
                    val newDist = currentDist + 1
                    
                    if (newDist < distances[ny][nx]) {
                        distances[ny][nx] = newDist
                        queue.add(nx to ny)
                    }
                }
            }
        }
        
        // Calculate flow directions
        for (y in 0 until mapHeight) {
            for (x in 0 until mapWidth) {
                if (!isPassable(x, y)) continue
                
                var bestDir = 0 to 0
                var bestDist = distances[y][x]
                
                directions.forEach { (dx, dy) ->
                    val nx = x + dx
                    val ny = y + dy
                    
                    if (nx in 0 until mapWidth && ny in 0 until mapHeight) {
                        val dist = distances[ny][nx]
                        if (dist < bestDist) {
                            bestDist = dist
                            bestDir = dx to dy
                        }
                    }
                }
                
                field.setFlow(x, y, bestDir.first.toFloat(), bestDir.second.toFloat())
            }
        }
        
        return field
    }
    
    internal fun generatePathFromFlowField(
        start: PositionComponent,
        end: PositionComponent,
        flowField: FlowField
    ): Path {
        val waypoints = mutableListOf<PositionComponent>()
        var current = start
        val maxSteps = 1000
        var steps = 0
        
        while (distance(current, end) > 5f && steps < maxSteps) {
            waypoints.add(current)
            
            val flow = flowField.getFlow(current.x.toInt(), current.y.toInt())
            current = PositionComponent(
                current.x + flow.first * 10f,
                current.y + flow.second * 10f
            )
            
            steps++
        }
        
        waypoints.add(end)
        return Path(waypoints)
    }
    
    internal fun distance(a: PositionComponent, b: PositionComponent): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }
}

/**
 * Cluster representation for hierarchical pathfinding
 */
class Cluster(val id: Int) {
    val x: Int = (id % 32) * 32 // Assuming 32x32 grid of clusters
    val y: Int = (id / 32) * 32
}

/**
 * Portal between clusters
 */
class Portal(
    val start: Int,
    val end: Int,
    val cluster1: Int,
    val cluster2: Int
) {
    fun getCenter(): PositionComponent {
        val mid = (start + end) / 2f
        // Calculate actual world position based on cluster positions
        return PositionComponent(mid * 32f, mid * 32f) // Simplified
    }
}

/**
 * Graph of cluster connections
 */
class ClusterGraph {
    internal val connections = mutableMapOf<Int, MutableMap<Int, MutableList<Portal>>>()
    
    fun addConnection(from: Int, to: Int, portal: Portal) {
        connections.getOrPut(from) { mutableMapOf() }
            .getOrPut(to) { mutableListOf() }
            .add(portal)
        
        connections.getOrPut(to) { mutableMapOf() }
            .getOrPut(from) { mutableListOf() }
            .add(portal)
    }
    
    fun getNeighbors(clusterId: Int): Map<Int, List<Portal>> {
        return connections[clusterId] ?: emptyMap()
    }
    
    fun getBestPortal(from: Int, to: Int): Portal? {
        return connections[from]?.get(to)?.firstOrNull()
    }
}

/**
 * Flow field for efficient group movement
 */
class FlowField(val width: Int, val height: Int) {
    internal val flowX = FloatArray(width * height)
    internal val flowY = FloatArray(width * height)
    
    fun setFlow(x: Int, y: Int, fx: Float, fy: Float) {
        val idx = y * width + x
        flowX[idx] = fx
        flowY[idx] = fy
    }
    
    fun getFlow(x: Int, y: Int): Pair<Float, Float> {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return 0f to 0f
        }
        
        val idx = y * width + x
        return flowX[idx] to flowY[idx]
    }
}

/**
 * Path representation
 */
data class Path(val waypoints: List<PositionComponent>) {
    fun isValid(): Boolean = waypoints.isNotEmpty()
    fun getLength(): Float {
        if (waypoints.size < 2) return 0f
        
        var length = 0f
        for (i in 1 until waypoints.size) {
            val dx = waypoints[i].x - waypoints[i-1].x
            val dy = waypoints[i].y - waypoints[i-1].y
            length += sqrt(dx * dx + dy * dy)
        }
        return length
    }
}