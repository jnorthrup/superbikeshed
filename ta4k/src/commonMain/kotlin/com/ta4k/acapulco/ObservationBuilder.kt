package borg.trikeshed.acapulco

import borg.trikeshed.common.Series  // Assuming this is the correct import based on project structure

interface ObservationBuilder {
    fun buildObservation(currentTick: MarketTick, history: MarketHistoryProvider): AgentObservation
}

// Define AgentObservation as per the task; using a placeholder based on description
typealias AgentObservation = Series<RowVec>  // Adjust RowVec as needed based on existing code

// Note: RowVec is not defined here; assume it's from existing project files