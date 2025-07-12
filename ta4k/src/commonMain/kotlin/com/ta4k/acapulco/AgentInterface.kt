package borg.trikeshed.acapulco

import borg.trikeshed.lib.Indexed

interface AgentInterface {
    suspend fun decideAction(observation: AgentObservation): AgentAction
}

// Placeholder for types based on task description
typealias AgentObservation = Indexed<Any>  // Using Any instead of star projection
typealias AgentAction = DoubleArray  // Represents AssetOutput