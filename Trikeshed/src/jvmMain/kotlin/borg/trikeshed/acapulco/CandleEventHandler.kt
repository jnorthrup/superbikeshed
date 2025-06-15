// =====================================================================
// === TrikeShed/src/jvmMain/kotlin/borg/trikeshed/acapulco/CandleEventHandler.kt ===
// =====================================================================
package borg.trikeshed.acapulco

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * for the purposes of RL, this would do the ordering, the doping, and the pancacke representation
 */
open class CandleEventHandler<T>(
    evtSource: SharedFlow<T>,
    val onEvt: suspend (event: T) -> Unit,
) {
    init {
        // Consider if runBlocking is appropriate here. Maybe launch in a provided scope?
        runBlocking { launch { start(evtSource) } }
    }

    suspend fun start(evtSource: SharedFlow<T>) {
        evtSource.collect { onEvt(it) }
    }
}