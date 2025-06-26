@file:Suppress("UNCHECKED_CAST")

package borg.trikeshed.common.collections

import borg.trikeshed.lib.Indexed

typealias Bucket<T> = MutableList<T>

open class HashSeriesSet<T : Any> : SeriesSet<T> {
    open var buckets: Array<Bucket<T>> = createBuckets(16)
    private var _size: Int = 0

    private fun createBuckets(size: Int): Array<Bucket<T>> = Array(size) { mutableListOf() }

    override val size: Int get() = _size
    override fun isEmpty(): Boolean = _size == 0

    override fun contains(element: T): Boolean {
        val bucketIndex = getBucketIndex(element)
        return buckets[bucketIndex].contains(element)
    }

    override fun containsAll(elements: Collection<T>): Boolean {
        return elements.all { contains(it) }
    }

    override fun iterator(): MutableIterator<T> =
        object : MutableIterator<T> {
            val a = buckets.flatMap { it }.iterator()
            override fun hasNext(): Boolean = a.hasNext()
            override fun next(): T = a.next()
            override fun remove() { (a as MutableIterator<T>).remove() }
        }

    private fun getBucketIndex(element: T): Int =
        (element.hashCode() and 0x7FFFFFFF) % buckets.size

    companion object {
        class MutableHashSeriesSet<T : Any>(
            private val theSet: HashSeriesSet<T> = HashSeriesSet<T>(),
        ) : MutableSeriesSet<T>, Set<T> by theSet {

            override val size: Int get() = theSet._size
            var buckets: Array<Bucket<T>>
                get() = theSet.buckets
                set(value) {
                    theSet.buckets = value
                }

            override fun add(element: T): Boolean {
                if (contains(element)) return false

                val bucketIndex = theSet.getBucketIndex(element)
                theSet.buckets[bucketIndex].add(element)
                theSet._size++

                if (theSet._size > buckets.size * 0.75) {
                    resize()
                }
                return true
            }

            override fun remove(element: T): Boolean {
                val bucketIndex = theSet.getBucketIndex(element)
                val removed = theSet.buckets[bucketIndex].remove(element)
                if (removed) {
                    theSet._size--
                }
                return removed
            }

            override fun clear() {
                buckets = theSet.createBuckets(16)
                theSet._size = 0
            }

            override fun addAll(elements: Collection<T>): Boolean {
                var changed = false
                elements.forEach { if (add(it)) changed = true }
                return changed
            }

            override fun removeAll(elements: Collection<T>): Boolean {
                var changed = false
                elements.forEach { if (remove(it)) changed = true }
                return changed
            }

            override fun retainAll(elements: Collection<T>): Boolean {
                val toRetain = elements.toSet()
                var changed = false
                val iterator = theSet.iterator() as MutableIterator
                while(iterator.hasNext()){
                    val item = iterator.next()
                    if(item !in toRetain) {
                        iterator.remove()
                        changed = true
                    }
                }
                return changed
            }

            private fun resize() {
                val oldBuckets = buckets
                buckets = theSet.createBuckets(buckets.size * 2)
                theSet._size = 0
                oldBuckets.forEach { bucket ->
                    bucket.forEach { element ->
                        add(element)
                    }
                }
            }
        }
    }
}

interface SeriesSet<T> : Set<T>

interface MutableSeriesSet<T> : SeriesSet<T>, MutableSet<T>

// Example usage
fun main1() {
    val testSet = HashSeriesSet.MutableHashSeriesSet<Int>()
    testSet.add(10)
    testSet.add(20)
    testSet.add(30)

    println("Set size: ${testSet.size}")
    println("Set contains 20: ${testSet.contains(20)}")
    testSet.remove(20)
    println("Set contains 20 after removal: ${testSet.contains(20)}")

    println("Set contents:")
    for (item in testSet) {
        println(item)
    }

    println("Bucket contents:")
    for (bucket in testSet.buckets) {
        println(bucket.toList().toString())
    }
}