@file:OptIn(ExperimentalUnsignedTypes::class)

package core

import borg.trikeshed.lib.Series
import borg.trikeshed.lib.get
import borg.trikeshed.lib.j
import borg.trikeshed.lib.size

/**
 * BigInt implementation from TrikeShed for Kademlia keys
 * 
 * Provides arbitrary precision arithmetic with XOR operations
 * needed for Kademlia distance calculations in the object mesh.
 */
class BigInt private constructor(private val sign: Boolean?, private val magnitude: Series<UInt>) : Number(),
    Comparable<BigInt> {

    // Constructor from Long
    constructor(value: Long) : this(
        sign = if (value.z) null else value > 0,
        magnitude = if (value.z) emptySeries() else {
            value.absoluteValue.let { absValue ->
                val low = (absValue and 0xFFFF_FFFFL).toUInt() // Lower 32 bits
                val high = ((absValue ushr 32) and 0xFFFF_FFFFL).toUInt() // Upper 32 bits
                if (high.nz) arrayOf(high, low) else arrayOf(low)
            }.toSeries()
        }
    )

    private constructor(
        value: ULong,
        /**This parameter is not used within the constructor but
         *  ensures that the method signatures for ULong and Long
         *  constructors do not clash when compiled in Java.*/
        javaCannotCompileULongAndLongMethodsThatConflict: String = ""
    ) : this(
        sign = if (value.z) null else true,
        magnitude = if (value.z) emptySeries() else value.let { absValue ->
            val low = (absValue and 0xFFFF_FFFFUL).toUInt()
            val high = ((absValue shr 32) and 0xFFFF_FFFFUL).toUInt()
            if (high.nz) arrayOf(high, low) else arrayOf(low)
        }.toSeries()
    )

    constructor(value: String) : this(
        sign = if (value.isEmpty()) null else value[0] != '-',
        magnitude = if (value.isEmpty()) emptySeries() else {
            val magnitude = mutableListOf<UInt>()
            var i = if (value[0] == '-') 1 else 0
            while (i < value.length) {
                var chunk = 0u
                for (j in 0..8) {
                    if (i < value.length) {
                        chunk = chunk * 10u + (value[i] - '0').toUInt()
                        i++
                    }
                }
                magnitude.add(chunk)
            }
            magnitude.toSeries()
        }
    )

    override fun compareTo(other: BigInt): Int {
        sign?.let {
            other.sign?.let { if (sign != other.sign) return if (sign) 1 else -1 } ?: return if (sign) 1 else -1
        } ?: other.sign?.let { return if (other.sign) -1 else 1 } ?: return 0

        val magnitudeComparison = magnitude.size.compareTo(other.magnitude.size)
        if (magnitudeComparison.nz) return magnitudeComparison

        var i = magnitude.a - 1
        while (i >= 0) {
            val magnitudeComparison = magnitude[i].compareTo(other.magnitude[i])
            if (magnitudeComparison.nz) return magnitudeComparison
            i--
        }

        return 0
    }

    /** inverts the bits (but not xor?).  Assumes that the sign of the result should be opposite of the current instance's sign */
    operator fun not(): BigInt = BigInt(sign?.let(Boolean::not), magnitude.a j { i -> magnitude.b(i).inv() })

    fun or(bigInt: BigInt): BigInt = BigInt(
        sign, kotlin.math.max(magnitude.size, bigInt.magnitude.size) j {
            (magnitude.getOrNull(it) ?: 0u) or (bigInt.magnitude.getOrNull(it) ?: 0u)
        }
    )

    fun and(bigInt: BigInt): BigInt = BigInt(
        sign, kotlin.math.max(magnitude.size, bigInt.magnitude.size) j {
            (magnitude.getOrNull(it) ?: 0u) and (bigInt.magnitude.getOrNull(it) ?: 0u)
        }
    )

    /**
     * XOR operation for Kademlia distance calculations
     */
    fun xor(bigInt: BigInt): BigInt = BigInt(
        sign, kotlin.math.max(magnitude.size, bigInt.magnitude.size) j {
            (magnitude.getOrNull(it) ?: 0u) xor (bigInt.magnitude.getOrNull(it) ?: 0u)
        }
    )

    override fun toString(): String {
        val signString = if (sign == null) "" else if (sign) "+" else "-"
        return signString + magnitude.reversed().materialize().joinToString("") { it.toString().padStart(9, '0') }
    }

    override fun toByte(): Byte = toInt().toByte()
    override fun toChar(): Char = toInt().toChar()
    override fun toDouble(): Double = toLong().toDouble()
    override fun toFloat(): Float = toLong().toFloat()
    override fun toInt(): Int = toLong().toInt()
    override fun toLong(): Long = when {
        sign == null -> 0L
        magnitude.size > 2 -> throw ArithmeticException("Overflow")
        else -> {
            val value = magnitude[0].toLong() or (magnitude.getOrNull(1)?.toLong()?.shl(32) ?: 0L)
            if (sign) value else -value
        }
    }
    override fun toShort(): Short = toInt().toShort()

    operator fun plus(addend: BigInt): BigInt = when {
        sign == null -> addend
        addend.sign == null -> this
        sign == addend.sign -> BigInt(sign, processMagnitudes(addend, true))
        else -> BigInt(sign, processMagnitudes(addend, false))
    }

    operator fun minus(subtrahend: BigInt): BigInt {
        val negatedSubtrahend = BigInt(subtrahend.sign?.not(), subtrahend.magnitude)
        return plus(negatedSubtrahend)
    }

    private fun processMagnitudes(addend: BigInt, addition: Boolean): Series<UInt> {
        val m1 = magnitude.reversed()
        val m2 = addend.magnitude.reversed()

        val result = mutableListOf<UInt>()
        var carry = 0uL
        val maxSize = kotlin.math.max(m1.size, m2.size)

        var i = 0
        while (i < maxSize) {
            val x = m1.getOrNull(i) ?: 0u
            val y = m2.getOrNull(i) ?: 0u
            val sum = if (addition) x + y + carry else x - y - carry
            result.add(sum.toUInt())
            carry = sum shr 32
            i++
        }

        if (carry != 0uL) result.add(carry.toUInt())
        return result.toUIntArray().toSeries()
    }

    companion object {
        val ZERO = BigInt(null, emptySeries())
        val ONE = BigInt(1)
        val TWO = BigInt(2UL)
    }
}

/**
 * Kademlia Distance Calculation using XOR
 */
fun kademliaDistance(a: BigInt, b: BigInt): BigInt = a.xor(b)

/**
 * Calculate XOR distance for routing
 */
fun xorDistance(key1: String, key2: String): BigInt {
    val bigInt1 = BigInt(key1)
    val bigInt2 = BigInt(key2)
    return kademliaDistance(bigInt1, bigInt2)
}

// CZero extensions for Peano-like arithmetic
val Byte.nz: Boolean get() = 0 != this.toInt()
val Short.nz: Boolean get() = 0 != this.toInt()
val Char.nz: Boolean get() = 0 != this.code
val Int.nz: Boolean get() = 0 != this
val Long.nz: Boolean get() = 0L != this
val UByte.nz: Boolean get() = 0 != this.toInt()
val UShort.nz: Boolean get() = 0 != this.toInt()
val UInt.nz: Boolean get() = 0U != this
val ULong.nz: Boolean get() = 0UL != this
val Byte.z: Boolean get() = 0 == this.toInt()
val Short.z: Boolean get() = 0 == this.toInt()
val Char.z: Boolean get() = 0 == this.code
val Int.z: Boolean get() = 0 == this
val Long.z: Boolean get() = 0L == this
val UByte.z: Boolean get() = 0 == this.toInt()
val UShort.z: Boolean get() = 0 == this.toInt()
val UInt.z: Boolean get() = 0U == this
val ULong.z: Boolean get() = 0UL == this
val Boolean.bool: Int get() = if (this) 1 else 0

private fun emptySeries(): Series<UInt> = 0 j { _ -> 0u }
private fun Array<UInt>.toSeries(): Series<UInt> = size j { i -> this[i] }
private fun List<UInt>.toSeries(): Series<UInt> = size j { i -> this[i] }
private fun UIntArray.toSeries(): Series<UInt> = size j { i -> this[i] }
private fun <T> Series<T>.reversed(): Series<T> = size j { i:Int -> this[size - 1 - i] }
private fun <T> Series<T>.materialize(): List<T> = buildList {
    var i = 0
    while (i < this@materialize.a) {
        add(this@materialize.b(i))
        i++
    }
}
private fun <T> Series<T>.getOrNull(index: Int): T? = if (index < a) this.b(index) else null
private val Long.absoluteValue: Long get() = kotlin.math.abs(this)