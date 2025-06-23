@file:Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")

package borg.trikeshed.trie

import borg.trikeshed.lib.*

/**
 * ## MetaSeries ArrayMap - Universal Key-Value Structure
 * 
 * Serves both TrikeShed and standard library patterns through MetaSeries foundation.
 * Uses the key type as the MetaSeries index realm for efficient lookups.
 */
class ArrayMap<K, V>(
    // MetaSeries<K, V> - Keys as index realm, values as elements
    private val data: MetaSeries<K, V>,
    private val sortedKeys: Indexed<K>,
    private val compareFunction: (K, K) -> Int = { a, b -> a.toString().compareTo(b.toString()) }
) {
    
    // === TRIKESHED NATIVE INTERFACE ===
    
    /** Core MetaSeries access - the foundational truth */
    val metaSeries: MetaSeries<K, V> get() = data
    
    /** Size using TrikeShed patterns */
    val size: Int get() = sortedKeys.a
    
    /** Keys as Indexed<K> */
    val keys: Indexed<K> get() = sortedKeys
    
    /** Values as Indexed<V> using MetaSeries realm */
    val values: Indexed<V> get() = sortedKeys.a j { i -> data.b(sortedKeys.b(i)) }
    
    /** TrikeShed lookup by key */
    fun getValue(key: K): V? = if (containsKey(key)) data.b(key) else null
    
    /** TrikeShed containment check */
    fun containsKey(key: K): Boolean = binSearchIndex(key) >= 0
    
    // === STANDARD LIBRARY BRIDGE ===
    
    /** Bridge to Map<K, V> for interop */
    fun asMap(): Map<K, V> = object : Map<K, V> {
        override val size: Int get() = this@ArrayMap.size
        override val keys: Set<K> get() = (0 until sortedKeys.a).map { sortedKeys.b(it) }.toSet()
        override val values: Collection<V> get() = (0 until sortedKeys.a).map { data.b(sortedKeys.b(it)) }
        override val entries: Set<Map.Entry<K, V>> get() = (0 until sortedKeys.a).map { i ->
            val key = sortedKeys.b(i)
            object : Map.Entry<K, V> {
                override val key: K = key
                override val value: V = data.b(key)
            }
        }.toSet()
        override fun containsKey(key: K): Boolean = this@ArrayMap.containsKey(key)
        override fun containsValue(value: V): Boolean = values.contains(value)
        override fun get(key: K): V? = this@ArrayMap.getValue(key)
        override fun isEmpty(): Boolean = size == 0
    }
    
    // === EFFICIENT IMPLEMENTATION ===
    
    /** Binary search on sorted keys for O(log n) lookup */
    private fun binSearchIndex(key: K): Int {
        var low = 0
        var high = sortedKeys.a - 1
        
        while (low <= high) {
            val mid = (low + high) / 2
            val midKey = sortedKeys.b(mid)
            val cmp = compareFunction(midKey, key)
            
            when {
                cmp < 0 -> low = mid + 1
                cmp > 0 -> high = mid - 1
                else -> return mid
            }
        }
        return -(low + 1) // Standard binary search return for not found
    }
    
    companion object {
        /** Create from standard Map */
        fun <K, V> fromMap(
            map: Map<K, V>,
            compareFunction: (K, K) -> Int = { a, b -> a.toString().compareTo(b.toString()) }
        ): ArrayMap<K, V> {
            val sortedKeys = map.keys.sortedWith(Comparator(compareFunction))
            val keysSeries = sortedKeys.size j { i -> sortedKeys[i] }
            val metaSeries = keysSeries.a j { key -> map[key]!! }
            
            return ArrayMap(metaSeries, keysSeries, compareFunction)
        }
        
        /** Create from TrikeShed data */
        fun <K, V> fromSeries(
            keys: Indexed<K>,
            values: Indexed<V>,
            compareFunction: (K, K) -> Int = { a, b -> a.toString().compareTo(b.toString()) }
        ): ArrayMap<K, V> {
            require(keys.a == values.a) { "Keys and values must have same size" }
            
            // Sort keys and reorder values accordingly
            val sortedIndices = (0 until keys.a).sortedWith { i, j -> compareFunction(keys.b(i), keys.b(j)) }
            val sortedKeys = sortedIndices.size j { i -> keys.b(sortedIndices[i]) }
            val metaSeries = sortedKeys.a j { key -> 
                val originalIndex = (0 until keys.a).first { keys.b(it) == key }
                values.b(originalIndex)
            }
            
            return ArrayMap(metaSeries, sortedKeys, compareFunction)
        }
    }
} 