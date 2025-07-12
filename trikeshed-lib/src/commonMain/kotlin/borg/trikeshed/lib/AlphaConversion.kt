package borg.trikeshed.lib

/**
 * Alpha Conversion (α) - Lambda calculus transformation
 * 
 * (λx.M[x]) → (λy.M[y])        α-conversion
 * https://en.wikipedia.org/wiki/Lambda_calculus
 *
 * In Kotlin terms:
 * - λ is a lambda expression 
 * - M is a function
 * - '.' is the body of the lambda
 * 
 * The function M is the receiver of the extension function and 
 * the lambda expression is the argument.
 * 
 * The simplest possible Kotlin example of λx.M[x] is:
 * `{ x -> M(x) }` 
 * Making the delta symbol into lambda braces, x into a parameter,
 * and M(x) into the body.
 */



// Alias for alpha conversion


// Array alpha conversion (type erasure forces inlining)
inline infix fun <X, C> Array<X>.α(crossinline xform: (X) -> C): Indexed<C> = 
    size j { i: Int -> xform(this[i]) }

// Iterable alpha conversion
infix fun <X, C, Subject : Iterable<X>> Subject.α(xform: (X) -> C) = object : Iterable<C> {
    override fun iterator(): Iterator<C> = object : Iterator<C> {
        val iter: Iterator<X> = this@α.iterator()
        override fun hasNext(): Boolean = iter.hasNext()
        override fun next(): C = xform(iter.next())
    }
}

// Extension functions in the spirit of Indexed

/**
 * Provides unbounded access to first and last rows beyond the existing bounds
 */
val <T> Indexed<T>.infinite: Indexed<T>
    get() = Int.MAX_VALUE j { x: Int ->
        this.b(
            when {
                x < 0 -> 0
                a <= x -> a.dec()
                else -> x
            }
        )
    }

/**
 * Enum-driven domain-specific MetaSeries controller
 * Enums can drive domain-specific MetaSeries controllers for type-safe domain operations
 */
typealias EnumMetaSeries<E, T> = MetaSeries<E, T>

/**
 * Create an enum-driven MetaSeries controller
 */
fun <E : Enum<E>, T> E.metaSeries(domainOperation: (E) -> T): EnumMetaSeries<E, T> = 
    this j domainOperation

/**
 * Domain-specific indexing through enum-driven MetaSeries
 */
fun <S, E : Enum<E>> Indexed<S>.getByEnum(e: E): S = b(e.ordinal)

/**
 * Convert Indexed to List
 */
fun <T> Indexed<T>.toList(): AbstractList<T> = object : AbstractList<T>() {
    override val size: Int = a
    override fun get(index: Int): T = b(index)
}

// Specialized toArray conversions
fun Indexed<Byte>.toByteArray(): ByteArray = ByteArray(a, b)
fun Indexed<Char>.toCharArray(): CharArray = CharArray(a, b)
fun Indexed<Boolean>.toBooleanArray(): BooleanArray = BooleanArray(a, b)
fun Indexed<Long>.toLongArray(): LongArray = LongArray(a, b)
fun Indexed<Float>.toFloatArray(): FloatArray = FloatArray(a, b)
fun Indexed<Double>.toDoubleArray(): DoubleArray = DoubleArray(a, b)
fun Indexed<Short>.toShortArray(): ShortArray = ShortArray(a, b)

inline fun <reified T> Indexed<T>.toArray(): Array<T> = Array(a, b)

// Array to Indexed conversion



/**
 * Map operation in the spirit of alpha conversion
 */
inline infix fun <T, R> Indexed<T>.map(crossinline transform: (T) -> R): Indexed<R> = 
    this α transform

/**
 * Filter operation returning Indexed
 */
inline fun <T> Indexed<T>.filter(predicate: (T) -> Boolean): Indexed<T> {
    val filtered = mutableListOf<T>()
    for (i in 0 until a) {
        val item = b(i)
        if (predicate(item)) filtered.add(item)
    }
    return filtered.size j filtered::get
}

/**
 * FlatMap operation
 */
inline fun <T, R> Indexed<T>.flatMap(transform: (T) -> Indexed<R>): Indexed<R> {
    val results = mutableListOf<R>()
    for (i in 0 until a) {
        val mapped = transform(b(i))
        for (j in 0 until mapped.a) {
            results.add(mapped.b(j))
        }
    }
    return results.size j results::get
}

/**
 * Fold operation
 */
inline fun <T, R> Indexed<T>.fold(initial: R, operation: (acc: R, T) -> R): R {
    var accumulator = initial
    for (i in 0 until a) {
        accumulator = operation(accumulator, b(i))
    }
    return accumulator
}

/**
 * Reduce operation
 */
inline fun <T> Indexed<T>.reduce(operation: (acc: T, T) -> T): T {
    if (a == 0) throw UnsupportedOperationException("Empty indexed cannot be reduced")
    var accumulator = b(0)
    for (i in 1 until a) {
        accumulator = operation(accumulator, b(i))
    }
    return accumulator
}

/**
 * Zip two Indexed collections
 */
infix fun <T, R> Indexed<T>.zip(other: Indexed<R>): Indexed<Join<T, R>> {
    val size = minOf(a, other.a)
    return size j { i -> b(i) j other.b(i) }
}

/**
 * Take first n elements
 */
fun <T> Indexed<T>.take(n: Int): Indexed<T> {
    val size = minOf(n, a)
    return size j b
}

/**
 * Drop first n elements
 */
fun <T> Indexed<T>.drop(n: Int): Indexed<T> {
    val remaining = maxOf(0, a - n)
    return remaining j { i -> b(i + n) }
}

/**
 * Slice operation
 */
fun <T> Indexed<T>.slice(range: IntRange): Indexed<T> {
    val start = maxOf(0, range.first)
    val end = minOf(a, range.last + 1)
    val size = maxOf(0, end - start)
    return size j { i -> b(i + start) }
}

/**
 * Reverse the indexed collection
 */
fun <T> Indexed<T>.reversed(): Indexed<T> = a j { i -> b(a - 1 - i) }

/**
 * Check if all elements match predicate
 */
inline fun <T> Indexed<T>.all(predicate: (T) -> Boolean): Boolean {
    for (i in 0 until a) {
        if (!predicate(b(i))) return false
    }
    return true
}

/**
 * Check if any element matches predicate
 */
inline fun <T> Indexed<T>.any(predicate: (T) -> Boolean): Boolean {
    for (i in 0 until a) {
        if (predicate(b(i))) return true
    }
    return false
}

/**
 * Find first element matching predicate
 */
inline fun <T> Indexed<T>.find(predicate: (T) -> Boolean): T? {
    for (i in 0 until a) {
        val item = b(i)
        if (predicate(item)) return item
    }
    return null
}

/**
 * Partition into two indexed collections
 */
inline fun <T> Indexed<T>.partition(predicate: (T) -> Boolean): Join<Indexed<T>, Indexed<T>> {
    val first = mutableListOf<T>()
    val second = mutableListOf<T>()
    for (i in 0 until a) {
        val item = b(i)
        if (predicate(item)) first.add(item) else second.add(item)
    }
    return (first.size j { i: Int -> first[i] }) j (second.size j { i: Int -> second[i] })
}

// Canonical conversion utilities
fun ByteArray.toIndexed(): Indexed<Byte> = size j { this[it] }
fun IntArray.toIndexed(): Indexed<Int> = size j { this[it] }
fun Indexed<Int>.toIntArray(): IntArray = IntArray(a) { b(it) } 