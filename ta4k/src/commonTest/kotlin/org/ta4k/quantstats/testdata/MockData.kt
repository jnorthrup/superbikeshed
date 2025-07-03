package org.ta4k.quantstats.testdata

import org.ta4k.quantstats.domain.*
// import borg.trikeshed.Indexed // Import if Indexed construction is known
// import borg.trikeshed.Join   // Import if Join construction is known for Indexed

fun createMockKlines(): List<Kline> {
    return listOf(
        Kline(
            timestamp = UnixTimestamp(1672531200000L), // 2023-01-01 00:00:00 GMT
            ohlc = OHLC(Price(100.0), Price(105.0), Price(99.0), Price(102.0)),
            volume = Volume(1000.0)
        ),
        Kline(
            timestamp = UnixTimestamp(1672617600000L), // 2023-01-02 00:00:00 GMT
            ohlc = OHLC(Price(102.0), Price(108.0), Price(101.0), Price(107.0)),
            volume = Volume(1200.0)
        ),
        Kline(
            timestamp = UnixTimestamp(1672704000000L), // 2023-01-03 00:00:00 GMT
            ohlc = OHLC(Price(107.0), Price(110.0), Price(105.0), Price(109.0)),
            volume = Volume(1100.0)
        )
    )
}

// Placeholder for creating a Trikeshed Indexed once its construction is clear
// fun createMockKlineSeries(): borg.trikeshed.Indexed<Kline> {
//     val klines = createMockKlines()
//     // Actual construction of Indexed<Kline> will depend on Join's API
//     // and how Indexed<T> = Join<Int, (Int) -> T> is instantiated.
//     // For example, if Join is data class Join<A,B>(val a: A, val b: B)
//     // return borg.trikeshed.Join(klines.size) { index -> klines[index] }
//     throw NotImplementedError("Indexed<Kline> construction depends on Join API details")
// }
