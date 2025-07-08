package borg.trikeshed.acapulco.model

import com.binance.api.client.domain.account.Account
import com.binance.api.client.domain.account.AssetBalance
import cursors.SimpleCursor
import vec.util.todub
import java.util.*

interface ITradingWallet {
    val holdings: SortedMap<String, AssetBalance>
    val byValue: List<*>

    fun updateAccount(currentAccount1: Account)
    fun updateWallet(balances: List<AssetBalance>)
    fun walletFree(
            mask: AssetKey, valueSymbol: String,
    ): SimpleCursor
}


//java integration fail
val AssetBalance.total: Double
    get() {
        return ((this as? AgentAssetBalance)?.run {
            gnomeAssetBalanceTotal()
        } ?: run {
            assetBalanceTotal()
        }).also { assert(it >= 0.0) }
    }

internal fun AssetBalance.assetBalanceTotal(): Double {
    val assetFree0 = todub(free).also { assert(it >= 0.0) }
    val assetFree1 = todub(locked).also { assert(it >= 0.0) }
    return assetFree0 + assetFree1
}

internal fun AgentAssetBalance.gnomeAssetBalanceTotal(): Double {
    val gnomeFree0 = todub(free)
    val gnomeFree1 = try {
        gnomeFree0.also { assert(it >= 0.0) }
    } catch (e: AssertionError) {
        e.printStackTrace()
        val d = 1.0 - gnomeFree0
        val d2 = gnomeFree0 - 1.0
        "$d$d2"
        0.0

    }
    val gnomeLocked0 = simLocked.also { assert(it >= 0.0) }
    return gnomeFree1 + gnomeLocked0
}
