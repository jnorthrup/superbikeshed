package borg.trikeshed.attention

import borg.trikeshed.lib.Join
import borg.trikeshed.lib.j
import borg.trikeshed.lib.Twin
import kotlin.jvm.JvmInline

/**
 * Inline compile-time double dispatch for attention predication
 * Clean, normalized, no runtime overhead
 */

// Normalized attention is just Twin<Long>
typealias NormalizedAttention = Twin<Long>  // start j end

// Inline predicates for first dispatch
@JvmInline value class Sequential(val range: NormalizedAttention)
@JvmInline value class Random(val range: NormalizedAttention)  
@JvmInline value class Sparse(val range: NormalizedAttention)

// Inline sources for second dispatch
@JvmInline value class HTTP(val url: String)
@JvmInline value class Torrent(val hash: Long)
@JvmInline value class Local(val fd: Int)

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