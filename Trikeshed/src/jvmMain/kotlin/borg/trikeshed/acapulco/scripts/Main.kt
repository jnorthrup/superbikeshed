// =====================================================================
// === TrikeShed/src/jvmMain/kotlin/borg/trikeshed/acapulco/scripts/Main.kt ===
// =====================================================================
package borg.trikeshed.acapulco.scripts

import borg.trikeshed.cursor.Cursor // Type alias for Series<RowVec>
import borg.trikeshed.cursor.RowVec // Type alias for Series2<Any?, () -> ColumnMeta>
import borg.trikeshed.cursor.SimpleCursor // Assuming ported or replaced
import borg.trikeshed.acapulco.rl.BinanceRlAdapter // Assuming ported
import borg.trikeshed.rl.QLearner // Assuming ported/available
import borg.trikeshed.lib.* // Trikeshed lib
import borg.trikeshed.common.collections.s_ // Use s_ for Series

/**
 * Entry point for the Acapulco Binance RL adapter using Trikeshed types.
 */
fun main(args: Array<String>) {
    println("Starting Acapulco Binance RL adapter (Trikeshed port)...")

    // TODO: Replace with actual cursor source for Binance simulator (e.g., IsamDataFile)
    // Placeholder: Creating an empty cursor
    val emptyCursor: Cursor = emptySeries() // An empty Series<RowVec>
    val cursor: Cursor = emptyCursor // Use the empty cursor for now

    // Ensure Agent state type matches Cursor row type (RowVec)
    val agent = QLearner<RowVec, Any>() // Use RowVec for State S
    val env = BinanceRlAdapter(cursor, agent) // Use RowVec for State S

    try {
        var state = env.reset() // Initial state is RowVec
        var done = false
        var steps = 0
        val maxSteps = 1000 // Add a step limit for safety

        while (!done && steps < maxSteps) {
            @Suppress("UNCHECKED_CAST")
            val action = agent.selectAction(state as RowVec) // Action selection based on RowVec state
            val (nextState, reward, finished) = env.step(action) // Step returns RowVec
            agent.learn(state, action, reward, nextState, finished) // Learn uses RowVec states
            state = nextState
            done = finished
            steps++
            if (steps % 100 == 0) {
                 println("Step: $steps, State: ${state.left.take(5).toList()}...")
            }
        }

        if (steps == maxSteps) {
             println("Run stopped after reaching max steps ($maxSteps).")
        } else {
             println("Run complete after $steps steps.")
        }

    } catch (e: IllegalStateException) {
        System.err.println("Error starting RL environment: ${e.message}")
        println("Ensure the cursor/data source is not empty.")
    } catch (e: NotImplementedError) {
         System.err.println("Error: QLearner methods are not implemented: ${e.message}")
         println("Replace QLearner with a functional agent.")
    } catch (e: Exception) {
        System.err.println("An unexpected error occurred during the run: ${e.message}")
        e.printStackTrace()
    }
}