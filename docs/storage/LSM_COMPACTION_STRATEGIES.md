# LSM Tree Compaction Strategies Specification

## Overview

LSM (Log-Structured Merge) trees require efficient compaction strategies to manage write amplification, space amplification, and read performance across different workload patterns. This specification defines the core compaction strategies and their implementation requirements.

## Core Compaction Strategies

### 1. Leveled Compaction (RocksDB/LevelDB Style)

**Characteristics:**
- Fixed size ratio between levels (typically 10x)
- Predictable space amplification (~1.1x)
- Higher write amplification (10-30x)
- Best for read-heavy workloads

**Implementation Requirements:**
- Compact when level exceeds target size
- Pick files with overlapping key ranges
- Maintain sorted levels for efficient reads

### 2. Tiered Compaction (Cassandra Style)

**Characteristics:**
- Merge files of similar size
- Lower write amplification (2-10x)
- Higher space amplification (~2x)
- Best for write-heavy workloads

**Implementation Requirements:**
- Group files by size buckets
- Merge when threshold reached (typically 4 files)
- Maintain size-based tiers

### 3. FIFO Compaction (Time-Series Optimized)

**Characteristics:**
- Zero write amplification
- TTL-based deletion
- Only works for time-series data
- Best for logs, metrics, time-series

**Implementation Requirements:**
- Delete old files based on TTL
- No merging required
- Simple file deletion

### 4. Universal Compaction (RocksDB Alternative)

**Characteristics:**
- Bounded space amplification
- Spiky I/O patterns
- Best for mixed workloads

**Implementation Requirements:**
- Compact based on space amplification ratio
- Merge all overlapping files at once
- Adaptive to workload patterns

## Columnar-Specific Strategies

### 5. Column-Aware Compaction

**Implementation Requirements:**
- Different strategy per column type
- Coordinate compactions across columns
- Batch compactions for I/O efficiency

### 6. Zone-Based Compaction

**Implementation Requirements:**
- Divide keyspace into zones
- Compact one zone at a time
- Round-robin through zones

### 7. Adaptive Compaction (ML-Driven)

**Implementation Requirements:**
- Collect workload signals
- Predict optimal strategy
- Adapt based on workload changes

## Hybrid Strategies

### 8. Hot/Cold Separation

**Implementation Requirements:**
- Recent data: aggressive compaction
- Old data: lazy compaction
- Age-based strategy selection

### 9. Priority-Based Compaction

**Implementation Requirements:**
- Score-based priority queue
- Space waste * read heat / estimated cost
- Run top priority compactions

### 10. Write-Buffer Aware Strategy

**Implementation Requirements:**
- Multiple write buffers for parallelism
- Coordinated flush when buffers near capacity
- Batch flush for I/O efficiency

## Strategy Selection Guidelines

### Workload-Based Recommendations

- **Time-series data with TTL**: FIFO Compaction
- **Write-heavy with relaxed consistency**: Tiered Compaction
- **Read-heavy with tight SLA**: Leveled Compaction
- **Large dataset with hot/cold pattern**: Hot/Cold Separation
- **Mixed workloads**: Universal Compaction

### Performance Targets

- **Write Amplification**: 1x (FIFO) to 30x (Leveled)
- **Space Amplification**: 1.1x (Leveled) to 2x (Tiered)
- **Read Latency**: <10ms for read-heavy workloads

## Implementation Requirements

### Core Interfaces

All compaction strategies must implement:
- `shouldCompact()`: Determine if compaction is needed
- `pickFilesToCompact()`: Select files for compaction
- `calculateWriteAmplification()`: Measure write overhead
- `calculateSpaceAmplification()`: Measure space overhead

### Performance Monitoring

- Track compaction metrics per strategy
- Monitor I/O patterns and costs
- Adapt strategy parameters based on workload

### Error Handling

- Graceful handling of corrupted files
- Recovery from compaction failures
- Rollback mechanisms for failed compactions 