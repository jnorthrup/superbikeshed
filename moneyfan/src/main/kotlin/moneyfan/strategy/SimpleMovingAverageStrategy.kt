package moneyfan.strategy

import moneyfan.model.TradeSignal
import borg.trikeshed.lib.Series
import com.ta4k.core.model.Kline
import java.math.BigDecimal

/**
 * Simple Moving Average Strategy using ta4j indicators and rules.
 */
class SimpleMovingAverageStrategy(
    private val fastPeriod: Int,
    private val slowPeriod: Int
) {
    fun generateSignals(klines: Series<Kline>): Series<TradeSignal> {
        // Convert Series<Kline> to ta4j BarSeries
        val barSeries = BaseBarSeries()
        for (i in 0 until klines.size) {
            val kline = klines.get(i)
            barSeries.addBar(
                kline.openTime,
                kline.openPrice.toDouble(),
                kline.highPrice.toDouble(),
                kline.lowPrice.toDouble(),
                kline.closePrice.toDouble(),
                kline.volume.toDouble()
            )
        }

        // Create indicators
        val closePrice = ClosePriceIndicator(barSeries)
        val fastMA = SMAIndicator(closePrice, fastPeriod)
        val slowMA = SMAIndicator(closePrice, slowPeriod)

        // Create trading rules
        val entryRule = CrossedUpIndicatorRule(fastMA, slowMA)
        val exitRule = CrossedDownIndicatorRule(fastMA, slowMA)
        val tradingRule = OrRule(entryRule, exitRule)

        // Generate signals
        return Series.of(barSeries.barCount) { i ->
            if (i < slowPeriod) {
                TradeSignal.NONE
            } else {
                when {
                    entryRule.isSatisfied(i) -> TradeSignal.BUY
                    exitRule.isSatisfied(i) -> TradeSignal.SELL
                    else -> TradeSignal.NONE
                }
            }
        }
    }
} 