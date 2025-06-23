// =====================================================================
// === TrikeShed/src/jvmMain/kotlin/borg/trikeshed/acapulco/IntraCandleEventView.kt ===
// =====================================================================
package borg.trikeshed.acapulco // Adjusted package

import borg.trikeshed.cursor.Cursor // Type alias for Indexed<RowVec>
import borg.trikeshed.acapulco.ITradePairEventMuxer // Assuming ported
import borg.trikeshed.lib.Join // Replaces Pai2
import borg.trikeshed.lib.j // Replaces t2

class IntraCandleEventView(
    private val muxer: TradePairEventMuxer, // Requires ITradePairEventMuxer interface from Acapulco
    private val streamer: Streamer, // Requires Streamer class from Acapulco
) : CandleEventHandler<Join<Cursor, Int>>( // Use Join, CandleEventHandler needs T param
    evtSource = muxer.intra_events,
    onEvt = { (c0, intra): Join<Cursor, Int> ->
        // decorateView needs to be ported and accept Join<Cursor, Int> and TradingWallet
        val c1 = decorateView(muxer, c0 j intra, streamer.tradingWallet)
        // Process c1 if needed
    }
)
