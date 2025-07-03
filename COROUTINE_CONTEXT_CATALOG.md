# Coroutine Context Catalog & Router DSL

## Context Loadouts for Services

### 1. HTTP Service Context Loadout

```kotlin
// HTTP service requires: parser, session, connection pool, timeout
val httpContextLoadout = coroutineContext {
    + ParserContext(BBCursiveHttp())
    + SessionContext(cookieStore = InMemoryCookieStore())
    + ConnectionPoolContext(maxConnections = 100)
    + TimeoutContext(request = 30.seconds, idle = 5.minutes)
    + BufferStrategyContext(adaptive = true)
}

// Router DSL for HTTP
router {
    ingress<ByteArray> {
        parse { BBCursiveHttp.parse(it) }
        channel(capacity = Channel.BUFFERED)
    }
    
    route("/api/*") {
        context { httpContextLoadout }
        handle { request ->
            channelFlow {
                send(processApi(request))
            }
        }
    }
    
    egress<HttpResponse> {
        serialize { it.toByteArray() }
        channel(capacity = Channel.UNLIMITED)
    }
}
```

### 2. SSH Service Context Loadout

```kotlin
// SSH requires: crypto, auth state, channel registry, keep-alive
val sshContextLoadout = coroutineContext {
    + CryptoContext(
        ciphers = listOf(AES256_GCM, CHACHA20_POLY1305),
        keyExchange = listOf(CURVE25519_SHA256)
    )
    + AuthStateContext(methods = listOf(PUBLICKEY, PASSWORD))
    + ChannelRegistryContext(maxChannels = 256)
    + KeepAliveContext(interval = 30.seconds)
    + CompressionContext(algorithms = listOf(ZLIB, NONE))
}

// Router DSL for SSH
router {
    ingress<TcpConnection> {
        decrypt { connection ->
            suspendCoroutineUninterceptedOrReturn { cont ->
                sshDecryptor.decryptAsync(connection) { 
                    cont.resume(it)
                }
                COROUTINE_SUSPENDED
            }
        }
    }
    
    multiplex {
        on<ChannelOpen> { request ->
            context { sshContextLoadout + ChannelContext(request.channelId) }
            when (request.channelType) {
                "session" -> route(ShellHandler())
                "direct-tcpip" -> route(ForwardHandler())
                "sftp" -> route(SftpHandler())
            }
        }
    }
    
    egress<SshPacket> {
        encrypt { packet -> sshEncryptor.encrypt(packet) }
        channel(onBufferOverflow = BufferOverflow.SUSPEND)
    }
}
```

### 3. Database Service Context Loadout

```kotlin
// Database requires: connection pool, transaction, query cache
val dbContextLoadout = coroutineContext {
    + ConnectionPoolContext(
        min = 10,
        max = 50,
        validation = "SELECT 1"
    )
    + TransactionContext(isolation = READ_COMMITTED)
    + QueryCacheContext(size = 1000, ttl = 5.minutes)
    + MetricsContext(enabled = true)
}

// Router DSL for Database
router {
    ingress<Query> {
        validate { it.isValid() }
        channel(capacity = 100)
    }
    
    route {
        context { dbContextLoadout }
        
        select<Query.Select> {
            cache(key = { it.sql }) {
                withConnection { conn ->
                    conn.executeQuery(it)
                }
            }
        }
        
        mutate<Query.Insert, Query.Update, Query.Delete> {
            transaction {
                withConnection { conn ->
                    conn.executeMutation(it)
                }
            }
        }
    }
}
```

### 4. QUIC Service Context Loadout

```kotlin
// QUIC requires: congestion control, flow control, stream registry
val quicContextLoadout = coroutineContext {
    + CongestionControlContext(algorithm = BBR)
    + FlowControlContext(
        connectionWindow = 15.MB,
        streamWindow = 6.MB
    )
    + StreamRegistryContext(maxStreams = 100)
    + PacketNumberContext(spaces = listOf(INITIAL, HANDSHAKE, APPLICATION))
    + LossDetectionContext(
        initialRtt = 333.milliseconds,
        timerGranularity = 1.milliseconds
    )
}

// Router DSL for QUIC
router {
    ingress<UdpPacket> {
        demux { packet ->
            val header = parseHeader(packet)
            StreamKey(header.connId, header.streamId)
        }
    }
    
    stream { key ->
        context { 
            quicContextLoadout + 
            StreamContext(id = key.streamId) +
            ConnectionContext(id = key.connId)
        }
        
        handle { data ->
            produce {
                // Stream processing with flow control
                while (hasData()) {
                    waitForWindow()
                    send(readChunk())
                }
            }
        }
    }
    
    egress<QuicFrame> {
        pace { frame ->
            // Congestion control pacing
            context[CongestionControlContext].pace(frame)
        }
        batch { frames ->
            // Coalesce into packets
            QuicPacket(frames)
        }
    }
}
```

### 5. gRPC Service Context Loadout

```kotlin
// gRPC requires: HTTP/2, protobuf, interceptors, deadline
val grpcContextLoadout = coroutineContext {
    + Http2Context(
        maxConcurrentStreams = 100,
        initialWindowSize = 65535
    )
    + ProtobufContext(
        registry = MessageRegistry.default()
    )
    + InterceptorContext(
        interceptors = listOf(
            AuthInterceptor(),
            LoggingInterceptor(),
            MetricsInterceptor()
        )
    )
    + DeadlineContext(propagate = true)
}

// Router DSL for gRPC
router {
    ingress<Http2Stream> {
        parse { stream ->
            GrpcRequest(
                method = stream.headers[":path"],
                metadata = stream.headers,
                message = parseMessage(stream.data)
            )
        }
    }
    
    service("/api.Service/*") {
        context { grpcContextLoadout }
        
        unary("/GetUser") { request: GetUserRequest ->
            withDeadline {
                userService.getUser(request)
            }
        }
        
        serverStream("/ListUsers") { request: ListUsersRequest ->
            channelFlow {
                userService.listUsers(request).collect {
                    send(it)
                }
            }
        }
        
        clientStream("/CreateUsers") {
            flow<CreateUserRequest>().fold(BatchResult()) { acc, req ->
                acc + userService.createUser(req)
            }
        }
        
        bidiStream("/Chat") {
            channelFlow {
                launch { 
                    collect { msg -> 
                        broadcast(msg)
                    }
                }
            }
        }
    }
}
```

### 6. WebSocket Service Context Loadout

```kotlin
// WebSocket requires: frame handling, ping/pong, compression
val wsContextLoadout = coroutineContext {
    + FrameContext(
        maxFrameSize = 65536,
        allowExtensions = true
    )
    + PingPongContext(
        interval = 30.seconds,
        timeout = 10.seconds
    )
    + PerMessageDeflateContext(
        clientNoContext = true,
        serverNoContext = true
    )
    + SubprotocolContext(supported = listOf("chat", "binary"))
}

// Router DSL for WebSocket
router {
    ingress<HttpUpgrade> {
        validate { 
            it.headers["Upgrade"] == "websocket" &&
            it.headers["Connection"]?.contains("Upgrade") == true
        }
        
        handshake { request ->
            WebSocketHandshake(
                key = request.headers["Sec-WebSocket-Key"],
                protocol = selectProtocol(request),
                extensions = negotiateExtensions(request)
            )
        }
    }
    
    connection { ws ->
        context { wsContextLoadout }
        
        frame<TextFrame> {
            handle { frame ->
                when (ws.subprotocol) {
                    "chat" -> handleChatMessage(frame.text)
                    else -> echo(frame)
                }
            }
        }
        
        frame<BinaryFrame> {
            compress { frame ->
                context[PerMessageDeflateContext].compress(frame.data)
            }
        }
        
        control<PingFrame> {
            autoPong { ping ->
                PongFrame(ping.applicationData)
            }
        }
    }
}
```

### 7. Kafka Service Context Loadout

```kotlin
// Kafka requires: partitioning, compression, transaction coordination
val kafkaContextLoadout = coroutineContext {
    + PartitionerContext(
        strategy = RoundRobinPartitioner()
    )
    + CompressionContext(
        type = CompressionType.SNAPPY,
        level = 6
    )
    + TransactionalContext(
        transactionalId = "service-producer-1",
        timeout = 60.seconds
    )
    + ConsumerGroupContext(
        groupId = "service-consumers",
        sessionTimeout = 10.seconds
    )
}

// Router DSL for Kafka
router {
    producer {
        context { kafkaContextLoadout }
        
        send<Order> { order ->
            ProducerRecord(
                topic = "orders",
                key = order.id,
                value = order,
                partition = context[PartitionerContext].partition(order)
            )
        }
        
        batch<Event> { events ->
            transaction {
                events.forEach { event ->
                    send(ProducerRecord("events", event))
                }
            }
        }
    }
    
    consumer {
        context { kafkaContextLoadout }
        
        subscribe("orders", "events") {
            poll { records ->
                channelFlow {
                    records.forEach { record ->
                        send(record.value())
                        commit(record.offset())
                    }
                }
            }
        }
        
        rebalance {
            onAssigned { partitions ->
                log("Assigned: $partitions")
            }
            onRevoked { partitions ->
                commitSync()
            }
        }
    }
}
```

### 8. io_uring Service Context Loadout

```kotlin
// io_uring requires: ring configuration, operation batching, completion handling
val uringContextLoadout = coroutineContext {
    + RingContext(
        entries = 4096,
        flags = IORING_SETUP_SQPOLL or IORING_SETUP_IOPOLL
    )
    + BatchContext(
        maxBatch = 64,
        submitThreshold = 32
    )
    + CompletionContext(
        mode = CompletionMode.KERNEL_SIDE
    )
    + MemoryContext(
        registerBuffers = true,
        hugePages = true
    )
}

// Router DSL for io_uring
router {
    submission {
        context { uringContextLoadout }
        
        read { request ->
            suspendCoroutineUninterceptedOrReturn { cont ->
                val sqe = ring.getSqe()
                sqe.prepareRead(
                    fd = request.fd,
                    buf = request.buffer,
                    offset = request.offset
                )
                sqe.userData = cont.asOpaque()
                
                if (context[BatchContext].shouldSubmit()) {
                    ring.submit()
                }
                
                COROUTINE_SUSPENDED
            }
        }
        
        readv { request ->
            // Vectored I/O
            batchOperation {
                request.vectors.map { vec ->
                    submitRead(vec)
                }
            }
        }
    }
    
    completion {
        process {
            while (true) {
                val cqe = ring.waitCqe()
                val continuation = cqe.userData.toContinuation<ByteArray>()
                
                if (cqe.res < 0) {
                    continuation.resumeWithException(
                        IOException(errorString(-cqe.res))
                    )
                } else {
                    continuation.resume(
                        cqe.getBuffer().slice(0, cqe.res)
                    )
                }
                
                ring.cqeSeen(cqe)
            }
        }
    }
}
```

## Common Context Components

### Buffer Strategy Contexts
```kotlin
object BufferStrategies {
    val adaptiveContext = BufferStrategyContext { load ->
        when {
            load < 0.3 -> Channel.RENDEZVOUS
            load < 0.7 -> Channel.BUFFERED
            else -> Channel.UNLIMITED
        }
    }
    
    val boundedContext = BufferStrategyContext { _ ->
        Channel.Factory.BUFFERED
    }
    
    val conflatedContext = BufferStrategyContext { _ ->
        Channel.CONFLATED
    }
}
```

### Timeout Contexts
```kotlin
object TimeoutStrategies {
    val httpTimeout = TimeoutContext(
        connect = 10.seconds,
        request = 30.seconds,
        idle = 5.minutes
    )
    
    val databaseTimeout = TimeoutContext(
        query = 5.seconds,
        transaction = 30.seconds,
        idle = 1.minute
    )
    
    val streamingTimeout = TimeoutContext(
        initial = 10.seconds,
        idle = 30.seconds,
        total = Duration.INFINITE
    )
}
```

### Retry Contexts
```kotlin
object RetryStrategies {
    val exponentialBackoff = RetryContext(
        maxAttempts = 3,
        initialDelay = 100.milliseconds,
        maxDelay = 10.seconds,
        factor = 2.0,
        jitter = 0.1
    )
    
    val circuitBreaker = CircuitBreakerContext(
        failureThreshold = 5,
        successThreshold = 2,
        timeout = 30.seconds,
        halfOpenAttempts = 3
    )
}
```