// =====================================================================
// === TrikeShed/src/jvmMain/kotlin/borg/trikeshed/acapulco/ITradePairEventMuxer.kt ===
// =====================================================================
package borg.trikeshed.acapulco

import borg.trikeshed.cursor.Cursor // Type alias for Indexed<RowVec>
import borg.trikeshed.lib.Join // Replaces Pai2
import kotlinx.coroutines.flow.MutableSharedFlow

interface ITradePairEventMuxer {
    val intra_events: MutableSharedFlow<Join<Cursor, Int>> // Use Join instead of Pai2
}