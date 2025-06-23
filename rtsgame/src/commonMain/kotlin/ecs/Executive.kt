package ecs

// --- Executive/Codec/Move System for Player Decision and Prediction ---

import ecs.EntityId

// Represents the current game state (stub for now, expand as needed)
data class GameState(val tick: Int, val playerTech: Map<Int, TechMilestone>)

// --- Tech Milestones ---
enum class TechMilestone {
    GATHER_RESOURCES,
    BUILD_BASIC_UNIT,
    RESEARCH_TECH,
    BUILD_ADVANCED_UNIT,
    COMPLETE
}

// Executive: Decides moves for a player (human, AI, or hybrid)
interface Executive {
    fun decideMoves(gameState: GameState, playerId: Int): List<Move>
}

// Codec: Predicts default moves for a player (policy engine, fallback AI)
interface Codec {
    fun predictMoves(gameState: GameState, playerId: Int): List<Move>
}

// Move: An action/command for an entity
// Extend Action with more game-specific actions as needed
sealed class Action {
    data class MoveTo(val x: Float, val y: Float) : Action()
    data class Attack(val targetId: EntityId) : Action()
    object Idle : Action()
    data class GatherResource(val resourceType: String) : Action()
    object BuildBasicUnit : Action()
    object ResearchTech : Action()
    object BuildAdvancedUnit : Action()
    // Add more actions (Defend, Build, UseAbility, etc.)
}

data class Move(val entityId: EntityId, val action: Action)

// Resolves which move to apply for each entity: executive overrides codec
fun resolveMoves(
    executiveMoves: List<Move>,
    codecMoves: List<Move>
): List<Move> {
    val moveMap = mutableMapOf<EntityId, Move>()
    for (move in codecMoves) {
        moveMap[move.entityId] = move
    }
    for (move in executiveMoves) {
        moveMap[move.entityId] = move // Executive overrides codec
    }
    return moveMap.values.toList()
}

// --- AI Executive that progresses through tech milestones ---
class AIExecutive : Executive {
    // Track milestone per player (could be in GameState, but local for demo)
    private val playerMilestones = mutableMapOf<Int, TechMilestone>()

    override fun decideMoves(gameState: GameState, playerId: Int): List<Move> {
        val milestone = playerMilestones.getOrPut(playerId) {
            gameState.playerTech[playerId] ?: TechMilestone.GATHER_RESOURCES
        }
        val moves = mutableListOf<Move>()
        // Example: one entity per player (expand as needed)
        val entityId = playerId // stub: entityId == playerId for demo
        when (milestone) {
            TechMilestone.GATHER_RESOURCES -> {
                moves += Move(entityId, Action.GatherResource("Mass"))
                // Advance milestone after some ticks (stub logic)
                if (gameState.tick > 10) playerMilestones[playerId] = TechMilestone.BUILD_BASIC_UNIT
            }
            TechMilestone.BUILD_BASIC_UNIT -> {
                moves += Move(entityId, Action.BuildBasicUnit)
                if (gameState.tick > 20) playerMilestones[playerId] = TechMilestone.RESEARCH_TECH
            }
            TechMilestone.RESEARCH_TECH -> {
                moves += Move(entityId, Action.ResearchTech)
                if (gameState.tick > 30) playerMilestones[playerId] = TechMilestone.BUILD_ADVANCED_UNIT
            }
            TechMilestone.BUILD_ADVANCED_UNIT -> {
                moves += Move(entityId, Action.BuildAdvancedUnit)
                if (gameState.tick > 40) playerMilestones[playerId] = TechMilestone.COMPLETE
            }
            TechMilestone.COMPLETE -> {
                moves += Move(entityId, Action.Idle)
            }
        }
        return moves
    }
}

// --- Extension Points ---
// - Implement more sophisticated milestone logic (resource checks, unit counts, etc.)
// - Integrate with ECS systems to apply resolved moves
// - Expand GameState with full ECS/world state
// - Add more Action types as game design evolves 