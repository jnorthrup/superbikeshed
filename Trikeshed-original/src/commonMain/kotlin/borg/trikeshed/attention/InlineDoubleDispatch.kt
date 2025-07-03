package borg.trikeshed.attention

import borg.trikeshed.lib.Join
import borg.trikeshed.lib.j
import borg.trikeshed.lib.Twin
import kotlin.jvm.JvmInline

/**
 * # Interest-Driven Dataflow and Competitive Query Planning (Normalized Settlement)
 *
 * This module models data access and transformation as a composable, competitive dataflow graph, where each "gate" (operator)
 * projects data closer to a user-defined "interest" (query/goal). An "interest solver" (query planner/automated planner)
 * selects and sequences gates, optimizing for cost and fidelity, drawing on principles from query optimization, dataflow systems,
 * and multi-agent planning. The process is extensible, supporting new formats and codecs, and ultimately yields normalized,
 * indexable outputs (plaintext, tags, hashes).
 *
 * ## Canonical Mapping
 * - **Gates**: Operators in a dataflow/query plan (Selinger et al., Graefe, Aurora, D-Streams)
 * - **Interest**: Query or goal state (databases, IR, planning)
 * - **Interest Solver**: Query planner/automated planner (Selinger et al., Ghallab et al., RFC 2295)
 * - **Competitive Game**: Cost-based plan selection, multi-agent optimization (Shoham & Leyton-Brown)
 * - **Composable Pipelines**: Operator DAGs, extensible query plans (Aurora, Graefe)
 * - **Final Output**: Materialized result, extracted features/metadata (Tika, ffmpeg, IR)
 *
 * ## Key Citations
 * - Selinger et al., "Access Path Selection in a Relational Database Management System" (1979)
 * - Graefe, "Query Evaluation Techniques for Large Databases" (1993)
 * - Abadi et al., "Aurora: a new model and architecture for data stream management" (2003)
 * - Zaharia et al., "Discretized Streams: Fault-Tolerant Streaming Computation at Scale" (2013)
 * - Ghallab, Nau, Traverso, "Automated Planning: Theory and Practice" (2004)
 * - Shoham & Leyton-Brown, "Multiagent Systems: Algorithmic, Game-Theoretic, and Logical Foundations" (2009)
 * - RFC 2295, "Content Negotiation in HTTP"
 * - Apache Tika documentation
 */

// Normalized attention is just Twin<Long>
typealias NormalizedAttention = Twin<Long>  // start j end

// Inline predicates for first dispatch
@JvmInline value class Sequential(val range: NormalizedAttention)
@JvmInline value class Random(val range: NormalizedAttention)  
@JvmInline value class Sparse(val range: NormalizedAttention)

// Sealed interface for runtime double dispatch
sealed interface Source
@JvmInline value class HTTP(val url: String) : Source
@JvmInline value class Torrent(val hash: Long) : Source
@JvmInline value class Local(val fd: Int) : Source

// Double dispatch through inline methods
inline fun Sequential.fetch(source: HTTP): ByteArray = 
    // HTTP sequential = single range request
    ByteArray((range.b - range.a).toInt())

inline fun Sequential.fetch(source: Torrent): ByteArray = 
    // Torrent sequential = piece stream
    ByteArray(16384)

inline fun Sequential.fetch(source: Local): ByteArray = 
    // Local sequential = mmap
    ByteArray((range.b - range.a).toInt())

inline fun Random.fetch(source: HTTP): ByteArray = 
    // HTTP random = multiple requests
    ByteArray(1024)

inline fun Random.fetch(source: Torrent): ByteArray = 
    // Torrent random = piece selection
    ByteArray(16384)

inline fun Random.fetch(source: Local): ByteArray = 
    // Local random = pread
    ByteArray(4096)

inline fun Sparse.fetch(source: HTTP): ByteArray = 
    // HTTP sparse = multipart ranges
    ByteArray(256)

inline fun Sparse.fetch(source: Torrent): ByteArray = 
    // Torrent sparse = selective pieces
    ByteArray(8192)

inline fun Sparse.fetch(source: Local): ByteArray = 
    // Local sparse = vectored IO
    ByteArray(512)

// Predicate constructors
fun seq(start: Long, end: Long) = Sequential(start j end)
fun rnd(start: Long, end: Long) = Random(start j end)
fun sparse(start: Long, end: Long) = Sparse(start j end)

// Source constructors  
fun http(url: String) = HTTP(url)
fun torrent(hash: Long) = Torrent(hash)
fun local(fd: Int) = Local(fd)

// Centralized double dispatch for runtime flexibility
inline fun Sequential.process(source: Source): ByteArray = when (source) {
    is HTTP -> fetch(source)
    is Torrent -> fetch(source)
    is Local -> fetch(source)
}

inline fun Random.process(source: Source): ByteArray = when (source) {
    is HTTP -> fetch(source)
    is Torrent -> fetch(source)
    is Local -> fetch(source)
}

inline fun Sparse.process(source: Source): ByteArray = when (source) {
    is HTTP -> fetch(source)
    is Torrent -> fetch(source)
    is Local -> fetch(source)
}

// Usage - all resolved at compile time
fun demo() {
    val s1 = seq(0L, 1024L)
    val s2 = rnd(0L, 4096L)
    val s3 = sparse(0L, 256L)
    
    val http = http("example.com")
    val torrent = torrent(0x12345678L)
    val local = local(3)
    
    // Compile-time dispatch - no virtual calls
    val r1 = s1.fetch(http)      // Sequential + HTTP
    val r2 = s2.fetch(torrent)   // Random + Torrent (optimized)
    val r3 = s3.fetch(local)     // Sparse + Local (vectored)
}

/**
 * This is normalized:
 * - Everything is Twin<Long> at the core
 * - Inline classes add zero overhead
 * - Double dispatch resolved at compile time
 * - Predication through inline method selection
 */