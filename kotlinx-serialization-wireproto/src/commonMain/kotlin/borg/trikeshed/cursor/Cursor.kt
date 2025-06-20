package borg.trikeshed.cursor

import borg.trikeshed.lib.Join

/**
 * Interface for data cursors that provide indexed access to data
 */
interface Cursor {
    val a: Int // size
    val b: (Int) -> Join<Int, (Int) -> Join<Any?, () -> ColumnMeta>> // accessor function
    
    fun iterator(): Iterator<RowVec>
} 