package borg.trikeshed.lib

// Core imports
import borg.trikeshed.core.Series // Needed for some function signatures & EmptySeries
import borg.trikeshed.core.Join // For explicit Pair usage if Series is not used directly
import borg.trikeshed.core.j
import borg.trikeshed.core.emptySeries
import borg.trikeshed.core.plus // If Series.plus is used for backing + s_[item]
import borg.trikeshed.core.Twin

import borg.trikeshed.common.collections.s_ // Seems to be a specific collection utility
import kotlin.properties.Delegates
import borg.trikeshed.lib.VersionedSeries
import borg.trikeshed.lib.MutableSeries

// Local type alias for the underlying structure of Series<T>
private typealias SeriesData<T> = Series<T>

// inline factory value for CopyOnWriteSeries
// The receiver 'this' is Series<T> (from core), which is Pair<Int, (Int)->T>
inline val <reified T> Series<T>.cow: CowSeriesHandle<T> get() = CowSeriesHandle(COWSeriesBody(this))


/**
 * CopyOnWriteSeries which creates a new copy of the backing Series on all mutation methods.
 *
 * this the envelope of a letter+envelope abstraction using CopyOnWriteSwappingSeries as the letter
 *
 * this contains a Long? immutable version attribute which is incremented during cloning, or stays null
 *
 */
class CowSeriesHandle<T>(
    letter1: COWSeriesBody<T>,
    var observer: ((Twin<Series<T>>) -> Unit)? = null,
    var versionObserver: ((Twin<Long?>) -> Unit)? = null,
) {

    var letter: COWSeriesBody<T> by Delegates.observable(letter1) { _, old, new ->
        observer?.invoke(old.asSeries() j new.asSeries())
    }

    val size: Int get() = letter.size
    operator fun get(index: Int): T = letter[index]

    fun set(index: Int, item: T) {
        letter = letter.set(index, item)
    }

    fun add(item: T) {
        letter = letter.append(item)
    }

    fun add(index: Int, item: T) {
        letter = letter.insert(index, item)
    }

    fun removeAt(index: Int): T {
        val item = letter[index]
        letter = letter.removeAt(index)
        return item
    }

    fun remove(item: T): Boolean {
        var indexToRemove = -1
        for (i in 0 until letter.size) {
            if (letter[i] == item) {
                indexToRemove = i
                break
            }
        }
        if (indexToRemove != -1) {
            letter = letter.removeAt(indexToRemove)
            return true
        }
        return false
    }

    fun clear() {
        letter = letter.clear()
    }

    fun plus(item: T): CowSeriesHandle<T> {
        letter = letter.append(item); return this
    }

    fun minus(item: T): CowSeriesHandle<T> {
        letter = letter.remove(item); return this
    }

    fun plusAssign(item: T) {
        letter = letter.append(item)
    }

    fun minusAssign(item: T) {
        letter = letter.remove(item)
    }

    // This get(range) returns COWSeriesBody, not Series<T> or MutableSeries<T>.
    // This is specific to CowSeriesHandle's API.
    operator fun get(range: IntRange): COWSeriesBody<T> = letter.get(range)

    //version from backing
    // COWSeriesBody has 'version', and also 'size'/'get' which makes it Series-like.
    val version: Any get() = letter.version ?: letter.toString()

    // Helper to expose Series<T> for observer
    fun asSeries(): Series<T> = letter.asSeries()
}

// Extension to get Series<T> from COWSeriesBody
fun <T> COWSeriesBody<T>.asSeries(): Series<T> = this.backing


/**
 * an immutable CopyOnWriteSwappingSeries (letter) which returns a new copy of itself on all add,set,append,remove,clear methods.
 *
 * this is the letter-and-envelope pattern for a mutable series.
 *
 * this contains a Long? immutable version attribute which is incremented during cloning, or stays null if the
 * object-identity is good enough for unordered version discriminator
 */
class COWSeriesBody<T>(
    val backing: Series<T> = emptySeries<T>(),
    override val version: Long? = null
) : VersionedSeries<T> {
    
    // Explicit implementation of Series<T> (from VersionedSeries<T>)
    override val size: Int get() = backing.a
    override operator fun get(index: Int): T = backing.b(index)

    fun set(index: Int, item: T): COWSeriesBody<T> {
        val newBacking: SeriesData<T> = size j { i: Int -> if (i == index) item else this[i] }
        return COWSeriesBody(newBacking, version?.inc())
    }

    fun append(item: T): COWSeriesBody<T> {
        val newSize = size + 1
        val newBacking: SeriesData<T> = newSize j { idx ->
            if (idx < size) this[idx] else item
        }
        return copy(backing = newBacking)
    }

    fun insert(index: Int, item: T): COWSeriesBody<T> {
        val newSize = size + 1
        val newBacking: Series<T> = newSize j { i ->
            when {
                i < index -> this[i]
                i > index -> this[i - 1]
                else -> item
            }
        }
        return copy(backing = newBacking)
    }

    fun remove(item: T): COWSeriesBody<T> {
        var indexToRemove = -1
        for (i in 0 until size) {
            if (this[i] == item) {
                indexToRemove = i
                break
            }
        }
        return if (indexToRemove != -1) removeAt(indexToRemove) else this
    }

    fun removeAt(index: Int): COWSeriesBody<T> {
        val newSize = size - 1
        val newBacking: Series<T> = newSize j { i ->
            if (i < index) this[i] else this[i + 1]
        }
        return copy(backing = newBacking)
    }

    fun clear(): COWSeriesBody<T> = copy(backing = emptySeries<T>())

    operator fun get(range: IntRange): COWSeriesBody<T> {
        val newSize = range.last - range.first + 1
        require(newSize >= 0) { "Range must not be empty" }
        val offset = range.first
        return copy(backing = newSize j { i -> this[offset + i] })
    }

    private fun copy(backing: Series<T> = this.backing, version: Long? = this.version?.inc()): COWSeriesBody<T> =
        COWSeriesBody(backing, version)
}
