package com.ta4k.acapulco.model

import borg.trikeshed.vec.macros.Pai2

@JvmInline
value class AssetKeyMapper(val m: MutableMap<String, AssetKey>) {
    operator fun get(k: String): AssetKey? = m.get(k.uppercase())
    operator fun plus(assetKey: AssetKey) = m.put(assetKey.binanceEventResponseSymbol, assetKey)
}

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

    val binanceEventRequestSymbol get() = (first + second).lowercase()
    val binanceEventResponseSymbol get() = (first + second).uppercase()

    override val first: String get() = tradeAsset
    override val second: String get() = counterAsset
}

val AssetKey.component1 by AssetKey::tradeAsset
val AssetKey.component2 by AssetKey::counterAsset 