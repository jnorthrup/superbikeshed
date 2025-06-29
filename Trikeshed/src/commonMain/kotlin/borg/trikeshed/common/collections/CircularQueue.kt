@file:Suppress("UNCHECKED_CAST")

package borg.trikeshed.common.collections

import borg.trikeshed.lib.Join
import borg.trikeshed.lib.j
import borg.trikeshed.lib.MutableIndexed // Import the interface
import borg.trikeshed.lib.Indexed // Explicit import for clarity, though it's a typealias from CoreTypes via MutableIndexed
import kotlinx.coroutines.InternalCoroutinesApi
import java.util.NoSuchElementException

@InternalCoroutinesApi
typealias CircularQueue<T> = CirQlar<T>

/**
 * A circular queue implementation using a fixed-size array that implements MutableIndexed<T>.
 * When the queue is full and a new element is offered (added to end), the oldest element is overwritten.
 * Supports an optional eviction callback.
 * Core queue operations `offer`, `poll`, and `peek` are O(1).
 * Indexed access (via `a` for size and `b` for accessor) is O(1).
 * Operations like `add(index, item)`, `removeAt(index)`, `remove(item)` are O(N).
 */
@InternalCoroutinesApi
open class CirQlar<T>(
    private val capacity: Int,
    private val buffer: Array<Any?> = arrayOfNulls<Any?>(capacity),
    private val evict: ((T) -> Unit)? = null,
) : MutableIndexed<T> {

    private var head: Int = 0 // Index of the first element in the buffer
    private var count: Int = 0 // Number of elements currently in the queue

    val currentSize: Int get() = count // Internal use for clarity
    val isEmpty: Boolean get() = count == 0
    val isFull: Boolean get() = count == capacity

    // Implementation for Indexed<T> (which is Join<Int, (Int) -> T>)
    override val a: Int get() = currentSize // 'a' is the size for Join/Indexed

    override val b: (index: Int) -> T = { index -> // 'b' is the accessor function for Join/Indexed
        if (index < 0 || index >= this.count) { // Use this.count to be explicit
            throw IndexOutOfBoundsException("Index $index is out of bounds for current size ${this.count}")
        }
        val arrayIndex = (this.head + index) % this.capacity // Use this. for clarity
        this.buffer[arrayIndex] as T
    }

    // Core queue operations
    fun offer(e: T): Boolean {
        if (capacity == 0) return false

        if (isFull) {
            val elementToEvict = buffer[head] as? T
            buffer[head] = e
            head = (head + 1) % capacity
            elementToEvict?.let { evict?.invoke(it) }
        } else {
            val insertIndex = (head + count) % capacity
            buffer[insertIndex] = e
            count++
        }
        return true
    }

    fun peek(): T {
        if (isEmpty) throw NoSuchElementException("Queue is empty.")
        return buffer[head] as T
    }

    fun poll(): T? {
        if (isEmpty) return null
        val element = buffer[head] as T
        buffer[head] = null
        head = (head + 1) % capacity
        count--
        return element
    }

    fun toList(): List<T> {
        if (isEmpty) return emptyList()
        // Use the accessor 'b' or direct buffer access; 'b' is safer if future logic changes.
        // The extension function 'get' on Indexed<T> will use 'b(index)'.
        val list = ArrayList<T>(a) // 'a' is size
        for (i in 0 until a) {
            list.add(b(i)) // Use the 'b' accessor
        }
        return list
    }

    // MutableIndexed<T> implementations
    override operator fun set(index: Int, item: T) {
        if (index < 0 || index >= count) {
            throw IndexOutOfBoundsException("Index $index is out of bounds for current size $count")
        }
        val arrayIndex = (head + index) % capacity
        buffer[arrayIndex] = item
    }

    override fun add(item: T) {
        offer(item)
    }

    override fun add(index: Int, item: T) {
        if (index < 0 || index > count) {
            throw IndexOutOfBoundsException("Index $index for add is out of bounds for current size $count")
        }
        if (index == count) {
            offer(item)
        } else {
            if (isFull) {
                evict?.let { if (buffer[head] != null) it(buffer[head] as T) }
                buffer[head] = null
                head = (head + 1) % capacity
                // count effectively reduced by 1 for insertion logic, will be restored to capacity
                for (i_logical in (count - 2) downTo index) {
                    val srcArrIdx = (this.head + i_logical) % this.capacity
                    val dstArrIdx = (this.head + i_logical + 1) % this.capacity
                    buffer[dstArrIdx] = buffer[srcArrIdx]
                }
                val insertArrIdx = (this.head + index) % this.capacity
                buffer[insertArrIdx] = item
                // count remains `capacity`
            } else {
                for (i_logical in count - 1 downTo index) {
                    val srcArrIdx = (head + i_logical) % capacity
                    val dstArrIdx = (head + i_logical + 1) % capacity
                    buffer[dstArrIdx] = buffer[srcArrIdx]
                }
                val insertArrIdx = (head + index) % capacity
                buffer[insertArrIdx] = item
                count++
            }
        }
    }

    override fun removeAt(index: Int): T {
        return removeAtLogicalIndex(index)
    }

    override fun remove(item: T): Boolean {
        // Use 'b(i)' for access via Indexed interface
        val logicalIndexToRemove = (0 until a).indexOfFirst { b(it) == item }
        if (logicalIndexToRemove != -1) {
            removeAtLogicalIndex(logicalIndexToRemove)
            return true
        }
        return false
    }

    override fun clear() {
        for (i in 0 until count) {
            buffer[(head + i) % capacity] = null
        }
        head = 0
        count = 0
    }

    override operator fun plus(item: T): MutableIndexed<T> { // Return type must match interface
        val newQueue = CirQlar<T>(capacity, evict = this.evict)
        for (i in 0 until a) { // Use 'a' for size
            newQueue.offer(b(i)) // Use 'b' for access
        }
        newQueue.offer(item)
        return newQueue
    }

    override operator fun minus(item: T): MutableIndexed<T> { // Return type must match interface
        val newQueue = CirQlar<T>(capacity, evict = this.evict)
        var removedOnce = false
        for (i in 0 until a) { // Use 'a' for size
            val currentItem = b(i) // Use 'b' for access
            if (currentItem == item && !removedOnce) {
                removedOnce = true
            } else {
                newQueue.offer(currentItem)
            }
        }
        return newQueue
    }

    override operator fun plusAssign(item: T) {
        offer(item)
    }

    override operator fun minusAssign(item: T) {
        remove(item)
    }

    fun toVect0r(): Join<Int, (Int) -> T> = (currentSize j { logicalIndex: Int ->
        b(logicalIndex) // Use the 'b' accessor which correctly handles indexing
    })

    // Note: iterator() is not part of MutableIndexed<T> or Indexed<T> (as Join)
    // It's provided as a standard collection feature.
    // The `override` keyword was removed as it doesn't override from these specific interfaces.
    fun iterator(): MutableIterator<T> {
        var logicalIdx = 0
        var lastReturnedLogicalIdx = -1
        var canRemove = false

        return object : MutableIterator<T> {
            override fun hasNext(): Boolean = logicalIdx < this@CirQlar.count

            override fun next(): T {
                if (!hasNext()) throw NoSuchElementException()
                // Use 'b(idx)' to get element, consistent with Indexed<T>
                val element = this@CirQlar.b(logicalIdx)
                lastReturnedLogicalIdx = logicalIdx
                logicalIdx++
                canRemove = true
                return element
            }

            override fun remove() {
                if (!canRemove || lastReturnedLogicalIdx < 0) {
                    throw IllegalStateException("next() must be called before remove(), and remove() can only be called once per call to next().")
                }
                this@CirQlar.removeAtLogicalIndex(lastReturnedLogicalIdx)
                logicalIdx--
                canRemove = false
                lastReturnedLogicalIdx = -1
            }
        }
    }

    private fun removeAtLogicalIndex(logicalIndex: Int): T {
        if (logicalIndex < 0 || logicalIndex >= count) {
            throw IndexOutOfBoundsException("Logical index $logicalIndex is out of bounds for size $count")
        }

        val arrayIndexToRemove = (head + logicalIndex) % capacity
        val removedElement = buffer[arrayIndexToRemove] as T

        for (i in logicalIndex until count - 1) {
            val currentElementTargetIndex = (head + i) % capacity
            val nextElementSourceIndex = (head + i + 1) % capacity
            buffer[currentElementTargetIndex] = buffer[nextElementSourceIndex]
        }

        val lastElementOldArrayIndex = (head + count - 1) % capacity
        buffer[lastElementOldArrayIndex] = null
        count--
        return removedElement
    }
}

