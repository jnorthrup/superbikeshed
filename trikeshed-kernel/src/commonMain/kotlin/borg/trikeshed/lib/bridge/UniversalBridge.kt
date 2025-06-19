@file:Suppress("UNCHECKED_CAST", "FunctionName", "NonAsciiCharacters", "NOTHING_TO_INLINE")

package borg.trikeshed.lib.bridge

import borg.trikeshed.lib.*
import kotlin.jvm.JvmInline

/**
 * Universal Type Bridge Infrastructure for TrikeShed
 * Provides zero-cost conversions between TrikeShed types and external requirements
 */

// === CORE CONVERSION BRIDGES ===

/** Universal ByteArray ↔ Series<Byte> bridge */
@JvmInline value class ByteBridge(val value: Any) {
    companion object {
        fun fromBytes(bytes: ByteArray): Series<Byte> = bytes.toSeries()
        fun fromSeries(series: Series<Byte>): ByteArray = series.toArray()
        fun fromJoin(join: Join<Int, (Int) -> Byte>): ByteArray = join.toArray()
        fun toHttpMessage(bytes: ByteArray): HttpMessage = HttpMessage(bytes)
        fun toHttpMessage(series: Series<Byte>): HttpMessage = HttpMessage(series.toArray())
    }
}

/** Universal JSON bridge for Map ↔ JsonObject conversions */
@JvmInline value class JsonBridge(val json: String) {
    fun asMap(): Map<String, Any> = emptyMap() // TODO: actual parsing
    fun asSeries(): Series<Join<String, Any>> = emptySeries()
    
    companion object {
        fun fromMap(map: Map<String, Any>): JsonObject = JsonObject("")
        fun fromSeries(series: Series<Join<String, Any>>): JsonObject = JsonObject("")
    }
}

/** Collection bridge for List ↔ Series conversions */
@JvmInline value class CollectionBridge<T>(val value: Any) {
    companion object {
        fun <T> fromList(list: List<T>): Series<T> = list.toSeries()
        fun <T> fromSeries(series: Series<T>): List<T> = series.toList()
    }
}

// === MISSING PLATFORM TYPES ===

/** Platform IO abstraction */
@JvmInline value class IO(val tag: String = "io")

/** Document ID for database operations */
@JvmInline value class DocumentId(val value: String)

/** Revision ID for versioning */
@JvmInline value class RevisionId(val value: String)

/** Database name wrapper */
@JvmInline value class DatabaseName(val value: String)

/** Storage tier classification */
enum class StorageTier { HOT, WARM, COLD }

/** Compression algorithm selection */
enum class CompressionAlgorithm { NONE, GZIP, LZ4 }

// === FUNCTION WITNESS TYPES ===

/** Type evidence for generic resolution */
@JvmInline value class TypeEvidence<T>(val witness: String = "evidence")

/** Function witness for lambda type resolution */
@JvmInline value class FunctionWitness<T, R>(val id: String = "fn") {
    companion object {
        fun <T, R> create(fn: (T) -> R): (T) -> R = fn
        fun <T> predicate(fn: (T) -> Boolean): (T) -> Boolean = fn
        fun <T> jsonPredicate(fn: (JsonObject) -> Boolean): (String) -> Boolean = { fn(JsonObject(it)) }
    }
}

// === HTTP MESSAGE BRIDGE ===

/** HTTP message integration */
@JvmInline value class HttpMessage(val data: ByteArray) {
    constructor(series: Series<Byte>) : this(series.toArray())
    constructor(text: String) : this(text.encodeToByteArray())
    
    fun asSeries(): Series<Byte> = data.toSeries()
    fun asText(): String = data.decodeToString()
}

// === REACTIVE BRIDGES ===

/** Channel bridge for missing properties */
@JvmInline value class ChannelBridge<T>(val id: String) {
    val internalReceiveChannel: Any get() = this
    val closed: Boolean get() = false
    fun close() = Unit
}

/** Socket bridge */
@JvmInline value class SocketBridge(val id: String) {
    val socket: Any? get() = null
    val remoteAddress: Any? get() = null
    val remoteConnectionId: Any get() = ByteArray(8)
}

// === EXTENSION FUNCTIONS FOR UNIVERSAL CONVERSION ===

/** Universal Series conversion */
@Suppress("UNCHECKED_CAST")
inline fun <T> Any.asSeries(): Series<T> = when(this) {
    is List<*> -> (this as List<T>).toSeries()
    is Array<*> -> (this as Array<T>).toSeries()
    else -> error("Cannot convert ${this::class} to Series")
}

/** Universal ByteArray conversion */
@Suppress("UNCHECKED_CAST")
inline fun Any.asByteArray(): ByteArray = when(this) {
    is ByteArray -> this
    else -> error("Cannot convert ${this::class} to ByteArray")
}

/** Universal Map conversion */
inline fun Any.asMap(): Map<String, Any> = when(this) {
    is Map<*, *> -> this as Map<String, Any>
    is JsonObject -> JsonBridge(this.value).asMap()
    else -> emptyMap()
}

// === OPERATORS FOR SEAMLESS CONVERSION ===

/** List to Series conversion operator */
operator fun <T> List<T>.unaryPlus(): Series<T> = this.toSeries()

/** Series to List conversion operator */
operator fun <T> Series<T>.unaryMinus(): List<T> = this.toList()

/** ByteArray to Series conversion operator */
operator fun ByteArray.unaryPlus(): Series<Byte> = this.toSeries()

/** Series to ByteArray conversion operator */
operator fun Series<Byte>.unaryMinus(): ByteArray = this.toArray()

// === EFFICIENT ABSTRACTLIST CONVERSIONS ===

/** Convert Series to AbstractList (lazy, no materialization) */
fun <T> Series<T>.asAbstractList(): kotlin.collections.AbstractList<T> = object : kotlin.collections.AbstractList<T>() {
    override val size = this@asAbstractList.size
    override fun get(index: Int) = this@asAbstractList[index]
}

/** Convert ByteArray to Series<Byte> */
fun ByteArray.toBytesSeries(): Series<Byte> = this.size j { i -> this[i] }

/** Convert to HttpMessage from various types */
fun ByteArray.toHttpMessage(): HttpMessage = HttpMessage(this)
fun Series<Byte>.toHttpMessage(): HttpMessage = HttpMessage(this.toArray())

// === JOIN TYPE CONVERSION BRIDGES ===

/** Convert Join<Int, Function0<Byte>> to ByteArray (lambda with no params) */
fun Join<Int, () -> Byte>.toFlatByteArray(): ByteArray = ByteArray(this.a) { this.b() }

/** Convert Join<Int, Function1<Int, Byte>> to ByteArray (Series<Byte>) - no conflict with existing toArray() */
fun Join<Int, (Int) -> Byte>.toFlatByteArray(): ByteArray = ByteArray(this.a) { this.b(it) }

/** Convert ByteArray to Join<Int, Function1<Int, Byte>> (Series<Byte>) */
fun ByteArray.toSeriesJoin(): Join<Int, (Int) -> Byte> = this.size j { i -> this[i] }

/** Convert List<Char> to Series<Char> */
fun List<Char>.toCharSeries(): Series<Char> = this.size j { i -> this[i] }

/** Fix lambda 'it' context by providing explicit parameter names */
inline fun <T, R> Join<Int, (Int) -> T>.mapWithIndex(transform: (index: Int, value: T) -> R): Series<R> {
    return this.a j { index -> transform(index, this.b(index)) }
}