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
    var observer: ((Twin<SeriesData<T>>) -> Unit)? = null,
    var versionObserver: ((Twin<Long?>) -> Unit)? = null,
) : MutableSeries<T> {

    var letter: COWSeriesBody<T> by Delegates.observable(letter1) { _, old, new ->
        observer?.invoke(old.asSeriesData() j new.asSeriesData())
    }

    // Implementation of Series<T> from MutableSeries<T>
    override val size: Int get() = letter.size
    override operator fun get(index: Int): T = letter[index]

    // Implementation of MutableSeries<T> methods
    override fun set(index: Int, item: T) {
        letter = letter.set(index, item)
    }

    override fun add(item: T) {
        letter = letter.append(item)
    }

    override fun add(index: Int, item: T) {
        letter = letter.insert(index, item)
    }

    override fun removeAt(index: Int): T {
        val item = letter[index]
        letter = letter.removeAt(index)
        return item
    }

    override fun remove(item: T): Boolean {
        val currentList = List(letter.size) { letter[it] }
        val i = currentList.indexOf(item)
        if (i != -1) {
            letter = letter.removeAt(i)
            return true
        }
        return false
    }

    override fun clear() {
        letter = letter.clear()
    }

    // Note: Standard MutableCollection operators like plus/minus typically return new collections,
    // not modify in place and return this. This might be a custom interpretation in MutableSeries.
    override fun plus(item: T): MutableSeries<T> {
        letter = letter.append(item)
        return this
    }

    override fun minus(item: T): MutableSeries<T> {
        letter = letter.remove(item)
        return this
    }

    override fun plusAssign(item: T) {
        letter = letter.append(item)
    }

    override fun minusAssign(item: T) {
        letter = letter.remove(item)
    }

    // This get(range) returns COWSeriesBody, not Series<T> or MutableSeries<T>.
    // This is specific to CowSeriesHandle's API.
    operator fun get(range: IntRange): COWSeriesBody<T> = letter.get(range)

    //version from backing
    // COWSeriesBody has 'version', and also 'size'/'get' which makes it Series-like.
    val version: Any get() = letter.version ?: letter.toString()

    // Helper to expose SeriesData for observer
    fun asSeriesData(): SeriesData<T> = letter.asSeriesData()
}

// Extension to get SeriesData from COWSeriesBody
fun <T> COWSeriesBody<T>.asSeriesData(): SeriesData<T> = this.backing


/**
 * an immutable CopyOnWriteSwappingSeries (letter) which returns a new copy of itself on all add,set,append,remove,clear methods.
 *
 * this is the letter-and-envelope pattern for a mutable series.
 *
 * this contains a Long? immutable version attribute which is incremented during cloning, or stays null if the
 * object-identity is good enough for unordered version discriminator
 */
class COWSeriesBody<T>(
    val backing: SeriesData<T> = emptySeries(),
    override val version: Long? = null
) : VersionedSeries<T> {
    
    // Explicit implementation of Series<T> (from VersionedSeries<T>)
    override val size: Int get() = backing.size
    override operator fun get(index: Int): T = backing[index]

    fun set(index: Int, item: T): COWSeriesBody<T> {
        val newBacking: SeriesData<T> = size j { i: Int -> if (i == index) item else this[i] }
        return COWSeriesBody(newBacking, version?.inc())
    }

    fun append(item: T): COWSeriesBody<T> {
        val newBacking: SeriesData<T> = (size + 1) j { i: Int -> if (i == size) item else this[i] }
        return COWSeriesBody(newBacking, version?.inc())
    }

    fun insert(index: Int, item: T): COWSeriesBody<T> {
        val newBacking: SeriesData<T> = (size + 1) j { i: Int ->
            when {
                i < index -> this[i]
                i == index -> item
                else -> this[i - 1]
            }
        }
        return COWSeriesBody(newBacking, version?.inc())
    }
    fun remove(item: T): COWSeriesBody<T> {
        val currentList = List(size) { this[it] }
        val i = currentList.indexOf(item)
        return if (i != -1) removeAt(i) else this
    }

    fun removeAt(index: Int): COWSeriesBody<T> {
        val newBacking: SeriesData<T> = (size - 1) j { i: Int ->
            if (i < index) this[i] else this[i + 1]
        }
        return COWSeriesBody(newBacking, version?.inc())
    }

    fun clear(): COWSeriesBody<T> = COWSeriesBody(emptySeries(), version?.inc())

    operator fun get(range: IntRange): COWSeriesBody<T> {
        val newSize = range.last - range.first + 1
        require(newSize >= 0) { "Range must not be empty" }
        val newBacking: SeriesData<T> = newSize j { i: Int -> this[range.first + i] }
        return COWSeriesBody(newBacking, version)
    }
}
