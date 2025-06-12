package com.moneyfan.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
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
    private val periodBd = BigDecimal(period)
    private var previousValue: BigDecimal? = null
    private var previousError: BigDecimal = BigDecimal.ZERO
    private var previousGain: BigDecimal = BigDecimal.ONE
    private val kalmanQ = BigDecimal("0.0001") // Process noise
    private val kalmanR = BigDecimal("0.1")    // Measurement noise

    fun smooth(value: BigDecimal): BigDecimal {
        return when (type) {
            is SmoothingType.Wilder -> wilderSmooth(value)
            is SmoothingType.Exponential -> exponentialSmooth(value)
            is SmoothingType.Hull -> hullSmooth(value)
            is SmoothingType.Kalman -> kalmanSmooth(value)
        }
    }

    private fun wilderSmooth(value: BigDecimal): BigDecimal {
        return previousValue?.let { prev ->
            (prev.multiply(periodBd.subtract(BigDecimal.ONE)).add(value))
                .divide(periodBd, calculationScale, RoundingMode.HALF_UP)
        } ?: value
    }

    private fun exponentialSmooth(value: BigDecimal): BigDecimal {
        val alpha = BigDecimal(2.0 / (period + 1))
        return previousValue?.let { prev ->
            prev.multiply(BigDecimal.ONE.subtract(alpha)).add(value.multiply(alpha))
                .setScale(calculationScale, RoundingMode.HALF_UP)
        } ?: value
    }

    private fun hullSmooth(value: BigDecimal): BigDecimal {
        // Hull Moving Average implementation
        val halfPeriod = period / 2
        val sqrtPeriod = kotlin.math.sqrt(period.toDouble()).toInt()
        
        return previousValue?.let { prev ->
            val wmaf = value.multiply(BigDecimal(2.0 / (halfPeriod + 1)))
                .add(prev.multiply(BigDecimal(1 - 2.0 / (halfPeriod + 1))))
            
            val wmas = value.multiply(BigDecimal(2.0 / (period + 1)))
                .add(prev.multiply(BigDecimal(1 - 2.0 / (period + 1))))
            
            wmaf.multiply(BigDecimal(2.0)).subtract(wmas)
                .multiply(BigDecimal(1.0 / sqrtPeriod))
                .setScale(calculationScale, RoundingMode.HALF_UP)
        } ?: value
    }

    private fun kalmanSmooth(value: BigDecimal): BigDecimal {
        // Kalman Filter implementation
        val prediction = previousValue ?: value
        val predictionError = previousError.add(kalmanQ)
        
        val kalmanGain = predictionError.divide(
            predictionError.add(kalmanR),
            calculationScale,
            RoundingMode.HALF_UP
        )
        
        val smoothed = prediction.add(
            kalmanGain.multiply(value.subtract(prediction))
                .setScale(calculationScale, RoundingMode.HALF_UP)
        )
        
        previousError = BigDecimal.ONE.subtract(kalmanGain)
            .multiply(predictionError)
            .setScale(calculationScale, RoundingMode.HALF_UP)
        
        previousValue = smoothed
        return smoothed
    }

    fun reset() {
        previousValue = null
        previousError = BigDecimal.ZERO
        previousGain = BigDecimal.ONE
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