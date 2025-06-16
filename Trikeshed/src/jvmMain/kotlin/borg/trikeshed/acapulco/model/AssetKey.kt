package borg.trikeshed.acapulco.model

import vec.macros.Pai2

@JvmInline
value class AssetKeyMapper(val m: MutableMap<String, AssetKey>) {
    operator fun get(k: String): AssetKey? = m.get(k.uppercase())
    operator fun plus(assetKey: AssetKey) = m.put(assetKey.binanceEventResponseSymbol, assetKey)


}

/**
 * binance specific trade symbol gymnastics
 */
@JvmInline
value class AssetKey(val symbol: String) : Pai2<String, String>, Comparable<AssetKey> {
    val div get() = symbol.indexOf('/')
    val tradeAsset get() = symbol.take(div)
    val counterAsset get() = symbol.drop(div + 1)

    companion object {
        fun of(tc: String, cc: String): AssetKey = AssetKey("$tc/$cc")
        fun of(tc: String): AssetKey = AssetKey(tc)

    }

    override fun compareTo(other: AssetKey) = symbol.compareTo(other.symbol)

    /**
     * binance requires lowercase request symbols and returns upper case.
     *
     * orders use this
     */
     val binanceEventRequestSymbol get() = (first + second).lowercase()

    /**
     * binance requires lowercase request symbols and returns upper case.
     */
    val binanceEventResponseSymbol get() = (first + second).uppercase()

    override val first: String
        get() = tradeAsset
    override val second: String
        get() = counterAsset

}

val AssetKey.component1 by AssetKey::tradeAsset
val AssetKey.component2 by AssetKey::counterAsset
