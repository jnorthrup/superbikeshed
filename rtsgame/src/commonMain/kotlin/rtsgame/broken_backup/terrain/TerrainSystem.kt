import kotlin.math.*
package rtsgame.terrain
import kotlinx.datetime.*
import kotlin.time.*

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.j
import rtsgame.config.*
import rtsgame.codec.DeterministicRandom
import kotlin.math.*

/**
 * Terrain generation and management system
 * Direct translation from JS with exact algorithm preservation
 */
class TerrainSystem(internal val random: DeterministicRandom) {
    
    data class TerrainTile(
        val type: Int,
        val elevation: Double,
        val walkable: Boolean = true,
        val buildable: Boolean = true,
        val resourceType: String? = null,
        val resourceAmount: Int = 0
    )
    
    internal val terrain: Array<Array<TerrainTile?>> = Array(GRID_SIZE) { arrayOfNulls(GRID_SIZE) }
    internal val resourceNodes = mutableListOf<ResourceNode>()
    internal var generationSeed: Long = 0
    
    fun generateTerrain(seed: Long) {
        generationSeed = seed
        random.restoreState(seed)
        
        // Phase 1: Base terrain types using noise
        generateBaseTerrain()
        
        // Phase 2: Smooth terrain and add features
        smoothTerrain()
        
        // Phase 3: Place resource nodes
        placeResourceNodes()
        
        // Phase 4: Ensure connectivity
        ensureConnectivity()
        
        println("Terrain generated: ${GRID_SIZE}x${GRID_SIZE} with ${resourceNodes.size} resource nodes")
    }
    
    internal fun generateBaseTerrain() {
        for (x in 0 until GRID_SIZE) {
            for (y in 0 until GRID_SIZE) {
                val noise = generateNoise(x, y, generationSeed)
                val elevation = generateElevation(x, y)
                
                val tile = when {
                    noise < 0.1 -> TerrainTile(
                        type = TERRAIN_TYPES.WATER,
                        elevation = -0.5,
                        walkable = false,
                        buildable = false
                    )
                    noise > 0.85 -> TerrainTile(
                        type = TERRAIN_TYPES.MOUNTAIN,
                        elevation = 1.0,
                        walkable = false,
                        buildable = false
                    )
                    noise > 0.80 -> TerrainTile(
                        type = TERRAIN_TYPES.RESOURCE,
                        elevation = 0.1,
                        walkable = true,
                        buildable = false
                    )
                    else -> TerrainTile(
                        type = TERRAIN_TYPES.LAND,
                        elevation = elevation,
                        walkable = true,
                        buildable = true
                    )
                }
                
                terrain[x][y] = tile
            }
        }
    }
    
    internal fun generateNoise(x: Int, y: Int, seed: Long): Double {
        // Simple deterministic noise matching JS implementation
        var value = seed
        value = ((value + x * 374761393L + y * 668265263L) * 1664525L + 1013904223L) and 0xFFFFFFFFL
        return (value and 0x7FFFFF).toDouble() / 0x800000
    }
    
    internal fun generateElevation(x: Int, y: Int): Double {
        val centerX = GRID_SIZE / 2.0
        val centerY = GRID_SIZE / 2.0
        val distanceFromCenter = sqrt((x - centerX).pow(2) + (y - centerY).pow(2))
        val maxDistance = sqrt(centerX.pow(2) + centerY.pow(2))
        
        // Gentle slope from center to edges
        return 0.5 - (distanceFromCenter / maxDistance) * 0.3
    }
    
    internal fun smoothTerrain() {
        val smoothed = Array(GRID_SIZE) { arrayOfNulls<TerrainTile>() }
        
        for (x in 0 until GRID_SIZE) {
            for (y in 0 until GRID_SIZE) {
                val neighbors = getNeighbors(x, y)
                val waterCount = neighbors.count { it?.type == TERRAIN_TYPES.WATER }
                val mountainCount = neighbors.count { it?.type == TERRAIN_TYPES.MOUNTAIN }
                
                val current = terrain[x][y]!!
                
                // Smooth based on neighbors
                smoothed[x][y] = when {
                    waterCount >= 3 && current.type != TERRAIN_TYPES.WATER -> current.copy(
                        type = TERRAIN_TYPES.WATER,
                        walkable = false,
                        buildable = false
                    )
                    mountainCount >= 3 && current.type != TERRAIN_TYPES.MOUNTAIN -> current.copy(
                        type = TERRAIN_TYPES.MOUNTAIN,
                        walkable = false,
                        buildable = false
                    )
                    else -> current
                }
            }
        }
        
        // Copy smoothed terrain back
        for (x in 0 until GRID_SIZE) {
            for (y in 0 until GRID_SIZE) {
                terrain[x][y] = smoothed[x][y]
            }
        }
    }
    
    internal fun placeResourceNodes() {
        resourceNodes.clear()
        
        for (x in 0 until GRID_SIZE) {
            for (y in 0 until GRID_SIZE) {
                val tile = terrain[x][y]!!
                
                if (tile.type == TERRAIN_TYPES.RESOURCE) {
                    val worldX = x * TILE_SIZE.toDouble()
                    val worldY = y * TILE_SIZE.toDouble()
                    
                    // Determine resource type based on location and random
                    val rand = random.nextDouble()
                    val resourceType = when {
                        rand < 0.4 -> RESOURCE_TYPES.MASS
                        rand < 0.8 -> RESOURCE_TYPES.ENERGY
                        else -> "COMPUTRONIUM" // Rare but valuable
                    }
                    
                    val amount = when (resourceType) {
                        RESOURCE_TYPES.MASS -> 8000 + random.nextInt(4000)
                        RESOURCE_TYPES.ENERGY -> 12000 + random.nextInt(6000)
                        "COMPUTRONIUM" -> 2000 + random.nextInt(3000)
                        else -> 10000
                    }
                    
                    resourceNodes.add(ResourceNode(
                        x = worldX,
                        y = worldY,
                        type = resourceType,
                        amount = amount,
                        maxAmount = amount,
                        occupied = false,
                        gridX = x,
                        gridY = y
                    ))
                    
                    // Update terrain tile
                    terrain[x][y] = tile.copy(
                        resourceType = resourceType,
                        resourceAmount = amount
                    )
                }
            }
        }
    }
    
    internal fun ensureConnectivity() {
        // Flood fill to ensure all land areas are connected
        val visited = Array(GRID_SIZE) { BooleanArray(GRID_SIZE) }
        val landTiles = mutableListOf<Pair<Int, Int>>()
        
        // Find all walkable tiles
        for (x in 0 until GRID_SIZE) {
            for (y in 0 until GRID_SIZE) {
                if (terrain[x][y]?.walkable == true) {
                    landTiles.add(x to y)
                }
            }
        }
        
        if (landTiles.isEmpty()) return
        
        // Start flood fill from first land tile
        val queue = mutableListOf(landTiles.first())
        visited[landTiles.first().first][landTiles.first().second] = true
        var connectedCount = 1
        
        while (queue.isNotEmpty()) {
            val (x, y) = queue.removeAt(0)
            
            for (dx in -1..1) {
                for (dy in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    
                    val nx = x + dx
                    val ny = y + dy
                    
                    if (nx in 0 until GRID_SIZE && ny in 0 until GRID_SIZE &&
                        !visited[nx][ny] && terrain[nx][ny]?.walkable == true) {
                        visited[nx][ny] = true
                        queue.add(nx to ny)
                        connectedCount++
                    }
                }
            }
        }
        
        // If not all land is connected, create bridges
        if (connectedCount < landTiles.size) {
            createBridges(visited, landTiles)
        }
    }
    
    internal fun createBridges(visited: Array<BooleanArray>, landTiles: List<Pair<Int, Int>>) {
        val unconnected = landTiles.filter { (x, y) -> !visited[x][y] }
        
        for ((x, y) in unconnected) {
            // Find nearest connected tile
            val nearest = landTiles.filter { (nx, ny) -> visited[nx][ny] }
                .minByOrNull { (nx, ny) -> abs(x - nx) + abs(y - ny) }
            
            if (nearest != null) {
                // Create path to nearest connected tile
                createPath(x, y, nearest.first, nearest.second)
            }
        }
    }
    
    internal fun createPath(startX: Int, startY: Int, endX: Int, endY: Int) {
        var currentX = startX
        var currentY = startY
        
        while (currentX != endX || currentY != endY) {
            // Simple pathfinding - move towards target
            val dx = (endX - currentX).coerceIn(-1, 1)
            val dy = (endY - currentY).coerceIn(-1, 1)
            
            currentX += dx
            currentY += dy
            
            if (currentX in 0 until GRID_SIZE && currentY in 0 until GRID_SIZE) {
                val current = terrain[currentX][currentY]!!
                if (!current.walkable) {
                    // Convert to walkable land
                    terrain[currentX][currentY] = current.copy(
                        type = TERRAIN_TYPES.LAND,
                        walkable = true,
                        buildable = true,
                        elevation = 0.0
                    )
                }
            }
        }
    }
    
    internal fun getNeighbors(x: Int, y: Int): List<TerrainTile?> {
        val neighbors = mutableListOf<TerrainTile?>()
        
        for (dx in -1..1) {
            for (dy in -1..1) {
                if (dx == 0 && dy == 0) continue
                
                val nx = x + dx
                val ny = y + dy
                
                if (nx in 0 until GRID_SIZE && ny in 0 until GRID_SIZE) {
                    neighbors.add(terrain[nx][ny])
                }
            }
        }
        
        return neighbors
    }
    
    fun getTile(x: Int, y: Int): TerrainTile? {
        return if (x in 0 until GRID_SIZE && y in 0 until GRID_SIZE) {
            terrain[x][y]
        } else null
    }
    
    fun getTileAt(worldX: Double, worldY: Double): TerrainTile? {
        val gridX = (worldX / TILE_SIZE).toInt()
        val gridY = (worldY / TILE_SIZE).toInt()
        return getTile(gridX, gridY)
    }
    
    fun isWalkable(worldX: Double, worldY: Double): Boolean {
        return getTileAt(worldX, worldY)?.walkable ?: false
    }
    
    fun isBuildable(worldX: Double, worldY: Double): Boolean {
        return getTileAt(worldX, worldY)?.buildable ?: false
    }
    
    fun getResourceNodes(): Indexed<ResourceNode> {
        return \1 j { \2: Int -> resourceNodes[i] }
    }
    
    fun getTerrainData(): Indexed<Indexed<TerrainTile?>> {
        return \1 j { \2: Int ->
            \1 j { \2: Int -> terrain[x][y] }
        }
    }
    
    fun findLandPosition(nearX: Double, nearY: Double, radius: Double = 100.0): Pair<Double, Double>? {
        val centerGridX = (nearX / TILE_SIZE).toInt()
        val centerGridY = (nearY / TILE_SIZE).toInt()
        val searchRadius = (radius / TILE_SIZE).toInt()
        
        // Spiral search for nearest land
        for (r in 0..searchRadius) {
            for (angle in 0 until 360 step 15) {
                val radians = Math.toRadians(angle.toDouble())
                val x = centerGridX + (cos(radians) * r).toInt()
                val y = centerGridY + (sin(radians) * r).toInt()
                
                val tile = getTile(x, y)
                if (tile?.walkable == true) {
                    return (x * TILE_SIZE).toDouble() to (y * TILE_SIZE).toDouble()
                }
            }
        }
        
        return null
    }
}

/**
 * Resource node data structure
 */
data class ResourceNode(
    var x: Double,
    var y: Double,
    var type: String,
    var amount: Int,
    var maxAmount: Int,
    var occupied: Boolean,
    val gridX: Int,
    val gridY: Int
) {
    fun extract(amount: Int): Int {
        val extracted = minOf(amount, this.amount)
        this.amount -= extracted
        return extracted
    }
    
    fun getExtractionRate(): Double {
        return when (type) {
            RESOURCE_TYPES.MASS -> MASS_EXTRACTOR_RATE
            RESOURCE_TYPES.ENERGY -> ENERGY_EXTRACTOR_RATE
            "COMPUTRONIUM" -> 0.1
            else -> 0.0
        }
    }
    
    fun isDepletedPercentage(): Double {
        return if (maxAmount > 0) {
            1.0 - (amount.toDouble() / maxAmount)
        } else 1.0
    }
}