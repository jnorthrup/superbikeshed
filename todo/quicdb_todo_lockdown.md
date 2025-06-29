# QUICDB Todo Items - Locked Down

**Status**: LOCKED DOWN - All QUICDB-related todo items consolidated and frozen

**Date**: 2025-01-27

**Purpose**: Consolidate all QUICDB-related todo items from MASTER_TODO.md into a single authoritative source to prevent fragmentation and ensure consistent tracking.

## Consolidated QUICDB Todo Items

### 1. OpenSSH over QUIC and SCTP via WebRTC Research Project

**Status**: RESEARCH PHASE - Protocol analysis and standards investigation

**Primary Goal**: Research and implement OpenSSH as a first-class project with QUIC transport integration and explore SCTP delivery through WebRTC channels.

**OpenSSH QUIC Integration**:
- **Protocol Analysis**: Research what QUIC needs for SSH transport (existing SSH over QUIC proposals, RFC drafts)
- **TrikeShed SSH Implementation**: Leverage existing `borg.trikeshed.net.ssh.SSHProtocol` and integrate with `borg.trikeshed.net.quic`
- **Connection Multiplexing**: Utilize QUIC streams for SSH channel multiplexing (replacing TCP-based SSH channels)
- **Latency Optimization**: Zero-RTT connection establishment for SSH sessions
- **Reliability Features**: QUIC's built-in connection migration for mobile SSH sessions

**SCTP via WebRTC Research**:
- **WebRTC DataChannel Analysis**: Investigate SCTP delivery through WebRTC DataChannel API
- **Browser Compatibility**: Research SCTP support in modern browsers (Chrome, Firefox, Safari, Edge)
- **TrikeShed Integration**: Design SCTP abstraction layer compatible with TrikeShed networking stack
- **Use Cases**: Multi-homing, ordered/unordered delivery, message boundaries for SSH
- **Performance Characteristics**: Compare SCTP vs TCP vs QUIC for SSH workloads

**Implementation Strategy**:
- **Phase 1**: SSH over QUIC protocol design and TrikeShed integration
- **Phase 2**: WebRTC SCTP research and proof-of-concept implementation
- **Phase 3**: Cross-platform compatibility (Native, JVM, JS/WASM with WebRTC)
- **Phase 4**: Performance benchmarking and protocol optimization

**Research Areas**:
- **SSH over QUIC Standards**: IETF drafts, existing implementations (OpenSSH proposals, libssh QUIC)
- **QUIC Stream Management**: Bidirectional streams, flow control, congestion control for SSH
- **SCTP Message Semantics**: Partial reliability, message boundaries, multi-streaming
- **WebRTC SCTP Limitations**: Browser sandboxing, API restrictions, performance constraints
- **Security Considerations**: TLS 1.3 integration, key exchange over QUIC, SCTP security models

**Integration Points**:
- Existing TrikeShed SSH implementation in `borg.trikeshed.net.ssh`
- QUIC implementation in `borg.trikeshed.net.quic`
- TLS 1.3 crypto stack in `borg.trikeshed.crypto`
- URing optimizations for Linux native performance

### 2. Complete URing QUIC Implementation Backlog

**Status**: IMPLEMENTATION PHASE - Core QUIC packet processing and connection management

**Primary Goal**: Address incomplete URing QUIC implementation in `superbikeshed-mcp-server/trikeshed/src/linuxX64Main/kotlin/`.

**Critical TODOs to complete**:
- `URingProtocols.kt`: Implement missing `stats(): ServerStats = TODO()` in all protocol servers (QUICServer, HTTP2Server, MemcachedServer, RedisServer, etc.)
- `URingQUIC.kt`: Complete QUIC packet parsing, handshake completion, and application data processing
- `URingHTTP2.kt`: Implement server push, TLS setup, and TLS handshake functions
- `URingHTTP2Bridge.kt`: Complete metrics implementation and server push functionality

**Priority items**:
1. Complete core QUIC packet processing and connection state management
2. Implement proper QUIC handshake with TLS integration
3. Add comprehensive metrics and monitoring
4. Complete HTTP/2 over QUIC (HTTP/3) implementation
5. Add proper error handling and connection lifecycle management

**Integration points**:
- Ensure compatibility with existing `borg.trikeshed.net.quic` implementation
- Complete io_uring buffer ring management and zero-copy optimizations
- Add proper testing and benchmarking for performance validation

### 3. QUIC Protocol Implementation Completion

**Status**: CORE IMPLEMENTATION PHASE - Congestion control and loss recovery

**Primary Goal**: Complete the QUIC protocol implementation with full congestion control, loss recovery, and TLS integration.

**Current State**: Basic protocol structure exists with configuration-only implementations

**Missing Components**:
- **Congestion Control Algorithms**: Implement cubic, BBR, and Reno algorithms with proper RTT measurement
- **Loss Detection and Recovery**: Packet loss detection, retransmission, and recovery mechanisms
- **TLS 1.3 Cryptographic Operations**: Complete handshake, encryption, and key exchange implementation
- **Real 0-RTT Support**: Proper zero-round-trip connection establishment with session resumption
- **RTT Measurement and Pacing**: Accurate round-trip time calculation and packet pacing

**Implementation Requirements**:
- **URing Integration**: Use io_uring for high-performance packet processing and crypto operations
- **Memory Efficiency**: Zero-copy packet handling with efficient buffer management
- **Cross-Platform Support**: Native Linux with URing, JVM fallback, JS/WASM compatibility
- **Performance Goals**: Sub-millisecond connection establishment, high-throughput data transfer

**Integration Points**:
- Existing QUIC protocol interfaces in `borg.trikeshed.net.quic`
- TLS 1.3 implementation in `borg.trikeshed.crypto`
- URing infrastructure for Linux native performance
- HTTP/3 integration for web transport

### 4. KMP RequestFactory from Scratch using TrikeShed Scanner and URing IO

**Status**: DESIGN PHASE - Architecture and scanner integration planning

**Primary Goal**: Design and implement a working RequestFactory from scratch in Kotlin Multiplatform using TrikeShed scanner architecture and URing-oriented IO designs.

**Core Architecture**:
- **TrikeShed Scanner Integration**: Leverage existing JSON scanner patterns in `Trikeshed/src/commonMain/kotlin/borg/trikeshed/parse/json/TrikeShedJsonScanner.kt` for HTTP request/response parsing
- **URing-First IO Design**: Built around io_uring paradigms for maximum performance on Linux platforms
- **KMP Compatibility**: Full Kotlin Multiplatform support (JVM, Native, JS/WASM targets)
- **Zero-Copy Operations**: Memory-efficient request processing using TrikeShed's `Indexed<T>` and `Series<T>` types

**Implementation Strategy**:
- **Phase 1**: TrikeShed HTTP scanner for request/response parsing using hierarchical token classification
- **Phase 2**: URing-based connection management and async IO operations
- **Phase 3**: KMP abstractions with platform-specific optimizations (URing on Linux, IOCP on Windows, kqueue on macOS)
- **Phase 4**: Integration with existing TrikeShed networking stack (`borg.trikeshed.net.quic`, `borg.trikeshed.net.http`)

**Technical Requirements**:
- **HTTP Protocol Support**: HTTP/1.1, HTTP/2, HTTP/3 with TLS 1.3
- **Request Lifecycle Management**: Connection pooling, keep-alive, timeout handling
- **TrikeShed Compliance**: Use `α` transforms, `Join<A,B>` composition, minimal `play` materialization
- **Memory Safety**: Zero-copy parsing, bounded memory usage, efficient garbage collection
- **URing Operations**: Batch request processing, zero-copy network IO, efficient buffer management

**Integration Points**:
- Reference existing `feature/refactor-requestfactory` branch patterns
- Compatibility with existing TrikeShed QUIC and HTTP implementations
- Integration with TrikeShed crypto and TLS modules
- Support for existing reactor patterns in `borg.trikeshed.reactor`

**Performance Goals**:
- Sub-millisecond request parsing using scanner architecture
- Zero-allocation request/response cycles for common patterns
- Batched IO operations leveraging URing submission queues
- Memory-mapped response caching for static content

### 5. URing Protocol Server Implementation

**Status**: IMPLEMENTATION PHASE - Server statistics and metrics completion

**Primary Goal**: Complete the URing-based protocol server implementation with full metrics and monitoring.

**Current State**: Basic server structure exists with extensive TODO placeholders

**Missing Components**:
- **Server Statistics**: Complete `stats()` implementation for all protocol servers
- **io_uring Integration**: Full io_uring API integration with proper buffer management
- **Protocol Handlers**: Complete implementations for QUIC, HTTP/2, Memcached, Redis servers
- **Metrics Collection**: Comprehensive metrics and monitoring for all server operations
- **Connection Management**: Proper connection lifecycle management with cleanup

**Implementation Requirements**:
- **Performance**: High-throughput server operations using io_uring optimizations
- **Memory Safety**: Proper buffer management and memory leak prevention
- **Error Handling**: Comprehensive error handling and recovery mechanisms
- **Monitoring**: Real-time metrics collection and performance monitoring

**Integration Points**:
- Existing URing protocol interfaces in `superbikeshed-mcp-server/trikeshed/src/linuxX64Main/kotlin/`
- QUIC and HTTP/2 protocol implementations
- URing infrastructure for high-performance server operations
- Metrics and monitoring systems

## Lockdown Rules

1. **Single Source of Truth**: This document is the authoritative source for all QUICDB-related todo items
2. **No Duplication**: QUICDB items should not appear in MASTER_TODO.md or other todo files
3. **Status Tracking**: Each item must have a clear status (RESEARCH, DESIGN, IMPLEMENTATION, etc.)
4. **Integration Points**: All items must specify their integration points with existing TrikeShed infrastructure
5. **Performance Goals**: All implementation items must specify measurable performance targets

## Migration Notes

- All QUICDB-related items have been extracted from MASTER_TODO.md
- Original items in MASTER_TODO.md should be marked as "MIGRATED TO quicdb_todo_lockdown.md"
- Future QUICDB items should be added directly to this document
- Cross-references to this document should be used in MASTER_TODO.md for QUICDB dependencies

## Next Steps

1. Update MASTER_TODO.md to reference this document for QUICDB items
2. Prioritize implementation order based on dependencies
3. Establish performance benchmarks for each implementation phase
4. Create integration test suites for QUICDB components 