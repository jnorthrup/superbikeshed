# TrikeShed Swimlane Development System

## Overview

Swimlanes provide isolated build environments for parallel development of TrikeShed features. Each swimlane is a complete clone (or git worktree) of the superbikeshed repository, allowing independent builds and experiments without affecting the main codebase.

## Architecture Vision: RelaxFactory Reboot

The TrikeShed swimlane system supports the RelaxFactory/RequestFactory reboot - a distributed system architecture that leverages:

- **CouchDB**: Document storage with built-in replication and conflict resolution
- **IPFS**: Content-addressed distributed storage for immutable data
- **QUIC**: Modern transport protocol for low-latency communication

### Object Storage Latency Tiers

```
Register Packing (0-1 cycles) → L1 Cache
  ↓
CouchDB Views (1-10ms) → L2 Cache  
  ↓
IPFS Local Pin (10-100ms) → L3 Cache
  ↓
IPFS Network Fetch (100ms-1s) → Remote Storage
  ↓
QUIC Stream Multiplexing → Parallel Fetch Optimization
```

## Quick Start

### Create a New Swimlane

```bash
# Basic usage
./setup-swimlane.sh feature-name

# Use git worktree (faster, shares .git directory)
./setup-swimlane.sh feature-name --worktree

# Example: Create swimlane for CouchDB/IPFS/QUIC integration
./setup-swimlane.sh couch-ipfs-quic
```

### Sync Changes

```bash
# Pull changes from main into swimlane
./sync-swimlane.sh feature-name --from-main

# Push changes from swimlane to main
./sync-swimlane.sh feature-name --to-main

# Sync specific modules only
./sync-swimlane.sh feature-name --from-main Trikeshed ksp-processors
```

## Swimlane Directory Structure

```
v2superbikeshed/
├── .claude/
│   └── commands/
│       ├── setup-swimlane.sh
│       ├── sync-swimlane.sh
│       └── README.md (this file)
└── build/                        # Git-ignored
    ├── couch-ipfs-quic/         # RelaxFactory distributed integration
    ├── jk-kj-double-dispatch/   # Manual double dispatch patterns
    ├── register-packing/        # Zero-allocation packing strategies
    └── trikeshed-core/          # Core library development
```

## Key Swimlanes

### couch-ipfs-quic
**Purpose**: Implement the RelaxFactory vision with distributed storage and networking

**Focus Areas**:
- CouchDB document store integration with TrikeShed Cursor types
- IPFS content addressing for immutable Join<A,B> structures  
- QUIC transport for low-latency MetaSeries streaming
- Object storage latency optimization strategies

**Key Files**:
- `Trikeshed/src/commonMain/kotlin/borg/trikeshed/couchdb/`
- `Trikeshed/src/commonMain/kotlin/borg/trikeshed/ipfs/`
- `Trikeshed/src/commonMain/kotlin/borg/trikeshed/net/quic/`

### jk-kj-double-dispatch
**Purpose**: Manual double dispatch implementation for register packing

**Focus Areas**:
- Explicit type-based dispatch without code generation
- jk pattern: primary dispatch on left operand
- kj pattern: primary dispatch on right operand
- Waterfall pattern combining both approaches

**Key Files**:
- `Trikeshed/src/commonMain/kotlin/borg/trikeshed/lib/PackingStrategies.kt`
- `Trikeshed/src/commonMain/kotlin/borg/trikeshed/lib/RegisterFastlane.kt`

### register-packing
**Purpose**: Optimize primitive packing for zero-allocation performance

**Focus Areas**:
- CPU register utilization strategies
- Primitive type combinations
- Token packing for parsers
- PackedView interface for unified access

## Development Workflow

1. **Create swimlane** for your feature
2. **Develop** in isolation with full build/test capability
3. **Sync from main** periodically to stay current
4. **Test** thoroughly in the isolated environment
5. **Sync to main** when feature is ready
6. **Create PR** from the swimlane branch

## Advanced Usage

### Working with Git Worktrees

Git worktrees share the same .git directory, making them lightweight:

```bash
# List all worktrees
git worktree list

# Remove a worktree swimlane
git worktree remove build/feature-name
```

### Cleaning Up Swimlanes

```bash
# Remove a swimlane completely
rm -rf build/feature-name

# For worktrees, also run:
git worktree prune
```

### Parallel Builds

Each swimlane can build independently:

```bash
# Terminal 1
cd build/couch-ipfs-quic
./build-local.sh

# Terminal 2  
cd build/register-packing
./gradlew :Trikeshed:test

# Terminal 3
cd build/jk-kj-double-dispatch
./gradlew :ksp-processors:build
```

## Best Practices

1. **Name swimlanes clearly**: Use descriptive names that indicate the feature
2. **Sync regularly**: Keep swimlanes current with main to avoid conflicts
3. **Test in isolation**: Ensure changes work in the swimlane before syncing
4. **Document changes**: Update relevant .md files in the swimlane
5. **Clean up**: Remove swimlanes when features are merged

## Troubleshooting

### Swimlane won't build
- Check `gradle.properties` in the swimlane
- Ensure all dependencies are available
- Try `./gradlew clean` first

### Sync conflicts
- Resolve in the swimlane first
- Use `git status` to identify conflicts
- Manually merge if necessary

### Out of disk space
- Remove old swimlanes
- Use `--worktree` for lightweight clones
- Clean build artifacts: `find build -name build -type d -exec rm -rf {} +`