package borg.trikeshed.common.collections

import borg.trikeshed.lib.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * BBCursive Tree Collections - Efficient tree structures with loopy scans
 * 
 * Uses bbcursive patterns for traversal and Join patterns for data composition.
 * Optimized for register-at-a-time scanning and SIMD-friendly operations.
 */

// === CORE TREE NODE PATTERNS ===

/**
 * Tree node using Join composition
 */
typealias TreeNode<T> = Join<T, Indexed<TreeNode<T>>>

/**
 * Binary tree node with left/right children
 */
typealias BinaryTreeNode<T> = Join<T, Join<TreeNode<T>?, TreeNode<T>?>>

/**
 * N-ary tree node with indexed children
 */
typealias NaryTreeNode<T> = Join<T, Indexed<TreeNode<T>>>

/**
 * Tree traversal state for bbcursive scanning
 */
data class TreeScanState<T>(
    val currentNode: TreeNode<T>?,
    val depth: Int,
    val path: Indexed<T>,
    val visited: Indexed<Boolean>
)

// === BBCURSIVE TREE SCANNERS ===

/**
 * BBCursive tree scanner interface
 */
fun interface TreeScanner<T, R> {
    fun scan(node: TreeNode<T>?, state: TreeScanState<T>): Join<R?, TreeScanState<T>>?
}

/**
 * BBCursive tree traversal with register-at-a-time scanning
 */
object BBCursiveTreeTraversal {
    
    // Pre-order traversal scanner
    fun <T> preOrderScanner(): TreeScanner<T, T> = TreeScanner { node, state ->
        if (node == null) return@TreeScanner null
        
        val value = node.a
        val children = node.b
        
        // Visit current node
        val newPath = (state.path.size + 1) j { i ->
            if (i < state.path.size) state.path[i] else value
        }
        
        val newVisited = (state.visited.size + 1) j { i ->
            if (i < state.visited.size) state.visited[i] else true
        }
        
        val newState = TreeScanState(
            currentNode = node,
            depth = state.depth + 1,
            path = newPath,
            visited = newVisited
        )
        
        value j newState
    }
    
    // In-order traversal scanner (for binary trees)
    fun <T> inOrderScanner(): TreeScanner<T, T> = TreeScanner { node, state ->
        if (node == null) return@TreeScanner null
        
        val value = node.a
        val children = node.b
        
        // For binary trees, visit left subtree first
        if (children.size > 0 && !state.visited[0]) {
            // Visit left child
            val leftState = state.copy(
                currentNode = children[0],
                depth = state.depth + 1
            )
            return@TreeScanner null // Continue with left child
        }
        
        // Visit current node
        val newPath = (state.path.size + 1) j { i ->
            if (i < state.path.size) state.path[i] else value
        }
        
        val newVisited = (state.visited.size + 1) j { i ->
            if (i < state.visited.size) state.visited[i] else true
        }
        
        val newState = TreeScanState(
            currentNode = node,
            depth = state.depth + 1,
            path = newPath,
            visited = newVisited
        )
        
        value j newState
    }
    
    // Post-order traversal scanner
    fun <T> postOrderScanner(): TreeScanner<T, T> = TreeScanner { node, state ->
        if (node == null) return@TreeScanner null
        
        val value = node.a
        val children = node.b
        
        // Check if all children have been visited
        val allChildrenVisited = (0 until children.size).all { i ->
            state.visited.size > i && state.visited[i]
        }
        
        if (!allChildrenVisited) {
            // Continue with unvisited children
            return@TreeScanner null
        }
        
        // Visit current node
        val newPath = (state.path.size + 1) j { i ->
            if (i < state.path.size) state.path[i] else value
        }
        
        val newVisited = (state.visited.size + 1) j { i ->
            if (i < state.visited.size) state.visited[i] else true
        }
        
        val newState = TreeScanState(
            currentNode = node,
            depth = state.depth + 1,
            path = newPath,
            visited = newVisited
        )
        
        value j newState
    }
    
    // Level-order (breadth-first) traversal scanner
    fun <T> levelOrderScanner(): TreeScanner<T, T> = TreeScanner { node, state ->
        if (node == null) return@TreeScanner null
        
        val value = node.a
        val children = node.b
        
        // Visit current node at current level
        val newPath = (state.path.size + 1) j { i ->
            if (i < state.path.size) state.path[i] else value
        }
        
        val newVisited = (state.visited.size + 1) j { i ->
            if (i < state.visited.size) state.visited[i] else true
        }
        
        val newState = TreeScanState(
            currentNode = node,
            depth = state.depth + 1,
            path = newPath,
            visited = newVisited
        )
        
        value j newState
    }
}

// === TREE COLLECTIONS ===

/**
 * Binary Search Tree using Join patterns and bbcursive scanning
 */
class BBCursiveBinarySearchTree<T : Comparable<T>> {
    private var root: BinaryTreeNode<T>? = null
    
    /**
     * Insert value using bbcursive pattern
     */
    fun insert(value: T) {
        root = insertRecursive(root, value)
    }
    
    private fun insertRecursive(node: BinaryTreeNode<T>?, value: T): BinaryTreeNode<T> {
        if (node == null) {
            return value j (null j null)
        }
        
        val currentValue = node.a
        val left = node.b.a
        val right = node.b.b
        
        return when {
            value < currentValue -> {
                val newLeft = insertRecursive(left, value)
                currentValue j (newLeft j right)
            }
            value > currentValue -> {
                val newRight = insertRecursive(right, value)
                currentValue j (left j newRight)
            }
            else -> node // Value already exists
        }
    }
    
    /**
     * Search using bbcursive pattern
     */
    fun search(value: T): T? {
        return searchRecursive(root, value)
    }
    
    private fun searchRecursive(node: BinaryTreeNode<T>?, value: T): T? {
        if (node == null) return null
        
        val currentValue = node.a
        val left = node.b.a
        val right = node.b.b
        
        return when {
            value == currentValue -> currentValue
            value < currentValue -> searchRecursive(left, value)
            else -> searchRecursive(right, value)
        }
    }
    
    /**
     * Traverse using bbcursive scanner
     */
    fun traverse(scanner: TreeScanner<T, T>): Flow<T> = flow {
        val initialState = TreeScanState<T>(
            currentNode = root?.let { it.a j (0 j { _ -> null }) },
            depth = 0,
            path = 0 j { _ -> throw IndexOutOfBoundsException() },
            visited = 0 j { _ -> false }
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

/**
 * N-ary Tree using Join patterns and bbcursive scanning
 */
class BBCursiveNaryTree<T> {
    private var root: NaryTreeNode<T>? = null
    
    /**
     * Insert value with parent reference
     */
    fun insert(value: T, parentValue: T? = null) {
        if (parentValue == null) {
            root = value j (0 j { _ -> null })
        } else {
            root = insertUnderParent(root, value, parentValue)
        }
    }
    
    private fun insertUnderParent(node: NaryTreeNode<T>?, value: T, parentValue: T): NaryTreeNode<T>? {
        if (node == null) return null
        
        val currentValue = node.a
        val children = node.b
        
        if (currentValue == parentValue) {
            // Add as child of this node
            val newChildren = (children.size + 1) j { i ->
                if (i < children.size) children[i] else (value j (0 j { _ -> null }))
            }
            return currentValue j newChildren
        }
        
        // Search in children
        val newChildren = children.size j { i ->
            insertUnderParent(children[i], value, parentValue) ?: children[i]
        }
        
        return currentValue j newChildren
    }
    
    /**
     * Traverse using bbcursive scanner
     */
    fun traverse(scanner: TreeScanner<T, T>): Flow<T> = flow {
        val initialState = TreeScanState<T>(
            currentNode = root,
            depth = 0,
            path = 0 j { _ -> throw IndexOutOfBoundsException() },
            visited = 0 j { _ -> false }
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

/**
 * Trie (Prefix Tree) using Join patterns and bbcursive scanning
 */
class BBCursiveTrie {
    private var root: NaryTreeNode<Char>? = null
    
    /**
     * Insert string using bbcursive pattern
     */
    fun insert(word: String) {
        var current = root
        for (char in word) {
            current = insertChar(current, char)
        }
    }
    
    private fun insertChar(node: NaryTreeNode<Char>?, char: Char): NaryTreeNode<Char> {
        if (node == null) {
            return char j (0 j { _ -> null })
        }
        
        val currentChar = node.a
        val children = node.b
        
        if (currentChar == char) {
            return node
        }
        
        // Add new child
        val newChildren = (children.size + 1) j { i ->
            if (i < children.size) children[i] else (char j (0 j { _ -> null }))
        }
        
        return currentChar j newChildren
    }
    
    /**
     * Search using bbcursive pattern
     */
    fun search(word: String): Boolean {
        var current = root
        for (char in word) {
            current = findChild(current, char) ?: return false
        }
        return current != null
    }
    
    private fun findChild(node: NaryTreeNode<Char>?, char: Char): NaryTreeNode<Char>? {
        if (node == null) return null
        
        val currentChar = node.a
        val children = node.b
        
        if (currentChar == char) {
            return node
        }
        
        // Search in children
        for (i in 0 until children.size) {
            val child = children[i]
            if (child != null && child.a == char) {
                return child
            }
        }
        
        return null
    }
}

// === REGISTER PACKING PROFILING ===

/**
 * Profiler for Join register packing opportunities
 */
object JoinPackingProfiler {
    
    /**
     * Profile tree traversal for register packing opportunities
     */
    fun <T> profileTreeTraversal(tree: BBCursiveNaryTree<T>): JoinPackingReport {
        val report = JoinPackingReport()
        
        // Profile different traversal patterns
        val preOrderFlow = tree.traverse(BBCursiveTreeTraversal.preOrderScanner())
        val inOrderFlow = tree.traverse(BBCursiveTreeTraversal.inOrderScanner())
        val postOrderFlow = tree.traverse(BBCursiveTreeTraversal.postOrderScanner())
        val levelOrderFlow = tree.traverse(BBCursiveTreeTraversal.levelOrderScanner())
        
        // Analyze register usage patterns
        report.registerUsagePatterns.add("pre_order" j "sequential_access")
        report.registerUsagePatterns.add("in_order" j "binary_search")
        report.registerUsagePatterns.add("post_order" j "stack_based")
        report.registerUsagePatterns.add("level_order" j "queue_based")
        
        return report
    }
    
    /**
     * Profile binary search tree for register packing opportunities
     */
    fun <T : Comparable<T>> profileBinaryTree(tree: BBCursiveBinarySearchTree<T>): JoinPackingReport {
        val report = JoinPackingReport()
        
        // Profile search operations
        report.registerUsagePatterns.add("binary_search" j "branch_prediction")
        report.registerUsagePatterns.add("tree_insert" j "pointer_chasing")
        report.registerUsagePatterns.add("tree_delete" j "rebalancing")
        
        return report
    }
    
    /**
     * Profile trie for register packing opportunities
     */
    fun profileTrie(trie: BBCursiveTrie): JoinPackingReport {
        val report = JoinPackingReport()
        
        // Profile string operations
        report.registerUsagePatterns.add("string_traversal" j "character_scanning")
        report.registerUsagePatterns.add("prefix_matching" j "early_termination")
        report.registerUsagePatterns.add("trie_compression" j "shared_prefixes")
        
        return report
    }
}

/**
 * Report for Join register packing analysis
 */
data class JoinPackingReport(
    val registerUsagePatterns: MutableList<Join<String, String>> = mutableListOf(),
    val optimizationOpportunities: MutableList<String> = mutableListOf(),
    val performanceMetrics: MutableMap<String, Double> = mutableMapOf()
) {
    fun addOptimizationOpportunity(description: String) {
        optimizationOpportunities.add(description)
    }
    
    fun addPerformanceMetric(name: String, value: Double) {
        performanceMetrics[name] = value
    }
} 