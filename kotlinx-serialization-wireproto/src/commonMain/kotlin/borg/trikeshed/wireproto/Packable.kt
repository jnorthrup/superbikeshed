
package borg.trikeshed.wireproto

import borg.trikeshed.lib.Indexed
import kotlin.jvm.JvmInline

// Compatibility alias for tests that still use Indexed
typealias Indexed<T> = Indexed<T>

/**
 * Manual demonstration of what KSP would generate for optimized join packers
 * This serves as a proof-of-concept for register-packed primitive combinations
 */

// === PACKABLE INTERFACE ===

interface Packable<T> {
    val bitWidth: Int
    fun pack(value: T): Long
    fun unpack(bits: Long): T
}

// === PRIMITIVE PACKERS ===

object PInt : Packable<Int> {
    override val bitWidth = 32
    override fun pack(value: Int): Long = value.toLong() and 0xFFFFFFFF
    override fun unpack(bits: Long): Int = bits.toInt()
}

object PLong : Packable<Long> {
    override val bitWidth = 64
    override fun pack(value: Long): Long = value
    override fun unpack(bits: Long): Long = bits
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

object PShort : Packable<Short> {
    override val bitWidth = 16
    override fun pack(value: Short): Long = value.toLong() and 0xFFFF
    override fun unpack(bits: Long): Short = bits.toShort()
}

object PFloat : Packable<Float> {
    override val bitWidth = 32
    override fun pack(value: Float): Long = value.toBits().toLong() and 0xFFFFFFFF
    override fun unpack(bits: Long): Float = Float.fromBits(bits.toInt())
}

object PDouble : Packable<Double> {
    override val bitWidth = 64
    override fun pack(value: Double): Long = value.toBits()
    override fun unpack(bits: Long): Double = Double.fromBits(bits)
}

// === REGISTER JOIN ===

/**
 * Zero-cost register-packed join for primitive combinations
 */
value class RegisterJoin<A, B>(val word: Long) {
    
    fun unpackA(packer: Packable<A>): A = packer.unpack(word)
    
    fun unpackB(packerA: Packable<A>, packerB: Packable<B>): B = 
        packerB.unpack(word shr packerA.bitWidth)
    
    // Convenience accessors for common combinations
    inline val a: A get() = unpackA(PInt as Packable<A>)
    inline val b: B get() = unpackB(PInt as Packable<A>, PBoolean as Packable<B>)
}

// === WIRE SERIALIZATION ===

fun RegisterJoin<*, *>.toWireBytes(): ByteArray {
    val bytes = ByteArray(8)
    for (i in 0..7) {
        bytes[i] = ((word shr (i * 8)) and 0xFF).toByte()
    }
    return bytes
}

fun ByteArray.toRegisterJoin(): RegisterJoin<*, *> {
    require(size == 8) { "RegisterJoin requires exactly 8 bytes" }
    var word = 0L
    for (i in 0..7) {
        word = word or ((this[i].toLong() and 0xFF) shl (i * 8))
    }
    return RegisterJoin<Any?, Any?>(word)
}