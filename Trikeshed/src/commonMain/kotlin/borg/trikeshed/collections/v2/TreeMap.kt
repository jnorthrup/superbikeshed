package borg.trikeshed.collections.v2

import kotlin.collections.SortedMap
import kotlin.collections.NavigableMap
import kotlin.collections.MutableMap

/**
 * A Red-Black Tree based implementation of NavigableMap.
 * This is the successor to borg.trikeshed.collections.TreeMap.
 */
class TreeMap<K, V>(private val comparator: Comparator<in K>? = null) : NavigableMap<K, V>, MutableMap<K, V> {
    private var root: Node<K, V>? = null
    private var modCount = 0
    private var _size = 0

    // Basic Node structure for Red-Black Tree
    private class Node<K, V>(
        override var key: K,
        override var value: V,
        var left: Node<K, V>? = null,
        var right: Node<K, V>? = null,
        var parent: Node<K, V>? = null,
        var color: Boolean = RED // true for RED, false for BLACK
    ) : MutableMap.MutableEntry<K, V> {
        override fun setValue(newValue: V): V {
            val oldValue = value
            value = newValue
            return oldValue
        }

        override fun equals(other: Any?): Boolean {
            if (other !is Map.Entry<*, *>) return false
            return key == other.key && value == other.value
        }

        override fun hashCode(): Int = key.hashCode() xor value.hashCode()
        override fun toString(): String = "$key=$value"
    }

    companion object {
        private const val RED = true
        private const val BLACK = false
    }

    // NavigableMap implementation
    override fun comparator(): Comparator<in K>? = comparator

    override fun firstKey(): K = firstEntry()?.key ?: throw NoSuchElementException()
    override fun lastKey(): K = lastEntry()?.key ?: throw NoSuchElementException()

    override fun firstEntry(): Map.Entry<K, V>? {
        var p = root
        if (p == null) return null
        while (p?.left != null) p = p.left
        return p
    }

    override fun lastEntry(): Map.Entry<K, V>? {
        var p = root
        if (p == null) return null
        while (p?.right != null) p = p.right
        return p
    }

    override fun pollFirstEntry(): Map.Entry<K, V>? {
        val e = firstEntry() ?: return null
        remove(e.key)
        return e
    }

    override fun pollLastEntry(): Map.Entry<K, V>? {
        val e = lastEntry() ?: return null
        remove(e.key)
        return e
    }

    override fun lowerEntry(key: K): Map.Entry<K, V>? {
        var p = root
        while (p != null) {
            val cmp = compareKeys(key, p.key)
            when {
                cmp > 0 -> {
                    if (p.right == null) return p
                    p = p.right
                }
                cmp <= 0 -> {
                    if (p.left == null) {
                        var parent = p.parent
                        var ch = p
                        while (parent != null && ch === parent.left) {
                            ch = parent
                            parent = parent.parent
                        }
                        return parent
                    }
                    p = p.left
                }
            }
        }
        return null
    }

    override fun lowerKey(key: K): K? = lowerEntry(key)?.key

    override fun floorEntry(key: K): Map.Entry<K, V>? {
        var p = root
        while (p != null) {
            val cmp = compareKeys(key, p.key)
            when {
                cmp > 0 -> {
                    if (p.right == null) return p
                    p = p.right
                }
                cmp < 0 -> {
                    if (p.left == null) {
                        var parent = p.parent
                        var ch = p
                        while (parent != null && ch === parent.left) {
                            ch = parent
                            parent = parent.parent
                        }
                        return parent
                    }
                    p = p.left
                }
                else -> return p
            }
        }
        return null
    }

    override fun floorKey(key: K): K? = floorEntry(key)?.key

    override fun ceilingEntry(key: K): Map.Entry<K, V>? {
        var p = root
        while (p != null) {
            val cmp = compareKeys(key, p.key)
            when {
                cmp < 0 -> {
                    if (p.left == null) return p
                    p = p.left
                }
                cmp > 0 -> {
                    if (p.right == null) {
                        var parent = p.parent
                        var ch = p
                        while (parent != null && ch === parent.right) {
                            ch = parent
                            parent = parent.parent
                        }
                        return parent
                    }
                    p = p.right
                }
                else -> return p
            }
        }
        return null
    }

    override fun ceilingKey(key: K): K? = ceilingEntry(key)?.key

    override fun higherEntry(key: K): Map.Entry<K, V>? {
        var p = root
        while (p != null) {
            val cmp = compareKeys(key, p.key)
            when {
                cmp < 0 -> {
                    if (p.left == null) return p
                    p = p.left
                }
                cmp >= 0 -> {
                    if (p.right == null) {
                        var parent = p.parent
                        var ch = p
                        while (parent != null && ch === parent.right) {
                            ch = parent
                            parent = parent.parent
                        }
                        return parent
                    }
                    p = p.right
                }
            }
        }
        return null
    }

    override fun higherKey(key: K): K? = higherEntry(key)?.key

    override fun descendingMap(): NavigableMap<K, V> = DescendingTreeMap(this)

    override fun navigableKeySet(): NavigableSet<K> = TreeSet(this)
    override fun descendingKeySet(): NavigableSet<K> = descendingMap().navigableKeySet()

    override fun subMap(fromKey: K, fromInclusive: Boolean, toKey: K, toInclusive: Boolean): NavigableMap<K, V> =
        SubMap(this, fromKey, fromInclusive, toKey, toInclusive)

    override fun headMap(toKey: K, inclusive: Boolean): NavigableMap<K, V> =
        SubMap(this, null, false, toKey, inclusive)

    override fun tailMap(fromKey: K, inclusive: Boolean): NavigableMap<K, V> =
        SubMap(this, fromKey, inclusive, null, false)

    // SortedMap implementation
    override fun subMap(fromKey: K, toKey: K): SortedMap<K, V> =
        subMap(fromKey, true, toKey, false)

    override fun headMap(toKey: K): SortedMap<K, V> =
        headMap(toKey, false)

    override fun tailMap(fromKey: K): SortedMap<K, V> =
        tailMap(fromKey, true)

    // MutableMap implementation
    override val size: Int get() = _size
    override fun isEmpty(): Boolean = _size == 0

    override fun containsKey(key: K): Boolean = getEntry(key) != null
    override fun containsValue(value: V): Boolean {
        if (root == null) return false
        val stack = mutableListOf<Node<K, V>>()
        var node = root
        while (node != null || stack.isNotEmpty()) {
            while (node != null) {
                stack.add(node)
                node = node.left
            }
            node = stack.removeAt(stack.lastIndex)
            if (value == node.value) return true
            node = node.right
        }
        return false
    }

    override fun get(key: K): V? = getEntry(key)?.value

    override fun put(key: K, value: V): V? {
        var t = root
        if (t == null) {
            root = Node(key, value)
            _size = 1
            return null
        }
        var parent: Node<K, V>
        var cmp: Int
        do {
            parent = t
            cmp = compareKeys(key, t.key)
            when {
                cmp < 0 -> t = t.left
                cmp > 0 -> t = t.right
                else -> {
                    val oldValue = t.value
                    t.value = value
                    return oldValue
                }
            }
        } while (t != null)

        val e = Node(key, value, parent = parent)
        when {
            cmp < 0 -> parent.left = e
            else -> parent.right = e
        }
        fixAfterInsertion(e)
        _size++
        return null
    }

    override fun putAll(from: Map<out K, V>) {
        for ((key, value) in from) {
            put(key, value)
        }
    }

    override fun remove(key: K): V? {
        val p = getEntry(key) ?: return null
        val oldValue = p.value
        deleteEntry(p)
        return oldValue
    }

    override fun clear() {
        modCount++
        root = null
        _size = 0
    }

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = object : AbstractMutableSet<MutableMap.MutableEntry<K, V>>() {
            override val size: Int get() = this@TreeMap.size
            override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> =
                object : MutableIterator<MutableMap.MutableEntry<K, V>> {
                    private var next = firstEntry()
                    private var lastReturned: Node<K, V>? = null
                    private val expectedModCount = modCount

                    override fun hasNext(): Boolean = next != null

                    override fun next(): MutableMap.MutableEntry<K, V> {
                        if (modCount != expectedModCount)
                            throw ConcurrentModificationException()
                        val e = next ?: throw NoSuchElementException()
                        lastReturned = e
                        next = successor(e)
                        return e as MutableMap.MutableEntry<K, V>
                    }

                    override fun remove() {
                        if (lastReturned == null)
                            throw IllegalStateException()
                        if (modCount != expectedModCount)
                            throw ConcurrentModificationException()
                        if (lastReturned?.left != null && lastReturned?.right != null)
                            next = lastReturned
                        deleteEntry(lastReturned!!)
                        lastReturned = null
                    }
                }

            override fun add(element: MutableMap.MutableEntry<K, V>): Boolean {
                throw UnsupportedOperationException()
            }
        }

    override val keys: MutableSet<K>
        get() = navigableKeySet()

    override val values: MutableCollection<V>
        get() = object : AbstractMutableCollection<V>() {
            override val size: Int get() = this@TreeMap.size
            override fun iterator(): MutableIterator<V> =
                object : MutableIterator<V> {
                    private val entryIt = entries.iterator()
                    override fun hasNext(): Boolean = entryIt.hasNext()
                    override fun next(): V = entryIt.next().value
                    override fun remove() = entryIt.remove()
                }

            override fun add(element: V): Boolean {
                throw UnsupportedOperationException()
            }
        }

    // Helper methods
    private fun compareKeys(k1: K, k2: K): Int {
        @Suppress("UNCHECKED_CAST")
        return comparator?.compare(k1, k2) ?: (k1 as Comparable<K>).compareTo(k2)
    }

    private fun getEntry(key: K): Node<K, V>? {
        var p = root
        while (p != null) {
            val cmp = compareKeys(key, p.key)
            when {
                cmp < 0 -> p = p.left
                cmp > 0 -> p = p.right
                else -> return p
            }
        }
        return null
    }

    private fun successor(t: Node<K, V>): Node<K, V>? {
        if (t.right != null) {
            var p = t.right
            while (p?.left != null) p = p.left
            return p
        }
        var p = t.parent
        var ch = t
        while (p != null && ch === p.right) {
            ch = p
            p = p.parent
        }
        return p
    }

    private fun deleteEntry(p: Node<K, V>) {
        modCount++
        _size--

        if (p.left != null && p.right != null) {
            val s = successor(p)!!
            p.key = s.key
            p.value = s.value
            deleteEntry(s)
            return
        }

        val replacement = if (p.left != null) p.left else p.right
        if (replacement != null) {
            replacement.parent = p.parent
            if (p.parent == null)
                root = replacement
            else if (p === p.parent!!.left)
                p.parent!!.left = replacement
            else
                p.parent!!.right = replacement
            p.left = null
            p.right = null
            p.parent = null
            if (p.color == BLACK)
                fixAfterDeletion(replacement)
        } else if (p.parent == null) {
            root = null
        } else {
            if (p.color == BLACK)
                fixAfterDeletion(p)
            if (p.parent != null) {
                if (p === p.parent!!.left)
                    p.parent!!.left = null
                else if (p === p.parent!!.right)
                    p.parent!!.right = null
                p.parent = null
            }
        }
    }

    private fun rotateLeft(p: Node<K, V>) {
        val r = p.right!!
        p.right = r.left
        if (r.left != null)
            r.left!!.parent = p
        r.parent = p.parent
        if (p.parent == null)
            root = r
        else if (p === p.parent!!.left)
            p.parent!!.left = r
        else
            p.parent!!.right = r
        r.left = p
        p.parent = r
    }

    private fun rotateRight(p: Node<K, V>) {
        val l = p.left!!
        p.left = l.right
        if (l.right != null)
            l.right!!.parent = p
        l.parent = p.parent
        if (p.parent == null)
            root = l
        else if (p === p.parent!!.right)
            p.parent!!.right = l
        else
            p.parent!!.left = l
        l.right = p
        p.parent = l
    }

    private fun fixAfterInsertion(x: Node<K, V>) {
        var node = x
        node.color = RED
        while (node != null && node !== root && node.parent!!.color == RED) {
            if (parentOf(node) === leftOf(parentOf(parentOf(node)))) {
                val y = rightOf(parentOf(parentOf(node)))
                if (colorOf(y) == RED) {
                    setColor(parentOf(node), BLACK)
                    setColor(y, BLACK)
                    setColor(parentOf(parentOf(node)), RED)
                    node = parentOf(parentOf(node))
                } else {
                    if (node === rightOf(parentOf(node))) {
                        node = parentOf(node)
                        rotateLeft(node)
                    }
                    setColor(parentOf(node), BLACK)
                    setColor(parentOf(parentOf(node)), RED)
                    if (parentOf(parentOf(node)) != null)
                        rotateRight(parentOf(parentOf(node)))
                }
            } else {
                val y = leftOf(parentOf(parentOf(node)))
                if (colorOf(y) == RED) {
                    setColor(parentOf(node), BLACK)
                    setColor(y, BLACK)
                    setColor(parentOf(parentOf(node)), RED)
                    node = parentOf(parentOf(node))
                } else {
                    if (node === leftOf(parentOf(node))) {
                        node = parentOf(node)
                        rotateRight(node)
                    }
                    setColor(parentOf(node), BLACK)
                    setColor(parentOf(parentOf(node)), RED)
                    if (parentOf(parentOf(node)) != null)
                        rotateLeft(parentOf(parentOf(node)))
                }
            }
        }
        root!!.color = BLACK
    }

    private fun fixAfterDeletion(x: Node<K, V>) {
        var node = x
        while (node !== root && colorOf(node) == BLACK) {
            if (node === leftOf(parentOf(node))) {
                var sib = rightOf(parentOf(node))
                if (colorOf(sib) == RED) {
                    setColor(sib, BLACK)
                    setColor(parentOf(node), RED)
                    rotateLeft(parentOf(node))
                    sib = rightOf(parentOf(node))
                }
                if (colorOf(leftOf(sib)) == BLACK && colorOf(rightOf(sib)) == BLACK) {
                    setColor(sib, RED)
                    node = parentOf(node)
                } else {
                    if (colorOf(rightOf(sib)) == BLACK) {
                        setColor(leftOf(sib), BLACK)
                        setColor(sib, RED)
                        rotateRight(sib)
                        sib = rightOf(parentOf(node))
                    }
                    setColor(sib, colorOf(parentOf(node)))
                    setColor(parentOf(node), BLACK)
                    setColor(rightOf(sib), BLACK)
                    rotateLeft(parentOf(node))
                    node = root!!
                }
            } else {
                var sib = leftOf(parentOf(node))
                if (colorOf(sib) == RED) {
                    setColor(sib, BLACK)
                    setColor(parentOf(node), RED)
                    rotateRight(parentOf(node))
                    sib = leftOf(parentOf(node))
                }
                if (colorOf(rightOf(sib)) == BLACK && colorOf(leftOf(sib)) == BLACK) {
                    setColor(sib, RED)
                    node = parentOf(node)
                } else {
                    if (colorOf(leftOf(sib)) == BLACK) {
                        setColor(rightOf(sib), BLACK)
                        setColor(sib, RED)
                        rotateLeft(sib)
                        sib = leftOf(parentOf(node))
                    }
                    setColor(sib, colorOf(parentOf(node)))
                    setColor(parentOf(node), BLACK)
                    setColor(leftOf(sib), BLACK)
                    rotateRight(parentOf(node))
                    node = root!!
                }
            }
        }
        setColor(node, BLACK)
    }

    private fun parentOf(p: Node<K, V>?): Node<K, V>? = p?.parent
    private fun leftOf(p: Node<K, V>?): Node<K, V>? = p?.left
    private fun rightOf(p: Node<K, V>?): Node<K, V>? = p?.right
    private fun colorOf(p: Node<K, V>?): Boolean = p?.color ?: BLACK
    private fun setColor(p: Node<K, V>?, c: Boolean) {
        p?.color = c
    }
} 