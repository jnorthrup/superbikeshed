# The TrikeShed Service Hymnbook: A Comprehensive Registry of CCEK Services

This document catalogs the primary Coroutine Context Element Keys (CCEKs) that form the service architecture of the TrikeShed Reactor Foundation. Each entry represents a "key" on the organ, allowing for the dynamic composition of execution contexts.

---

## I. Core Asynchronous & I/O Primitives

These are the most fundamental services, forming the base layer for all I/O and concurrency.

| Module | Service / Context Element | `CoroutineContext.Key` | Purpose & Role |
| :--- | :--- | :--- | :--- |
| `trikeshed-async-core` | `AsyncContext` | *(No direct key; used via companion object)* | Provides platform-optimized execution contexts (`default`, `io`, `compute`). This is the foundational choice of *how* and *where* a coroutine runs. |
| `trikeshed-io` | `IODaemon` | *(No direct key; used via factory)* | Orchestrates high-performance, batched, kernel-level I/O operations. Represents the lowest-level I/O abstraction. |
| `trikeshed-io` | `CcekContext` | `CcekContext.CcekContextKey` | **The fundamental CCEK.** Carries execution metadata, rules, and constraints for a single, orchestrated operation. |
| `trikeshed-io` | `UringBatchContext` | `UringBatchContext.UringBatchKey` | Specifies configuration for `io_uring` batch operations, enabling kernel-level efficiency. **(Linux Native Endgame)** |
| `trikeshed-io` | `ChannelChainContext` | `ChannelChainContext.ChannelChainKey` | Defines a chain of I/O channels, crucial for proxying and relaying operations (e.g., SOCKS5). |
| `trikeshed-io` | `AsyncChannelContext` | `AsyncChannelContext.AsyncChannelKey` | Represents a single, addressable async I/O channel within a `ChannelChainContext`. |
| `trikeshed-lib` | `IoPreference` | `IoPreference.Key` | **Refined evolution of `AsyncContext`**. Specifies the *desired* I/O backend (URING, NIO, etc.) for an operation, allowing for dynamic dispatch. |
| `trikeshed-lib` | `ExecutionId` | `ExecutionId.Key` | Carries a unique ID for tracing a request or execution flow through the entire system. |
| `trikeshed-lib` | `ExecutionPhase` | `ExecutionPhase.Key` | Tracks the current phase of execution (INIT, VALIDATE, TRANSFORM, SERIALIZE, COMPLETE, ERROR). |
| `trikeshed-lib` | `HandlerRegistry<K, V>` | `HandlerRegistry.Key` | A generic, compositional handler registry that maps keys to suspend functions. Replaces MetaIndexed "chord sheets". |

---

## II. Channelization & Protocol Stack

This layer abstracts raw I/O into protocol-aware channels and services.

| Module | Service / Context Element | `CoroutineContext.Key` | Purpose & Role |
| :--- | :--- | :--- | :--- |
| `trikeshed-channel-api` | `ChannelService` | `ChannelService.Key` | **The primary entry point for all channel operations.** Provides a unified API to create, connect, and manage I/O channels, abstracting the underlying provider (NIO, io_uring). |
| `trikeshed-channel-api` | `RecordingService` | `RecordingService.Key` | A powerful testing and debugging utility that wraps any `Channel` to record and replay all I/O events. |
| `trikeshed-channel-impl` | `SshProtocolAdapter` | `SshProtocolAdapter.Key` | A CCEK-composable protocol adapter for handling the SSH protocol over a channel. |
| `trikeshed-channel-impl` | `HttpProtocolAdapter` | `HttpProtocolAdapter.Key` | A CCEK-composable protocol adapter for handling the HTTP protocol over a channel. |
| `trikeshed-channel-impl` | `QuicProtocolAdapter` | `QuicProtocolAdapter.Key` | A CCEK-composable protocol adapter for handling the QUIC protocol over a channel. |
| `trikeshed-ccek` | `HTTPProtocolAdapter` | `HTTPProtocolAdapter.Key` | A higher-level adapter for HTTP protocol-specific context transitions. |
| `trikeshed-ccek` | `QUICProtocolAdapter` | `QUICProtocolAdapter.Key` | A higher-level adapter for QUIC protocol-specific context transitions. |
| `trikeshed-socks` | `SocksIngressChannelElement` | `SocksIngressChannelKey` | Provides SOCKS ingress (incoming data) channel for network operations. |
| `trikeshed-socks` | `SocksEgressChannelElement` | `SocksEgressChannelKey` | Provides SOCKS egress (outgoing data) channel for network operations. |

---

## III. Application & Service Logic

This layer represents the business logic and higher-level services built on top of the protocol stacks.

| Module | Service / Context Element | `CoroutineContext.Key` | Purpose & Role |
| :--- | :--- | :--- | :--- |
| `trikeshed-ccek` | `ChannelizedRequestFactory`| `ChannelizedRequestFactory.Key`| **The GWT/Wave-inspired RPC engine.** Manages roundtrip transactions for various HTTP protocols, providing a type-safe interface for remote service calls. |
| `trikeshed-ccek` | `CRDTChannelEngine` | `CRDTChannelEngine.Key` | A Conflict-free Replicated Data Type engine for real-time collaborative applications, integrated with the channel architecture. |
| `trikeshed-ccek` | `CRDTRequestFactory` | `CRDTRequestFactory.Key` | A specialized request factory for managing transactional CRDT operations, ensuring atomicity. |
| `trikeshed-dht` | `ChannelizedKademliaNode` | `ChannelizedKademliaNode.Key` | A Kademlia-based Distributed Hash Table (DHT) for peer discovery and decentralized storage. |
| `trikeshed-dht` | `MetaverseKademliaAgent` | `MetaverseKademliaAgent.Key`| An intelligent agent operating within the Kademlia DHT, designed for tasks like "fiduciary attention" monitoring in the Metaverse demo. |
| `trikeshed-services` | `DealService` | `DealService.Key` | A higher-level business service for managing deals and transactions. |
| `moneyfan` | `LatencyProvider` | `LatencyProviderKey` | Provides latency simulation capabilities for testing and development purposes. |

---

## IV. Platform & Infrastructure Services

These services provide platform-specific capabilities and infrastructure support.

| Module | Service / Context Element | `CoroutineContext.Key` | Purpose & Role |
| :--- | :--- | :--- | :--- |
| `k2script` | `K2ScriptContextElement<T>` | `ContextKey` (enum-based) | Centralized context elements for k2script message bus, providing type safety and discoverability. |
| `k2script` | `FileSystemOperationsKey` | `FileSystemOperationsKey.Key` | Provides file system operations through coroutine context. |
| `k2script` | `ProcessExecutorKey` | `ProcessExecutorKey.Key` | Provides process execution capabilities through coroutine context. |
| `platform-launcher` | `K2ScriptContext` | `K2ScriptContext.Key` | Context for k2script platform operations. |
| `platform-launcher` | `NexusContext` | `NexusContext.Key` | Context for Nexus platform operations. |
| `platform-launcher` | `CouchDBContext` | `CouchDBContext.Key` | Context for CouchDB operations. |
| `platform-launcher` | `BFDPlatformContext` | `BFDPlatformContext.Key` | Context for BFD platform operations. |

---

## V. IPFS & Distributed Services

These services provide IPFS and distributed system capabilities.

| Module | Service / Context Element | `CoroutineContext.Key` | Purpose & Role |
| :--- | :--- | :--- | :--- |
| `trikeshed-ipfs` | `IpfsClientContext` | `IpfsClientContext.Key` | Provides IPFS client operations. |
| `trikeshed-ipfs` | `IpfsServerContext` | `IpfsServerContext.Key` | Provides IPFS server operations. |
| `trikeshed-ipfs` | `DHTServiceContext` | `DHTServiceContext.Key` | Provides DHT operations for IPFS. |
| `trikeshed-ipfs` | `IpfsStorageContext` | `IpfsStorageContext.Key` | Provides IPFS storage operations. |
| `trikeshed-ipfs` | `IpfsPubSubContext` | `IpfsPubSubContext.Key` | Provides IPFS pubsub operations. |
| `trikeshed-ipfs` | `IpfsConfigContext` | `IpfsConfigContext.Key` | Provides IPFS configuration. |
| `trikeshed-ipfs` | `IpfsPeerContext` | `IpfsPeerContext.Key` | Provides IPFS peer information. |
| `trikeshed-ipfs` | `IpfsContentContext` | `IpfsContentContext.Key` | Provides IPFS content routing information. |
| `trikeshed-ipfs` | `IpfsNetworkContext` | `IpfsNetworkContext.Key` | Provides IPFS network statistics. |

---

## VI. The "Endgame" Architecture: Meta-Composition Patterns

This advanced layer demonstrates the ultimate vision of TrikeShed, where execution contexts are composed to map directly to kernel-level operations.

| Module | Service / Context Element | `CoroutineContext.Key` | Purpose & Role |
| :--- | :--- | :--- | :--- |
| `trikeshed-ccek` | `StateElement` | `StateElement.Key` | Represents a single state in a composable state machine, executed via context changes. |
| `trikeshed-ccek` | `PipelineStage` | `PipelineStage.Key` | Represents a single stage in a data processing pipeline, composed via context. |
| `trikeshed-ccek` | `IoUringContext` | `IoUringContext.Key` | A context element that provides direct access to `io_uring` submission capabilities. **(Kernel Endgame)** |
| `trikeshed-ccek` | `EbpfContext` | `EbpfContext.Key` | A context element for loading and executing eBPF programs in the kernel. **(Kernel Endgame)** |
| `trikeshed-ccek` | `TailcallContext` | `TailcallContext.Key` | Represents a terminal operation that can be optimized into a kernel tailcall, avoiding user-kernel roundtrips. **(Kernel Endgame)** |
| `trikeshed-ccek` | `KernelDatabaseContext` | `KernelDatabaseContext.Key` | **The "Kernel as Database" vision.** Abstracts database-like queries (`SELECT`, `INSERT`) into operations that map directly to eBPF programs and `io_uring`. |

---

## VII. Specialized & Domain-Specific Services

These services provide specialized functionality for specific domains or use cases.

| Module | Service / Context Element | `CoroutineContext.Key` | Purpose & Role |
| :--- | :--- | :--- | :--- |
| `trikeshed-json` | `JsonScanContext` | `JsonScanContext.Key` | Provides context for parallel JSON scanning operations. |
| `trikeshed-cursor` | `CursorContext` | `CursorContext.Key` | Provides cursor-based operations and context. |
| `trikeshed-couchdb` | `CouchCCekContext` | `CouchCCekContext.CouchCCekContextKey` | Provides CouchDB-specific CCEK context. |
| `trikeshed-oauth` | `OAuthCCekContext` | `OAuthCCekContext.OAuthCCekContextKey` | Provides OAuth-specific CCEK context. |
| `trikeshed-net` | `SshSessionContext` | `SshSessionContext.Key` | Provides SSH session context for SSH protocol operations. |
| `rtsgame` | `RTSGameContext` | `RTSGameContext.Key` | Provides context for RTS game operations. |
| `rtsgame` | `EntityOperationContext` | `EntityOperationContext.Key` | Provides context for entity operations in RTS games. |
| `rtsgame` | `ComponentChangeContext` | `ComponentChangeContext.Key` | Provides context for component changes in RTS games. |
| `rtsgame` | `MovementSystemContext` | `MovementSystemContext.Key` | Provides context for movement system operations. |
| `rtsgame` | `CombatSystemContext` | `CombatSystemContext.Key` | Provides context for combat system operations. |

---

## VIII. QUIC Protocol Handlers

These services provide QUIC protocol-specific handling capabilities.

| Module | Service / Context Element | `CoroutineContext.Key` | Purpose & Role |
| :--- | :--- | :--- | :--- |
| `trikeshed-channel-impl` | `QuicPacketHandler` | `QuicPacketHandler.Key` | Handles QUIC packet processing. |
| `trikeshed-channel-impl` | `QuicFrameHandler` | `QuicFrameHandler.Key` | Handles QUIC frame processing. |
| `trikeshed-channel-impl` | `QuicStreamHandler` | `QuicStreamHandler.Key` | Handles QUIC stream processing. |

---

## IX. Abstract & Unimplemented Services (The Future Hymns)

These services are defined in the codebase but are placeholders or have their logic stripped, representing future work. They are not part of the active, usable hymnbook yet.

*   **CouchDB Chords:** The vision in `CCEK.kt` to use `MetaSeries` as "protocol chord sheets" for CouchDB operations is a powerful concept but is not yet implemented. The `stepExecutionChord`, `serializationFormatChord`, etc., are blueprints for a declarative, type-driven execution engine.
*   **`CCEKChunked...ChordSheet`:** These classes are placeholders for handling HTTP chunked encoding/decoding specifically for CouchDB, likely intended to be integrated with the `MetaSeries` chords.
*   **`DealService`:** Defined as an interface but its concrete implementation, `ReactorDealService`, shows a reliance on a `HttpServerContext` that is being phased out in favor of the more granular CCEK elements. It serves as a good example of a higher-level business service.

---

## Summary: The Organ's Composition

This "hymnbook" reveals a clear and powerful architectural vision:

1.  **Foundation:** Start with fundamental, granular contexts for I/O (`IoPreference`) and execution (`ExecutionId`).
2.  **Channelization:** Build on this with a unified `ChannelService` that abstracts away the specific I/O backend.
3.  **Protocol Stacks:** Compose protocol adapters (`HttpProtocolAdapter`, `SshProtocolAdapter`) on top of the `ChannelService` to handle specific wire formats.
4.  **Application Services:** Layer high-level application logic (`ChannelizedRequestFactory`, `CRDTChannelEngine`, `ChannelizedKademliaNode`) on top of the protocol stacks.
5.  **The Endgame:** Compose all of the above into a final `KernelTerminal` context, allowing a high-level request like "get user data" to be refined all the way down to a series of `io_uring` operations, executed with maximum efficiency by the kernel itself.

This is the music of the TrikeShed organ: a complex but harmonious composition where every layer adds capability, and the entire system is orchestrated through the flow of the `CoroutineContext`.

---

## Usage Patterns

### Basic Context Composition
```kotlin
// Simple composition with +
val context = ExecutionId("req-123") + IoPreference(IoCapability.URING)

// Bulk composition with vararg
val context = withContext(
    ExecutionId("req-123"),
    IoPreference(IoCapability.URING),
    ChannelService(provider)
) {
    // Execute with composed context
}
```

### Service Discovery
```kotlin
// Get service from context
val channelService = coroutineContext[ChannelService.Key]
val executionId = coroutineContext[ExecutionId.Key]

// Extension functions for easy access
val execId = coroutineContext.executionId
val ioCap = coroutineContext.ioCapability
```

### Meta-Composition Patterns
```kotlin
// State machine composition
val context = withStateTransition(
    StateElement("INIT"),
    StateElement("PROCESSING")
) {
    // Execute in new state
}

// Pipeline composition
val context = withPipelineStage(
    PipelineStage("parse") { data -> parseData(data) }
) {
    // Execute pipeline stage
}

// Kernel terminal composition
val context = withKernelTerminal(
    IoUringContext(),
    EbpfContext()
) {
    // Execute in kernel space
}
```

---

## Key Architectural Principles

1. **Composition over Inheritance:** Services are composed via context rather than inherited.
2. **Granularity:** Each service has a single, well-defined responsibility.
3. **Discoverability:** Services are discovered through their keys in the coroutine context.
4. **Type Safety:** Strong typing ensures correct service usage.
5. **Platform Independence:** Services work across JVM, Native, and JS platforms.
6. **Performance:** Direct mapping to kernel operations for maximum efficiency.
7. **Testability:** Services can be easily mocked and tested in isolation.

This hymnbook serves as the definitive reference for understanding and composing the TrikeShed service architecture. 