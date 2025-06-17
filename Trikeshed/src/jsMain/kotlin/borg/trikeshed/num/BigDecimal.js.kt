package borg.trikeshed.num

// WARNING: This is a very simplified BigDecimal for JS.
// It primarily uses Double for arithmetic, so it does NOT offer arbitrary precision of Java's BigDecimal.
// For true arbitrary precision decimal arithmetic in JS, a dedicated JS library would typically be used.
// This implementation is a placeholder to allow common code to compile and run,
// but with known limitations on precision and scale for the JS target.

actual class BigDecimal actual constructor() {
    // Store value as Double for simplicity in this basic JS version.
    // This means it's subject to standard double precision limitations.
    private var doubleValue: Double = 0.0

    private constructor(value: Double) : this() {
        this.doubleValue = value
    }
    private constructor(valueStr: String) : this() {
        this.doubleValue = valueStr.toDoubleOrNull() ?: throw NumberFormatException("Invalid BigDecimal string: $valueStr")
    }

    actual companion object {
        actual fun parseString(value: String): BigDecimal = BigDecimal(value)
        actual fun fromDouble(value: Double): BigDecimal = BigDecimal(value)
        actual fun fromLong(value: Long): BigDecimal = BigDecimal(value.toDouble())
        actual fun fromInt(value: Int): BigDecimal = BigDecimal(value.toDouble())
        actual fun fromBigInt(value: BigInt): BigDecimal = BigDecimal(value.toDouble()) // Loses precision

        actual val ZERO: BigDecimal by lazy { BigDecimal(0.0) }
        actual val ONE: BigDecimal by lazy { BigDecimal(1.0) }
        actual val TEN: BigDecimal by lazy { BigDecimal(10.0) }
    }

    actual operator fun plus(other: BigDecimal): BigDecimal = BigDecimal(this.doubleValue + other.doubleValue)
    actual operator fun minus(other: BigDecimal): BigDecimal = BigDecimal(this.doubleValue - other.doubleValue)
    actual operator fun times(other: BigDecimal): BigDecimal = BigDecimal(this.doubleValue * other.doubleValue)

    actual fun div(other: BigDecimal, scale: Int, roundingMode: RoundingMode): BigDecimal {
        // Simplified: Perform double division. Scale and rounding mode are largely ignored in this basic version.
        // Proper implementation would require complex logic.
        if (other.doubleValue == 0.0) throw ArithmeticException("Division by zero")
        var result = this.doubleValue / other.doubleValue
        // Basic rounding based on scale (very approximate)
        val multiplier = kotlin.math.pow(10.0, scale.toDouble())
        result = when (roundingMode) {
            RoundingMode.UP -> if (result > 0) kotlin.math.ceil(result * multiplier) / multiplier else kotlin.math.floor(result*multiplier)/multiplier
            RoundingMode.DOWN -> if (result > 0) kotlin.math.floor(result * multiplier) / multiplier else kotlin.math.ceil(result*multiplier)/multiplier
            RoundingMode.CEILING -> kotlin.math.ceil(result * multiplier) / multiplier
            RoundingMode.FLOOR -> kotlin.math.floor(result * multiplier) / multiplier
            RoundingMode.HALF_UP -> kotlin.math.round(result * multiplier) / multiplier // Simplified half_up
            RoundingMode.HALF_DOWN -> kotlin.math.floor(result * multiplier + 0.5) / multiplier // Simplified, not perfect
            RoundingMode.HALF_EVEN -> { // Very simplified Bankers' rounding
                val r = kotlin.math.round(result*multiplier)
                if (kotlin.math.abs(result*multiplier - r) == 0.5 && r % 2.0 != 0.0) {
                    if (result*multiplier > 0) (r-1)/multiplier else (r+1)/multiplier
                } else r/multiplier
            }
        }
        return BigDecimal(result)
    }

    actual fun div(other: BigDecimal): BigDecimal {
        if (other.doubleValue == 0.0) throw ArithmeticException("Division by zero")
        // No specific scale/rounding, direct double division.
        return BigDecimal(this.doubleValue / other.doubleValue)
    }

    actual fun pow(exponent: Int): BigDecimal = BigDecimal(kotlin.math.pow(this.doubleValue, exponent.toDouble()))
    actual fun abs(): BigDecimal = BigDecimal(kotlin.math.abs(this.doubleValue))
    actual operator fun unaryMinus(): BigDecimal = BigDecimal(-this.doubleValue)

    actual fun scale(): Int {
        // Simplistic scale for JS based on string representation after decimal point.
        // This is not robust like Java's BigDecimal scale.
        val s = this.doubleValue.toString()
        val dotIndex = s.indexOf('.')
        return if (dotIndex < 0) 0 else s.length - dotIndex - 1
    }

    actual fun precision(): Int {
        // Simplistic precision for JS: total number of digits in string representation.
        // Not robust.
         val s = this.doubleValue.toString().replace(".", "").replace("-","")
         return s.length
    }

    actual fun setScale(newScale: Int, roundingMode: RoundingMode): BigDecimal {
        // Simplified: uses the div logic for rounding.
        val one = BigDecimal(1.0)
        return this.div(one, newScale, roundingMode)
    }

    actual operator fun compareTo(other: BigDecimal): Int = this.doubleValue.compareTo(other.doubleValue)

    actual override fun toString(): String {
        // JS doesn't have a direct equivalent to Java BigDecimal's default toString (scientific if needed).
        // This will just be the default double to string.
        return doubleValue.toString()
    }
    actual fun toPlainString(): String {
        // Attempt to mimic plain string, but limited by double's nature
        return doubleValue.toString() // JS default toString for numbers is usually plain.
    }
    actual fun toDouble(): Double = this.doubleValue
    actual fun toBigInt(): BigInt = BigInt.fromLong(this.doubleValue.toLong()) // Loses decimal part & precision
    actual fun toInt(): Int = this.doubleValue.toInt()
    actual fun toLong(): Long = this.doubleValue.toLong()

    override fun hashCode(): Int = doubleValue.hashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        // For this simplified JS version, direct double comparison is used.
        // This means 0.1 + 0.2 != 0.3 might be true.
        if (other !is BigDecimal) return false
        return this.doubleValue == other.doubleValue
    }
}
