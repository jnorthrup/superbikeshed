package borg.trikeshed.acapulco

import borg.trikeshed.common.Series  // Assuming this import is correct based on project structure

interface MarketHistoryProvider {
    fun getHistory(assetKey: String, ticks: Int): Series<TickData>
}