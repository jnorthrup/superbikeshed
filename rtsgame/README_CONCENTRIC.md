# RTS Game - Concentric Network Architecture

## Overview

The RTS game uses a **concentric ring network** architecture inspired by Kademlia DHT, allowing players to meet and interact across multiple protocol mediums for lobby, game, observation, and RPC communication.

## Architecture

### Network Rings

The network is organized into concentric rings based on purpose and distance:

```
    INNER (0-10)     - Core game state
      |
    GAME (11-50)     - Active players
      |  
    LOBBY (51-100)   - Waiting players
      |
    OBSERVER (101-200) - Spectators
      |
    OUTER (201+)     - Discovery/matchmaking
```

### Supported Protocols

Players can connect using any of these protocols:
- **WebSocket** (default) - Browser-friendly bidirectional communication
- **UDP** - Low-latency game commands
- **WebRTC** - P2P connections between players
- **QUIC** - Modern multiplexed streams
- **CouchDB** - Persistent game state
- **IPFS** - Distributed content delivery
- **SSH** - Secure tunneled connections
- **REST** - Simple HTTP-based communication
- **Wave** - Custom streaming protocol

### Core Components

#### 1. **ConcentricNetwork**
- Manages ring topology and routing
- Protocol-agnostic message passing
- Automatic node discovery
- Ring transitions (lobby → game → observer)

#### 2. **GameOrchestrator**
- High-level game flow management
- Lobby creation and joining
- Game lifecycle (waiting → playing → ended)
- Observer mode support

#### 3. **Type System**
- TrikeShed-based core types
- CCEK (CoroutineContext.Element.Key) for context propagation
- Dense functional patterns
- Binary codec with delta compression

## Usage

### Running the Game

```bash
# Demo concentric network
./gradlew runRTS

# Solo game
./gradlew runSolo

# Host multiplayer
./gradlew runHost

# Join multiplayer
./gradlew runJoin
```

### Command Line

```bash
# Solo game
java -jar rtsgame.jar solo [playerName]

# Host game
java -jar rtsgame.jar host [playerName]

# Join game
java -jar rtsgame.jar join [playerName]

# Observe game
java -jar rtsgame.jar observe [observerName] [gameId]

# Run demo
java -jar rtsgame.jar demo
```

## Code Structure

### Core Types (`rtsgame.core`)
- `RTSTypes.kt` - Game-specific types built on TrikeShed
- `TrikeShedTypes.kt` - Platform integration (if separate)

### Codec (`rtsgame.codec`)
- `RTSCodec.kt` - Binary serialization with varint encoding
- Delta compression for network efficiency

### Network (`rtsgame.network`)
- `ConcentricNetwork.kt` - Ring-based P2P network
- `NetworkEngine.kt` - Deterministic lockstep simulation

### Game (`rtsgame.game`)
- `GameOrchestrator.kt` - High-level game management

## Protocol Details

### Message Flow

1. **Discovery Phase** (OUTER ring)
   - Nodes announce presence
   - Exchange capability information
   - Find suitable lobbies/games

2. **Lobby Phase** (LOBBY ring)
   - Create/join lobbies
   - Configure game settings
   - Ready checks

3. **Game Phase** (GAME ring)
   - Lockstep simulation
   - Command distribution
   - State synchronization

4. **Observer Phase** (OBSERVER ring)
   - Filtered game state
   - Reduced update frequency
   - No input capability

### RPC Interface

```kotlin
// Get game info
rpc("getGameInfo", emptyMap()) -> GameInfo

// Join lobby
rpc("joinLobby", mapOf("playerName" to name)) -> Result

// Get neighbors
rpc("getNeighbors", mapOf("ring" to Ring.GAME)) -> List<Node>
```

## Network Resilience

- **Multi-protocol support** - Fallback if one protocol fails
- **Ring isolation** - Issues in one ring don't affect others
- **Automatic routing** - Find best path through network
- **Node capabilities** - Match nodes with required features

## Example Flow

```kotlin
// 1. Create orchestrator
val orchestrator = GameOrchestrator(playerId, playerName)

// 2. Start and connect
orchestrator.start().collect { event ->
    when (event) {
        is Connected -> // Find or create lobby
        is LobbyJoined -> // Wait for players
        is GameStarted -> // Play game
    }
}

// 3. Game automatically handles:
// - Ring transitions
// - Protocol selection
// - State synchronization
// - Observer broadcasts
```

## Performance Considerations

- **Delta compression** reduces bandwidth by 60-80%
- **Ring distance** keeps related nodes close
- **Protocol selection** optimizes for use case
- **Lazy state updates** for observers

## Future Enhancements

- Blockchain integration for persistent state
- CRDT-based eventual consistency
- Advanced matchmaking algorithms
- Replay system using event sourcing
- Mobile protocol optimizations