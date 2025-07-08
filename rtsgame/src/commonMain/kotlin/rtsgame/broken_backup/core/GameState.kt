import kotlin.math.*
package rtsgame.core
import kotlinx.datetime.*
import kotlin.time.*

// Direct translation of js/core/gameState.js
// NO IMPROVEMENTS - preserving exact JS behavior

data class TeamResources(
    var mass: Double = 100.0,
    var energy: Double = 150.0,
    var massIncome: Double = 0.0,
    var energyIncome: Double = 0.0
)

class GameState {
    var gameTime: Int = 0
    var winner: String? = null
    var paused: Boolean = false
    var isRunning: Boolean = false
    
    val resources = mutableMapOf(
        "blue" to TeamResources(),
        "red" to TeamResources()
    )
    
    val units = mutableListOf<Any>() // Will be GameUnit type when ported
    val buildings = mutableListOf<Any>() // Will be Building type when ported
    val terrain = mutableListOf<Any>()
    val resourceNodes = mutableListOf<Any>()
    val effects = mutableListOf<Any>()
    val projectiles = mutableListOf<Any>()
    val captions = mutableListOf<Any>()

    fun updateResourceIncome() {
        // Update mass income
        for (team in listOf("blue", "red")) {
            var massIncome = 0.0
            var energyIncome = 0.0
            
            // Calculate income from extractors
            for (building in buildings) {
                // TODO: When Building is ported, this will be typed properly
                // For now, using reflection-style access to match JS behavior
                val b = building as? Map<String, Any>
                if (b != null && b["team"] == team) {
                    when (b["type"]) {
                        "massExtractor" -> {
                            massIncome += (b["income"] as? Double) ?: 0.0
                        }
                        "energyExtractor" -> {
                            energyIncome += (b["income"] as? Double) ?: 0.0
                        }
                    }
                }
            }
            
            // Update income values
            resources[team]?.massIncome = massIncome
            resources[team]?.energyIncome = energyIncome
            
            // Add income to resources
            resources[team]?.mass = (resources[team]?.mass ?: 0.0) + massIncome
            resources[team]?.energy = (resources[team]?.energy ?: 0.0) + energyIncome
        }
    }

    fun createGameContext(): Map<String, Any> {
        return mapOf(
            "units" to units,
            "buildings" to buildings,
            "terrain" to terrain,
            "resourceNodes" to resourceNodes,
            "effects" to effects,
            "projectiles" to projectiles,
            "resources" to resources,
            "gameTime" to gameTime
        )
    }

    fun reset() {
        gameTime = 0
        winner = null
        paused = false
        isRunning = false
        
        resources.clear()
        resources["blue"] = TeamResources()
        resources["red"] = TeamResources()
        
        units.clear()
        buildings.clear()
        terrain.clear()
        resourceNodes.clear()
        effects.clear()
        projectiles.clear()
        captions.clear()
    }
    
    fun addEvent(type: String, message: String, priority: Int, position: Pair<Double, Double>) {
        effects.add(mapOf(
            "type" to type,
            "message" to message,
            "priority" to priority,
            "x" to position.first,
            "y" to position.second,
            "time" to gameTime
        ))
    }
}