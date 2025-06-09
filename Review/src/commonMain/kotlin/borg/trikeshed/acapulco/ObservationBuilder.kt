package borg.trikeshed.acapulco

import borg.trikeshed.lib.Series  // Assuming this is the correct import based on project structure

interface ObservationBuilder {
    fun buildObservation(currentTick: MarketTick, history: MarketHistoryProvider): AgentObservation
}

// Note: AgentObservation is defined in AgentInterface.kt