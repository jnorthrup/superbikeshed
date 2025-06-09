package com.ta4k.trikeshedutils

import borg.trikeshed.core.Series // Import from the correct, new package
import borg.trikeshed.core.j      // Import the infix constructor
import borg.trikeshed.core.size   // Import Series.size extension
import borg.trikeshed.core.get    // Import Series.get() operator extension
import borg.trikeshed.core.emptySeries // Import emptySeries from borg.trikeshed.core

/**
 * Helper function to easily create a [Series] from a [List].
 * The resulting Series will have the same size and elements as the list.
 *
 * @param T The type of elements in the list and the resulting series.
 * @return A [borg.trikeshed.core.Series<T>] wrapping the given list.
 */
fun <T> List<T>.toSeries(): Series<T> {
    if (this.isEmpty()) return emptySeries() // Use the TrikeShedCore emptySeries
    return this.size j { index ->
        // The accessor function should handle its own bounds checks if necessary,
        // but typically it's called for 0 until size-1.
        // The original TrikeShed Series typealias implies the accessor is called with valid indices.
        // Adding a check here can make the helper more robust if used carelessly,
        // but strictly speaking, the Series contract is size + accessor.
        // The 'j' infix creates a Join, and Series is Join<Int, (Int)->T>.
        // The accessor here is { index -> this[index] }
        if (index < 0 || index >= this.size) { // Defensive check
            throw IndexOutOfBoundsException("Index $index out of bounds for list of size ${this.size} when creating Series via toSeries()")
        }
        this[index]
    }
}

/**
 * Helper function to create a [Series] from a [List] of nullable elements.
 *
 * @param T The type of elements in the list and the resulting series.
 * @return A [borg.trikeshed.core.Series<T?>] wrapping the given list.
 */
fun <T> List<T?>.toNullableSeries(): Series<T?> {
    if (this.isEmpty()) return emptySeries() // Use the TrikeShedCore emptySeries
    return this.size j { index ->
        if (index < 0 || index >= this.size) { // Defensive check
            throw IndexOutOfBoundsException("Index $index out of bounds for list of size ${this.size} when creating Series via toNullableSeries()")
        }
        this[index]
    }
}

/**
 * Helper function to collect all elements of a [Series] into a [List].
 * Useful for inspections, debugging, or when a concrete collection is needed (e.g., in tests).
 *
 * @param T The type of elements in the series.
 * @return A [List<T>] containing all elements from the series.
 */
fun <T> Series<T>.toList(): List<T> {
    // The Series.size extension and Series.get() operator are from borg.trikeshed.core
    if (this.size == 0) return emptyList()
    // Consider using this.`▶`.toList() if IterableSeries is preferred and fully functional.
    // For now, direct construction:
    return List(this.size) { index -> this[index] }
}
