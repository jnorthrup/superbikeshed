package borg.trikeshed.acapulco.model

enum class AssetMutation(upperBound: Double) {
///OCO Price Restrictions:
//SELL: Limit Price > Last Price > Stop Price
//BUY: Limit Price < Last Price < Stop Price

    //action direction
    PredictionOnly(1.0),
    BuyTrade(1.0),
    SellTrade(1.0),

    //operations types
    AsMarket(1.0),
    AsLimit(1.0),
//    AsStopLimit,
//    AsOneCancelsOther,

    /**
     * primary limit as mult of close candle (Double.MIN_VALUE...)
     */
    AssetPriceFraction(2.0),

    /**
     * primary qty as fraction of free wallet (Double.MIN_DOUBLE..1.0)
     */
    AssetQty(1.0),

//    /**
//     * stop/profitTaking qty
//     */
//    StopLimitQty,
//
//    /**
//     * stop/profitTaking trigger  mult of close
//     */
//    StopTriggerMult,
//
//    /**
//     * lower stop trigger price for oco as mult of close
//     */
//    OcoStopTriggerMult,
//
//    /**
//     * oco counter price as mult of close
//     */
//    OcoStopLimitMult,
    ;

    companion object {
//        this might be cheaper than kotlin enum.values()
        val cachedAssetMutation by lazy { values().clone() }
    }
}