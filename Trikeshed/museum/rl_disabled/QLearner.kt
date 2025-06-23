package borg.trikeshed.rl

import borg.trikeshed.lib.*
import kotlin.random.Random

/**
 * Q-learning implementation using TrikeShed data structures
 */
class QLearner<S, A>(
    private val learningRate: Double = 0.1,
    private val discountFactor: Double = 0.95,
    private val epsilon: Double = 0.1
) : Agent<S, A> {
    
    // Q-table using TrikeShed: state-action pairs to Q-values  
    private var qTable: Indexed<Join<Join<S, A>, Double>> = 0 j { (null as S j null as A) j 0.0 }
    private var qTableSize = 0
    
    override fun selectAction(state: S): A {
        // ε-greedy action selection
        if (Random.nextDouble() < epsilon) {
            // Random exploration - this is a placeholder, needs action space definition
            return getRandomAction(state)
        } else {
            // Greedy exploitation - select action with highest Q-value
            return getBestAction(state)
        }
    }

    override fun learn(state: S, action: A, reward: Double, nextState: S, done: Boolean) {
        val currentQ = getQValue(state, action)
        val maxNextQ = if (done) 0.0 else getMaxQValue(nextState)
        
        // Q-learning update: Q(s,a) = Q(s,a) + α[r + γ max Q(s',a') - Q(s,a)]
        val newQ = currentQ + learningRate * (reward + discountFactor * maxNextQ - currentQ)
        
        setQValue(state, action, newQ)
    }
    
    private fun getQValue(state: S, action: A): Double {
        for (i in 0 until qTable.a) {
            val entry = qTable.b(i)
            val stateAction = entry.a
            if (stateAction.a == state && stateAction.b == action) {
                return entry.b
            }
        }
        return 0.0 // Default Q-value for unseen state-action pairs
    }
    
    private fun setQValue(state: S, action: A, qValue: Double) {
        // Update existing entry or add new one
        val stateActionPair = state j action
        
        // Check if entry exists
        for (i in 0 until qTable.a) {
            val entry = qTable.b(i)
            if (entry.a.a == state && entry.a.b == action) {
                // Update existing entry
                qTable = qTable.a j { idx -> 
                    if (idx == i) stateActionPair j qValue else qTable.b(idx)
                }
                return
            }
        }
        
        // Add new entry
        qTable = (qTableSize + 1) j { idx ->
            if (idx == qTableSize) stateActionPair j qValue
            else if (idx < qTableSize) qTable.b(idx)
            else (null as S j null as A) j 0.0
        }
        qTableSize++
    }
    
    private fun getMaxQValue(state: S): Double {
        var maxQ = Double.NEGATIVE_INFINITY
        for (i in 0 until qTable.a) {
            val entry = qTable.b(i)
            if (entry.a.a == state && entry.b > maxQ) {
                maxQ = entry.b
            }
        }
        return if (maxQ == Double.NEGATIVE_INFINITY) 0.0 else maxQ
    }
    
    private fun getBestAction(state: S): A {
        var bestAction: A? = null
        var maxQ = Double.NEGATIVE_INFINITY
        
        for (i in 0 until qTable.a) {
            val entry = qTable.b(i)
            if (entry.a.a == state && entry.b > maxQ) {
                maxQ = entry.b
                bestAction = entry.a.b
            }
        }
        
        return bestAction ?: getRandomAction(state)
    }
    
    private fun getRandomAction(state: S): A {
        // This is a placeholder - in practice, you'd need to define the action space
        // For now, return the first action found in the Q-table for this state
        for (i in 0 until qTable.a) {
            val entry = qTable.b(i)
            if (entry.a.a == state) {
                return entry.a.b
            }
        }
        // Fallback - this should be properly implemented based on domain
        throw IllegalStateException("No actions available for state $state")
    }
}