package borg.trikeshed.core.collections

import java.util.*
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.MutableCollection
import kotlin.collections.MutableIterator

/**
 * A high-performance implementation of TreeMap with Red-Black tree balancing.
 * This implementation follows the TrikeShed type system guidelines.
 */
class TreeMap<K, V>(private val comparator: Comparator<in K>? = null) : NavigableMap<K, V>, MutableMap<K, V> {
    // ... existing code ...

    private class DescendingSubMapView<K, V>(private val m: SubMapView<K, V>) : NavigableMap<K, V>, AbstractMutableMap<K, V>() {
        private val reverseComparator: Comparator<in K>? = m.comparator()?.reversed() ?: Comparator { k1, k2 -> (k2 as Comparable<K>).compareTo(k1) }

        override fun comparator(): Comparator<in K>? = reverseComparator
        override val size: Int get() = m.size
        override fun isEmpty(): Boolean = m.isEmpty()
        override fun containsKey(key: K): Boolean = m.containsKey(key)
        override fun containsValue(value: V): Boolean = m.containsValue(value)
        override fun get(key: K): V? = m.get(key)
        override fun put(key: K, value: V): V? = m.put(key, value)
        override fun remove(key: K): V? = m.remove(key)
        override fun clear() = m.clear()

        override fun firstKey(): K = m.lastKey()
        override fun lastKey(): K = m.firstKey()
        override fun firstEntry(): Map.Entry<K, V>? = m.lastEntry()
        override fun lastEntry(): Map.Entry<K, V>? = m.firstEntry()
        override fun pollFirstEntry(): Map.Entry<K, V>? = m.pollLastEntry()
        override fun pollLastEntry(): Map.Entry<K, V>? = m.pollFirstEntry()

        override fun lowerEntry(key: K): Map.Entry<K, V>? = m.higherEntry(key)
        override fun lowerKey(key: K): K? = m.higherKey(key)
        override fun floorEntry(key: K): Map.Entry<K, V>? = m.ceilingEntry(key)
        override fun floorKey(key: K): K? = m.ceilingKey(key)
        override fun ceilingEntry(key: K): Map.Entry<K, V>? = m.floorEntry(key)
        override fun ceilingKey(key: K): K? = m.floorKey(key)
        override fun higherEntry(key: K): Map.Entry<K, V>? = m.lowerEntry(key)
        override fun higherKey(key: K): K? = m.lowerKey(key)

        override fun descendingMap(): NavigableMap<K, V> = m
        override fun navigableKeySet(): NavigableSet<K> = m.navigableKeySet().descendingSet()
        override fun descendingKeySet(): NavigableSet<K> = m.navigableKeySet()

        override fun subMap(fromKey: K, fromInclusive: Boolean, toKey: K, toInclusive: Boolean): NavigableMap<K, V> {
            return m.subMap(toKey, toInclusive, fromKey, fromInclusive).descendingMap()
        }

        override fun headMap(toKey: K, inclusive: Boolean): NavigableMap<K, V> {
            return m.tailMap(toKey, inclusive).descendingMap()
        }

        override fun tailMap(fromKey: K, inclusive: Boolean): NavigableMap<K, V> {
            return m.headMap(fromKey, inclusive).descendingMap()
        }

        override val entries: MutableSet<MutableMap.MutableEntry<K, V>> by lazy { DescendingEntrySet() }
        override val keys: MutableSet<K> by lazy { navigableKeySet() }
        override val values: MutableCollection<V> by lazy { DescendingValueCollection() }

        private inner class DescendingEntrySet : AbstractMutableSet<MutableMap.MutableEntry<K, V>>() {
            override val size: Int get() = this@DescendingSubMapView.size
            override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> = DescendingEntryIterator()
            override fun contains(element: MutableMap.MutableEntry<K, V>): Boolean {
                val v = this@DescendingSubMapView.get(element.key)
                return v != null && v == element.value
            }
            override fun remove(element: MutableMap.MutableEntry<K, V>): Boolean {
                return this@DescendingSubMapView.remove(element.key, element.value)
            }
            override fun clear() = this@DescendingSubMapView.clear()
            override fun add(element: MutableMap.MutableEntry<K, V>): Boolean = throw UnsupportedOperationException()
        }

        private inner class DescendingValueCollection : AbstractMutableCollection<V>() {
            override val size: Int get() = this@DescendingSubMapView.size
            override fun iterator(): MutableIterator<V> = DescendingValueIterator()
            override fun contains(element: V): Boolean = this@DescendingSubMapView.containsValue(element)
            override fun clear() = this@DescendingSubMapView.clear()
            override fun add(element: V): Boolean = throw UnsupportedOperationException()
        }

        private inner class DescendingEntryIterator : MutableIterator<MutableMap.MutableEntry<K, V>> {
            private val iter = m.entries.iterator()
            override fun hasNext(): Boolean = iter.hasNext()
            override fun next(): MutableMap.MutableEntry<K, V> = iter.next()
            override fun remove() = iter.remove()
        }

        private inner class DescendingValueIterator : MutableIterator<V> {
            private val iter = m.values.iterator()
            override fun hasNext(): Boolean = iter.hasNext()
            override fun next(): V = iter.next()
            override fun remove() = iter.remove()
        }
    }

    private class SubMapNavigableKeySet<K, V>(private val map: SubMapView<K, V>) : AbstractMutableSet<K>(), NavigableSet<K> {
        override val size: Int get() = map.size
        override fun iterator(): MutableIterator<K> = map.SubMapKeyIterator()
        override fun descendingIterator(): Iterator<K> = map.descendingKeySet().iterator()
        override fun lower(e: K): K? = map.lowerKey(e)
        override fun floor(e: K): K? = map.floorKey(e)
        override fun ceiling(e: K): K? = map.ceilingKey(e)
        override fun higher(e: K): K? = map.higherKey(e)
        override fun first(): K = map.firstKey()
        override fun last(): K = map.lastKey()
        override fun pollFirst(): K? = map.pollFirstEntry()?.key
        override fun pollLast(): K? = map.pollLastEntry()?.key
        override fun contains(element: K): Boolean = map.containsKey(element)
        override fun remove(element: K): Boolean = map.remove(element) != null
        override fun clear() = map.clear()
        override fun add(element: K): Boolean = throw UnsupportedOperationException()
        override fun descendingSet(): NavigableSet<K> = map.descendingKeySet()
        override fun subSet(fromElement: K, fromInclusive: Boolean, toElement: K, toInclusive: Boolean): NavigableSet<K> {
            return map.subMap(fromElement, fromInclusive, toElement, toInclusive).navigableKeySet()
        }
        override fun headSet(toElement: K, inclusive: Boolean): NavigableSet<K> {
            return map.headMap(toElement, inclusive).navigableKeySet()
        }
        override fun tailSet(fromElement: K, inclusive: Boolean): NavigableSet<K> {
            return map.tailMap(fromElement, inclusive).navigableKeySet()
        }
        override fun subSet(fromElement: K, toElement: K): SortedSet<K> = subSet(fromElement, true, toElement, false)
        override fun headSet(toElement: K): SortedSet<K> = headSet(toElement, false)
        override fun tailSet(fromElement: K): SortedSet<K> = tailSet(fromElement, true)
        override fun comparator(): Comparator<in K>? = map.comparator()
    }

    // ... rest of existing code ...
} 