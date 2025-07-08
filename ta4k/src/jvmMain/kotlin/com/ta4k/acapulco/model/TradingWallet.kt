package borg.trikeshed.acapulco.model

import com.binance.api.client.BinanceApiRestClient
import com.binance.api.client.domain.account.Account
import com.binance.api.client.domain.account.AssetBalance
import cursors.SimpleCursor
import cursors.context.Scalar
import cursors.io.IOMemento
import kotlinx.coroutines.runBlocking
import org.bereft.CoinsAndPairings
import org.bereft.node.config.Help
import vec.macros.*
import vec.util._v
import java.io.Closeable
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

class TradingWallet(
    /**this should be on main key*/
    internal val coinsAndPairings: CoinsAndPairings,
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

    val symbolSpeedball by lazy {
        (/*Help.walletSymbols.value +"+" +*/ Help.tickerAssets.value).split("([\t ,]|\\s)+".toRegex())
            .toSortedSet().toTypedArray()
    }

    override fun walletFree(
        mask: AssetKey, valueSymbol: String,
    ): SimpleCursor {
        val (tc, cc) = mask

        val colNames = (AssetModel.assetOracle.keys.let { it.map { it.tradeAsset } + it.map { it.component1() } }
            .toSortedSet() + tc + cc + valueSymbol).toList()
        val negInf = colNames - tc - cc - valueSymbol

        val up = holdings[tc]?.free?.toDoubleOrNull() ?: 0.0
        val down = -(holdings[cc]?.free?.toDoubleOrNull() ?: 0.0)
        return SimpleCursor(colNames α { Scalar.Scalar(IOMemento.IoDouble, it) },
            _v[colNames α {
                when (it) {
                    tc -> up
                    cc -> down
                    valueSymbol -> 1e-10
                    else -> -1e-10
                }
            }]
        )
    }


}
