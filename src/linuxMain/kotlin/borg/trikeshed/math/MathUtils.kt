package borg.trikeshed.math

actual object MathUtils {
    actual fun createBigDecimal(value: Double): BigDecimal = BigDecimal(value.toString())
    actual fun createBigDecimal(value: String): BigDecimal = BigDecimal(value)
    actual fun createBigDecimal(value: Int): BigDecimal = BigDecimal(value.toString())
    actual fun createBigDecimal(value: Long): BigDecimal = BigDecimal(value.toString())

    actual fun BigDecimal.scale(): Int = this.scale
    actual fun BigDecimal.setScale(scale: Int, roundingMode: RoundingMode): BigDecimal = 
        BigDecimal(this.value, scale, roundingMode)

    actual fun BigDecimal.add(other: BigDecimal): BigDecimal = 
        BigDecimal((this.value.toDouble() + other.value.toDouble()).toString())
    actual fun BigDecimal.subtract(other: BigDecimal): BigDecimal = 
        BigDecimal((this.value.toDouble() - other.value.toDouble()).toString())
    actual fun BigDecimal.multiply(other: BigDecimal): BigDecimal = 
        BigDecimal((this.value.toDouble() * other.value.toDouble()).toString())
    actual fun BigDecimal.divide(other: BigDecimal, scale: Int, roundingMode: RoundingMode): BigDecimal = 
        BigDecimal((this.value.toDouble() / other.value.toDouble()).toString(), scale, roundingMode)

    actual fun BigDecimal.pow(n: Int): BigDecimal = 
        BigDecimal(kotlin.math.pow(this.value.toDouble(), n.toDouble()).toString())
    actual fun BigDecimal.sqrt(scale: Int, roundingMode: RoundingMode): BigDecimal = 
        BigDecimal(kotlin.math.sqrt(this.value.toDouble()).toString(), scale, roundingMode)

    actual fun BigDecimal.compareTo(other: BigDecimal): Int = 
        this.value.toDouble().compareTo(other.value.toDouble())

    actual val BigDecimal.isZero: Boolean get() = this.value.toDouble() == 0.0
    actual val BigDecimal.isPositive: Boolean get() = this.value.toDouble() > 0.0
    actual val BigDecimal.isNegative: Boolean get() = this.value.toDouble() < 0.0

    actual fun BigDecimal.abs(): BigDecimal = 
        BigDecimal(kotlin.math.abs(this.value.toDouble()).toString())
    actual fun BigDecimal.negate(): BigDecimal = 
        BigDecimal((-this.value.toDouble()).toString())

    actual fun min(a: BigDecimal, b: BigDecimal): BigDecimal = 
        if (a.value.toDouble() <= b.value.toDouble()) a else b
    actual fun max(a: BigDecimal, b: BigDecimal): BigDecimal = 
        if (a.value.toDouble() >= b.value.toDouble()) a else b

    actual fun BigDecimal.toLong(): Long = this.value.toDouble().toLong()

    actual val ZERO: BigDecimal = BigDecimal("0")
    actual val ONE: BigDecimal = BigDecimal("1")
    actual val TWO: BigDecimal = BigDecimal("2")
    actual val TEN: BigDecimal = BigDecimal("10")
    actual val HUNDRED: BigDecimal = BigDecimal("100")
}

actual enum class RoundingMode {
    UP,
    DOWN,
    CEILING,
    FLOOR,
    HALF_UP,
    HALF_DOWN,
    HALF_EVEN,
    UNNECESSARY
}

actual class BigDecimal(val value: String, val scale: Int = 0, val roundingMode: RoundingMode = RoundingMode.HALF_UP) {
    constructor(value: String) : this(value, 0, RoundingMode.HALF_UP)

    override fun toString(): String = value
    override fun equals(other: Any?): Boolean = other is BigDecimal && value == other.value
    override fun hashCode(): Int = value.hashCode()
} 