package borg.trikeshed.lib

/**
 * ByteBuffer Pyramid Architecture
 * 
 * A unified type hierarchy for byte/char data handling using CoreTypes
 * All mutable operations are encapsulated within pure functions returning immutable Joins
 * 
 * Join<A,B> provides rich composition through:
 * - MetaSeries<A,T> = Join<A, (A) -> T> for metadata-driven access
 * - Indexed<T> = Join<Int, (Int) -> T> for position-based access
 * - Pure functional dispatch with potentiate transformations
 */

// Core pyramid types - MetaSeries specializations for rich composition
typealias BytePyramid = MetaSeries<Int, Byte>  // Join<Int, (Int) -> Byte>
typealias CharPyramid = MetaSeries<Int, Char>  // Join<Int, (Int) -> Char>

// Advanced pyramid types with metadata
typealias ByteMetaPyramid<M> = MetaSeries<M, Byte>  // Join<M, (M) -> Byte>
typealias CharMetaPyramid<M> = MetaSeries<M, Char>  // Join<M, (M) -> Char>

// Factory functions using mutable internals for efficiency
fun ByteArray.toPyramid(): BytePyramid = this.size j { i -> this[i] }
fun CharArray.toPyramid(): CharPyramid = this.size j { i -> this[i] }
fun String.toPyramid(): CharPyramid = this.length j { i -> this[i] }

// Rich composition operators for pyramids
operator fun <M, T> MetaSeries<M, T>.plus(other: MetaSeries<M, T>): MetaSeries<M, T> =
    this.a j { m -> if (m in this) this.b(m) else other.b(m) }

// Transform pyramid with metadata preservation
fun <M, T, R> MetaSeries<M, T>.map(f: (T) -> R): MetaSeries<M, R> =
    this.a j { m -> f(this.b(m)) }

// Compose pyramids through join dispatch
fun <A, B, T> MetaSeries<A, T>.compose(bridge: MetaSeries<B, A>): MetaSeries<B, T> =
    bridge.a j { b -> this.b(bridge.b(b)) }

// ByteBuffer-like operations as pure functions
data class PyramidSlice<M>(
    val pyramid: MetaSeries<M, Byte>,
    val position: M,
    val limit: M,
    val mark: M? = null
) where M : Comparable<M> {
    // Abstract over position types - Int, Long, or custom indices
    val hasRemaining: Boolean get() = position < limit
    
    // Pure functional operations returning new slices
    fun get(): Join<Byte, PyramidSlice<M>> = 
        if (!hasRemaining) throw IndexOutOfBoundsException()
        else pyramid.b(position) j this // position increment depends on M type
    
    fun put(byte: Byte): PyramidSlice = copy(position = position + 1)
    fun flip(): PyramidSlice = copy(position = 0, limit = position, mark = -1)
    fun clear(): PyramidSlice = copy(position = 0, limit = pyramid.a, mark = -1)
    fun rewind(): PyramidSlice = copy(position = 0, mark = -1)
    fun mark(): PyramidSlice = copy(mark = position)
    fun reset(): PyramidSlice = copy(position = if (mark >= 0) mark else position)
    
    // Slice operations
    fun slice(): PyramidSlice = PyramidSlice(
        pyramid = pyramid.a j { i -> pyramid.b(position + i) },
        position = 0,
        limit = remaining
    )
    
    // Convert to array (mutable internally, immutable result)
    fun toArray(): ByteArray = ByteArray(remaining) { pyramid.b(position + it) }
}

// Extension to work with existing ByteIndexed/CharIndexed
fun ByteIndexed.toPyramid(): BytePyramid = buf
fun CharIndexed.toPyramid(): CharPyramid = buf

fun BytePyramid.toByteIndexed(): ByteIndexed = ByteIndexed(this)
fun CharPyramid.toCharIndexed(): CharIndexed = CharIndexed(this)

// Potentiate transformations - pure functional power
fun <M, T> MetaSeries<M, T>.potentiate(n: Int): MetaSeries<M, T> =
    if (n <= 0) this
    else this.a j { m -> 
        var result = this.b(m)
        repeat(n - 1) { result = this.b(m) }
        result
    }

// Join dispatch for rich type composition
fun <A, B, C> Join<A, B>.dispatch(f: (A, B) -> C): C = f(a, b)
fun <A, B, C, D> Join<Join<A, B>, C>.dispatch2(f: (A, B, C) -> D): D = 
    f(a.a, a.b, b)

// Pyramid composition through join chaining
fun BytePyramid.chain(other: BytePyramid): BytePyramid =
    (this.a + other.a) j { i -> 
        if (i < this.a) this.b(i) else other.b(i - this.a)
    }

// BBCursive integration
interface PyramidParser<T> {
    fun parse(pyramid: BytePyramid): Join<T?, Int> // result j consumed
}

// Combinator builders
fun <T> pyramidParser(f: (BytePyramid) -> Join<T?, Int>): PyramidParser<T> = 
    object : PyramidParser<T> {
        override fun parse(pyramid: BytePyramid) = f(pyramid)
    }

/**
 * Entity Registry - All consumers/producers of the pyramid
 * 
 * PRODUCERS (create pyramids):
 * - ByteArray.toPyramid()
 * - CharArray.toPyramid() 
 * - String.toPyramid()
 * - ByteIndexed.toPyramid()
 * - CharIndexed.toPyramid()
 * - Network IO:
 *   - trikeshed-net/http/HttpParser.kt - PlatformByteBuffer -> ByteArray
 *   - trikeshed-net/http/HttpServer.kt - ByteBuffer.allocate/wrap
 *   - trikeshed-net/ssh/SSHParser.kt - ByteIndexedBuffer operations
 *   - trikeshed-net/ssh/SSHProtocol.kt - ByteBuffer operations
 *   - trikeshed-net/quic/QuicStream.kt - ByteBuffer.allocate
 *   - trikeshed-net/quic/QuicConnection.kt - ByteBuffer.allocate
 * - File IO:
 *   - trikeshed-io/MappedFile.kt - ByteBuffer mapping
 *   - trikeshed-isam/FileAccessActual.kt - ByteBuffer.allocate
 *   - trikeshed-isam/ISAMCursorActual.kt - ByteBuffer operations
 * - IPC:
 *   - trikeshed-ipc/IPCPerformance.kt - ByteBuffer benchmarks
 * 
 * CONSUMERS (use pyramids):
 * - JSON parsers:
 *   - trikeshed-lib/json/JsonBbcursive.kt - ByteIndexed parsing
 *   - trikeshed-ljson/JsonBBCursive.kt - ByteIndexed/CharIndexed
 *   - trikeshed-ljson/JsonStreaming.kt - bbcursive patterns
 * - Protocol parsers:
 *   - trikeshed-net/ssh/SSHParser.kt - ByteIndexedBuffer readers
 *   - trikeshed-reactor/socks/Socks5*.kt - ByteIndexed operations
 *   - trikeshed-reactor/http/HttpStateMachine.kt - ByteBuffer
 *   - trikeshed-reactor/quic/QuicServer.kt - ByteBuffer.allocate
 * - Torrent:
 *   - trikeshed-torrent/TorrentKettle.kt - ByteIndexed bencode
 * - Entity scanners:
 *   - kotlin-entity-scanner/TokenStairway.kt - CharIndexed
 *   - kotlin-entity-scanner/KotlinDietScanner.kt - CharIndexed
 * - BBCursive combinators:
 *   - CCEKBbcursive*.kt - bbcursive protocols
 *   - NarseseBbcursive.kt - parser combinators
 * 
 * TRANSFORMERS (pyramid -> pyramid):
 * - slice() operations
 * - trim/rtrim operations
 * - seekTo operations
 * - encoding/decoding transforms
 * - k2script wagon/launcher - CharIndexed/ByteIndexed
 */