# CouchDB Master TODO

**Status**: Generated from project-wide analysis
**Last Updated**: 2025-07-06
**Purpose**: Single source of truth for all CouchDB-related tasks, features, and bug fixes. This consolidates items from `config/tasks.json`, `todo/MASTER_TODO.md`, and various other design documents.

---

## Phase 1: Build Fixes & Core API Implementation

This phase focuses on getting the `trikeshed-couchdb` module to a stable, compilable state and implementing the fundamental document API.

### 1.1. Resolve Compilation Errors (PRIORITY 1)

**Goal**: Achieve a clean build for the `trikeshed-couchdb` module.
**Source**: `compile_output.txt`

- [ ] **Fix Serialization Issues**:
    - [ ] Resolve all `Unresolved reference: serialization` errors in `CouchClient.kt` and `CouchProtocol.kt`.
    - [ ] Add `kotlinx.serialization` plugin and dependencies to `trikeshed-couchdb/build.gradle.kts`.
    - [ ] Fix all `@Serializable` and `@SerialName` annotation errors.
    - [ ] Correct all `Unresolved reference: Json`, `buildJsonObject`, `jsonPrimitive`, etc. by ensuring the correct JSON library is used and imported.

- [ ] **Fix Type Mismatches**:
    - [ ] Correct `Argument type mismatch` errors in `CCEKProtocolOrchestrator.kt` related to CouchDB handlers.
    - [ ] Resolve `Overload resolution ambiguity` in `CouchClient.kt`.
    - [ ] Fix all other type inference and mismatch errors.

- [ ] **Fix Redeclarations**:
    - [ ] Resolve redeclaration errors for data classes in `CouchClient.kt` and `CouchProtocol.kt`.

### 1.2. Implement Core Document API

**Goal**: Implement the basic CRUD (Create, Read, Update, Delete) operations for CouchDB documents.
**Source**: `nexus/api/CouchDbApi.kt`, `docs/REASSIMILATION_PLAN.md`

- [ ] **`GET /db/docid`**: Retrieve a document by its ID.
- [ ] **`PUT /db/docid`**: Create or update a document.
- [ ] **`DELETE /db/docid`**: Delete a document.
- [ ] **HTTP Client Integration**: Replace placeholder HTTP client calls in `CouchClient.kt` with a real implementation.

### 1.3. Implement Database API

**Goal**: Implement database-level operations.
**Source**: `nexus/api/CouchDbApi.kt`

- [ ] **`PUT /db`**: Create a database.
- [ ] **`DELETE /db`**: Delete a database.
- [ ] **`GET /db`**: Get database information.

---

## Phase 2: Advanced Features & Protocol

This phase builds on the core API to add more complex CouchDB functionality.

### 2.1. Bulk & Batched Operations

**Goal**: Implement efficient bulk operations.
**Source**: `nexus/api/CouchDbApi.kt`, `todo/MASTER_TODO.md`

- [ ] **`POST /db/_bulk_docs`**: Implement bulk document creation, update, and deletion.
- [ ] **URing Optimization**: Use `io_uring` for batched I/O to improve performance.

### 2.2. Changes Feed

**Goal**: Implement the real-time changes feed.
**Source**: `nexus/api/CouchDbApi.kt`, `fiduciary/README_APACHE_WAVE.md`

- [ ] **`GET /db/_changes`**: Implement the changes feed endpoint.
- [ ] **Feed Types**: Support `normal`, `longpoll`, and `continuous` feeds.
- [ ] **Filtering**: Allow filtering changes feed by a design document filter function.

### 2.3. Views and Design Documents

**Goal**: Implement CouchDB's view and design document functionality.
**Source**: `todo/MASTER_TODO.md`, `ta4k/cascading/CouchViewsForTA.kt`

- [ ] **`PUT /db/_design/doc`**: Create and update design documents.
- [ ] **View Generation**: Implement MapReduce view generation.
- [ ] **`GET /db/_design/doc/_view/name`**: Implement view querying with various parameters (`key`, `startkey`, `endkey`, `limit`, etc.).
- [ ] **Cascading MapReduce**: Investigate and implement the cascading MapReduce pattern for hierarchical aggregation.

### 2.4. Replication Protocol

**Goal**: Implement the CouchDB replication protocol.
**Source**: `todo/MASTER_TODO.md`, `docs/compass_artifact_wf-3e538241-fb58-4173-b3e4-ca9686c138ad_text_markdown.md`

- [ ] **`POST /_replicate`**: Implement the replication endpoint.
- [ ] **Conflict Resolution**: Implement MVCC-based conflict resolution during replication.
- [ ] **Continuous Replication**: Support for continuous replication between databases.
- [ ] **Filtered Replication**: Support for filtered replication.

---

## Phase 3: Integration & Optimization

This phase focuses on integrating CouchDB with other TrikeShed components and optimizing its performance.

### 3.1. QUIC & HTTP/3 Integration

**Goal**: Use QUIC as the transport for CouchDB replication and API calls.
**Source**: `docs/INTERLOCKING_FACTORS_ANALYSIS.md`

- [ ] **Replace HTTP/1.1**: Modernize the transport layer with QUIC.
- [ ] **HTTP/3 Parsing**: Parse HTTP/3 requests from QUIC streams for the CouchDB API.
- [ ] **Reduce Replication Latency**: Leverage QUIC's stream multiplexing to reduce head-of-line blocking.

### 3.2. IPFS Bridge

**Goal**: Use IPFS for content-addressed storage of documents and attachments.
**Source**: `nexus/src/main/kotlin/nexus/bridge/IpfsBridge.kt`, `docs/REASSIMILATION_PLAN.md`

- [ ] **Document-to-CID Mapping**: Store documents in IPFS and map CouchDB doc IDs to CIDs.
- [ ] **Attachment Handling**: Store attachments in IPFS.
- [ ] **PubSub for Index Sync**: Use IPFS PubSub to synchronize document indexes across nodes.

### 3.3. Storage & Performance Optimization

**Goal**: Evolve the storage backend from append-only B-trees to a more modern, performant solution.
**Source**: `CRASH_ONLY_COLUMNAR_STORAGE.md`, `config/tasks.json`

- [ ] **Columnar Storage**: Investigate and implement a hybrid approach combining CouchDB's model with columnar storage for analytics.
- [ ] **`io_uring` Everywhere**: Use `io_uring` for all file I/O to maximize performance.
- [ ] **Connection Pooling**: Implement CouchDB connection pooling.
- [ ] **Compaction**: Automate CouchDB compaction.
- [ ] **Caching**: Implement intelligent caching strategies.

---

## Phase 4: Testing & Validation

**Goal**: Ensure the CouchDB implementation is robust, correct, and performant.
**Source**: `nexus/docs/COUCHDB_TEST_PLAN.md`

- [ ] **Database Operations Tests**: Cover creation, deletion, and info.
- [ ] **Document CRUD Tests**: Cover all aspects of document lifecycle.
- [ ] **Bulk Operations Tests**: Test bulk operations with mixed success and failure.
- [ ] **Changes Feed Tests**: Verify all change scenarios.
- [ ] **Query & View Tests**: Test various view query parameters.
- [ ] **Conflict Handling Tests**: Simulate and test conflict scenarios.
- [ ] **Performance & Stress Tests**: Benchmark large bulk operations and large documents.
- [ ] **Network Synchronization Tests**: Test replication and sync across multiple nodes.

---

## Phase 5: Application-Specific Features

### 5.1. Fiduciary & Apache Wave

**Goal**: Support the real-time document collaboration features required by the Fiduciary project.
**Source**: `fiduciary/README_APACHE_WAVE.md`

- [ ] **Real-time Sync**: Provide a reliable and low-latency changes feed for Wave's Operational Transformation.
- [ ] **Audit Trails**: Ensure all document changes are tracked and auditable.
- [ ] **Persistence**: Persist Wave operations in CouchDB documents.

### 5.2. Git Mirroring

**Goal**: Use CouchDB to store and mirror git repositories.
**Source**: `config/tasks.json`

- [ ] **Git Object to Attachment Mapping**: Map git objects to CouchDB attachments.
- [ ] **Refs Synchronization**: Sync git refs to CouchDB documents.
- [ ] **History Reconstruction**: Reconstruct git history from CouchDB.

---

## Research & Architecture

- [ ] **Database Treatise for Personal Computing Devices**: Continue research on optimal database architectures for personal devices, drawing lessons from CouchDB.
- [ ] **Evolution Analysis**: Formally compare CouchDB's append-only model with modern columnar storage technologies.
