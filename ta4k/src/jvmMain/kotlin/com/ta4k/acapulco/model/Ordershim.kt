package borg.trikeshed.acapulco.model


class OrderShim(
    val assetKey: AssetKey,
    val price: Double,
    val isBuy: Boolean,
    val amt: Double,
    val created: Int,
) {
    override fun toString(): String = "Order(${assetKey.symbol} @$price, ${if(isBuy ) "BUY" else "SELL"} amt:$amt)"


}
