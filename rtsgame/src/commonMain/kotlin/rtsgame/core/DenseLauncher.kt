package rtsgame.core

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlin.time.*

/**
 * Dense game launcher that orchestrates all systems
 */
class DenseRTSGame(
    val mode: GameMode = GameMode.Multiplayer(isHost = true),
    val config: GameConfig = GameConfig()
) {
    sealed class GameMode {
        object Solo : GameMode()
        data class Multiplayer(val isHost: Boolean, val serverAddress: String? = null) : GameMode()
        object Replay : GameMode()
    }
    
    data class GameConfig(
        val mapSize: Vec3 = Vec3(2000f, 2000f, 0f),
        val maxPlayers: Int = 8,
        val aiDifficulty: Float = 0.5f,
        val startingUnits: Int = 5
    )
    
    // Core game state
    private val world = MutableStateFlow(emptyWorld())
    private val commands = Channel<Cmd>(Channel.UNLIMITED)
    private val network = Channel<Net>(Channel.UNLIMITED)
    
    // AI controllers
    private val aiControllers = mutableMapOf<Team, CoroutineScope>()
    
    suspend fun launch() = coroutineScope {
        println("🎮 Dense RTS Game Starting - Mode: $mode")
        
        // Initialize world
        world.value = generateWorld()
        
        // Start appropriate game mode
        when (mode) {
            is GameMode.Solo -> launchSolo()
            is GameMode.Multiplayer -> if (mode.isHost) launchHost() else launchClient(mode.serverAddress!!)
            is GameMode.Replay -> launchReplay()
        }
    }
    
    private suspend fun CoroutineScope.launchSolo() {
        // Game simulation
        launch {
            Game.gameLoop(world.value, commands.consumeAsFlow())
                .collect { newWorld ->
                    world.value = newWorld
                }
        }
        
        // AI opponents
        (1..3).forEach { teamId ->
            launchAI(Team(teamId))
        }
        
        // Player input handler
        launch {
            handlePlayerInput(Team(0))
        }
        
        // Visualization
        launch {
            visualize()
        }
    }
    
    private suspend fun CoroutineScope.launchHost() {
        val engine = DenseNetworkEngine(playerId = 0)
        
        // Server simulation
        launch {
            engine.serverSimulate(world.value, (0..3).toSet(), network)
                .collect { newWorld ->
                    world.value = newWorld
                }
        }
        
        // Host's client prediction
        launch {
            val playerCommands = Channel<Cmd>()
            engine.clientPredict(world.value, playerCommands.consumeAsFlow(), network)
                .collect { (predictedWorld, _) ->
                    world.value = predictedWorld
                }
            
            // Forward player commands
            launch {
                handlePlayerInput(Team(0), playerCommands)
            }
        }
        
        // AI for other teams
        (1..3).forEach { teamId ->
            launchAI(Team(teamId))
        }
        
        // Network server
        launch {
            networkServer()
        }
    }
    
    private suspend fun CoroutineScope.launchClient(serverAddress: String) {
        val engine = DenseNetworkEngine(playerId = 1) // Assigned by server
        
        // Connect to server
        val serverConnection = connectToServer(serverAddress)
        
        // Client prediction
        launch {
            val playerCommands = Channel<Cmd>()
            engine.clientPredict(world.value, playerCommands.consumeAsFlow(), serverConnection)
                .collect { (predictedWorld, rollback) ->
                    world.value = predictedWorld
                    if (rollback) {
                        println("⚡ Prediction rollback occurred")
                    }
                }
            
            // Player input
            launch {
                handlePlayerInput(Team(1), playerCommands)
            }
        }
        
        // Visualization
        launch {
            visualize()
        }
    }
    
    private suspend fun CoroutineScope.launchReplay() {
        // Load replay data
        val replayCommands = loadReplay()
        
        // Playback simulation
        launch {
            Game.gameLoop(world.value, replayCommands)
                .collect { newWorld ->
                    world.value = newWorld
                    delay(16) // Playback speed
                }
        }
        
        // Visualization with controls
        launch {
            visualizeReplay()
        }
    }
    
    private fun CoroutineScope.launchAI(team: Team) {
        aiControllers[team] = launch {
            // Multiple AI personalities
            val strategy = StrategyAI(team)
            val neural = NeuralAI()
            
            // Hybrid AI decision making
            while (isActive) {
                val currentWorld = world.value
                
                // Strategic decisions
                val strategicCmds = strategy.think(currentWorld)
                strategicCmds.forEach { commands.send(it) }
                
                // Tactical decisions for individual units
                currentWorld.with<Team>("team")
                    .filter { (_, t) -> t.id == team.id }
                    .forEach { (id, _) ->
                        // Neural network for micro
                        val input = neural.perceive(currentWorld, id)
                        val decision = neural.decide(input)
                        
                        val cmd = when (decision) {
                            0 -> { // Move
                                val pos = currentWorld[id]?.get<Pos>("pos")?.vec ?: return@forEach
                                val offset = Vec3(
                                    (Math.random() - 0.5f) * 100f,
                                    (Math.random() - 0.5f) * 100f,
                                    0f
                                )
                                Cmd.Move(id, pos + offset)
                            }
                            1 -> { // Attack
                                val enemies = currentWorld.with<Team>("team")
                                    .filter { (_, t) -> t.id != team.id }
                                    .map { (enemyId, _) -> enemyId }
                                    .toList()
                                
                                enemies.randomOrNull()?.let { target ->
                                    Cmd.Attack(id, target)
                                }
                            }
                            else -> null
                        }
                        
                        cmd?.let { commands.send(it) }
                    }
                
                // Swarm behaviors
                Swarm.flock(currentWorld, team).first().forEach { cmd ->
                    if (Math.random() < 0.1) { // 10% chance to follow swarm
                        commands.send(cmd)
                    }
                }
                
                delay(100) // AI think rate
            }
        }
    }
    
    private suspend fun handlePlayerInput(team: Team, output: SendChannel<Cmd>? = null) {
        // Simplified input handling - in real implementation would connect to UI
        while (currentCoroutineContext().isActive) {
            // Mock player commands
            val cmd = when ((Math.random() * 4).toInt()) {
                0 -> {
                    val unit = world.value.with<Team>("team")
                        .filter { (_, t) -> t.id == team.id }
                        .map { (id, _) -> id }
                        .randomOrNull()
                    
                    unit?.let {
                        val pos = world.value[it]?.get<Pos>("pos")?.vec ?: Vec3(0f, 0f, 0f)
                        Cmd.Move(it, pos + Vec3(100f, 0f, 0f))
                    }
                }
                1 -> Cmd.Spawn("tank", team.id, Vec3(100f, 100f, 0f))
                else -> null
            }
            
            cmd?.let { 
                output?.send(it) ?: commands.send(it)
            }
            
            delay(1000) // Input rate
        }
    }
    
    private suspend fun visualize() {
        world.collect { w ->
            // Terminal visualization
            println("\n=== Game State ===")
            println("Entities: ${w.size}")
            
            // Team summary
            val teams = w.with<Team>("team")
                .groupBy({ it.second.id }, { it.first })
                .mapValues { (_, units) -> units.size }
            
            teams.forEach { (team, count) ->
                println("Team $team: $count units")
            }
            
            // Simple ASCII map (top-down view)
            val mapSize = 50
            val scale = config.mapSize.first / mapSize
            val map = Array(mapSize) { CharArray(mapSize) { '.' } }
            
            w.forEach { (id, entity) ->
                val pos = entity.get<Pos>("pos")?.vec ?: return@forEach
                val team = entity.get<Team>("team")?.id ?: -1
                val x = (pos.first / scale).toInt().coerceIn(0, mapSize - 1)
                val y = (pos.second / scale).toInt().coerceIn(0, mapSize - 1)
                
                map[y][x] = when (team) {
                    0 -> 'P' // Player
                    1 -> '1'
                    2 -> '2'
                    3 -> '3'
                    else -> '?'
                }
            }
            
            map.forEach { row ->
                println(row.joinToString(""))
            }
        }
    }
    
    private suspend fun visualizeReplay() {
        // Enhanced replay visualization with controls
        visualize() // Reuse base visualization
    }
    
    private fun generateWorld(): World {
        val world = mutableMapOf<EntityId, Entity>()
        var nextId = 0
        
        // Spawn starting units for each team
        (0..3).forEach { team ->
            val teamSpawnPos = when (team) {
                0 -> Vec3(100f, 100f, 0f)
                1 -> Vec3(1900f, 100f, 0f)
                2 -> Vec3(100f, 1900f, 0f)
                3 -> Vec3(1900f, 1900f, 0f)
                else -> Vec3(1000f, 1000f, 0f)
            }
            
            // Commander
            world[nextId++] = entityOf(
                "type" to "commander",
                "pos" to Pos(teamSpawnPos),
                "team" to Team(team),
                "hp" to HP(1000f to 1000f),
                "dmg" to Dmg(50f),
                "range" to Range(100f)
            )
            
            // Starting units
            repeat(config.startingUnits) {
                val offset = Vec3(
                    (Math.random() - 0.5f) * 200f,
                    (Math.random() - 0.5f) * 200f,
                    0f
                )
                
                world[nextId++] = entityOf(
                    "type" to "scout",
                    "pos" to Pos(teamSpawnPos + offset),
                    "team" to Team(team),
                    "hp" to HP(50f to 50f),
                    "dmg" to Dmg(10f),
                    "range" to Range(75f),
                    "vel" to Vel(Vec3(0f, 0f, 0f))
                )
            }
        }
        
        // Spawn resources
        repeat(20) {
            val resourcePos = Vec3(
                (Math.random() * config.mapSize.first).toFloat(),
                (Math.random() * config.mapSize.second).toFloat(),
                0f
            )
            
            world[nextId++] = entityOf(
                "type" to "resource",
                "pos" to Pos(resourcePos),
                "resource" to "minerals",
                "amount" to 1000f
            )
        }
        
        return world
    }
    
    private fun emptyWorld(): World = emptyMap()
    
    private suspend fun connectToServer(address: String): Channel<Net> {
        // In real implementation, establish network connection
        return Channel(Channel.UNLIMITED)
    }
    
    private suspend fun networkServer() {
        // In real implementation, accept client connections
    }
    
    private fun loadReplay(): Flow<Cmd> = flow {
        // In real implementation, load from file
        emit(Cmd.Move(0, Vec3(100f, 100f, 0f)))
    }
}

// Extension for easy launching
suspend fun launchDenseRTS(
    mode: DenseRTSGame.GameMode = DenseRTSGame.GameMode.Solo,
    config: DenseRTSGame.GameConfig = DenseRTSGame.GameConfig()
) {
    DenseRTSGame(mode, config).launch()
}

// Example usage
fun main() = runBlocking {
    launchDenseRTS()
}