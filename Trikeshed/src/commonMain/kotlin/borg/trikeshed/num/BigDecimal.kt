package borg.trikeshed.num

expect class BigDecimal private constructor() {
    // Constructors (or factory methods)
    companion object {
        fun parseString(value: String): BigDecimal
        fun fromDouble(value: Double): BigDecimal // Be cautious with precision
        fun fromLong(value: Long): BigDecimal
        fun fromInt(value: Int): BigDecimal
        fun fromBigInt(value: BigInt): BigDecimal
        val ZERO: BigDecimal
        val ONE: BigDecimal
        val TEN: BigDecimal
    }

    // Arithmetic operations
    operator fun plus(other: BigDecimal): BigDecimal
    operator fun minus(other: BigDecimal): BigDecimal
    operator fun times(other: BigDecimal): BigDecimal
    fun div(other: BigDecimal, scale: Int, roundingMode: RoundingMode): BigDecimal // Division requires scale and rounding
    fun div(other: BigDecimal): BigDecimal // Default division, might be problematic without context

    fun pow(exponent: Int): BigDecimal
    fun abs(): BigDecimal
    operator fun unaryMinus(): BigDecimal

    // Scale and Precision
    fun scale(): Int
    fun precision(): Int
    fun setScale(newScale: Int, roundingMode: RoundingMode): BigDecimal

    // Comparison
    operator fun compareTo(other: BigDecimal): Int

    // Conversion
    override fun toString(): String // May return scientific notation
    fun toPlainString(): String    // Should not use scientific notation
    fun toDouble(): Double
    fun toBigInt(): BigInt
    fun toInt(): Int
    fun toLong(): Long
}
