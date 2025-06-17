package moneyfan.examples

import moneyfan.trikeshed.Series
import moneyfan.trikeshed.scope.AttentionScope
import moneyfan.trikeshed.scope.FractionalScope
import moneyfan.trikeshed.scope.RangeScope
import moneyfan.trikeshed.extendByClamping
import moneyfan.trikeshed.extendWithDefault
import moneyfan.trikeshed.ffill
import moneyfan.trikeshed.fillna
import moneyfan.trikeshed.focus // Assuming focus is in moneyfan.trikeshed.SeriesExtensions.kt
import moneyfan.trikeshed.toSeries
import moneyfan.trikeshed.emptySeries

/**
 * Simplified representation of a portfolio row for demonstration purposes.
 * In a real scenario, this would align with the KTS bot's `PortfolioRow`.
 *
 * @property symbol The asset symbol (e.g., "BTC").
 * @property actualAllocation Current actual allocation percentage (e.g., 0.2 for 20%). Nullable if unknown.
 * @property targetAllocation Target allocation percentage (e.g., 0.25 for 25%). Nullable if not set.
 * @property fiatValue Current fiat value of the holding. Nullable if not applicable.
 */
data class DemoPortfolioRow(
    val symbol: String,
    val actualAllocation: Double?,
    val targetAllocation: Double?,
    val fiatValue: Double?
) {
    /**
     * Calculated deviation: (actual - target) / target.
     * Returns null if targetAllocation is null, 0.0, or actualAllocation is null.
     */
    val deviation: Double?
        get() {
            return if (actualAllocation != null && targetAllocation != null && targetAllocation != 0.0) {
                (actualAllocation - targetAllocation) / targetAllocation
            } else {
                null
            }
        }

    override fun toString(): String {
        return "Symbol: $symbol, Actual: ${actualAllocation?.times(100)?.format(2)}%, Target: ${targetAllocation?.times(100)?.format(2)}%, Value: ${fiatValue?.format(2)}, Deviation: ${deviation?.times(100)?.format(2)}%"
    }
}

// Helper for formatting doubles, assuming it's not available elsewhere easily
internal fun Double.format(digits: Int): String = this.asDynamic().toFixed(digits)


/**
 * Demonstrates applying AttentionScope to a series of portfolio items,
 * simulating a part of the KTS bot's logic.
 */
fun demonstrateAttentionScopeOnPortfolio(portfolioRows: List<DemoPortfolioRow>) {
    println("--- Demonstrating AttentionScope on Portfolio ---")
    if (portfolioRows.isEmpty()) {
        println("Portfolio is empty. No demonstration possible.")
        return
    }
    val portfolioSeries: Series<DemoPortfolioRow> = portfolioRows.toSeries()
    println("Original portfolio size: ${portfolioSeries.a}")

    // 1. Demonstrate FractionalScope
    val fractionalScope = FractionalScope<DemoPortfolioRow>(0.5, seed = 123L) // Focus on 50% of items
    val focusedFractionalSeries = portfolioSeries.focus(fractionalScope)
    println("\nFocused portfolio (FractionalScope 50%, seed 123): ${focusedFractionalSeries.a} items")
    focusedFractionalSeries.`▶`.forEachIndexed { index, row ->
        println("  Item $index (Fractional): $row")
        // Simulate strategy logic based on deviation
        row.deviation?.let { dev ->
            when {
                dev > 0.03 -> println("    => Harvest signal for ${row.symbol} (Deviation: ${dev.times(100).format(2)}%)")
                dev < -0.04 -> println("    => Rebalance signal for ${row.symbol} (Deviation: ${dev.times(100).format(2)}%)")
                else -> println("    => Hold signal for ${row.symbol} (Deviation: ${dev.times(100).format(2)}%)")
            }
        } ?: println("    => Hold signal for ${row.symbol} (Deviation: N/A)")
    }

    // 2. Demonstrate RangeScope
    val rangeEnd = if (portfolioSeries.a > 0) portfolioSeries.a / 2 else 0
    if (rangeEnd > 0) {
        val rangeScope = RangeScope<DemoPortfolioRow>(0, rangeEnd) // Focus on the first half
        val focusedRangeSeries = portfolioSeries.focus(rangeScope)
        println("\nFocused portfolio (RangeScope first half): ${focusedRangeSeries.a} items")
        focusedRangeSeries.`▶`.forEachIndexed { index, row ->
            println("  Item $index (Range): $row")
            // Simulate strategy logic (can be a shared function)
             row.deviation?.let { dev ->
                when {
                    dev > 0.03 -> println("    => Harvest signal for ${row.symbol} (Deviation: ${dev.times(100).format(2)}%)")
                    dev < -0.04 -> println("    => Rebalance signal for ${row.symbol} (Deviation: ${dev.times(100).format(2)}%)")
                    else -> println("    => Hold signal for ${row.symbol} (Deviation: ${dev.times(100).format(2)}%)")
                }
            } ?: println("    => Hold signal for ${row.symbol} (Deviation: N/A)")
        }
    } else {
        println("\nNot enough items for a meaningful RangeScope example (less than 2 items).")
    }
    println("-------------------------------------------------")
}

/**
 * Demonstrates sparse data handling (fillna, ffill) and series extension.
 */
fun demonstrateSparseAndExtendedHandling() {
    println("\n--- Demonstrating Sparse and Extended Series Handling ---")
    val pricesWithNullsList = listOf(10.0, null, 12.0, null, null, 15.0, 16.0)
    val sparsePrices: Series<Double?> = pricesWithNullsList.toSeries()
    println("Original sparse prices: ${sparsePrices.`▶`.joinToString { it?.format(2) ?: "null" }}")

    // fillna with a default value
    val filledPricesConstant = sparsePrices.fillna(0.0) // Fill nulls with 0.0
    println("fillna(0.0):          ${filledPricesConstant.`▶`.joinToString { it.format(2) }}")

    // ffill (forward fill)
    val ffilledPrices = sparsePrices.ffill()
    println("ffill():              ${ffilledPrices.`▶`.joinToString { it?.format(2) ?: "null" }}")

    // dropna (example, though not explicitly requested but related)
    // val droppedPrices = sparsePrices.dropna()
    // println("dropna():             ${droppedPrices.`▶`.joinToString { it.format(2) }}")


    println("\n--- Demonstrating Series Extension ---")
    val baseSeries = listOf(1.0, 2.0, 3.0).toSeries()
    println("Base series: ${baseSeries.`▶`.joinToString { it.format(2) }} (Size: ${baseSeries.a})")

    // Extend by clamping
    val clampedSeries = baseSeries.extendByClamping()
    println("Extended by Clamping (size ${clampedSeries.a}):") // Size will be Int.MAX_VALUE
    print("  Access: [-2, -1, 0, 1, 2, 3, 4, 5] -> [")
    listOf(-2, -1, 0, 1, 2, 3, 4, 5).forEach { print("${clampedSeries.b(it).format(2)}, ") }
    println("...]")


    // Extend with default
    val defaultExtendedSeries = baseSeries.extendWithDefault(-1.0)
    println("Extended with Default (-1.0) (size ${defaultExtendedSeries.a}):")
    print("  Access: [-2, -1, 0, 1, 2, 3, 4, 5] -> [")
    listOf(-2, -1, 0, 1, 2, 3, 4, 5).forEach { print("${defaultExtendedSeries.b(it).format(2)}, ") }
    println("...]")

    // Extend empty series by clamping (should throw on access)
    val emptyClamped = emptySeries<Double>().extendByClamping()
    try {
        println("Accessing extended empty clamped series at index 0: ${emptyClamped.b(0)}")
    } catch (e: Exception) {
        println("Accessing extended empty clamped series at index 0: Caught - ${e::class.simpleName}: ${e.message}")
    }

    println("-------------------------------------------------")
}


/**
 * Main function to run the KTS Bot Refactor demonstrations.
 */
fun runKtsBotRefactorDemo() {
    println("======== KTS Bot Refactor Demo Start ========")

    val samplePortfolio = listOf(
        DemoPortfolioRow("BTC", 0.25, 0.20, 25000.0),  // Deviation: 0.25 (Harvest)
        DemoPortfolioRow("ETH", 0.15, 0.20, 15000.0),  // Deviation: -0.25 (Rebalance)
        DemoPortfolioRow("ADA", 0.10, 0.10, 10000.0),  // Deviation: 0.0 (Hold)
        DemoPortfolioRow("SOL", 0.05, 0.10, 5000.0),   // Deviation: -0.5 (Rebalance)
        DemoPortfolioRow("DOT", 0.12, 0.10, 12000.0),  // Deviation: 0.2 (Harvest)
        DemoPortfolioRow("XRP", 0.08, 0.05, 8000.0),   // Deviation: 0.6 (Harvest)
        DemoPortfolioRow("LINK",0.07, 0.07, 7000.0),   // Deviation: 0.0 (Hold)
        DemoPortfolioRow("AVAX",0.03, 0.05, 3000.0),   // Deviation: -0.4 (Rebalance)
        DemoPortfolioRow("ATOM",0.06, 0.05, 6000.0),   // Deviation: 0.2 (Harvest)
        DemoPortfolioRow("XTZ", 0.04, 0.08, 4000.0),   // Deviation: -0.5 (Rebalance)
        DemoPortfolioRow("FIL", null, 0.00, 0.0)       // Deviation: null (Hold)
    )
    demonstrateAttentionScopeOnPortfolio(samplePortfolio)
    demonstrateSparseAndExtendedHandling()

    println("========= KTS Bot Refactor Demo End =========")
}

/*
// Conceptual main, actual execution would be in a suitable Kotlin environment
// (e.g., a main.kt file with a CoroutineScope or `runBlocking` for suspend functions if any)
fun main() {
    runKtsBotRefactorDemo()
}
*/
