package rtsgame

import rtsgame.core.*
import rtsgame.game.*
import rtsgame.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * RTS game launcher demonstrating concentric network architecture
 */

object RTSLauncher {
    
    suspend fun launchSolo(playerName: String = "Player") {
        val orchestrator = GameOrchestrator(
            playerId = 0,
            playerName = playerName,
            config = GameOrchestrator.OrchestratorConfig(
                capabilities = setOf(Capability.HOST, Capability.COMPUTE)
            )
        )
        
        orchestrator.start().collect { event ->
            when (event) {
                is OrchestratorEvent.Connected -> {
                    println("✓ Connected to concentric network")
                    
                    // Create solo lobby
                    val lobby = orchestrator.findOrCreateLobby(
                        LobbyCriteria(
                            maxPlayers = 1,
                            map = "solo_map"
                        )
                    ).getOrThrow()
                    
                    println("✓ Created solo lobby: ${lobby.info.name}")
                    
                    // Start game immediately
                    val game = orchestrator.startGame().getOrThrow()
                    println("✓ Game started: ${game.id}")
                }
                
                is OrchestratorEvent.GameStarted -> {
                    println("🎮 Playing solo game")
                }
                
                is OrchestratorEvent.GameEnded -> {
                    println("🏁 Game ended: ${event.reason}")
                }
                
                else -> {}
            }
        }
    }
    
    suspend fun launchMultiplayer(
        playerName: String,
        host: Boolean = false
    ) {
        val orchestrator = GameOrchestrator(
            playerId = playerName.hashCode(),
            playerName = playerName
        )
        
        orchestrator.start().collect { event ->
            when (event) {
                is OrchestratorEvent.Connected -> {
                    println("$playerName: Connected to network")
                    
                    if (host) {
                        // Create lobby
                        val lobby = orchestrator.findOrCreateLobby(
                            LobbyCriteria(
                                minPlayers = 2,
                                maxPlayers = 8,
                                map = "default"
                            )
                        ).getOrThrow()
                        
                        println("$playerName: Created lobby '${lobby.info.name}'")
                        
                        // Wait for players
                        delay(5000)
                        
                        // Start game
                        orchestrator.startGame()
                    } else {
                        // Find and join lobby
                        delay(1000) // Let host create lobby
                        
                        val lobby = orchestrator.findOrCreateLobby(
                            LobbyCriteria(map = "default")
                        ).getOrThrow()
                        
                        println("$playerName: Joined lobby '${lobby.info.name}'")
                    }
                }
                
                is OrchestratorEvent.LobbyJoined -> {
                    println("$playerName: In lobby with ${event.lobby.info.players}")
                }
                
                is OrchestratorEvent.GameStarted -> {
                    println("$playerName: Game started!")
                }
                
                else -> {}
            }
        }
    }
    
    suspend fun launchObserver(
        observerName: String,
        gameId: String
    ) {
        val orchestrator = GameOrchestrator(
            playerId = observerName.hashCode(),
            playerName = observerName,
            config = GameOrchestrator.OrchestratorConfig(
                capabilities = setOf(Capability.OBSERVE)
            )
        )
        
        orchestrator.start().collect { event ->
            when (event) {
                is OrchestratorEvent.Connected -> {
                    println("$observerName: Connected as observer")
                    
                    // Start observing
                    orchestrator.observeGame(gameId).collect { state ->
                        println("$observerName: Game tick ${state.tick} - ${state.world.size} entities")
                    }
                }
                
                else -> {}
            }
        }
    }
    
    suspend fun demonstrateConcentricNetwork() {
        println("=== RTS Concentric Network Demo ===\n")
        
        // Launch multiple coroutines for different roles
        coroutineScope {
            // Host player
            launch {
                launchMultiplayer("Alice", host = true)
            }
            
            // Joining players
            launch {
                delay(2000)
                launchMultiplayer("Bob", host = false)
            }
            
            launch {
                delay(3000)
                launchMultiplayer("Charlie", host = false)
            }
            
            // Observer
            launch {
                delay(7000)
                launchObserver("Observer1", "game_123")
            }
            
            // Keep demo running
            delay(20000)
        }
    }
}

// ============================================================================
// Main entry points
// ============================================================================

suspend fun main() {
    RTSLauncher.demonstrateConcentricNetwork()
}

// JVM-specific launcher
suspend fun launchRTSGame(args: Array<String>) {
    when (args.getOrNull(0)) {
        "solo" -> RTSLauncher.launchSolo(args.getOrNull(1) ?: "Player")
        
        "host" -> RTSLauncher.launchMultiplayer(
            args.getOrNull(1) ?: "Host",
            host = true
        )
        
        "join" -> RTSLauncher.launchMultiplayer(
            args.getOrNull(1) ?: "Player",
            host = false
        )
        
        "observe" -> RTSLauncher.launchObserver(
            args.getOrNull(1) ?: "Observer",
            args.getOrNull(2) ?: "game_123"
        )
        
        "demo" -> RTSLauncher.demonstrateConcentricNetwork()
        
        else -> {
            println("""
                RTS Game - Concentric Network Architecture
                
                Usage:
                  solo [name]           - Start solo game
                  host [name]           - Host multiplayer game
                  join [name]           - Join multiplayer game
                  observe [name] [id]   - Observe game
                  demo                  - Run network demo
                
                Supported protocols:
                  - WebSocket (default)
                  - UDP
                  - WebRTC
                  - QUIC
                  - CouchDB
                  - IPFS
                  - SSH
                  - REST
                  - Wave
                
                Network rings:
                  - OUTER: Discovery/matchmaking
                  - LOBBY: Waiting players
                  - GAME: Active players
                  - OBSERVER: Spectators
                  - INNER: Core game state
            """.trimIndent())
        }
    }
}