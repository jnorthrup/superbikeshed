package borg.trikeshed.common.collections

import borg.trikeshed.core.Series
import borg.trikeshed.core.emptySeries
import borg.trikeshed.core.j // For Pair construction / SeriesData
import borg.trikeshed.core.get // Extension operator for Series
import borg.trikeshed.core.size // Extension property for Series
import borg.trikeshed.core.isEmpty // Extension function for Series
import java.util.NoSuchElementException // For remove() and element() on empty heap

class Heap<T> {
    private var series: Series<T> = emptySeries()
    private val comparator: Comparator<T>

    constructor(comparator: Comparator<T>) {
        this.comparator = comparator
    }

    constructor() {
        this.comparator = Comparator { a, b -> a.toString().compareTo(b.toString()) }
    }

    private fun swap(s: Series<T>, index1: Int, index2: Int): Series<T> {
        return s.size j { i -> // New series has the same size as s
            when (i) {
                index1 -> s[index2]
                index2 -> s[index1]
                else -> s[i]
            }
        }
    }

    private fun Series<T>.append(item: T): Series<T> {
        val newSize = this.size + 1
        return newSize j { idx ->
            if (idx < this.size) this[idx] else item
        }
    }

    fun add(e: T) {
        var tempSeries = this.series.append(e)
        var currentIndex = tempSeries.size - 1
        while (currentIndex > 0) {
            val parentIndex = (currentIndex - 1) / 2
            // For a min-heap, parent should be smaller than or equal to child.
            // If parent (tempSeries[parentIndex]) is GREATER than child (tempSeries[currentIndex]), then swap.
            if (comparator.compare(tempSeries[parentIndex], tempSeries[currentIndex]) <= 0) {
                break // Parent is smaller or equal, heap property satisfied
            }
            // Parent is larger, swap
            tempSeries = swap(tempSeries, parentIndex, currentIndex)
            currentIndex = parentIndex
        }
        this.series = tempSeries
    }

    fun remove(): T {
        if (series.isEmpty()) {
            throw NoSuchElementException("Heap is empty.")
        }

        val rootElement = series[0]

        if (series.size == 1) {
            series = emptySeries()
            return rootElement
        }

        // Move last element to root and effectively shorten the series by 1 for sift-down
        // The new series for sift-down will have (original size - 1) elements.
        var tempSeries = (series.size - 1) j { i ->
            if (i == 0) series[series.size - 1] // Last element of original series moves to root
            else series[i] // Other elements are from original series, up to series.size - 2
        }

        var currentIndex = 0
        val currentSize = tempSeries.size // Size of the series being processed for sift-down

        while (true) {
            val leftChildIndex = currentIndex * 2 + 1
            val rightChildIndex = currentIndex * 2 + 2

            var smallestChildIndex = -1 // Placeholder for the index of the smaller child

            if (leftChildIndex < currentSize) {
                smallestChildIndex = leftChildIndex
            } else {
                // No children (left child is out of bounds), so sift-down ends for this path
                break
            }

            // If right child exists and is smaller than left child, it becomes the smallestChildIndex
            if (rightChildIndex < currentSize && comparator.compare(tempSeries[rightChildIndex], tempSeries[leftChildIndex]) < 0) {
                smallestChildIndex = rightChildIndex
            }

            // If current node (tempSeries[currentIndex]) is smaller than or equal to its smallest child,
            // heap property is satisfied for this subtree.
            if (comparator.compare(tempSeries[currentIndex], tempSeries[smallestChildIndex]) <= 0) {
                break
            }

            // Current node is larger than its smallest child, so swap them
            tempSeries = swap(tempSeries, currentIndex, smallestChildIndex)
            // Move down to the smallest child's position and continue sifting down
            currentIndex = smallestChildIndex
        }
        this.series = tempSeries
        return rootElement
    }

    fun poll(): T? {
        return if (series.isEmpty()) null else remove()
    }

    fun element(): T {
        if (series.isEmpty()) {
            throw NoSuchElementException("Heap is empty.")
        }
        return series[0]
    }

    fun peek(): T? {
        return if (series.isEmpty()) null else series[0]
    }

    fun size(): Int {
        return series.size
    }

    fun isEmpty(): Boolean {
        return series.isEmpty()
    }

    fun clear() {
        series = emptySeries()
    }
}
