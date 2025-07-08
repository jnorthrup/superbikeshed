# Trikeshed Reactor Architecture Analysis

## Current State (BEFORE)

```mermaid
graph TB
    subgraph "trikeshed-reactor (275 errors)"
        R[Reactor Core]
        R --> HTTP["HTTP/0.9-2 State Machines"]
        R --> QUIC["QUIC Protocol"]
        R --> SOCKS["SOCKS5 Proxy"]
        R --> IPC["IPC Channels"]
        R --> ASYNC["Async I/O"]
        R --> BUF["ByteBuffer Management"]
        R --> SEL["Selection/Channel APIs"]
    end
    
    subgraph "Dependencies"
        R --> IO["trikeshed-io"]
        R --> LIB["trikeshed-lib"]
        R --> MISSING["❌ Missing nio package"]
        R --> DT["❌ Missing datetime"]
    end
    
    subgraph "Problems"
        P1["🔴 Protocol Sprawl"]
        P2["🔴 Mixed Abstraction Levels"]
        P3["🔴 Platform Dependencies"]
        P4["🔴 Compilation Complexity"]
    end
```

## Proposed State (AFTER)

```mermaid
graph TB
    subgraph "Foundation Layer"
        CORE["trikeshed-async-core"]
        CORE --> AC["AsyncChannel"]
        CORE --> SC["SelectableChannel"]
        CORE --> SK["SelectionKey"]
        CORE --> BB["ByteBuffer"]
        CORE --> IO2["IOOperation"]
        CORE --> UAR["UnaryAsyncReaction"]
    end
    
    subgraph "Protocol Modules"
        HTTP2["trikeshed-http"]
        QUIC2["trikeshed-quic"]
        SOCKS2["trikeshed-socks"]
        IPC2["trikeshed-ipc (existing)"]
    end
    
    subgraph "Reactor (Simplified)"
        REACTOR["trikeshed-reactor"]
        REACTOR --> ORCH["Async Orchestration"]
        REACTOR --> MUX["Channel Multiplexing"]
    end
    
    %% Dependencies
    HTTP2 --> CORE
    QUIC2 --> CORE
    SOCKS2 --> CORE
    IPC2 --> CORE
    REACTOR --> CORE
    
    %% Use Cases
    UC1["HTTP Server App"] --> HTTP2
    UC1 --> CORE
    
    UC2["SOCKS Proxy App"] --> SOCKS2
    UC2 --> CORE
    
    UC3["QUIC Service"] --> QUIC2
    UC3 --> CORE
    
    UC4["Multi-Protocol Gateway"] --> HTTP2
    UC4 --> SOCKS2
    UC4 --> REACTOR
```

## Module Responsibilities

### Before (Monolithic Reactor)
- **Everything in one place**: 275 compilation errors
- **Burden**: HTTP/0.9-2, QUIC, SOCKS5, IPC, async I/O, buffer management
- **Problem**: Can't use just SOCKS without dragging in HTTP and QUIC

### After (Modular Design)

#### trikeshed-async-core (Foundation)
```kotlin
// Minimal async abstractions
interface AsyncChannel
interface SelectableChannel 
class SelectionKey
class ByteBuffer
class IOOperation
typealias UnaryAsyncReaction
```

#### Protocol Modules
- **trikeshed-http**: HTTP state machines, HTTP/2 proxy
- **trikeshed-quic**: QUIC server/client implementation
- **trikeshed-socks**: SOCKS5 proxy (RFC compliant)
- **trikeshed-ipc**: Inter-process communication (already exists)

#### trikeshed-reactor (Orchestration Only)
- Channel multiplexing
- Async event loops
- Protocol composition
- No protocol implementations

## Benefits

1. **Tiny Scripts**: Each use case imports only what it needs
2. **Clear Dependencies**: Protocols depend on async-core, not on each other
3. **Easier Testing**: Test each protocol in isolation
4. **Reduced Compilation**: Smaller modules = faster builds
5. **Platform Flexibility**: Core abstractions can have platform-specific implementations

## Migration Path

1. Create `trikeshed-async-core` with minimal abstractions
2. Move protocol implementations to separate modules
3. Fix nio → io imports
4. Add missing dependencies (datetime)
5. Simplify reactor to orchestration only