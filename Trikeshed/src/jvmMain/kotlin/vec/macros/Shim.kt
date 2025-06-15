// =====================================================================
// === TrikeShed/src/jvmMain/kotlin/vec/macros/Shim.kt ===
// =====================================================================
package vec.macros // Keep original package for compatibility if absolutely necessary, or move/refactor later

// Trikeshed lib imports
import borg.trikeshed.lib.Join
import borg.trikeshed.lib.Series
import borg.trikeshed.lib.j // Import Trikeshed 'j' infix

/**
 * Shim for legacy vec.macros.Pai2 -> borg.trikeshed.lib.Join
 */
typealias Pai2<A, B> = Join<A, B>

/**
 * Shim for legacy vec.macros.Vect0r -> borg.trikeshed.lib.Series
 */
typealias Vect0r<T> = Series<T>

/**
 * Shim for legacy vec.macros.Vect02_ accessors
 * Note: These might not be directly needed if calling code uses Series2 extensions.
 */
object Vect02_ {
     val <A, B> Series<Join<A, B>>.left: Series<A> get() = borg.trikeshed.lib.left // Delegate
     val <A, B> Series<Join<A, B>>.right: Series<B> get() = borg.trikeshed.lib.right // Delegate
}

/**
 * Shim for legacy 't2' infix to construct a pair, mapping to Trikeshed 'j' infix.
 */
@Deprecated("Use 'j' infix instead", ReplaceWith("this j other"), DeprecationLevel.WARNING)
infix fun <A, B> A.t2(other: B): Pai2<A, B> = this j other // Delegate to j

// Add shims for other vec.macros if they were used (e.g., combine, specific types)
// Example shim for combine if needed:
// fun <A> combine(vararg catn: Vect0r<A>): Vect0r<A> = borg.trikeshed.lib.combine(*catn)

// Shim for RowVec if used directly from vec.macros
// typealias RowVec = borg.trikeshed.cursor.RowVec // Assuming it's defined in cursor package

// Shim for _v if needed (assuming it created Vect0r)
// import borg.trikeshed.common.collections.s_ as _s_ // Alias Trikeshed series creator
// object _v {
//     operator fun <T> get(vararg t: T): Vect0r<T> = _s_[*t]
// }