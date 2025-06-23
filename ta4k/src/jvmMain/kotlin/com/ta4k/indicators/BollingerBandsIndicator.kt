package com.ta4k.indicators

import com.ta4k.core.model.Kline // Assuming this path
import borg.trikeshed.lib.j      // Import infix j for creating Series
import borg.trikeshed.lib.Indexed
import java.math.BigDecimal
import java.math.MathContext // Required for BigDecimal sqrt
import java.math.RoundingMode
import kotlin.math.sqrt

/**
 * Bollinger Bands indicator.
 * Consists of a middle band (SMA) and upper/lower bands based on standard deviation.
 * Operates on a Trikethed [Indexed] of [Kline].
 */
class BollingerBandsIndicator(
    private val klineIndexed: Indexed<Kline>, // Changed
    private val period: Int,
    private val standardDeviationMultiplier: BigDecimal = BigDecimal("2.0"),
    private val klinePropertySelector: (Kline) -> BigDecimal = { it.closePrice }
) {
    init {
        require(period > 0) { "Period must be positive" }
        require(standardDeviationMultiplier > BigDecimal.ZERO) { "Standard deviation multiplier must be positive" }
    }

    // SMAIndicator has been refactored to accept Indexed<Kline>.
    val middleBandIndicator: SMAIndicator = SMAIndicator(klineIndexed, period, klinePropertySelector)

    // Internal caches
    private val upperBandResults = mutableListOf<BigDecimal?>()
    private val lowerBandResults = mutableListOf<BigDecimal?>()
    private val stdDevResults = mutableListOf<BigDecimal?>()
    private var calculatedUpToIndex = -1

    private val calculationScale = 8
    // Result scale for bands usually matches price scale. Default to 2 if series is empty or first kline has no scale.
    private val resultScale = klineIndexed.firstOrNull()?.let { kline ->
        // Access kline safely as klineSeries might be empty, though firstOrNull handles that.
        // klinePropertySelector might return BigDecimal with default scale if underlying is Int.
        val price = klinePropertySelector(kline)
        if (price.scale() > 0) price.scale() else 2
    } ?: 2

    // Helper to get the first Kline from the series, returns null if series is empty.
    private fun Indexed<Kline>.firstOrNull(): Kline? = if (this.size > 0) this[0] else null

    private fun ensureCalculatedUpTo(targetIndex: Int) {
        if (targetIndex < 0 || targetIndex >= klineIndexed.size || targetIndex <= calculatedUpToIndex) {
            return
        }

        // Pre-allocate lists if this is the first major calculation run or if lists are too small.
        if (calculatedUpToIndex == -1) {
            for (k in 0 until klineIndexed.size) {
                upperBandResults.add(null)
                lowerBandResults.add(null)
                stdDevResults.add(null)
            }
        } else {
            while (upperBandResults.size < klineIndexed.size) upperBandResults.add(null)
            while (lowerBandResults.size < klineIndexed.size) lowerBandResults.add(null)
            while (stdDevResults.size < klineIndexed.size) stdDevResults.add(null)
        }

        // Ensure middle band (SMA) is calculated up to the target index.
        // SMAIndicator's getValue will handle its own Series interaction and caching.
        middleBandIndicator.getValue(targetIndex)

        val startIndex = if (calculatedUpToIndex == -1) 0 else calculatedUpToIndex + 1
        for (i in startIndex..targetIndex) {
            val smaValue = middleBandIndicator.getValue(i) // Get already calculated SMA for index i

            if (smaValue == null || i < period - 1) {
                // upperBandResults[i], lowerBandResults[i], stdDevResults[i] remain null (from pre-allocation)
                continue
            }

            var sumOfSquares = BigDecimal.ZERO
            for (j in 0 until period) {
                val price = klinePropertySelector(klineIndexed[i - j])
                val deviation = price.subtract(smaValue)
                sumOfSquares += deviation.pow(2)
            }

            val variance = sumOfSquares.divide(BigDecimal(period), calculationScale, RoundingMode.HALF_UP)
            // Standard deviation using double conversion. For higher precision, a BigDecimal sqrt is needed.
            val standardDeviation = BigDecimal(sqrt(variance.toDouble()), MathContext(calculationScale))
                                    .setScale(calculationScale, RoundingMode.HALF_UP)
            stdDevResults[i] = standardDeviation // Store with calculationScale

            val bandOffset = standardDeviation.multiply(standardDeviationMultiplier)

            upperBandResults[i] = smaValue.add(bandOffset)
            lowerBandResults[i] = smaValue.subtract(bandOffset)
        }
        calculatedUpToIndex = targetIndex
    }

    /** Gets the value of the upper band for the given index. */
    fun getUpperBand(index: Int): BigDecimal? {
        if (index < 0 || index >= klineIndexed.size) return null
        ensureCalculatedUpTo(index)
        val rawVal = if (index < upperBandResults.size) upperBandResults[index] else null
        return rawVal?.setScale(resultScale, RoundingMode.HALF_UP)
    }

    /** Gets the value of the lower band for the given index. */
    fun getLowerBand(index: Int): BigDecimal? {
        if (index < 0 || index >= klineIndexed.size) return null
        ensureCalculatedUpTo(index)
        val rawVal = if (index < lowerBandResults.size) lowerBandResults[index] else null
        return rawVal?.setScale(resultScale, RoundingMode.HALF_UP)
    }

    /** Gets the value of the middle band (SMA) for the given index. */
    fun getMiddleBandValue(index: Int): BigDecimal? {
        if (index < 0 || index >= klineIndexed.size) return null
        // SMAIndicator's getValue will handle its own Series interaction and caching.
        val smaVal = middleBandIndicator.getValue(index)
        // The SMAIndicator's getValue now returns values at calculationScale + 4.
        // We need to scale it to the common resultScale for BBands.
        return smaVal?.setScale(resultScale, RoundingMode.HALF_UP)
    }

    /** Gets the calculated standard deviation for the given index (used to compute bands). */
    fun getStandardDeviation(index: Int): BigDecimal? {
        if (index < 0 || index >= klineIndexed.size) return null
        ensureCalculatedUpTo(index)
        val rawVal = if (index < stdDevResults.size) stdDevResults[index] else null
        // Return with internal calculationScale as this is not usually scaled to price output scale
        return rawVal?.setScale(calculationScale, RoundingMode.HALF_UP)
    }

    val upperBandValues: Indexed<BigDecimal?> // Renamed to avoid conflict with upperBandResults list
        get() {
            if (klineIndexed.size > 0 && calculatedUpToIndex < klineIndexed.size - 1) {
                ensureCalculatedUpTo(klineIndexed.size - 1)
            }
            return klineIndexed.size j { idx:Int -> this.getUpperBand(idx) }
        }

    val lowerBandValues: Indexed<BigDecimal?> // Renamed to avoid conflict with lowerBandResults list
        get() {
            if (klineIndexed.size > 0 && calculatedUpToIndex < klineIndexed.size - 1) {
                ensureCalculatedUpTo(klineIndexed.size - 1)
            }
            return klineIndexed.size j { idx:Int -> this.getLowerBand(idx) }
        }

    val middleBandValues: Indexed<BigDecimal?> // Renamed for consistency
        get() {
            // SMAIndicator's 'values' property already returns a Indexed<BigDecimal?>
            // and handles scaling. We just need to ensure it's calculated.
            if (klineIndexed.size > 0) { // Ensure SMA is calculated if klines exist
                 middleBandIndicator.getValue(klineIndexed.size -1) // Trigger calculation up to the end
            }
            // Access the SMA's values Series, then re-scale if necessary for BBands context.
            // However, SMAIndicator's getValue already scales. If middleBandIndicator.values uses getValue, it's fine.
            // Let's assume middleBandIndicator.values is correctly scaled or use getMiddleBandValue
            return klineIndexed.size j { idx:Int -> this.getMiddleBandValue(idx) }
        }

    val standardDeviationValues: Indexed<BigDecimal?> // Renamed for consistency
        get() {
            if (klineIndexed.size > 0 && calculatedUpToIndex < klineIndexed.size - 1) {
                ensureCalculatedUpTo(klineIndexed.size - 1)
            }
            return klineIndexed.size j { idx:Int -> this.getStandardDeviation(idx) }
        }
}
