package moneyfan.examples

import moneyfan.data.HistoricalDataService
import moneyfan.io.MockFileContentProvider
import moneyfan.models.Kline
import moneyfan.models.TimestampEpochMillis
import moneyfan.trikeshed.Series
import moneyfan.trikeshed.toList // Using toList and then forEach for simple iteration
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Helper function to convert a Kline's TimestampEpochMillis to a readable LocalDateTime string.
 */
fun klineTimestampToLocalDateTimeString(timestamp: TimestampEpochMillis): String {
    return Instant.fromEpochMilliseconds(timestamp.value)
        .toLocalDateTime(TimeZone.UTC)
        .toString()
}

/**
 * Demonstrates the usage of HistoricalDataService with a MockFileContentProvider.
 * Fetches and prints sample DOGE kline data for 2021 and 2022.
 */
suspend fun runHistoricalDataServiceExample() {
    println("--- Starting HistoricalDataService Example ---")

    // 1. Instantiate MockFileContentProvider
    val mockProvider = MockFileContentProvider()

    // 2. Populate with sample DOGEUSDT data
    val csvHeader = "open_time,open,high,low,close,volume,close_time,quote_asset_volume,number_of_trades,taker_buy_base_asset_volume,taker_buy_quote_asset_volume,ignore"

    // DOGEUSDT 2021 Data
    val doge2021Data = listOf(
        csvHeader,
        "1609459200000,0.004682,0.005680,0.004600,0.005000,100000000,1609545599999,500000,1000,50000000,250000,0", // 2021-01-01
        "1609545600000,0.005000,0.005200,0.004800,0.005100,120000000,1609631999999,600000,1200,60000000,300000,0", // 2021-01-02
        "1609632000000,0.005100,0.009900,0.005050,0.009700,200000000,1609718399999,1940000,2000,100000000,970000,0"  // 2021-01-03
    )
    mockProvider.addMockFile("data/DOGEUSDT_1d_2021.csv", doge2021Data)

    // DOGEUSDT 2022 Data
    val doge2022Data = listOf(
        csvHeader,
        "1640995200000,0.1700,0.1750,0.1690,0.1730,80000000,1641081599999,13840000,800,40000000,6920000,0", // 2022-01-01
        "1641081600000,0.1730,0.1780,0.1720,0.1750,85000000,1641167999999,14875000,850,42500000,7437500,0"  // 2022-01-02
    )
    mockProvider.addMockFile("data/DOGEUSDT_1d_2022.csv", doge2022Data)

    // 3. Instantiate HistoricalDataService
    val dataService = HistoricalDataService(mockProvider)

    // 4. Fetch and print DOGE klines for 2021 (Jan 1-2)
    println("\nFetching DOGEUSDT 1d for 2021-01-01 to 2021-01-02:")
    val klines2021 = dataService.getHistoricalKlines(
        symbol = "DOGEUSDT",
        interval = "1d",
        startDate = LocalDate(2021, 1, 1),
        endDate = LocalDate(2021, 1, 2)
    )
    printKlines(klines2021)

    // 5. Fetch and print DOGE klines for 2022 (Jan 1)
    println("\nFetching DOGEUSDT 1d for 2022-01-01 to 2022-01-01:")
    val klines2022 = dataService.getHistoricalKlines(
        symbol = "DOGEUSDT",
        interval = "1d",
        startDate = LocalDate(2022, 1, 1),
        endDate = LocalDate(2022, 1, 1)
    )
    printKlines(klines2022)

    // 6. Example: Fetch data that doesn't exist (different year file)
    println("\nFetching DOGEUSDT 1d for 2023-01-01 (expected empty):")
    val klines2023 = dataService.getHistoricalKlines(
        symbol = "DOGEUSDT",
        interval = "1d",
        startDate = LocalDate(2023, 1, 1),
        endDate = LocalDate(2023, 1, 1)
    )
    printKlines(klines2023)

    // 7. Example: Fetch data for a symbol that doesn't exist
    println("\nFetching NONEXISTENT_SYMBOL 1d for 2021-01-01 (expected empty):")
    val klinesNonExistent = dataService.getHistoricalKlines(
        symbol = "NONEXISTENT",
        interval = "1d",
        startDate = LocalDate(2021, 1, 1),
        endDate = LocalDate(2021, 1, 1)
    )
    printKlines(klinesNonExistent)

    // 8. Example: Test caching - fetch 2021 data again
    // This time it should be faster and retrieved from cache (though we can't measure speed here)
    // The mock provider's readFileLines would not be called for "data/DOGEUSDT_1d_2021.csv" again.
    println("\nFetching DOGEUSDT 1d for 2021-01-01 to 2021-01-01 (again, testing cache):")
    val klines2021Again = dataService.getHistoricalKlines(
        symbol = "DOGEUSDT",
        interval = "1d",
        startDate = LocalDate(2021, 1, 1),
        endDate = LocalDate(2021, 1, 1)
    )
    printKlines(klines2021Again)


    println("\n--- HistoricalDataService Example Finished ---")
}

fun printKlines(klines: Series<Kline>) {
    if (klines.isEmpty()) {
        println("No klines found or returned empty series.")
        return
    }
    // Use toList() and then forEach for simple iteration as Series itself is not directly Iterable
    // Alternatively, use klines.`play`.forEach { ... } if IterableSeries is preferred.
    klines.toList().forEachIndexed { index, kline ->
        println(
            "  Kline ${index + 1}: Time=${klineTimestampToLocalDateTimeString(kline.timestamp)}, " +
            "O=${kline.open.value}, H=${kline.high.value}, L=${kline.low.value}, C=${kline.close.value}, V=${kline.volume.value}"
        )
    }
}

/*
// Conceptual main function to run the example.
// This requires a coroutine scope to launch runHistoricalDataServiceExample.
// For example, in a main.kt:
// import kotlinx.coroutines.runBlocking
//
// fun main() = runBlocking {
//     runHistoricalDataServiceExample()
// }
*/
