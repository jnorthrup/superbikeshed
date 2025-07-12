package borg.trikeshed.common.collections

import borg.trikeshed.lib.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Linear Collections - Efficient linear data structures with bbcursive patterns
 * 
 * Includes HashSet, TreeSet, Stack, CircularQueue, and other linear structures
 * optimized for register-at-a-time scanning and SIMD-friendly operations.
 */

// === TYPE DEFINITIONS ===

// Binary Tree Node type removed - defined in BBCursiveTreeCollections.kt

// === HASH SET ===

/**
 * Hash Set entry using Join patterns
 */
typealias HashSetEntry<T> = Join<T, Boolean>

/**
 * Hash Set implementation with bbcursive scanning
 */
class BBCursiveHashSet<T> {
    private var buckets: Indexed<Indexed<HashSetEntry<T>?>> = 16 j { _ -> 0 j { _ -> null } }
    private var size = 0
    private val loadFactor = 0.75
    
    /**
     * Add element using bbcursive pattern
     */
    fun add(element: T): Boolean {
        val hash = element.hashCode()
        val bucketIndex = hash and (buckets.a - 1)
        val bucket = buckets[bucketIndex]
        
        // Check if element already exists
        for (i in 0 until bucket.a) {
            val entry = bucket[i]
            if (entry != null && entry.a == element) {
                return false // Already exists
            }
        }
        
        // Add new element
        val newEntry = element j true
        val newBucket = (bucket.a + 1) j { i ->
            if (i < bucket.a) bucket[i] else newEntry
        }
        
        buckets = buckets.a j { i ->
            if (i == bucketIndex) newBucket else buckets[i]
        }
        
        size++
        
        // Resize if needed
        if (size > buckets.a * loadFactor) {
            resize()
        }
        
        return true
    }
    
    /**
     * Remove element using bbcursive pattern
     */
    fun remove(element: T): Boolean {
        val hash = element.hashCode()
        val bucketIndex = hash and (buckets.a - 1)
        val bucket = buckets[bucketIndex]
        
        // Find and remove element
        for (i in 0 until bucket.a) {
            val entry = bucket[i]
            if (entry != null && entry.a == element) {
                val newBucket = (bucket.a - 1) j { j ->
                    when {
                        j < i -> bucket[j]
                        j == i -> null
                        else -> bucket[j + 1]
                    }
                }
                
                buckets = buckets.a j { k ->
                    if (k == bucketIndex) newBucket else buckets[k]
                }
                
                size--
                return true
            }
        }
        
        return false
    }
    
    /**
     * Check if element exists using bbcursive pattern
     */
    fun contains(element: T): Boolean {
        val hash = element.hashCode()
        val bucketIndex = hash and (buckets.a - 1)
        val bucket = buckets[bucketIndex]
        
        for (i in 0 until bucket.a) {
            val entry = bucket[i]
            if (entry != null && entry.a == element)                    return true
            
        }
        
        return false
    }
    
    private fun resize() {
        val oldBuckets = buckets
        buckets = (oldBuckets.a * 2) j { _ -> 0 j { _ -> null } }
        size = 0
        
        // Rehash all elements
        for (i in 0 until oldBuckets.a) {
            val bucket = oldBuckets[i]
            for (j in 0 until bucket.a) {
                val entry = bucket[j]
                if (entry != null)  
                    add(entry.a)
              
            }
        }
    }
    
    /**
     * Get all elements as Indexed
     */
    fun elements(): Indexed<T> {
        val elements = mutableListOf<T>()
        
        for (i in 0 until buckets.size) {
            val bucket = buckets[i]
            for (j in 0 until bucket.size) {
                val entry = bucket[j]
                if (entry != null)                    elements.add(entry.a)
                
            }
        }
        
        return elements.size j { i -> elements[i] }
    }
}

// === TREE SET ===

/**
 * Tree Set implementation using binary search tree
 */
class BBCursiveTreeSet<T : Comparable<T>> {
    private var root: BinaryTreeNode<T>? = null
    private var size = 0
    
    /**
     * Add element using bbcursive pattern
     */
    fun add(element: T): Boolean {
        val oldSize = size
        root = insertRecursive(root, element)
        return size > oldSize
    }
    
    private fun insertRecursive(node: BinaryTreeNode<T>?, element: T): BinaryTreeNode<T> {
        if (node == null) {
            size++
            return BinaryTreeNode(element, null, null)
        }
        
        val currentValue = node.value
        val left = node.left
        val right = node.right
        
        return when {
            element < currentValue -> {
                val newLeft = insertRecursive(left, element)
                BinaryTreeNode(currentValue, newLeft, right)
            }
            element > currentValue -> {
                val newRight = insertRecursive(right, element)
                BinaryTreeNode(currentValue, left, newRight)
            }
            else -> node // Element already exists
        }
    }
    
    /**
     * Remove element using bbcursive pattern
     */
    fun remove(element: T): Boolean {
        val oldSize = size
        root = removeRecursive(root, element)
        return size < oldSize
    }
    
    private fun removeRecursive(node: BinaryTreeNode<T>?, element: T): BinaryTreeNode<T>? {
        if (node == null) return null
        
        val currentValue = node.value
        val left = node.left
        val right = node.right
        
        return when {
            element < currentValue -> {
                val newLeft = removeRecursive(left, element)
                BinaryTreeNode(currentValue, newLeft, right)
            }
            element > currentValue -> {
                val newRight = removeRecursive(right, element)
                BinaryTreeNode(currentValue, left, newRight)
            }
            else -> {
                // Found element to remove
                size--
                when {
                    left == null -> right
                    right == null -> left
                    else -> {
                        // Node has two children, find successor
                        val successor = findMin(right)
                        val newRight = removeRecursive(right, successor.value)
                        BinaryTreeNode(successor.value, left, newRight)
                    }
                }
            }
        }
    }
    
    private fun findMin(node: BinaryTreeNode<T>): BinaryTreeNode<T> {
        var current = node
        while (current.left != null) {
            current = current.left!!
        }
        return current
    }
    
    /**
     * Check if element exists using bbcursive pattern
     */
    fun contains(element: T): Boolean = searchRecursive(root, element) != null
    
    private fun searchRecursive(node: BinaryTreeNode<T>?, element: T): T? {
        if (node == null) return null
        
        val currentValue = node.value
        val left = node.left
        val right = node.right
        
        return when {
            element == currentValue -> currentValue
            element < currentValue -> searchRecursive(left, element)
            else -> searchRecursive(right, element)
        }
    }
    
    /**
     * Get all elements as Indexed
     */
    fun elements(): Indexed<T> {
        val elements = mutableListOf<T>()
        collectElements(root, elements)
        return elements.size j { i -> elements[i] }
    }
    
    private fun collectElements(node: BinaryTreeNode<T>?, elements: MutableList<T>) {
        if (node == null) return
        
        val currentValue = node.value
        val left = node.left
        val right = node.right
        
        collectElements(left, elements)
        elements.add(currentValue)
        collectElements(right, elements)
    }
}

// === BINARY TREE ===

/**
 * Binary Tree implementation using Join patterns
 */
class BBCursiveBinaryTree<T> {
    private var root: TreeNode<T>? = null
    
    /**
     * Insert value using bbcursive pattern
     */
    fun insert(value: T) {
        root = insertRecursive(root, value)
    }
    
    private fun insertRecursive(node: TreeNode<T>?, value: T): TreeNode<T> {
        if (node == null) return TreeNode(value, 0 j { throw IndexOutOfBoundsException() })
        
        val currentValue = node.value
        val children = node.children
        
        return when {
            value is Comparable<*> && currentValue is Comparable<*> -> {
                @Suppress("UNCHECKED_CAST")
                val comparableValue = value as Comparable<Any>
                @Suppress("UNCHECKED_CAST")
                val comparableCurrentValue = currentValue as Comparable<Any>
                when {
                    comparableValue < comparableCurrentValue -> {
                        val newLeft = if (children.isEmpty()) null else insertRecursive(children.getOrNull(0), value)
                        val right = if (children.size < 2) null else children.getOrNull(1)
                        val newChildren = 2 j { i -> if (i == 0) newLeft!! else right!! }
                        TreeNode(currentValue, newChildren)
                    }
                    comparableValue > comparableCurrentValue -> {
                        val left = if (children.isEmpty()) null else children.getOrNull(0)
                        val newRight = if (children.size < 2) insertRecursive(null, value) else insertRecursive(children.getOrNull(1), value)
                        val newChildren = 2 j { i -> if (i == 0) left!! else newRight!! }
                        TreeNode(currentValue, newChildren)
                    }
                    else -> node // Value already exists
                }
            }
            else -> node // Can't compare non-comparable values
        }
    }
    
    /**
     * Search using bbcursive pattern
     */
    fun search(value: T): T? = searchRecursive(root, value)?.value
    
    private fun searchRecursive(node: TreeNode<T>?, value: T): TreeNode<T>? {
        if (node == null) return null
        
        val currentValue = node.value
        val children = node.children
        
        return when {
            value == currentValue -> node
            value is Comparable<*> && currentValue is Comparable<*> -> {
                @Suppress("UNCHECKED_CAST")
                val comparableValue = value as Comparable<Any>
                @Suppress("UNCHECKED_CAST")
                val comparableCurrentValue = currentValue as Comparable<Any>
                if (comparableValue < comparableCurrentValue) {
                    searchRecursive(children.getOrNull(0), value)
                } else {
                    searchRecursive(children.getOrNull(1), value)
                }
            }
            else -> null
        }
    }
    
    /**
     * Remove value using bbcursive pattern
     */
    fun remove(value: T): Boolean {
        val oldRoot = root
        root = removeRecursive(root, value)
        return root != oldRoot
    }
    
    private fun removeRecursive(node: TreeNode<T>?, value: T): TreeNode<T>? {
        if (node == null) return null
        
        val currentValue = node.value
        val children = node.children
        val left = children.getOrNull(0)
        val right = children.getOrNull(1)

        return when {
            value is Comparable<*> && currentValue is Comparable<*> -> {
                @Suppress("UNCHECKED_CAST")
                val comparableValue = value as Comparable<Any>
                @Suppress("UNCHECKED_CAST")
                val comparableCurrentValue = currentValue as Comparable<Any>
                when {
                    comparableValue < comparableCurrentValue -> {
                        val newLeft = removeRecursive(left, value)
                        TreeNode(currentValue, 2 j { i -> if (i == 0) newLeft!! else right!! })
                    }
                    comparableValue > comparableCurrentValue -> {
                        val newRight = removeRecursive(right, value)
                        TreeNode(currentValue, 2 j { i -> if (i == 0) left!! else newRight!! })
                    }
                    else -> {
                        // Found value to remove
                        when {
                            left == null -> right
                            right == null -> left
                            else -> {
                                // Node has two children, find successor
                                val successor = findMin(right)
                                val newRight = removeRecursive(right, successor.value)
                                TreeNode(successor.value, 2 j { i -> if (i == 0) left else newRight!! })
                            }
                        }
                    }
                }
            }
            value == currentValue -> {
                // Found value to remove
                when {
                    left == null -> right
                    right == null -> left
                    else -> {
                        // Node has two children, find successor
                        val successor = findMin(right)
                        val newRight = removeRecursive(right, successor.value)
                        TreeNode(successor.value, 2 j { i -> if (i == 0) left else newRight!! })
                    }
                }
            }
            else -> node // Can't compare non-comparable values
        }
    }
    
    private fun findMin(node: TreeNode<T>): TreeNode<T> {
        var current = node
        while (current.children.getOrNull(0) != null) {
            current = current.children[0]
        }
        return current
    }
    
    /**
     * Traverse using bbcursive scanner
     */
    fun traverse(scanner: TreeScanner<T, T>): Flow<T> = flow {
        val initialState = TreeScanState<T>(
            currentNode = root,
            depth = 0,
            path = 0 j { throw IndexOutOfBoundsException() },
            visited = 0 j { false }
        )
        
        var state = initialState
        while (state.currentNode != null) {
            val result = scanner.scan(state.currentNode, state)
            if (result != null) {
                val value = result.a
                if (value != null) {
                    emit(value)
                }
                state = result.b
            } else {
                break
            }
        }
    }
}

// === STACK ===

class BBCursiveStack<T> {
    private var elements: Indexed<T> = 0 j { throw IndexOutOfBoundsException() }
    private var top = -1

    fun push(element: T) {
        val newSize = top + 2
        elements = newSize j { i ->
            if (i <= top) elements[i] else element
        }
        top++
    }

    fun pop(): T? {
        if (isEmpty()) return null
        val element = elements[top]
        top--
        return element
    }

    fun peek(): T? {
        return if (isEmpty()) null else elements[top]
    }

    fun isEmpty(): Boolean = top < 0

    fun size(): Int = top + 1

    fun elements(): Indexed<T> {
        return (top + 1) j { i -> elements[i] }
    }
}

/**
 * Circular Queue implementation using Join patterns
 */
class BBCursiveCircularQueue<T>(private val capacity: Int) {
    private var elements: Indexed<T?> = capacity j { _ -> null }
    private var front = 0
    private var rear = -1
    private var count = 0
    
    /**
     * Enqueue element using bbcursive pattern
     */
    fun enqueue(element: T): Boolean {
        if (isFull()) return false
        
        rear = (rear + 1) % capacity
        elements = capacity j { i ->
            if (i == rear) element else elements[i]
        }
        count++
        return true
    }
    
    /**
     * Dequeue element using bbcursive pattern
     */
    fun dequeue(): T? {
        if (isEmpty()) return null
        
        val element = elements[front]
        front = (front + 1) % capacity
        count--
        return element
    }
    
    /**
     * Peek at front element using bbcursive pattern
     */
    fun peek(): T? {
        return if (isEmpty()) null else elements[front]
    }
    
    /**
     * Check if queue is empty
     */
    fun isEmpty(): Boolean = count == 0
    
    /**
     * Check if queue is full
     */
    fun isFull(): Boolean = count == capacity
    
    /**
     * Get queue size
     */
    fun size(): Int = count
    
    /**
     * Get all elements as Indexed (from front to rear)
     */
    fun elements(): Indexed<T> {
        val result = mutableListOf<T>()
        var current = front
        var remaining = count
        
        while (remaining > 0) {
            val element = elements[current]
            if (element != null) {
                result.add(element)
            }
            current = (current + 1) % capacity
            remaining--
        }
        
        return result.size j { i -> result[i] }
    }
}

// === HEAP ===

/**
 * Heap entry using Join patterns
 */
typealias HeapEntry<T> = Join<T, Int>

/**
 * Min Heap implementation using Join patterns
 */
class BBCursiveMinHeap<T : Comparable<T>> {
    private var elements: Indexed<HeapEntry<T>> = emptyIndexed<HeapEntry<T>>()
    private var size = 0
    
    /**
     * Insert element using bbcursive pattern
     */
    fun insert(element: T) {
        val entry = makeJoin(element, size)
        elements = makeIndexed(size + 1) { i ->
            if (i < size) elements[i] else entry
        }
        size++
        heapifyUp(size - 1)
    }
    
    /**
     * Extract minimum element using bbcursive pattern
     */
    fun extractMin(): T? {
        if (isEmpty()) return null
        
        val minElement = elements[0].a
        val lastElement = elements[size - 1]
        
        elements = makeIndexed(size - 1) { i ->
            if (i == 0) lastElement else elements[i]
        }
        size--
        
        if (size > 0) {
            heapifyDown(0)
        }
        
        return minElement
    }
    
    /**
     * Peek at minimum element using bbcursive pattern
     */
    fun peekMin(): T? {
        return if (isEmpty()) null else elements[0].a
    }
    
    /**
     * Check if heap is empty
     */
    fun isEmpty(): Boolean = size == 0
    
    /**
     * Get heap size
     */
    fun size(): Int = size
    
    private fun heapifyUp(index: Int) {
        var current = index
        while (current > 0) {
            val parent = (current - 1) / 2
            if (elements[current].a < elements[parent].a) {
                // Swap elements
                val temp = elements[current]
                elements = makeIndexed(size) { i ->
                    when (i) {
                        current -> elements[parent]
                        parent -> temp
                        else -> elements[i]
                    }
                }
                current = parent
            } else {
                break
            }
        }
    }
    
    private fun heapifyDown(index: Int) {
        var current = index
        while (true) {
            val leftChild = 2 * current + 1
            val rightChild = 2 * current + 2
            var smallest = current
            
            if (leftChild < size && elements[leftChild].a < elements[smallest].a) {
                smallest = leftChild
            }
            
            if (rightChild < size && elements[rightChild].a < elements[smallest].a) {
                smallest = rightChild
            }
            
            if (smallest == current) break
            
            // Swap elements
            val temp = elements[current]
            elements = makeIndexed(size) { i ->
                when (i) {
                    current -> elements[smallest]
                    smallest -> temp
                    else -> elements[i]
                }
            }
            current = smallest
        }
    }
    
    /**
     * Get all elements as Indexed (in heap order)
     */
    fun elements(): Indexed<T> {
        return makeIndexed(size) { i -> elements[i].a }
    }
}

// === REGISTER PACKING PROFILING FOR LINEAR COLLECTIONS ===

/**
 * Profiler for linear collections
 */
object LinearCollectionPackingProfiler {
    
    /**
     * Profile Hash Set for register packing opportunities
     */
    fun <T> profileHashSet(set: BBCursiveHashSet<T>): JoinPackingReport {
        val report = JoinPackingReport()
        
        // Profile hash operations
        report.registerUsagePatterns.add("hash_computation" j "bucket_indexing")
        report.registerUsagePatterns.add("collision_resolution" j "linear_probing")
        report.registerUsagePatterns.add("resize_operations" j "rehashing")
        
        return report
    }
    
    /**
     * Profile Tree Set for register packing opportunities
     */
    fun <T : Comparable<T>> profileTreeSet(set: BBCursiveTreeSet<T>): JoinPackingReport {
        val report = JoinPackingReport()
        
        // Profile tree operations
        report.registerUsagePatterns.add("binary_search" j "tree_traversal")
        report.registerUsagePatterns.add("node_insertion" j "rebalancing")
        report.registerUsagePatterns.add("in_order_traversal" j "sorted_access")
        
        return report
    }
    
    /**
     * Profile Stack for register packing opportunities
     */
    fun <T> profileStack(stack: BBCursiveStack<T>): JoinPackingReport {
        val report = JoinPackingReport()
        
        // Profile stack operations
        report.registerUsagePatterns.add("push_operations" j "array_growth")
        report.registerUsagePatterns.add("pop_operations" j "lifo_access")
        report.registerUsagePatterns.add("peek_operations" j "constant_time")
        
        return report
    }
    
    /**
     * Profile Circular Queue for register packing opportunities
     */
    fun <T> profileCircularQueue(queue: BBCursiveCircularQueue<T>): JoinPackingReport {
        val report = JoinPackingReport()
        
        // Profile queue operations
        report.registerUsagePatterns.add("enqueue_operations" j "circular_wrapping")
        report.registerUsagePatterns.add("dequeue_operations" j "fifo_access")
        report.registerUsagePatterns.add("modulo_arithmetic" j "boundary_handling")
        
        return report
    }
    
    /**
     * Profile Min Heap for register packing opportunities
     */
    fun <T : Comparable<T>> profileMinHeap(heap: BBCursiveMinHeap<T>): JoinPackingReport {
        val report = JoinPackingReport()
        
        // Profile heap operations
        report.registerUsagePatterns.add("heapify_up" j "parent_child_swapping")
        report.registerUsagePatterns.add("heapify_down" j "child_comparison")
        report.registerUsagePatterns.add("priority_queue" j "min_extraction")
        
        return report
    }
} 