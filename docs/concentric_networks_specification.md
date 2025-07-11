# Concentric Networks Specification

## Overview

This document addresses all ambiguities in the concentric network architecture and provides a definitive specification for the largest network that was previously undefined.

**Key Insight**: "Concentric" refers to semantic scope and visibility based on NUID proximity. Larger NUIDs attract broader agent scope, while smaller subnets are created and visible to agents seeking specific semantics.

## Current Ambiguities Identified

### 1. SENATE Ring Size Inconsistency

**Problem**: The SENATE ring is defined with different sizes across the codebase:
- `24` agents in most implementations (fiduciary, ConcentricDispatch, QuicConcentricProtocol)
- `100` agents in some implementations (FiduciaryBlobstoreLauncher, LAUNCH_FIDUCIARY_NOW.md)

**Resolution**: Standardize on **24 agents** for SENATE ring based on the mathematical progression and practical considerations.

### 2. Ring Size Progression Pattern

**Current Pattern**: 1 → 2 → 3 → 5 → 12 → 24 (SENATE)
**Missing**: The largest network that follows the mathematical progression

**Resolution**: Add the **CONGRESS** ring with **100 agents** as the largest defined network.

## Definitive Ring Specification

### NUID-Based Semantic Scope

The concentric network operates on NUID proximity where:

- **Larger NUIDs** attract broader agent scope and create wider semantic visibility
- **Smaller NUIDs** create focused subnets visible to agents seeking specific semantics
- **Ring levels** represent semantic scope boundaries based on NUID distance

The ring sizes follow a progression based on practical group dynamics and mathematical properties:

```kotlin
enum class ConcentricRing(
    val level: Int,
    val groupSize: Int,
    val quorumSize: Int,
    val maxBandwidth: Long,
    val priority: StreamPriority
) {
    CORE(0, 1, 1, Long.MAX_VALUE, StreamPriority.URGENT),
    DYAD(1, 2, 2, 100_000_000L, StreamPriority.HIGH),
    TRIAD(2, 3, 2, 50_000_000L, StreamPriority.HIGH),
    PENTAD(3, 5, 3, 25_000_000L, StreamPriority.NORMAL),
    DODECAD(4, 12, 7, 10_000_000L, StreamPriority.NORMAL),
    SENATE(5, 24, 13, 5_000_000L, StreamPriority.LOW),
    CONGRESS(6, 100, 51, 2_000_000L, StreamPriority.BACKGROUND)
}
```

### Ring Characteristics

| Ring | Size | Quorum | Bandwidth | Priority |
|------|------|--------|-----------|----------|
| CORE | 1 | 1 | Unlimited | URGENT |
| DYAD | 2 | 2 | 100 MB/s | HIGH |
| TRIAD | 3 | 2 | 50 MB/s | HIGH |
| PENTAD | 5 | 3 | 25 MB/s | NORMAL |
| DODECAD | 12 | 7 | 10 MB/s | NORMAL |
| SENATE | 24 | 13 | 5 MB/s | LOW |
| CONGRESS | 100 | 51 | 2 MB/s | BACKGROUND |



## NUID-Based Network Topology

### Semantic Scope and Visibility

```kotlin
class NUIDConcentricTopology {
    // NUID distance determines semantic scope
    fun calculateSemanticScope(nuid1: NUID, nuid2: NUID): Int {
        return nuid1.xor(nuid2).countOneBits()
    }
    
    // Agents with larger NUIDs see broader scope
    fun getVisibleAgents(agentNUID: NUID, scope: Int): List<NUID> {
        return allAgents.filter { otherNUID ->
            calculateSemanticScope(agentNUID, otherNUID) <= scope
        }
    }
    
    // Smaller subnets for specific semantics
    fun createSemanticSubnet(targetSemantics: String): NUID {
        return NUID.fromSemantic(targetSemantics) // Creates focused NUID
    }
}
```

### Communication Patterns

```kotlin
class ConcentricTopology {
    // Outward communication (always allowed)
    fun allowOutward(from: ConcentricRing, to: ConcentricRing): Boolean {
        return from.level <= to.level
    }
    
    // Inward communication (restricted)
    fun allowInward(from: ConcentricRing, to: ConcentricRing): Boolean {
        return when {
            from.level - to.level == 1 -> true  // Adjacent rings
            from.level - to.level == 2 -> true  // Skip one ring
            else -> false  // No deeper access
        }
    }
    
    // Peer communication within ring
    fun allowPeer(ring: ConcentricRing): Boolean = true
}
```

### NUID-Based Trust and Scope

```kotlin
enum class TrustLevel {
    FULL,      // CORE only - smallest NUID scope
    HIGH,      // DYAD, TRIAD - focused semantic scope
    MEDIUM,    // PENTAD, DODECAD - moderate semantic scope
    LOW,       // SENATE - broad semantic scope
    MINIMAL    // CONGRESS - widest NUID scope
}

fun getTrustLevel(ring: ConcentricRing): TrustLevel {
    return when (ring.level) {
        0 -> TrustLevel.FULL      // Smallest NUID, highest trust
        1, 2 -> TrustLevel.HIGH   // Focused semantic subnets
        3, 4 -> TrustLevel.MEDIUM // Moderate semantic scope
        5 -> TrustLevel.LOW       // Broad semantic scope
        6 -> TrustLevel.MINIMAL   // Largest NUID, widest scope
        else -> TrustLevel.MINIMAL
    }
}

// NUID scope determines what agents can see and interact with
fun getNUIDScope(ring: ConcentricRing): Int {
    return when (ring.level) {
        0 -> 1    // CORE: sees only immediate neighbors
        1 -> 2    // DYAD: sees 2-hop scope
        2 -> 4    // TRIAD: sees 4-hop scope
        3 -> 8    // PENTAD: sees 8-hop scope
        4 -> 16   // DODECAD: sees 16-hop scope
        5 -> 32   // SENATE: sees 32-hop scope
        6 -> 64   // CONGRESS: sees 64-hop scope
        else -> 64
    }
}
```

## Implementation Guidelines

### 1. NUID-Based Ring Definitions

The concentric network uses NUID proximity to determine semantic scope:

- **CORE**: Smallest NUID scope (1-hop), highest trust, focused semantics
- **DYAD**: 2-hop NUID scope, peer validation semantics
- **TRIAD**: 4-hop NUID scope, consensus processing semantics
- **PENTAD**: 8-hop NUID scope, distributed analysis semantics
- **DODECAD**: 16-hop NUID scope, parallel processing semantics
- **SENATE**: 32-hop NUID scope, large-scale coordination semantics
- **CONGRESS**: 64-hop NUID scope, mass distribution semantics

### 2. Standardize Ring Definitions

All implementations must use the standardized ring sizes:
- CORE: 1 agent
- DYAD: 2 agents
- TRIAD: 3 agents
- PENTAD: 5 agents
- DODECAD: 12 agents
- SENATE: 24 agents
- CONGRESS: 100 agents

### 2. Quorum Calculation

Quorum sizes follow the formula: `ceil(groupSize * 0.5) + 1` for odd sizes, `ceil(groupSize * 0.5)` for even sizes.

### 3. Bandwidth Allocation

Bandwidth decreases by approximately 50% per ring level, reflecting the increasing number of agents and reduced per-agent resource allocation.

### 4. Priority Assignment

Priorities follow a descending pattern from URGENT (CORE) to BACKGROUND (CONGRESS), ensuring critical operations get precedence.

## Migration Strategy

### Phase 1: Update Core Definitions
1. Update `ConcentricRing` enum in all modules
2. Standardize on 24 agents for SENATE
3. Add CONGRESS ring definition

### Phase 2: Update Implementations
1. Update `FiduciaryBlobstoreLauncher.kt`
2. Update `LAUNCH_FIDUCIARY_NOW.md`
3. Update all agent factory implementations

### Phase 3: Update Documentation
1. Update all README files
2. Update architecture diagrams
3. Update API documentation

## Validation

### Test Cases

```kotlin
@Test
fun testRingProgression() {
    val rings = ConcentricRing.values()
    assertEquals(7, rings.size)  // CORE through CONGRESS
    
    // Verify progression
    assertEquals(1, ConcentricRing.CORE.groupSize)
    assertEquals(2, ConcentricRing.DYAD.groupSize)
    assertEquals(3, ConcentricRing.TRIAD.groupSize)
    assertEquals(5, ConcentricRing.PENTAD.groupSize)
    assertEquals(12, ConcentricRing.DODECAD.groupSize)
    assertEquals(24, ConcentricRing.SENATE.groupSize)
    assertEquals(100, ConcentricRing.CONGRESS.groupSize)
}

@Test
fun testQuorumCalculation() {
    assertEquals(1, ConcentricRing.CORE.quorumSize)
    assertEquals(2, ConcentricRing.DYAD.quorumSize)
    assertEquals(2, ConcentricRing.TRIAD.quorumSize)
    assertEquals(3, ConcentricRing.PENTAD.quorumSize)
    assertEquals(7, ConcentricRing.DODECAD.quorumSize)
    assertEquals(13, ConcentricRing.SENATE.quorumSize)
    assertEquals(51, ConcentricRing.CONGRESS.quorumSize)
}
```

## Conclusion

This specification resolves all ambiguities in the concentric network architecture by:

1. **Standardizing ring sizes** across all implementations
2. **Defining the largest network** (CONGRESS with 100 agents)
3. **Establishing clear communication rules** between rings
4. **Providing implementation guidelines** for consistency
5. **Including validation tests** to ensure compliance

The CONGRESS ring serves as the largest defined network with the widest NUID scope (64-hop) for mass distribution and global synchronization operations. The concentric network hierarchy operates on NUID proximity where larger NUIDs attract broader agent scope and smaller subnets are created for specific semantics, completing the semantic scope progression from 1-hop to 64-hop visibility. 