package borg.trikeshed.collections

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlin.random.Random

/**
 * A thread-safe, lock-free skip list implementation of NavigableMap.
 * 
 * This implementation provides expected average log(n) time cost for containsKey,
 * get, put and remove operations and their variants. Insertion, removal,
 * update, and access operations safely execute concurrently by multiple threads.
 */
class ConcurrentSkipListMap<K, V>(
    private val comparator: Comparator<in K>? = null
) : NavigableMap<K, V> {

    companion object {
        private const val MAX_LEVEL = 31
        private val BASE_HEADER = Any()
    }

    /**
     * Node in the base list level
     */
    private class Node<K, V>(
        val key: K?,
        var value: V?,
        val next: AtomicRef<Node<K, V>?>
    ) {
        fun isBaseHeader(): Boolean = key === BASE_HEADER
        fun isMarker(): Boolean = value == null && !isBaseHeader()
    }

    /**
     * Index node for skip list levels
     */
    private open class Index<K, V>(
        val node: Node<K, V>,
        val down: Index<K, V>?,
        val right: AtomicRef<Index<K, V>?>
    )

    /**
     * Special index node for level headers
     */
    private class HeadIndex<K, V>(
        node: Node<K, V>,
        down: Index<K, V>?,
        right: AtomicRef<Index<K, V>?>,
        val level: Int
    ) : Index<K, V>(node, down, right)

    // Base header node - never removed
    @Suppress("UNCHECKED_CAST")
    private val baseHeader = Node<K, V>(BASE_HEADER as K, null, atomic(null))
    
    // Top-level head index
    private val head: AtomicRef<HeadIndex<K, V>> = atomic(
        HeadIndex(baseHeader, null, atomic(null), 0)
    )

    /**
     * Compares two keys using the comparator or natural ordering
     */
    @Suppress("UNCHECKED_CAST")
    private fun compareKeys(k1: K, k2: K): Int {
        return comparator?.compare(k1, k2) ?: (k1 as Comparable<K>).compareTo(k2)
    }

    /**
     * Returns a random level for a new node
     */
    private fun randomLevel(): Int {
        var level = 0
        while (Random.nextDouble() < 0.5 && level < MAX_LEVEL) {
            level++
        }
        return level
    }

    /**
     * Finds the node with the given key
     */
    private fun findNode(key: K): Node<K, V>? {
        var q = head.value
        
        outer@ while (true) {
            var r = q.right.value
            
            // Move right at current level
            while (r != null) {
                val rNode = r.node
                if (rNode.isBaseHeader()) {
                    r = r.right.value
                    continue
                }
                
                val rKey = rNode.key
                if (rKey != null) {
                    val c = compareKeys(key, rKey)
                    if (c == 0) {
                        return if (!rNode.isMarker()) rNode else null
                    }
                    if (c < 0) break
                }
                
                q = r
                r = r.right.value
            }
            
            // Move down to next level
            val down = q.down
            if (down != null) {
                @Suppress("UNCHECKED_CAST")
                q = down as HeadIndex<K, V>
            } else {
                // At base level, search base list
                var n = q.node.next.value
                while (n != null) {
                    if (!n.isBaseHeader() && n.key != null) {
                        val c = compareKeys(key, n.key!!)
                        if (c == 0) {
                            return if (!n.isMarker()) n else null
                        }
                        if (c < 0) break
                    }
                    n = n.next.value
                }
                return null
            }
        }
    }

    /**
     * Finds predecessor nodes at each level for insertion/deletion
     */
    private fun findPredecessors(key: K): Array<Index<K, V>?> {
        val currentHead = head.value
        val preds = arrayOfNulls<Index<K, V>>(currentHead.level + 1)
        var q: Index<K, V> = currentHead
        
        for (i in currentHead.level downTo 0) {
            var r = q.right.value
            while (r != null) {
                val rNode = r.node
                if (!rNode.isBaseHeader() && rNode.key != null) {
                    val c = compareKeys(rNode.key!!, key)
                    if (c < 0) {
                        q = r
                        r = r.right.value
                        continue
                    }
                }
                break
            }
            preds[i] = q
            val down = q.down
            q = if (down != null) down as Index<K, V> else break
        }
        return preds
    }

    override val size: Int
        get() {
            var count = 0
            var curr = baseHeader.next.value
            while (curr != null) {
                if (!curr.isMarker() && !curr.isBaseHeader()) {
                    count++
                }
                curr = curr.next.value
            }
            return count
        }

    override fun isEmpty(): Boolean = firstKey() == null

    override fun containsKey(key: K): Boolean {
        if (key == null) throw NullPointerException("Key cannot be null")
        return findNode(key) != null
    }

    override fun containsValue(value: V): Boolean {
        if (value == null) throw NullPointerException("Value cannot be null")
        var curr = baseHeader.next.value
        while (curr != null) {
            if (!curr.isMarker() && !curr.isBaseHeader() && curr.value == value) {
                return true
            }
            curr = curr.next.value
        }
        return false
    }

    override fun get(key: K): V? {
        if (key == null) throw NullPointerException("Key cannot be null")
        val node = findNode(key)
        return node?.value
    }

    override fun put(key: K, value: V): V? {
        if (key == null || value == null) throw NullPointerException("Null keys/values not allowed")
        
        retry@ while (true) {
            val preds = findPredecessors(key)
            val pred = preds[0]!!.node
            var succ = pred.next.value
            
            // Check if key already exists
            if (succ != null && !succ.isBaseHeader() && succ.key != null && compareKeys(succ.key!!, key) == 0) {
                val oldValue = succ.value
                if (oldValue == null) continue@retry // Concurrent deletion
                succ.value = value
                return oldValue
            }
            
            // Insert new node
            val newNode = Node(key, value, atomic(succ))
            if (!pred.next.compareAndSet(succ, newNode)) {
                continue@retry
            }
            
            // Add index levels
            val level = randomLevel()
            val currentLevel = head.value.level
            
            if (level > currentLevel) {
                // Need to add new head levels
                var oldHead = head.value
                for (i in currentLevel + 1..level) {
                    val newHead = HeadIndex(baseHeader, oldHead, atomic(null), i)
                    if (head.compareAndSet(oldHead, newHead)) {
                        oldHead = newHead
                    } else {
                        break // Someone else added levels, use current max
                    }
                }
            }
            
            // Build index chain
            var idx: Index<K, V>? = null
            for (i in 0..minOf(level, head.value.level)) {
                val pred = preds[i] ?: continue
                idx = Index(newNode, idx, atomic(null))
                
                // Insert index
                while (true) {
                    val succ = pred.right.value
                    idx.right.value = succ
                    if (pred.right.compareAndSet(succ, idx)) break
                    // Retry if CAS failed
                }
            }
            
            return null
        }
    }

    override fun remove(key: K): V? {
        if (key == null) throw NullPointerException("Key cannot be null")
        
        retry@ while (true) {
            val preds = findPredecessors(key)
            val pred = preds[0]!!.node
            val victim = pred.next.value
            
            if (victim == null || victim.isBaseHeader() || victim.key == null || compareKeys(victim.key!!, key) != 0) {
                return null // Not found
            }
            
            val oldValue = victim.value
            if (oldValue == null) continue@retry // Already deleted
            
            // Mark as deleted
            victim.value = null
            
            // Unlink from base list
            val succ = victim.next.value
            if (pred.next.compareAndSet(victim, succ)) {
                // Try to remove from index levels (best effort)
                for (i in 1..head.value.level) {
                    val predIdx = preds[i] ?: continue
                    var idx = predIdx.right.value
                    while (idx != null && idx.node == victim) {
                        val succIdx = idx.right.value
                        predIdx.right.compareAndSet(idx, succIdx)
                        break
                    }
                }
            }
            
            return oldValue
        }
    }

    override fun putAll(from: Map<out K, V>) {
        for ((key, value) in from) {
            put(key, value)
        }
    }

    override fun clear() {
        var curr = baseHeader.next.value
        while (curr != null) {
            curr.value = null // Mark as deleted
            val next = curr.next.value
            curr.next.value = null
            curr = next
        }
        baseHeader.next.value = null
        
        // Reset head to level 0
        head.value = HeadIndex(baseHeader, null, atomic(null), 0)
    }

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>> by lazy { EntrySet() }
    override val keys: MutableSet<K> by lazy { KeySet() }
    override val values: MutableCollection<V> by lazy { ValueCollection() }

    // NavigableMap methods
    override fun comparator(): Comparator<in K>? = comparator

    private fun findFirst(): Node<K, V>? {
        var curr = baseHeader.next.value
        while (curr != null && (curr.isMarker() || curr.isBaseHeader())) {
            curr = curr.next.value
        }
        return curr
    }

    private fun findLast(): Node<K, V>? {
        var last: Node<K, V>? = null
        var curr = baseHeader.next.value
        while (curr != null) {
            if (!curr.isMarker() && !curr.isBaseHeader()) {
                last = curr
            }
            curr = curr.next.value
        }
        return last
    }

    override fun firstKey(): K? = findFirst()?.key

    override fun lastKey(): K? = findLast()?.key

    override fun firstEntry(): Map.Entry<K, V>? {
        val node = findFirst()
        return if (node?.key != null && node.value != null) {
            SimpleEntry(node.key!!, node.value!!)
        } else null
    }

    override fun lastEntry(): Map.Entry<K, V>? {
        val node = findLast()
        return if (node?.key != null && node.value != null) {
            SimpleEntry(node.key!!, node.value!!)
        } else null
    }

    override fun lowerEntry(key: K): Map.Entry<K, V>? {
        var result: Node<K, V>? = null
        var curr = baseHeader.next.value
        
        while (curr != null) {
            if (!curr.isMarker() && !curr.isBaseHeader() && curr.key != null) {
                val c = compareKeys(curr.key!!, key)
                if (c < 0) {
                    result = curr
                } else if (c >= 0) {
                    break
                }
            }
            curr = curr.next.value
        }
        
        return if (result?.key != null && result.value != null) {
            SimpleEntry(result.key!!, result.value!!)
        } else null
    }

    override fun lowerKey(key: K): K? = lowerEntry(key)?.key

    override fun floorEntry(key: K): Map.Entry<K, V>? {
        var result: Node<K, V>? = null
        var curr = baseHeader.next.value
        
        while (curr != null) {
            if (!curr.isMarker() && !curr.isBaseHeader() && curr.key != null) {
                val c = compareKeys(curr.key!!, key)
                if (c <= 0) {
                    result = curr
                    if (c == 0) break
                } else {
                    break
                }
            }
            curr = curr.next.value
        }
        
        return if (result?.key != null && result.value != null) {
            SimpleEntry(result.key!!, result.value!!)
        } else null
    }

    override fun floorKey(key: K): K? = floorEntry(key)?.key

    override fun ceilingEntry(key: K): Map.Entry<K, V>? {
        var curr = baseHeader.next.value
        
        while (curr != null) {
            if (!curr.isMarker() && !curr.isBaseHeader() && curr.key != null) {
                val c = compareKeys(curr.key!!, key)
                if (c >= 0) {
                    return if (curr.value != null) {
                        SimpleEntry(curr.key!!, curr.value!!)
                    } else null
                }
            }
            curr = curr.next.value
        }
        return null
    }

    override fun ceilingKey(key: K): K? = ceilingEntry(key)?.key

    override fun higherEntry(key: K): Map.Entry<K, V>? {
        var curr = baseHeader.next.value
        
        while (curr != null) {
            if (!curr.isMarker() && !curr.isBaseHeader() && curr.key != null) {
                val c = compareKeys(curr.key!!, key)
                if (c > 0) {
                    return if (curr.value != null) {
                        SimpleEntry(curr.key!!, curr.value!!)
                    } else null
                }
            }
            curr = curr.next.value
        }
        return null
    }

    override fun higherKey(key: K): K? = higherEntry(key)?.key

    override fun pollFirstEntry(): Map.Entry<K, V>? {
        while (true) {
            val first = firstEntry() ?: return null
            val removedValue = remove(first.key)
            if (removedValue != null) {
                return SimpleEntry(first.key, removedValue)
            }
        }
    }

    override fun pollLastEntry(): Map.Entry<K, V>? {
        while (true) {
            val last = lastEntry() ?: return null
            val removedValue = remove(last.key)
            if (removedValue != null) {
                return SimpleEntry(last.key, removedValue)
            }
        }
    }

    override fun descendingMap(): NavigableMap<K, V> = throw UnsupportedOperationException("Not implemented")
    override fun navigableKeySet(): NavigableSet<K> = throw UnsupportedOperationException("Not implemented")
    override fun descendingKeySet(): NavigableSet<K> = throw UnsupportedOperationException("Not implemented")
    override fun subMap(fromKey: K, fromInclusive: Boolean, toKey: K, toInclusive: Boolean): NavigableMap<K, V> = throw UnsupportedOperationException("Not implemented")
    override fun headMap(toKey: K, inclusive: Boolean): NavigableMap<K, V> = throw UnsupportedOperationException("Not implemented")
    override fun tailMap(fromKey: K, inclusive: Boolean): NavigableMap<K, V> = throw UnsupportedOperationException("Not implemented")

    // Collection views
    private inner class EntrySet : AbstractMutableSet<MutableMap.MutableEntry<K, V>>() {
        override val size: Int get() = this@ConcurrentSkipListMap.size
        
        override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> = EntryIterator()
        
        override fun contains(element: MutableMap.MutableEntry<K, V>): Boolean {
            val v = get(element.key)
            return v != null && v == element.value
        }
        
        override fun remove(element: MutableMap.MutableEntry<K, V>): Boolean {
            val currentValue = get(element.key)
            return if (currentValue == element.value) {
                this@ConcurrentSkipListMap.remove(element.key) != null
            } else false
        }
        
        override fun add(element: MutableMap.MutableEntry<K, V>): Boolean {
            throw UnsupportedOperationException("add")
        }
    }

    private inner class KeySet : AbstractMutableSet<K>() {
        override val size: Int get() = this@ConcurrentSkipListMap.size
        override fun iterator(): MutableIterator<K> = KeyIterator()
        override fun contains(element: K): Boolean = containsKey(element)
        override fun remove(element: K): Boolean = this@ConcurrentSkipListMap.remove(element) != null
        override fun add(element: K): Boolean {
            throw UnsupportedOperationException("add")
        }
    }

    private inner class ValueCollection : AbstractMutableCollection<V>() {
        override val size: Int get() = this@ConcurrentSkipListMap.size
        override fun iterator(): MutableIterator<V> = ValueIterator()
        override fun contains(element: V): Boolean = containsValue(element)
        override fun add(element: V): Boolean {
            throw UnsupportedOperationException("add")
        }
    }

    // Iterators
    private abstract inner class BaseIterator<T> : MutableIterator<T> {
        protected var currentNode: Node<K, V>? = null
        protected var nextNode: Node<K, V>? = null

        init {
            advance()
        }

        private fun advance() {
            var curr = nextNode?.next?.value ?: baseHeader.next.value
            while (curr != null && (curr.isMarker() || curr.isBaseHeader())) {
                curr = curr.next.value
            }
            nextNode = curr
        }

        override fun hasNext(): Boolean = nextNode != null

        protected fun advanceToNext(): Node<K, V> {
            val current = nextNode ?: throw NoSuchElementException()
            currentNode = current
            advance()
            return current
        }

        override fun remove() {
            val node = currentNode ?: throw IllegalStateException()
            this@ConcurrentSkipListMap.remove(node.key!!)
            currentNode = null
        }
    }

    private inner class EntryIterator : BaseIterator<MutableMap.MutableEntry<K, V>>() {
        override fun next(): MutableMap.MutableEntry<K, V> {
            val node = advanceToNext()
            return MutableEntry(node.key!!, node.value!!)
        }
    }

    private inner class KeyIterator : BaseIterator<K>() {
        override fun next(): K = advanceToNext().key!!
    }

    private inner class ValueIterator : BaseIterator<V>() {
        override fun next(): V = advanceToNext().value!!
    }

    // Entry implementations
    private class SimpleEntry<K, V>(
        override val key: K,
        override val value: V
    ) : Map.Entry<K, V>

    private inner class MutableEntry(
        override val key: K,
        private var _value: V
    ) : MutableMap.MutableEntry<K, V> {
        override val value: V get() = _value
        
        override fun setValue(newValue: V): V {
            val oldValue = _value
            _value = newValue
            this@ConcurrentSkipListMap.put(key, newValue)
            return oldValue
        }
    }
}