package rtsgame.game

import rtsgame.core.*
import rtsgame.codec.*
import rtsgame.network.*
import trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*

/**
 * Game orchestrator using concentric network rings
 * Manages transitions between lobby, game, and observation
 */

class GameOrchestrator(
    private val playerId: PlayerId,
    private val playerName: String,
    private val config: OrchestratorConfig = OrchestratorConfig()
) {
    data class OrchestratorConfig(
        val nodeId: NodeId = NodeId(ByteArray(32) { it.toByte() }),
        val protocols: Set<Protocol> = setOf(
            Protocol.WebSocket,
            Protocol.UDP,
            Protocol.WebRTC
        ),
        val preferredProtocol: Protocol = Protocol.WebSocket,
        val capabilities: Set<Capability> = setOf(Capability.HOST, Capability.COMPUTE)
    )
    
    // Local node identity
    private val localNode = Node(
        id = config.nodeId,
        ring = Ring.OUTER,
        protocols = config.protocols,
        endpoint = "ws://localhost:8080/player/$playerId",
        capabilities = config.capabilities
    )
    
    // Concentric network
    private val network = ConcentricNetwork(localNode)
    
    // Current state
    private var currentRing = Ring.OUTER
    private var currentLobby: Lobby? = null
    private var currentGame: GameInstance? = null
    
    // ========================================================================
    // Main Flow
    // ========================================================================
    
    suspend fun start(): Flow<OrchestratorEvent> = flow {
        // Join outer ring for discovery
        emit(OrchestratorEvent.Connected)
        
        network.join(Ring.OUTER, config.preferredProtocol).collect { event ->
            when (event) {
                is RingEvent.NodeJoined -> {
                    emit(OrchestratorEvent.NodeDiscovered(event.node))
                }
                is RingEvent.StateUpdate -> {
                    // Handle discovery updates
                }
                else -> {}
            }
        }
    }
    
    // ========================================================================
    // Lobby Operations
    // ========================================================================
    
    suspend fun findOrCreateLobby(
        criteria: LobbyCriteria = LobbyCriteria()
    ): Result<Lobby> = runCatching {
        // Try to find existing lobby
        network.findLobby(criteria)?.let { lobby ->
            joinLobby(lobby)
            return@runCatching lobby
        }
        
        // Create new lobby
        createLobby("${playerName}'s Game", criteria)
    }
    
    private suspend fun createLobby(name: String, criteria: LobbyCriteria): Lobby {
        // Move to lobby ring
        network.moveToRing(currentRing, Ring.LOBBY)
        currentRing = Ring.LOBBY
        
        // Create lobby info
        val lobbyInfo = LobbyInfo(
            id = generateLobbyId(),
            name = name,
            host = playerName,
            players = listOf(playerName),
            settings = mapOf(
                "map" to (criteria.map ?: "default"),
                "maxPlayers" to (criteria.maxPlayers ?: 8),
                "mods" to criteria.mods
            )
        )
        
        currentLobby = Lobby(localNode, lobbyInfo)
        
        // Announce lobby
        broadcastLobbyUpdate()
        
        return currentLobby!!
    }
    
    private suspend fun joinLobby(lobby: Lobby) {
        // Move to lobby ring
        network.moveToRing(currentRing, Ring.LOBBY)
        currentRing = Ring.LOBBY
        
        // Request to join
        network.rpc(
            lobby.node.id,
            "joinLobby",
            mapOf("playerName" to playerName)
        )
        
        currentLobby = lobby
    }
    
    suspend fun leaveLobby() {
        currentLobby?.let { lobby ->
            network.rpc(
                lobby.node.id,
                "leaveLobby",
                mapOf("playerName" to playerName)
            )
            
            currentLobby = null
            
            // Move back to outer ring
            network.moveToRing(Ring.LOBBY, Ring.OUTER)
            currentRing = Ring.OUTER
        }
    }
    
    // ========================================================================
    // Game Operations
    // ========================================================================
    
    suspend fun startGame(): Result<GameInstance> = runCatching {
        val lobby = currentLobby ?: throw IllegalStateException("Not in lobby")
        
        // Only host can start
        if (lobby.info.host != playerName) {
            throw IllegalStateException("Only host can start game")
        }
        
        // Create game instance
        val gameId = generateGameId()
        val game = GameInstance(
            id = gameId,
            host = localNode,
            players = lobby.info.players.mapIndexed { idx, name ->
                PlayerState(
                    id = idx,
                    name = name,
                    team = idx % 4,
                    ready = false
                )
            },
            config = GameConfig(
                map = lobby.info.settings["map"] as String,
                tickRate = 60,
                startingUnits = 5
            )
        )
        
        currentGame = game
        
        // Move to game ring
        network.moveToRing(Ring.LOBBY, Ring.GAME)
        currentRing = Ring.GAME
        
        // Notify all players
        broadcastGameStart(game)
        
        // Start game loop
        launchGameLoop(game)
        
        game
    }
    
    suspend fun joinGame(gameId: String): Result<Unit> = runCatching {
        // Find game host
        val hosts = network.discoverGameHosts().toList()
        val gameHost = hosts.find { it.info.id == gameId }
            ?: throw NoSuchElementException("Game not found")
        
        // Request to join
        network.rpc(
            gameHost.node.id,
            "joinGame",
            mapOf(
                "playerName" to playerName,
                "gameId" to gameId
            )
        )
        
        // Move to game ring
        network.moveToRing(currentRing, Ring.GAME)
        currentRing = Ring.GAME
    }
    
    suspend fun observeGame(gameId: String): Flow<GameState> {
        // Move to observer ring
        network.moveToRing(currentRing, Ring.OBSERVER)
        currentRing = Ring.OBSERVER
        
        // Subscribe to game updates
        return network.observeGame(gameId)
    }
    
    // ========================================================================
    // Game Loop
    // ========================================================================
    
    private fun launchGameLoop(game: GameInstance) = CoroutineScope(Dispatchers.Default).launch {
        var world = generateInitialWorld(game)
        var tick = 0L
        
        val commandChannel = Channel<Cmd>(Channel.UNLIMITED)
        
        // Command collection
        launch {
            network.join(Ring.GAME, config.preferredProtocol).collect { event ->
                when (event) {
                    is RingEvent.StateUpdate -> {
                        val cmd = event.state as? Cmd
                        cmd?.let { commandChannel.send(it) }
                    }
                    else -> {}
                }
            }
        }
        
        // Main game loop
        while (isActive && game.state == GameInstance.State.PLAYING) {
            // Collect commands for this tick
            val commands = mutableListOf<Cmd>()
            while (true) {
                val cmd = commandChannel.tryReceive().getOrNull() ?: break
                if (cmd.t == tick) {
                    commands.add(cmd)
                }
            }
            
            // Simulate tick
            world = simulateTick(world, tick, commands)
            
            // Broadcast state to players
            if (tick % 3 == 0L) { // Every 3 ticks
                broadcastGameState(GameState(
                    gameId = game.id,
                    tick = tick,
                    world = world,
                    state = GameState.State.PLAYING
                ))
            }
            
            // Send to observers
            if (tick % 10 == 0L) { // Every 10 ticks
                broadcastToObservers(GameState(
                    gameId = game.id,
                    tick = tick,
                    world = filterForObservers(world),
                    state = GameState.State.PLAYING
                ))
            }
            
            tick++
            delay(1000L / game.config.tickRate)
        }
    }
    
    private suspend fun simulateTick(world: W, tick: Tick, commands: List<Cmd>): W {
        var w = world
        
        // Apply commands
        commands.forEach { cmd ->
            w = applyCommand(w, cmd)
        }
        
        // Run systems
        w = movementSystem(w, tick)
        w = combatSystem(w, tick)
        w = resourceSystem(w, tick)
        
        return w
    }
    
    // ========================================================================
    // Command Processing
    // ========================================================================
    
    private fun applyCommand(world: W, cmd: Cmd): W = when (cmd) {
        is Cmd.Mv -> world.toMutableMap().apply {
            cmd.e.forEach { id ->
                this[id]?.let { ent ->
                    this[id] = ent + ("target" to Pos(cmd.tgt))
                }
            }
        }
        
        is Cmd.Atk -> world.toMutableMap().apply {
            cmd.component1().forEach { id ->
                this[id]?.let { ent ->
                    this[id] = ent + ("attackTarget" to Tgt(cmd.tgt))
                }
            }
        }
        
        is Cmd.Bld -> {
            val newId = (world.keys.maxOrNull() ?: 0) + 1
            world + (newId to ent(
                "type" to Type(cmd.ut),
                "pos" to Pos(cmd.pos),
                "owner" to Owner(cmd.p),
                "hp" to HP(100f, 100f),
                "building" to Bldg(0f)
            ))
        }
        
        is Cmd.Hrv -> world.toMutableMap().apply {
            cmd.h.forEach { id ->
                this[id]?.let { ent ->
                    this[id] = ent + ("harvestTarget" to Tgt(cmd.r))
                }
            }
        }
        
        is Cmd.Stp -> world.toMutableMap().apply {
            cmd.e.forEach { id ->
                this[id]?.let { ent ->
                    val updated = ent.toMutableMap()
                    updated.remove("target")
                    updated.remove("attackTarget") 
                    updated.remove("harvestTarget")
                    this[id] = updated
                }
            }
        }
    }
    
    // ========================================================================
    // Systems
    // ========================================================================
    
    private fun movementSystem(world: W, tick: Tick): W = world.mapValues { (_, ent) ->
        ent.g<Pos>("pos")?.let { pos ->
            ent.g<Pos>("target")?.let { target ->
                val speed = ent.g<Spd>("speed")?.v ?: 1f
                val dir = (target.v - pos.v).norm()
                val dist = pos.v.dist(target.v)
                
                if (dist > speed) {
                    val newPos = pos.v + (dir * speed)
                    ent + ("pos" to Pos(newPos))
                } else {
                    ent.toMutableMap().apply {
                        put("pos", Pos(target.v))
                        remove("target")
                    }
                }
            } ?: ent
        } ?: ent
    }
    
    private fun combatSystem(world: W, tick: Tick): W = world // Simplified
    
    private fun resourceSystem(world: W, tick: Tick): W = world // Simplified
    
    // ========================================================================
    // Broadcasting
    // ========================================================================
    
    private suspend fun broadcastLobbyUpdate() {
        // Broadcast to lobby ring
        network.join(Ring.LOBBY, config.preferredProtocol)
    }
    
    private suspend fun broadcastGameStart(game: GameInstance) {
        // Notify all players in game ring
        game.players.forEach { player ->
            // Send game start notification
        }
    }
    
    private suspend fun broadcastGameState(state: GameState) {
        // Send to all players in game ring
    }
    
    private suspend fun broadcastToObservers(state: GameState) {
        // Send to observer ring
    }
    
    // ========================================================================
    // Helper Functions
    // ========================================================================
    
    private fun generateLobbyId() = "lobby_${System.currentTimeMillis()}"
    private fun generateGameId() = "game_${System.currentTimeMillis()}"
    
    private fun generateInitialWorld(game: GameInstance): W {
        val world = mutableMapOf<EntityId, Ent>()
        var nextId = 0
        
        // Spawn commanders
        game.players.forEach { player ->
            val spawnPos = getSpawnPosition(player.team)
            world[nextId++] = ent(
                "type" to Type("commander"),
                "pos" to Pos(spawnPos),
                "team" to Team(player.team),
                "owner" to Owner(player.id),
                "hp" to HP(1000f, 1000f),
                "dmg" to Dmg(50f)
            )
        }
        
        return world
    }
    
    private fun getSpawnPosition(team: TeamId): V3 = when (team) {
        0 -> V3(100f, 100f, 0f)
        1 -> V3(1900f, 100f, 0f)
        2 -> V3(100f, 1900f, 0f)
        3 -> V3(1900f, 1900f, 0f)
        else -> V3(1000f, 1000f, 0f)
    }
    
    private fun filterForObservers(world: W): W {
        // Remove sensitive information for observers
        return world.mapValues { (_, ent) ->
            ent.filterKeys { key ->
                key !in setOf("orders", "buildQueue", "research")
            }
        }
    }
}

// ============================================================================
// Data Classes
// ============================================================================

data class GameInstance(
    val id: String,
    val host: Node,
    val players: List<PlayerState>,
    val config: GameConfig,
    var state: State = State.WAITING
) {
    enum class State {
        WAITING, STARTING, PLAYING, PAUSED, ENDED
    }
}

data class PlayerState(
    val id: PlayerId,
    val name: String,
    val team: TeamId,
    var ready: Boolean
)

data class GameConfig(
    val map: String,
    val tickRate: Int,
    val startingUnits: Int
)

sealed class OrchestratorEvent {
    object Connected : OrchestratorEvent()
    data class NodeDiscovered(val node: Node) : OrchestratorEvent()
    data class LobbyJoined(val lobby: Lobby) : OrchestratorEvent()
    data class GameStarted(val game: GameInstance) : OrchestratorEvent()
    data class GameEnded(val reason: String) : OrchestratorEvent()
}