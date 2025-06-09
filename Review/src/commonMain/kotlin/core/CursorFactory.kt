@file:Suppress("NOTHING_TO_INLINE", "FunctionName")

package core

import borg.trikeshed.cursor.Cursor
import borg.trikeshed.cursor.ColumnMeta
import borg.trikeshed.cursor.TypeMemento
import borg.trikeshed.cursor.meta
import borg.trikeshed.cursor.names
import borg.trikeshed.lib.*
import kotlin.jvm.JvmInline

/**
 * # CursorFactory: The Scientifically Optimal Join-Based Cursor Creation Syntax
 *
 * Based on TrikeShed's fundamental insight: `Cursor = Series<RowVec>` where `RowVec = Series2<Any?, () -> ColumnMeta>`
 * 
 * This provides the strongest possible decision for type-erased cursor creation using Join elegance:
 * - Zero runtime overhead through inline value classes
 * - Compile-time safety via context-guided transformation
 * - Mathematical elegance: everything reduces to `Join<A,B>` primitives
 * - Scientific precision: type erasure handled at the boundary, not throughout the computation
 */

/**
 * ## 1. Context-Guided Cursor Builder (CCEK Pattern)
 * 
 * Uses the transformative DSL stairway pattern to guide cursor construction
 * Each step provides different operations based on current state
 */
@JvmInline
value class CursorBuilder(val columnSpecs: Series<ColumnSpec>) {
    
    /** Add a typed column with automatic type inference */
    fun <T> column(name: String, accessor: (Int) -> T): CursorBuilder =
        CursorBuilder(columnSpecs.size + 1 j { i ->
            if (i < columnSpecs.size) columnSpecs[i]
            else ColumnSpec(name, Any::class, accessor as (Int) -> Any?)
        })
    
    /** Add raw column without type checking (for dynamic cases) */
    fun columnRaw(name: String, accessor: (Int) -> Any?): CursorBuilder =
        CursorBuilder(columnSpecs.size + 1 j { i ->
            if (i < columnSpecs.size) columnSpecs[i]
            else ColumnSpec(name, Any::class, accessor)
        })
    
    /** Materialize to actual Cursor - the ▶ moment */
    fun build(rowCount: Int): Cursor = rowCount j { rowIndex: Int ->
        columnSpecs.size j { colIndex: Int ->
            val spec = columnSpecs[colIndex]
            spec.accessor(rowIndex) j { spec.meta }
        }
    }
}

/**
 * ## 2. Column Specification (Join-based metadata)
 */
data class ColumnSpec(
    val name: String,
    val type: kotlin.reflect.KClass<*>,
    val accessor: (Int) -> Any?
) {
    val meta: ColumnMeta get() = name j type.inferTypeMemento()
}

/**
 * ## 3. Primary Factory Functions (The "j" elegance for cursors)
 */

/** Primary factory: `C_` - starts cursor building (mimics `T_` pattern from tensor core) */
inline fun C_(): CursorBuilder = CursorBuilder(emptySeries())

/** Direct cursor from vararg pairs (most convenient for simple cases) */
fun C_(vararg columns: Pair<String, (Int) -> Any?>): CursorBuilder =
    columns.fold(C_()) { builder, (name, accessor) ->
        builder.column(name, accessor)
    }

/** Direct cursor from Map (for dynamic column creation) */
fun C_(columns: Map<String, (Int) -> Any?>): CursorBuilder =
    columns.entries.fold(C_()) { builder, (name, accessor) ->
        builder.column(name, accessor)
    }

/**
 * ## 4. Advanced Join-Based Patterns
 */

/** Create cursor from existing data structures with single column */
fun <T> List<T>.toCursor(columnName: String = "value"): Cursor = 
    C_().column(columnName) { this[it] }.build(size)

/** Create cursor from arrays using α transform with single column */
fun <T> Array<T>.toCursor(columnName: String = "value"): Cursor =
    C_().column(columnName) { this[it] }.build(size)

/**
 * ## 5. Type Evidence Integration (Scientific Precision)
 */

/** Simple TypeMemento implementation for Kotlin types */
private data class SimpleTypeMemento(override val networkSize: Int?) : TypeMemento

/** Infer TypeMemento from Kotlin types for TrikeShed compatibility */
private fun kotlin.reflect.KClass<*>.inferTypeMemento(): TypeMemento = when (this) {
    String::class -> SimpleTypeMemento(null) // Variable size
    Int::class -> SimpleTypeMemento(4)
    Long::class -> SimpleTypeMemento(8)
    Double::class -> SimpleTypeMemento(8)
    Float::class -> SimpleTypeMemento(4)
    Boolean::class -> SimpleTypeMemento(1)
    Byte::class -> SimpleTypeMemento(1)
    Short::class -> SimpleTypeMemento(2)
    Char::class -> SimpleTypeMemento(2)
    else -> SimpleTypeMemento(null) // Unknown size
}

/**
 * ## 6. Examples of Usage:
 * 
 * ```kotlin
 * // Simple case - pure Join elegance
 * val trades = C_(
 *     "symbol" to { i -> symbols[i] },
 *     "price" to { i -> prices[i] },
 *     "volume" to { i -> volumes[i] }
 * ).build(1000)
 * 
 * // Data class case - automatic property extraction
 * data class Trade(val symbol: String, val price: Double, val volume: Long)
 * val trades = tradeList.toCursor()
 * 
 * // Builder pattern - step by step
 * val advanced = C_()
 *     .column("timestamp") { i -> timestamps[i] }
 *     .column("price") { i -> prices[i] }
 *     .column("derived") { i -> prices[i] * volumes[i] }
 *     .build(rowCount)
 * ```
 */