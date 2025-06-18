package moneyfan.examples

import moneyfan.models.Kline
import moneyfan.models.Price
import moneyfan.models.Volume
import moneyfan.trikeshed.Series
import moneyfan.trikeshed.Series2
import moneyfan.trikeshed.α // For the map-like transform operator
import moneyfan.trikeshed.j // For Series construction (Int.j) and Join creation (A.j(B))
import moneyfan.trikeshed.toList // For verification/display if needed
// import moneyfan.trikeshed.left // Not strictly needed for these examples but good for context
// import moneyfan.trikeshed.right // Not strictly needed for these examples but good for context

/**
 * Contains example functions demonstrating data processing on `Series<Kline>`
 * using the replicated TrikeShed API operators like `α` (alpha/map) and `j` (join/construct).
 */
object SeriesDataProcessingExamples {

    /**
     * Extracts the closing prices from a Series of Klines.
     *
     * @param klines A Series of Kline objects.
     * @return A Series of Price objects, representing the closing price of each Kline.
     */
    fun extractClosePrices(klines: Series<Kline>): Series<Price> {
        // The `α` operator (alpha) is used here as a map-like transformation.
        // It iterates through each Kline in the input series and applies the lambda,
        // which extracts the `close` property (a Price object).
        return klines α { it.close }
    }

    /**
     * Calculates the typical price for each Kline in a Series.
     * Typical Price = (High + Low + Close) / 3.
     *
     * @param klines A Series of Kline objects.
     * @return A Series of Price objects, representing the typical price for each Kline.
     */
    fun calculateTypicalPrices(klines: Series<Kline>): Series<Price> {
        // Again, `α` is used to transform each Kline.
        // The lambda calculates the typical price using the high, low, and close values
        // of the Kline. The result is wrapped in a `Price` value class instance.
        return klines α { kline ->
            val typicalValue = (kline.high.value + kline.low.value + kline.close.value) / 3.0
            Price(typicalValue)
        }
    }

    /**
     * Joins the closing price and volume of each Kline into a Series of pairs (Series2).
     *
     * @param klines A Series of Kline objects.
     * @return A Series2<Price, Volume> (which is Series<Join<Price, Volume>>),
     *         where each element is a Join pair of (closePrice, volume) for a Kline.
     */
    fun joinClosePriceAndVolume(klines: Series<Kline>): Series2<Price, Volume> {
        // To create a Series of Joins (Series2), we use the `Int.j` factory method
        // for Series construction. `klines.a` gives the size of the input series.
        // For each index, we access the corresponding Kline from the input series (`klines.b(index)` or `klines[index]`)
        // and then create a Join pair of its `close` price and `volume` using the `A.j(B)` infix function.
        // The result is `Series<Join<Price, Volume>>`, which is typealiased as `Series2<Price, Volume>`.
        return klines.a j { index ->
            val kline = klines.b(index) // or klines[index]
            kline.close j kline.volume
        }
    }

    // --- Main function for simple demonstration (conceptual) ---
    // This would typically be in a test file or a separate main execution context.
    /*
    fun main() {
        // Create some dummy Kline data for examples
        val dummyKlines = listOf(
            Kline(TimestampEpochMillis(1000L), Price(10.0), Price(12.0), Price(9.0), Price(11.0), Volume(100.0), TimestampEpochMillis(1060L), Volume(1100.0), 10, Volume(50.0), Volume(550.0), null),
            Kline(TimestampEpochMillis(2000L), Price(11.0), Price(13.0), Price(10.0), Price(12.0), Volume(120.0), TimestampEpochMillis(2060L), Volume(1320.0), 12, Volume(60.0), Volume(660.0), null),
            Kline(TimestampEpochMillis(3000L), Price(12.0), Price(14.0), Price(11.0), Price(13.0), Volume(110.0), TimestampEpochMillis(3060L), Volume(1330.0), 11, Volume(55.0), Volume(605.0), null)
        ).toSeries()

        println("Original Klines:")
        dummyKlines.play.forEach { println(it) }

        println("\nExtracted Close Prices:")
        val closePrices = extractClosePrices(dummyKlines)
        closePrices.play.forEach { println(it.value) } // Assuming Price has a meaningful toString or access to value

        println("\nCalculated Typical Prices:")
        val typicalPrices = calculateTypicalPrices(dummyKlines)
        typicalPrices.play.forEach { println(it.value) }

        println("\nJoined Close Price and Volume:")
        val closeAndVolume = joinClosePriceAndVolume(dummyKlines)
        closeAndVolume.play.forEach { join -> println("Close: ${join.a.value}, Volume: ${join.b.value}") }

        // Example of accessing left/right from Series2
        // println("\nJoined Close (left):")
        // closeAndVolume.left.play.forEach { println(it.value) }
        // println("\nJoined Volume (right):")
        // closeAndVolume.right.play.forEach { println(it.value) }
    }
    */
    // Note: To run main, KlineModels.kt and Trikeshed files (Series.kt, Join.kt, Series2.kt)
    // would need to be accessible in the classpath.
    // The `toSeries()` extension for List also needs to be available.
    // The `play` operator provides an Iterable wrapper.
}
