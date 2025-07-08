import kotlin.math.*
package rtsgame.procedural
import kotlinx.datetime.*
import kotlin.time.*

import rtsgame.core.*
import rtsgame.components.*
import borg.trikeshed.lib.*
import kotlin.math.*
import kotlin.random.Random

/**
 * Advanced procedural map generation with multiple biomes and features
 */
class ProceduralMapGenerator(
    val width: Int = 2048,
    val height: Int = 2048,
    val seed: Long = Clock.System.now().toEpochMilliseconds()
) {
    internal val random = Random(seed)
    internal val heightMap = FloatArray(width * height)
    internal val moistureMap = FloatArray(width * height)
    internal val temperatureMap = FloatArray(width * height)
    internal val biomeMap = Array(width * height) { Biome.PLAINS }
    
    // Noise generators
    internal val elevationNoise = PerlinNoise(random.nextLong())
    internal val moistureNoise = PerlinNoise(random.nextLong())
    internal val temperatureNoise = PerlinNoise(random.nextLong())
    
    fun generate(): ProceduralMap {
        // Generate base maps
        generateHeightMap()
        generateMoistureMap()
        generateTemperatureMap()
        
        // Determine biomes
        assignBiomes()
        
        // Place features
        val features = placeFeatures()
        
        // Generate spawn points
        val spawnPoints = generateSpawnPoints()
        
        return ProceduralMap(
            width = width,
            height = height,
            heightMap = heightMap,
            biomeMap = biomeMap,
            features = features,
            spawnPoints = spawnPoints
        )
    }
    
    internal fun generateHeightMap() {
        // Multi-octave Perlin noise for realistic terrain
        for (y in 0 until height) {
            for (x in 0 until width) {
                var elevation = 0f
                var amplitude = 1f
                var frequency = 0.005f
                
                // 6 octaves of noise
                for (i in 0 until 6) {
                    elevation += elevationNoise.noise(
                        x * frequency,
                        y * frequency
                    ) * amplitude
                    
                    amplitude *= 0.5f
                    frequency *= 2f
                }
                
                // Island mask - creates landmasses surrounded by water
                val centerX = width / 2f
                val centerY = height / 2f
                val maxDist = minOf(width, height) * 0.4f
                
                val distX = abs(x - centerX)
                val distY = abs(y - centerY)
                val dist = sqrt(distX * distX + distY * distY)
                
                val islandMask = 1f - (dist / maxDist).coerceIn(0f, 1f).pow(2)
                
                elevation *= islandMask
                
                // Normalize to 0-1
                heightMap[y * width + x] = (elevation + 1f) / 2f
            }
        }
        
        // Erosion simulation
        simulateErosion(100)
    }
    
    internal fun simulateErosion(iterations: Int) {
        repeat(iterations) {
            // Simplified hydraulic erosion
            val dropX = random.nextInt(width)
            val dropY = random.nextInt(height)
            
            var x = dropX.toFloat()
            var y = dropY.toFloat()
            var sediment = 0f
            var water = 1f
            
            for (step in 0 until 30) {
                val ix = x.toInt()
                val iy = y.toInt()
                
                if (ix < 1 || ix >= width - 1 || iy < 1 || iy >= height - 1) break
                
                // Find flow direction
                val h = getHeight(ix, iy)
                var lowestX = ix
                var lowestY = iy
                var lowestH = h
                
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        
                        val nh = getHeight(ix + dx, iy + dy)
                        if (nh < lowestH) {
                            lowestH = nh
                            lowestX = ix + dx
                            lowestY = iy + dy
                        }
                    }
                }
                
                // Erode
                val erosionRate = 0.01f * water
                val eroded = minOf(erosionRate, h - lowestH)
                
                setHeight(ix, iy, h - eroded)
                sediment += eroded
                
                // Flow
                x = lowestX.toFloat()
                y = lowestY.toFloat()
                water *= 0.9f
                
                // Deposit sediment
                val depositionRate = sediment * 0.1f
                setHeight(lowestX, lowestY, getHeight(lowestX, lowestY) + depositionRate)
                sediment -= depositionRate
            }
        }
    }
    
    internal fun generateMoistureMap() {
        // Distance from water affects moisture
        for (y in 0 until height) {
            for (x in 0 until width) {
                val elevation = heightMap[y * width + x]
                
                // Ocean and lakes provide moisture
                var moisture = if (elevation < 0.3f) 1f else 0f
                
                // Add noise
                moisture += moistureNoise.noise(x * 0.01f, y * 0.01f) * 0.5f
                
                // Altitude affects moisture
                moisture -= (elevation - 0.3f).coerceAtLeast(0f) * 2f
                
                moistureMap[y * width + x] = moisture.coerceIn(0f, 1f)
            }
        }
        
        // Blur to spread moisture
        blur(moistureMap, 5)
    }
    
    internal fun generateTemperatureMap() {
        for (y in 0 until height) {
            for (x in 0 until width) {
                // Latitude-based temperature
                val latitude = abs(y - height / 2f) / (height / 2f)
                var temp = 1f - latitude * 0.8f
                
                // Altitude affects temperature
                val elevation = heightMap[y * width + x]
                temp -= (elevation - 0.3f).coerceAtLeast(0f) * 0.5f
                
                // Add noise
                temp += temperatureNoise.noise(x * 0.008f, y * 0.008f) * 0.3f
                
                temperatureMap[y * width + x] = temp.coerceIn(0f, 1f)
            }
        }
    }
    
    internal fun assignBiomes() {
        for (i in biomeMap.indices) {
            val elevation = heightMap[i]
            val moisture = moistureMap[i]
            val temperature = temperatureMap[i]
            
            biomeMap[i] = when {
                elevation < 0.15f -> Biome.DEEP_OCEAN
                elevation < 0.25f -> Biome.OCEAN
                elevation < 0.3f -> Biome.BEACH
                elevation > 0.8f -> {
                    if (temperature < 0.3f) Biome.SNOW
                    else Biome.MOUNTAIN
                }
                temperature < 0.2f -> Biome.TUNDRA
                moisture < 0.2f -> {
                    if (temperature > 0.7f) Biome.DESERT
                    else Biome.PLAINS
                }
                moisture > 0.7f -> {
                    if (temperature > 0.7f) Biome.JUNGLE
                    else Biome.FOREST
                }
                else -> Biome.PLAINS
            }
        }
    }
    
    internal fun placeFeatures(): List<MapFeature> {
        val features = mutableListOf<MapFeature>()
        
        // Place resources
        placeResources(features)
        
        // Place strategic points
        placeStrategicPoints(features)
        
        // Place obstacles
        placeObstacles(features)
        
        return features
    }
    
    internal fun placeResources(features: MutableList<MapFeature>) {
        // Minerals in mountains
        for (y in 0 until height step 32) {
            for (x in 0 until width step 32) {
                val biome = getBiome(x, y)
                val elevation = getHeight(x, y)
                
                when (biome) {
                    Biome.MOUNTAIN -> {
                        if (random.nextFloat() < 0.3f) {
                            features.add(MapFeature.ResourceNode(
                                x.toFloat(), y.toFloat(),
                                ResourceType.RARE_MINERALS,
                                5000
                            ))
                        }
                    }
                    Biome.PLAINS, Biome.FOREST -> {
                        if (random.nextFloat() < 0.2f) {
                            features.add(MapFeature.ResourceNode(
                                x.toFloat(), y.toFloat(),
                                ResourceType.MINERALS,
                                3000
                            ))
                        }
                    }
                    Biome.JUNGLE -> {
                        if (random.nextFloat() < 0.15f) {
                            features.add(MapFeature.ResourceNode(
                                x.toFloat(), y.toFloat(),
                                ResourceType.GAS,
                                2000
                            ))
                        }
                    }
                    else -> {}
                }
            }
        }
    }
    
    internal fun placeStrategicPoints(features: MutableList<MapFeature>) {
        // Find chokepoints using pathfinding
        val chokepoints = findChokepoints()
        
        chokepoints.forEach { point ->
            features.add(MapFeature.StrategicPoint(
                point.x, point.y,
                StrategicPointType.CHOKEPOINT
            ))
        }
        
        // High ground positions
        for (y in 16 until height - 16 step 64) {
            for (x in 16 until width - 16 step 64) {
                if (isHighGround(x, y)) {
                    features.add(MapFeature.StrategicPoint(
                        x.toFloat(), y.toFloat(),
                        StrategicPointType.HIGH_GROUND
                    ))
                }
            }
        }
    }
    
    internal fun placeObstacles(features: MutableList<MapFeature>) {
        // Rock formations
        for (i in 0 until 50) {
            val x = random.nextInt(width).toFloat()
            val y = random.nextInt(height).toFloat()
            val radius = random.nextFloat() * 30 + 20
            
            features.add(MapFeature.Obstacle(x, y, radius, ObstacleType.ROCKS))
        }
    }
    
    internal fun generateSpawnPoints(): List<SpawnPoint> {
        val spawnPoints = mutableListOf<SpawnPoint>()
        val playerCount = 4
        
        // Find suitable spawn locations
        val candidates = mutableListOf<Pair<Int, Int>>()
        
        for (y in 100 until height - 100 step 20) {
            for (x in 100 until width - 100 step 20) {
                if (isValidSpawnLocation(x, y)) {
                    candidates.add(x to y)
                }
            }
        }
        
        // Select spawn points with maximum distance between them
        if (candidates.size >= playerCount) {
            val selected = selectSpawnPoints(candidates, playerCount)
            
            selected.forEachIndexed { index, (x, y) ->
                spawnPoints.add(SpawnPoint(
                    playerId = index,
                    x = x.toFloat(),
                    y = y.toFloat()
                ))
            }
        }
        
        return spawnPoints
    }
    
    internal fun isValidSpawnLocation(x: Int, y: Int): Boolean {
        val biome = getBiome(x, y)
        val elevation = getHeight(x, y)
        
        // Must be on land, not mountain
        if (elevation < 0.3f || elevation > 0.7f) return false
        
        // Must be buildable biome
        if (biome !in listOf(Biome.PLAINS, Biome.FOREST)) return false
        
        // Check surrounding area is also valid
        for (dy in -50..50 step 10) {
            for (dx in -50..50 step 10) {
                val nx = x + dx
                val ny = y + dy
                
                if (nx < 0 || nx >= width || ny < 0 || ny >= height) return false
                
                val nearbyElevation = getHeight(nx, ny)
                if (nearbyElevation < 0.25f) return false
            }
        }
        
        return true
    }
    
    internal fun selectSpawnPoints(
        candidates: List<Pair<Int, Int>>,
        count: Int
    ): List<Pair<Int, Int>> {
        // Greedy algorithm to maximize minimum distance
        val selected = mutableListOf<Pair<Int, Int>>()
        
        // Start with random point
        selected.add(candidates[random.nextInt(candidates.size)])
        
        while (selected.size < count) {
            var bestCandidate: Pair<Int, Int>? = null
            var bestMinDist = 0f
            
            candidates.forEach { candidate ->
                if (candidate !in selected) {
                    val minDist = selected.minOf { spawn ->
                        val dx = (candidate.first - spawn.first).toFloat()
                        val dy = (candidate.second - spawn.second).toFloat()
                        sqrt(dx * dx + dy * dy)
                    }
                    
                    if (minDist > bestMinDist) {
                        bestMinDist = minDist
                        bestCandidate = candidate
                    }
                }
            }
            
            bestCandidate?.let { selected.add(it) }
        }
        
        return selected
    }
    
    internal fun findChokepoints(): List<Vec2> {
        // Simplified chokepoint detection
        val chokepoints = mutableListOf<Vec2>()
        
        for (y in 50 until height - 50 step 25) {
            for (x in 50 until width - 50 step 25) {
                if (isChokepoint(x, y)) {
                    chokepoints.add(Vec2(x.toFloat(), y.toFloat()))
                }
            }
        }
        
        return chokepoints
    }
    
    internal fun isChokepoint(x: Int, y: Int): Boolean {
        // Check if position forms a narrow passage
        val passable = isPassable(x, y)
        if (!passable) return false
        
        // Check horizontal and vertical passages
        var horizontalBlocked = 0
        var verticalBlocked = 0
        
        for (d in -20..20 step 5) {
            if (!isPassable(x + d, y)) horizontalBlocked++
            if (!isPassable(x, y + d)) verticalBlocked++
        }
        
        return (horizontalBlocked > 6 && verticalBlocked < 3) ||
               (verticalBlocked > 6 && horizontalBlocked < 3)
    }
    
    internal fun isHighGround(x: Int, y: Int): Boolean {
        val centerHeight = getHeight(x, y)
        if (centerHeight < 0.5f) return false
        
        var higherCount = 0
        
        for (dy in -10..10 step 5) {
            for (dx in -10..10 step 5) {
                if (dx == 0 && dy == 0) continue
                
                if (getHeight(x + dx, y + dy) > centerHeight) {
                    higherCount++
                }
            }
        }
        
        return higherCount < 3
    }
    
    internal fun blur(map: FloatArray, radius: Int) {
        val temp = FloatArray(map.size)
        
        for (y in radius until height - radius) {
            for (x in radius until width - radius) {
                var sum = 0f
                var count = 0
                
                for (dy in -radius..radius) {
                    for (dx in -radius..radius) {
                        sum += map[(y + dy) * width + (x + dx)]
                        count++
                    }
                }
                
                temp[y * width + x] = sum / count
            }
        }
        
        System.arraycopy(temp, 0, map, 0, map.size)
    }
    
    internal fun getHeight(x: Int, y: Int): Float {
        if (x < 0 || x >= width || y < 0 || y >= height) return 0f
        return heightMap[y * width + x]
    }
    
    internal fun setHeight(x: Int, y: Int, value: Float) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            heightMap[y * width + x] = value.coerceIn(0f, 1f)
        }
    }
    
    internal fun getBiome(x: Int, y: Int): Biome {
        if (x < 0 || x >= width || y < 0 || y >= height) return Biome.OCEAN
        return biomeMap[y * width + x]
    }
    
    internal fun isPassable(x: Int, y: Int): Boolean {
        val elevation = getHeight(x, y)
        return elevation > 0.3f && elevation < 0.8f
    }
}

/**
 * Perlin noise generator
 */
class PerlinNoise(seed: Long) {
    internal val permutation = IntArray(512)
    internal val random = Random(seed)
    
    init {
        val p = IntArray(256) { it }
        p.shuffle(random)
        
        for (i in 0 until 256) {
            permutation[i] = p[i]
            permutation[i + 256] = p[i]
        }
    }
    
    fun noise(x: Float, y: Float): Float {
        val X = x.toInt() and 255
        val Y = y.toInt() and 255
        
        val xf = x - x.toInt()
        val yf = y - y.toInt()
        
        val u = fade(xf)
        val v = fade(yf)
        
        val a = permutation[X] + Y
        val aa = permutation[a]
        val ab = permutation[a + 1]
        val b = permutation[X + 1] + Y
        val ba = permutation[b]
        val bb = permutation[b + 1]
        
        return lerp(v,
            lerp(u, grad(permutation[aa], xf, yf),
                 grad(permutation[ba], xf - 1, yf)),
            lerp(u, grad(permutation[ab], xf, yf - 1),
                 grad(permutation[bb], xf - 1, yf - 1))
        )
    }
    
    internal fun fade(t: Float): Float {
        return t * t * t * (t * (t * 6 - 15) + 10)
    }
    
    internal fun lerp(t: Float, a: Float, b: Float): Float {
        return a + t * (b - a)
    }
    
    internal fun grad(hash: Int, x: Float, y: Float): Float {
        val h = hash and 3
        val u = if (h < 2) x else y
        val v = if (h < 2) y else x
        return (if (h and 1 == 0) u else -u) + (if (h and 2 == 0) v else -v)
    }
}

// Data structures
enum class Biome {
    DEEP_OCEAN, OCEAN, BEACH, PLAINS, FOREST, JUNGLE, DESERT, TUNDRA, MOUNTAIN, SNOW
}

sealed class MapFeature {
    data class ResourceNode(
        val x: Float,
        val y: Float,
        val type: ResourceType,
        val amount: Int
    ) : MapFeature()
    
    data class StrategicPoint(
        val x: Float,
        val y: Float,
        val type: StrategicPointType
    ) : MapFeature()
    
    data class Obstacle(
        val x: Float,
        val y: Float,
        val radius: Float,
        val type: ObstacleType
    ) : MapFeature()
}

enum class StrategicPointType {
    CHOKEPOINT, HIGH_GROUND, RESOURCE_CLUSTER
}

enum class ObstacleType {
    ROCKS, TREES, RUINS
}

data class SpawnPoint(
    val playerId: Int,
    val x: Float,
    val y: Float
)

data class ProceduralMap(
    val width: Int,
    val height: Int,
    val heightMap: FloatArray,
    val biomeMap: Array<Biome>,
    val features: List<MapFeature>,
    val spawnPoints: List<SpawnPoint>
)

data class Vec2(val x: Float, val y: Float)