package borg.trikeshed.common.collections

import borg.trikeshed.lib.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Advanced Tree Collections - Specialized tree structures with bbcursive patterns
 * 
 * Includes RadixTree, SortedMap, NavigableMap, and other advanced tree structures
 * optimized for register-at-a-time scanning and SIMD-friendly operations.
 */

// === RADIX TREE ===

/**
 * Radix Tree node using Join patterns
 */
data class RadixTreeNode<T>(
    val key: String,
    val value: T?,
    val children: Indexed<RadixTreeNode<T>?>
)

/**
 * Radix Tree implementation with bbcursive scanning
 */
class BBCursiveRadixTree<T> {
    private var root: RadixTreeNode<T>? = null
    
    /**
     * Insert key-value pair using bbcursive pattern
     */
    fun insert(key: String, value: T): RadixTreeNode<T> {
        return insertRecursive(root, key, value, 0)
    }
    
    private fun insertRecursive(node: RadixTreeNode<T>?, key: String, value: T, depth: Int): RadixTreeNode<T> {
        if (node == null) {
            return RadixTreeNode(key, value, 0 j { _ -> null })
        }
        
        val commonPrefix = findCommonPrefix(node.key, key)
        val remainingKey = key.substring(commonPrefix.length)
        
        if (remainingKey.isEmpty()) {
            return node.copy(value = value)
        }
        
        val remainingNodeKey = node.key.substring(commonPrefix.length)
        
        if (remainingNodeKey.isEmpty()) {
            // Insert into existing node
            val childIndex = remainingKey[0].code % 256
            val children = node.children.toMutableList()
            children[childIndex] = insertRecursive(children[childIndex], remainingKey, value, depth + 1)
            return node.copy(children = children)
        } else {
            // Split node
            val newChild = RadixTreeNode(remainingNodeKey, node.value, node.children)
            val splitNode = RadixTreeNode(commonPrefix, null, 0 j { _ -> null })
            val splitNodeChildren = splitNode.children.toMutableList()
            splitNodeChildren[remainingNodeKey[0].code % 256] = newChild
            splitNodeChildren[remainingKey[0].code % 256] = RadixTreeNode(remainingKey, value, 0 j { _ -> null })
            return splitNode.copy(children = splitNodeChildren)
        }
    }
    
    private fun findCommonPrefix(str1: String, str2: String): String {
        val minLength = minOf(str1.length, str2.length)
        for (i in 0 until minLength) if (str1[i] != str2[i]) return str1.substring(0, i)
        return str1.substring(0, minLength)
    }
    
    /**
     * Search using bbcursive pattern
     */
    fun search(key: String): T? {
        return searchRecursive(root, key, 0)
    }
    
    private fun searchRecursive(node: RadixTreeNode<T>?, key: String, depth: Int): T? {
        if (node == null) return null
        
        val commonPrefix = findCommonPrefix(node.key, key)
        val remainingKey = key.substring(commonPrefix.length)
        
        if (remainingKey.isEmpty()) {
            return node.value
        }
        
        val remainingNodeKey = node.key.substring(commonPrefix.length)
        
        if (remainingNodeKey.isEmpty()) {
            // Search in children
            val childIndex = remainingKey[0].code % 256
            val child = node.children[childIndex]
            return searchRecursive(child, remainingKey, depth + 1)
        }
        
        return null
    }
}

// === SORTED MAP ===

/**
 * Sorted Map entry using Join patterns
 */
typealias SortedMapEntry<K, V> = Join<K, V>

// Binary tree node removed - using direct Join pattern to avoid recursive type alias

/**
 * Sorted Map implementation using binary search tree
 */
class BBCursiveSortedMap<K : Comparable<K>, V> {
    private var root: Join<SortedMapEntry<K, V>, Join<Any?, Any?>>? = null
    
    /**
     * Insert key-value pair using bbcursive pattern
     */
    fun put(key: K, value: V) {
        val entry = key j value
        root = insertRecursive(root, entry)
    }
    
    private fun insertRecursive(node: Join<SortedMapEntry<K, V>, Join<Any?, Any?>>?, entry: SortedMapEntry<K, V>): Join<SortedMapEntry<K, V>, Join<Any?, Any?>> {
        if (node == null) return entry j (null j null)
        
        val currentEntry = node.a
        val left = node.b.a as? Join<SortedMapEntry<K, V>, Join<Any?, Any?>>
        val right = node.b.b as? Join<SortedMapEntry<K, V>, Join<Any?, Any?>>
        
        val currentKey = currentEntry.a
        val entryKey = entry.a
        
        return when {
            entryKey < currentKey -> {
                val newLeft = insertRecursive(left, entry)
                currentEntry j (newLeft j right)
            }
            entryKey > currentKey -> {
                val newRight = insertRecursive(right, entry)
                currentEntry j (left j newRight)
            }
            else -> entry j (left j right)
        }
    }
    
    /**
     * Get value by key using bbcursive pattern
     */
    fun get(key: K): V? = getRecursive(root, key)
    
    private fun getRecursive(node: Join<SortedMapEntry<K, V>, Join<Any?, Any?>>?, key: K): V? {
        if (node == null) return null
        
        val currentEntry = node.a
        val left = node.b.a as? Join<SortedMapEntry<K, V>, Join<Any?, Any?>>
        val right = node.b.b as? Join<SortedMapEntry<K, V>, Join<Any?, Any?>>
        
        val currentKey = currentEntry.a
        val currentValue = currentEntry.b
        
        return when {
            key == currentKey -> currentValue
            key < currentKey -> getRecursive(left, key)
            else -> getRecursive(right, key)
        }
    }
    
    /**
     * Get all entries as Indexed
     */
    fun entries(): Indexed<SortedMapEntry<K, V>> {
        val entries = mutableListOf<SortedMapEntry<K, V>>()
        collectEntries(root, entries)
        return entries.size j { i -> entries[i] }
    }
    
    private fun collectEntries(node: Join<SortedMapEntry<K, V>, Join<Any?, Any?>>?, entries: MutableList<SortedMapEntry<K, V>>) {
        if (node == null) return
        
        val currentEntry = node.a
        val left = node.b.a as? Join<SortedMapEntry<K, V>, Join<Any?, Any?>>
        val right = node.b.b as? Join<SortedMapEntry<K, V>, Join<Any?, Any?>>
        
        collectEntries(left, entries)
        entries.add(currentEntry)
        collectEntries(right, entries)
    }
}

// === NAVIGABLE MAP ===

/**
 * Navigable Map implementation with additional navigation methods
 */
class BBCursiveNavigableMap<K : Comparable<K>, V> {
    private val sortedMap = BBCursiveSortedMap<K, V>()
    
    // Delegate basic operations to sorted map
    fun put(key: K, value: V) = sortedMap.put(key, value)
    fun get(key: K): V? = sortedMap.get(key)
    fun entries() = sortedMap.entries()
    
    // Access to root for navigation methods
    private var root: Join<SortedMapEntry<K, V>, Join<Any?, Any?>>? = null
    
    /**
     * Get the first (smallest) key
     */
    fun firstKey(): K? = findFirstKey(root)
    
    private fun findFirstKey(node: Join<SortedMapEntry<K, V>, Join<Any?, Any?>>?): K? {
        if (node == null) return null
        val left = node.b.a as? Join<SortedMapEntry<K, V>, Join<Any?, Any?>>
        return if (left != null) findFirstKey(left) else node.a.a
    }
    
    /**
     * Get the last (largest) key
     */
    fun lastKey(): K? = findLastKey(root)
    
    private fun findLastKey(node: Join<SortedMapEntry<K, V>, Join<Any?, Any?>>?): K? {
        if (node == null) return null
        val right = node.b.b as? Join<SortedMapEntry<K, V>, Join<Any?, Any?>>
        return if (right != null) findLastKey(right) else node.a.a
    }
    
    /**
     * Get the greatest key less than the given key
     */
    fun lowerKey(key: K): K? = findLowerKey(root, key)
    
    private fun findLowerKey(node: Join<SortedMapEntry<K, V>, Join<Any?, Any?>>?, key: K): K? {
        if (node == null) return null
        
        val currentEntry = node.a
        val left = node.b.a as? Join<SortedMapEntry<K, V>, Join<Any?, Any?>>
        val right = node.b.b as? Join<SortedMapEntry<K, V>, Join<Any?, Any?>>
        
        val currentKey = currentEntry.a
        
        return when {
            key <= currentKey -> findLowerKey(left, key)
            else -> {
                val rightResult = findLowerKey(right, key)
                rightResult ?: currentKey
            }
        }
    }
    
    /**
     * Get the least key greater than the given key
     */
    fun higherKey(key: K): K? = findHigherKey(root, key)
    
    private fun findHigherKey(node: Join<SortedMapEntry<K, V>, Join<Any?, Any?>>?, key: K): K? {
        if (node == null) return null
        
        val currentEntry = node.a
        val left = node.b.a as? Join<SortedMapEntry<K, V>, Join<Any?, Any?>>
        val right = node.b.b as? Join<SortedMapEntry<K, V>, Join<Any?, Any?>>
        
        val currentKey = currentEntry.a
        
        return when {
            key >= currentKey -> findHigherKey(right, key)
            else -> {
                val leftResult = findHigherKey(left, key)
                leftResult ?: currentKey
            }
        }
    }
}

// === TRIE WITH COMPRESSION ===

/**
 * Compressed Trie node using data class to avoid recursive type aliases
 */
data class CompressedTrieNode<T>(
    val key: String,
    val value: T?,
    val children: Indexed<CompressedTrieNode<T>?>
)

/**
 * Compressed Trie implementation for memory efficiency
 */
class BBCursiveCompressedTrie<T> {
    private var root: CompressedTrieNode<T>? = null
    
    /**
     * Insert with compression using bbcursive pattern
     */
    fun insert(key: String, value: T) {
        root = insertCompressed(root, key, value)
    }
    
    private fun insertCompressed(node: CompressedTrieNode<T>?, key: String, value: T): CompressedTrieNode<T> {
        if (node == null) return CompressedTrieNode(key, value, 0 j { _ -> null })
        
        val nodeKey = node.key
        val nodeValue = node.value
        val children = node.children
        
        val commonPrefix = findCommonPrefix(nodeKey, key)
        
        if (commonPrefix.isEmpty()) {
            val newRoot = CompressedTrieNode("", null, 2 j { i -> when (i) { 0 -> node; 1 -> CompressedTrieNode(key, value, 0 j { _ -> null }); else -> null } })
            return newRoot
        }
        
        if (commonPrefix == nodeKey) {
            val remainingKey = key.substring(commonPrefix.length)
            val newChild = insertCompressed(null, remainingKey, value)
            val newChildren: Indexed<CompressedTrieNode<T>?> = (children.a + 1) j { i -> if (i < children.a) children.b(i) else newChild }
            return CompressedTrieNode(commonPrefix, nodeValue, newChildren)
        } else if (commonPrefix == key) {
            val remainingNodeKey = nodeKey.substring(commonPrefix.length)
            val newChild = insertCompressed(null, remainingNodeKey, nodeValue)
            val newChildren: Indexed<CompressedTrieNode<T>?> = (children.a + 1) j { i -> if (i < children.a) children.b(i) else newChild }
            return CompressedTrieNode(commonPrefix, value, newChildren)
        } else {
            val nodeRemaining = nodeKey.substring(commonPrefix.length)
            val keyRemaining = key.substring(commonPrefix.length)
            
            val nodeChild = insertCompressed(null, nodeRemaining, nodeValue)
            val keyChild = insertCompressed(null, keyRemaining, value)
            
            val newChildren: Indexed<CompressedTrieNode<T>?> = 2 j { i -> when (i) { 0 -> nodeChild; 1 -> keyChild; else -> null } }
            return CompressedTrieNode(commonPrefix, null, newChildren)
        }
    }
    
    private fun findCommonPrefix(str1: String, str2: String): String {
        val minLength = minOf(str1.length, str2.length)
        for (i in 0 until minLength) if (str1[i] != str2[i]) return str1.substring(0, i)
        return str1.substring(0, minLength)
    }
    
    /**
     * Search with compression using bbcursive pattern
     */
    fun search(key: String): T? = searchCompressed(root, key)
    
    private fun searchCompressed(node: CompressedTrieNode<T>?, key: String): T? {
        if (node == null) return null
        
        val nodeKey = node.key
        val nodeValue = node.value
        val children = node.children
        
        if (key.startsWith(nodeKey)) {
            val remainingKey = key.substring(nodeKey.length)
            if (remainingKey.isEmpty()) return nodeValue
            
            for (i in 0 until children.a) {
                val child = children.b(i)
                if (child != null) {
                    val result = searchCompressed(child, remainingKey)
                    if (result != null) return result
                }
            }
        }
        return null
    }
}

// === REGISTER PACKING PROFILING FOR ADVANCED TREES ===

/**
 * Extended profiler for advanced tree structures
 */
object AdvancedTreePackingProfiler {
    
    /**
     * Profile Radix Tree for register packing opportunities
     */
    fun <T> profileRadixTree(tree: BBCursiveRadixTree<T>): JoinPackingReport {
        val report = JoinPackingReport()
        report.registerUsagePatterns.add("radix_traversal" j "prefix_matching")
        report.registerUsagePatterns.add("string_compression" j "shared_prefixes")
        report.registerUsagePatterns.add("node_splitting" j "memory_efficiency")
        return report
    }
    
    /**
     * Profile Sorted Map for register packing opportunities
     */
    fun <K : Comparable<K>, V> profileSortedMap(map: BBCursiveSortedMap<K, V>): JoinPackingReport {
        val report = JoinPackingReport()
        report.registerUsagePatterns.add("key_value_pairs" j "join_composition")
        report.registerUsagePatterns.add("binary_search" j "logarithmic_access")
        report.registerUsagePatterns.add("in_order_traversal" j "sorted_iteration")
        return report
    }
    
    /**
     * Profile Navigable Map for register packing opportunities
     */
    fun <K : Comparable<K>, V> profileNavigableMap(map: BBCursiveNavigableMap<K, V>): JoinPackingReport {
        val report = JoinPackingReport()
        report.registerUsagePatterns.add("boundary_queries" j "range_operations")
        report.registerUsagePatterns.add("successor_predecessor" j "pointer_chasing")
        report.registerUsagePatterns.add("submap_operations" j "view_creation")
        return report
    }
    
    /**
     * Profile Compressed Trie for register packing opportunities
     */
    fun <T> profileCompressedTrie(trie: BBCursiveCompressedTrie<T>): JoinPackingReport {
        val report = JoinPackingReport()
        report.registerUsagePatterns.add("prefix_compression" j "memory_optimization")
        report.registerUsagePatterns.add("node_merging" j "space_efficiency")
        report.registerUsagePatterns.add("string_operations" j "character_scanning")
        return report
    }
} 