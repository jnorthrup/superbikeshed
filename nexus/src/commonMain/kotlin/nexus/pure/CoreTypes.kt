package nexus.pure

/**
 * Self-contained core types for pure functional nexus to avoid circular dependencies
 */

// Basic collections using Array-based implementation
typealias Indexed<T> = Array<T>

// Convert array to our indexed type
fun <T> Array<T>.toSeries(): Indexed<T> = this

// Convert list to our indexed type
fun <T> List<T>.toTypedArray(): Array<T> = this.toTypedArray()

// Extension functions for Indexed
fun <T> Indexed<T>.size(): Int = size
fun <T> Indexed<T>.isEmpty(): Boolean = isEmpty()
fun <T> Indexed<T>.isNotEmpty(): Boolean = isNotEmpty()
fun <T> Indexed<T>.first(): T = first()
fun <T> Indexed<T>.firstOrNull(): T? = firstOrNull()
fun <T> Indexed<T>.last(): T = last()
fun <T> Indexed<T>.toList(): List<T> = toList()
fun <T> Indexed<T>.random(): T = random()

// Join type - simple data class
data class Join<A, B>(val a: A, val b: B)

// Infix constructor for Join
infix fun <A, B> A.j(b: B): Join<A, B> = Join(this, b)

// Either type for error handling
sealed class Either<out L, out R> {
    data class Left<L>(val value: L) : Either<L, Nothing>()
    data class Right<R>(val value: R) : Either<Nothing, R>()
    
    companion object {
        fun <L> left(value: L): Either<L, Nothing> = Left(value)
        fun <R> right(value: R): Either<Nothing, R> = Right(value)
    }
}

// Extensions for Either
fun <L, R, R2> Either<L, R>.map(f: (R) -> R2): Either<L, R2> = when (this) {
    is Either.Left -> this
    is Either.Right -> Either.right(f(value))
}

fun <L, R, R2> Either<L, R>.flatMap(f: (R) -> Either<L, R2>): Either<L, R2> = when (this) {
    is Either.Left -> this
    is Either.Right -> f(value)
}

fun <L, R> Either<L, R>.getOrElse(default: () -> R): R = when (this) {
    is Either.Left -> default()
    is Either.Right -> value
}

fun <L, R> Either<L, R>.orElse(other: () -> Either<L, R>): Either<L, R> = when (this) {
    is Either.Left -> other()
    is Either.Right -> this
}

// Helper functions for creating indexed collections
fun <T> emptyArray(): Array<T> = arrayOf()
fun <T> arrayOf(vararg elements: T): Array<T> = arrayOf(*elements)

// Series type alias for backwards compatibility
// typealias Series<T> = Indexed<T> // EXTINCT per CLAUDE.md Series Type Extinction Policy