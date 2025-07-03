package com.ta4k.indicators

import com.ta4k.core.model.Kline // Assuming this path
import borg.trikeshed.lib.j // For creating Indexed from results
import borg.trikeshed.lib.Indexed
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Average Directional Index (ADX) indicator.
 * Measures trend strength. It uses smoothed +DM, -DM, and TR.
 * Operates on a Trikethed [Indexed] of [Kline].
 */
class ADXIndicator(
    private val klineSeries: Indexed<Kline>, // Changed
    private val period: Int
) {
    init {
        require(period > 0) { "Period must be positive" }
    }

    // Internal lists to store intermediate calculations
    private val plusDMResults = mutableListOf<BigDecimal?>()    // Raw +DM
    private val minusDMResults = mutableListOf<BigDecimal?>()   // Raw -DM
    private val trueRangeResults = mutableListOf<BigDecimal?>() // Raw TR (used for ADX's internal TR smoothing)

    private val smoothedPlusDM = mutableListOf<BigDecimal?>()
    private val smoothedMinusDM = mutableListOf<BigDecimal?>()
    private val smoothedTR = mutableListOf<BigDecimal?>()

    private val plusDIResults = mutableListOf<BigDecimal?>()
    private val minusDIResults = mutableListOf<BigDecimal?>()
    private val dxResults = mutableListOf<BigDecimal?>()
    val adxResults = mutableListOf<BigDecimal?>() // Smoothed ADX values

    private val calculationScale = 8
    private val resultScale = 2 // Standard scale for ADX, DI values

    private var calculatedUpToIndex = -1

    private fun ensureListSize(list: MutableList<BigDecimal?>, size: Int) {
        while (list.size < size) { // Use < instead of <= to make it size of klineSeries
            list.add(null)
        }
    }

    private fun preAllocateListsIfNeeded() {
        if (klineSeries.size == 0) return
        // Only pre-allocate if lists are currently empty, meaning it's the first pass
        if (plusDMResults.isEmpty()) ensureListSize(plusDMResults, klineSeries.size)
        if (minusDMResults.isEmpty()) ensureListSize(minusDMResults, klineSeries.size)
        if (trueRangeResults.isEmpty()) ensureListSize(trueRangeResults, klineSeries.size)
        if (smoothedPlusDM.isEmpty()) ensureListSize(smoothedPlusDM, klineSeries.size)
        if (smoothedMinusDM.isEmpty()) ensureListSize(smoothedMinusDM, klineSeries.size)
        if (smoothedTR.isEmpty()) ensureListSize(smoothedTR, klineSeries.size)
        if (plusDIResults.isEmpty()) ensureListSize(plusDIResults, klineSeries.size)
        if (minusDIResults.isEmpty()) ensureListSize(minusDIResults, klineSeries.size)
        if (dxResults.isEmpty()) ensureListSize(dxResults, klineSeries.size)
        if (adxResults.isEmpty()) ensureListSize(adxResults, klineSeries.size)
    }


    private fun ensureCalculatedUpTo(targetIndex: Int) {
        if (targetIndex < 0 || targetIndex >= klineSeries.size || targetIndex <= calculatedUpToIndex ) {
            return
        }

        if (calculatedUpToIndex == -1 && klineSeries.size > 0) {
            preAllocateListsIfNeeded()
        }

        val periodBigDecimal = BigDecimal(period)
        val startIndex = if (calculatedUpToIndex == -1) 0 else calculatedUpToIndex + 1

        for (i in startIndex..targetIndex) {
            if (i == 0) {
                plusDMResults[i] = BigDecimal.ZERO
                minusDMResults[i] = BigDecimal.ZERO
                trueRangeResults[i] = klineSeries[0].highPrice.subtract(klineSeries[0].lowPrice)
                // Other lists (smoothed, DI, DX, ADX) remain null for index 0 as set by preAllocate
                continue
            }

            val currentKline = klineSeries[i]
            val prevKline = klineSeries[i - 1]

            // Calculate +DM, -DM
            val upMove = currentKline.highPrice.subtract(prevKline.highPrice)
            val downMove = prevKline.lowPrice.subtract(currentKline.lowPrice)

            var currentPlusDM = BigDecimal.ZERO
            if (upMove > downMove && upMove > BigDecimal.ZERO) {
                currentPlusDM = upMove
            }
            plusDMResults[i] = currentPlusDM

            var currentMinusDM = BigDecimal.ZERO
            if (downMove > upMove && downMove > BigDecimal.ZERO) {
                currentMinusDM = downMove
            }
            minusDMResults[i] = currentMinusDM

            var currentTR = currentKline.highPrice.subtract(currentKline.lowPrice)
            currentTR = currentTR.max(currentKline.highPrice.subtract(prevKline.closePrice).abs())
            currentTR = currentTR.max(currentKline.lowPrice.subtract(prevKline.closePrice).abs())
            trueRangeResults[i] = currentTR

            if (i < period) {
                // smoothed*, DI*, DX*, ADX* results remain null
            } else { // i >= period
                val sPlusDM: BigDecimal
                val sMinusDM: BigDecimal
                val sTR: BigDecimal

                if (i == period) {
                    sPlusDM = (1..period).sumOf { k -> plusDMResults[k] ?: BigDecimal.ZERO }
                    sMinusDM = (1..period).sumOf { k -> minusDMResults[k] ?: BigDecimal.ZERO }
                    sTR = (1..period).sumOf { k -> trueRangeResults[k] ?: BigDecimal.ZERO }
                } else {
                    val prevSPlusDM = smoothedPlusDM[i-1] ?: BigDecimal.ZERO
                    sPlusDM = prevSPlusDM.subtract(prevSPlusDM.divide(periodBigDecimal, calculationScale, RoundingMode.HALF_UP)).add(plusDMResults[i]!!)

                    val prevSMinusDM = smoothedMinusDM[i-1] ?: BigDecimal.ZERO
                    sMinusDM = prevSMinusDM.subtract(prevSMinusDM.divide(periodBigDecimal, calculationScale, RoundingMode.HALF_UP)).add(minusDMResults[i]!!)

                    val prevSTR = smoothedTR[i-1] ?: BigDecimal.ZERO
                    sTR = prevSTR.subtract(prevSTR.divide(periodBigDecimal, calculationScale, RoundingMode.HALF_UP)).add(trueRangeResults[i]!!)
                }
                smoothedPlusDM[i] = sPlusDM
                smoothedMinusDM[i] = sMinusDM
                smoothedTR[i] = sTR

                val plusDI = if (sTR > BigDecimal.ZERO) sPlusDM.multiply(BigDecimal(100)).divide(sTR, calculationScale, RoundingMode.HALF_UP) else BigDecimal.ZERO
                val minusDI = if (sTR > BigDecimal.ZERO) sMinusDM.multiply(BigDecimal(100)).divide(sTR, calculationScale, RoundingMode.HALF_UP) else BigDecimal.ZERO
                plusDIResults[i] = plusDI
                minusDIResults[i] = minusDI

                val diSum = plusDI.add(minusDI)
                val dx = if (diSum > BigDecimal.ZERO) (plusDI.subtract(minusDI)).abs().multiply(BigDecimal(100)).divide(diSum, calculationScale, RoundingMode.HALF_UP) else BigDecimal.ZERO
                dxResults[i] = dx

                // ADX smoothing starts after 'period' DX values are available.
                // First DX is at index 'period'. So 'period' DX values are from index 'period' to '2*period - 1'.
                // First ADX is at index '2*period - 1'.
                if (i < 2 * period - 1) {
                    // adxResults[i] remains null
                } else if (i == 2 * period - 1) {
                    var sumDX = BigDecimal.ZERO
                    for (k in period until (period + period)) {
                        sumDX += dxResults[k] ?: BigDecimal.ZERO
                    }
                    adxResults[i] = sumDX.divide(periodBigDecimal, calculationScale, RoundingMode.HALF_UP)
                } else { // i > 2 * period - 1
                    val prevAdx = adxResults[i-1] ?: BigDecimal.ZERO
                    val currentDx = dxResults[i] ?: BigDecimal.ZERO // Current DX value at index i
                    // Corrected Wilder's smoothing for ADX (same as for DM and TR)
                    val adx = prevAdx.subtract(prevAdx.divide(periodBigDecimal, calculationScale, RoundingMode.HALF_UP)).add(currentDx)
                    adxResults[i] = adx
                }
            }
        }
        calculatedUpToIndex = targetIndex
    }

    fun getPlusDI(index: Int): BigDecimal? {
        if (index < 0 || index >= klineSeries.size) return null
        ensureCalculatedUpTo(index)
        return if (index >= period && index < plusDIResults.size) plusDIResults[index]?.setScale(resultScale, RoundingMode.HALF_UP) else null
    }

    fun getMinusDI(index: Int): BigDecimal? {
        if (index < 0 || index >= klineSeries.size) return null
        ensureCalculatedUpTo(index)
        return if (index >= period && index < minusDIResults.size) minusDIResults[index]?.setScale(resultScale, RoundingMode.HALF_UP) else null
    }

    fun getDX(index: Int): BigDecimal? {
        if (index < 0 || index >= klineSeries.size) return null
        ensureCalculatedUpTo(index)
        return if (index >= period && index < dxResults.size) dxResults[index]?.setScale(resultScale, RoundingMode.HALF_UP) else null
    }

    fun getADX(index: Int): BigDecimal? {
        if (index < 0 || index >= klineSeries.size) return null
        ensureCalculatedUpTo(index)
        return if (index >= (2 * period - 1) && index < adxResults.size) adxResults[index]?.setScale(resultScale, RoundingMode.HALF_UP) else null
    }

    val plusDISeries: Indexed<BigDecimal?>
        get() {
            if (klineSeries.size > 0 && calculatedUpToIndex < klineSeries.size - 1) {
                ensureCalculatedUpTo(klineSeries.size - 1)
            }
            return klineSeries.size j { idx:Int -> this.getPlusDI(idx) }
        }

    val minusDISeries: Indexed<BigDecimal?>
        get() {
            if (klineSeries.size > 0 && calculatedUpToIndex < klineSeries.size - 1) {
                ensureCalculatedUpTo(klineSeries.size - 1)
            }
            return klineSeries.size j { idx:Int -> this.getMinusDI(idx) }
        }

    val adxValueSeries: Indexed<BigDecimal?> // Renamed from adxSeries to avoid conflict with adxResults list
        get() {
            if (klineSeries.size > 0 && calculatedUpToIndex < klineSeries.size - 1) {
                ensureCalculatedUpTo(klineSeries.size - 1)
            }
            return klineSeries.size j { idx:Int -> this.getADX(idx) }
        }
}
