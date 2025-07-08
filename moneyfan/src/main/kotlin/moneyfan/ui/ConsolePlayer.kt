package moneyfan.ui

import borg.trikeshed.cursor.Cursor
import borg.trikeshed.lib.Indexed
import com.ta4k.core.model.Kline
import moneyfan.model.TradeSignal
import moneyfan.strategy.SimpleMovingAverageStrategy

/**
 * ConsolePlayer class to display rolling A, B, C players in separate child panes ticking on the same candles.
 */
class ConsolePlayer(
    internal val klines: Indexed<Kline>,
    internal val strategyA: SimpleMovingAverageStrategy = SimpleMovingAverageStrategy(10, 20),
    internal val strategyB: SimpleMovingAverageStrategy = SimpleMovingAverageStrategy(5, 15),
    internal val strategyC: SimpleMovingAverageStrategy = SimpleMovingAverageStrategy(15, 30)
) {
    fun play() {
        val signalsA = strategyA.generateSignals(klines)
        val signalsB = strategyB.generateSignals(klines)
        val signalsC = strategyC.generateSignals(klines)

        println("Player A Signals:")
        for (i in 0 until signalsA.size) {
            println("Candle $i: ${signalsA.get(i)}")
        }

        println("\nPlayer B Signals:")
        for (i in 0 until signalsB.size) {
            println("Candle $i: ${signalsB.get(i)}")
        }

        println("\nPlayer C Signals:")
        for (i in 0 until signalsC.size) {
            println("Candle $i: ${signalsC.get(i)}")
        }
    }
} 