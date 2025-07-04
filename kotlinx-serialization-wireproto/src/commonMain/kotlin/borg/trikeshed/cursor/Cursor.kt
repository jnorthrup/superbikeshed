package borg.trikeshed.cursor

import borg.trikeshed.lib.Join

/**
 * DEPRECATED: Legacy cursor interface - superseded by trikeshed-cursor module
 * 
 * This cursor implementation is now CANCELLED per cursor policy update.
 * Use borg.trikeshed.cursor.Cursor from trikeshed-cursor module instead.
 * 
 * The canonical columnar cursor implementation provides:
 * - Type-safe column access
 * - Proven JVM-tested algorithms
 * - Meta-driven schema evolution
 * - KMP compatibility
 * 
 * @deprecated Use borg.trikeshed.cursor.Cursor from trikeshed-cursor module
 */
@Deprecated(
    "Use borg.trikeshed.cursor.Cursor from trikeshed-cursor module", 
    ReplaceWith("borg.trikeshed.cursor.Cursor", "borg.trikeshed.cursor")
)
interface Cursor {
    val a: Int // size
    val b: (Int) -> Join<Int, (Int) -> Join<Any?, () -> ColumnMeta>> // accessor function
    
    fun iterator(): Iterator<RowVec>
} 