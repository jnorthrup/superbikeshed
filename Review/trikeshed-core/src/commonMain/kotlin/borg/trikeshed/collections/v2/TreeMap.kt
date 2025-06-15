package borg.trikeshed.collections.v2

import kotlin.collections.SortedMap
import kotlin.collections.NavigableMap
import kotlin.collections.MutableMap

/**
 * A new implementation of TreeMap with improved performance and features.
 * This is the successor to borg.trikeshed.collections.TreeMap.
 */
class TreeMap<K, V>(private val comparator: Comparator<in K>? = null) : NavigableMap<K, V>, MutableMap<K, V> {
    private var root: Node<K, V>? = null
    private var modCount = 0
    private var _size = 0

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

    // --- MutableMap Interface ---
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

    override fun get(key: K): V? = findNode(key)?.value

    override val size: Int get() = _size

    override fun isEmpty(): Boolean = size == 0

    override fun containsKey(key: K): Boolean = findNode(key) != null

    override fun containsValue(value: V): Boolean {
        val iter = ValueIterator()
        while (iter.hasNext()) {
            if (iter.next() == value) {
                return true
            }
        }
        return false
    }

    override fun putAll(from: Map<out K, V>) {
        // modCount will be incremented by individual put calls
        for ((key, value) in from) {
            put(key, value)
        }
    }

    override fun clear() {
        modCount++
        root = null
        _size = 0
    }

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>> by lazy { EntrySet() }
    override val keys: MutableSet<K> by lazy { KeySet() }
    override val values: MutableCollection<V> by lazy { ValueCollection() }

    // --- SortedMap Interface ---
    override fun comparator(): Comparator<in K>? = comparator

    override fun subMap(fromKey: K, toKey: K): NavigableMap<K, V> = subMap(fromKey, true, toKey, false)
    override fun headMap(toKey: K): NavigableMap<K, V> = headMap(toKey, false)
    override fun tailMap(fromKey: K): NavigableMap<K, V> = tailMap(fromKey, true)

    // --- NavigableMap Interface ---
    override fun subMap(fromKey: K, fromInclusive: Boolean, toKey: K, toInclusive: Boolean): NavigableMap<K, V> {
        if (compareKeys(fromKey, toKey) > 0) throw IllegalArgumentException("fromKey > toKey")
        return SubMapView(this, fromKey, fromInclusive, toKey, toInclusive)
    }

    override fun headMap(toKey: K, inclusive: Boolean): NavigableMap<K, V> {
        return SubMapView(this, null, false, toKey, inclusive)
    }

    override fun tailMap(fromKey: K, inclusive: Boolean): NavigableMap<K, V> {
        return SubMapView(this, fromKey, inclusive, null, false)
    }

    override fun firstKey(): K = getFirstNode()?.key ?: throw NoSuchElementException()
    override fun lastKey(): K = getLastNode()?.key ?: throw NoSuchElementException()

    override fun lowerKey(key: K): K? = lowerEntry(key)?.key
    override fun floorKey(key: K): K? = floorEntry(key)?.key
    override fun ceilingKey(key: K): K? = ceilingEntry(key)?.key
    override fun higherKey(key: K): K? = higherEntry(key)?.key

    override fun firstEntry(): Map.Entry<K, V>? = getFirstNode()?.let { SimpleMutableEntry(it) }
    override fun lastEntry(): Map.Entry<K, V>? = getLastNode()?.let { SimpleMutableEntry(it) }

    override fun pollFirstEntry(): Map.Entry<K, V>? {
        val first = getFirstNode() ?: return null
        val entry = SimpleMutableEntry(first)
        remove(first.key)
        return entry
    }

    override fun pollLastEntry(): Map.Entry<K, V>? {
        val last = getLastNode() ?: return null
        val entry = SimpleMutableEntry(last)
        remove(last.key)
        return entry
    }

    override fun lowerEntry(key: K): Map.Entry<K, V>? {
        var node = root
        var result: Node<K, V>? = null
        while (node != null) {
            val cmp = compareKeys(key, node.key)
            if (cmp > 0) {
                result = node
                node = node.right
            } else {
                node = node.left
            }
        }
        return result?.let { SimpleMutableEntry(it) }
    }

    override fun floorEntry(key: K): Map.Entry<K, V>? {
        var node = root
        var result: Node<K, V>? = null
        while (node != null) {
            val cmp = compareKeys(key, node.key)
            when {
                cmp > 0 -> {
                    result = node
                    node = node.right
                }
                cmp < 0 -> node = node.left
                else -> return SimpleMutableEntry(node)
            }
        }
        return result?.let { SimpleMutableEntry(it) }
    }

    override fun ceilingEntry(key: K): Map.Entry<K, V>? {
        var node = root
        var result: Node<K, V>? = null
        while (node != null) {
            val cmp = compareKeys(key, node.key)
            when {
                cmp < 0 -> {
                    result = node
                    node = node.left
                }
                cmp > 0 -> node = node.right
                else -> return SimpleMutableEntry(node)
            }
        }
        return result?.let { SimpleMutableEntry(it) }
    }

    override fun higherEntry(key: K): Map.Entry<K, V>? {
        var node = root
        var result: Node<K, V>? = null
        while (node != null) {
            val cmp = compareKeys(key, node.key)
            if (cmp < 0) {
                result = node
                node = node.left
            } else {
                node = node.right
            }
        }
        return result?.let { SimpleMutableEntry(it) }
    }

    override fun descendingMap(): NavigableMap<K, V> = DescendingTreeMapView(this)
    override fun navigableKeySet(): NavigableSet<K> = NavigableKeySet(this)
    override fun descendingKeySet(): NavigableSet<K> = navigableKeySet().descendingSet()

    // --- Helper Methods ---
    @Suppress("UNCHECKED_CAST")
    internal fun compareKeys(k1: K, k2: K): Int {
        return comparator?.compare(k1, k2) ?: (k1 as Comparable<K>).compareTo(k2)
    }

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
                else -> return current
            }
        }
        return null
    }

    // --- Red-Black Tree Balancing ---
    private fun fixAfterInsertion(x: Node<K, V>) {
        var current = x
        current.color = Node.RED

        while (current != root && current.parent?.color == Node.RED) {
            if (current.parent == current.parent?.parent?.left) {
                val y = current.parent?.parent?.right
                if (y?.color == Node.RED) {
                    current.parent?.color = Node.BLACK
                    y.color = Node.BLACK
                    current.parent?.parent?.color = Node.RED
                    current = current.parent?.parent!!
                } else {
                    if (current == current.parent?.right) {
                        current = current.parent!!
                        rotateLeft(current)
                    }
                    current.parent?.color = Node.BLACK
                    current.parent?.parent?.color = Node.RED
                    rotateRight(current.parent?.parent!!)
                }
            } else {
                val y = current.parent?.parent?.left
                if (y?.color == Node.RED) {
                    current.parent?.color = Node.BLACK
                    y.color = Node.BLACK
                    current.parent?.parent?.color = Node.RED
                    current = current.parent?.parent!!
                } else {
                    if (current == current.parent?.left) {
                        current = current.parent!!
                        rotateRight(current)
                    }
                    current.parent?.color = Node.BLACK
                    current.parent?.parent?.color = Node.RED
                    rotateLeft(current.parent?.parent!!)
                }
            }
        }
        root?.color = Node.BLACK
    }

    private fun rotateLeft(x: Node<K, V>) {
        val y = x.right ?: return
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

    private fun rotateRight(x: Node<K, V>) {
        val y = x.left ?: return
        x.left = y.right
        if (y.right != null) {
            y.right!!.parent = x
        }
        y.parent = x.parent
        when {
            x.parent == null -> root = y
            x == x.parent!!.right -> x.parent!!.right = y
            else -> x.parent!!.left = y
        }
        y.right = x
        x.parent = y
    }

    private fun deleteNode(p: Node<K, V>) {
        // If strictly internal, copy successor's element to p and then make p
        // point to successor.
        if (p.left != null && p.right != null) {
            val s = successor(p)
            p.key = s!!.key
            p.value = s.value
            p = s
        } // p has 2 children

        // Start fixup at replacement node, if it exists.
        val replacement = p.left ?: p.right

        if (replacement != null) {
            // Link replacement to parent
            replacement.parent = p.parent
            when {
                p.parent == null -> root = replacement
                p == p.parent!!.left -> p.parent!!.left = replacement
                else -> p.parent!!.right = replacement
            }

            // Null out links so they are OK to use by fixAfterDeletion.
            p.left = null
            p.right = null
            p.parent = null

            // Fix replacement
            if (p.color == Node.BLACK) {
                fixAfterDeletion(replacement)
            }
        } else if (p.parent == null) { // return if we are the only node.
            root = null
        } else { //  No children. Use self as phantom replacement and unlink.
            if (p.color == Node.BLACK) {
                fixAfterDeletion(p)
            }

            if (p.parent != null) {
                if (p == p.parent!!.left) {
                    p.parent!!.left = null
                } else if (p == p.parent!!.right) {
                    p.parent!!.right = null
                }
                p.parent = null
            }
        }
    }

    private fun fixAfterDeletion(x: Node<K, V>) {
        var current = x
        while (current != root && current.color == Node.BLACK) {
            if (current == current.parent?.left) {
                var sibling = current.parent?.right
                if (sibling?.color == Node.RED) {
                    sibling.color = Node.BLACK
                    current.parent?.color = Node.RED
                    rotateLeft(current.parent!!)
                    sibling = current.parent?.right
                }
                if (sibling?.left?.color == Node.BLACK && sibling.right?.color == Node.BLACK) {
                    sibling.color = Node.RED
                    current = current.parent!!
                } else {
                    if (sibling?.right?.color == Node.BLACK) {
                        sibling.left?.color = Node.BLACK
                        sibling.color = Node.RED
                        rotateRight(sibling)
                        sibling = current.parent?.right
                    }
                    sibling?.color = current.parent?.color ?: Node.BLACK
                    current.parent?.color = Node.BLACK
                    sibling?.right?.color = Node.BLACK
                    rotateLeft(current.parent!!)
                    current = root!!
                }
            } else {
                var sibling = current.parent?.left
                if (sibling?.color == Node.RED) {
                    sibling.color = Node.BLACK
                    current.parent?.color = Node.RED
                    rotateRight(current.parent!!)
                    sibling = current.parent?.left
                }
                if (sibling?.right?.color == Node.BLACK && sibling.left?.color == Node.BLACK) {
                    sibling.color = Node.RED
                    current = current.parent!!
                } else {
                    if (sibling?.left?.color == Node.BLACK) {
                        sibling.right?.color = Node.BLACK
                        sibling.color = Node.RED
                        rotateLeft(sibling)
                        sibling = current.parent?.left
                    }
                    sibling?.color = current.parent?.color ?: Node.BLACK
                    current.parent?.color = Node.BLACK
                    sibling?.left?.color = Node.BLACK
                    rotateRight(current.parent!!)
                    current = root!!
                }
            }
        }
        current.color = Node.BLACK
    }

    private fun successor(t: Node<K, V>?): Node<K, V>? {
        if (t == null) return null
        else if (t.right != null) {
            var p = t.right
            while (p?.left != null) {
                p = p.left
            }
            return p
        } else {
            var p = t.parent
            var ch = t
            while (p != null && ch == p.right) {
                ch = p
                p = p.parent
            }
            return p
        }
    }

    // --- Iterator Classes ---
    private abstract inner class TreeIterator<E> : MutableIterator<E> {
        var expectedModCount: Int = modCount
        var lastReturned: Node<K, V>? = null
        var nextNode: Node<K, V>? = null

        init {
            nextNode = getFirstNode()
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
            this@TreeMap.remove(lastReturned!!.key)
            expectedModCount = modCount
            lastReturned = null
        }
    }

    private inner class KeyIterator : TreeIterator<K>() {
        override fun next(): K = nextNode().key
    }

    private inner class ValueIterator : TreeIterator<V>() {
        override fun next(): V = nextNode().value
    }

    private inner class EntryIterator : TreeIterator<MutableMap.MutableEntry<K, V>>() {
        override fun next(): MutableMap.MutableEntry<K, V> = nextNode().toMutableEntry()
    }

    // --- Collection Views ---
    private inner class KeySet : AbstractMutableSet<K>() {
        override val size: Int get() = this@TreeMap.size
        override fun iterator(): MutableIterator<K> = KeyIterator()
        override fun contains(element: K): Boolean = this@TreeMap.containsKey(element)
        override fun remove(element: K): Boolean {
            val result = this@TreeMap.remove(element) != null
            return result
        }
        override fun clear() = this@TreeMap.clear()
    }

    private inner class ValueCollection : AbstractMutableCollection<V>() {
        override val size: Int get() = this@TreeMap.size
        override fun iterator(): MutableIterator<V> = ValueIterator()
        override fun contains(element: V): Boolean = this@TreeMap.containsValue(element)
        override fun clear() = this@TreeMap.clear()
    }

    private inner class EntrySet : AbstractMutableSet<MutableMap.MutableEntry<K, V>>() {
        override val size: Int get() = this@TreeMap.size
        override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> = EntryIterator()
        override fun contains(element: MutableMap.MutableEntry<K, V>): Boolean {
            val node = findNode(element.key)
            return node != null && node.value == element.value
        }
        override fun remove(element: MutableMap.MutableEntry<K, V>): Boolean {
            val node = findNode(element.key)
            if (node != null && node.value == element.value) {
                this@TreeMap.remove(element.key)
                return true
            }
            return false
        }
        override fun clear() = this@TreeMap.clear()
    }

    // --- View Classes ---
    private class DescendingTreeMapView<K, V>(private val m: TreeMap<K, V>) : NavigableMap<K, V>, AbstractMutableMap<K, V>() {
        private val reverseComparator: Comparator<in K>? = m.comparator?.reversed() ?: Comparator { k1, k2 -> (k2 as Comparable<K>).compareTo(k1) }

        override fun comparator(): Comparator<in K>? = reverseComparator
        override val size: Int get() = m.size
        override fun isEmpty(): Boolean = m.isEmpty()
        override fun containsKey(key: K): Boolean = m.containsKey(key)
        override fun containsValue(value: V): Boolean = m.containsValue(value)
        override fun get(key: K): V? = m.get(key)
        override fun put(key: K, value: V): V? = m.put(key, value)
        override fun remove(key: K): V? = m.remove(key)
        override fun clear() = m.clear()

        override fun firstKey(): K = m.lastKey()
        override fun lastKey(): K = m.firstKey()
        override fun firstEntry(): Map.Entry<K, V>? = m.lastEntry()
        override fun lastEntry(): Map.Entry<K, V>? = m.firstEntry()
        override fun pollFirstEntry(): Map.Entry<K, V>? = m.pollLastEntry()
        override fun pollLastEntry(): Map.Entry<K, V>? = m.pollFirstEntry()

        override fun lowerEntry(key: K): Map.Entry<K, V>? = m.higherEntry(key)
        override fun lowerKey(key: K): K? = m.higherKey(key)
        override fun floorEntry(key: K): Map.Entry<K, V>? = m.ceilingEntry(key)
        override fun floorKey(key: K): K? = m.ceilingKey(key)
        override fun ceilingEntry(key: K): Map.Entry<K, V>? = m.floorEntry(key)
        override fun ceilingKey(key: K): K? = m.floorKey(key)
        override fun higherEntry(key: K): Map.Entry<K, V>? = m.lowerEntry(key)
        override fun higherKey(key: K): K? = m.lowerKey(key)

        override fun descendingMap(): NavigableMap<K, V> = m
        override fun navigableKeySet(): NavigableSet<K> = m.navigableKeySet().descendingSet()
        override fun descendingKeySet(): NavigableSet<K> = m.navigableKeySet()

        override fun subMap(fromKey: K, fromInclusive: Boolean, toKey: K, toInclusive: Boolean): NavigableMap<K, V> {
            return m.subMap(toKey, toInclusive, fromKey, fromInclusive).descendingMap()
        }

        override fun headMap(toKey: K, inclusive: Boolean): NavigableMap<K, V> {
            return m.tailMap(toKey, inclusive).descendingMap()
        }

        override fun tailMap(fromKey: K, inclusive: Boolean): NavigableMap<K, V> {
            return m.headMap(fromKey, inclusive).descendingMap()
        }

        override val entries: MutableSet<MutableMap.MutableEntry<K, V>> by lazy { DescendingEntrySet() }
        override val keys: MutableSet<K> by lazy { navigableKeySet() }
        override val values: MutableCollection<V> by lazy { DescendingValueCollection() }

        private inner class DescendingEntrySet : AbstractMutableSet<MutableMap.MutableEntry<K, V>>() {
            override val size: Int get() = this@DescendingTreeMapView.size
            override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> = DescendingEntryIterator()
            override fun contains(element: MutableMap.MutableEntry<K, V>): Boolean {
                val v = this@DescendingTreeMapView.get(element.key)
                return v != null && v == element.value
            }
            override fun remove(element: MutableMap.MutableEntry<K, V>): Boolean {
                return this@DescendingTreeMapView.remove(element.key, element.value)
            }
            override fun clear() = this@DescendingTreeMapView.clear()
        }

        private inner class DescendingValueCollection : AbstractMutableCollection<V>() {
            override val size: Int get() = this@DescendingTreeMapView.size
            override fun iterator(): MutableIterator<V> = DescendingValueIterator()
            override fun contains(element: V): Boolean = this@DescendingTreeMapView.containsValue(element)
            override fun clear() = this@DescendingTreeMapView.clear()
        }

        private inner class DescendingEntryIterator : MutableIterator<MutableMap.MutableEntry<K, V>> {
            private val iter = m.entries.iterator()
            override fun hasNext(): Boolean = iter.hasNext()
            override fun next(): MutableMap.MutableEntry<K, V> = iter.next()
            override fun remove() = iter.remove()
        }

        private inner class DescendingValueIterator : MutableIterator<V> {
            private val iter = m.values.iterator()
            override fun hasNext(): Boolean = iter.hasNext()
            override fun next(): V = iter.next()
            override fun remove() = iter.remove()
        }
    }

    private class SubMapView<K, V>(
        private val m: TreeMap<K, V>,
        private val fromKey: K?,
        private val fromInclusive: Boolean,
        private val toKey: K?,
        private val toInclusive: Boolean
    ) : NavigableMap<K, V>, AbstractMutableMap<K, V>() {
        override fun comparator(): Comparator<in K>? = m.comparator()
        override val size: Int get() {
            var count = 0
            val iter = SubMapEntryIterator()
            while (iter.hasNext()) {
                iter.next()
                count++
            }
            return count
        }
        override fun isEmpty(): Boolean = firstEntry() == null
        override fun containsKey(key: K): Boolean = keyInRange(key) && m.containsKey(key)
        override fun containsValue(value: V): Boolean {
            val iter = SubMapValueIterator()
            while (iter.hasNext()) {
                if (iter.next() == value) return true
            }
            return false
        }
        override fun get(key: K): V? = if (keyInRange(key)) m.get(key) else null
        override fun put(key: K, value: V): V? {
            if (!keyInRange(key)) throw IllegalArgumentException("key out of range")
            return m.put(key, value)
        }
        override fun remove(key: K): V? {
            if (!keyInRange(key)) return null
            return m.remove(key)
        }
        override fun clear() {
            val iter = SubMapKeyIterator()
            while (iter.hasNext()) {
                iter.next()
                iter.remove()
            }
        }

        override fun firstKey(): K = firstEntry()?.key ?: throw NoSuchElementException()
        override fun lastKey(): K = lastEntry()?.key ?: throw NoSuchElementException()
        override fun firstEntry(): Map.Entry<K, V>? = getFirstEntry()
        override fun lastEntry(): Map.Entry<K, V>? = getLastEntry()
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

        override fun lowerEntry(key: K): Map.Entry<K, V>? {
            if (!keyInRange(key)) throw IllegalArgumentException("key out of range")
            return m.lowerEntry(key)?.let { if (keyInRange(it.key)) it else null }
        }
        override fun lowerKey(key: K): K? = lowerEntry(key)?.key
        override fun floorEntry(key: K): Map.Entry<K, V>? {
            if (!keyInRange(key)) throw IllegalArgumentException("key out of range")
            return m.floorEntry(key)?.let { if (keyInRange(it.key)) it else null }
        }
        override fun floorKey(key: K): K? = floorEntry(key)?.key
        override fun ceilingEntry(key: K): Map.Entry<K, V>? {
            if (!keyInRange(key)) throw IllegalArgumentException("key out of range")
            return m.ceilingEntry(key)?.let { if (keyInRange(it.key)) it else null }
        }
        override fun ceilingKey(key: K): K? = ceilingEntry(key)?.key
        override fun higherEntry(key: K): Map.Entry<K, V>? {
            if (!keyInRange(key)) throw IllegalArgumentException("key out of range")
            return m.higherEntry(key)?.let { if (keyInRange(it.key)) it else null }
        }
        override fun higherKey(key: K): K? = higherEntry(key)?.key

        override fun descendingMap(): NavigableMap<K, V> = DescendingSubMapView(this)
        override fun navigableKeySet(): NavigableSet<K> = SubMapNavigableKeySet(this)
        override fun descendingKeySet(): NavigableSet<K> = navigableKeySet().descendingSet()

        override fun subMap(fromKey: K, fromInclusive: Boolean, toKey: K, toInclusive: Boolean): NavigableMap<K, V> {
            if (!keyInRange(fromKey) || !keyInRange(toKey)) throw IllegalArgumentException("key out of range")
            return m.subMap(fromKey, fromInclusive, toKey, toInclusive)
        }

        override fun headMap(toKey: K, inclusive: Boolean): NavigableMap<K, V> {
            if (!keyInRange(toKey)) throw IllegalArgumentException("key out of range")
            return m.headMap(toKey, inclusive)
        }

        override fun tailMap(fromKey: K, inclusive: Boolean): NavigableMap<K, V> {
            if (!keyInRange(fromKey)) throw IllegalArgumentException("key out of range")
            return m.tailMap(fromKey, inclusive)
        }

        override val entries: MutableSet<MutableMap.MutableEntry<K, V>> by lazy { SubMapEntrySet() }
        override val keys: MutableSet<K> by lazy { navigableKeySet() }
        override val values: MutableCollection<V> by lazy { SubMapValueCollection() }

        private fun keyInRange(key: K): Boolean {
            return (fromKey == null || m.compareKeys(key, fromKey) > 0 || (fromInclusive && m.compareKeys(key, fromKey) == 0)) &&
                   (toKey == null || m.compareKeys(key, toKey) < 0 || (toInclusive && m.compareKeys(key, toKey) == 0))
        }

        private fun getFirstEntry(): Map.Entry<K, V>? {
            val e = if (fromKey == null) m.firstEntry() else m.ceilingEntry(fromKey)
            return if (e != null && keyInRange(e.key)) e else null
        }

        private fun getLastEntry(): Map.Entry<K, V>? {
            val e = if (toKey == null) m.lastEntry() else m.floorEntry(toKey)
            return if (e != null && keyInRange(e.key)) e else null
        }

        private inner class SubMapEntrySet : AbstractMutableSet<MutableMap.MutableEntry<K, V>>() {
            override val size: Int get() = this@SubMapView.size
            override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> = SubMapEntryIterator()
            override fun contains(element: MutableMap.MutableEntry<K, V>): Boolean {
                val v = this@SubMapView.get(element.key)
                return v != null && v == element.value
            }
            override fun remove(element: MutableMap.MutableEntry<K, V>): Boolean {
                return this@SubMapView.remove(element.key, element.value)
            }
            override fun clear() = this@SubMapView.clear()
        }

        private inner class SubMapValueCollection : AbstractMutableCollection<V>() {
            override val size: Int get() = this@SubMapView.size
            override fun iterator(): MutableIterator<V> = SubMapValueIterator()
            override fun contains(element: V): Boolean = this@SubMapView.containsValue(element)
            override fun clear() = this@SubMapView.clear()
        }

        private inner class SubMapEntryIterator : MutableIterator<MutableMap.MutableEntry<K, V>> {
            private var next = getFirstEntry()
            private var lastReturned: Map.Entry<K, V>? = null

            override fun hasNext(): Boolean = next != null

            override fun next(): MutableMap.MutableEntry<K, V> {
                val e = next ?: throw NoSuchElementException()
                lastReturned = e
                next = m.higherEntry(e.key)?.let { if (keyInRange(it.key)) it else null }
                return e as MutableMap.MutableEntry<K, V>
            }

            override fun remove() {
                val e = lastReturned ?: throw IllegalStateException()
                this@SubMapView.remove(e.key)
                lastReturned = null
            }
        }

        private inner class SubMapKeyIterator : MutableIterator<K> {
            private val entryIter = SubMapEntryIterator()
            override fun hasNext(): Boolean = entryIter.hasNext()
            override fun next(): K = entryIter.next().key
            override fun remove() = entryIter.remove()
        }

        private inner class SubMapValueIterator : MutableIterator<V> {
            private val entryIter = SubMapEntryIterator()
            override fun hasNext(): Boolean = entryIter.hasNext()
            override fun next(): V = entryIter.next().value
            override fun remove() = entryIter.remove()
        }
    }

    private class NavigableKeySet<K, V>(private val map: TreeMap<K, V>) : AbstractMutableSet<K>(), NavigableSet<K> {
        override val size: Int get() = map.size
        override fun iterator(): MutableIterator<K> = map.KeyIterator()
        override fun descendingIterator(): Iterator<K> = map.DescendingKeyIterator()
        override fun lower(e: K): K? = map.lowerKey(e)
        override fun floor(e: K): K? = map.floorKey(e)
        override fun ceiling(e: K): K? = map.ceilingKey(e)
        override fun higher(e: K): K? = map.higherKey(e)
        override fun first(): K = map.firstKey()
        override fun last(): K = map.lastKey()
        override fun pollFirst(): K? = map.pollFirstEntry()?.key
        override fun pollLast(): K? = map.pollLastEntry()?.key
        override fun contains(element: K): Boolean = map.containsKey(element)
        override fun remove(element: K): Boolean = map.remove(element) != null
        override fun clear() = map.clear()
        override fun add(element: K): Boolean = throw UnsupportedOperationException()
        override fun descendingSet(): NavigableSet<K> = map.descendingKeySet()
        override fun subSet(fromElement: K, fromInclusive: Boolean, toElement: K, toInclusive: Boolean): NavigableSet<K> {
            return map.subMap(fromElement, fromInclusive, toElement, toInclusive).navigableKeySet()
        }
        override fun headSet(toElement: K, inclusive: Boolean): NavigableSet<K> {
            return map.headMap(toElement, inclusive).navigableKeySet()
        }
        override fun tailSet(fromElement: K, inclusive: Boolean): NavigableSet<K> {
            return map.tailMap(fromElement, inclusive).navigableKeySet()
        }
        override fun subSet(fromElement: K, toElement: K): SortedSet<K> = subSet(fromElement, true, toElement, false)
        override fun headSet(toElement: K): SortedSet<K> = headSet(toElement, false)
        override fun tailSet(fromElement: K): SortedSet<K> = tailSet(fromElement, true)
    }

    private class SimpleMutableEntry<K, V>(private val node: Node<K, V>) : MutableMap.MutableEntry<K, V> {
        override val key: K get() = node.key
        override var value: V
            get() = node.value
            set(value) { node.value = value }
    }
} 