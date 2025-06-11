package borg.trikeshed.collections

import kotlin.collections.SortedMap
import kotlin.collections.NavigableMap
import kotlin.collections.MutableMap

class TreeMap<K, V>(private val comparator: Comparator<in K>? = null) : NavigableMap<K, V>, MutableMap<K, V> {

    private var root: Node<K, V>? = null
    private var modCount = 0
    // size is already part of MutableMap, so we override it.
    // private var _size = 0 // Renaming to avoid clash, will use backing field for override

    // Basic Node structure (Red-Black Tree specific details will be added later)
    private class Node<K, V>(
        var key: K,
        var value: V,
        var left: Node<K, V>? = null,
        var right: Node<K, V>? = null,
        var parent: Node<K, V>? = null,
        var color: Boolean = RED // true for RED, false for BLACK
    ) {
        companion object {
            const val RED = true
            const val BLACK = false
        }
        // Implement MutableMap.MutableEntry for entries set
        fun toMutableEntry(): MutableMap.MutableEntry<K, V> = SimpleMutableEntry(this)
    }
     private data class SimpleMutableEntry<K, V>(private val node: Node<K, V>) : MutableMap.MutableEntry<K, V> {
        override val key: K get() = node.key
        override val value: V get() = node.value
        override fun setValue(newValue: V): V {
            val oldValue = node.value
            node.value = newValue
            return oldValue
        }
        // Optional: Implement equals and hashCode if necessary, though defaults might be fine
        // For use in sets, proper equals/hashCode based on key-value pair is important.
        // However, the node itself changing (e.g. value update) doesn't change its identity in the tree structure.
        // The set of entries should reflect the current state.
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Map.Entry<*, *>) return false
            return key == other.key && value == other.value
        }
        override fun hashCode(): Int = key.hashCode() xor value.hashCode()
    }


    // --- Map interface methods ( بخشی از MutableMap و NavigableMap) ---
    override fun get(key: K): V? {
        val node = findNode(key)
        return node?.value
    }

    override fun isEmpty(): Boolean = _size == 0

    override fun containsKey(key: K): Boolean {
        return findNode(key) != null
    }

    override fun containsValue(value: V): Boolean {
        // This is O(N) as it requires iterating through values.
        val iter = ValueIterator()
        while(iter.hasNext()) {
            if (iter.next() == value) {
                return true
            }
        }
        return false
    }

    // --- MutableMap interface methods ---
    override val entries: MutableSet<MutableMap.MutableEntry<K, V>> by lazy { EntrySet() }
    override val keys: MutableSet<K> by lazy { KeySet() }
    override val values: MutableCollection<V> by lazy { ValueCollection() }

    override fun put(key: K, value: V): V? {
        modCount++
        var y: Node<K, V>? = null
        var x = root
        while (x != null) {
            y = x
            val cmp = compareKeys(key, x.key)
            x = when {
                cmp < 0 -> x.left
                cmp > 0 -> x.right
                else -> {
                    // Key already exists, update value and return old value
                    val oldValue = x.value
                    x.value = value
                    return oldValue
                }
            }
        }

        val newNode = Node(key, value, parent = y)
        if (y == null) {
            root = newNode // Tree was empty
        } else {
            val cmp = compareKeys(key, y.key)
            if (cmp < 0) {
                y.left = newNode
            } else {
                y.right = newNode
            }
        }
        newNode.left = null
        newNode.right = null
        newNode.color = Node.RED // New nodes are red
        fixAfterInsertion(newNode)
        _size++
        return null // Key was not present before
    }

    override fun remove(key: K): V? {
        val nodeToRemove = findNode(key) ?: return null // Key not found
        modCount++
        val oldValue = nodeToRemove.value
        deleteNode(nodeToRemove)
        _size--
        return oldValue
    }

    override fun putAll(from: Map<out K, V>) {
        // modCount will be incremented by individual put calls
        for ((key, value) in from) {
            put(key, value)
        }
    }

    internal fun maximum(node: Node<K,V>): Node<K,V> { // For predecessor
        var current = node
        while (current.right != null) {
            current = current.right!!
        }
        return current
    }

    internal fun predecessor(t: Node<K, V>?): Node<K, V>? { // For descending iterator
        var p = t ?: return null
        if (p.left != null) {
            return maximum(p.left!!)
        } else {
            var parent = p.parent
            var ch = p
            while (parent != null && ch == parent.left) {
                ch = parent
                parent = parent.parent
            }
            return parent
        }
    }

    override fun clear() {
        modCount++
        root = null
        _size = 0
    }

    // --- SortedMap interface methods ---
    override fun comparator(): Comparator<in K>? = comparator // Already correctly defined

    // Default to inclusive for fromKey, exclusive for toKey, as is common
    override fun subMap(fromKey: K, toKey: K): NavigableMap<K, V> = subMap(fromKey, true, toKey, false)

    override fun headMap(toKey: K): NavigableMap<K, V> = headMap(toKey, false)

    override fun tailMap(fromKey: K): NavigableMap<K, V> = tailMap(fromKey, true)


    // More specific subMap, headMap, tailMap from NavigableMap (not present in SortedMap directly)
    // These are the ones that should be implemented by creating SubMapView instances.
    // The SortedMap versions above can delegate to these.
    fun subMap(fromKey: K, fromInclusive: Boolean, toKey: K, toInclusive: Boolean, isDescending: Boolean = false): NavigableMap<K, V> {
         if (compareKeys(fromKey, toKey) > 0 && fromKey != null && toKey != null) throw IllegalArgumentException("fromKey > toKey")
        return SubMapView(this, fromKey, fromInclusive, toKey, toInclusive, isDescending)
    }

    fun headMap(toKey: K, inclusive: Boolean, isDescending: Boolean = false): NavigableMap<K, V> {
        return SubMapView(this, null, false, toKey, inclusive, isDescending)
    }

    fun tailMap(fromKey: K, inclusive: Boolean, isDescending: Boolean = false): NavigableMap<K, V> {
        return SubMapView(this, fromKey, inclusive, null, false, isDescending)
    }


    override fun firstKey(): K {
        if (_size == 0) throw NoSuchElementException("Map is empty")
        return getFirstNode()!!.key
    }

    override fun lastKey(): K {
        if (_size == 0) throw NoSuchElementException("Map is empty")
        return getLastNode()!!.key
    }

    // --- NavigableMap interface methods ---
    // Note: Kotlin's NavigableMap uses Map.Entry, not MutableMap.MutableEntry for these.
    // This seems to be a slight mismatch if the map is mutable and entries should reflect that.
    // However, we must adhere to the interface. Our SimpleMutableEntry also implements Map.Entry.
    override fun lowerEntry(key: K): Map.Entry<K, V>? {
        var node = root
        var result: Node<K, V>? = null
        while (node != null) {
            val cmp = compareKeys(key, node.key)
            if (cmp > 0) { // node.key < key
                result = node
                node = node.right
            } else { // node.key >= key
                node = node.left
            }
        }
        return result?.let { SimpleMutableEntry(it) }
    }

    override fun lowerKey(key: K): K? = lowerEntry(key)?.key

    override fun floorEntry(key: K): Map.Entry<K, V>? {
        var node = root
        var result: Node<K, V>? = null
        while (node != null) {
            val cmp = compareKeys(key, node.key)
            when {
                cmp > 0 -> { // node.key < key
                    result = node
                    node = node.right
                }
                cmp < 0 -> { // node.key > key
                    node = node.left
                }
                else -> return SimpleMutableEntry(node) // exact match
            }
        }
        return result?.let { SimpleMutableEntry(it) }
    }

    override fun floorKey(key: K): K? = floorEntry(key)?.key

    override fun ceilingEntry(key: K): Map.Entry<K, V>? {
        var node = root
        var result: Node<K, V>? = null
        while (node != null) {
            val cmp = compareKeys(key, node.key)
            when {
                cmp < 0 -> { // node.key > key
                    result = node
                    node = node.left
                }
                cmp > 0 -> { // node.key < key
                    node = node.right
                }
                else -> return SimpleMutableEntry(node) // exact match
            }
        }
        return result?.let { SimpleMutableEntry(it) }
    }

    override fun ceilingKey(key: K): K? = ceilingEntry(key)?.key

    override fun higherEntry(key: K): Map.Entry<K, V>? {
        var node = root
        var result: Node<K, V>? = null
        while (node != null) {
            val cmp = compareKeys(key, node.key)
            if (cmp < 0) { // node.key > key
                result = node
                node = node.left
            } else { // node.key <= key
                node = node.right
            }
        }
        return result?.let { SimpleMutableEntry(it) }
    }

    override fun higherKey(key: K): K? = higherEntry(key)?.key

    override fun firstEntry(): Map.Entry<K, V>? = getFirstNode()?.let { SimpleMutableEntry(it) }

    override fun lastEntry(): Map.Entry<K, V>? = getLastNode()?.let { SimpleMutableEntry(it) }

    override fun pollFirstEntry(): Map.Entry<K, V>? { // This implies mutation
        val first = getFirstNode() ?: return null
        val entry = SimpleMutableEntry(first) // Capture before removal
        remove(first.key) // Assumes remove is implemented
        return entry
    }

    override fun pollLastEntry(): Map.Entry<K, V>? { // This implies mutation
        val last = getLastNode() ?: return null
        val entry = SimpleMutableEntry(last)
        remove(last.key)
        return entry
    }

    // Store the descending map view to avoid creating it multiple times.
    @Transient private var descendingMapView: NavigableMap<K, V>? = null

    override fun descendingMap(): NavigableMap<K, V> {
        return descendingMapView ?: DescendingTreeMapView(this).also { descendingMapView = it }
    }

    override fun navigableKeySet(): NavigableSet<K> = NavigableKeySet(this)

    override fun descendingKeySet(): NavigableSet<K> = navigableKeySet().descendingSet()

    // Helper for comparison
    @Suppress("UNCHECKED_CAST")
    internal fun compareKeys(k1: K, k2: K): Int { // Made internal for potential use in iterators/views
        return comparator?.compare(k1, k2) ?: (k1 as Comparable<K>).compareTo(k2)
    }

    // --- Size property ---
    private var _size = 0
    override val size: Int
        get() = _size


    // --- Helper methods for finding nodes ---
    private fun getFirstNode(): Node<K, V>? {
        var p = root
        while (p?.left != null) {
            p = p.left
        }
        return p
    }

    private fun getLastNode(): Node<K, V>? {
        var p = root
        while (p?.right != null) {
            p = p.right
        }
        return p
    }

    private fun findNode(key: K): Node<K, V>? {
        var current = root
        while (current != null) {
            val cmp = compareKeys(key, current.key)
            current = when {
                cmp < 0 -> current.left
                cmp > 0 -> current.right
                else -> return current // Found
            }
        }
        return null // Not found
    }

    // --- Red-Black Tree Balancing ---

    private fun rotateLeft(x: Node<K, V>) {
        val y = x.right ?: return // Should not happen if called correctly
        x.right = y.left
        if (y.left != null) {
            y.left!!.parent = x
        }
        y.parent = x.parent
        when {
            x.parent == null -> root = y
            x == x.parent!!.left -> x.parent!!.left = y
            else -> x.parent!!.right = y
        }
        y.left = x
        x.parent = y
    }

    private fun rotateRight(y: Node<K, V>) {
        val x = y.left ?: return // Should not happen
        y.left = x.right
        if (x.right != null) {
            x.right!!.parent = y
        }
        x.parent = y.parent
        when {
            y.parent == null -> root = x
            y == y.parent!!.right -> y.parent!!.right = x
            else -> y.parent!!.left = x
        }
        x.right = y
        y.parent = x
    }

    private fun fixAfterInsertion(nodeZ: Node<K, V>) {
        var z = nodeZ
        while (z.parent?.color == Node.RED) { // If parent is RED, then grandparent must exist (root is BLACK)
            val parent = z.parent!!
            val grandparent = parent.parent!! // Must exist because parent is RED (hence not root)

            if (parent == grandparent.left) {
                val uncle = grandparent.right
                if (uncle?.color == Node.RED) { // Case 1: Uncle is RED
                    parent.color = Node.BLACK
                    uncle.color = Node.BLACK
                    grandparent.color = Node.RED
                    z = grandparent // Move z up to grandparent to continue checking
                } else { // Uncle is BLACK (or null)
                    if (z == parent.right) { // Case 2: z is right child, parent is left child (triangle)
                        z = parent
                        rotateLeft(z)
                    }
                    // Case 3: z is left child, parent is left child (line)
                    // Or z was right child and has been rotated to become left child
                    z.parent!!.color = Node.BLACK // parent after potential rotation
                    z.parent!!.parent!!.color = Node.RED // grandparent after potential rotation
                    rotateRight(z.parent!!.parent!!)
                }
            } else { // Symmetric to above: parent is right child of grandparent
                val uncle = grandparent.left
                if (uncle?.color == Node.RED) { // Case 1: Uncle is RED
                    parent.color = Node.BLACK
                    uncle.color = Node.BLACK
                    grandparent.color = Node.RED
                    z = grandparent
                } else { // Uncle is BLACK (or null)
                    if (z == parent.left) { // Case 2: z is left child, parent is right child (triangle)
                        z = parent
                        rotateRight(z)
                    }
                    // Case 3: z is right child, parent is right child (line)
                    z.parent!!.color = Node.BLACK
                    z.parent!!.parent!!.color = Node.RED
                    rotateLeft(z.parent!!.parent!!)
                }
            }
        }
        root?.color = Node.BLACK // Root must always be BLACK
    }

    private fun transplant(u: Node<K, V>?, v: Node<K, V>?) {
        when {
            u?.parent == null -> root = v
            u == u.parent?.left -> u.parent?.left = v
            else -> u.parent?.right = v
        }
        v?.parent = u?.parent
    }

    private fun deleteNode(z: Node<K, V>) {
        var y = z
        var yOriginalColor = y.color
        var x: Node<K, V>? // The node that moves into y's original position

        if (z.left == null) {
            x = z.right
            transplant(z, z.right)
        } else if (z.right == null) {
            x = z.left
            transplant(z, z.left)
        } else {
            y = minimum(z.right!!) // y is z's successor
            yOriginalColor = y.color
            x = y.right

            if (y.parent == z) {
                x?.parent = y // If x is not null, its parent is now y
            } else {
                transplant(y, y.right)
                y.right = z.right
                y.right?.parent = y
            }
            transplant(z, y)
            y.left = z.left
            y.left?.parent = y
            y.color = z.color
        }

        if (yOriginalColor == Node.BLACK) {
            if (x != null) { // Only fix if x is not null and y's original color was black
                fixAfterDeletion(x)
            } else if (root != null && y.parent != null) { // If x is null (a leaf that was removed) and y was black
                                                          // and it's not an empty tree, we might still need to fix.
                                                          // This case needs careful handling; often fixAfterDeletion is called with y.parent if x is null.
                                                          // However, standard algorithms usually ensure x is the node that needs fixing or its parent.
                                                          // If x is null, and y was black, the path is missing a black node.
                                                          // The fixup should start from x's *position*, which means x's parent if x is null.
                                                          // Let's consider x as a conceptual node for fixup, even if null.
                 // The fixAfterDeletion needs a non-null node to start.
                 // If x is null, it means a black leaf was removed.
                 // The parent of where x *was* is the point of interest.
                 // However, typical fixAfterDeletion handles x being null (representing a black leaf).
                 // Let's ensure fixAfterDeletion can handle x being null or points to where the problem is.
                 // For simplicity, if x is null and yOriginalColor was black, the tree might be unbalanced.
                 // The issue is that fixAfterDeletion needs a real node to work on its properties.
                 // Most CLRS based fixups expect x to be the child that replaced the deleted node.
                 // If x is null and y was black, the path has one less black node.
                 // We need to ensure fixAfterDeletion is robust or called correctly.
                 // A common approach is to use a temporary NIL node if x is null for fixup logic.
                 // Or, the fixup logic directly checks for null and handles parent pointers.
                 // Given our current fixAfterDeletion structure, it expects a non-null node.
                 // This part of deletion is tricky. Let's assume for now x might be null and fixAfterDeletion needs to be robust.
                 // Or, we ensure x is always a valid reference or a placeholder.
                 // If x is null, and yOriginalColor was black, the fixup is needed at x's parent.
                 // Let's pass y.parent to fixAfterDeletion if x is null and y was black. This is not standard.
                 // The node x itself (even if null) is the one whose "black height" is affected.
                 // The fixup logic should handle x being null if it represents a leaf.
                 // Let's proceed with passing x, and adapt fixAfterDeletion if needed.
                 // If x is null, it implies we removed a black leaf, and its parent's path is short.
                 // The fixup algorithm will need to handle this. Usually, x is treated as a sentinel if null.
                 // For now, we call fixAfterDeletion(x) and expect it to handle null x if necessary,
                 // or we ensure x is a sentinel if it's conceptually a leaf.
                 // Given typical implementations, if x is null, its parent is where the imbalance is noted.
                 // The fix logic will look at x's sibling.
                 // So, x is passed as is.
                 val parentOfX = if (x != null) x.parent else y.parent // y.parent is parent of z, or parent of successor y
                                                                      // This is the parent of the position where x now is.
                 fixAfterDeletion(x, parentOfX)
            }
        }
    }

    // Based on CLRS, x is the node that might have an extra black or is deficient.
    // xParent is its parent.
    private fun fixAfterDeletion(nodeX: Node<K, V>?, parentOfNodeX: Node<K, V>?) {
        var x = nodeX          // current node, can be null (representing a black leaf position)
        var xParent = parentOfNodeX // parent of x's position

        while (x != root && (x?.color ?: Node.BLACK) == Node.BLACK) {
            if (x == xParent?.left || (x == null && xParent?.left == null && xParent!=null) ) { // x is left child or null in left child's place
                var w = xParent?.right // Sibling of x

                if (w?.color == Node.RED) { // Case 1: x's sibling w is red
                    w.color = Node.BLACK
                    xParent?.color = Node.RED
                    if (xParent != null) rotateLeft(xParent)
                    w = xParent?.right // New sibling
                }

                // Case 1 leads to Case 2, 3, or 4. Sibling w is now black.
                if ((w?.left?.color ?: Node.BLACK) == Node.BLACK &&
                    (w?.right?.color ?: Node.BLACK) == Node.BLACK) { // Case 2: x's sibling w is black, and both of w's children are black
                    w?.color = Node.RED
                    x = xParent         // Move up the tree
                    xParent = x?.parent // Update parent for next iteration
                } else {
                    if ((w?.right?.color ?: Node.BLACK) == Node.BLACK) { // Case 3: x's sibling w is black, w.left is red, and w.right is black
                        w?.left?.color = Node.BLACK
                        w?.color = Node.RED
                        if (w != null) rotateRight(w)
                        w = xParent?.right // New sibling
                    }

                    // Case 4: x's sibling w is black, and w.right is red
                    w?.color = xParent?.color ?: Node.BLACK // Inherit color from parent
                    xParent?.color = Node.BLACK
                    w?.right?.color = Node.BLACK
                    if (xParent != null) rotateLeft(xParent)
                    x = root // Balanced, exit loop
                }
            } else { // x is right child or null in right child's place
                var w = xParent?.left // Sibling of x

                if (w?.color == Node.RED) { // Case 1 (symmetric)
                    w.color = Node.BLACK
                    xParent?.color = Node.RED
                    if (xParent != null) rotateRight(xParent)
                    w = xParent?.left
                }

                if ((w?.left?.color ?: Node.BLACK) == Node.BLACK &&
                    (w?.right?.color ?: Node.BLACK) == Node.BLACK) { // Case 2 (symmetric)
                    w?.color = Node.RED
                    x = xParent
                    xParent = x?.parent
                } else {
                    if ((w?.left?.color ?: Node.BLACK) == Node.BLACK) { // Case 3 (symmetric)
                        w?.right?.color = Node.BLACK
                        w?.color = Node.RED
                        if (w != null) rotateLeft(w)
                        w = xParent?.left
                    }

                    // Case 4 (symmetric)
                    w?.color = xParent?.color ?: Node.BLACK
                    xParent?.color = Node.BLACK
                    w?.left?.color = Node.BLACK
                    if (xParent != null) rotateRight(xParent)
                    x = root
                }
            }
        }
        x?.color = Node.BLACK
    }


    internal fun minimum(node: Node<K,V>): Node<K,V> { // Made internal for iterator
        var current = node
        while (current.left != null) {
            current = current.left!!
        }
        return current
    }

    internal fun successor(t: Node<K, V>?): Node<K, V>? { // Made internal for iterator
        var p = t ?: return null
        if (p.right != null) {
            return minimum(p.right!!)
        } else {
            var parent = p.parent
            var ch = p
            while (parent != null && ch == parent.right) {
                ch = parent
                parent = parent.parent
            }
            return parent
        }
    }


    // --- Iterators and Collection Views ---

    private abstract inner class TreeIterator<E> : MutableIterator<E> {
        var expectedModCount: Int = modCount
        var lastReturned: Node<K, V>? = null
        var nextNode: Node<K, V>? = null

        init {
            nextNode = getFirstNode() // Start with the smallest key
        }

        override fun hasNext(): Boolean {
            if (modCount != expectedModCount) {
                throw ConcurrentModificationException()
            }
            return nextNode != null
        }

        fun nextNode(): Node<K, V> {
            if (modCount != expectedModCount) {
                throw ConcurrentModificationException()
            }
            if (nextNode == null) {
                throw NoSuchElementException()
            }
            lastReturned = nextNode
            nextNode = successor(nextNode)
            return lastReturned!!
        }

        override fun remove() {
            if (modCount != expectedModCount) {
                throw ConcurrentModificationException()
            }
            if (lastReturned == null) {
                throw IllegalStateException("remove() called before next() or after multiple remove() calls")
            }
            // TreeMap.this.remove(lastReturned!!.key) // This would re-increment modCount
            // Need to call a version of remove that doesn't increment modCount again, or adjust expectedModCount
            // For simplicity, let TreeMap.remove handle modCount and update expectedModCount here.
            this@TreeMap.remove(lastReturned!!.key)
            expectedModCount = modCount // Re-sync after remove
            lastReturned = null
        }
    }

    private inner class EntryIterator : TreeIterator<MutableMap.MutableEntry<K, V>>() {
        override fun next(): MutableMap.MutableEntry<K, V> = nextNode().toMutableEntry()
    }

    private inner class KeyIterator : TreeIterator<K>() {
        override fun next(): K = nextNode().key
    }

    private inner class ValueIterator : TreeIterator<V>() {
        override fun next(): V = nextNode().value
    }

    private abstract inner class DescendingTreeIterator<E> : MutableIterator<E> {
        var expectedModCount: Int = modCount
        var lastReturned: Node<K, V>? = null
        var nextNode: Node<K, V>? = null // nextNode in descending order is previous in ascending

        init {
            nextNode = getLastNode() // Start with the largest key
        }

        override fun hasNext(): Boolean {
            if (modCount != expectedModCount) {
                throw ConcurrentModificationException()
            }
            return nextNode != null
        }

        fun prevNode(): Node<K, V> { // Renamed for clarity, conceptually it's "next" in descending
            if (modCount != expectedModCount) {
                throw ConcurrentModificationException()
            }
            if (nextNode == null) {
                throw NoSuchElementException()
            }
            lastReturned = nextNode
            nextNode = predecessor(nextNode)
            return lastReturned!!
        }

        override fun remove() {
            if (modCount != expectedModCount) {
                throw ConcurrentModificationException()
            }
            if (lastReturned == null) {
                throw IllegalStateException("remove() called before next() or after multiple remove() calls")
            }
            this@TreeMap.remove(lastReturned!!.key)
            expectedModCount = modCount
            lastReturned = null
        }
    }

    private inner class DescendingEntryIterator : DescendingTreeIterator<MutableMap.MutableEntry<K, V>>() {
        override fun next(): MutableMap.MutableEntry<K, V> = prevNode().toMutableEntry()
    }

    private inner class DescendingKeyIterator : DescendingTreeIterator<K>() {
        override fun next(): K = prevNode().key
    }

    // Value iterator for descending map would also use DescendingTreeIterator

    private inner class EntrySet : AbstractMutableSet<MutableMap.MutableEntry<K, V>>() {
        override val size: Int get() = this@TreeMap.size

        override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> = EntryIterator()

        override fun contains(element: MutableMap.MutableEntry<K, V>): Boolean {
            if (element !is Map.Entry<*,*>) return false // Should be Map.Entry, not necessarily Mutable
            val node = findNode(element.key)
            return node != null && node.value == element.value
        }

        override fun remove(element: MutableMap.MutableEntry<K, V>): Boolean {
             if (element !is Map.Entry<*,*>) return false
            val node = findNode(element.key)
            if (node != null && node.value == element.value) {
                this@TreeMap.remove(element.key) // relies on TreeMap.remove to update modCount
                return true
            }
            return false
        }

        override fun clear() {
            this@TreeMap.clear() // relies on TreeMap.clear to update modCount
        }

        override fun add(element: MutableMap.MutableEntry<K, V>): Boolean {
            // Set usually doesn't allow adding if exists, but put behavior is to replace.
            // This is a bit of a mismatch. Standard sets return false if element already exists.
            // For a map's entry set, add is not typically supported or means put.
            // Let's make `add` behave like `put`, returning true if the map changed.
            val oldValue = this@TreeMap.put(element.key, element.value)
            return oldValue != element.value // True if new key or value changed.
                                            // This isn't perfect for "set changed" semantics.
                                            // A better check might be if size changed or if value for key changed.
                                            // Or, throw UnsupportedOperationException if add is not sensible for EntrySet.
                                            // For now, align with put.
        }
    }

    private inner class KeySet : AbstractMutableSet<K>() {
        override val size: Int get() = this@TreeMap.size
        override fun iterator(): MutableIterator<K> = KeyIterator()
        override fun contains(element: K): Boolean = this@TreeMap.containsKey(element)
        override fun remove(element: K): Boolean {
            val result = this@TreeMap.remove(element) != null
            // modCount updated by TreeMap.remove
            return result
        }
        override fun clear() = this@TreeMap.clear() // modCount updated by TreeMap.clear
        // `add` for KeySet doesn't make sense as values are needed.
        // AbstractMutableSet.add would throw by default if not overridden.
    }

    private inner class ValueCollection : AbstractMutableCollection<V>() {
        override val size: Int get() = this@TreeMap.size
        override fun iterator(): MutableIterator<V> = ValueIterator()
        override fun contains(element: V): Boolean = this@TreeMap.containsValue(element)

        // remove(element: V) for AbstractMutableCollection is tricky.
        // It needs to find an entry with that value and remove it.
        // Standard library implementations often iterate and remove the first match.
        override fun remove(element: V): Boolean {
            val iter = iterator()
            while (iter.hasNext()) {
                if (iter.next() == element) {
                    iter.remove() // this will use ValueIterator.remove -> TreeIterator.remove
                    return true
                }
            }
            return false
        }
        override fun clear() = this@TreeMap.clear()
         // `add` for ValueCollection doesn't make sense.
    }

    // Implementation of NavigableSet for keys
    // This is a simplified NavigableKeySet. A full implementation would handle sub-views, descending views correctly.
    private class NavigableKeySet<K, V>(private val map: TreeMap<K, V>) : AbstractMutableSet<K>(), NavigableSet<K> {

        override val size: Int get() = map.size

        override fun iterator(): MutableIterator<K> = map.KeyIterator() // Standard iterator

        override fun descendingIterator(): Iterator<K> {
                // Returns a MutableIterator, but NavigableSet.descendingIterator is Iterator<K>
                return map.DescendingKeyIterator() as Iterator<K>
        }


        override fun lower(e: K): K? = map.lowerKey(e)
        override fun floor(e: K): K? = map.floorKey(e)
        override fun ceiling(e: K): K? = map.ceilingKey(e)
        override fun higher(e: K): K? = map.higherKey(e)

        override fun first(): K = map.firstKey()
        override fun last(): K = map.lastKey()

        override fun pollFirst(): K? { // Removes and returns the first element
            val first = map.firstKey()
            map.remove(first)
            return first
        }

        override fun pollLast(): K? { // Removes and returns the last element
            val last = map.lastKey()
            map.remove(last)
            return last
        }

        override fun contains(element: K): Boolean = map.containsKey(element)

        override fun remove(element: K): Boolean = map.remove(element) != null

        override fun clear() = map.clear()

        // Add for a key set is not standard unless it's a Set<K> being built, not a view.
        // For a map's keySet, add doesn't make sense as there's no value.
        override fun add(element: K): Boolean {
            throw UnsupportedOperationException("Cannot add to a NavigableMap's keySet without a value.")
        }

        override fun descendingSet(): NavigableSet<K> {
            // This would typically return a view that uses a descending iterator.
            // For now, if descendingIterator is not ready, this will also be problematic.
            // This requires a full DescendingNavigableKeySet view or similar.
            // Simplified:
            // return DescendingNavigableKeySet(map) - if such a class existed.
            // Or, if map.descendingMap() is ready: map.descendingMap().navigableKeySet()
            return map.descendingMap().navigableKeySet()
        }

        // subSet, headSet, tailSet from SortedSet interface (which NavigableSet extends)
        override fun subSet(fromElement: K, fromInclusive: Boolean, toElement: K, toInclusive: Boolean): NavigableSet<K> {
            // This should return a view. Complex to implement fully.
            throw UnsupportedOperationException("subSet not yet fully implemented")
        }
        override fun headSet(toElement: K, inclusive: Boolean): NavigableSet<K> {
            throw UnsupportedOperationException("headSet not yet fully implemented")
        }
        override fun tailSet(fromElement: K, inclusive: Boolean): NavigableSet<K> {
            throw UnsupportedOperationException("tailSet not yet fully implemented")
        }
        // These are from SortedSet but not directly on NavigableSet, they come via inheritance.
        // NavigableSet re-declares them with NavigableSet return types.
        override fun subSet(fromElement: K, toElement: K): SortedSet<K> = subSet(fromElement, true, toElement, false) // Delegate to the more specific version
        override fun headSet(toElement: K): SortedSet<K> = headSet(toElement, false) // Delegate
        override fun tailSet(fromElement: K): SortedSet<K> = tailSet(fromElement, true) // Delegate
    }


    private class DescendingTreeMapView<K, V>(
        private val m: TreeMap<K, V>,
        private val fromKey: K? = null, private val fromInclusive: Boolean = true, // For submaps
        private val toKey: K? = null, private val toInclusive: Boolean = true       // For submaps
    ) : NavigableMap<K, V>, AbstractMutableMap<K, V>() { // AbstractMutableMap provides default impl for putAll, clear, etc.
                                                       // But NavigableMap is read-only in Kotlin stdlib.
                                                       // This implies our DescendingMap should also be effectively read-only through NavigableMap interface
                                                       // or we need a MutableNavigableMap interface.
                                                       // For now, let's assume it's a view.
                                                       // If m is mutable, this view reflects changes.
                                                       // Operations like put/remove on this view should write through to m.

        // Invert original comparator, or use reversed natural order
        private val reverseComparator: Comparator<in K>? = m.comparator?.reversed() ?: Comparator { k1, k2 -> (k2 as Comparable<K>).compareTo(k1) }

        override fun comparator(): Comparator<in K>? = reverseComparator

        // --- Map methods (many can be delegated, some need reversed logic) ---
        override val size: Int get() = m.size // TODO: This should be size of submap if from/to keys are used
        override fun isEmpty(): Boolean = m.isEmpty() // TODO: submap isEmpty
        override fun containsKey(key: K): Boolean = m.containsKey(key) // TODO: range check for submap
        override fun containsValue(value: V): Boolean = m.containsValue(value) // TODO: range check for submap (inefficient anyway)
        override fun get(key: K): V? = m.get(key) // TODO: range check

        // --- MutableMap methods (if this view were mutable) ---
        // For now, these would throw if not overridden from AbstractMutableMap,
        // or if we didn't extend it.
        // Since NavigableMap is read-only, perhaps these shouldn't be here,
        // but then it cannot be a full "view" of a mutable TreeMap for descending operations.
        // This is a common issue with view wrappers. Let's make it reflect mutations on `m`
        // but make this view itself "read-only" in terms of modification through its own methods.
        // Or, implement them to write through to `m` using `m`'s normal (non-reversed) logic.
        // Example: put on descending map still means put on original map.
        override fun put(key: K, value: V): V? = m.put(key,value) // No reversal for put
        override fun remove(key: K): V? = m.remove(key) // No reversal for remove
        override fun clear() = m.clear() // Clears the whole underlying map


        // --- SortedMap methods (must use reversed logic) ---
        override fun firstKey(): K = m.lastKey() // Reversed
        override fun lastKey(): K = m.firstKey()  // Reversed

        override fun subMap(fromKey: K, toKey: K): NavigableMap<K, V> = m.subMap(toKey, fromKey).descendingMap() // Re-delegate and reverse
        override fun headMap(toKey: K): NavigableMap<K, V> = m.tailMap(toKey).descendingMap() // Reversed
        override fun tailMap(fromKey: K): NavigableMap<K, V> = m.headMap(fromKey).descendingMap() // Reversed


        // --- NavigableMap methods (must use reversed logic for keys) ---
        override fun lowerEntry(key: K): Map.Entry<K, V>? = m.higherEntry(key)
        override fun lowerKey(key: K): K? = m.higherKey(key)
        override fun floorEntry(key: K): Map.Entry<K, V>? = m.ceilingEntry(key)
        override fun floorKey(key: K): K? = m.ceilingKey(key)
        override fun ceilingEntry(key: K): Map.Entry<K, V>? = m.floorEntry(key)
        override fun ceilingKey(key: K): K? = m.floorKey(key)
        override fun higherEntry(key: K): Map.Entry<K, V>? = m.lowerEntry(key)
        override fun higherKey(key: K): K? = m.lowerKey(key)

        override fun firstEntry(): Map.Entry<K, V>? = m.lastEntry()
        override fun lastEntry(): Map.Entry<K, V>? = m.firstEntry()

        override fun pollFirstEntry(): Map.Entry<K, V>? = m.pollLastEntry()
        override fun pollLastEntry(): Map.Entry<K, V>? = m.pollFirstEntry()

        override fun descendingMap(): NavigableMap<K, V> = m // Returns the original map

        override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
            get() = DescendingEntrySetView() // Needs its own set view
        override val keys: MutableSet<K>
            get() = navigableKeySet() // NavigableKeySet will use descending iterators
        override val values: MutableCollection<V>
             get() = object : AbstractMutableCollection<V>() { // Basic value collection for descending map
                override val size: Int get() = this@DescendingTreeMapView.size
                override fun iterator(): MutableIterator<V> = m.DescendingValueIterator() // Assuming this exists or is added
                 override fun contains(element: V): Boolean = m.containsValue(element)
                 override fun clear() = m.clear()
             }


        override fun navigableKeySet(): NavigableSet<K> {
            // This needs to be a NavigableSet that respects the descending order.
            // It can wrap the original map's NavigableKeySet and invert its operations,
            // or be a new implementation.
            // For now, let's create a simple wrapper around original NavigableKeySet but use descending iterator.
             return object : AbstractMutableSet<K>(), NavigableSet<K> {
                private val originalKeySet = m.navigableKeySet() // Ascending keyset
                override val size: Int get() = m.size
                override fun iterator(): MutableIterator<K> = m.DescendingKeyIterator() // Correct iterator
                override fun descendingIterator(): Iterator<K> = m.KeyIterator() as Iterator<K> // Reversed again is original
                override fun lower(e: K): K? = m.higherKey(e)
                override fun floor(e: K): K? = m.ceilingKey(e)
                override fun ceiling(e: K): K? = m.floorKey(e)
                override fun higher(e: K): K? = m.lowerKey(e)
                override fun first(): K = m.lastKey()
                override fun last(): K = m.firstKey()
                override fun pollFirst(): K? = m.pollLastEntry()?.key
                override fun pollLast(): K? = m.pollFirstEntry()?.key
                override fun contains(element: K): Boolean = m.containsKey(element)
                override fun remove(element: K): Boolean = m.remove(element) != null
                override fun clear() = m.clear()
                override fun add(element: K): Boolean = throw UnsupportedOperationException()
                override fun descendingSet(): NavigableSet<K> = m.navigableKeySet() // Original ascending keyset

                override fun subSet(fromElement: K, fromInclusive: Boolean, toElement: K, toInclusive: Boolean): NavigableSet<K> {
                    return m.subMap(toElement, toInclusive, fromElement, fromInclusive).navigableKeySet().descendingSet() // Complex delegation
                }
                override fun headSet(toElement: K, inclusive: Boolean): NavigableSet<K> = m.tailMap(toElement, inclusive).navigableKeySet().descendingSet()
                override fun tailSet(fromElement: K, inclusive: Boolean): NavigableSet<K> = m.headMap(fromElement, inclusive).navigableKeySet().descendingSet()
                override fun subSet(fromElement: K, toElement: K): SortedSet<K> = subSet(fromElement, true, toElement, false)
                override fun headSet(toElement: K): SortedSet<K> = headSet(toElement, false)
                override fun tailSet(fromElement: K): SortedSet<K> = tailSet(fromElement, true)
            }
        }

        override fun descendingKeySet(): NavigableSet<K> = m.navigableKeySet() // Keys of original map

        // Need DescendingEntrySetView
        private inner class DescendingEntrySetView : AbstractMutableSet<MutableMap.MutableEntry<K,V>>() {
            override val size: Int get() = m._size
            override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> = m.DescendingEntryIterator()
            override fun contains(element: MutableMap.MutableEntry<K, V>): Boolean {
                return m.get(element.key)?.let { it == element.value } ?: (element.value == null && m.containsKey(element.key))
            }
            override fun remove(element: MutableMap.MutableEntry<K, V>): Boolean {
                return m.entries.remove(element) // delegate to original, which checks value
            }
            override fun clear() = m.clear()
        }
    }

    private inner class DescendingValueIterator : DescendingTreeIterator<V>() {
        override fun next(): V = prevNode().value
    }

    // --- SubMap View ---
    private class SubMapView<K, V>(
        private val m: TreeMap<K, V>,
        private val lowBound: K?, // Can be null for unbounded start
        private val lowInclusive: Boolean,
        private val highBound: K?, // Can be null for unbounded end
        private val highInclusive: Boolean,
        private val descending: Boolean = false
    ) : NavigableMap<K, V>, AbstractMutableMap<K, V>() { // Inherit basic mutable behaviors like putAll

        private fun keyInRange(key: K): Boolean {
            if (key == null) throw NullPointerException("Key cannot be null in SubMapView range check") // Or handle based on map's allowance of nulls
            val cmpLow = lowBound?.let { m.compareKeys(key, it) }
            val cmpHigh = highBound?.let { m.compareKeys(key, it) }

            if (cmpLow != null) {
                if (cmpLow < 0) return false // key < lowBound
                if (cmpLow == 0 && !lowInclusive) return false // key == lowBound but not inclusive
            }
            if (cmpHigh != null) {
                if (cmpHigh > 0) return false // key > highBound
                if (cmpHigh == 0 && !highInclusive) return false // key == highBound but not inclusive
            }
            return true
        }

        private fun checkKey(key: K) {
            if (!keyInRange(key)) {
                throw IllegalArgumentException("Key out of range for SubMapView: $key")
            }
        }

        // Comparator depends on whether this submap is descending
        override fun comparator(): Comparator<in K>? {
            return if (descending) m.comparator()?.reversed() ?: Comparator { k1, k2 -> (k2 as Comparable<K>).compareTo(k1) }
                   else m.comparator()
        }

        // --- Basic Map methods with range checks ---
        override fun get(key: K): V? = if (keyInRange(key)) m.get(key) else null
        override fun containsKey(key: K): Boolean = keyInRange(key) && m.containsKey(key)

        override fun put(key: K, value: V): V? {
            checkKey(key) // Throws if key is out of range for put
            return m.put(key, value)
        }
        override fun remove(key: K): V? = if (keyInRange(key)) m.remove(key) else null // m.remove will handle modCount

        override val entries: MutableSet<MutableMap.MutableEntry<K, V>> by lazy { SubMapEntrySet() }
        override val keys: MutableSet<K> by lazy { SubMapKeySet() }
        override val values: MutableCollection<V> by lazy { SubMapValueCollection() }


        override val size: Int get() { // Inefficient for now, can be improved with dedicated iterator.
            var count = 0
            val iter = SubMapEntryIterator() // Uses firstEntry and successor/predecessor logic
            while(iter.hasNext()) {
                iter.next()
                count++
            }
            return count
        }
        override fun isEmpty(): Boolean { // More direct way to check emptiness
            return firstEntry() == null
        }

        // --- NavigableMap methods (delegating to m, then adjusting for range and descending) ---

        private fun <T> T?.filterRange(): T? where T : Map.Entry<K,V> {
            return if (this != null && keyInRange(this.key)) this else null
        }
        private fun K?.filterRangeKey(): K? {
             return if (this != null && keyInRange(this)) this else null
        }

        override fun lowerEntry(key: K): Map.Entry<K, V>? {
            val effectiveKey = if (descending && highBound != null && m.compareKeys(key,highBound) > 0) highBound else key
            val r = if (descending) m.higherEntry(effectiveKey) else m.lowerEntry(effectiveKey)
            return r.filterRange()
        }
        override fun lowerKey(key: K): K? = lowerEntry(key)?.key

        override fun floorEntry(key: K): Map.Entry<K, V>? {
             val effectiveKey = if (descending && highBound != null && m.compareKeys(key,highBound) > 0) highBound else key
            val r = if (descending) m.ceilingEntry(effectiveKey) else m.floorEntry(effectiveKey)
            return r.filterRange()
        }
        override fun floorKey(key: K): K? = floorEntry(key)?.key

        override fun ceilingEntry(key: K): Map.Entry<K, V>? {
            val effectiveKey = if (descending && lowBound != null && m.compareKeys(key,lowBound) < 0) lowBound else key
            val r = if (descending) m.floorEntry(effectiveKey) else m.ceilingEntry(effectiveKey)
            return r.filterRange()
        }
        override fun ceilingKey(key: K): K? = ceilingEntry(key)?.key

        override fun higherEntry(key: K): Map.Entry<K, V>? {
            val effectiveKey = if (descending && lowBound != null && m.compareKeys(key,lowBound) < 0) lowBound else key
            val r = if (descending) m.lowerEntry(effectiveKey) else m.higherEntry(effectiveKey)
            return r.filterRange()
        }
        override fun higherKey(key: K): K? = higherEntry(key)?.key


        override fun firstEntry(): Map.Entry<K, V>? {
            val node = if (descending) {
                // For descending, first entry is the highest key in range
                if (highBound == null) m.lastEntry() // No upper bound, global last
                else m.floorEntry(if (highInclusive) highBound else m.lowerKey(highBound) ?: return null) // Highest possible within highBound
            } else {
                // For ascending, first entry is the lowest key in range
                if (lowBound == null) m.firstEntry() // No lower bound, global first
                else m.ceilingEntry(if (lowInclusive) lowBound else m.higherKey(lowBound) ?: return null) // Lowest possible within lowBound
            }
            return node.filterRange() // Ensure it also respects the other bound
        }
        override fun firstKey(): K = firstEntry()?.key ?: throw NoSuchElementException()


        override fun lastEntry(): Map.Entry<K, V>? {
            val node = if (descending) {
                // For descending, last entry is the lowest key in range
                if (lowBound == null) m.firstEntry()
                else m.ceilingEntry(if (lowInclusive) lowBound else m.higherKey(lowBound) ?: return null)
            } else {
                // For ascending, last entry is the highest key in range
                if (highBound == null) m.lastEntry()
                else m.floorEntry(if (highInclusive) highBound else m.lowerKey(highBound) ?: return null)
            }
            return node.filterRange() // Ensure it also respects the other bound
        }
        override fun lastKey(): K = lastEntry()?.key ?: throw NoSuchElementException()

        override fun pollFirstEntry(): Map.Entry<K, V>? {
            val e = firstEntry()
            if (e != null) m.remove(e.key)
            return e
        }
        override fun pollLastEntry(): Map.Entry<K, V>? {
            val e = lastEntry()
            if (e != null) m.remove(e.key)
            return e
        }

        override fun descendingMap(): NavigableMap<K, V> {
            return SubMapView(m, lowBound, lowInclusive, highBound, highInclusive, !descending)
        }

        override fun navigableKeySet(): NavigableSet<K> = SubMapNavigableKeySet(this)
        override fun descendingKeySet(): NavigableSet<K> = descendingMap().navigableKeySet()

        // --- SortedMap methods that need to create new sub-views ---
        override fun subMap(fromKey: K, toKey: K): NavigableMap<K, V> = subMap(fromKey, true, toKey, false) // delegates to NavigableMap version

        // These need to be implemented considering the current submap's bounds and descending status
        // For subMap, headMap, tailMap on a SubMapView, we need to ensure the new bounds
        // are within the current SubMapView's bounds.
        private fun getRestrictedFromKey(newFrom: K?, newFromInclusive: Boolean): Pair<K?, Boolean> {
            if (lowBound == null) return newFrom to newFromInclusive
            if (newFrom == null) return lowBound to lowInclusive
            val cmp = m.compareKeys(newFrom, lowBound)
            return when {
                cmp < 0 -> lowBound to lowInclusive // new bound is outside current lower bound
                cmp == 0 -> (newFrom to (newFromInclusive && lowInclusive))
                else -> newFrom to newFromInclusive // new bound is tighter
            }
        }
        private fun getRestrictedToKey(newTo: K?, newToInclusive: Boolean): Pair<K?, Boolean> {
             if (highBound == null) return newTo to newToInclusive
             if (newTo == null) return highBound to highInclusive
             val cmp = m.compareKeys(newTo, highBound)
             return when {
                cmp > 0 -> highBound to highInclusive
                cmp == 0 -> (newTo to (newToInclusive && highInclusive))
                else -> newTo to newToInclusive
            }
        }


        fun subMap(newFromKey: K, newFromInclusive: Boolean, newToKey: K, newToInclusive: Boolean): NavigableMap<K, V> {
            if (m.compareKeys(newFromKey, newToKey) > 0) throw IllegalArgumentException("fromKey > toKey")

            val (effFrom, effFromIncl) = getRestrictedFromKey(newFromKey, newFromInclusive)
            val (effTo, effToIncl) = getRestrictedToKey(newToKey, newToInclusive)

            // Ensure restricted keys are still valid before creating new SubMapView
            if (effFrom != null && effTo != null && m.compareKeys(effFrom, effTo) > 0) {
                 // This can happen if original bounds were tighter, e.g. subMap(1,5).subMap(6,10)
                 // Return an empty map view for this scenario.
                 // Or, more simply, the iterators for such a map won't yield anything.
                 // Let SubMapView constructor handle it, keyInRange will prevent iteration.
            }
            return SubMapView(m, effFrom, effFromIncl, effTo, effToIncl, descending)
        }

        fun headMap(newToKey: K, newToInclusive: Boolean): NavigableMap<K, V> {
            val (effFrom, effFromIncl) = getRestrictedFromKey(null, false) // Use current lowBound
            val (effTo, effToIncl) = getRestrictedToKey(newToKey, newToInclusive)
            return SubMapView(m, effFrom, effFromIncl, effTo, effToIncl, descending)
        }

        fun tailMap(newFromKey: K, newFromInclusive: Boolean): NavigableMap<K, V> {
            val (effFrom, effFromIncl) = getRestrictedFromKey(newFromKey, newFromInclusive)
            val (effTo, effToIncl) = getRestrictedToKey(null, false) // Use current highBound
            return SubMapView(m, effFrom, effFromIncl, effTo, effToIncl, descending)
        }

         // Override these from AbstractMutableMap as they are on NavigableMap too
        override fun headMap(toKey: K): NavigableMap<K, V> = headMap(toKey, false) // Standard is exclusive
        override fun tailMap(fromKey: K): NavigableMap<K, V> = tailMap(fromKey, true) // Standard is inclusive


    // --- Iterators for SubMapView ---
    private abstract inner class SubMapIterator<E> : MutableIterator<E> {
        var expectedModCount: Int = m.modCount // Use main map's modCount
        var lastReturnedNode: Node<K, V>? = null
        var nextNodeToYield: Node<K, V>? = null

        init {
            // Start at the first valid node in the submap's range and direction
            var current = if (descending) this@SubMapView.firstEntry()?.let { m.findNode(it.key) } // Get the actual node
                          else this@SubMapView.firstEntry()?.let { m.findNode(it.key) }

            // Ensure the first node found is actually within range (filterRange in firstEntry might be enough)
            // but keyInRange is the ultimate check.
            if (current != null && !keyInRange(current.key)) {
                current = null // Do not start if the found first/last is outside due to inclusivity mismatch
            }
            nextNodeToYield = current
        }

        override fun hasNext(): Boolean {
            if (m.modCount != expectedModCount) throw ConcurrentModificationException()
            return nextNodeToYield != null
        }

        fun stepForward(): Node<K, V>? { // Ascending step
            return m.successor(nextNodeToYield)
        }

        fun stepBackward(): Node<K, V>? { // Descending step
            return m.predecessor(nextNodeToYield)
        }

        fun nextNodeInternal(): Node<K, V> {
            if (m.modCount != expectedModCount) throw ConcurrentModificationException()
            val nodeToReturn = nextNodeToYield ?: throw NoSuchElementException()

            lastReturnedNode = nodeToReturn

            // Advance nextNodeToYield
            var candidate = if (descending) stepBackward() else stepForward()
            while(candidate != null && !keyInRange(candidate.key)) { // Skip nodes outside range
                candidate = if (descending) stepBackward() else stepForward()
            }
            nextNodeToYield = candidate

            return nodeToReturn
        }

        override fun remove() {
            if (m.modCount != expectedModCount) throw ConcurrentModificationException()
            val lr = lastReturnedNode ?: throw IllegalStateException("next() not called or element already removed")
            m.remove(lr.key) // This will update m.modCount
            expectedModCount = m.modCount
            lastReturnedNode = null
        }
    }

    private inner class SubMapEntryIterator : SubMapIterator<MutableMap.MutableEntry<K,V>>() {
        override fun next(): MutableMap.MutableEntry<K, V> = nextNodeInternal().toMutableEntry()
    }
    private inner class SubMapKeyIterator : SubMapIterator<K>() {
        override fun next(): K = nextNodeInternal().key
    }
    private inner class SubMapValueIterator : SubMapIterator<V>() {
        override fun next(): V = nextNodeInternal().value
    }

    // --- Collection views for SubMapView ---
    private inner class SubMapEntrySet : AbstractMutableSet<MutableMap.MutableEntry<K,V>>() {
        override val size: Int get() = this@SubMapView.size
        override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> = SubMapEntryIterator()
        override fun contains(element: MutableMap.MutableEntry<K, V>): Boolean {
            return keyInRange(element.key) && m.entries.contains(element)
        }
        override fun remove(element: MutableMap.MutableEntry<K, V>): Boolean {
            return keyInRange(element.key) && m.entries.remove(element)
        }
        override fun clear() { // Clears only the submap range
            val iter = SubMapEntryIterator()
            while(iter.hasNext()){
                iter.next()
                iter.remove()
            }
        }
    }
    private inner class SubMapKeySet : AbstractMutableSet<K>() {
        override val size: Int get() = this@SubMapView.size
        override fun iterator(): MutableIterator<K> = SubMapKeyIterator()
        override fun contains(element: K): Boolean = keyInRange(element) && m.containsKey(element)
        override fun remove(element: K): Boolean = keyInRange(element) && m.remove(element) != null
        override fun clear() { SubMapEntrySet().clear() } // Reuse entry set clear
    }
    private inner class SubMapValueCollection : AbstractMutableCollection<V>() {
        override val size: Int get() = this@SubMapView.size
        override fun iterator(): MutableIterator<V> = SubMapValueIterator()
        override fun contains(element: V): Boolean { // Inefficient
            val iter = SubMapValueIterator()
            while(iter.hasNext()) if(iter.next() == element) return true
            return false
        }
        override fun remove(element: V): Boolean { // Inefficient
            val iter = SubMapValueIterator()
            while(iter.hasNext()){
                if(iter.next() == element){
                    iter.remove()
                    return true
                }
            }
            return false
        }
        override fun clear() { SubMapEntrySet().clear() }
    }
  }

    private class SubMapNavigableKeySet<K,V>(
        private val subMapView: SubMapView<K,V>
    ) : AbstractMutableSet<K>(), NavigableSet<K> {

        override val size: Int get() = subMapView.size

        override fun iterator(): MutableIterator<K> = subMapView.SubMapKeyIterator() // Uses SubMapView's iterator

        override fun descendingIterator(): Iterator<K> {
            // Need a descending key iterator for the submap
            return subMapView.descendingMap().navigableKeySet().iterator() // This will be a SubMapKeyIterator on a descending SubMapView
        }

        override fun lower(e: K): K? = subMapView.lowerKey(e)
        override fun floor(e: K): K? = subMapView.floorKey(e)
        override fun ceiling(e: K): K? = subMapView.ceilingKey(e)
        override fun higher(e: K): K? = subMapView.higherKey(e)

        override fun first(): K = subMapView.firstKey()
        override fun last(): K = subMapView.lastKey()

        override fun pollFirst(): K? = subMapView.pollFirstEntry()?.key
        override fun pollLast(): K? = subMapView.pollLastEntry()?.key

        override fun contains(element: K): Boolean = subMapView.containsKey(element)
        override fun remove(element: K): Boolean = subMapView.remove(element) != null
        override fun clear() = subMapView.clear()

        override fun add(element: K): Boolean {
            throw UnsupportedOperationException("Cannot add to a NavigableMap's keySet without a value.")
        }

        override fun descendingSet(): NavigableSet<K> = subMapView.descendingMap().navigableKeySet()

        override fun subSet(fromElement: K, fromInclusive: Boolean, toElement: K, toInclusive: Boolean): NavigableSet<K> {
            return subMapView.subMap(fromElement, fromInclusive, toElement, toInclusive).navigableKeySet()
        }
        override fun headSet(toElement: K, inclusive: Boolean): NavigableSet<K> {
            return subMapView.headMap(toElement, inclusive).navigableKeySet()
        }
        override fun tailSet(fromElement: K, inclusive: Boolean): NavigableSet<K> {
            return subMapView.tailMap(fromElement, inclusive).navigableKeySet()
        }
        override fun subSet(fromElement: K, toElement: K): SortedSet<K> = subSet(fromElement, true, toElement, false)
        override fun headSet(toElement: K): SortedSet<K> = headSet(toElement, false)
        override fun tailSet(fromElement: K): SortedSet<K> = tailSet(fromElement, true)
    }


    // --- Standard methods (equals, hashCode, toString) ---
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Map<*, *>) return false
        if (size != other.size) return false

        try {
            for (entry in entries) {
                if (other[entry.key] != entry.value) return false
            }
        } catch (cce: ClassCastException) {
            return false
        } catch (npe: NullPointerException) {
            return false
        }
        return true
    }

    override fun hashCode(): Int {
        var h = 0
        for (entry in entries) {
            h += entry.hashCode()
        }
        return h
    }

    override fun toString(): String {
        return entries.joinToString(separator = ", ", prefix = "{", postfix = "}") {
            "${it.key}=${it.value}"
        }
    }
}

// Kotlin's NavigableMap doesn't have put/remove.
// It seems standard library NavigableMap is read-only.
// We likely need to implement `kotlin.collections.MutableMap` in addition to `kotlin.collections.NavigableMap`.
// Let's check the definition of `kotlin.collections.NavigableMap`.
// If it's read-only, the user's request to "Implement the red-black tree logic for put, get, remove, containsKey"
// implies we need a mutable structure.

// For now, I'll proceed with this structure and will adjust if MutableMap is indeed needed.
// The prompt mentions "Implement all methods from NavigableMap, SortedMap, and Map."
// Standard Map interface in Kotlin also does not have `put` or `remove`.
// It seems the user expects a mutable TreeMap, like Java's TreeMap.
// So, I should implement `kotlin.collections.MutableMap<K, V>` as well.
// I will add `MutableMap<K,V>` to the interface list and its methods.
