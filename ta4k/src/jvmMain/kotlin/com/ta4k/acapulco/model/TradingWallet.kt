package com.ta4k.acapulco.model

import com.binance.api.client.BinanceApiRestClient
import com.binance.api.client.domain.account.Account
import com.binance.api.client.domain.account.AssetBalance
import borg.trikeshed.cursors.SimpleCursor
import borg.trikeshed.cursors.context.Scalar
import borg.trikeshed.cursors.io.IOMemento
import borg.trikeshed.vec.macros.*
import borg.trikeshed.vec.util._v
import com.ta4k.acapulco.CoinsAndPairings
import com.ta4k.acapulco.config.Help
import kotlinx.coroutines.runBlocking
import java.io.Closeable
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

class TradingWallet(
    private val coinsAndPairings: CoinsAndPairings,
) : ITradingWallet {
    lateinit var bookHandle: Closeable
    lateinit var tickerCloseHandle: Closeable
    lateinit var walletCloseHandle: Closeable
    lateinit var listenKey: String
    lateinit var currentAccount: Account

    override fun updateAccount(currentAccount1: Account) {
        this.currentAccount = currentAccount1
        updateWallet(currentAccount1.balances)
    }

    override val holdings = sortedMapOf<String, AssetBalance>()

    override val byValue: List<*>
        get() {
            return runBlocking {
                holdings.filter { it.value.total > 0.0 }
                    .map { (k, v) -> k to v.total to v.total * coinsAndPairings.pathValue(k, "USDT") }
                    .sortedByDescending { (_, total) -> total }
            }
        }

    fun initWallet(restClient: BinanceApiRestClient): Pai2<String, Account> {
        listenKey = restClient.startUserDataStream()
        currentAccount = restClient.account
        updateAccount(currentAccount)
        return listenKey t2 currentAccount
    }

    override fun updateWallet(balances: List<AssetBalance>) {
        balances.map {
            holdings[it.asset] = it
        }
    }
}

interface ITradingWallet {
    fun updateAccount(currentAccount: Account)
    val holdings: MutableMap<String, AssetBalance>
    val byValue: List<*>
    fun updateWallet(balances: List<AssetBalance>)
} 