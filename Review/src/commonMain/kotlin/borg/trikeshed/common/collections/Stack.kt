package borg.trikeshed.common.collections

import borg.trikeshed.core.Series
import borg.trikeshed.core.emptySeries
import borg.trikeshed.core.toSeries
import borg.trikeshed.core.j // For Pair construction / SeriesData for append helper
import borg.trikeshed.core.get // Extension operator for Series
import borg.trikeshed.core.size // Extension property for Series
import borg.trikeshed.core.isEmpty // Extension function for Series
import borg.trikeshed.core.dropLast // Extension function for Series
import borg.trikeshed.core.toList // Extension function for Series for toString
import java.util.NoSuchElementException

class Stack<T>(src: List<T> = emptyList()) {
    private var series: Series<T>

    init {
        this.series = if (src.isEmpty()) emptySeries() else src.toSeries()
    }

    // Internal constructor for cloning or direct Series initialization
    internal constructor(initialSeries: Series<T>) : this() { // Calls primary constructor then overwrites series
        this.series = initialSeries
    }

    // Helper function to append an item to a series, creating a new series
    // This could be an extension in a broader Series utility file if used elsewhere
    private fun Series<T>.append(item: T): Series<T> {
        val newSize = this.size + 1
        return newSize j { idx -> // Creates a Pair<Int, (Int)->T> which is Series<T>
            if (idx < this.size) this[idx] else item
        }
    }

    fun push(t: T): Stack<T> {
        this.series = this.series.append(t)
        return this // As per original signature
    }

    fun pop(): T {
        if (series.isEmpty()) {
            throw NoSuchElementException("Stack is empty.")
        }
        val topElement = series[series.size - 1]
        this.series = series.dropLast(1) // dropLast is available in Series.kt
        return topElement
    }

    fun peek(): T {
        if (series.isEmpty()) {
            throw NoSuchElementException("Stack is empty.")
        }
        return series[series.size - 1]
    }

    fun size(): Int = series.size

    fun isEmpty(): Boolean = series.isEmpty()

    fun clone(): Stack<T> {
        return Stack(this.series) // Uses the internal constructor
    }

    override fun toString(): String = "Stack(series=${series.toList()})"
}