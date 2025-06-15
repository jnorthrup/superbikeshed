package borg.trikeshed.acapulco

interface AgentInterface {
    suspend fun decideAction(observation: AgentObservation): AgentAction
}

// Placeholder for types based on task description
typealias AgentObservation = borg.trikeshed.common.Series<*>  // Adjust as per actual implementation
typealias AgentAction = DoubleArray  // Represents AssetOutput