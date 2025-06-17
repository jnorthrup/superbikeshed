package borg.trikeshed.num

expect class BigInt private constructor() {
    // Constructors (or factory methods if preferred for expect classes)
    companion object {
        fun parseString(value: String, radix: Int = 10): BigInt
        fun fromLong(value: Long): BigInt
        fun fromInt(value: Int): BigInt
        val ZERO: BigInt
        val ONE: BigInt
        val TEN: BigInt
    }

    // Arithmetic operations
    operator fun plus(other: BigInt): BigInt
    operator fun minus(other: BigInt): BigInt
    operator fun times(other: BigInt): BigInt
    operator fun div(other: BigInt): BigInt
    operator fun rem(other: BigInt): BigInt
    fun pow(exponent: Int): BigInt
    fun abs(): BigInt
    operator fun unaryMinus(): BigInt

    // Comparison
    operator fun compareTo(other: BigInt): Int

    // Conversion
    override fun toString(): String
    fun toString(radix: Int): String
    fun toLong(): Long
    fun toInt(): Int
    fun toDouble(): Double
    // Potentially toByteArray / fromByteArray if needed later

    // Bitwise operations (optional for initial, can be added if needed)
    // fun and(other: BigInt): BigInt
    // fun or(other: BigInt): BigInt
    // fun xor(other: BigInt): BigInt
    // fun not(): BigInt
    // fun shl(n: Int): BigInt
    // fun shr(n: Int): BigInt
}
