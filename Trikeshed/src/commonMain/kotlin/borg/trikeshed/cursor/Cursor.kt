package borg.trikeshed.cursor

import borg.trikeshed.lib.Join
import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.RowVec

/**
 * Cursor interface for platform-specific implementations
 */
interface CursorInterface {
    val a: Int
    val b: (Int) -> RowVec
} 