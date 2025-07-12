import kotlin.math.*
package rtsgame.systems
import kotlinx.datetime.*
import kotlin.time.*

import borg.trikeshed.lib.*
import rtsgame.codec.*
import rtsgame.entities.*
import kotlinx.serialization.*
import kotlinx.serialization.json.Json

/**
 * Battle Journal - Records and analyzes complete battle data
 * Direct translation from JS with exact recording patterns preserved
 */
class BattleJournal {
    
    internal var currentBattle: BattleRecord? = null
    internal val completedBattles = mutableListOf<BattleRecord>()
    
    // Recording state
    internal var isRecording = false
    internal var recordingStartTime: Long = 0
    internal var frameCounter: Long = 0
    
    // Analysis data
    internal val eventBuffer = mutableListOf<BattleEvent>()
    internal val snapshots = mutableListOf<BattleSnapshot>()
    internal val playerActions = mutableListOf<PlayerAction>()
    
    fun startBattle(seed: Long, teams: List<String>) {
        currentBattle = BattleRecord(
            id = generateBattleId(),
            seed = seed,
            teams = teams,
            startTime = getCurrentTime(),
            events = mutableListOf(),
            snapshots = mutableListOf(),
            playerActions = mutableListOf(),
            result = null
        )
        
        isRecording = true
        recordingStartTime = getCurrentTime()
        frameCounter = 0
        
        eventBuffer.clear()
        snapshots.clear()
        playerActions.clear()
        
        addEvent("BATTLE_START", "Battle initiated with seed $seed", EventImportance.HIGH)
    }
    
    fun recordFrame(simulation: rtsgame.core.Simulation) {
        if (!isRecording || currentBattle == null) return
        
        frameCounter++
        
        // Record snapshot every 60 frames (1 second at 60 FPS)
        if (frameCounter % 60 == 0L) {
            recordSnapshot(simulation)
        }
        
        // Process events from buffer
        flushEventBuffer()
    }
    
    internal fun recordSnapshot(simulation: rtsgame.core.Simulation) {
        val snapshot = BattleSnapshot(
            frame = frameCounter,
            gameTime = simulation.gameState.gameTime.toDouble(),
            entities = simulation.entityManager.getAllEntitiesForSync(),
            resources = simulation.resources.mapValues { (_, res) ->
                ResourceSnapshot(
                    mass = res.mass,
                    energy = res.energy,
                    computronium = res.computronium,
                    massIncome = res.massIncome,
                    energyIncome = res.energyIncome,
                    computroniumIncome = res.computroniumIncome
                )
            },
            teamStats = calculateTeamStats(simulation)
        )
        
        snapshots.add(snapshot)
        currentBattle?.snapshots?.add(snapshot)
    }
    
    internal fun calculateTeamStats(simulation: rtsgame.core.Simulation): Map<String, TeamStats> {
        val stats = mutableMapOf<String, TeamStats>()
        
        currentBattle?.teams?.forEach { team ->
            val units = simulation.entityManager.getUnitsByTeam(team)
            val buildings = simulation.entityManager.getBuildingsByTeam(team)
            
            val militaryValue = units.sumOf { unit ->
                when (unit.type) {
                    "scout" -> 1.0
                    "tank" -> 3.0
                    "artillery" -> 2.5
                    "fighter" -> 2.0
                    "submarine" -> 2.5
                    else -> 0.5
                }
            }
            
            val economicValue = buildings.filter { 
                it.type.contains("Extractor") || it.type.contains("Plant") 
            }.sumOf { it.income }
            
            stats[team] = TeamStats(
                unitCount = units.size,
                buildingCount = buildings.size,
                militaryValue = militaryValue,
                economicValue = economicValue,
                totalValue = militaryValue + economicValue * 10.0
            )
        }
        
        return stats
    }
    
    fun addEvent(type: String, description: String, importance: EventImportance, 
                 position: Pair<Double, Double>? = null, entityId: Int? = null) {
        if (!isRecording) return
        
        val event = BattleEvent(
            frame = frameCounter,
            gameTime = (getCurrentTime() - recordingStartTime).toDouble() / 1000.0,
            type = type,
            description = description,
            importance = importance,
            position = position,
            entityId = entityId
        )
        
        eventBuffer.add(event)
        
        // Immediately flush high importance events
        if (importance == EventImportance.CRITICAL) {
            flushEventBuffer()
        }
    }
    
    fun recordPlayerAction(team: String, action: String, target: Any? = null) {
        if (!isRecording) return
        
        val playerAction = PlayerAction(
            frame = frameCounter,
            gameTime = (getCurrentTime() - recordingStartTime).toDouble() / 1000.0,
            team = team,
            action = action,
            targetId = when (target) {
                is GameUnit -> target.id
                is Building -> target.id
                else -> null
            },
            targetType = when (target) {
                is GameUnit -> "unit"
                is Building -> "building"
                else -> "none"
            }
        )
        
        playerActions.add(playerAction)
        currentBattle?.playerActions?.add(playerAction)
    }
    
    fun endBattle(winner: String?, reason: String) {
        if (!isRecording || currentBattle == null) return
        
        addEvent("BATTLE_END", "Battle ended: $reason", EventImportance.CRITICAL)
        
        val battle = currentBattle!!
        battle.endTime = getCurrentTime()
        battle.duration = battle.endTime!! - battle.startTime
        battle.result = BattleResult(
            winner = winner,
            reason = reason,
            duration = battle.duration!!,
            totalFrames = frameCounter
        )
        
        // Final analysis
        battle.analysis = analyzeBattle(battle)
        
        completedBattles.add(battle)
        isRecording = false
        currentBattle = null
    }
    
    internal fun flushEventBuffer() {
        currentBattle?.events?.addAll(eventBuffer)
        eventBuffer.clear()
    }
    
    internal fun analyzeBattle(battle: BattleRecord): BattleAnalysis {
        val keyMoments = identifyKeyMoments(battle)
        val balanceIssues = detectBalanceIssues(battle)
        val playerBehavior = analyzePlayerBehavior(battle)
        val economicProgression = analyzeEconomicProgression(battle)
        
        return BattleAnalysis(
            keyMoments = keyMoments,
            balanceIssues = balanceIssues,
            playerBehavior = playerBehavior,
            economicProgression = economicProgression,
            averageAPM = calculateAverageAPM(battle),
            dominanceShifts = calculateDominanceShifts(battle)
        )
    }
    
    internal fun identifyKeyMoments(battle: BattleRecord): List<KeyMoment> {
        val moments = mutableListOf<KeyMoment>()
        
        // First unit production
        val firstUnit = battle.events.find { it.type == "UNIT_SPAWNED" }
        firstUnit?.let { 
            moments.add(KeyMoment("FIRST_UNIT", it.gameTime, KeyMomentType.ECONOMIC))
        }
        
        // First building
        val firstBuilding = battle.events.find { it.type == "BUILDING_COMPLETED" }
        firstBuilding?.let {
            moments.add(KeyMoment("FIRST_BUILDING", it.gameTime, KeyMomentType.ECONOMIC))
        }
        
        // First combat
        val firstCombat = battle.events.find { it.type == "UNIT_DESTROYED" || it.type == "COMBAT_START" }
        firstCombat?.let {
            moments.add(KeyMoment("FIRST_COMBAT", it.gameTime, KeyMomentType.MILITARY))
        }
        
        // Technology milestones
        val firstAdvancedUnit = battle.events.find { 
            it.type == "UNIT_SPAWNED" && it.description.contains("artillery") 
        }
        firstAdvancedUnit?.let {
            moments.add(KeyMoment("FIRST_ADVANCED_UNIT", it.gameTime, KeyMomentType.TECHNOLOGY))
        }
        
        return moments
    }
    
    internal fun detectBalanceIssues(battle: BattleRecord): List<BalanceIssue> {
        val issues = mutableListOf<BalanceIssue>()
        
        battle.snapshots.forEach { snapshot ->
            val teams = snapshot.teamStats.values.toList()
            if (teams.size >= 2) {
                val team1 = teams[0]
                val team2 = teams[1]
                
                // Economic imbalance
                val economicRatio = if (team2.economicValue > 0) {
                    team1.economicValue / team2.economicValue
                } else Double.MAX_VALUE
                
                if (economicRatio > 3.0 || economicRatio < 0.33) {
                    issues.add(BalanceIssue(
                        type = "ECONOMIC_IMBALANCE",
                        severity = kotlin.math.ln(kotlin.math.max(economicRatio, 1.0 / economicRatio)),
                        time = snapshot.gameTime,
                        description = "Economic disparity detected"
                    ))
                }
                
                // Military imbalance
                val militaryRatio = if (team2.militaryValue > 0) {
                    team1.militaryValue / team2.militaryValue
                } else Double.MAX_VALUE
                
                if (militaryRatio > 4.0 || militaryRatio < 0.25) {
                    issues.add(BalanceIssue(
                        type = "MILITARY_IMBALANCE",
                        severity = kotlin.math.ln(kotlin.math.max(militaryRatio, 1.0 / militaryRatio)),
                        time = snapshot.gameTime,
                        description = "Military power disparity detected"
                    ))
                }
            }
        }
        
        return issues
    }
    
    internal fun analyzePlayerBehavior(battle: BattleRecord): PlayerBehaviorAnalysis {
        val totalActions = battle.playerActions.size
        val duration = battle.duration?.toDouble() ?: 1.0
        val apm = (totalActions * 60.0) / (duration / 1000.0)
        
        val actionTypes = battle.playerActions.groupBy { it.action }.mapValues { it.value.size }
        
        val earlyActions = battle.playerActions.filter { it.gameTime < 120.0 } // First 2 minutes
        val buildActions = earlyActions.count { it.action.contains("build", ignoreCase = true) }
        val combatActions = earlyActions.count { it.action.contains("attack", ignoreCase = true) }
        
        val playstyle = when {
            buildActions > combatActions * 2 -> "Economic"
            combatActions > buildActions -> "Aggressive"
            else -> "Balanced"
        }
        
        return PlayerBehaviorAnalysis(
            totalActions = totalActions,
            actionsPerMinute = apm,
            actionDistribution = actionTypes,
            playstyle = playstyle,
            microManagement = if (apm > 100) "High" else if (apm > 50) "Medium" else "Low"
        )
    }
    
    internal fun analyzeEconomicProgression(battle: BattleRecord): EconomicProgression {
        val milestones = mutableListOf<EconomicMilestone>()
        
        battle.snapshots.forEach { snapshot ->
            snapshot.resources.forEach { (team, res) ->
                val totalIncome = res.massIncome + res.energyIncome
                
                when {
                    totalIncome >= 10.0 && milestones.none { it.type == "INCOME_ESTABLISHED" && it.team == team } -> {
                        milestones.add(EconomicMilestone("INCOME_ESTABLISHED", snapshot.gameTime, team))
                    }
                    totalIncome >= 25.0 && milestones.none { it.type == "INCOME_EXPANSION" && it.team == team } -> {
                        milestones.add(EconomicMilestone("INCOME_EXPANSION", snapshot.gameTime, team))
                    }
                    totalIncome >= 50.0 && milestones.none { it.type == "INCOME_ADVANCED" && it.team == team } -> {
                        milestones.add(EconomicMilestone("INCOME_ADVANCED", snapshot.gameTime, team))
                    }
                }
            }
        }
        
        return EconomicProgression(milestones)
    }
    
    internal fun calculateAverageAPM(battle: BattleRecord): Double {
        val duration = battle.duration?.toDouble() ?: 1.0
        return (battle.playerActions.size * 60.0) / (duration / 1000.0)
    }
    
    internal fun calculateDominanceShifts(battle: BattleRecord): List<DominanceShift> {
        val shifts = mutableListOf<DominanceShift>()
        var currentLeader: String? = null
        
        battle.snapshots.forEach { snapshot ->
            val teamValues = snapshot.teamStats.mapValues { it.value.totalValue }
            val newLeader = teamValues.maxByOrNull { it.value }?.key
            
            if (newLeader != currentLeader && currentLeader != null) {
                shifts.add(DominanceShift(
                    time = snapshot.gameTime,
                    newLeader = newLeader ?: "",
                    previousLeader = currentLeader ?: "",
                    margin = teamValues.values.maxOrNull()!! - teamValues.values.sorted().getOrNull(1)!!
                ))
            }
            
            currentLeader = newLeader
        }
        
        return shifts
    }
    
    fun exportBattle(battleId: String): String? {
        val battle = completedBattles.find { it.id == battleId }
        return battle?.let { Json.encodeToString(BattleRecord.serializer(), it) }
    }
    
    fun getBattleHistory(): Indexed<BattleRecord> {
        return \1 j { \2: Int -> completedBattles[i] }
    }
    
    internal fun generateBattleId(): String = "battle_${getCurrentTime()}"
}

// Data structures for battle recording
data class BattleRecord(
    val id: String,
    val seed: Long,
    val teams: List<String>,
    val startTime: Long,
    var endTime: Long? = null,
    var duration: Long? = null,
    val events: MutableList<BattleEvent>,
    val snapshots: MutableList<BattleSnapshot>,
    val playerActions: MutableList<PlayerAction>,
    var result: BattleResult? = null,
    var analysis: BattleAnalysis? = null
)

data class BattleEvent(
    val frame: Long,
    val gameTime: Double,
    val type: String,
    val description: String,
    val importance: EventImportance,
    val position: Pair<Double, Double>? = null,
    val entityId: Int? = null
)

enum class EventImportance { LOW, MEDIUM, HIGH, CRITICAL }

data class BattleSnapshot(
    val frame: Long,
    val gameTime: Double,
    val entities: List<EntityState>,
    val resources: Map<String, ResourceSnapshot>,
    val teamStats: Map<String, TeamStats>
)

data class ResourceSnapshot(
    val mass: Int,
    val energy: Int,
    val computronium: Int,
    val massIncome: Double,
    val energyIncome: Double,
    val computroniumIncome: Double
)

data class TeamStats(
    val unitCount: Int,
    val buildingCount: Int,
    val militaryValue: Double,
    val economicValue: Double,
    val totalValue: Double
)

data class PlayerAction(
    val frame: Long,
    val gameTime: Double,
    val team: String,
    val action: String,
    val targetId: Int? = null,
    val targetType: String = "none"
)

data class BattleResult(
    val winner: String?,
    val reason: String,
    val duration: Long,
    val totalFrames: Long
)

data class BattleAnalysis(
    val keyMoments: List<KeyMoment>,
    val balanceIssues: List<BalanceIssue>,
    val playerBehavior: PlayerBehaviorAnalysis,
    val economicProgression: EconomicProgression,
    val averageAPM: Double,
    val dominanceShifts: List<DominanceShift>
)

data class KeyMoment(
    val type: String,
    val time: Double,
    val momentType: KeyMomentType
)

enum class KeyMomentType { ECONOMIC, MILITARY, TECHNOLOGY, STRATEGIC }

data class BalanceIssue(
    val type: String,
    val severity: Double,
    val time: Double,
    val description: String
)

data class PlayerBehaviorAnalysis(
    val totalActions: Int,
    val actionsPerMinute: Double,
    val actionDistribution: Map<String, Int>,
    val playstyle: String,
    val microManagement: String
)

data class EconomicProgression(
    val milestones: List<EconomicMilestone>
)

data class EconomicMilestone(
    val type: String,
    val time: Double,
    val team: String
)

data class DominanceShift(
    val time: Double,
    val newLeader: String,
    val previousLeader: String,
    val margin: Double
)