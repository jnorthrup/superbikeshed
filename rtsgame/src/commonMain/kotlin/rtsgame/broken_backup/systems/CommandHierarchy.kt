import kotlin.math.*
package rtsgame.systems
import kotlinx.datetime.*
import kotlin.time.*

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.j
import rtsgame.config.*
import rtsgame.entities.GameUnit
import rtsgame.entities.Building
import rtsgame.codec.*
import rtsgame.ai.StrategicAI
import kotlin.math.*

/**
 * Enhanced Command Hierarchy - Multi-level command structure with authority delegation
 * Direct translation from JS with exact behavior preservation
 */
class EnhancedCommandHierarchy(
    val team: String,
    internal val computroniumManager: ComputroniumManager
) {
    
    // Command structure
    internal val commandNodes = mutableMapOf<Int, CommandNode>()
    internal val commandLinks = mutableListOf<CommandLink>()
    internal var rootCommanderId: Int? = null
    
    // Authority levels
    internal val authorityLevels = mutableMapOf<Int, AuthorityLevel>()
    internal val delegationChains = mutableMapOf<Int, MutableList<Int>>()
    
    // Command fitness tracking
    internal val commandFitness = mutableMapOf<Int, CommandFitnessData>()
    internal var lastFitnessUpdate: Long = 0
    
    // Decision override system
    internal val overrideQueue = mutableListOf<CommandOverride>()
    internal val decisionHistory = mutableListOf<CommandDecision>()
    
    fun update(simulation: rtsgame.core.Simulation, deltaTime: Double) {
        val currentTime = simulation.gameState.gameTime.toLong()
        
        // Update command structure
        updateCommandNodes(simulation)
        
        // Process command fitness
        if (currentTime - lastFitnessUpdate >= 60) { // Every second
            updateCommandFitness(simulation)
            lastFitnessUpdate = currentTime
        }
        
        // Process delegation chains
        processDelegationChains(simulation)
        
        // Handle override requests
        processOverrides(simulation)
        
        // Execute autonomous commands
        executeAutonomousCommands(simulation)
    }
    
    internal fun updateCommandNodes(simulation: rtsgame.core.Simulation) {
        val units = simulation.units.filter { it.team == team && !it.isDead }
        val buildings = simulation.buildings.filter { it.team == team && it.hp > 0 }
        
        // Clear old nodes
        commandNodes.clear()
        
        // Find commanders
        val commanders = units.filter { it.type == "commander" }
        if (commanders.isNotEmpty()) {
            rootCommanderId = commanders.first().id
            
            commanders.forEach { commander ->
                commandNodes[commander.id] = CommandNode(
                    id = commander.id,
                    type = CommandNodeType.COMMANDER,
                    entity = commander,
                    authorityRange = COMPUTRONIUM_CONFIG.COMMAND_RANGES.STRATEGIC.toDouble(),
                    commandCapacity = 20,
                    subordinates = mutableListOf()
                )
            }
        }
        
        // Add factory commanders
        val factories = buildings.filter { it.type.contains("Factory") && !it.isUnderConstruction }
        factories.forEach { factory ->
            commandNodes[factory.id] = CommandNode(
                id = factory.id,
                type = CommandNodeType.FACTORY,
                entity = factory,
                authorityRange = COMPUTRONIUM_CONFIG.COMMAND_RANGES.OPERATIONAL.toDouble(),
                commandCapacity = 5,
                subordinates = mutableListOf()
            )
        }
        
        // Add field commanders (experienced units)
        val fieldCommanders = units.filter { 
            it.veterancyLevel in listOf(VETERANCY_LEVELS.VETERAN, VETERANCY_LEVELS.ELITE, VETERANCY_LEVELS.HERO)
        }
        fieldCommanders.forEach { unit ->
            commandNodes[unit.id] = CommandNode(
                id = unit.id,
                type = CommandNodeType.FIELD_COMMANDER,
                entity = unit,
                authorityRange = COMPUTRONIUM_CONFIG.COMMAND_RANGES.TACTICAL.toDouble(),
                commandCapacity = 8,
                subordinates = mutableListOf()
            )
        }
        
        // Establish command links
        establishCommandLinks(units)
    }
    
    internal fun establishCommandLinks(units: List<GameUnit>) {
        commandLinks.clear()
        
        // Each unit finds its best commander
        units.forEach { unit ->
            if (unit.id in commandNodes) return@forEach // Skip if unit is already a commander
            
            val bestCommander = findBestCommander(unit)
            if (bestCommander != null) {
                val link = CommandLink(
                    commanderId = bestCommander.id,
                    subordinateId = unit.id,
                    distance = calculateDistance(bestCommander.entity, unit),
                    latency = calculateCommandLatency(bestCommander, unit),
                    reliability = calculateLinkReliability(bestCommander, unit)
                )
                
                commandLinks.add(link)
                bestCommander.subordinates.add(unit.id)
                
                // Set authority level
                authorityLevels[unit.id] = determineAuthorityLevel(bestCommander, unit)
            }
        }
    }
    
    internal fun findBestCommander(unit: GameUnit): CommandNode? {
        val candidates = commandNodes.values.filter { commander ->
            val distance = calculateDistance(commander.entity, unit)
            distance <= commander.authorityRange && 
            commander.subordinates.size < commander.commandCapacity
        }
        
        return candidates.minByOrNull { commander ->
            val distance = calculateDistance(commander.entity, unit)
            val capacityRatio = commander.subordinates.size.toDouble() / commander.commandCapacity
            val authorityPriority = when (commander.type) {
                CommandNodeType.COMMANDER -> 1.0
                CommandNodeType.FIELD_COMMANDER -> 2.0
                CommandNodeType.FACTORY -> 3.0
            }
            
            // Weighted score: closer + less loaded + higher authority = better
            distance * 0.01 + capacityRatio * 100.0 + authorityPriority * 10.0
        }
    }
    
    internal fun calculateDistance(entity1: Any, entity2: Any): Double {
        val (x1, y1) = getEntityPosition(entity1)
        val (x2, y2) = getEntityPosition(entity2)
        return sqrt((x1 - x2).pow(2) + (y1 - y2).pow(2))
    }
    
    internal fun getEntityPosition(entity: Any): Pair<Double, Double> {
        return when (entity) {
            is GameUnit -> entity.x to entity.y
            is Building -> entity.x to entity.y
            else -> 0.0 to 0.0
        }
    }
    
    internal fun calculateCommandLatency(commander: CommandNode, unit: GameUnit): Double {
        val baseLatency = computroniumManager.getCommandLatencyReduction()
        val distanceLatency = calculateDistance(commander.entity, unit) * 0.001 // 1ms per distance unit
        val hierarchyLatency = when (commander.type) {
            CommandNodeType.COMMANDER -> 0.01 // 10ms
            CommandNodeType.FIELD_COMMANDER -> 0.02 // 20ms
            CommandNodeType.FACTORY -> 0.05 // 50ms
        }
        
        return baseLatency + distanceLatency + hierarchyLatency
    }
    
    internal fun calculateLinkReliability(commander: CommandNode, unit: GameUnit): Double {
        val distance = calculateDistance(commander.entity, unit)
        val maxRange = commander.authorityRange
        val distanceReliability = 1.0 - (distance / maxRange) * 0.3
        
        val loadReliability = 1.0 - (commander.subordinates.size.toDouble() / commander.commandCapacity) * 0.2
        
        val computroniumBonus = minOf(0.2, computroniumManager.getComputroniumStatus().efficiency * 0.2)
        
        return (distanceReliability * loadReliability + computroniumBonus).coerceIn(0.1, 1.0)
    }
    
    internal fun determineAuthorityLevel(commander: CommandNode, unit: GameUnit): AuthorityLevel {
        val distance = calculateDistance(commander.entity, unit)
        val maxRange = commander.authorityRange
        val distanceRatio = distance / maxRange
        
        return when {
            distanceRatio <= 0.3 -> AuthorityLevel.FULL_COMMAND
            distanceRatio <= 0.6 -> AuthorityLevel.REDUCED_AUTHORITY
            distanceRatio <= 0.8 -> AuthorityLevel.COMPROMISED_COMMAND
            distanceRatio <= 1.0 -> AuthorityLevel.CRITICAL_STATUS
            else -> AuthorityLevel.COMBAT_INEFFECTIVE
        }
    }
    
    internal fun updateCommandFitness(simulation: rtsgame.core.Simulation) {
        commandNodes.values.forEach { commander ->
            val fitness = calculateCommandFitness(commander, simulation)
            commandFitness[commander.id] = fitness
            
            // Trigger delegation if fitness is poor
            if (fitness.overallFitness < 0.3) {
                considerDelegation(commander, simulation)
            }
        }
    }
    
    internal fun calculateCommandFitness(commander: CommandNode, simulation: rtsgame.core.Simulation): CommandFitnessData {
        val subordinateCount = commander.subordinates.size
        val loadRatio = subordinateCount.toDouble() / commander.commandCapacity
        
        // Calculate response time fitness
        val avgLatency = commander.subordinates.mapNotNull { subId ->
            commandLinks.find { it.subordinateId == subId }?.latency
        }.average().takeIf { !it.isNaN() } ?: 0.0
        
        val responseTimeFitness = (1.0 - avgLatency.coerceAtMost(1.0))
        
        // Calculate coordination fitness
        val coordinationFitness = if (subordinateCount > 0) {
            val activeSubordinates = commander.subordinates.count { subId ->
                val unit = simulation.units.find { it.id == subId }
                unit?.currentCommand != null
            }
            activeSubordinates.toDouble() / subordinateCount
        } else 1.0
        
        // Calculate combat effectiveness
        val combatFitness = calculateCombatEffectiveness(commander, simulation)
        
        val overallFitness = (responseTimeFitness + coordinationFitness + combatFitness) / 3.0
        
        return CommandFitnessData(
            commanderId = commander.id,
            responseTimeFitness = responseTimeFitness,
            coordinationFitness = coordinationFitness,
            combatEffectiveness = combatFitness,
            overallFitness = overallFitness,
            loadRatio = loadRatio
        )
    }
    
    internal fun calculateCombatEffectiveness(commander: CommandNode, simulation: rtsgame.core.Simulation): Double {
        val subordinates = commander.subordinates.mapNotNull { subId ->
            simulation.units.find { it.id == subId }
        }
        
        if (subordinates.isEmpty()) return 1.0
        
        // Calculate based on unit health, position, and recent actions
        val healthRatio = subordinates.sumOf { it.hp / it.maxHp } / subordinates.size
        val activeBehaviorRatio = subordinates.count { it.autonomousBehavior }.toDouble() / subordinates.size
        
        return (healthRatio + activeBehaviorRatio) / 2.0
    }
    
    internal fun considerDelegation(commander: CommandNode, simulation: rtsgame.core.Simulation) {
        if (commander.subordinates.size <= 2) return // Too few to delegate
        
        // Find potential field commanders among subordinates
        val candidates = commander.subordinates.mapNotNull { subId ->
            simulation.units.find { it.id == subId }
        }.filter { 
            it.veterancyLevel in listOf(VETERANCY_LEVELS.REGULAR, VETERANCY_LEVELS.VETERAN) &&
            it.id !in commandNodes
        }
        
        if (candidates.isNotEmpty()) {
            val newFieldCommander = candidates.maxByOrNull { 
                when (it.veterancyLevel) {
                    VETERANCY_LEVELS.VETERAN -> 2.0
                    VETERANCY_LEVELS.REGULAR -> 1.0
                    else -> 0.0
                }
            }!!
            
            // Promote to field commander
            promoteToFieldCommander(newFieldCommander, commander)
        }
    }
    
    internal fun promoteToFieldCommander(unit: GameUnit, parentCommander: CommandNode) {
        // Create new command node
        val newCommander = CommandNode(
            id = unit.id,
            type = CommandNodeType.FIELD_COMMANDER,
            entity = unit,
            authorityRange = COMPUTRONIUM_CONFIG.COMMAND_RANGES.TACTICAL.toDouble(),
            commandCapacity = 5,
            subordinates = mutableListOf()
        )
        
        commandNodes[unit.id] = newCommander
        
        // Transfer some subordinates
        val subordinatesToTransfer = parentCommander.subordinates.filter { subId ->
            if (subId == unit.id) return@filter false
            val subordinate = commandNodes.values.find { it.id == subId }
            subordinate?.let { 
                calculateDistance(unit, it.entity) < newCommander.authorityRange 
            } ?: false
        }.take(3)
        
        subordinatesToTransfer.forEach { subId ->
            parentCommander.subordinates.remove(subId)
            newCommander.subordinates.add(subId)
        }
        
        // Remove unit from parent's subordinates
        parentCommander.subordinates.remove(unit.id)
        
        // Add delegation record
        addDelegationChain(parentCommander.id, unit.id)
    }
    
    internal fun addDelegationChain(parentId: Int, delegateId: Int) {
        delegationChains.getOrPut(parentId) { mutableListOf() }.add(delegateId)
    }
    
    internal fun processDelegationChains(simulation: rtsgame.core.Simulation) {
        delegationChains.forEach { (parentId, delegates) ->
            val parent = commandNodes[parentId]
            if (parent != null) {
                delegates.forEach { delegateId ->
                    val delegate = commandNodes[delegateId]
                    if (delegate != null) {
                        // Sync strategic decisions
                        syncStrategicDecisions(parent, delegate, simulation)
                    }
                }
            }
        }
    }
    
    internal fun syncStrategicDecisions(parent: CommandNode, delegate: CommandNode, simulation: rtsgame.core.Simulation) {
        // Transfer high-level strategy from parent to delegate
        // This would integrate with the strategic AI system
    }
    
    internal fun processOverrides(simulation: rtsgame.core.Simulation) {
        overrideQueue.removeAll { override ->
            executeOverride(override, simulation)
            true
        }
    }
    
    internal fun executeOverride(override: CommandOverride, simulation: rtsgame.core.Simulation): Boolean {
        val unit = simulation.units.find { it.id == override.unitId } ?: return false
        
        when (override.type) {
            OverrideType.MOVE -> {
                unit.issueCommand(rtsgame.entities.MoveCommand(override.x, override.y))
            }
            OverrideType.ATTACK -> {
                val target = simulation.units.find { it.id == override.targetId }
                if (target != null) {
                    unit.issueCommand(rtsgame.entities.AttackCommand(target))
                }
            }
            OverrideType.STOP -> {
                unit.issueCommand(rtsgame.entities.StopCommand())
            }
        }
        
        recordDecision(override, unit)
        return true
    }
    
    internal fun executeAutonomousCommands(simulation: rtsgame.core.Simulation) {
        // Units with high authority can make autonomous decisions
        commandNodes.values.forEach { commander ->
            val fitness = commandFitness[commander.id]
            if (fitness?.overallFitness ?: 0.0 > 0.7) {
                // High-performing commanders get more autonomy
                grantAutonomousAuthority(commander, simulation)
            }
        }
    }
    
    internal fun grantAutonomousAuthority(commander: CommandNode, simulation: rtsgame.core.Simulation) {
        commander.subordinates.forEach { subId ->
            val unit = simulation.units.find { it.id == subId }
            if (unit != null && unit.currentCommand == null) {
                unit.autonomousBehavior = true
            }
        }
    }
    
    internal fun recordDecision(override: CommandOverride, unit: GameUnit) {
        decisionHistory.add(CommandDecision(
            commanderId = override.commanderId,
            unitId = override.unitId,
            decisionType = override.type.name,
            timestamp = System.kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
            authorityLevel = authorityLevels[unit.id] ?: AuthorityLevel.COMBAT_INEFFECTIVE
        ))
        
        // Limit history size
        if (decisionHistory.size > 1000) {
            decisionHistory.removeFirst()
        }
    }
    
    // Public interface
    fun requestCommandOverride(request: RTSRequest.AIDecisionOverride): Boolean {
        val override = when (request.decisionType) {
            "move" -> CommandOverride(
                commanderId = rootCommanderId ?: return false,
                unitId = request.overrideDecision.split(",")[0].toIntOrNull() ?: return false,
                type = OverrideType.MOVE,
                x = request.overrideDecision.split(",")[1].toDoubleOrNull() ?: 0.0,
                y = request.overrideDecision.split(",")[2].toDoubleOrNull() ?: 0.0
            )
            else -> return false
        }
        
        overrideQueue.add(override)
        return true
    }
    
    fun getCommandStructure(): Indexed<CommandNode> {
        val nodes = commandNodes.values.toList()
        return \1 j { \2: Int -> nodes[i] }
    }
    
    fun getCommandFitness(): Indexed<CommandFitnessData> {
        val fitness = commandFitness.values.toList()
        return \1 j { \2: Int -> fitness[i] }
    }
}

// Supporting data structures
data class CommandNode(
    val id: Int,
    val type: CommandNodeType,
    val entity: Any,
    val authorityRange: Double,
    val commandCapacity: Int,
    val subordinates: MutableList<Int>
)

enum class CommandNodeType {
    COMMANDER, FIELD_COMMANDER, FACTORY
}

data class CommandLink(
    val commanderId: Int,
    val subordinateId: Int,
    val distance: Double,
    val latency: Double,
    val reliability: Double
)

enum class AuthorityLevel {
    FULL_COMMAND, REDUCED_AUTHORITY, COMPROMISED_COMMAND, CRITICAL_STATUS, COMBAT_INEFFECTIVE
}

data class CommandFitnessData(
    val commanderId: Int,
    val responseTimeFitness: Double,
    val coordinationFitness: Double,
    val combatEffectiveness: Double,
    val overallFitness: Double,
    val loadRatio: Double
)

data class CommandOverride(
    val commanderId: Int,
    val unitId: Int,
    val type: OverrideType,
    val x: Double = 0.0,
    val y: Double = 0.0,
    val targetId: Int = -1
)

enum class OverrideType {
    MOVE, ATTACK, STOP
}

data class CommandDecision(
    val commanderId: Int,
    val unitId: Int,
    val decisionType: String,
    val timestamp: Long,
    val authorityLevel: AuthorityLevel
)

internal val rtsgame.core.Simulation.units: List<GameUnit>
    get() = entityManager.units.mapNotNull { it as? GameUnit }

internal val rtsgame.core.Simulation.buildings: List<Building>
    get() = entityManager.buildings.mapNotNull { it as? Building }