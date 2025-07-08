package org.bereft.model

import com.google.gson.internal.bind.util.ISO8601Utils
import org.bereft.node.config.MeshNode
import org.bereft.runtime.couchUtil.has_id
import java.io.Serializable
import java.math.BigDecimal
import java.util.*

data class ExchangeSummary(
    val xname: String,
    val btcPriceInUsd: BigDecimal,
    val assets: List<ExchangeAsset>,
    val apiKey: String,
    val timestamp: Long = System.currentTimeMillis(),
    override val _id: String? = xname + ":" + apiKey + ":"
            + ISO8601Utils.format(Date(timestamp)),
) : Serializable, has_id {
    override fun toString(): String = MeshNode.gson.toJson(this)
    val usdValue get() = assets.sumByDouble { it.usdValue.toDouble() }

    val btcValue get() = assets.sumByDouble { it.holdingInBtc.toDouble() }

}