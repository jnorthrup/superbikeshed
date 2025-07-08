# Composable Buckets of Isomorphs

## Concept

Buckets of isomorphs are collections of invertible, type-safe projections (e.g., JSON-to-row, row-to-JSON, bitmap index layouts) that can be composed, layered, or chained. This enables flexible, lossless data transformation, schema evolution, and multi-view analytics.

## Key Points
- **Buckets**: Groups of isomorphic projections or indexes, organized by schema, use-case, or query pattern.
- **Composability**: Each isomorph is invertible. Buckets can be composed for schema evolution, multi-view analytics, and efficient joins/unions.
- **Meta-Driven**: Each bucket's meta description encodes structure and isomorphism rules, supporting runtime composition and introspection.

## Minimal Code Sketch

```kotlin
interface Isomorph<A, B> {
    fun forward(a: A): B
    fun backward(b: B): A
}

data class IsomorphBucket<A, B>(
    val isomorphs: List<Isomorph<A, B>>,
    val meta: IsamMeta
) {
    fun composeAll(a: A): List<B> = isomorphs.map { it.forward(a) }
    fun invertAll(bs: List<B>): List<A> = isomorphs.zip(bs).map { (iso, b) -> iso.backward(b) }
}
```

## Applications
- **Schema evolution**: Compose buckets to support field addition/removal or structure changes.
- **Multi-view analytics**: Different projections for different queries or analytics tasks.
- **Efficient joins/unions**: Compose indexes/projections across buckets for set operations.

## Summary
Buckets of isomorphs provide a powerful, meta-driven foundation for flexible, lossless, and composable data transformation and indexing in modern storage and analytics systems.

## Implementation Status

### ✅ Implemented Uses in Codebase

#### 1. **JSON Scanner Isomorphism** (`trikeshed-json`)
- **Location**: `trikeshed-json/src/commonTest/kotlin/borg/trikeshed/json/BothFormatsTest.kt`
- **Implementation**: `isIsomorphicTo` extension function for JSON scanners
- **Usage**: Detects structural similarity between JSON documents regardless of values

```kotlin
// Test isomorphism detection
val json1 = """{"name":"test","count":42}"""
val json2 = """{"name":"other","count":99}"""
val scanner1 = json1.json()
val scanner2 = json2.json()
assertTrue(scanner1 isIsomorphicTo scanner2) // Same structure
```

#### 2. **DHT Bucket Isomorphism** (`trikeshed-dht`, `trikeshed-ipfs`)
- **Location**: `trikeshed-dht/src/commonMain/kotlin/borg/trikeshed/dht/kademlia/routing/`
- **Implementation**: Kademlia routing table buckets with isomorphic peer structures
- **Usage**: Peer discovery and routing with consistent bucket structures

#### 3. **ISAM Cursor Isomorphism** (`trikeshed-isam`)
- **Location**: `trikeshed-isam/src/commonMain/kotlin/borg/trikeshed/isam/`
- **Implementation**: Cursor operations with isomorphic data layouts
- **Usage**: Consistent data access patterns across different storage formats

### 🔄 Partial Implementations

#### 1. **Bitmap Streaming Isomorphism** (`trikeshed-ljson`)
- **Location**: `trikeshed-ljson/src/commonMain/kotlin/borg/trikeshed/ljson/BitmapStreaming.kt`
- **Status**: Basic structure exists, needs full isomorphic bucket implementation
- **Usage**: JSON to ISAM conversions with bitmap optimization

### 📋 Planned Implementations

#### 1. **Schema Evolution Buckets**
- **Purpose**: Handle database schema changes with isomorphic projections
- **Status**: Design phase
- **Priority**: Medium

#### 2. **Multi-View Analytics Buckets**
- **Purpose**: Different projections for different query patterns
- **Status**: Not started
- **Priority**: Low

## Current Limitations

1. **No Generic Bucket Framework**: Isomorphic buckets are implemented ad-hoc rather than through a unified framework
2. **Limited Composition**: Buckets cannot be easily composed or layered
3. **No Meta-Driven Structure**: Missing the meta description encoding mentioned in the concept
4. **Platform-Specific**: Implementations are tied to specific platforms rather than being cross-platform

## Next Steps

1. **Create Generic IsomorphBucket Framework**
2. **Implement Meta-Driven Structure**
3. **Add Composition Operators**
4. **Create Cross-Platform Support** 