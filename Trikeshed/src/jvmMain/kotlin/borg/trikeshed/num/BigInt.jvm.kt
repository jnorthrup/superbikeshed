package borg.trikeshed.num

import java.math.BigInteger as JavaBigInteger

actual class BigInt actual constructor() {
    // Internal actual constructor and property to hold the Java BigInteger
    private lateinit var value: JavaBigInteger

    // Private constructor for internal use by companion object methods
    private constructor(value: JavaBigInteger) : this() {
        this.value = value
    }

    actual companion object {
        actual fun parseString(value: String, radix: Int): BigInt = BigInt(JavaBigInteger(value, radix))
        actual fun fromLong(value: Long): BigInt = BigInt(JavaBigInteger.valueOf(value))
        actual fun fromInt(value: Int): BigInt = BigInt(JavaBigInteger.valueOf(value.toLong()))
        actual val ZERO: BigInt by lazy { BigInt(JavaBigInteger.ZERO) }
        actual val ONE: BigInt by lazy { BigInt(JavaBigInteger.ONE) }
        actual val TEN: BigInt by lazy { BigInt(JavaBigInteger.TEN) }
    }

    actual operator fun plus(other: BigInt): BigInt = BigInt(this.value.add(other.value))
    actual operator fun minus(other: BigInt): BigInt = BigInt(this.value.subtract(other.value))
    actual operator fun times(other: BigInt): BigInt = BigInt(this.value.multiply(other.value))
    actual operator fun div(other: BigInt): BigInt = BigInt(this.value.divide(other.value))
    actual operator fun rem(other: BigInt): BigInt = BigInt(this.value.remainder(other.value))
    actual fun pow(exponent: Int): BigInt = BigInt(this.value.pow(exponent))
    actual fun abs(): BigInt = BigInt(this.value.abs())
    actual operator fun unaryMinus(): BigInt = BigInt(this.value.negate())

    actual operator fun compareTo(other: BigInt): Int = this.value.compareTo(other.value)

    actual override fun toString(): String = this.value.toString()
    actual fun toString(radix: Int): String = this.value.toString(radix)
    actual fun toLong(): Long = this.value.longValueExact() // Or longValue() if exactness can throw unwanted exceptions
    actual fun toInt(): Int = this.value.intValueExact()   // Or intValue()
    actual fun toDouble(): Double = this.value.toDouble()

    // hashCode and equals are important for use in collections, etc.
    override fun hashCode(): Int = value.hashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BigInt) return false
        return this.value == other.value
    }
}
