package org.bereft.model

import java.io.Serializable
import java.math.BigDecimal

data class ExchangeAsset(
    val symbol: kotlin.String,
    var displayName: kotlin.String,
    var total: BigDecimal,
    val hiloStanding: Any,
    var usdValue: BigDecimal,
    var holdingInBtc: BigDecimal,
    val last_updated: Long,
) : Serializable {

    override fun toString() =
        "$symbol/$displayName: $total : ฿${
            holdingInBtc.setScale(
                8,
                BigDecimal.ROUND_DOWN)
        }($${
            usdValue.setScale(
                2, BigDecimal.ROUND_DOWN)
        }) ↕ $hiloStanding"

}