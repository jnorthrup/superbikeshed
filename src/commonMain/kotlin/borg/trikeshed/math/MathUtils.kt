package borg.trikeshed.math

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Platform-specific math utilities for handling BigDecimal and related operations
 */
expect object MathUtils {
    /**
     * Creates a BigDecimal from a Double value
     */
    fun createBigDecimal(value: Double): BigDecimal

    /**
     * Creates a BigDecimal from a String value
     */
    fun createBigDecimal(value: String): BigDecimal

    /**
     * Creates a BigDecimal from an Int value
     */
    fun createBigDecimal(value: Int): BigDecimal

    /**
     * Creates a BigDecimal from a Long value
     */
    fun createBigDecimal(value: Long): BigDecimal

    /**
     * Returns the scale of a BigDecimal
     */
    fun BigDecimal.scale(): Int

    /**
     * Sets the scale of a BigDecimal with the specified rounding mode
     */
    fun BigDecimal.setScale(scale: Int, roundingMode: RoundingMode): BigDecimal

    /**
     * Adds two BigDecimal values
     */
    fun BigDecimal.add(other: BigDecimal): BigDecimal

    /**
     * Subtracts two BigDecimal values
     */
    fun BigDecimal.subtract(other: BigDecimal): BigDecimal

    /**
     * Multiplies two BigDecimal values
     */
    fun BigDecimal.multiply(other: BigDecimal): BigDecimal

    /**
     * Divides two BigDecimal values with the specified scale and rounding mode
     */
    fun BigDecimal.divide(other: BigDecimal, scale: Int, roundingMode: RoundingMode): BigDecimal

    /**
     * Raises a BigDecimal to the specified power
     */
    fun BigDecimal.pow(n: Int): BigDecimal

    /**
     * Calculates the square root of a BigDecimal with the specified scale and rounding mode
     */
    fun BigDecimal.sqrt(scale: Int, roundingMode: RoundingMode): BigDecimal

    /**
     * Compares two BigDecimal values
     */
    fun BigDecimal.compareTo(other: BigDecimal): Int

    /**
     * Returns true if the BigDecimal is zero
     */
    val BigDecimal.isZero: Boolean

    /**
     * Returns true if the BigDecimal is positive
     */
    val BigDecimal.isPositive: Boolean

    /**
     * Returns true if the BigDecimal is negative
     */
    val BigDecimal.isNegative: Boolean

    /**
     * Returns the absolute value of a BigDecimal
     */
    fun BigDecimal.abs(): BigDecimal

    /**
     * Returns the negation of a BigDecimal
     */
    fun BigDecimal.negate(): BigDecimal

    /**
     * Returns the minimum of two BigDecimal values
     */
    fun min(a: BigDecimal, b: BigDecimal): BigDecimal

    /**
     * Returns the maximum of two BigDecimal values
     */
    fun max(a: BigDecimal, b: BigDecimal): BigDecimal

    /**
     * Converts a BigDecimal to Long
     */
    fun BigDecimal.toLong(): Long

    /**
     * Constants
     */
    val ZERO: BigDecimal
    val ONE: BigDecimal
    val TWO: BigDecimal
    val TEN: BigDecimal
    val HUNDRED: BigDecimal
}

/**
 * Rounding mode for BigDecimal operations
 */
expect enum class RoundingMode {
    UP,
    DOWN,
    CEILING,
    FLOOR,
    HALF_UP,
    HALF_DOWN,
    HALF_EVEN,
    UNNECESSARY
}

/**
 * Platform-specific BigDecimal type
 */
expect class BigDecimal 