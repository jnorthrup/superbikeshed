# MASTER TODO - Unified Project Management

**Status**: CURATED - All todo items consolidated and organized
**Last Updated**: 2025-01-27
**Purpose**: Single source of truth for all project tasks and priorities

## Table of Contents

1. [Architecture & Core Systems](#architecture--core-systems)
2. [Protocol Implementations](#protocol-implementations)
3. [Component Development](#component-development)
4. [Integration & Testing](#integration--testing)
5. [Performance & Optimization](#performance--optimization)
6. [Documentation & Quality](#documentation--quality)

---

## Architecture & Core Systems

### TrikeShed Type System Migration (PRIORITY 1)

**Status**: IN PROGRESS - Critical foundation work
**Owner**: Core Team
**Deadline**: Foundation for all other work
**Total Estimate**: 3-4 weeks

#### Shunned Types Elimination

- [ ] **Replace List<T> with Series<T>** (1-2 weeks)
  - [ ] Audit all List<T> usage across codebase (2-3 days)
  - [ ] Convert to Series<T> with proper α transforms (3-5 days)
  - [ ] Update method signatures and return types (2-3 days)
  - [ ] Verify performance implications (1-2 days)
  
- [ ] **Replace MutableList<T> with Series<T> α transforms** (1 week)
  - [ ] Identify mutable collection patterns (1-2 days)
  - [ ] Implement immutable transform chains (2-3 days)
  - [ ] Ensure no side-effect mutations remain (1-2 days)

- [ ] **Replace Pair<A,B> with Join<A,B>** (3-5 days)
  - [ ] Convert all Pair usage to Join with `j` operator (2-3 days)
  - [ ] Update destructuring patterns (1-2 days)
  - [ ] Verify type inference correctness (1 day)

#### Mandatory Pattern Implementation

- [ ] **Join Composition Operator (`a j b`)** (1 week)
  - [ ] Implement Join<A,B> as primary composition mechanism (2-3 days)
  - [ ] Ensure `j` operator is the ONLY composition method (1-2 days)
  - [ ] Add compile-time verification where possible (1-2 days)

- [ ] **Alpha Transform Pattern (`series.α { transform }`)** (1-2 weeks)
  - [ ] Implement α as the ONLY transformation operator (3-4 days)
  - [ ] Convert all map/filter/reduce operations to α transforms (4-6 days)
  - [ ] Ensure lazy evaluation and optimization (2-3 days)

- [ ] **Play Button Materialization (`series play`)** (3-5 days)
  - [ ] Implement play as gateway to AbstractList/Iterable<T> (2-3 days)
  - [ ] Use only for final materialization to legacy collections (1-2 days)
  - [ ] Ensure no direct Series to Collection conversion (1 day)

### Dead Code Elimination

**Status**: ONGOING - Compliance with CLAUDE.md directives
**Total Estimate**: 1-2 weeks

- [ ] **Remove Simulated Benchmarks** (3-5 days)
  - [ ] Audit performance measurement code (1-2 days)
  - [ ] Keep only actual running code metrics (1-2 days)
  - [ ] Remove any "fake" performance data (1 day)

- [ ] **Eliminate Fake Demonstrations** (3-5 days)
  - [ ] Remove hardcoded "successful connection" simulations (1-2 days)
  - [ ] Ensure all behavior is real implementation (1-2 days)
  - [ ] Convert demos to actual working features or remove (1-2 days)

- [ ] **Remove Mock Functionality** (2-3 days)
  - [ ] Identify code that pretends to work (1 day)
  - [ ] Either implement real functionality or mark as TODO (1-2 days)
  - [ ] No placeholder success responses (1 day)

### Tensor-First Processing

**Status**: PARTIAL - Complete tensor-first columnar processing

- [ ] **Tensor<T> Implementation**
  - [ ] Ensure Tensor<T> = Join<IntArray,(IntArray)->T>
  - [ ] Implement efficient columnar operations
  - [ ] Optimize for cache locality and SIMD

- [ ] **Series<T> Evolution**
  - [ ] Enhance Series<T> as primary collection type
  - [ ] Implement efficient transform chains
  - [ ] Ensure integration with tensor operations

---

## Protocol Implementations

### QUIC Protocol Stack (LOCKED DOWN)

**Status**: MIGRATED TO `todo/quicdb_todo_lockdown.md`
**Reference**: See consolidated QUICDB todo items

**Items Moved**:

- OpenSSH over QUIC and SCTP via WebRTC Research Project
- Complete URing QUIC Implementation Backlog  
- QUIC Protocol Implementation Completion
- KMP RequestFactory from Scratch using TrikeShed Scanner and URing IO
- URing Protocol Server Implementation

### REST Implementation Completion

**Status**: IMPLEMENTATION - Basic architectural foundation exists
**Priority**: HIGH

**Missing Components**:

- [ ] **Request Body Serialization/Deserialization**: JSON/XML content handling with proper MIME type support
- [ ] **Path Parameter Extraction**: Dynamic route parameter parsing (e.g., `/users/{id}` → `id` extraction)
- [ ] **Query String Parsing**: URL parameter handling with type conversion and validation
- [ ] **Content Negotiation**: Accept/Content-Type header processing for proper response formatting
- [ ] **HTTP Client send() Method**: Complete implementation of HTTP client request transmission

**Implementation Requirements**:

- **TrikeShed Scanner Integration**: Use existing JSON scanner for request/response body parsing
- **URing Optimization**: Leverage io_uring for high-performance HTTP operations
- **KMP Compatibility**: Full support across JVM, Native, and JS/WASM targets
- **Memory Safety**: Zero-copy operations using TrikeShed's `Indexed<T>` and `Series<T>` types

### SSH Protocol Implementation Completion

**Status**: IMPLEMENTATION - Basic protocol structure and DSL exist
**Priority**: MEDIUM

**Missing Components**:

- [ ] **Transport Layer**: Complete TCP/QUIC transport implementation with proper connection handling
- [ ] **Key Exchange**: Full Diffie-Hellman and ECDH key exchange with proper crypto operations
- [ ] **Authentication**: Complete public key, password, and keyboard-interactive authentication
- [ ] **Channel Management**: Full SSH channel multiplexing with proper flow control
- [ ] **SFTP Support**: File transfer protocol implementation over SSH channels

### CouchDB Client Implementation Completion

**Status**: MIGRATED - See `couchdb_master_todo.md` for details
**Reference**: All CouchDB-related tasks have been migrated to `couchdb_master_todo.md`.

### IPFS Client Implementation Completion

**Status**: IMPLEMENTATION - Basic client structure exists with placeholder implementations
**Priority**: LOW

**Missing Components**:

- [ ] **DHT Implementation**: Complete distributed hash table for peer discovery and routing
- [ ] **Content Routing**: Proper content-based routing with Kademlia DHT
- [ ] **Block Exchange**: BitSwap protocol for efficient block exchange between peers
- [ ] **Merkle DAG**: Complete implementation of IPFS Merkle DAG for file storage
- [ ] **Peer Discovery**: mDNS and DHT-based peer discovery mechanisms

### Implement Trikeshed sweeping protocol tree

- Define base protocol interfaces (Protocol, TransportProtocol, ApplicationProtocol, etc.)
- Create hierarchical protocol abstraction for broad protocol families (HTTP, TCP, UDP, WebSocket, etc.)
- Integrate command-line argument adapters for system tools (openssl, aria2, apache, nginx, netty, couchdb, ipfs, etc.) as protocol leaf nodes or utility branches
- For hard protocols (CouchDB, IPFS, QUIC, etc.), implement diamond relationships (multiple inheritance/composition) to allow shared features and cross-protocol utilities
- Use only minimal required Trikeshed modules to minimize footprint
- No new naming conventions for diamond manifestations unless explicitly specified
- All orchestration and control via stdio as the minimum interface
- Record protocol and architectural decisions in claude.db for continuity

---

## Component Development

### RequestFactory Business Logic Implementation

**Status**: IMPLEMENTATION - Architectural framework exists with placeholder method implementations
**Priority**: HIGH

**Missing Components**:

- [ ] **RTS Game Command Processors**: Complete implementations for:
  - [ ] Unit movement, attack targeting, and structure building
  - [ ] Production queue management (queue/cancel operations)
  - [ ] Rally point configuration and AI override controls
- [ ] **CouchDB View Query Implementation**: Full support for CouchDB view queries and design documents
- [ ] **Business Logic Validation**: Complete service method validation and error handling

### Blob Storage API Implementation

**Status**: IMPLEMENTATION - Basic interfaces exist with CouchDB backend and placeholder methods
**Priority**: MEDIUM

**Missing Components**:

- [ ] **URing-Optimized I/O**: Implement efficient batched I/O operations using io_uring
- [ ] **Memory-Mapped Operations**: Zero-copy blob access using memory mapping
- [ ] **Bulk Operations**: Efficient batch upload/download with parallel processing
- [ ] **Content Addressing**: Proper content-addressable storage with deduplication
- [ ] **Compression Support**: Built-in compression and decompression for blob storage

### Circular Queue and Data Structure Implementation

**Status**: IMPLEMENTATION - Basic interfaces exist with placeholder implementations
**Priority**: MEDIUM

**Missing Components**:

- [ ] **Circular Queue**: Complete `poll()`, `peek()`, and `remove()` implementations
- [ ] **Array Map**: Complete value access and associative array operations
- [ ] **Patricia Trie**: Complete trie operations with proper memory management
- [ ] **Memory Optimization**: Efficient memory usage with proper cleanup

### Crypto Implementation Completion

**Status**: IMPLEMENTATION - Basic interfaces exist with extensive TODO placeholders
**Priority**: HIGH

**Missing Components**:

- [ ] **Native Crypto**: Complete native crypto implementation for Linux/macOS
- [ ] **WASM Crypto**: Complete WebAssembly crypto implementation for JS targets
- [ ] **TLS 1.3**: Full TLS 1.3 implementation with proper handshake and encryption
- [ ] **Key Management**: Proper key generation, storage, and lifecycle management
- [ ] **Random Number Generation**: Cryptographically secure random number generation

---

## Scanner Models & Parsing

### TrikeShed YAML Scanner Model

**Status**: DESIGN - New scanner model needed
**Priority**: MEDIUM

**Requirements**:

- [ ] **Hierarchical Token Classification**: 5-level stairway (raw, lexical, syntactic, semantic, graph node) using `@JvmInline value class`
- [ ] **TrikeShed Compliance**: Use `Series<T>`, `Join<A,B>`, `α` transforms, and `play` only for final materialization
- [ ] **YAML-Specific Token Types**: Handle YAML constructs (indentation, anchors, aliases, tags, flow/block styles, comments)
- [ ] **Evidence-Based Inductive Refinement**: Forward chaining logic with confidence scoring for ambiguous YAML constructs
- [ ] **Taxonomical Type Aliases**: Define semantic type aliases like `YamlChar`, `YamlPosition`, `YamlTokenType`, etc.
- [ ] **Incremental Parsing**: Support for real-time updates and error recovery

### TrikeShed Protocol Buffer Scanner Model

**Status**: DESIGN - New scanner model needed
**Priority**: LOW

**Requirements**:

- [ ] **Hierarchical Token Classification**: 5-level stairway (raw bytes, wire format, field tokens, message tokens, schema tokens)
- [ ] **Binary Wire Format Parsing**: Handle protobuf's varint encoding, field numbers, wire types, and length-delimited data
- [ ] **Schema-Aware Scanning**: Integrate with protobuf schema definitions for type-safe parsing
- [ ] **Evidence-Based Inductive Refinement**: Forward chaining logic with confidence scoring for ambiguous protobuf constructs
- [ ] **Taxonomical Type Aliases**: Define semantic type aliases for protobuf constructs

---

## Integration & Testing

### Graal Python + Nexus + DGM Integration

**Status**: RESEARCH - High-risk architectural integration
**Priority**: MEDIUM
**Risk Level**: HIGH - Significant architectural changes and performance considerations

**Implementation Timeline**: 10-week phased approach with incremental risk management

**Phase 1: Foundation Setup (Weeks 1-2)**:

- [ ] **Graal Python Environment**: Install GraalVM with Python support and configure build dependencies
- [ ] **JVM Bridge Infrastructure**: Implement `GraalPythonBridge` with Python context management and script execution
- [ ] **Memory Management**: Create `PythonMemoryManager` with object pooling and garbage collection triggers

**Phase 2: Nexus Agent Integration (Weeks 3-4)**:

- [ ] **Python-Aware Agents**: Extend `DefaultNexusAgent` with `PythonNexusAgent` for Python task execution
- [ ] **Task Definitions**: Create `PythonAgentTask` with script execution, dependencies, and resource limits
- [ ] **Workflow Integration**: Add Python data processing workflows with setup, execution, and result collection steps

**Phase 3: DGM Python Enhancement (Weeks 5-6)**:

- [ ] **Enhanced DGM**: Extend existing DGM with `GraalPythonDGM` class for polyglot improvement cycles
- [ ] **Python-Nexus Bridge**: Implement `NexusClient` for submitting Python tasks to distributed Nexus agents
- [ ] **TrikeShed Bridge**: Create Python-TrikeShed integration for data exchange and type conversion

**Phase 4: Performance Optimization (Weeks 7-8)**:

- [ ] **Memory Pool Management**: Implement `PythonMemoryPool` with object reuse and memory limits
- [ ] **Concurrency Management**: Create `PythonConcurrencyManager` with semaphore-based execution control
- [ ] **URing Integration**: Leverage existing io_uring infrastructure for Python I/O operations

**Phase 5: Integration Testing (Weeks 9-10)**:

- [ ] **Test Infrastructure**: Comprehensive test suite for Python script execution and error handling
- [ ] **Performance Benchmarks**: Sub-millisecond execution targets with memory usage monitoring
- [ ] **Stress Testing**: 24-hour memory leak testing and concurrent execution validation

### RTS Game Integration

**Status**: FEATURE INTEGRATION - Multiple feature branches ready for integration
**Priority**: HIGH

**Feature Branches**:

- [ ] **Command Hierarchy Enhancements** (`feature/command-hierarchy-enhancements`)
  - [x] Dynamic unit authority (health, veterancy, context, Computronium)
  - [x] Veterancy progression system with ranks and stat boosts
  - [x] Command succession protocol implementation
  - [ ] Advanced veterancy abilities implementation
  - [ ] UI elements for rank/authority display
  - [ ] Contextual authority modifier logic

- [ ] **Formation Movement System** (`feature/enhanced-formation-movement`)
  - [x] Leader-follower system with A* pathfinding for leaders
  - [x] Predictive slot tracking for followers relative to leader
  - [x] Steering behaviors: seek/arrive, separation, terrain avoidance
  - [ ] Advanced obstacle avoidance
  - [ ] Leader behavior enhancements
  - [ ] Dynamic formation shapes (line, column, wedge)

- [ ] **AI Prediction Interface** (`feature/ai-prediction-interface-poc`)
  - [x] StrategicAI prediction generation
  - [x] Map visualization with confidence-based colors
  - [x] Player interaction via right-click acknowledgment
  - [ ] More prediction types
  - [ ] Enhanced enemy attack vector prediction
  - [ ] Tangible AI behavior changes from interactions

### Nexus Dining Philosophers Orchestration

**Status**: RESEARCH - Concurrent task consensus system
**Priority**: LOW

**Core Architecture**:

- [ ] **Philosopher Agents**: Each agent represents a worker that needs shared resources (forks) to execute tasks
- [ ] **Concentric Subnet Tasks**: Tasks organized in concentric rings where inner tasks must complete before outer ones
- [ ] **Swift Git Transforms**: Integration with git operations for version control of task states and decisions
- [ ] **URing Queue Design**: High-performance task queue using io_uring for orchestration context

**Consensus Protocol**:

- [ ] **Pre-Work Discussion Phase**: Agents join discussion group for assigned task, log objections and support
- [ ] **Work Execution Phase**: Single agent executes based on group consensus with real-time progress logging
- [ ] **Post-Work Review Phase**: Short discussion period for review with fidelity and completion quality ratings

---

## Performance & Optimization

### URing CouchDB Strategies

**Status**: MIGRATED - See `couchdb_master_todo.md` for details
**Reference**: All CouchDB-related tasks have been migrated to `couchdb_master_todo.md`.

### Database Treatise for Personal Computing Devices

**Status**: RESEARCH - Comprehensive study of database architectures for personal devices
**Priority**: LOW

**Research Areas**:

- [ ] **Device Class Analysis**: Laptops, workstations, high-end tablets with specific constraints
- [ ] **Database Architecture Requirements**: Local-first design, memory efficiency, storage optimization
- [ ] **Technology Considerations**: Embedded databases, in-memory databases, vector databases
- [ ] **Performance Characteristics**: Startup time, query performance, background operations
- [ ] **Use Case Analysis**: Personal knowledge management, creative work, development, data analysis

---

## Documentation & Quality

### TODO/FIXME Resolution

**Status**: ONGOING - Code quality improvement
**Priority**: MEDIUM

- [ ] **Address all TODO comments** in TrikeShed code
- [ ] **Convert FIXME items** to proper issues
- [ ] **Clean up temporary implementations**

### Testing Infrastructure

**Status**: ONGOING - Quality assurance
**Priority**: HIGH

- [ ] **Comprehensive unit tests** for type system
- [ ] **Performance benchmarks** for tensor operations
- [ ] **Integration tests** with other components
- [ ] **Large-scale battle testing** (500+ units)
- [ ] **Complex terrain navigation** testing
- [ ] **Multi-formation coordination** testing

### Documentation

**Status**: ONGOING - Knowledge management
**Priority**: MEDIUM

- [ ] **Complete API documentation** for all public types
- [ ] **Usage examples** for common patterns
- [ ] **Migration guide** from legacy patterns
- [ ] **Architecture documentation** for new systems
- [ ] **User guide** for advanced features
- [ ] **Developer guide** for extending systems

---

## Status Legend

- **PRIORITY 1**: Critical foundation work, blocks other development
- **PRIORITY 2**: Important features, significant impact
- **PRIORITY 3**: Nice-to-have features, low impact
- **IN PROGRESS**: Active development
- **IMPLEMENTATION**: Ready for implementation
- **DESIGN**: Needs design work before implementation
- **RESEARCH**: Needs research and analysis
- **MIGRATED**: Moved to specialized todo file
- **LOCKED DOWN**: Consolidated in specialized file with strict rules

## Migration Notes

- QUICDB items consolidated in `todo/quicdb_todo_lockdown.md`
- Component-specific details may be found in individual todo files
- This document serves as the master index and high-level planning
- All items should be tracked here with appropriate status and priority
