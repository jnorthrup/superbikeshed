package org.bereft.model

import cursors.io.IOMemento
import cursors.context.Scalar

enum class KLineColumn(val colName: String, val type: IOMemento) {
    OPEN_TIME("Open_time", IOMemento.IoLong),
    OPEN("Open", IOMemento.IoDouble),
    HIGH("High", IOMemento.IoDouble),
    LOW("Low", IOMemento.IoDouble),
    CLOSE("Close", IOMemento.IoDouble),
    VOLUME("Volume", IOMemento.IoDouble),
    CLOSE_TIME("Close_time", IOMemento.IoLong),
    QUOTE_ASSET_VOLUME("Quote asset volume", IOMemento.IoDouble),
    NUMBER_OF_TRADES("Number of trades", IOMemento.IoInt),
    TAKER_BUY_BASE_ASSET_VOLUME("Taker buy base asset volume", IOMemento.IoDouble),
    TAKER_BUY_QUOTE_ASSET_VOLUME("Taker buy quote asset volume", IOMemento.IoDouble),
    IGNORE("Ignore", IOMemento.IoString);

    fun toScalar(): Scalar = Scalar(type, colName)
}
