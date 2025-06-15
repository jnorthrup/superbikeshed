package borg.trikeshed.math

import java.math.BigDecimal as JavaBigDecimal
import java.math.RoundingMode as JavaRoundingMode
import java.math.MathContext
import java.math.BigInteger

actual object MathUtils {
    actual fun createBigDecimal(value: Double): BigDecimal = BigDecimal(value)
    actual fun createBigDecimal(value: String): BigDecimal = BigDecimal(value)
    actual fun createBigDecimal(value: Int): BigDecimal = BigDecimal(value)
    actual fun createBigDecimal(value: Long): BigDecimal = BigDecimal(value)

    actual fun BigDecimal.scale(): Int = (this as JavaBigDecimal).scale()
    actual fun BigDecimal.setScale(scale: Int, roundingMode: RoundingMode): BigDecimal = 
        BigDecimal((this as JavaBigDecimal).setScale(scale, roundingMode.toJavaRoundingMode()))

    actual fun BigDecimal.add(other: BigDecimal): BigDecimal = 
        BigDecimal((this as JavaBigDecimal).add(other as JavaBigDecimal))
    actual fun BigDecimal.subtract(other: BigDecimal): BigDecimal = 
        BigDecimal((this as JavaBigDecimal).subtract(other as JavaBigDecimal))
    actual fun BigDecimal.multiply(other: BigDecimal): BigDecimal = 
        BigDecimal((this as JavaBigDecimal).multiply(other as JavaBigDecimal))
    actual fun BigDecimal.divide(other: BigDecimal, scale: Int, roundingMode: RoundingMode): BigDecimal = 
        BigDecimal((this as JavaBigDecimal).divide(other as JavaBigDecimal, scale, roundingMode.toJavaRoundingMode()))

    actual fun BigDecimal.pow(n: Int): BigDecimal = 
        BigDecimal((this as JavaBigDecimal).pow(n))
    actual fun BigDecimal.sqrt(scale: Int, roundingMode: RoundingMode): BigDecimal = 
        BigDecimal((this as JavaBigDecimal).sqrt(MathContext(scale, roundingMode.toJavaRoundingMode())))

    actual fun BigDecimal.compareTo(other: BigDecimal): Int = 
        (this as JavaBigDecimal).compareTo(other as JavaBigDecimal)

    actual val BigDecimal.isZero: Boolean get() = (this as JavaBigDecimal).compareTo(JavaBigDecimal.ZERO) == 0
    actual val BigDecimal.isPositive: Boolean get() = (this as JavaBigDecimal).compareTo(JavaBigDecimal.ZERO) > 0
    actual val BigDecimal.isNegative: Boolean get() = (this as JavaBigDecimal).compareTo(JavaBigDecimal.ZERO) < 0

    actual fun BigDecimal.abs(): BigDecimal = BigDecimal((this as JavaBigDecimal).abs())
    actual fun BigDecimal.negate(): BigDecimal = BigDecimal((this as JavaBigDecimal).negate())

    actual fun min(a: BigDecimal, b: BigDecimal): BigDecimal = 
        BigDecimal((a as JavaBigDecimal).min(b as JavaBigDecimal))
    actual fun max(a: BigDecimal, b: BigDecimal): BigDecimal = 
        BigDecimal((a as JavaBigDecimal).max(b as JavaBigDecimal))

    actual fun BigDecimal.toLong(): Long = (this as JavaBigDecimal).toLong()

    actual val ZERO: BigDecimal = BigDecimal(JavaBigDecimal.ZERO)
    actual val ONE: BigDecimal = BigDecimal(JavaBigDecimal.ONE)
    actual val TWO: BigDecimal = BigDecimal(JavaBigDecimal.TWO)
    actual val TEN: BigDecimal = BigDecimal(JavaBigDecimal.TEN)
    actual val HUNDRED: BigDecimal = BigDecimal(JavaBigDecimal.valueOf(100))
}

actual enum class RoundingMode {
    UP,
    DOWN,
    CEILING,
    FLOOR,
    HALF_UP,
    HALF_DOWN,
    HALF_EVEN,
    UNNECESSARY;

    fun toJavaRoundingMode(): JavaRoundingMode = when (this) {
        UP -> JavaRoundingMode.UP
        DOWN -> JavaRoundingMode.DOWN
        CEILING -> JavaRoundingMode.CEILING
        FLOOR -> JavaRoundingMode.FLOOR
        HALF_UP -> JavaRoundingMode.HALF_UP
        HALF_DOWN -> JavaRoundingMode.HALF_DOWN
        HALF_EVEN -> JavaRoundingMode.HALF_EVEN
        UNNECESSARY -> JavaRoundingMode.UNNECESSARY
    }
}

actual class BigDecimal : JavaBigDecimal {
    constructor(value: Double) : super(value)
    constructor(value: String) : super(value)
    constructor(value: Int) : super(value)
    constructor(value: Long) : super(value)
    constructor(value: JavaBigDecimal) : super(value.toString())

    override fun toByte(): Byte = (this as JavaBigDecimal).toByte()
    override fun toShort(): Short = (this as JavaBigDecimal).toShort()
    override fun toInt(): Int = (this as JavaBigDecimal).toInt()
    override fun toLong(): Long = (this as JavaBigDecimal).toLong()
    override fun toFloat(): Float = (this as JavaBigDecimal).toFloat()
    override fun toDouble(): Double = (this as JavaBigDecimal).toDouble()
    override fun toBigInteger(): BigInteger = (this as JavaBigDecimal).toBigInteger()
    override fun toBigIntegerExact(): BigInteger = (this as JavaBigDecimal).toBigIntegerExact()
    override fun toPlainString(): String = (this as JavaBigDecimal).toPlainString()
    override fun toEngineeringString(): String = (this as JavaBigDecimal).toEngineeringString()
    override fun toString(): String = (this as JavaBigDecimal).toString()
    override fun hashCode(): Int = (this as JavaBigDecimal).hashCode()
    override fun equals(other: Any?): Boolean = (this as JavaBigDecimal).equals(other)
} 