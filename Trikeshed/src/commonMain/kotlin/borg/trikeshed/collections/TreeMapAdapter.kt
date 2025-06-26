package borg.trikeshed.collections

// TODO: Uncomment when v2.TreeMap is implemented
/*
import borg.trikeshed.collections.v2.TreeMap as V2TreeMap
import kotlin.collections.NavigableMap
import kotlin.collections.MutableMap

/**
 * Adapter class to help migrate from old TreeMap to new v2.TreeMap implementation.
 * This class provides methods to convert between the two implementations.
 */
object TreeMapAdapter {
    /**
     * Convert an old TreeMap to the new v2.TreeMap implementation.
     * @param oldMap The old TreeMap instance to convert
     * @return A new v2.TreeMap containing the same elements
     */
    fun <K, V> toV2(oldMap: TreeMap<K, V>): V2TreeMap<K, V> {
        val newMap = V2TreeMap<K, V>(oldMap.comparator())
        newMap.putAll(oldMap)
        return newMap
    }

    /**
     * Convert a new v2.TreeMap to the old TreeMap implementation.
     * @param newMap The v2.TreeMap instance to convert
     * @return A new TreeMap containing the same elements
     */
    fun <K, V> fromV2(newMap: V2TreeMap<K, V>): TreeMap<K, V> {
        val oldMap = TreeMap<K, V>(newMap.comparator())
        oldMap.putAll(newMap)
        return oldMap
    }

    /**
     * Create a view that adapts an old TreeMap to the v2.TreeMap interface.
     * Changes to the view are reflected in the original map and vice versa.
     * @param oldMap The old TreeMap to adapt
     * @return A v2.TreeMap view of the old map
     */
    fun <K, V> asV2View(oldMap: TreeMap<K, V>): V2TreeMap<K, V> {
        return object : V2TreeMap<K, V>(oldMap.comparator()) {
            override fun put(key: K, value: V): V? = oldMap.put(key, value)
            override fun remove(key: K): V? = oldMap.remove(key)
            override fun get(key: K): V? = oldMap.get(key)
            override val size: Int get() = oldMap.size
            override fun isEmpty(): Boolean = oldMap.isEmpty()
            override fun containsKey(key: K): Boolean = oldMap.containsKey(key)
            override fun containsValue(value: V): Boolean = oldMap.containsValue(value)
            override fun putAll(from: Map<out K, V>) = oldMap.putAll(from)
            override fun clear() = oldMap.clear()
            override val entries: MutableSet<MutableMap.MutableEntry<K, V>> get() = oldMap.entries
            override val keys: MutableSet<K> get() = oldMap.keys
            override val values: MutableCollection<V> get() = oldMap.values
        }
    }

    /**
     * Create a view that adapts a v2.TreeMap to the old TreeMap interface.
     * Changes to the view are reflected in the original map and vice versa.
     * @param newMap The v2.TreeMap to adapt
     * @return A TreeMap view of the new map
     */
    fun <K, V> asOldView(newMap: V2TreeMap<K, V>): TreeMap<K, V> {
        return object : TreeMap<K, V>(newMap.comparator()) {
            override fun put(key: K, value: V): V? = newMap.put(key, value)
            override fun remove(key: K): V? = newMap.remove(key)
            override fun get(key: K): V? = newMap.get(key)
            override val size: Int get() = newMap.size
            override fun isEmpty(): Boolean = newMap.isEmpty()
            override fun containsKey(key: K): Boolean = newMap.containsKey(key)
            override fun containsValue(value: V): Boolean = newMap.containsValue(value)
            override fun putAll(from: Map<out K, V>) = newMap.putAll(from)
            override fun clear() = newMap.clear()
            override val entries: MutableSet<MutableMap.MutableEntry<K, V>> get() = newMap.entries
            override val keys: MutableSet<K> get() = newMap.keys
            override val values: MutableCollection<V> get() = newMap.values
        }
    }
}
*/