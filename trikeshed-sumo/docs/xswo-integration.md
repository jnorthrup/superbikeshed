# xSWO Integration with BBCursive

## Overview

This document describes the integration of [xSWO](https://github.com/jnorthrup/xSWO) collections and Boost Spirit grammars into the TrikeShed ecosystem using bbcursive patterns. The integration absorbs xSWO's high-performance data structures and parsing capabilities while maintaining the TrikeShed architectural principles of Join composition, register-at-a-time scanning, and compile-time optimization.

## Key Components Absorbed

### Collections from xSWO

1. **HAT-trie** (`hat_trie.h`) - Cache-conscious trie for strings
2. **Quadbag** (`quadbag.hpp`) - Multi-index container for RDF quads using Boost.MultiIndex
3. **Array Hash** (`array_hash.h`) - Fast hash table for small sets
4. **Hat Set** (`hat_set.h`) - Set implementation using HAT-trie

### Grammars from xSWO

1. **SQL-2003** (`s2k3.bnf`) - ISO/IEC 9075-2:2003 Database Language SQL
2. **SPARQL** (`sparql.cpp`) - SPARQL Protocol and RDF Query Language

## Architecture

### Join Composition Integration

All xSWO components are integrated using Join composition patterns:

```kotlin
// HAT-trie node using Join composition
typealias HatTrieNode<T> = Join<T, Indexed<HatTrieNode<T>>>

// RDF Quad using Join composition
typealias Quad<T> = Join<Join<T, T>, Join<T, T>>

// Grammar rule using Join composition
typealias GrammarRule = Join<String, (List<KifToken>) -> ParseResult?>
```

### BBCursive Pattern Integration

xSWO components use bbcursive patterns for register-at-a-time scanning:

```kotlin
// BBCursive scanner for xSWO collections
object XswoBbcursiveScanner {
    fun <T> scanHatTrie(trie: HatTrie<T>): Indexed<T>
    fun <T> scanQuadBag(quadBag: QuadBag<T>): Indexed<Quad<T>>
    fun <T> scanArrayHash(arrayHash: ArrayHash<T>): Indexed<Join<T, T>>
}
```

### Triple Dispatch Integration

Compile-time optimized dispatch for xSWO operations:

```kotlin
interface XswoCollectionDispatcher<R> {
    fun dispatch(collection: Any, operation: String, params: List<Any>): R
}

interface XswoGrammarDispatcher<R> {
    fun dispatch(grammar: String, tokens: List<KifToken>, operation: String): R
}
```

## Implementation Details

### HAT-trie Integration

The HAT-trie (Hash Array Mapped Trie) is a cache-conscious trie data structure that provides efficient string operations:

```kotlin
class HatTrie<T> {
    fun insert(key: String, value: T) // Using bbcursive pattern
    fun search(key: String): T? // Using bbcursive pattern
}
```

**Key Features:**
- Cache-conscious design for better performance
- Burst behavior for handling large containers
- Join composition for memory efficiency
- Register-at-a-time scanning for SIMD optimization

### Quadbag Integration

The quadbag provides multi-index storage for RDF quads with SPARQL-like querying:

```kotlin
class QuadBag<T> {
    fun insert(quad: Quad<T>) // Using bbcursive pattern
    fun query(subject: T?, predicate: T?, object: T?, context: T?): Indexed<Quad<T>>
}
```

**Key Features:**
- Multiple indices (subject, predicate, object, context)
- SPARQL-like query interface
- Join composition for quad representation
- BBCursive scanning for efficient traversal

### Array Hash Integration

The array hash provides fast hash table operations for small sets:

```kotlin
class ArrayHash<T> {
    fun insert(key: T, value: T): Boolean // Using bbcursive pattern
    fun find(key: T): T? // Using bbcursive pattern
    fun entries(): Indexed<Join<T, T>> // Using Join composition
}
```

**Key Features:**
- Linear probing for collision resolution
- Fixed capacity for predictable performance
- Join composition for entry representation
- BBCursive patterns for operations

### SQL-2003 Grammar Integration

The SQL-2003 grammar provides parsing capabilities for SQL statements:

```kotlin
object Sql2003Grammar {
    val SQL_STATEMENT: GrammarRule
    val SELECT_STATEMENT: GrammarRule
    val INSERT_STATEMENT: GrammarRule
    val UPDATE_STATEMENT: GrammarRule
    val DELETE_STATEMENT: GrammarRule
}
```

**Key Features:**
- Character classification using Join composition
- Recursive descent parsing with bbcursive patterns
- Token-based parsing with KIF integration
- Register-at-a-time scanning for performance

### SPARQL Grammar Integration

The SPARQL grammar provides parsing capabilities for SPARQL queries:

```kotlin
object SparqlGrammar {
    val SPARQL_QUERY: GrammarRule
    val SELECT_QUERY: GrammarRule
    val CONSTRUCT_QUERY: GrammarRule
    val ASK_QUERY: GrammarRule
    val DESCRIBE_QUERY: GrammarRule
}
```

**Key Features:**
- SPARQL query type parsing
- Triple pattern and graph pattern parsing
- Join composition for query representation
- BBCursive patterns for efficient parsing

## Usage Examples

### Basic HAT-trie Usage

```kotlin
val trie = HatTrie<String>()
trie.insert("hello", "world")
trie.insert("help", "me")
val result = trie.search("hello") // Returns "world"
```

### Basic Quadbag Usage

```kotlin
val quadBag = QuadBag<String>()
quadBag.insert(Quad("alice", "knows", "bob", "context1"))
val results = quadBag.query(subject = "alice", predicate = "knows")
```

### Basic Array Hash Usage

```kotlin
val arrayHash = ArrayHash<String>(16)
arrayHash.insert("key", "value")
val result = arrayHash.find("key") // Returns "value"
```

### Basic Grammar Usage

```kotlin
val sqlTokens = listOf(
    KifToken.ParenOpen(0),
    KifToken.Symbol("SELECT", 1),
    KifToken.Symbol("*", 8),
    KifToken.ParenClose(10)
)
val result = Sql2003Grammar.SQL_STATEMENT.b(sqlTokens)
```

### BBCursive Scanner Usage

```kotlin
val trieScan = XswoBbcursiveScanner.scanHatTrie(trie)
val quadBagScan = XswoBbcursiveScanner.scanQuadBag(quadBag)
val arrayHashScan = XswoBbcursiveScanner.scanArrayHash(arrayHash)
```

### Triple Dispatch Usage

```kotlin
val trieResult = XswoCollectionProcessor.dispatch(trie, "search", listOf("key"))
val grammarResult = XswoGrammarProcessor.dispatch("sql2003", tokens, "parse")
```

## Performance Characteristics

### HAT-trie Performance

- **Insert**: O(k) where k is key length
- **Search**: O(k) where k is key length
- **Memory**: Cache-conscious design reduces cache misses
- **SIMD**: Register-at-a-time scanning enables vectorization

### Quadbag Performance

- **Insert**: O(1) amortized
- **Query**: O(log n) for indexed queries
- **Memory**: Join composition reduces memory overhead
- **SIMD**: BBCursive patterns enable vectorized operations

### Array Hash Performance

- **Insert**: O(1) average case
- **Find**: O(1) average case
- **Memory**: Fixed capacity for predictable usage
- **SIMD**: Linear probing is vectorization-friendly

### Grammar Performance

- **Parse**: O(n) where n is token count
- **Memory**: Join composition for AST representation
- **SIMD**: Register-at-a-time token processing

## Integration with TrikeShed

### SUMO Integration

The xSWO integration works seamlessly with the SUMO knowledge base:

```kotlin
// Create collections with SUMO knowledge
val trie = HatTrie<String>()
trie.insert("concept", "Human")

val quadBag = QuadBag<String>()
quadBag.insert(Quad("Human", "subclass", "Mammal", "SUMO"))

// Parse SUMO KIF using xSWO grammars
val kifTokens = KifParser.tokenize(kifContent)
val parsed = Sql2003Grammar.SQL_STATEMENT.b(kifTokens)
```

### BBCursive Integration

All xSWO components use bbcursive patterns for consistency:

```kotlin
// BBCursive-style operations
fun interface XswoOp<T> {
    fun apply(collection: Any): T?
}

// Register-at-a-time scanning
inline fun <T> scanXswoCollection(collection: Any): RegisterJoin<T, Int>?
```

### Join Integration

xSWO components use Join composition throughout:

```kotlin
// Join-based data structures
typealias XswoNode<T> = Join<T, Indexed<XswoNode<T>>>
typealias XswoResult<T> = Join<T, List<KifToken>>
```

## Testing

Comprehensive TDD tests are provided in `XswoIntegrationTest.kt`:

- HAT-trie insertion and search tests
- Quadbag query and Join composition tests
- Array hash operation tests
- Grammar parsing tests
- BBCursive scanner tests
- Triple dispatch tests
- Full integration workflow tests

## Demo

A comprehensive demo is provided in `XswoIntegrationDemo.kt` that shows:

1. HAT-trie operations with bbcursive patterns
2. Quadbag SPARQL-like querying
3. Array hash operations
4. SQL-2003 grammar parsing
5. SPARQL grammar parsing
6. BBCursive scanner usage
7. Triple dispatch operations
8. Full integration workflow

## Future Enhancements

### Planned Improvements

1. **Full HAT-trie Traversal**: Implement complete traversal for HAT-trie
2. **Advanced SPARQL Support**: Add more SPARQL query types and patterns
3. **SQL Optimization**: Add query optimization for SQL-2003
4. **Memory Pooling**: Add memory pooling for better performance
5. **SIMD Optimization**: Further optimize for SIMD operations

### Potential Extensions

1. **Graph Database Integration**: Use quadbag for graph database operations
2. **Streaming Parsers**: Add streaming capabilities for large files
3. **Distributed Collections**: Add distributed versions of collections
4. **Compile-time Code Generation**: Generate optimized code at compile time

## Conclusion

The xSWO integration successfully absorbs high-performance collections and grammars into the TrikeShed ecosystem while maintaining architectural consistency. The use of Join composition, bbcursive patterns, and register-at-a-time scanning ensures optimal performance while providing a clean, composable API.

The integration demonstrates how external libraries can be absorbed into the TrikeShed architecture without compromising on performance or architectural principles. 