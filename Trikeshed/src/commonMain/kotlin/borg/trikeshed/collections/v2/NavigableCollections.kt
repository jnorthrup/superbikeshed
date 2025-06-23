package borg.trikeshed.collections.v2

/**
 * A SortedMap is a Map that maintains its entries in ascending order, sorted according to the keys' natural ordering,
 * or according to a Comparator provided at creation time.
 */
interface SortedMap<K, V> : Map<K, V> {
    fun comparator(): Comparator<in K>?
    fun firstKey(): K
    fun lastKey(): K
    fun headMap(toKey: K): SortedMap<K, V>
    fun tailMap(fromKey: K): SortedMap<K, V>
    fun subMap(fromKey: K, toKey: K): SortedMap<K, V>
}

/**
 * A NavigableMap is a SortedMap extended with navigation methods returning the closest matches for given search targets.
 */
interface NavigableMap<K, V> : SortedMap<K, V> {
    fun lowerEntry(key: K): Map.Entry<K, V>?
    fun lowerKey(key: K): K?
    fun floorEntry(key: K): Map.Entry<K, V>?
    fun floorKey(key: K): K?
    fun ceilingEntry(key: K): Map.Entry<K, V>?
    fun ceilingKey(key: K): K?
    fun higherEntry(key: K): Map.Entry<K, V>?
    fun higherKey(key: K): K?
    fun firstEntry(): Map.Entry<K, V>?
    fun lastEntry(): Map.Entry<K, V>?
    fun pollFirstEntry(): Map.Entry<K, V>?
    fun pollLastEntry(): Map.Entry<K, V>?
    fun descendingMap(): NavigableMap<K, V>
    fun navigableKeySet(): NavigableSet<K>
    fun descendingKeySet(): NavigableSet<K>
    fun subMap(fromKey: K, fromInclusive: Boolean, toKey: K, toInclusive: Boolean): NavigableMap<K, V>
    fun headMap(toKey: K, inclusive: Boolean): NavigableMap<K, V>
    fun tailMap(fromKey: K, inclusive: Boolean): NavigableMap<K, V>
}

/**
 * A SortedSet is a Set that maintains its elements in ascending order, sorted according to the elements' natural ordering,
 * or according to a Comparator provided at creation time.
 */
interface SortedSet<E> : Set<E> {
    fun comparator(): Comparator<in E>?
    fun first(): E
    fun last(): E
    fun headSet(toElement: E): SortedSet<E>
    fun tailSet(fromElement: E): SortedSet<E>
    fun subSet(fromElement: E, toElement: E): SortedSet<E>
}

/**
 * A NavigableSet is a SortedSet extended with navigation methods returning the closest matches for given search targets.
 */
interface NavigableSet<E> : SortedSet<E> {
    fun lower(e: E): E?
    fun floor(e: E): E?
    fun ceiling(e: E): E?
    fun higher(e: E): E?
    fun pollFirst(): E?
    fun pollLast(): E?
    fun descendingSet(): NavigableSet<E>
    fun descendingIterator(): Iterator<E>
    fun subSet(fromElement: E, fromInclusive: Boolean, toElement: E, toInclusive: Boolean): NavigableSet<E>
    fun headSet(toElement: E, inclusive: Boolean): NavigableSet<E>
    fun tailSet(fromElement: E, inclusive: Boolean): NavigableSet<E>
} 