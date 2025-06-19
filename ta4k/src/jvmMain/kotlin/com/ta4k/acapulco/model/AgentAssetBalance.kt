package borg.trikeshed.acapulco.model

import com.binance.api.client.domain.account.AssetBalance
import org.bereft.node.config.asymmetricCryptography
import vec.macros.Pai2
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class AgentAssetBalance
constructor(
    var orderList:  List<OrderShim>? = null,
    free: Number = 1,
    asset: String,
) : AssetBalance() {
    init {
        this.free = "$free"
        this.asset = asset
    }

    val simLocked
        get() =(orderList?.sumOf{ it.amt } ?:0.0 ).also { assert (it >=0.0) }
    override fun toString(): String = "bal(${asset}, free:${free}, lock:${simLocked}${orderList?.let{"orders"+it.toList()}?:""})"

}