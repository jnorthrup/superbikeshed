package com.ta4k.acapulco.model

import borg.trikeshed.cursor.Cursor
import borg.trikeshed.cursor.calendar.UnixTimeRemapper
import borg.trikeshed.vec.macros.size
import borg.trikeshed.isam.meta.IOMemento
import borg.trikeshed.lib.Indexed

enum class DataBinanceVision(
    val names: Indexed<String>,
    val types: Indexed<IOMemento>,
    val fixup: (Cursor) -> Cursor = { it },
) {
    aggtrades(
        Series(8) { i -> arrayOf("Aggregate tradeId", "Price", "Quantity", "First tradeId", "Last tradeId", "Timestamp", "Was the buyer the maker", "Was the trade the best price match")[i] },
        Series(8) { i -> arrayOf(IOMemento.IoLong, IOMemento.IoDouble, IOMemento.IoDouble, IOMemento.IoLong, IOMemento.IoLong, IOMemento.IoString, IOMemento.IoBoolean, IOMemento.IoBoolean)[i] },
        { c -> c.takeIf { it.size > 0 }?.let { UnixTimeRemapper.timestampFromIoLong("Timestamp")(c) } ?: c }
    ),
    klines(
        Series(12) { i -> arrayOf("Open_time", "Open", "High", "Low", "Close", "Volume", "Close_time", "Quote asset volume", "Number of trades", "Taker buy base asset volume", "Taker buy quote asset volume", "Ignore")[i] },
        Series(12) { i -> arrayOf(IOMemento.IoString, IOMemento.IoDouble, IOMemento.IoDouble, IOMemento.IoDouble, IOMemento.IoDouble, IOMemento.IoDouble,
                IOMemento.IoString, IOMemento.IoDouble, IOMemento.IoInt, IOMemento.IoDouble, IOMemento.IoDouble, IOMemento.IoString)[i] },
        { c -> c.takeIf { it.size > 0 }?.let { UnixTimeRemapper.timestampFromIoLong("Open_time", "Close_time")(c) } ?: c }),

    trades(
        Series(7) { i -> arrayOf("trade Id", "price", "qty", "quoteQty", "time", "isBuyerMaker", "isBestMatch")[i] },
        Series(7) { i -> arrayOf(IOMemento.IoLong, IOMemento.IoDouble, IOMemento.IoDouble, IOMemento.IoDouble, IOMemento.IoString, IOMemento.IoBoolean, IOMemento.IoBoolean)[i] },
        { c -> c.takeIf { it.size > 0 }?.let { UnixTimeRemapper.timestampFromIoLong("time")(c) } ?: c }
    );
} 