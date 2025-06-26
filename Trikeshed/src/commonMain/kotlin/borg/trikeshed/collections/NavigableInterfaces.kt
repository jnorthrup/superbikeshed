package borg.trikeshed.collections

/**
 * NavigableMap interface for Kotlin Multiplatform
 * Provides navigation methods for sorted maps
 */
interface NavigableMap<K, V> : MutableMap<K, V> {
    fun comparator(): Comparator<in K>?
    fun firstKey(): K?
    fun lastKey(): K?
    fun firstEntry(): Map.Entry<K, V>?
    fun lastEntry(): Map.Entry<K, V>?
    
    fun lowerEntry(key: K): Map.Entry<K, V>?
    fun lowerKey(key: K): K?
    fun floorEntry(key: K): Map.Entry<K, V>?
    fun floorKey(key: K): K?
    fun ceilingEntry(key: K): Map.Entry<K, V>?
    fun ceilingKey(key: K): K?
    fun higherEntry(key: K): Map.Entry<K, V>?
    fun higherKey(key: K): K?
    
    fun pollFirstEntry(): Map.Entry<K, V>?
    fun pollLastEntry(): Map.Entry<K, V>?
    
    fun descendingMap(): NavigableMap<K, V>
    fun navigableKeySet(): NavigableSet<K>
    fun descendingKeySet(): NavigableSet<K>
    
    fun subMap(fromKey: K, fromInclusive: Boolean, toKey: K, toInclusive: Boolean): NavigableMap<K, V>
    fun headMap(toKey: K, inclusive: Boolean): NavigableMap<K, V>
    fun tailMap(fromKey: K, inclusive: Boolean): NavigableMap<K, V>
    
    fun subMap(fromKey: K, toKey: K): NavigableMap<K, V> = subMap(fromKey, true, toKey, false)
    fun headMap(toKey: K): NavigableMap<K, V> = headMap(toKey, false)
    fun tailMap(fromKey: K): NavigableMap<K, V> = tailMap(fromKey, true)
}

/**
 * NavigableSet interface for Kotlin Multiplatform
 * Provides navigation methods for sorted sets
 */
interface NavigableSet<E> : MutableSet<E> {
    fun comparator(): Comparator<in E>?
    fun first(): E?
    fun last(): E?
    
    fun lower(e: E): E?
    fun floor(e: E): E?
    fun ceiling(e: E): E?
    fun higher(e: E): E?
    
    fun pollFirst(): E?
    fun pollLast(): E?
    
    fun descendingSet(): NavigableSet<E>
    fun descendingIterator(): MutableIterator<E>
    
    fun subSet(fromElement: E, fromInclusive: Boolean, toElement: E, toInclusive: Boolean): NavigableSet<E>
    fun headSet(toElement: E, inclusive: Boolean): NavigableSet<E>
    fun tailSet(fromElement: E, inclusive: Boolean): NavigableSet<E>
    
    fun subSet(fromElement: E, toElement: E): NavigableSet<E> = subSet(fromElement, true, toElement, false)
    fun headSet(toElement: E): NavigableSet<E> = headSet(toElement, false)
    fun tailSet(fromElement: E): NavigableSet<E> = tailSet(fromElement, true)
}