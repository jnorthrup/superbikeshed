package borg.trikeshed.num

import java.math.BigDecimal as JavaBigDecimal
import java.math.RoundingMode as JavaRoundingMode // Already available via helper

actual class BigDecimal actual constructor() {
    private lateinit var value: JavaBigDecimal

    private constructor(value: JavaBigDecimal) : this() {
        this.value = value
    }

    actual companion object {
        actual fun parseString(value: String): BigDecimal = BigDecimal(JavaBigDecimal(value))
        actual fun fromDouble(value: Double): BigDecimal = BigDecimal(JavaBigDecimal.valueOf(value))
        actual fun fromLong(value: Long): BigDecimal = BigDecimal(JavaBigDecimal.valueOf(value))
        actual fun fromInt(value: Int): BigDecimal = BigDecimal(JavaBigDecimal.valueOf(value.toLong()))
        actual fun fromBigInt(value: BigInt): BigDecimal = BigDecimal(JavaBigDecimal(value.toString())) // Convert BigInt to string then to JavaBigDecimal
        actual val ZERO: BigDecimal by lazy { BigDecimal(JavaBigDecimal.ZERO) }
        actual val ONE: BigDecimal by lazy { BigDecimal(JavaBigDecimal.ONE) }
        actual val TEN: BigDecimal by lazy { BigDecimal(JavaBigDecimal.TEN) }
    }

    actual operator fun plus(other: BigDecimal): BigDecimal = BigDecimal(this.value.add(other.value))
    actual operator fun minus(other: BigDecimal): BigDecimal = BigDecimal(this.value.subtract(other.value))
    actual operator fun times(other: BigDecimal): BigDecimal = BigDecimal(this.value.multiply(other.value))

    actual fun div(other: BigDecimal, scale: Int, roundingMode: RoundingMode): BigDecimal {
        return BigDecimal(this.value.divide(other.value, scale, roundingMode.toJavaRoundingMode()))
    }

    actual fun div(other: BigDecimal): BigDecimal {
        // Default division in Java BigDecimal can throw ArithmeticException if no exact result.
        // This behavior might need refinement based on desired default scale/rounding for the common API.
        // For now, let's assume it might require context or throw.
        // A common approach is to define a default scale and rounding mode at a higher level or require it.
        // Or, use a MathContext. For now, we stick to the expect signature.
        // This might mean we need to adjust the expect signature or provide a default context.
        // Let's try a common behavior:
        try {
            return BigDecimal(this.value.divide(other.value))
        } catch (e: ArithmeticException) {
            // If exact division is not possible, try with a default high precision and rounding.
            // This is a common strategy if no context is given.
            val defaultScale = maxOf(this.scale(), other.scale(), 18) // Ensure a reasonable default scale
            return BigDecimal(this.value.divide(other.value, defaultScale, JavaRoundingMode.HALF_UP))
        }
    }

    actual fun pow(exponent: Int): BigDecimal = BigDecimal(this.value.pow(exponent))
    actual fun abs(): BigDecimal = BigDecimal(this.value.abs())
    actual operator fun unaryMinus(): BigDecimal = BigDecimal(this.value.negate())

    actual fun scale(): Int = this.value.scale()
    actual fun precision(): Int = this.value.precision()

    actual fun setScale(newScale: Int, roundingMode: RoundingMode): BigDecimal {
        return BigDecimal(this.value.setScale(newScale, roundingMode.toJavaRoundingMode()))
    }

    actual operator fun compareTo(other: BigDecimal): Int = this.value.compareTo(other.value)

    actual override fun toString(): String = this.value.toString()
    actual fun toPlainString(): String = this.value.toPlainString()
    actual fun toDouble(): Double = this.value.toDouble()
    actual fun toBigInt(): BigInt = BigInt.parseString(this.value.toBigInteger().toString())
    actual fun toInt(): Int = this.value.intValueExact() // Or intValue()
    actual fun toLong(): Long = this.value.longValueExact() // Or longValue()

    override fun hashCode(): Int = value.hashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BigDecimal) return false
        // Java BigDecimal's equals also compares scale, which might not be desired for numerical equality.
        // compareTo is generally better for numerical equality.
        return this.value.compareTo(other.value) == 0 && this.scale() == other.scale() // Or just compareTo if scale shouldn't matter for equals.
    }
}
