# Channel & Coroutine Patterns - Simple Version

## CouchDB Service

```mermaid
flowchart TD
    subgraph "CouchDB Service"
        A[HTTP Request] -->|parse| B[Parsed Request]
        B -->|query| C[Database Query]
        C -->|stream| D[Document Stream]
        D -->|serialize| E[HTTP Response]
        
        B -.->|suspend for parsing| BP[Parser Continuation]
        C -.->|suspend for DB| DB[Database I/O]
        D -.->|Channel backpressure| CH[Document Channel]
    end
```

### Implementation
```kotlin
suspend fun handleCouchRequest(request: ByteArray): ByteArray {
    // Parse HTTP
    val parsed = suspendCoroutine { cont ->
        httpParser.parseAsync(request) { result ->
            cont.resume(result)
        }
    }
    
    // Query with connection from context
    val conn = coroutineContext[DbConnection]?.conn 
        ?: error("No DB connection")
    
    // Stream results through channel
    val docs = channelFlow {
        conn.query(parsed.query).forEach { doc ->
            send(doc) // Suspends on backpressure
        }
    }
    
    // Serialize response
    return buildResponse(docs)
}
```

---

## QUIC Service  

```mermaid
flowchart TD
    subgraph "QUIC Service"
        A[UDP Datagram] -->|demux| B[Stream Router]
        B -->|stream 1| C1[Stream Handler 1]
        B -->|stream 2| C2[Stream Handler 2]
        B -->|stream n| CN[Stream Handler N]
        
        C1 & C2 & CN -->|mux| D[Packet Builder]
        D -->|send| E[UDP Socket]
        
        B -.->|Channel per stream| CH[Stream Channels]
        D -.->|select for multiplexing| SEL[Channel Select]
    end
```

### Implementation
```kotlin
suspend fun handleQuic(socket: DatagramSocket) = coroutineScope {
    val streams = ConcurrentHashMap<Int, Channel<ByteArray>>()
    
    // Demux incoming
    launch {
        while (isActive) {
            val packet = socket.receive()
            val streamId = parseStreamId(packet)
            streams.getOrPut(streamId) { Channel() }
                .send(packet.data)
        }
    }
    
    // Mux outgoing
    launch {
        while (isActive) {
            select {
                streams.forEach { (id, channel) ->
                    channel.onReceive { data ->
                        socket.send(buildPacket(id, data))
                    }
                }
            }
        }
    }
}
```

---

## SSH Service

```mermaid
flowchart TD
    subgraph "SSH Service"
        A[TCP Socket] -->|decrypt| B[SSH Packets]
        B -->|route| C[Channel Router]
        
        C -->|shell| D1[Shell Channel]
        C -->|sftp| D2[SFTP Channel]  
        C -->|forward| D3[Port Forward]
        
        D1 & D2 & D3 -->|encrypt| E[TCP Socket]
        
        B -.->|suspend auth| AUTH[Auth Continuation]
        D1 -.->|bidirectional flow| BID[Shell I/O Channels]
    end
```

### Implementation
```kotlin
suspend fun handleSsh(socket: Socket) {
    // Auth
    val session = suspendCancellableCoroutine { cont ->
        sshAuth.authenticate(socket) { result ->
            if (result.isSuccess) cont.resume(result.session)
            else cont.resumeWithException(result.error)
        }
    }
    
    // Channel multiplexing
    coroutineScope {
        val channels = mutableMapOf<Int, Channel<ByteArray>>()
        
        // Read loop
        launch {
            while (isActive) {
                val packet = session.readPacket()
                channels[packet.channelId]?.send(packet.data)
            }
        }
        
        // Channel handlers
        session.onChannelOpen { id, type ->
            channels[id] = Channel()
            when (type) {
                "shell" -> launch { handleShell(channels[id]) }
                "sftp" -> launch { handleSftp(channels[id]) }
                "forward" -> launch { handleForward(channels[id]) }
            }
        }
    }
}
```

---

## io_uring Integration

```mermaid
flowchart TD
    subgraph "io_uring I/O"
        A[Read Request] -->|prepare| B[SQE Entry]
        B -->|submit| C[io_uring]
        C -->|complete| D[CQE Entry]
        D -->|resume| E[Read Complete]
        
        B -.->|store continuation| CONT[Continuation in userData]
        D -.->|retrieve continuation| RESUME[Resume Coroutine]
    end
```

### Implementation
```kotlin
suspend fun readWithUring(fd: Int, size: Int): ByteArray {
    return suspendCoroutineUninterceptedOrReturn { cont ->
        val sqe = ring.getSqe()
        val buffer = ByteBuffer.allocateDirect(size)
        
        sqe.prepareRead(fd, buffer, 0)
        sqe.userData = StoreContination(cont) // Store for completion
        
        ring.submit()
        COROUTINE_SUSPENDED
    }
}

// Completion handler (runs in separate coroutine)
suspend fun processCompletions() {
    while (true) {
        val cqe = ring.waitCqe()
        val cont = RetrieveContinuation<ByteArray>(cqe.userData)
        
        if (cqe.res < 0) {
            cont.resumeWithException(IOException("Read failed: ${cqe.res}"))
        } else {
            val buffer = cqe.getBuffer()
            cont.resume(buffer.toByteArray())
        }
        
        ring.cqeSeen(cqe)
    }
}
```

---

## Common Patterns

### 1. Simple Suspension
```kotlin
suspend fun doAsync(param: String): Result {
    return suspendCoroutine { cont ->
        asyncApi.call(param) { result ->
            cont.resume(result)
        }
    }
}
```

### 2. Channel Backpressure
```kotlin
val channel = Channel<Data>(
    capacity = Channel.BUFFERED,
    onBufferOverflow = BufferOverflow.SUSPEND
)
```

### 3. Context Usage
```kotlin
// Add to context
withContext(DbConnection(conn) + RequestId(id)) {
    doWork()
}

// Access from context
val conn = coroutineContext[DbConnection]?.conn
val reqId = coroutineContext[RequestId]?.id
```

### 4. Streaming with Channels
```kotlin
fun streamData() = channelFlow {
    while (hasMore()) {
        send(readNext()) // Suspends on backpressure
    }
}
```

### 5. Multiplexing with Select
```kotlin
select {
    channel1.onReceive { data ->
        process1(data)
    }
    channel2.onReceive { data ->
        process2(data)  
    }
}
```