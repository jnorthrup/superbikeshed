package borg.trikeshed.acapulco

import borg.trikeshed.lib.Series
import borg.trikeshed.lib.j
import borg.trikeshed.lib.`▶`
import borg.trikeshed.acapulco.model.PortfolioRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigDecimal
import java.time.Instant

/**
 * Bridge class to connect the Coinbase trading bot with the Moneyfan UI.
 * Handles data transformation and state synchronization between the two systems.
 */
class MoneyfanBridge {
    // State flows for UI updates
    private val _portfolioState = MutableStateFlow<PortfolioState?>(null)
    val portfolioState: StateFlow<PortfolioState?> = _portfolioState.asStateFlow()

    private val _tradingState = MutableStateFlow<TradingState?>(null)
    val tradingState: StateFlow<TradingState?> = _tradingState.asStateFlow()

    // Data transformation methods
    fun updatePortfolioState(
        portfolioRows: List<PortfolioRow>,
        totalValue: BigDecimal,
        cashBalance: BigDecimal
    ) {
        _portfolioState.value = PortfolioState(
            assets = portfolioRows.map { row: PortfolioRow ->
                AssetState(
                    symbol = row.symbol,
                    quantity = row.quantity,
                    price = row.price,
                    value = row.value,
                    baseline = row.baseline,
                    deviation = row.deviation,
                    priceChange = row.priceChange
                )
            },
            totalValue = totalValue,
            cashBalance = cashBalance,
            timestamp = Instant.now()
        )
    }

    fun updateTradingState(
        harvestedAmount: BigDecimal,
        anyTrades: Boolean,
        portfolioDeviation: Double,
        crashProtectionActive: Boolean
    ) {
        _tradingState.value = TradingState(
            harvestedAmount = harvestedAmount,
            anyTrades = anyTrades,
            portfolioDeviation = portfolioDeviation,
            crashProtectionActive = crashProtectionActive,
            timestamp = Instant.now()
        )
    }
}

data class PortfolioState(
    val assets: List<AssetState>,
    val totalValue: BigDecimal,
    val cashBalance: BigDecimal,
    val timestamp: Instant
)

data class AssetState(
    val symbol: String,
    val quantity: BigDecimal,
    val price: BigDecimal?,
    val value: BigDecimal?,
    val baseline: Double?,
    val deviation: Double?,
    val priceChange: BigDecimal?
)

data class TradingState(
    val harvestedAmount: BigDecimal,
    val anyTrades: Boolean,
    val portfolioDeviation: Double,
    val crashProtectionActive: Boolean,
    val timestamp: Instant
) 