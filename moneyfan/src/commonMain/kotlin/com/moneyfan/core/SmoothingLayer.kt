package com.moneyfan.core

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.RoundingMode
import com.ionspin.kotlin.bignum.integer.toBigInteger
// No longer needed as CandleFSM handles datetime
// import java.time.Instant
import kotlin.math.exp
import kotlin.math.ln

sealed class SmoothingType {
    object Wilder : SmoothingType()
    object Exponential : SmoothingType()
    object Hull : SmoothingType()
    object Kalman : SmoothingType()
}

class SmoothingLayer(
    private val type: SmoothingType = SmoothingType.Exponential,
    private val period: Int = 14,
    private val calculationScale: Int = 8
) {
    private val periodBd = BigDecimal.fromInt(period)
    private var previousValue: BigDecimal? = null
    private var previousError: BigDecimal = BigDecimal.ZERO
    private var previousGain: BigDecimal = BigDecimal.ONE
    private val kalmanQ = BigDecimal.parseString("0.0001") // Process noise
    private val kalmanR = BigDecimal.parseString("0.1")    // Measurement noise

    fun smooth(value: BigDecimal): BigDecimal {
        return when (type) {
            is SmoothingType.Wilder -> wilderSmooth(value)
            is SmoothingType.Exponential -> exponentialSmooth(value)
            is SmoothingType.Hull -> hullSmooth(value)
            is SmoothingType.Kalman -> kalmanSmooth(value)
        }
    }

    private fun wilderSmooth(value: BigDecimal): BigDecimal {
        val actualPreviousValue = previousValue ?: value
        val result = (actualPreviousValue * (periodBd - BigDecimal.ONE) + value)
            .divide(periodBd, calculationScale, RoundingMode.HALF_UP)
        previousValue = result
        return result
    }

    private fun exponentialSmooth(value: BigDecimal): BigDecimal {
        val alpha = BigDecimal.fromDouble(2.0 / (period + 1))
        val actualPreviousValue = previousValue ?: value
        val result = actualPreviousValue * (BigDecimal.ONE - alpha) + (value * alpha)
        val scaledResult = result.setScale(calculationScale, RoundingMode.HALF_UP)
        previousValue = scaledResult
        return scaledResult
    }

    private fun hullSmooth(value: BigDecimal): BigDecimal {
        // Hull Moving Average implementation
        // HMA[n] = WMA(2*WMA(price, n/2) - WMA(price, n), sqrt(n))
        // For simplicity and to avoid needing a full WMA history for the standard HMA,
        // this implementation is a simplified version that uses the previous smoothed value.
        // A more correct HMA would require a list of recent prices or WMAs.
        // This version will behave more like a significantly smoothed EMA.
        // Consider this a placeholder if true HMA is strictly needed.

        val actualPreviousValue = previousValue ?: value // Use current value if no previous
        val halfPeriod = period / 2
        val sqrtPeriodDouble = kotlin.math.sqrt(period.toDouble())

        // Alpha for WMA with period p is 2 / (p * (p + 1)), but for simple recursive WMA-like: 2/(p+1)
        val alphaHalf = BigDecimal.fromDouble(2.0 / (halfPeriod + 1))
        val alphaFull = BigDecimal.fromDouble(2.0 / (period + 1))

        // First WMA-like smooth (half period)
        val wmaHalf = value * alphaHalf + actualPreviousValue * (BigDecimal.ONE - alphaHalf)

        // Second WMA-like smooth (full period)
        val wmaFull = value * alphaFull + actualPreviousValue * (BigDecimal.ONE - alphaFull)

        // Difference, scaled
        val diff = wmaHalf * BigDecimal.TWO - wmaFull

        // Smoothed result using a WMA-like approach on the diff with sqrtPeriod
        // This is a simplification. True HMA uses WMA on a series of these diffs.
        val alphaSqrt = BigDecimal.fromDouble(2.0 / (sqrtPeriodDouble + 1)) // Alpha for the final smoothing

        // Apply smoothing to the diff. If previousValue was null, diff is based on current value,
        // so the "previous" for this stage should also be related to current value.
        // Using `diff` itself as the initial "previous" if `previousValue` was null.
        val previousDiffSmooth = if (this.previousValue == null) diff else this.previousValue!!

        val smoothedResult = diff * alphaSqrt + previousDiffSmooth * (BigDecimal.ONE - alphaSqrt)

        val result = smoothedResult.setScale(calculationScale, RoundingMode.HALF_UP)
        previousValue = result
        return result
    }

    private fun kalmanSmooth(value: BigDecimal): BigDecimal {
        // Kalman Filter implementation
        val prediction = previousValue ?: value
        var errorCovariance = previousError // Use 'var' as it's updated

        // Prediction update
        errorCovariance += kalmanQ // Error covariance prediction

        // Measurement update
        val kalmanGain = errorCovariance.divide(
            errorCovariance + kalmanR,
            calculationScale,
            RoundingMode.HALF_UP
        )

        val smoothed = prediction + (kalmanGain * (value - prediction))
        val result = smoothed.setScale(calculationScale, RoundingMode.HALF_UP)

        previousError = (BigDecimal.ONE - kalmanGain) * errorCovariance
        previousError = previousError.setScale(calculationScale, RoundingMode.HALF_UP) // Scale after update

        previousValue = result
        return result
    }

    fun reset() {
        previousValue = null
        previousError = BigDecimal.ZERO // Reset to initial uncertainty
        // previousGain is not directly used in this Kalman version, errorCovariance (previousError) is key
    }
}

class TickSmoother(
    private val smoothingLayer: SmoothingLayer = SmoothingLayer()
) {
    fun smoothTick(tick: Tick): Tick {
        val smoothedPrice = smoothingLayer.smooth(tick.price)
        val smoothedVolume = smoothingLayer.smooth(tick.volume)

        return tick.copy(
            price = smoothedPrice,
            volume = smoothedVolume
        )
    }
}

class CandleSmoother(
    private val smoothingLayer: SmoothingLayer = SmoothingLayer()
) {
    fun smoothCandle(candle: Candle): Candle {
        val smoothedOpen = smoothingLayer.smooth(candle.open)
        val smoothedHigh = smoothingLayer.smooth(candle.high)
        val smoothedLow = smoothingLayer.smooth(candle.low)
        val smoothedClose = smoothingLayer.smooth(candle.close)
        val smoothedVolume = smoothingLayer.smooth(candle.volume)

        return candle.copy(
            open = smoothedOpen,
            high = smoothedHigh,
            low = smoothedLow,
            close = smoothedClose,
            volume = smoothedVolume
        )
    }
}