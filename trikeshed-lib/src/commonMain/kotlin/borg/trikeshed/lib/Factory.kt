package borg.trikeshed.lib

/**
 * Factory functions for creating core TrikeShed types.
 * This helps with type inference and provides a clear, centralized way to construct these objects.
 */

/**
 * Creates a Join object. This is the primary factory for all Join-based types.
 */
fun <A, B> makeJoin(a: A, b: B): Join<A, B> = a j b

/**
 * Creates an Indexed<T> object.
 */
fun <T> makeIndexed(size: Int, getter: (Int) -> T): Indexed<T> = size j getter

/**
 * Creates an empty Indexed<T> object.
 */
fun <T> emptyIndexed(): Indexed<T> = makeIndexed(0) { throw IndexOutOfBoundsException("Cannot access elements of an empty Indexed collection.") }
