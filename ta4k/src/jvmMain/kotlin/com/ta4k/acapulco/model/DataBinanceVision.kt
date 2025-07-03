package borg.trikeshed.acapulco.model

import borg.trikeshed.lib.IOMemento

enum class DataBinanceVision(
    val names: Vect0r<String>,
    val types: Vect0r<IOMemento>,
    val fixup: (Cursor) -> Cursor = { it },
) {
    aggtrades(
        _v["Aggregate tradeId", "Price", "Quantity", "First tradeId", "Last tradeId", "Timestamp", "Was the buyer the maker", "Was the trade the best price match"],
        _v[IOMemento.IoLong, IOMemento.IoDouble, IOMemento.IoDouble, IOMemento.IoLong, IOMemento.IoLong, IOMemento.IoString, IOMemento.IoBoolean, IOMemento.IoBoolean],
        UnixTimeRemapper.timestampFromIoLong("Timestamp")
    ),
    klines(
        _v["Open_time", "Open", "High", "Low", "Close", "Volume", "Close_time", "Quote asset volume", "Number of trades", "Taker buy base asset volume", "Taker buy quote asset volume", "Ignore"],
        _v[IOMemento.IoString, IOMemento.IoDouble, IOMemento.IoDouble, IOMemento.IoDouble, IOMemento.IoDouble, IOMemento.IoDouble,
                IOMemento.IoString, IOMemento.IoDouble, IOMemento.IoInt, IOMemento.IoDouble, IOMemento.IoDouble, IOMemento.IoString],
        { c -> c.takeIf { it.size > 0 }?.let { UnixTimeRemapper.timestampFromIoLong("Open_time", "Close_time")(c[-"Ignore"]) } ?: c }),

    trades(
        _v["trade Id", "price", "qty", "quoteQty", "time", "isBuyerMaker", "isBestMatch"],
        _v[IOMemento.IoLong, IOMemento.IoDouble, IOMemento.IoDouble, IOMemento.IoDouble, IOMemento.IoString, IOMemento.IoBoolean, IOMemento.IoBoolean],
        UnixTimeRemapper.timestampFromIoLong("time")
    );
}
