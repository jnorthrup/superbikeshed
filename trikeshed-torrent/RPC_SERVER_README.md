# TrikeDownloader RPC Server

An aria2c-compatible JSON-RPC server with distributed object support using the RequestFactory pattern.

## 🏗️ Architecture

### Core Components

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   RPC Client    │    │  TorrentRpcServer │    │  TrikeDownloader │
│   (aria2c)      │◄──►│                  │◄──►│                 │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                                │
                                ▼
                       ┌──────────────────┐
                       │ RequestFactory   │
                       │   Service        │
                       └──────────────────┘
                                │
                                ▼
                       ┌──────────────────┐
                       │ Distributed      │
                       │   Objects        │
                       └──────────────────┘
                                │
                                ▼
                       ┌──────────────────┐
                       │ Node Coordinator │
                       └──────────────────┘
```

### Distributed Objects

1. **DownloadSession** - Immutable session state
2. **DistributedDownloadRegistry** - Central download registry
3. **DistributedProgressTracker** - Real-time progress tracking
4. **DistributedSessionManager** - Session persistence
5. **DistributedNodeCoordinator** - Multi-node coordination

## 🚀 Features

### aria2c-Compatible API

All standard aria2c RPC methods are supported:

```json
// Add HTTP download
{
  "jsonrpc": "2.0",
  "id": "req-1",
  "method": "aria2.addUri",
  "params": {
    "uris": ["https://example.com/file.zip"],
    "options": {
      "header": ["User-Agent: TrikeDownloader/1.0"],
      "timeout": 30000
    }
  }
}

// Add torrent download
{
  "jsonrpc": "2.0",
  "id": "req-2", 
  "method": "aria2.addTorrent",
  "params": {
    "torrent": "magnet:?xt=urn:btih:example",
    "uris": ["udp://tracker.example.com:1337"],
    "options": {
      "piece-length": 16384
    }
  }
}

// Get download status
{
  "jsonrpc": "2.0",
  "id": "req-3",
  "method": "aria2.tellStatus",
  "params": {
    "gid": "trike_1234567890_123",
    "keys": ["status", "completedLength", "totalLength", "downloadSpeed"]
  }
}
```

### Distributed Object Management

#### DownloadSession
```kotlin
data class DownloadSession(
    val sessionId: String,
    val nodeId: String,
    val downloads: Map<String, DownloadTask>,
    val stats: Map<String, DownloadProgress>,
    val metadata: Map<String, String>,
    val createdAt: Long,
    val lastModified: Long
)
```

#### DistributedDownloadRegistry
```kotlin
val registry = DistributedDownloadRegistry(requestFactory, "node-1")

// Create session
val session = registry.createSession("session-1", mapOf("user" to "alice"))

// Add download
registry.addDownload("session-1", "download-1", task)

// Update progress
registry.updateProgress("session-1", "download-1", progress)
```

#### DistributedProgressTracker
```kotlin
val tracker = DistributedProgressTracker(requestFactory, "node-1")

// Subscribe to progress updates
val progressFlow = tracker.subscribeToProgress("download-1")
progressFlow.collect { progress ->
    println("Progress: ${progress.downloadedBytes}/${progress.totalBytes}")
}

// Update progress
tracker.updateProgress("download-1", newProgress)
```

### Multi-Node Coordination

```kotlin
val coordinator = DistributedNodeCoordinator(requestFactory, "node-1", registry, tracker, sessionManager)

// Register node capabilities
coordinator.registerNode(setOf("http", "torrent", "ftp"))

// Distribute download across nodes
val targetNodes = coordinator.distributeDownload("session-1", "download-1", task)
```

## 📡 RPC Methods

### Download Management

| Method | Description | Parameters |
|--------|-------------|------------|
| `aria2.addUri` | Add HTTP/FTP download | `uris`, `options` |
| `aria2.addTorrent` | Add torrent download | `torrent`, `uris`, `options` |
| `aria2.remove` | Remove download | `gid` |
| `aria2.pause` | Pause download | `gid` |
| `aria2.resume` | Resume download | `gid` |

### Status Queries

| Method | Description | Parameters |
|--------|-------------|------------|
| `aria2.tellStatus` | Get download status | `gid`, `keys` |
| `aria2.tellActive` | List active downloads | `keys` |
| `aria2.tellWaiting` | List waiting downloads | `offset`, `num`, `keys` |
| `aria2.tellStopped` | List stopped downloads | `offset`, `num`, `keys` |

### Global Operations

| Method | Description | Parameters |
|--------|-------------|------------|
| `aria2.getGlobalStat` | Get global statistics | None |
| `aria2.pauseAll` | Pause all downloads | None |
| `aria2.resumeAll` | Resume all downloads | None |

### Session Management

| Method | Description | Parameters |
|--------|-------------|------------|
| `aria2.saveSession` | Save session state | `session-file` |
| `aria2.shutdown` | Graceful shutdown | None |
| `aria2.forceShutdown` | Force shutdown | None |

## 🔧 Distributed Object Methods

### Registry Methods

| Method | Description |
|--------|-------------|
| `registry.createSession` | Create new session |
| `registry.getSession` | Get session by ID |
| `registry.updateSession` | Update session atomically |
| `registry.deleteSession` | Delete session |
| `registry.listSessions` | List all sessions |
| `registry.addDownload` | Add download to session |
| `registry.removeDownload` | Remove download from session |
| `registry.updateProgress` | Update download progress |
| `registry.getProgress` | Get download progress |
| `registry.listDownloads` | List downloads in session |

### Progress Tracker Methods

| Method | Description |
|--------|-------------|
| `progress.subscribe` | Subscribe to progress updates |
| `progress.unsubscribe` | Unsubscribe from updates |
| `progress.update` | Update progress |
| `progress.get` | Get current progress |
| `progress.list` | List tracked downloads |

### Session Manager Methods

| Method | Description |
|--------|-------------|
| `session.save` | Save session to storage |
| `session.load` | Load session from storage |
| `session.delete` | Delete session from storage |
| `session.list` | List saved sessions |
| `session.backup` | Create backup of all sessions |
| `session.restore` | Restore sessions from backup |

### Coordinator Methods

| Method | Description |
|--------|-------------|
| `coordinator.register` | Register node |
| `coordinator.unregister` | Unregister node |
| `coordinator.listNodes` | List connected nodes |
| `coordinator.getCapabilities` | Get node capabilities |
| `coordinator.distribute` | Distribute download across nodes |
| `coordinator.sync` | Sync state across nodes |

## 🎯 Usage Examples

### Basic HTTP Download

```bash
# Start RPC server
./demo-rpc-server.kts

# Add HTTP download via curl
curl -X POST http://localhost:6800/jsonrpc \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "req-1",
    "method": "aria2.addUri",
    "params": {
      "uris": ["https://example.com/file.zip"]
    }
  }'
```

### Torrent Download with Trackers

```bash
curl -X POST http://localhost:6800/jsonrpc \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "req-2",
    "method": "aria2.addTorrent",
    "params": {
      "torrent": "magnet:?xt=urn:btih:example",
      "uris": [
        "udp://tracker.opentrackr.org:1337",
        "udp://tracker.openbittorrent.com:6969"
      ]
    }
  }'
```

### Monitor Download Progress

```bash
curl -X POST http://localhost:6800/jsonrpc \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "req-3",
    "method": "aria2.tellStatus",
    "params": {
      "gid": "trike_1234567890_123",
      "keys": ["status", "completedLength", "totalLength", "downloadSpeed"]
    }
  }'
```

### Multi-Node Setup

```kotlin
// Node 1: HTTP and torrent capabilities
val node1 = DistributedNodeCoordinator(requestFactory, "node-1", registry, tracker, sessionManager)
node1.registerNode(setOf("http", "torrent"))

// Node 2: HTTP only
val node2 = DistributedNodeCoordinator(requestFactory, "node-2", registry2, tracker2, sessionManager2)
node2.registerNode(setOf("http"))

// Distribute downloads based on capabilities
val httpTask = DownloadTask.HttpDownload(...)
val torrentTask = DownloadTask.TorrentDownload(...)

val httpNodes = node1.distributeDownload("session-1", "http-1", httpTask)
val torrentNodes = node1.distributeDownload("session-1", "torrent-1", torrentTask)
```

## 🔒 Security & Validation

### Method Validation

All RPC methods are validated before execution:

```kotlin
requestFactory.registerMethodValidator("aria2.addUri") { params ->
    // Validate parameters
    val uris = params["uris"] as? List<String>
    uris != null && uris.isNotEmpty()
}
```

### Session Isolation

Each session is isolated with its own mutex:

```kotlin
val lock = sessionLocks.getOrPut(sessionId) { Mutex() }
lock.withLock {
    // Atomic session operations
}
```

### Node Authentication

Nodes can be authenticated and authorized:

```kotlin
coordinator.registerNode(setOf("http", "torrent"), authToken = "secret-token")
```

## 📊 Monitoring & Metrics

### Global Statistics

```json
{
  "jsonrpc": "2.0",
  "id": "req-4",
  "method": "aria2.getGlobalStat"
}
```

Response:
```json
{
  "jsonrpc": "2.0",
  "id": "req-4",
  "result": {
    "downloadSpeed": 1048576,
    "uploadSpeed": 0,
    "numActive": 2,
    "numWaiting": 1,
    "numStopped": 5,
    "numStoppedTotal": 5
  }
}
```

### Real-Time Progress

```kotlin
val progressFlow = tracker.subscribeToProgress("download-1")
progressFlow.collect { progress ->
    println("${progress.downloadedBytes}/${progress.totalBytes} (${progress.downloadSpeed} B/s)")
}
```

## 🚀 Performance Features

### Concurrent Downloads

- Multiple downloads run concurrently
- Each download uses its own coroutine
- Progress updates are non-blocking

### Distributed Scaling

- Downloads can be distributed across nodes
- Load balancing based on node capabilities
- Automatic failover to available nodes

### Memory Efficiency

- Immutable session objects
- Lazy loading of download data
- Automatic cleanup of completed downloads

## 🔧 Configuration

### RPC Server Configuration

```kotlin
val rpcServer = TorrentRpcServer(
    downloader = TrikeDownloader(),
    requestFactory = RequestFactoryServiceImpl(context),
    config = RpcServerConfig(
        maxConcurrentRequests = 100,
        requestTimeout = 30000L,
        enableValidation = true,
        enableMetrics = true
    )
)
```

### Node Configuration

```kotlin
val coordinator = DistributedNodeCoordinator(
    requestFactory = requestFactory,
    nodeId = "node-1",
    registry = registry,
    tracker = tracker,
    sessionManager = sessionManager,
    config = NodeConfig(
        capabilities = setOf("http", "torrent", "ftp"),
        maxConcurrentDownloads = 10,
        heartbeatInterval = 5000L,
        sessionTimeout = 300000L
    )
)
```

## 🧪 Testing

### Unit Tests

```kotlin
@Test
fun `test addUri RPC method`() = runTest {
    val rpcServer = TorrentRpcServer(downloader, requestFactory)
    
    val request = RpcRequest(
        id = "test-1",
        method = "aria2.addUri",
        params = mapOf("uris" to listOf("https://example.com/file.zip"))
    )
    
    val response = rpcServer.handleRpcRequest(request)
    
    assertNotNull(response.result)
    assertNull(response.error)
}
```

### Integration Tests

```kotlin
@Test
fun `test distributed session management`() = runTest {
    val registry = DistributedDownloadRegistry(requestFactory, "test-node")
    
    val session = registry.createSession("test-session")
    assertNotNull(session)
    
    val task = DownloadTask.HttpDownload(id = "test-1", url = "https://example.com")
    val added = registry.addDownload("test-session", "test-1", task)
    assertTrue(added)
    
    val downloads = registry.listDownloads("test-session")
    assertEquals(1, downloads.size)
}
```

## 🎉 Benefits

### For Users
- **Familiar Interface**: aria2c-compatible API
- **Multi-Protocol**: HTTP, HTTPS, FTP, BitTorrent
- **Real-Time Progress**: Live progress updates
- **Session Persistence**: Resume downloads after restart

### For Developers
- **Distributed Architecture**: Scale across multiple nodes
- **Type Safety**: Kotlin with RequestFactory pattern
- **Extensible**: Easy to add new protocols
- **Testable**: Comprehensive test coverage

### For Operations
- **Monitoring**: Built-in metrics and statistics
- **Reliability**: Automatic failover and recovery
- **Security**: Method validation and authentication
- **Performance**: Concurrent downloads and zero-copy operations

This RPC server provides a **production-ready foundation** for distributed download management with aria2c compatibility and enterprise-grade features. 