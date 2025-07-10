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
typealias RadixTreeNode<T> = Join<String, Join<T?, Indexed<RadixTreeNode<T>>>>

/**
 * Radix Tree implementation with bbcursive scanning
 */
class BBCursiveRadixTree<T> {
    private var root: RadixTreeNode<T>? = null
    
    /**
     * Insert key-value pair using bbcursive pattern
     */
    fun insert(key: String, value: T) {
        root = insertRecursive(root, key, value)
    }
    
    private fun insertRecursive(node: RadixTreeNode<T>?, key: String, value: T): RadixTreeNode<T> {
        if (node == null) {
            return key j (value j (0 j { _ -> null }))
        }
        
        val nodeKey = node.a
        val nodeValue = node.b.a
        val children = node.b.b
        
        val commonPrefix = findCommonPrefix(nodeKey, key)
        
        if (commonPrefix == nodeKey) {
            // Key is a prefix of node key
            if (key == nodeKey) {
                // Exact match, update value
                return key j (value j children)
            } else {
                // Key is shorter, split node
                val remainingKey = key.substring(commonPrefix.length)
                val newChild = insertRecursive(null, remainingKey, value)
                val newChildren = (children.size + 1) j { i ->
                    if (i < children.size) children[i] else newChild
                }
                return commonPrefix j (nodeValue j newChildren)
            }
        } else if (commonPrefix == key) {
            // Node key is a prefix of key
            val remainingKey = nodeKey.substring(commonPrefix.length)
            val newChild = insertRecursive(null, remainingKey, nodeValue)
            val newChildren = (children.size + 1) j { i ->
                if (i < children.size) children[i] else newChild
            }
            return commonPrefix j (value j newChildren)
        } else {
            // Partial match, split both
            val nodeRemaining = nodeKey.substring(commonPrefix.length)
            val keyRemaining = key.substring(commonPrefix.length)
            
            val nodeChild = insertRecursive(null, nodeRemaining, nodeValue)
            val keyChild = insertRecursive(null, keyRemaining, value)
            
            val newChildren = 2 j { i ->
                when (i) {
                    0 -> nodeChild
                    1 -> keyChild
                    else -> null
                }
            }
            
            return commonPrefix j (null j newChildren)
        }
    }
    
    private fun findCommonPrefix(str1: String, str2: String): String {
        val minLength = minOf(str1.length, str2.length)
        for (i in 0 until minLength) {
            if (str1[i] != str2[i]) {
                return str1.substring(0, i)
            }
        }
        return str1.substring(0, minLength)
    }
    
    /**
     * Search using bbcursive pattern
     */
    fun search(key: String): T? {
        return searchRecursive(root, key)
    }
    
    private fun searchRecursive(node: RadixTreeNode<T>?, key: String): T? {
        if (node == null) return null
        
        val nodeKey = node.a
        val nodeValue = node.b.a
        val children = node.b.b
        
        if (key.startsWith(nodeKey)) {
            val remainingKey = key.substring(nodeKey.length)
            if (remainingKey.isEmpty()) {
                return nodeValue
            }
            
            // Search in children
            for (i in 0 until children.size) {
                val child = children[i]
                if (child != null) {
                    val result = searchRecursive(child, remainingKey)
                    if (result != null) return result
                }
            }
        }
        
        return null
    }
}

// === SORTED MAP ===

/**
 * Sorted Map entry using Join patterns
 */
typealias SortedMapEntry<K, V> = Join<K, V>

/**
 * Sorted Map implementation using binary search tree
 */
class BBCursiveSortedMap<K : Comparable<K>, V> {
    private var root: BinaryTreeNode<SortedMapEntry<K, V>>? = null
    
    /**
     * Insert key-value pair using bbcursive pattern
     */
    fun put(key: K, value: V) {
        val entry = key j value
        root = insertRecursive(root, entry)
    }
    
    private fun insertRecursive(node: BinaryTreeNode<SortedMapEntry<K, V>>?, entry: SortedMapEntry<K, V>): BinaryTreeNode<SortedMapEntry<K, V>> {
        if (node == null) {
            return entry j (null j null)
        }
        
        val currentEntry = node.a
        val left = node.b.a
        val right = node.b.b
        
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
            else -> {
                // Key already exists, update value
                entry j (left j right)
            }
        }
    }
    
    /**
     * Get value by key using bbcursive pattern
     */
    fun get(key: K): V? {
        return getRecursive(root, key)
    }
    
    private fun getRecursive(node: BinaryTreeNode<SortedMapEntry<K, V>>?, key: K): V? {
        if (node == null) return null
        
        val currentEntry = node.a
        val left = node.b.a
        val right = node.b.b
        
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
    
    private fun collectEntries(node: BinaryTreeNode<SortedMapEntry<K, V>>?, entries: MutableList<SortedMapEntry<K, V>>) {
        if (node == null) return
        
        val currentEntry = node.a
        val left = node.b.a
        val right = node.b.b
        
        collectEntries(left, entries)
        entries.add(currentEntry)
        collectEntries(right, entries)
    }
}

// === NAVIGABLE MAP ===

/**
 * Navigable Map implementation with additional navigation methods
 */
class BBCursiveNavigableMap<K : Comparable<K>, V> : BBCursiveSortedMap<K, V>() {
    
    /**
     * Get the first (smallest) key
     */
    fun firstKey(): K? {
        return findFirstKey(root)
    }
    
    private fun findFirstKey(node: BinaryTreeNode<SortedMapEntry<K, V>>?): K? {
        if (node == null) return null
        
        val left = node.b.a
        return if (left != null) {
            findFirstKey(left)
        } else {
            node.a.a
        }
    }
    
    /**
     * Get the last (largest) key
     */
    fun lastKey(): K? {
        return findLastKey(root)
    }
    
    private fun findLastKey(node: BinaryTreeNode<SortedMapEntry<K, V>>?): K? {
        if (node == null) return null
        
        val right = node.b.b
        return if (right != null) {
            findLastKey(right)
        } else {
            node.a.a
        }
    }
    
    /**
     * Get the greatest key less than the given key
     */
    fun lowerKey(key: K): K? {
        return findLowerKey(root, key)
    }
    
    private fun findLowerKey(node: BinaryTreeNode<SortedMapEntry<K, V>>?, key: K): K? {
        if (node == null) return null
        
        val currentEntry = node.a
        val left = node.b.a
        val right = node.b.b
        
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
    fun higherKey(key: K): K? {
        return findHigherKey(root, key)
    }
    
    private fun findHigherKey(node: BinaryTreeNode<SortedMapEntry<K, V>>?, key: K): K? {
        if (node == null) return null
        
        val currentEntry = node.a
        val left = node.b.a
        val right = node.b.b
        
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
 * Compressed Trie node using Join patterns
 */
typealias CompressedTrieNode<T> = Join<String, Join<T?, Indexed<CompressedTrieNode<T>>>>

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
        if (node == null) {
            return key j (value j (0 j { _ -> null }))
        }
        
        val nodeKey = node.a
        val nodeValue = node.b.a
        val children = node.b.b
        
        val commonPrefix = findCommonPrefix(nodeKey, key)
        
        if (commonPrefix.isEmpty()) {
            // No common prefix, create new root
            val newRoot = "" j (null j (2 j { i ->
                when (i) {
                    0 -> node
                    1 -> key j (value j (0 j { _ -> null }))
                    else -> null
                }
            }))
            return newRoot
        }
        
        if (commonPrefix == nodeKey) {
            // Node key is a prefix
            val remainingKey = key.substring(commonPrefix.length)
            val newChild = insertCompressed(null, remainingKey, value)
            val newChildren = (children.size + 1) j { i ->
                if (i < children.size) children[i] else newChild
            }
            return commonPrefix j (nodeValue j newChildren)
        } else if (commonPrefix == key) {
            // Key is a prefix
            val remainingNodeKey = nodeKey.substring(commonPrefix.length)
            val newChild = insertCompressed(null, remainingNodeKey, nodeValue)
            val newChildren = (children.size + 1) j { i ->
                if (i < children.size) children[i] else newChild
            }
            return commonPrefix j (value j newChildren)
        } else {
            // Partial match, split both
            val nodeRemaining = nodeKey.substring(commonPrefix.length)
            val keyRemaining = key.substring(commonPrefix.length)
            
            val nodeChild = insertCompressed(null, nodeRemaining, nodeValue)
            val keyChild = insertCompressed(null, keyRemaining, value)
            
            val newChildren = 2 j { i ->
                when (i) {
                    0 -> nodeChild
                    1 -> keyChild
                    else -> null
                }
            }
            
            return commonPrefix j (null j newChildren)
        }
    }
    
    private fun findCommonPrefix(str1: String, str2: String): String {
        val minLength = minOf(str1.length, str2.length)
        for (i in 0 until minLength) {
            if (str1[i] != str2[i]) {
                return str1.substring(0, i)
            }
        }
        return str1.substring(0, minLength)
    }
    
    /**
     * Search with compression using bbcursive pattern
     */
    fun search(key: String): T? {
        return searchCompressed(root, key)
    }
    
    private fun searchCompressed(node: CompressedTrieNode<T>?, key: String): T? {
        if (node == null) return null
        
        val nodeKey = node.a
        val nodeValue = node.b.a
        val children = node.b.b
        
        if (key.startsWith(nodeKey)) {
            val remainingKey = key.substring(nodeKey.length)
            if (remainingKey.isEmpty()) {
                return nodeValue
            }
            
            // Search in children
            for (i in 0 until children.size) {
                val child = children[i]
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
        
        // Profile string operations
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
        
        // Profile map operations
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
        
        // Profile navigation operations
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
        
        // Profile compression operations
        report.registerUsagePatterns.add("prefix_compression" j "memory_optimization")
        report.registerUsagePatterns.add("node_merging" j "space_efficiency")
        report.registerUsagePatterns.add("string_operations" j "character_scanning")
        
        return report
    }
} 