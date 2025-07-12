import kotlin.math.*
package rtsgame.physics
import kotlinx.datetime.*
import kotlin.time.*

import rtsgame.core.*
import rtsgame.components.*
import borg.trikeshed.lib.*
import kotlin.math.*

/**
 * Advanced physics system with spatial hashing and broadphase optimization
 */
class AdvancedPhysicsSystem : System {
    internal val spatialHash = SpatialHashGrid(100f)  // 100 unit cell size
    internal val collisionPairs = mutableSetOf<CollisionPair>()
    internal val penetrationSolver = PenetrationSolver()
    
    override fun update(world: ECSWorld, deltaTime: Float) {
        // Clear spatial hash
        spatialHash.clear()
        
        // Insert all physics entities
        insertEntitiesIntoSpatialHash(world)
        
        // Broadphase collision detection
        detectCollisions(world)
        
        // Resolve collisions
        resolveCollisions(world, deltaTime)
        
        // Apply constraints
        applyConstraints(world, deltaTime)
        
        // Update positions (handled by movement system)
    }
    
    internal fun insertEntitiesIntoSpatialHash(world: ECSWorld) {
        world.forEach<PhysicsComponent>(ComponentTypes.PHYSICS) { entity, physics ->
            val pos = world.getComponent<PositionComponent>(entity, ComponentTypes.POSITION) ?: return@forEach
            spatialHash.insert(entity, pos.x, pos.y, 10f)  // 10 unit radius
        }
    }
    
    internal fun detectCollisions(world: ECSWorld) {
        collisionPairs.clear()
        
        spatialHash.forEachCell { entities ->
            // Check collisions within cell
            for (i in 0 until entities.size - 1) {
                for (j in i + 1 until entities.size) {
                    checkCollision(world, entities[i], entities[j])
                }
            }
        }
    }
    
    internal fun checkCollision(world: ECSWorld, a: EntityId, b: EntityId) {
        val posA = world.getComponent<PositionComponent>(a, ComponentTypes.POSITION) ?: return
        val posB = world.getComponent<PositionComponent>(b, ComponentTypes.POSITION) ?: return
        
        val dx = posB.x - posA.x
        val dy = posB.y - posA.y
        val distSq = dx * dx + dy * dy
        
        val radius = 20f  // Combined radius
        if (distSq < radius * radius) {
            collisionPairs.add(CollisionPair(a, b, sqrt(distSq)))
        }
    }
    
    internal fun resolveCollisions(world: ECSWorld, deltaTime: Float) {
        collisionPairs.forEach { pair ->
            val posA = world.getComponent<PositionComponent>(pair.component1(), ComponentTypes.POSITION) ?: return@forEach
            val posB = world.getComponent<PositionComponent>(pair.component2(), ComponentTypes.POSITION) ?: return@forEach
            val physicsA = world.getComponent<PhysicsComponent>(pair.component1(), ComponentTypes.PHYSICS)
            val physicsB = world.getComponent<PhysicsComponent>(pair.component2(), ComponentTypes.PHYSICS)
            
            // Calculate collision normal
            val dx = posB.x - posA.x
            val dy = posB.y - posA.y
            val dist = pair.distance
            
            if (dist > 0) {
                val nx = dx / dist
                val ny = dy / dist
                
                // Separate entities
                val separation = 20f - dist  // Assuming 10 unit radius each
                val separationForce = separation * 0.5f
                
                if (physicsA != null) {
                    physicsA.acceleration.vx -= nx * separationForce / physicsA.mass
                    physicsA.acceleration.vy -= ny * separationForce / physicsA.mass
                }
                
                if (physicsB != null) {
                    physicsB.acceleration.vx += nx * separationForce / physicsB.mass
                    physicsB.acceleration.vy += ny * separationForce / physicsB.mass
                }
            }
        }
    }
    
    internal fun applyConstraints(world: ECSWorld, deltaTime: Float) {
        // Apply world boundaries
        world.forEach<PositionComponent>(ComponentTypes.POSITION) { entity, pos ->
            pos.x = pos.x.coerceIn(0f, 10000f)
            pos.y = pos.y.coerceIn(0f, 10000f)
        }
    }
}

/**
 * Spatial hash grid for broadphase collision detection
 */
class SpatialHashGrid(internal val cellSize: Float) {
    internal val grid = mutableMapOf<Long, MutableList<EntityId>>()
    
    fun insert(entity: EntityId, x: Float, y: Float, radius: Float) {
        val minX = ((x - radius) / cellSize).toInt()
        val maxX = ((x + radius) / cellSize).toInt()
        val minY = ((y - radius) / cellSize).toInt()
        val maxY = ((y + radius) / cellSize).toInt()
        
        for (cy in minY..maxY) {
            for (cx in minX..maxX) {
                val key = getKey(cx, cy)
                grid.getOrPut(key) { mutableListOf() }.add(entity)
            }
        }
    }
    
    fun query(x: Float, y: Float, radius: Float): List<EntityId> {
        val results = mutableSetOf<EntityId>()
        
        val minX = ((x - radius) / cellSize).toInt()
        val maxX = ((x + radius) / cellSize).toInt()
        val minY = ((y - radius) / cellSize).toInt()
        val maxY = ((y + radius) / cellSize).toInt()
        
        for (cy in minY..maxY) {
            for (cx in minX..maxX) {
                val key = getKey(cx, cy)
                grid[key]?.let { results.addAll(it) }
            }
        }
        
        return results.toList()
    }
    
    fun forEachCell(action: (List<EntityId>) -> Unit) {
        grid.values.forEach { entities ->
            if (entities.size > 1) {
                action(entities)
            }
        }
    }
    
    fun clear() {
        grid.clear()
    }
    
    internal fun getKey(x: Int, y: Int): Long {
        return (x.toLong() shl 32) or (y.toLong() and 0xFFFFFFFF)
    }
}

/**
 * Advanced terrain physics with height maps
 */
class TerrainPhysics(
    internal val width: Int,
    internal val height: Int,
    internal val scale: Float = 10f
) {
    internal val heightMap = FloatArray(width * height)
    internal val normalMap = Array(width * height) { Vec3(0f, 1f, 0f) }
    
    init {
        generateTerrain()
        calculateNormals()
    }
    
    internal fun generateTerrain() {
        // Generate procedural terrain using diamond-square algorithm
        diamondSquare(0, 0, width - 1, height - 1, 100f)
        
        // Smooth terrain
        repeat(3) {
            smoothTerrain()
        }
    }
    
    internal fun diamondSquare(x1: Int, y1: Int, x2: Int, y2: Int, roughness: Float) {
        if (x2 - x1 <= 1 || y2 - y1 <= 1) return
        
        val midX = (x1 + x2) / 2
        val midY = (y1 + y2) / 2
        
        // Diamond step
        val avg = (getHeight(x1, y1) + getHeight(x2, y1) + 
                  getHeight(x1, y2) + getHeight(x2, y2)) / 4f
        setHeight(midX, midY, avg + (Random.nextFloat() - 0.5f) * roughness)
        
        // Square step
        setHeight(midX, y1, (getHeight(x1, y1) + getHeight(x2, y1)) / 2f + 
                            (Random.nextFloat() - 0.5f) * roughness * 0.5f)
        setHeight(midX, y2, (getHeight(x1, y2) + getHeight(x2, y2)) / 2f + 
                            (Random.nextFloat() - 0.5f) * roughness * 0.5f)
        setHeight(x1, midY, (getHeight(x1, y1) + getHeight(x1, y2)) / 2f + 
                            (Random.nextFloat() - 0.5f) * roughness * 0.5f)
        setHeight(x2, midY, (getHeight(x2, y1) + getHeight(x2, y2)) / 2f + 
                            (Random.nextFloat() - 0.5f) * roughness * 0.5f)
        
        // Recurse
        val newRoughness = roughness * 0.5f
        diamondSquare(x1, y1, midX, midY, newRoughness)
        diamondSquare(midX, y1, x2, midY, newRoughness)
        diamondSquare(x1, midY, midX, y2, newRoughness)
        diamondSquare(midX, midY, x2, y2, newRoughness)
    }
    
    internal fun smoothTerrain() {
        val smoothed = FloatArray(width * height)
        
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var sum = 0f
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        sum += getHeight(x + dx, y + dy)
                    }
                }
                smoothed[y * width + x] = sum / 9f
            }
        }
        
        System.arraycopy(smoothed, 0, heightMap, 0, heightMap.size)
    }
    
    internal fun calculateNormals() {
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val hL = getHeight(x - 1, y)
                val hR = getHeight(x + 1, y)
                val hD = getHeight(x, y - 1)
                val hU = getHeight(x, y + 1)
                
                val normal = Vec3(
                    (hL - hR) / (2f * scale),
                    2f,
                    (hD - hU) / (2f * scale)
                ).normalized()
                
                normalMap[y * width + x] = normal
            }
        }
    }
    
    fun getHeight(x: Int, y: Int): Float {
        if (x < 0 || x >= width || y < 0 || y >= height) return 0f
        return heightMap[y * width + x]
    }
    
    fun getHeightAt(worldX: Float, worldY: Float): Float {
        val x = (worldX / scale).toInt()
        val y = (worldY / scale).toInt()
        
        if (x < 0 || x >= width - 1 || y < 0 || y >= height - 1) return 0f
        
        // Bilinear interpolation
        val fx = worldX / scale - x
        val fy = worldY / scale - y
        
        val h00 = getHeight(x, y)
        val h10 = getHeight(x + 1, y)
        val h01 = getHeight(x, y + 1)
        val h11 = getHeight(x + 1, y + 1)
        
        val h0 = h00 * (1 - fx) + h10 * fx
        val h1 = h01 * (1 - fx) + h11 * fx
        
        return h0 * (1 - fy) + h1 * fy
    }
    
    fun getNormalAt(worldX: Float, worldY: Float): Vec3 {
        val x = (worldX / scale).toInt().coerceIn(0, width - 1)
        val y = (worldY / scale).toInt().coerceIn(0, height - 1)
        return normalMap[y * width + x]
    }
    
    fun getSlopeAt(worldX: Float, worldY: Float): Float {
        val normal = getNormalAt(worldX, worldY)
        return acos(normal.y).toDegrees()
    }
    
    internal fun setHeight(x: Int, y: Int, height: Float) {
        if (x >= 0 && x < width && y >= 0 && y < this.height) {
            heightMap[y * width + x] = height
        }
    }
}

/**
 * Fluid simulation for water and lava
 */
class FluidSimulation(
    internal val gridSize: Int = 256,
    internal val cellSize: Float = 4f
) {
    internal val velocityX = FloatArray(gridSize * gridSize)
    internal val velocityY = FloatArray(gridSize * gridSize)
    internal val pressure = FloatArray(gridSize * gridSize)
    internal val density = FloatArray(gridSize * gridSize)
    
    internal val viscosity = 0.001f
    internal val diffusion = 0.0001f
    
    fun update(deltaTime: Float) {
        // Add forces (gravity, wind, etc.)
        addForces(deltaTime)
        
        // Diffuse velocity
        diffuse(velocityX, viscosity, deltaTime)
        diffuse(velocityY, viscosity, deltaTime)
        
        // Project to maintain incompressibility
        project()
        
        // Advect velocity
        advect(velocityX, velocityX, velocityY, deltaTime)
        advect(velocityY, velocityX, velocityY, deltaTime)
        
        // Project again
        project()
        
        // Advect density
        advect(density, velocityX, velocityY, deltaTime)
        diffuse(density, diffusion, deltaTime)
    }
    
    internal fun addForces(dt: Float) {
        // Add gravity
        for (i in velocityY.indices) {
            velocityY[i] += -9.81f * dt * density[i]
        }
    }
    
    internal fun diffuse(field: FloatArray, diff: Float, dt: Float) {
        val a = dt * diff * gridSize * gridSize
        
        repeat(20) {  // Gauss-Seidel iterations
            for (y in 1 until gridSize - 1) {
                for (x in 1 until gridSize - 1) {
                    val idx = y * gridSize + x
                    field[idx] = (field[idx] + a * (
                        field[idx - 1] + field[idx + 1] +
                        field[idx - gridSize] + field[idx + gridSize]
                    )) / (1 + 4 * a)
                }
            }
        }
    }
    
    internal fun project() {
        // Calculate divergence
        for (y in 1 until gridSize - 1) {
            for (x in 1 until gridSize - 1) {
                val idx = y * gridSize + x
                val div = -0.5f * (
                    velocityX[idx + 1] - velocityX[idx - 1] +
                    velocityY[idx + gridSize] - velocityY[idx - gridSize]
                )
                pressure[idx] = 0f
                velocityX[idx] = div  // Temporarily store divergence
            }
        }
        
        // Solve pressure
        repeat(20) {
            for (y in 1 until gridSize - 1) {
                for (x in 1 until gridSize - 1) {
                    val idx = y * gridSize + x
                    pressure[idx] = (velocityX[idx] + 
                        pressure[idx - 1] + pressure[idx + 1] +
                        pressure[idx - gridSize] + pressure[idx + gridSize]
                    ) / 4f
                }
            }
        }
        
        // Subtract pressure gradient
        for (y in 1 until gridSize - 1) {
            for (x in 1 until gridSize - 1) {
                val idx = y * gridSize + x
                velocityX[idx] -= 0.5f * (pressure[idx + 1] - pressure[idx - 1])
                velocityY[idx] -= 0.5f * (pressure[idx + gridSize] - pressure[idx - gridSize])
            }
        }
    }
    
    internal fun advect(field: FloatArray, vx: FloatArray, vy: FloatArray, dt: Float) {
        val dt0 = dt * gridSize
        val temp = FloatArray(field.size)
        
        for (y in 1 until gridSize - 1) {
            for (x in 1 until gridSize - 1) {
                val idx = y * gridSize + x
                
                // Trace particle back in time
                var px = x - dt0 * vx[idx]
                var py = y - dt0 * vy[idx]
                
                // Clamp to grid
                px = px.coerceIn(0.5f, gridSize - 1.5f)
                py = py.coerceIn(0.5f, gridSize - 1.5f)
                
                // Bilinear interpolation
                val x0 = px.toInt()
                val x1 = x0 + 1
                val y0 = py.toInt()
                val y1 = y0 + 1
                
                val sx = px - x0
                val sy = py - y0
                
                temp[idx] = (1 - sx) * (1 - sy) * field[y0 * gridSize + x0] +
                           sx * (1 - sy) * field[y0 * gridSize + x1] +
                           (1 - sx) * sy * field[y1 * gridSize + x0] +
                           sx * sy * field[y1 * gridSize + x1]
            }
        }
        
        System.arraycopy(temp, 0, field, 0, field.size)
    }
    
    fun addDensity(x: Int, y: Int, amount: Float) {
        if (x >= 0 && x < gridSize && y >= 0 && y < gridSize) {
            density[y * gridSize + x] += amount
        }
    }
    
    fun addVelocity(x: Int, y: Int, vx: Float, vy: Float) {
        if (x >= 0 && x < gridSize && y >= 0 && y < gridSize) {
            val idx = y * gridSize + x
            velocityX[idx] += vx
            velocityY[idx] += vy
        }
    }
    
    fun getVelocityAt(worldX: Float, worldY: Float): Pair<Float, Float> {
        val x = (worldX / cellSize).toInt()
        val y = (worldY / cellSize).toInt()
        
        if (x < 0 || x >= gridSize || y < 0 || y >= gridSize) {
            return 0f to 0f
        }
        
        val idx = y * gridSize + x
        return velocityX[idx] to velocityY[idx]
    }
}

// Supporting classes
data class CollisionPair(val a: EntityId, val b: EntityId, val distance: Float)

class PenetrationSolver {
    fun solve(contacts: List<Contact>) {
        // Solve penetration constraints
    }
}

data class Contact(
    val entityA: EntityId,
    val entityB: EntityId,
    val normal: Vec3,
    val penetration: Float,
    val position: Vec3
)

fun Vec3.normalized(): Vec3 {
    val len = sqrt(x * x + y * y + z * z)
    return if (len > 0) Vec3(x / len, y / len, z / len) else this
}

fun Float.toDegrees(): Float = this * 180f / PI.toFloat()