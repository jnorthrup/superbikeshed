# Fiduciary System - Normalized Documentation

## System Overview

The Fiduciary System is a distributed, cryptographically-secure document management platform combining CouchDB, IPFS, and Wave CRDTs with a concentric agent network for intelligent curation and processing.

## Core Components

### 1. Storage Layer - CouchDB with io_uring

**Purpose**: High-performance document storage with async I/O
**Status**: Implemented, not running

```
Component: UringCouchDBServer
Location: platform-launcher/src/main/kotlin/borg/trikeshed/launcher/
Protocols: REST (5984), QUIC (5985), IPFS (5986)
```

**Key Features**:
- io_uring event loop (kqueue on macOS)
- Multi-protocol support
- Channelized blob storage
- CouchDB-compatible API

### 2. Distributed Consensus - Git-IPFS-CRDT

**Purpose**: Conflict-free distributed document editing
**Status**: Fully implemented, not integrated

```
Component: GitIPFSCRDT + WaveCRDTDistributed
Location: fiduciary/src/commonMain/kotlin/borg/trikeshed/fiduciary/
```

**Key Features**:
- Git commits as CRDT operations
- IPFS content addressing
- Wave operational transformation
- Multi-party consensus

### 3. Intelligence Layer - Concentric Agent Network

**Purpose**: Distributed processing with trust-based rings
**Status**: Implemented, not deployed

```
Component: ConcentricQuicAgent + CurationAgent
Location: platform-launcher/ and fiduciary/src/commonMain/kotlin/fiduciary/agents/
```

**Ring Topology**:
```
CORE (1) → DYAD (2) → TRIAD (3) → PENTAD (5) → DODECAD (12) → SENATE (100)
```

**Agent Capabilities**:
- Content validation
- Quality assessment  
- Provenance tracking
- Trust verification
- Knowledge synthesis

### 4. Security Layer - Fiduciary Cryptography

**Purpose**: Document encryption and integrity
**Status**: Implemented, not active

```
Component: FiduciaryCryptoSecurity
Location: platform-launcher/src/main/kotlin/borg/trikeshed/launcher/
```

**Security Stack**:
- AES-256-GCM encryption
- RSA-4096 signatures
- Ring-based access control
- Immutable audit trails

### 5. Knowledge Layer - Bitgraph Ontology

**Purpose**: Efficient ontological reasoning
**Status**: ✅ FULLY WORKING

```
Component: SumoBitgraph + BitgraphOperations
Location: trikeshed-sumo/src/commonMain/kotlin/borg/trikeshed/sumo/bitgraph/
```

**Operations**:
- Subsumption checking: O(1)
- Concept intersection: O(1) 
- Transitive closure: O(n)
- Clustering: O(n²)

## Data Flow Architecture

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│  User Request   │────▶│ Protocol Handler │────▶│ Concentric Ring │
└─────────────────┘     └──────────────────┘     └────────┬────────┘
                                                           │
                        ┌──────────────────┐               ▼
                        │  Git-IPFS-CRDT   │◀────── Agent Processing
                        └────────┬─────────┘               │
                                 │                         │
                        ┌────────▼─────────┐     ┌─────────▼────────┐
                        │   CouchDB Store  │◀────│ Bitgraph Reason  │
                        └────────┬─────────┘     └──────────────────┘
                                 │
                        ┌────────▼─────────┐
                        │   Audit Trail    │
                        └──────────────────┘
```

## API Endpoints

### REST API (Port 5984)

```
GET    /                     # Server info
GET    /_all_dbs            # List databases
PUT    /{db}                # Create database
DELETE /{db}                # Delete database
GET    /{db}/{doc}          # Get document
PUT    /{db}/{doc}          # Create/update document
DELETE /{db}/{doc}          # Delete document
POST   /{db}/_bulk_docs     # Bulk operations
GET    /{db}/_design/{ddoc}/_view/{view}  # Query view
```

### QUIC API (Port 5985)

```
Stream Priorities:
- URGENT: Core ring communications
- HIGH: Inward ring messages
- NORMAL: Outward ring messages  
- LOW: Peer-to-peer messages
```

### IPFS API (Port 5986)

```
POST   /ipfs/add            # Add content
GET    /ipfs/{cid}          # Get by CID
GET    /ipns/{name}         # Resolve IPNS
```

## System Databases

```
_users                  # User authentication
_replicator            # Replication configuration
_global_changes        # Change feed
fiduciary_ledger       # Transaction log
patrick_devine_archives # Archive metadata
bitgraph_cache         # Ontology cache
agent_coordination     # Agent state
audit_trail           # Security audit log
```

## Configuration

### Environment Variables

```bash
FIDUCIARY_HOME          # Installation directory
FIDUCIARY_DATA          # Data storage path
FIDUCIARY_LOG           # Log directory
FIDUCIARY_COUCHDB_PORT  # REST API port (default: 5984)
FIDUCIARY_QUIC_PORT     # QUIC port (default: 5985)
FIDUCIARY_IPFS_PORT     # IPFS port (default: 5986)
```

### Startup Parameters

```bash
--data-dir              # Data directory
--log-level            # Logging verbosity
--enable-ssl           # Enable TLS
--ring-config          # Agent ring configuration
--storage-backend      # Storage implementation
```

## Deployment

### Local Development

```bash
# Build
./gradlew :platform-launcher:build

# Run
./gradlew :platform-launcher:runFiduciary

# Test
./gradlew :platform-launcher:test
```

### Docker

```dockerfile
FROM openjdk:17-slim
COPY build/libs/fiduciary.jar /app/
WORKDIR /app
EXPOSE 5984 5985 5986
CMD ["java", "-jar", "fiduciary.jar"]
```

### Kubernetes

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: fiduciary-server
spec:
  replicas: 3
  template:
    spec:
      containers:
      - name: fiduciary
        image: fiduciary:latest
        ports:
        - containerPort: 5984
        - containerPort: 5985
        - containerPort: 5986
```

## Monitoring

### Health Checks

```
GET /_up                    # Basic health
GET /_node/_local/_stats    # Node statistics
GET /_active_tasks          # Running tasks
```

### Metrics

- Request latency (p50, p95, p99)
- Agent queue depth
- CRDT merge conflicts
- Storage usage
- Network bandwidth

### Logging

```
fiduciary.server     # Server operations
fiduciary.agents     # Agent activities
fiduciary.security   # Security events
fiduciary.crdt       # CRDT operations
```

## Current Status Summary

| Component | Implemented | Tested | Integrated | Running |
|-----------|------------|---------|------------|---------|
| CouchDB Server | ✅ | ✅ | ❌ | ❌ |
| io_uring | ✅ | ✅ | ❌ | ❌ |
| Git-IPFS-CRDT | ✅ | ✅ | ❌ | ❌ |
| Concentric Agents | ✅ | ✅ | ❌ | ❌ |
| Crypto Security | ✅ | ✅ | ❌ | ❌ |
| Bitgraph | ✅ | ✅ | ✅ | ✅ |
| Storage Backend | ❌ | - | - | - |
| Network Listeners | ❌ | - | - | - |
| Main Entry Point | ❌ | - | - | - |

## Next Steps

1. **Create main() entry point** - LaunchFiduciary.kt
2. **Implement persistent storage** - Replace in-memory maps
3. **Start network listeners** - Bind to actual ports
4. **Connect components** - Wire up the integrations
5. **Deploy and monitor** - Get it running in production

---
*Last Updated: January 10, 2025*
*Version: 0.9.0 (Pre-Launch)*