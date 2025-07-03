# Context-Driven Execution (No Selector Reactors)

## Context Must Contain Execution Strategy

### 1. Direct Continuation Context

```kotlin
// Context must contain the continuation strategy, not rely on external reactor
sealed class ExecutionContext {
    // Direct suspension - no reactor needed
    data class DirectSuspension(
        val dispatcher: CoroutineDispatcher = Dispatchers.IO
    ) : ExecutionContext() {
        suspend fun <T> execute(block: suspend () -> T): T = 
            withContext(dispatcher) { block() }
    }
    
    // Thread-per-request - no selector
    data class ThreadPerRequest(
        val threadFactory: ThreadFactory = Executors.defaultThreadFactory()
    ) : ExecutionContext() {
        suspend fun <T> execute(block: suspend () -> T): T = 
            suspendCoroutine { cont ->
                threadFactory.newThread {
                    runBlocking {
                        cont.resume(block())
                    }
                }.start()
            }
    }
    
    // Coroutine-per-operation - no central reactor
    data class CoroutinePerOp(
        val scope: CoroutineScope = GlobalScope
    ) : ExecutionContext() {
        suspend fun <T> execute(block: suspend () -> T): T {
            return scope.async { block() }.await()
        }
    }
}
```

### 2. HTTP Context with Embedded Execution

```kotlin
// HTTP context contains its own execution model
data class HttpExecutionContext(
    val parser: suspend (ByteArray) -> HttpRequest,
    val handler: suspend (HttpRequest) -> HttpResponse,
    val serializer: suspend (HttpResponse) -> ByteArray
) {
    // No reactor - direct coroutine execution
    suspend fun handleConnection(socket: Socket) = coroutineScope {
        val input = socket.getInputStream()
        val output = socket.getOutputStream()
        
        // Direct read - no selector
        val requestBytes = suspendCoroutine<ByteArray> { cont ->
            launch(Dispatchers.IO) {
                val buffer = ByteArray(8192)
                val read = input.read(buffer)
                cont.resume(buffer.sliceArray(0 until read))
            }
        }
        
        // Parse in context
        val request = parser(requestBytes)
        
        // Handle in context
        val response = handler(request)
        
        // Write directly
        val responseBytes = serializer(response)
        suspendCoroutine<Unit> { cont ->
            launch(Dispatchers.IO) {
                output.write(responseBytes)
                output.flush()
                cont.resume(Unit)
            }
        }
    }
}
```

### 3. Database Context with Connection-Per-Coroutine

```kotlin
// Database context owns connections, not a reactor
data class DatabaseExecutionContext(
    val connectionFactory: () -> Connection,
    val releaseStrategy: (Connection) -> Unit = { it.close() }
) {
    // Each coroutine gets its own connection
    suspend fun <T> withConnection(block: suspend (Connection) -> T): T {
        val connection = suspendCoroutine<Connection> { cont ->
            // Get connection in IO thread
            launch(Dispatchers.IO) {
                cont.resume(connectionFactory())
            }
        }
        
        return try {
            block(connection)
        } finally {
            withContext(Dispatchers.IO) {
                releaseStrategy(connection)
            }
        }
    }
    
    // No connection pool reactor - direct allocation
    suspend fun query(sql: String): ResultSet = withConnection { conn ->
        suspendCoroutine { cont ->
            // Direct JDBC call, no reactor
            val stmt = conn.createStatement()
            val rs = stmt.executeQuery(sql)
            cont.resume(rs)
        }
    }
}
```

### 4. io_uring Context with Direct Ring Access

```kotlin
// io_uring context contains ring, not reactor
data class IoUringExecutionContext(
    val ring: IoUring,
    val completionStrategy: CompletionStrategy
) {
    sealed class CompletionStrategy {
        // Busy wait - no reactor
        object BusyWait : CompletionStrategy()
        
        // Kernel thread - no reactor  
        object KernelThread : CompletionStrategy()
        
        // Coroutine-per-completion
        data class CoroutinePerCompletion(
            val dispatcher: CoroutineDispatcher
        ) : CompletionStrategy()
    }
    
    suspend fun read(fd: Int, buffer: ByteBuffer, offset: Long): Int {
        return when (completionStrategy) {
            is CompletionStrategy.BusyWait -> {
                suspendCoroutine { cont ->
                    val sqe = ring.getSqe()
                    sqe.prepareRead(fd, buffer, offset)
                    sqe.userData = cont.hashCode().toLong()
                    ring.submit()
                    
                    // Busy wait - no reactor
                    while (true) {
                        val cqe = ring.peekCqe() ?: continue
                        if (cqe.userData == cont.hashCode().toLong()) {
                            ring.cqeSeen(cqe)
                            cont.resume(cqe.res)
                            break
                        }
                    }
                }
            }
            
            is CompletionStrategy.KernelThread -> {
                // Let kernel thread handle it
                suspendCoroutineUninterceptedOrReturn { cont ->
                    val sqe = ring.getSqe()
                    sqe.prepareRead(fd, buffer, offset)
                    sqe.userData = cont.asOpaque()
                    sqe.flags = sqe.flags or IOSQE_ASYNC
                    ring.submit()
                    COROUTINE_SUSPENDED
                }
            }
            
            is CompletionStrategy.CoroutinePerCompletion -> {
                val result = CompletableDeferred<Int>()
                
                // Launch coroutine to wait for completion
                launch(completionStrategy.dispatcher) {
                    val cqe = ring.waitCqe()
                    result.complete(cqe.res)
                    ring.cqeSeen(cqe)
                }
                
                // Submit
                val sqe = ring.getSqe()
                sqe.prepareRead(fd, buffer, offset)
                ring.submit()
                
                result.await()
            }
        }
    }
}
```

### 5. QUIC Context with Stream-Local Execution

```kotlin
// QUIC context with per-stream execution, no global reactor
data class QuicExecutionContext(
    val congestionControl: CongestionControl,
    val streamFactory: (StreamId) -> QuicStream
) {
    // Each stream has its own execution context
    inner class QuicStream(val id: StreamId) {
        private val sendChannel = Channel<ByteArray>(Channel.UNLIMITED)
        private val recvChannel = Channel<ByteArray>(Channel.UNLIMITED)
        
        // Stream-local execution, no reactor
        suspend fun send(data: ByteArray) {
            // Direct congestion control check
            while (!congestionControl.canSend(data.size)) {
                delay(1) // Simple backoff, no reactor
            }
            
            sendChannel.send(data)
            
            // Direct packet creation
            launch {
                val packet = createPacket(id, data)
                directSend(packet)
            }
        }
        
        suspend fun receive(): ByteArray = recvChannel.receive()
        
        // No stream multiplexing reactor
        private suspend fun directSend(packet: QuicPacket) {
            withContext(Dispatchers.IO) {
                socket.send(packet.toDatagramPacket())
            }
        }
    }
}
```

### 6. SSH Context with Channel-Embedded Execution

```kotlin
// SSH context where each channel owns its execution
data class SshExecutionContext(
    val transport: SshTransport,
    val channelAllocator: () -> SshChannel
) {
    inner class SshChannel(val channelId: Int) {
        // Channel owns its coroutines, not a reactor
        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        
        suspend fun execute(command: String): String {
            // Direct execution in channel scope
            return scope.async {
                val packet = SshPacket.ChannelRequest(
                    channelId = channelId,
                    requestType = "exec",
                    command = command
                )
                
                // Direct send, no reactor
                transport.send(packet)
                
                // Direct receive, no reactor
                val response = transport.receive()
                response.data.decodeToString()
            }.await()
        }
        
        fun startShell() {
            // Launch shell handler in channel scope
            scope.launch {
                while (isActive) {
                    val input = readInput()
                    val output = processCommand(input)
                    writeOutput(output)
                }
            }
        }
    }
}
```

### 7. WebSocket Context with Frame-by-Frame Execution

```kotlin
// WebSocket with direct frame handling, no reactor
data class WebSocketExecutionContext(
    val frameHandler: suspend (Frame) -> Unit,
    val pingInterval: Duration = 30.seconds
) {
    suspend fun handleConnection(socket: Socket) = coroutineScope {
        val input = socket.getInputStream()
        val output = socket.getOutputStream()
        
        // Ping task - runs independently, no reactor
        val pingJob = launch {
            while (isActive) {
                delay(pingInterval)
                val pingFrame = Frame.Ping(Random.nextBytes(4))
                output.write(pingFrame.toBytes())
            }
        }
        
        // Frame reading - direct, no reactor
        while (isActive) {
            val frame = suspendCoroutine<Frame> { cont ->
                launch(Dispatchers.IO) {
                    val header = readFrameHeader(input)
                    val payload = readPayload(input, header.length)
                    cont.resume(Frame.parse(header, payload))
                }
            }
            
            // Handle frame directly
            when (frame) {
                is Frame.Text -> frameHandler(frame)
                is Frame.Binary -> frameHandler(frame)
                is Frame.Close -> break
                is Frame.Ping -> {
                    val pong = Frame.Pong(frame.data)
                    output.write(pong.toBytes())
                }
                is Frame.Pong -> {} // Ignore
            }
        }
        
        pingJob.cancel()
    }
}
```

### 8. Kafka Context with Partition-Local Execution

```kotlin
// Kafka with partition-scoped execution, no central reactor
data class KafkaExecutionContext(
    val bootstrapServers: String,
    val producerConfig: Map<String, Any> = emptyMap(),
    val consumerConfig: Map<String, Any> = emptyMap()
) {
    // Each partition gets its own execution scope
    inner class PartitionContext(
        val topic: String,
        val partition: Int
    ) {
        private val scope = CoroutineScope(Dispatchers.IO)
        
        fun startConsumer(handler: suspend (ConsumerRecord<*, *>) -> Unit) {
            scope.launch {
                val consumer = KafkaConsumer<ByteArray, ByteArray>(
                    consumerConfig + mapOf(
                        "bootstrap.servers" to bootstrapServers,
                        "enable.auto.commit" to "false"
                    )
                )
                
                // Assign specific partition - no rebalance reactor
                consumer.assign(listOf(TopicPartition(topic, partition)))
                
                // Direct poll loop - no reactor
                while (isActive) {
                    val records = consumer.poll(Duration.ofMillis(100))
                    
                    for (record in records) {
                        // Handle each record in partition scope
                        launch { handler(record) }
                    }
                    
                    // Direct commit
                    consumer.commitSync()
                }
            }
        }
        
        suspend fun produce(key: ByteArray, value: ByteArray) {
            withContext(Dispatchers.IO) {
                val producer = KafkaProducer<ByteArray, ByteArray>(
                    producerConfig + mapOf(
                        "bootstrap.servers" to bootstrapServers
                    )
                )
                
                // Direct send to partition - no partitioner reactor
                val record = ProducerRecord(topic, partition, key, value)
                
                suspendCoroutine<RecordMetadata> { cont ->
                    producer.send(record) { metadata, exception ->
                        if (exception != null) {
                            cont.resumeWithException(exception)
                        } else {
                            cont.resume(metadata)
                        }
                    }
                }
            }
        }
    }
}
```

## Key Principles

1. **Context Contains Execution** - The context itself knows how to execute, not relying on external reactors
2. **Direct Suspension** - Use `suspendCoroutine` and `suspendCoroutineUninterceptedOrReturn` directly
3. **Scope-Per-Resource** - Each connection/channel/stream gets its own CoroutineScope
4. **No Central Loop** - No selector loop, epoll reactor, or central event dispatcher
5. **Coroutine-Per-Operation** - Each operation can be its own coroutine
6. **Embedded Strategies** - Execution strategies are part of the context data

This approach means:
- No reactor threads
- No selector registration/deregistration
- Direct OS calls from coroutines
- Context-local execution decisions
- Natural backpressure through coroutine suspension