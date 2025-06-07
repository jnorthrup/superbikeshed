package borg.trikeshed.acapulco

import borg.trikeshed.lib.Series  // Assuming this import is correct based on project structure

interface MarketHistoryProvider {
    fun getHistory(assetKey: String, ticks: Int): Series<TickData>
}