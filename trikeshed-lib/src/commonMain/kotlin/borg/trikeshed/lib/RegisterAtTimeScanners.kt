package borg.trikeshed.lib

import kotlin.jvm.JvmInline

/**
 * Register-at-a-Time Scanners with Autovec Optimization
 * 
 * Core concepts distilled from the conversation:
 * 1. Register packing for pair data (Join expansion) - much cheaper than suspension
 * 2. Autovec strategy selection - automatically chooses optimal scanning strategy
 * 3. Zero-cost abstractions - no allocation overhead
 */

// === CORE TYPES ===

/**
 * Zero-cost register-packed join for primitive combinations
 */
@JvmInline
value class RegisterJoin<A, B>(val word: Long) {
    fun unpackA(packer: Packable<A>): A = packer.unpack(word)
    fun unpackB(packerA: Packable<A>, packerB: Packable<B>): B = 
        packerB.unpack(word shr packerA.bitWidth)
}

/**
 * Packable interface for primitive types
 */
interface Packable<T> {
    val bitWidth: Int
    fun pack(value: T): Long
    fun unpack(bits: Long): T
}

/**
 * Primitive packers
 */
object PInt : Packable<Int> {
    override val bitWidth = 32
    override fun pack(value: Int): Long = value.toLong() and 0xFFFFFFFF
    override fun unpack(bits: Long): Int = bits.toInt()
}

object PBoolean : Packable<Boolean> {
    override val bitWidth = 1
    override fun pack(value: Boolean): Long = if (value) 1L else 0L
    override fun unpack(bits: Long): Boolean = bits != 0L
}

object PByte : Packable<Byte> {
    override val bitWidth = 8
    override fun pack(value: Byte): Long = value.toLong() and 0xFF
    override fun unpack(bits: Long): Byte = bits.toByte()
}

/**
 * Type evidence tracking
 */
enum class TypeEvidence {
    BEFORE, AFTER;
    
    fun process(): TypeEvidence = AFTER
}

/**
 * Scan strategies
 */
enum class ScanStrategy {
    SCALAR, SIMD, VECTOR, AUTOVEC
}

// === JOIN OPERATOR ===

/**
 * Join operator for register packing
 */
inline infix fun Int.j(b: Boolean): RegisterJoin<Int, Boolean> {
    val bitsL = PInt.pack(this)
    val bitsR = PBoolean.pack(b)
    return RegisterJoin(bitsL or (bitsR shl 32))
}

// JVM-specific - moved to jvmMain
// inline infix fun String.j(b: Class<*>): RegisterJoin<String, Class<*>> {
//     val leftHash = this.hashCode().toLong()
//     val rightHash = b.hashCode().toLong()
//     return RegisterJoin((leftHash shl 32) or (rightHash and 0xFFFFFFFF))
// }

inline infix fun String.j(b: Long): RegisterJoin<String, Long> {
    val leftHash = this.hashCode().toLong()
    return RegisterJoin((leftHash shl 32) or (b and 0xFFFFFFFF))
}

inline infix fun Byte.j(b: Boolean): RegisterJoin<Byte, Boolean> {
    val bitsL = PByte.pack(this)
    val bitsR = PBoolean.pack(b)
    return RegisterJoin(bitsL or (bitsR shl 8))
}

inline infix fun Byte.j(b: Int): RegisterJoin<Byte, Int> {
    val bitsL = PByte.pack(this)
    val bitsR = PInt.pack(b)
    return RegisterJoin(bitsL or (bitsR shl 8))
}

// === JSON ELEMENT (SIMPLIFIED) ===

data class JsonElement(internal val data: Map<String, Any>) {
    fun getString(key: String): String = data[key] as String
    fun getInt(key: String): Int = data[key] as Int
} 