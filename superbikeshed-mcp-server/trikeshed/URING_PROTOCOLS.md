# Trikeshed io_uring Protocol Stack

## Overview

Trikeshed implements a first-class io_uring-based network protocol stack supporting modern protocols with zero-copy, multi-shot operations, and kernel bypass techniques. This is a pure Kotlin/Native implementation with only liburing as a dependency.

## Core Features

### io_uring Advanced Setup
```kotlin
// Initialize with cutting-edge features
val params = io_uring_params {
    flags = IORING_SETUP_SQPOLL or          // Kernel polling thread
            IORING_SETUP_CQSIZE or          // Custom CQ size
            IORING_SETUP_SINGLE_ISSUER or   // Single submitter optimization
            IORING_SETUP_DEFER_TASKRUN      // Defer task work
    cq_entries = 32768u                     // 32k completion queue
}
```

### Zero-Copy Architecture
- **Buffer Rings**: Pre-registered memory for zero-copy I/O
- **Fixed Files**: Registered file descriptors for reduced overhead
- **Multi-shot Operations**: Accept/receive without re-arming
- **Kernel TLS**: Offload encryption to kernel

## Supported Protocols

### 1. HTTP/3 with QUIC
```kotlin
val quic = urings.serveQUIC(443)
```
- UDP GSO/GRO for packet batching
- Multi-shot receive with buffer selection
- Zero-copy path for large transfers
- Built-in congestion control

### 2. HTTP/2
```kotlin
val http2 = urings.serveHTTP2(443)
```
- Kernel TLS for encryption offload
- Multi-shot accept for connection handling
- Stream multiplexing with io_uring
- HPACK compression in userspace

### 3. WebTransport
```kotlin
val webtransport = urings.serveWebTransport(443)
```
- Builds on HTTP/3 QUIC server
- Bidirectional streams
- Datagrams support
- Low-latency real-time communication

### 4. gRPC
```kotlin
val grpc = urings.servegRPC(50051)
```
- HTTP/2 transport
- Protocol buffers handling
- Streaming RPC support
- Load balancing ready

### 5. Memcached Binary Protocol
```kotlin
val memcached = urings.serveMemcached(11211)
```
- Fixed file optimization
- Zero-copy for large values
- Binary protocol efficiency
- Multi-get support

### 6. Redis RESP3
```kotlin
val redis = urings.serveRedis(6379)
```
- CPU affinity with SO_INCOMING_CPU
- Pipelining support
- Pub/Sub with io_uring
- Cluster protocol ready

### 7. MySQL Wire Protocol
```kotlin
val mysql = urings.serveMySQL(3306)
```
- Binary protocol support
- Prepared statements
- Result set streaming
- Connection pooling

### 8. PostgreSQL Wire Protocol
```kotlin
val postgresql = urings.servePostgreSQL(5432)
```
- TCP_NODELAY for low latency
- Extended query protocol
- COPY streaming
- Logical replication

### 9. ScyllaDB CQL
```kotlin
val scylla = urings.serveScylla(9042)
```
- Shard-per-core architecture
- Multiple io_uring instances
- Token-aware routing
- Prepared statements

### 10. NATS
```kotlin
val nats = urings.serveNATS(4222)
```
- Text-based protocol
- Pub/Sub messaging
- Request/Reply patterns
- Clustering support

### 11. Kafka Protocol
```kotlin
val kafka = urings.serveKafka(9092)
```
- SO_ZEROCOPY for large messages
- Batch processing
- Compression support
- Partition management

## Performance Optimizations

### Multi-Shot Operations
```kotlin
// Accept multiple connections with one syscall
io_uring_prep_multishot_accept(sqe, sock, null, null, 0)

// Receive multiple packets with one syscall
io_uring_prep_recv_multishot(sqe, sock, null, 0, 0)
```

### Buffer Ring Management
```kotlin
// Pre-allocate 1024 buffers of 64KB each
val bufRing = registerBufferRing(1024, 65536)
```

### Kernel TLS Offload
```kotlin
// Enable kTLS for hardware offload
enableKernelTLS(sock)
```

### CPU Affinity
```kotlin
// Pin to incoming CPU for cache locality
setsockopt(sock, SOL_SOCKET, SO_INCOMING_CPU, cpu)
```

## Comparison with Traditional Stacks

| Feature | Traditional | Trikeshed io_uring |
|---------|------------|-------------------|
| System Calls | Per operation | Batched |
| Memory Copies | Multiple | Zero-copy |
| Thread Model | Thread per connection | Single thread capable |
| Kernel Bypass | No | Partial |
| Hardware Offload | Limited | Full support |

## Integration with Trikeshed

### Concentric DHT Routing
All protocols integrate with the Kademlia-based routing:
```kotlin
// Route requests through DHT
val endpoint = concentricDHT.route(request.key)
protocol.forward(endpoint, request)
```

### Agent Quorum
Protocols participate in distributed consensus:
```kotlin
// Achieve quorum before writes
val decision = agentQuorum.propose(
    QuorumProposal(
        type = ProposalType.STATE_CHANGE,
        data = request.serialize()
    )
)
```

### Gossip Sphere
State synchronization across nodes:
```kotlin
// Spread protocol state updates
gossipSphere.spread(
    Rumor(
        origin = nodeId,
        data = protocolState.serialize()
    )
)
```

## Godbolt-like Features

### Compilation Visualization
- Real-time Kotlin → Native compilation
- io_uring syscall visualization
- Buffer allocation tracking
- Performance counter integration

### Protocol Analysis
- Packet flow visualization
- Latency heat maps
- Buffer utilization graphs
- Syscall trace analysis

## Future Enhancements

### Planned Protocols
- AMQP (RabbitMQ)
- MongoDB Wire Protocol
- Cassandra Native Protocol
- MQTT 5.0
- CoAP

### io_uring Features
- IORING_OP_URING_CMD for custom commands
- BPF integration for packet filtering
- Hardware queue support
- Direct I/O optimization

## Benchmarks

Expected performance (single core):
- HTTP/3: 1M+ requests/sec
- Redis: 2M+ ops/sec
- MySQL: 500K+ queries/sec
- Kafka: 1GB+/sec throughput

All while maintaining:
- <1ms P99 latency
- Zero allocation hot path
- Minimal CPU usage
- Full protocol compliance