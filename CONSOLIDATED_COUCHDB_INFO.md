# Consolidated CouchDB Information

This document consolidates all information related to CouchDB within the project, gathered from various Markdown files.

## 1. CouchDB Role and Vision

CouchDB is a critical component in the TrikeShed ecosystem, envisioned as a decentralized, real-time document database. The goal is to combine CouchDB's familiar API and MVCC model with modern technologies like QUIC for transport and IPFS for content-addressed storage, creating a performant, secure, and scalable solution.

Key aspects of the vision include:
- **Familiar API**: Full CouchDB API compatibility for existing applications.
- **Decentralized Storage**: IPFS-based document storage with CID mapping and PubSub for index synchronization.
- **Modern Transport**: QUIC (HTTP/3) for reduced replication latency and efficient stream multiplexing.
- **Real-time Collaboration**: Integration with Apache Wave's Operational Transformation for real-time document editing.
- **Git Mirroring**: Using CouchDB to store and mirror Git repositories.
- **Hermetic Implementation**: Building a self-contained, KMP CouchDB server.

## 2. Current Implementation Status and Features

The `trikeshed-couchdb` module is central to this effort, and while core components are implemented, ongoing work focuses on full feature parity and integration.

### 2.1. Core API and Protocol
- **CouchDB Protocol Implementation**: `CouchProtocol.kt`, `CouchClient.kt`, `SecureCouchClient.kt` are available.
- **Core Document API**: Basic CRUD (Create, Read, Update, Delete) operations for documents are implemented.
- **Database API**: Database-level operations (create, delete, info) are implemented.
- **Bulk Operations**: Handling of bulk document creation, update, and deletion is implemented.
- **Changes Feed**: Basic changes feed functionality is implemented, tracking document operations and supporting `since` parameters and deletions.
- **Cursor Integration**: `CouchCursorIntegration.kt` provides cursor-driven operations with indexed channels and CCek FSM for streaming, batching, and reactive processing of CouchDB documents.

### 2.2. Integration Points
- **QUIC Integration**: The system uses a real TrikeShed QUIC engine. The goal is to use QUIC as the transport for CouchDB replication and API calls, replacing HTTP/1.1. HTTP/3 request parsing and response serialization are key areas of focus.
- **IPFS Bridge**: Documents are stored in IPFS, and CouchDB document IDs are mapped to CIDs. IPFS PubSub is used for synchronizing document indexes.
- **Nexus Integration**: CouchDB API is integrated into the RelaxFactory server, routing QUIC streams to the CouchDB API.
- **Fiduciary Project**: CouchDB is used for real-time document recording, persistence of Wave operations, and audit trails in the Apache Wave implementation.
- **Git Forensics Service**: Integrates with real-time CouchDB objects for live analysis, scene replay, and pattern detection of Git taxonomic objects.

### 2.3. Architectural Principles
- **Modular Architecture**: Clean separation between QUIC, IPFS, and CouchDB layers.
- **Crash-Only Design**: Inspired by CouchDB's append-only model.
- **Context-Driven Execution (CCEK)**: Leveraging CoroutineContext.Element.Key for context-aware operations.

## 3. Key TODOs and Future Work

### 3.1. Build Fixes & Core API Completion
- Resolve compilation errors related to serialization, type mismatches, and redeclarations in `trikeshed-couchdb`.
- Replace placeholder HTTP client calls in `CouchClient.kt` with a real implementation.

### 3.2. Advanced Features
- **Changes Feed**: Implement `longpoll` and `continuous` feed types, and filtering by design document functions.
- **Views and Design Documents**: Implement `PUT /db/_design/doc`, MapReduce view generation, and `GET /db/_design/doc/_view/name` with various query parameters. Investigate and implement cascading MapReduce.
- **Replication Protocol**: Implement `POST /_replicate`, conflict resolution during replication, and continuous/filtered replication.

### 3.3. Integration & Optimization
- **QUIC & HTTP/3**: Complete HTTP/3 request parsing and response serialization from QUIC streams.
- **IPFS Bridge**: Enhance attachment handling and PubSub for index sync.
- **Storage & Performance**: Investigate columnar storage, implement `io_uring` for file I/O, connection pooling, compaction, and caching strategies.

### 3.4. Testing & Validation
- Complete implementation of query operations tests (e.g., `_all_docs` with `include_docs`).
- Enhance conflict handling tests and implement conflict resolution mechanisms.
- Implement performance tests for large bulk operations and large documents.
- Implement network synchronization tests for multi-node environments.

### 3.5. Application-Specific Features
- **Fiduciary & Apache Wave**: Ensure reliable and low-latency changes feed for Wave's OT, and robust audit trails.
- **Git Mirroring**: Implement mapping git objects to CouchDB attachments, syncing git refs, and reconstructing git history.

## 4. Test Plan Summary

The CouchDB test plan follows TDD principles and covers:
- **Database Operations**: Creation, deletion, information retrieval.
- **Document CRUD**: Create, retrieve, update, delete with revision handling.
- **Bulk Operations**: Mixed operations and error handling.
- **Changes Feed**: Tracking changes and deletions.
- **Query Operations**: All documents query.
- **Conflict Handling**: Simulation and resolution.
- **Error Handling**: Invalid names/IDs, network errors.
- **Performance Tests**: Large operations and documents.
- **Network Synchronization**: Multi-node sync and conflict resolution.

Current test status shows 70% passing, with query operations, conflict handling, performance, and network tests still in progress or planned.

## 5. Relevant Files and Modules

- `trikeshed-couchdb` module: Contains core CouchDB client and protocol implementations.
- `nexus/api/CouchDbApi.kt`: Implements the CouchDB API layer.
- `nexus/bridge/IpfsBridge.kt`: Handles IPFS integration for CouchDB documents.
- `nexus/server/RelaxFactoryServer.kt`: Integrates CouchDB API with QUIC server.
- `fiduciary` module: Utilizes CouchDB for Apache Wave and other fiduciary services.
- `nexus/docs/COUCHDB_TEST_PLAN.md`: Comprehensive test plan.
- `couchdb_master_todo.md`: Detailed task list.
