package org.ta4k.quantstats.domain

// Assuming Join will be importable from borg.trikeshed.Join
// If it's not, this code will need adjustment once Join's definition is clear.
import borg.trikeshed.Join

@kotlin.jvm.JvmInline
value class Price(val value: Double)

@kotlin.jvm.JvmInline
value class Volume(val value: Double)

@kotlin.jvm.JvmInline
value class UnixTimestamp(val millis: Long)

data class OHLC(
    val open: Price,
    val high: Price,
    val low: Price,
    val close: Price
) {
    // Example: How one might convert to/from a Join if that's the Trikeshed pattern.
    // This is speculative without knowing Join's constructor or if Indexed<OHLC> takes OHLC directly.
    fun toJoin(): Join<Price, Join<Price, Join<Price, Price>>> {
        // Assuming Join has a constructor like: data class Join<A,B>(val first: A, val second: B)
        // This will need to be verified based on actual Join definition.
         return Join(open, Join(high, Join(low, close)))
    }

    companion object {
        fun fromJoin(join: Join<Price, Join<Price, Join<Price, Price>>>): OHLC {
            // Assuming Join has 'first' and 'second' properties.
            return OHLC(
                open = join.first,
                high = join.second.first,
                low = join.second.second.first,
                close = join.second.second.second
            )
        }
    }
}

data class Kline(
    val timestamp: UnixTimestamp,
    val ohlc: OHLC,
    val volume: Volume
)
