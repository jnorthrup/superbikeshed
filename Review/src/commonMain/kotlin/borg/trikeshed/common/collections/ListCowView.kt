package borg.trikeshed.common.collections

import borg.trikeshed.core.Series
import borg.trikeshed.core.emptySeries
import borg.trikeshed.core.j // For Pair construction / SeriesData
import borg.trikeshed.core.toSeries // For converting List to Series
import borg.trikeshed.core.get // Extension operator for Series
import borg.trikeshed.core.size // Extension property for Series

/**
 * A view that presents a mutable list interface (`AbstractMutableList<T>`)
 * by internally managing an immutable `borg.trikeshed.core.Series<T>`.
 * Each mutation operation results in a new `Series<T>` instance being created
 * and assigned to the internal store, effectively behaving like copy-on-write
 * at the series level. This class is not thread-safe for concurrent modifications.
 */
class ListCowView<T>(initialSeries: Series<T>) : AbstractMutableList<T>() {

    private var series: Series<T> = initialSeries

    // Secondary constructor for initialising with a List
    constructor(initialList: List<T>) : this(initialList.toSeries())

    // Secondary constructor for default empty initialisation
    constructor() : this(emptySeries<T>())

    override fun get(index: Int): T {
        if (index < 0 || index >= series.size) throw IndexOutOfBoundsException("Index: $index, Size: ${series.size}")
        return series[index] // Uses Series.get extension
    }

    override val size: Int
        get() = series.size // Uses Series.size extension

    override fun add(index: Int, element: T) {
        // AbstractMutableList.add(index, element) calls this method.
        // It also handles modCount++
        if (index < 0 || index > size) throw IndexOutOfBoundsException("Index: $index, Size: $size")

        val newUnderlyingSize = this.size + 1
        series = newUnderlyingSize j { i ->
            when {
                i < index -> series[i]
                i == index -> element
                else -> series[i - 1] // Shift elements from original series
            }
        }
    }

    override fun removeAt(index: Int): T {
        // AbstractMutableList.removeAt(index) calls this method.
        // It also handles modCount++
        if (index < 0 || index >= size) throw IndexOutOfBoundsException("Index: $index, Size: $size")

        val oldElement = series[index]
        val newUnderlyingSize = this.size - 1
        series = newUnderlyingSize j { i ->
            // Shift elements from original series
            if (i < index) series[i] else series[i + 1]
        }
        return oldElement
    }

    override fun set(index: Int, element: T): T {
        // AbstractMutableList.set(index, element) calls this method.
        // It does NOT handle modCount++ by default.
        if (index < 0 || index >= size) throw IndexOutOfBoundsException("Index: $index, Size: $size")

        val oldElement = series[index]
        series = series.size j { i -> // New series has the same size
            if (i == index) element else series[i]
        }
        return oldElement
    }

    override fun clear() {
        // AbstractMutableList.clear() calls removeRange(0, size).
        // removeRange, in turn, calls removeAt repeatedly.
        // While that works, it's inefficient for Series.
        // A direct implementation is better.
        series = emptySeries<T>()
        this.modCount++ // Manually increment modCount as we are overriding clear
                        // and not relying on repeated removeAt calls from superclass.
    }

    // Note: Methods like indexOf, lastIndexOf, remove(element: T), iterator, listIterator
    // are provided by AbstractMutableList and AbstractList.
    // They will work correctly using the overridden get(), size(), add(), removeAt(), set().
    // For example, remove(element: T) uses indexOf then removeAt(index).
    // indexOf iterates using get() and size().

    // toString() is inherited from AbstractCollection, which provides a standard list string representation.
}