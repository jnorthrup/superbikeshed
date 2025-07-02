@file:Suppress("UNCHECKED_CAST", "FunctionName", "NonAsciiCharacters", "NOTHING_TO_INLINE")

package borg.trikeshed.wireproto

import borg.trikeshed.lib.*
import kotlin.jvm.JvmInline

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
@JvmInline
value class RegisterJoin<A, B>(val word: Long) {
    
    fun unpackA(packer: Packable<A>): A = packer.unpack(word)
    
    fun unpackB(packerA: Packable<A>, packerB: Packable<B>): B = 
        packerB.unpack(word shr packerA.bitWidth)
    
    // Convenience accessors for common combinations
    inline val a: A get() = unpackA(PInt as Packable<A>)
    inline val b: B get() = unpackB(PInt as Packable<A>, PBoolean as Packable<B>)
}

// === MANUAL JOIN OVERLOADS (KSP would generate these) ===

/**
 * Optimized j overloads for primitive combinations that fit in 64-bit register
 */

// Int + Boolean (32 + 1 = 33 bits) ✓
inline infix fun Int.j(b: Boolean): RegisterJoin<Int, Boolean> {
    val bitsL = PInt.pack(this)
    val bitsR = PBoolean.pack(b)
    return RegisterJoin(bitsL or (bitsR shl 32))
}

// Int + Byte (32 + 8 = 40 bits) ✓
inline infix fun Int.j(b: Byte): RegisterJoin<Int, Byte> {
    val bitsL = PInt.pack(this)
    val bitsR = PByte.pack(b)
    return RegisterJoin(bitsL or (bitsR shl 32))
}

// Int + Short (32 + 16 = 48 bits) ✓
inline infix fun Int.j(b: Short): RegisterJoin<Int, Short> {
    val bitsL = PInt.pack(this)
    val bitsR = PShort.pack(b)
    return RegisterJoin(bitsL or (bitsR shl 32))
}

// Int + Int (32 + 32 = 64 bits) ✓
inline infix fun Int.j(b: Int): RegisterJoin<Int, Int> {
    val bitsL = PInt.pack(this)
    val bitsR = PInt.pack(b)
    return RegisterJoin(bitsL or (bitsR shl 32))
}

// Boolean + Boolean (1 + 1 = 2 bits) ✓
inline infix fun Boolean.j(b: Boolean): RegisterJoin<Boolean, Boolean> {
    val bitsL = PBoolean.pack(this)
    val bitsR = PBoolean.pack(b)
    return RegisterJoin(bitsL or (bitsR shl 1))
}

// Boolean + Int (1 + 32 = 33 bits) ✓
inline infix fun Boolean.j(b: Int): RegisterJoin<Boolean, Int> {
    val bitsL = PBoolean.pack(this)
    val bitsR = PInt.pack(b)
    return RegisterJoin(bitsL or (bitsR shl 1))
}

// Short + Short (16 + 16 = 32 bits) ✓
inline infix fun Short.j(b: Short): RegisterJoin<Short, Short> {
    val bitsL = PShort.pack(this)
    val bitsR = PShort.pack(b)
    return RegisterJoin(bitsL or (bitsR shl 16))
}

// Byte + Byte (8 + 8 = 16 bits) ✓
inline infix fun Byte.j(b: Byte): RegisterJoin<Byte, Byte> {
    val bitsL = PByte.pack(this)
    val bitsR = PByte.pack(b)
    return RegisterJoin(bitsL or (bitsR shl 8))
}

// Float + Float (32 + 32 = 64 bits) ✓
inline infix fun Float.j(b: Float): RegisterJoin<Float, Float> {
    val bitsL = PFloat.pack(this)
    val bitsR = PFloat.pack(b)
    return RegisterJoin(bitsL or (bitsR shl 32))
}

// NOTE: Long + anything exceeds 64 bits, would need multiple registers or fallback

// === DEMO USAGE ===

/**
 * Demonstrates the zero-branch register-packed joins
 */
fun demoRegisterJoins() {
    // Zero-cost primitive packing
    val intBool = 42.j(true)
    val boolInt = false.j(100)
    val shortShort = 1000.toShort().j(2000.toShort())
    
    // Efficient unpacking
    val a = intBool.unpackA(PInt)
    val b = intBool.unpackB(PInt, PBoolean)
    
    println("Packed 42,true -> word: ${intBool.word}, unpacked: $a,$b")
}

// === WIRE PROTOCOL INTEGRATION ===

/**
 * Wire protocol serializer - softened to lambda-based definitions
 */
fun <A, B> RegisterJoin<A, B>.toWireBytes(): UByteArray {
    // Lambda-based serialization for wire protocol flexibility
    val serializer: (RegisterJoin<A, B>) -> UByteArray = { join ->
        val buffer = UByteArray(8)
        var w = join.word
        for (i in 0..7) {
            buffer[i] = (w and 0xFF).toUByte()
            w = w shr 8
        }
        buffer
    }
    return serializer(this)
}

/**
 * Wire protocol deserializer - softened to lambda-based definitions
 */
fun UByteArray.toRegisterJoin(): RegisterJoin<*, *> {
    // Lambda-based deserialization for wire protocol flexibility
    val deserializer: (UByteArray) -> RegisterJoin<*, *> = { bytes ->
        require(bytes.size >= 8) { "RegisterJoin requires 8 bytes" }
        var word = 0L
        for (i in 7 downTo 0) {
            word = (word shl 8) or (bytes[i].toLong() and 0xFF)
        }
        RegisterJoin<Any, Any>(word)
    }
    return deserializer(this)
}

// === COMPATIBILITY OVERLOADS ===

/**
 * Minimal placeholder manual join overloads for compilation
 */
object ManualJoinOverloads {
    // These provide fallback for non-primitive types
    infix fun <A, B> A.j(other: B): Join<A, B> = object : Join<A, B> {
        override val a: A = this@j
        override val b: B = other
    }
}