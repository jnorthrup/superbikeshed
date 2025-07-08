import kotlin.math.*
package rtsgame.core
import kotlinx.datetime.*
import kotlin.time.*

import borg.trikeshed.lib.*
import rtsgame.entities.*
import rtsgame.config.*

/**
 * Unified entity management system using TrikeShed patterns
 * Manages all game entities with efficient data structures
 */
class EntityManager {
    // Entity collections
    val units = mutableListOf<GameUnit>()
    val buildings = mutableListOf<Building>()
    val projectiles = mutableListOf<Projectile>()
    val effects = mutableListOf<Effect>()
    val captions = mutableListOf<Any>() // Caption type when ported
    
    // ID generation
    var nextEntityId: Int = 1
    
    // Spatial indexing for performance
    internal val spatialIndex = SpatialIndex()
    
    fun addUnit(unit: GameUnit) {
        units.add(unit)
        spatialIndex.addEntity(unit.id, unit.x, unit.y, EntityType.UNIT)
    }
    
    fun addBuilding(building: Building) {
        buildings.add(building)
        spatialIndex.addEntity(building.id, building.x, building.y, EntityType.BUILDING)
    }
    
    fun addProjectile(projectile: Projectile) {
        projectiles.add(projectile)
        spatialIndex.addEntity(projectile.hashCode(), projectile.x, projectile.y, EntityType.PROJECTILE)
    }
    
    fun addEffect(effect: Effect) {
        effects.add(effect)
    }
    
    fun removeUnit(unit: GameUnit) {
        units.remove(unit)
        spatialIndex.removeEntity(unit.id)
    }
    
    fun removeBuilding(building: Building) {
        buildings.remove(building)
        spatialIndex.removeEntity(building.id)
    }
    
    fun removeProjectile(projectile: Projectile) {
        projectiles.remove(projectile)
        spatialIndex.removeEntity(projectile.hashCode())
    }
    
    fun removeEffect(effect: Effect) {
        effects.remove(effect)
    }
    
    fun update(simulation: rtsgame.core.Simulation, deltaTime: Double) {
        // Update units
        units.removeAll { unit ->
            unit.update(simulation, deltaTime)
            
            // Update spatial index
            spatialIndex.updateEntity(unit.id, unit.x, unit.y)
            
            // Remove if dead
            if (unit.isDead) {
                spatialIndex.removeEntity(unit.id)
                true
            } else false
        }
        
        // Update buildings
        buildings.removeAll { building ->
            building.update(simulation, deltaTime)
            
            // Remove if destroyed
            if (building.hp <= 0) {
                spatialIndex.removeEntity(building.id)
                true
            } else false
        }
        
        // Update projectiles
        projectiles.removeAll { projectile ->
            projectile.update(simulation, deltaTime)
            
            // Update spatial index
            spatialIndex.updateEntity(projectile.hashCode(), projectile.x, projectile.y)
            
            // Remove if finished
            if (projectile.shouldDestroy) {
                spatialIndex.removeEntity(projectile.hashCode())
                true
            } else false
        }
        
        // Update effects
        effects.removeAll { effect ->
            effect.update()
            effect.life <= 0
        }
    }
    
    fun findUnitsInRange(x: Double, y: Double, range: Double): List<GameUnit> {
        val entityIds = spatialIndex.getEntitiesInRange(x, y, range, EntityType.UNIT)
        return units.filter { it.id in entityIds }
    }
    
    fun findBuildingsInRange(x: Double, y: Double, range: Double): List<Building> {
        val entityIds = spatialIndex.getEntitiesInRange(x, y, range, EntityType.BUILDING)
        return buildings.filter { it.id in entityIds }
    }
    
    fun findUnitById(id: Int): GameUnit? = units.find { it.id == id }
    fun findBuildingById(id: Int): Building? = buildings.find { it.id == id }
    
    fun getUnitsByTeam(team: String): List<GameUnit> = units.filter { it.team == team && !it.isDead }
    fun getBuildingsByTeam(team: String): List<Building> = buildings.filter { it.team == team && it.hp > 0 }
    
    fun getEntityCount(): Int = units.size + buildings.size + projectiles.size + effects.size
    
    fun clear() {
        units.clear()
        buildings.clear()
        projectiles.clear()
        effects.clear()
        spatialIndex.clear()
        nextEntityId = 1
    }
    
    fun getAllEntitiesForSync(): List<EntityState> {
        val entities = mutableListOf<EntityState>()
        
        units.forEach { unit ->
            entities.add(unit.toEntityState())
        }
        
        buildings.forEach { building ->
            entities.add(building.toEntityState())
        }
        
        return entities
    }
}

/**
 * Spatial indexing for efficient range queries
 */
internal class SpatialIndex {
    internal data class SpatialEntity(
        val id: Int,
        var x: Double,
        var y: Double,
        val type: EntityType
    )
    
    internal val entities = mutableMapOf<Int, SpatialEntity>()
    internal val gridSize = 100.0 // Grid cell size
    internal val grid = mutableMapOf<Pair<Int, Int>, MutableList<Int>>()
    
    fun addEntity(id: Int, x: Double, y: Double, type: EntityType) {
        val entity = SpatialEntity(id, x, y, type)
        entities[id] = entity
        addToGrid(id, x, y)
    }
    
    fun removeEntity(id: Int) {
        val entity = entities.remove(id)
        if (entity != null) {
            removeFromGrid(id, entity.x, entity.y)
        }
    }
    
    fun updateEntity(id: Int, newX: Double, newY: Double) {
        val entity = entities[id]
        if (entity != null) {
            removeFromGrid(id, entity.x, entity.y)
            entity.x = newX
            entity.y = newY
            addToGrid(id, newX, newY)
        }
    }
    
    fun getEntitiesInRange(x: Double, y: Double, range: Double, type: EntityType): Set<Int> {
        val result = mutableSetOf<Int>()
        val rangeSq = range * range
        
        // Calculate grid bounds to check
        val minGridX = ((x - range) / gridSize).toInt()
        val maxGridX = ((x + range) / gridSize).toInt()
        val minGridY = ((y - range) / gridSize).toInt()
        val maxGridY = ((y + range) / gridSize).toInt()
        
        for (gx in minGridX..maxGridX) {
            for (gy in minGridY..maxGridY) {
                val cellEntities = grid[gx to gy] ?: continue
                
                for (entityId in cellEntities) {
                    val entity = entities[entityId]
                    if (entity != null && entity.type == type) {
                        val distSq = (entity.x - x) * (entity.x - x) + (entity.y - y) * (entity.y - y)
                        if (distSq <= rangeSq) {
                            result.add(entityId)
                        }
                    }
                }
            }
        }
        
        return result
    }
    
    internal fun addToGrid(id: Int, x: Double, y: Double) {
        val gridX = (x / gridSize).toInt()
        val gridY = (y / gridSize).toInt()
        val cell = grid.getOrPut(gridX to gridY) { mutableListOf() }
        cell.add(id)
    }
    
    internal fun removeFromGrid(id: Int, x: Double, y: Double) {
        val gridX = (x / gridSize).toInt()
        val gridY = (y / gridSize).toInt()
        val cell = grid[gridX to gridY]
        cell?.remove(id)
        
        // Clean up empty cells
        if (cell?.isEmpty() == true) {
            grid.remove(gridX to gridY)
        }
    }
    
    fun clear() {
        entities.clear()
        grid.clear()
    }
}

enum class EntityType {
    UNIT, BUILDING, PROJECTILE
}