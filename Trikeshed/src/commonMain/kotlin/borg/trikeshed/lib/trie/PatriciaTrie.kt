@file:OptIn(ExperimentalUnsignedTypes::class)
package borg.trikeshed.lib.trie


/**
 * Persistent, immutable Patricia (radix) trie with arraymap children and frozen sorted keys.
 * Keys are sequences (e.g., String, List<Char>, etc.).
 */
interface PatriciaTrie<K, V> {
    /** Returns the value for the given key, or null if not found. */
    operator fun get(key: K): V?
    /** Returns a new trie with the given key set to value. */
    fun put(key: K, value: V): PatriciaTrie<K, V>
    /** Returns a new trie with the given key removed. */
    fun remove(key: K): PatriciaTrie<K, V>
    /** True if the trie contains the given key. */
    fun containsKey(key: K): Boolean = get(key) != null
    /** Number of entries in the trie. */
    val size: Int
    /** All keys in the trie. */
    val keys: Set<K>
    /** All values in the trie. */
    val values: Collection<V>
    /** All key-value pairs in the trie. */
    val entries: Set<Map.Entry<K, V>>

    companion object {
        /** Create an empty PatriciaTrie for the given key type. */
        fun <K, V> empty(keySplitter: (K) -> List<Any>): PatriciaTrie<K, V> =
            ArrayMapPatriciaTrie(emptyList(), null, emptyArray(), keySplitter)
    }
}

private class ArrayMapPatriciaTrie<K, V>(
    private val prefix: List<Any>,
    private val value: V?,
    private val children: Array<Pair<Any, ArrayMapPatriciaTrie<K, V>>>,
    private val keySplitter: (K) -> List<Any>
) : PatriciaTrie<K, V> {
    override fun get(key: K): V? {
        val parts = keySplitter(key)
        return get(parts)
    }
    
    private fun get(parts: List<Any>): V? {
        if (parts.startsWith(prefix)) {
            val rest = parts.drop(prefix.size)
            if (rest.isEmpty()) return value
            val idx = children.indexOfFirst { it.first == rest[0] }
            return if (idx >= 0) children[idx].second.get(rest) else null
        } else if (prefix.startsWith(parts)) {
            return if (parts.size == prefix.size) value else null
        }
        return null
    }
    
    override fun put(key: K, value: V): PatriciaTrie<K, V> = put(keySplitter(key), value)
    
    private fun put(parts: List<Any>, value: V): PatriciaTrie<K, V> {
        if (prefix.isEmpty()) {
            if (parts.isEmpty()) return ArrayMapPatriciaTrie(prefix, value, children, keySplitter)
            val idx = children.indexOfFirst { it.first == parts[0] }
            return if (idx >= 0) {
                val updated = children.copyOf()
                updated[idx] = parts[0] to children[idx].second.put(parts.drop(1), value) as ArrayMapPatriciaTrie<K, V>
                ArrayMapPatriciaTrie(prefix, this.value, updated, keySplitter)
            } else {
                val newChild = ArrayMapPatriciaTrie(parts.drop(1), value, emptyArray<Pair<Any, ArrayMapPatriciaTrie<K, V>>>(), keySplitter)
                val newChildren = (children + (parts[0] to newChild)).sortedBy { it.first.toString() }.toTypedArray()
                ArrayMapPatriciaTrie(prefix, this.value, newChildren, keySplitter)
            }
        }
        
        val common = prefix.commonPrefixWith(parts)
        if (common.size == prefix.size && common.size == parts.size) {
            return ArrayMapPatriciaTrie(prefix, value, children, keySplitter)
        } else if (common.size == 0) {
            val newThis = ArrayMapPatriciaTrie(prefix.drop(1), this.value, children, keySplitter)
            val newOther = ArrayMapPatriciaTrie(parts.drop(1), value, emptyArray(), keySplitter)
            val newChildren = listOf(prefix[0] to newThis, parts[0] to newOther).sortedBy { it.first.toString() }.toTypedArray()
            return ArrayMapPatriciaTrie(common, null, newChildren, keySplitter)
        } else {
            val newThis = ArrayMapPatriciaTrie(prefix.drop(common.size), this.value, children, keySplitter)
            val newOther = ArrayMapPatriciaTrie(parts.drop(common.size), value, emptyArray(), keySplitter)
            val newChildren = listOfNotNull(
                if (newThis.prefix.isNotEmpty()) newThis.prefix[0] to newThis else null,
                if (newOther.prefix.isNotEmpty()) newOther.prefix[0] to newOther else null
            ).sortedBy { it.first.toString() }.toTypedArray()
            return ArrayMapPatriciaTrie(common, null, newChildren, keySplitter)
        }
    }
    
    override fun remove(key: K): PatriciaTrie<K, V> = remove(keySplitter(key))
    
    private fun remove(parts: List<Any>): PatriciaTrie<K, V> {
        if (!parts.startsWith(prefix)) return this
        val rest = parts.drop(prefix.size)
        if (rest.isEmpty()) {
            return if (children.isEmpty()) ArrayMapPatriciaTrie(prefix, null, children, keySplitter)
            else ArrayMapPatriciaTrie(prefix, null, children, keySplitter)
        }
        val idx = children.indexOfFirst { it.first == rest[0] }
        if (idx < 0) return this
        val updatedChild = children[idx].second.remove(rest)
        val updatedChildren = if ((updatedChild as ArrayMapPatriciaTrie<K, V>).isEmpty()) {
            children.toMutableList().apply { removeAt(idx) }.toTypedArray()
        } else {
            children.copyOf().apply { this[idx] = children[idx].first to updatedChild as ArrayMapPatriciaTrie<K, V> }
        }
        return ArrayMapPatriciaTrie(prefix, value, updatedChildren, keySplitter)
    }
    
    fun isEmpty(): Boolean = value == null && children.isEmpty()
    
    override val size: Int
        get() = (if (value != null) 1 else 0) + children.sumOf { it.second.size }
    
    override val keys: Set<K>
        get() = TODO("Implementation needed")
        
    override val values: Collection<V>
        get() = TODO("Implementation needed")
        
    override val entries: Set<Map.Entry<K, V>>
        get() = TODO("Implementation needed")
}

// Extension function for list prefix checking
private fun <T> List<T>.startsWith(other: List<T>): Boolean {
    if (other.size > this.size) return false
    return this.take(other.size) == other
}

// Extension function for common prefix
private fun <T> List<T>.commonPrefixWith(other: List<T>): List<T> {
    val result = mutableListOf<T>()
    val minSize = minOf(this.size, other.size)
    for (i in 0 until minSize) {
        if (this[i] == other[i]) {
            result.add(this[i])
        } else {
            break
        }
    }
    return result
}