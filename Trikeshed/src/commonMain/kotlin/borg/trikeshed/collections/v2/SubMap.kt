package borg.trikeshed.collections.v2

/**
 * A view of a portion of a TreeMap.
 */
internal class SubMap<K, V>(
    private val m: TreeMap<K, V>,
    private val fromKey: K?,
    private val fromInclusive: Boolean,
    private val toKey: K?,
    private val toInclusive: Boolean
) : NavigableMap<K, V>, AbstractMutableMap<K, V>() {

    init {
        if (fromKey != null && toKey != null && m.compareKeys(fromKey, toKey) > 0) {
            throw IllegalArgumentException("fromKey > toKey")
        }
    }

    override fun comparator(): Comparator<in K>? = m.comparator()

    override val size: Int get() {
        var count = 0
        val iter = entries.iterator()
        while (iter.hasNext()) {
            iter.next()
            count++
        }
        return count
    }

    override fun isEmpty(): Boolean = firstEntry() == null

    private fun tooLow(key: K): Boolean =
        fromKey != null && m.compareKeys(key, fromKey) < 0 ||
                (fromKey != null && !fromInclusive && m.compareKeys(key, fromKey) == 0)

    private fun tooHigh(key: K): Boolean =
        toKey != null && m.compareKeys(key, toKey) > 0 ||
                (toKey != null && !toInclusive && m.compareKeys(key, toKey) == 0)

    private fun inRange(key: K): Boolean = !tooLow(key) && !tooHigh(key)

    override fun containsKey(key: K): Boolean = inRange(key) && m.containsKey(key)
    override fun containsValue(value: V): Boolean {
        val iter = entries.iterator()
        while (iter.hasNext()) {
            if (iter.next().value == value) return true
        }
        return false
    }

    override fun get(key: K): V? = if (inRange(key)) m.get(key) else null

    override fun put(key: K, value: V): V? {
        if (!inRange(key)) throw IllegalArgumentException("key out of range")
        return m.put(key, value)
    }

    override fun remove(key: K): V? = if (inRange(key)) m.remove(key) else null

    override fun clear() {
        val iter = entries.iterator()
        while (iter.hasNext()) {
            iter.next()
            iter.remove()
        }
    }

    override fun firstKey(): K = firstEntry()?.key ?: throw NoSuchElementException()
    override fun lastKey(): K = lastEntry()?.key ?: throw NoSuchElementException()

    override fun firstEntry(): Map.Entry<K, V>? {
        val e = if (fromKey == null) m.firstEntry()
        else m.ceilingEntry(fromKey)
        return if (e != null && !tooHigh(e.key)) e else null
    }

    override fun lastEntry(): Map.Entry<K, V>? {
        val e = if (toKey == null) m.lastEntry()
        else m.floorEntry(toKey)
        return if (e != null && !tooLow(e.key)) e else null
    }

    override fun pollFirstEntry(): Map.Entry<K, V>? {
        val e = firstEntry() ?: return null
        m.remove(e.key)
        return e
    }

    override fun pollLastEntry(): Map.Entry<K, V>? {
        val e = lastEntry() ?: return null
        m.remove(e.key)
        return e
    }

    override fun lowerEntry(key: K): Map.Entry<K, V>? {
        if (!inRange(key)) throw IllegalArgumentException("key out of range")
        val e = m.lowerEntry(key)
        return if (e != null && inRange(e.key)) e else null
    }

    override fun lowerKey(key: K): K? = lowerEntry(key)?.key

    override fun floorEntry(key: K): Map.Entry<K, V>? {
        if (!inRange(key)) throw IllegalArgumentException("key out of range")
        val e = m.floorEntry(key)
        return if (e != null && inRange(e.key)) e else null
    }

    override fun floorKey(key: K): K? = floorEntry(key)?.key

    override fun ceilingEntry(key: K): Map.Entry<K, V>? {
        if (!inRange(key)) throw IllegalArgumentException("key out of range")
        val e = m.ceilingEntry(key)
        return if (e != null && inRange(e.key)) e else null
    }

    override fun ceilingKey(key: K): K? = ceilingEntry(key)?.key

    override fun higherEntry(key: K): Map.Entry<K, V>? {
        if (!inRange(key)) throw IllegalArgumentException("key out of range")
        val e = m.higherEntry(key)
        return if (e != null && inRange(e.key)) e else null
    }

    override fun higherKey(key: K): K? = higherEntry(key)?.key

    override fun descendingMap(): NavigableMap<K, V> = DescendingTreeMap(m).subMap(
        toKey ?: m.lastKey(),
        toInclusive,
        fromKey ?: m.firstKey(),
        fromInclusive
    )

    override fun navigableKeySet(): NavigableSet<K> = TreeSet(this)
    override fun descendingKeySet(): NavigableSet<K> = descendingMap().navigableKeySet()

    override fun subMap(fromKey: K, fromInclusive: Boolean, toKey: K, toInclusive: Boolean): NavigableMap<K, V> {
        if (!inRange(fromKey) || !inRange(toKey)) throw IllegalArgumentException("key out of range")
        return m.subMap(fromKey, fromInclusive, toKey, toInclusive)
    }

    override fun headMap(toKey: K, inclusive: Boolean): NavigableMap<K, V> {
        if (!inRange(toKey)) throw IllegalArgumentException("key out of range")
        return m.headMap(toKey, inclusive)
    }

    override fun tailMap(fromKey: K, inclusive: Boolean): NavigableMap<K, V> {
        if (!inRange(fromKey)) throw IllegalArgumentException("key out of range")
        return m.tailMap(fromKey, inclusive)
    }

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = object : AbstractMutableSet<MutableMap.MutableEntry<K, V>>() {
            override val size: Int get() = this@SubMap.size
            override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> =
                object : MutableIterator<MutableMap.MutableEntry<K, V>> {
                    private var next = firstEntry()
                    private var lastReturned: Map.Entry<K, V>? = null

                    override fun hasNext(): Boolean = next != null

                    override fun next(): MutableMap.MutableEntry<K, V> {
                        val e = next ?: throw NoSuchElementException()
                        lastReturned = e
                        val n = m.higherEntry(e.key)
                        next = if (n != null && inRange(n.key)) n else null
                        return e as MutableMap.MutableEntry<K, V>
                    }

                    override fun remove() {
                        val e = lastReturned ?: throw IllegalStateException()
                        this@SubMap.remove(e.key)
                        lastReturned = null
                    }
                }
        }

    override val keys: MutableSet<K>
        get() = navigableKeySet()

    override val values: MutableCollection<V>
        get() = object : AbstractMutableCollection<V>() {
            override val size: Int get() = this@SubMap.size
            override fun iterator(): MutableIterator<V> =
                object : MutableIterator<V> {
                    private val entryIt = entries.iterator()
                    override fun hasNext(): Boolean = entryIt.hasNext()
                    override fun next(): V = entryIt.next().value
                    override fun remove() = entryIt.remove()
                }
        }
} 