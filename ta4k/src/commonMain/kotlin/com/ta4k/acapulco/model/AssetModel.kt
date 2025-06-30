package com.ta4k.acapulco.model

import com.binance.api.client.domain.event.BookTickerEvent
import borg.trikeshed.cursors.Cursor
import borg.trikeshed.vec.macros.combine
import borg.trikeshed.vec.macros.t2
import borg.trikeshed.vec.macros.toVect0r
import java.nio.channels.FileChannel
import java.time.Instant
import java.util.*

class AssetModel(
    val tradeSymbol: AssetKey,
    vararg slabs: Cursor,
) {
    operator fun component1() = tradeSymbol
    operator fun component2() = episodes

    var book: BookTickerEvent = BookTickerEvent()

    val episodes: MutableList<Cursor>

    init {
        this.episodes = slabs.toMutableList()
    }

    fun view(): Cursor = combine(episodes.toVect0r())
    fun viewLatest(lastCount: Int = -1) =
        if (episodes.size == lastCount)
            null else
            episodes.size t2 view()

    companion object {
        var hidden: MutableSet<AssetKey> = linkedSetOf()
        val assetOracle: MutableMap<AssetKey, AssetModel> by lazy { sortedMapOf() }
        fun push(key: AssetKey, vararg m: Cursor) {
            if (assetOracle.containsKey(key))
                assetOracle[key]!!.episodes.addAll(m)
            else
                assetOracle[key] = AssetModel(key).also { it.episodes.addAll(m) }
        }

        @JvmStatic
        public operator fun get(key: AssetKey) = assetOracle[key]
        val leakThese: LinkedList<FileChannel?> = LinkedList()
    }
}

interface TimeProvider {
    fun next(): Instant
    fun current(): Instant
} 