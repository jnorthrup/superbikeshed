package moneyfan.strategy

import com.ta4k.core.model.Kline
import com.ta4k.indicators.RSIIndicator
import com.ta4k.indicators.SMAIndicator
import moneyfan.model.TradeSignal
import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.component2()

/**
 * 2-Period RSI Strategy
 * Based on: http://stockcharts.com/school/doku.php?id=chart_school:trading_strategies:rsi2
 */
class RSI2Strategy(
    internal val shortPeriod: Int = 2,
    internal val longPeriod: Int = 15,
    internal val rsiPeriod: Int = 2,
    internal val rsiLowerThreshold: Int = 5,
    internal val rsiUpperThreshold: Int = 95
) {
    fun generateSignals(klines: Indexed<Kline>): Indexed<TradeSignal> {
        // Create indicators
        val shortSma = SMAIndicator(klines, shortPeriod)
        val longSma = SMAIndicator(klines, longPeriod)
        val rsi = RSIIndicator(klines, rsiPeriod)

        // Generate signals
        return Indexed.of(klines.size) { i ->
            if (i < longPeriod) {
                TradeSignal.Hold
            } else {
                when {
                    // Entry conditions
                    shortSma.getValue(i)?.compareTo(longSma.getValue(i) ?: BigDecimal.ZERO) == 1 && // Trend
                    rsi.getValue(i)?.compareTo(BigDecimal(rsiLowerThreshold)) == -1 && // Signal 1
                    shortSma.getValue(i)?.compareTo(klines.component2()(i).closePrice) == 1 -> TradeSignal.Buy // Signal 2

                    // Exit conditions
                    shortSma.getValue(i)?.compareTo(longSma.getValue(i) ?: BigDecimal.ZERO) == -1 && // Trend
                    rsi.getValue(i)?.compareTo(BigDecimal(rsiUpperThreshold)) == 1 && // Signal 1
                    shortSma.getValue(i)?.compareTo(klines.component2()(i).closePrice) == -1 -> TradeSignal.Sell // Signal 2

                    else -> TradeSignal.Hold
                }
            }
        }
    }
} 