package moneyfan.backtest

import borg.trikeshed.lib.Series
import com.ta4k.core.model.Kline
import com.ta4k.core.model.TradingPair
import moneyfan.model.TradeSignal
import moneyfan.model.TradeResult
import java.math.BigDecimal
import java.time.Instant

/**
 * Core backtesting engine that processes historical data and simulates trading strategies
 */
class BacktestEngine(
    private val initialCapital: BigDecimal,
    private val tradingFee: BigDecimal = BigDecimal("0.001") // 0.1% default fee
) {
    private var currentCapital = initialCapital
    private var currentPosition: Position? = null
    private val tradeHistory = mutableListOf<TradeResult>()
    
    data class Position(
        val entryPrice: BigDecimal,
        val quantity: BigDecimal,
        val entryTime: Instant,
        val isLong: Boolean
    )

    /**
     * Runs a backtest on the provided historical data
     */
    fun runBacktest(
        pair: TradingPair,
        klines: Indexed<Kline>,
        strategy: (Indexed<Kline>) -> Indexed<TradeSignal>
    ): BacktestResult {
        val signals = strategy(klines)
        var currentPosition: Position? = null
        
        for (i in 0 until klines.size) {
            val kline = klines[i]
            val signal = signals[i]
            
            when (signal) {
                is TradeSignal.Buy -> {
                    if (currentPosition == null) {
                        val quantity = calculatePositionSize(kline.closePrice)
                        currentPosition = Position(
                            entryPrice = kline.closePrice,
                            quantity = quantity,
                            entryTime = kline.openTime,
                            isLong = true
                        )
                        currentCapital = currentCapital.subtract(quantity.multiply(kline.closePrice))
                    }
                }
                is TradeSignal.Sell -> {
                    if (currentPosition != null && currentPosition.isLong) {
                        val exitPrice = kline.closePrice
                        val pnl = calculatePnL(currentPosition, exitPrice)
                        tradeHistory.add(
                            TradeResult(
                                pair = pair,
                                entryPrice = currentPosition.entryPrice,
                                exitPrice = exitPrice,
                                quantity = currentPosition.quantity,
                                pnl = pnl,
                                entryTime = currentPosition.entryTime,
                                exitTime = kline.openTime
                            )
                        )
                        currentCapital = currentCapital.add(pnl)
                        currentPosition = null
                    }
                }
                is TradeSignal.Short -> {
                    if (currentPosition == null) {
                        val quantity = calculatePositionSize(kline.closePrice)
                        currentPosition = Position(
                            entryPrice = kline.closePrice,
                            quantity = quantity,
                            entryTime = kline.openTime,
                            isLong = false
                        )
                        currentCapital = currentCapital.add(quantity.multiply(kline.closePrice))
                    }
                }
                is TradeSignal.Cover -> {
                    if (currentPosition != null && !currentPosition.isLong) {
                        val exitPrice = kline.closePrice
                        val pnl = calculatePnL(currentPosition, exitPrice)
                        tradeHistory.add(
                            TradeResult(
                                pair = pair,
                                entryPrice = currentPosition.entryPrice,
                                exitPrice = exitPrice,
                                quantity = currentPosition.quantity,
                                pnl = pnl,
                                entryTime = currentPosition.entryTime,
                                exitTime = kline.openTime
                            )
                        )
                        currentCapital = currentCapital.add(pnl)
                        currentPosition = null
                    }
                }
                is TradeSignal.Hold -> {
                    // Do nothing, maintain current position
                }
            }
        }
        
        return BacktestResult(
            initialCapital = initialCapital,
            finalCapital = currentCapital,
            trades = tradeHistory,
            pair = pair
        )
    }
    
    private fun calculatePositionSize(price: BigDecimal): BigDecimal {
        // Use 95% of available capital to account for fees
        val availableCapital = currentCapital.multiply(BigDecimal("0.95"))
        return availableCapital.divide(price, 8, java.math.RoundingMode.DOWN)
    }
    
    private fun calculatePnL(position: Position, exitPrice: BigDecimal): BigDecimal {
        val grossPnL = if (position.isLong) {
            exitPrice.subtract(position.entryPrice).multiply(position.quantity)
        } else {
            position.entryPrice.subtract(exitPrice).multiply(position.quantity)
        }
        
        // Apply trading fees
        val entryFee = position.entryPrice.multiply(position.quantity).multiply(tradingFee)
        val exitFee = exitPrice.multiply(position.quantity).multiply(tradingFee)
        
        return grossPnL.subtract(entryFee).subtract(exitFee)
    }
}

data class BacktestResult(
    val initialCapital: BigDecimal,
    val finalCapital: BigDecimal,
    val trades: List<TradeResult>,
    val pair: TradingPair
) {
    val totalReturn: BigDecimal
        get() = finalCapital.subtract(initialCapital).divide(initialCapital, 4, java.math.RoundingMode.HALF_UP)
    
    val winRate: BigDecimal
        get() = if (trades.isEmpty()) BigDecimal.ZERO else {
            val winningTrades = trades.count { it.pnl > BigDecimal.ZERO }
            BigDecimal(winningTrades).divide(BigDecimal(trades.size), 4, java.math.RoundingMode.HALF_UP)
        }
    
    val profitFactor: BigDecimal
        get() {
            val grossProfit = trades.filter { it.pnl > BigDecimal.ZERO }
                .fold(BigDecimal.ZERO) { acc, trade -> acc.add(trade.pnl) }
            val grossLoss = trades.filter { it.pnl < BigDecimal.ZERO }
                .fold(BigDecimal.ZERO) { acc, trade -> acc.add(trade.pnl.abs()) }
            return if (grossLoss == BigDecimal.ZERO) BigDecimal.ZERO
                   else grossProfit.divide(grossLoss, 4, java.math.RoundingMode.HALF_UP)
        }
    
    val maxDrawdown: BigDecimal
        get() {
            var peak = initialCapital
            var maxDrawdown = BigDecimal.ZERO
            
            var currentCapital = initialCapital
            trades.forEach { trade ->
                currentCapital = currentCapital.add(trade.pnl)
                if (currentCapital > peak) {
                    peak = currentCapital
                }
                val drawdown = peak.subtract(currentCapital).divide(peak, 4, java.math.RoundingMode.HALF_UP)
                if (drawdown > maxDrawdown) {
                    maxDrawdown = drawdown
                }
            }
            return maxDrawdown
        }
} 