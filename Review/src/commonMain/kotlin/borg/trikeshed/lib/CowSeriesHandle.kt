package borg.trikeshed.lib

// Core imports
import borg.trikeshed.core.Series // Needed for some function signatures & EmptySeries
import borg.trikeshed.core.Join // For explicit Pair usage if Series is not used directly
import borg.trikeshed.core.j
import borg.trikeshed.core.emptySeries
import borg.trikeshed.core.plus // If Series.plus is used for backing + s_[item]

import borg.trikeshed.common.collections.s_ // Seems to be a specific collection utility
import kotlin.properties.Delegates

// Local type alias for the underlying structure of Series<T>
private typealias SeriesData<T> = Pair<Int, (Int) -> T>

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
    var observer: ((Twin<SeriesData<T>>) -> Unit)? = null, // Changed Series<T> to SeriesData<T>
    var versionObserver: ((Twin<Long?>) -> Unit)? = null,

    ) : MutableSeries<T> { // MutableSeries<T> extends borg.trikeshed.lib.Series<T> (alias to core.Series<T>)

    var letter: COWSeriesBody<T> by Delegates.observable(letter1) { _, old, new ->
        // old and new are COWSeriesBody. As COWSeriesBody itself will provide SeriesData via its 'backing'
        // or if it still implements Series<T>, this j needs to be compatible.
        // Assuming 'j' can join COWSeriesBody instances or their relevant SeriesData parts.
        // If observer expects SeriesData: old.seriesData j new.seriesData
        // If observer expects Series<T> (Pair): old.asSeriesData() j new.asSeriesData()
        // For now, let's assume COWSeriesBody will be treated as Series<T> by 'j' due to its interface,
        // or j is smart enough. This might need adjustment based on how 'j' and Twin work.
        // The safest is to make Twin expect SeriesData if that's the common currency.
        // However, observer is (Twin<Series<T>>) -> Unit. Series<T> is Pair<Int, (Int)->T>.
        // So, old j new should produce Twin<Pair<Int,(Int)->T>>
        observer?.invoke(old.asSeriesData() j new.asSeriesData())
    }

    // Implementation of Series<T> from MutableSeries<T>
    override val size: Int get() = letter.size // Delegates to COWSeriesBody's size
    override operator fun get(index: Int): T = letter[index] // Delegates to COWSeriesBody's get

    // Implementation of MutableSeries<T> methods
    override fun set(index: Int, item: T) {
        letter = letter.set(index, item) // COWSeriesBody.set returns a new COWSeriesBody
    }

    override fun add(item: T) {
        letter = letter.append(item) // COWSeriesBody.append returns a new COWSeriesBody
    }

    override fun add(index: Int, item: T) {
        letter = letter.insert(index, item) // COWSeriesBody.insert returns a new COWSeriesBody
    }

    override fun removeAt(index: Int): T {
        val item = letter[index] // Use new get
        letter = letter.removeAt(index) // COWSeriesBody.removeAt returns a new COWSeriesBody
        return item
    }

    override fun remove(item: T): Boolean {
        // letter.backing is now Pair. Need to iterate it to find index.
        // This is inefficient. COWSeriesBody should provide an indexOf or contains method.
        // For now, convert to list to find index.
        val currentList = List(letter.size) { letter[it] }
        val i = currentList.indexOf(item)
        if (i != -1) {
            letter = letter.removeAt(i) // COWSeriesBody.removeAt
            return true
        }
        return false
    }

    override fun clear() {
        letter = letter.clear() // COWSeriesBody.clear returns a new COWSeriesBody
    }

    // Note: Standard MutableCollection operators like plus/minus typically return new collections,
    // not modify in place and return this. This might be a custom interpretation in MutableSeries.
    override fun plus(item: T): MutableSeries<T> {
        letter = letter.append(item); return this // Assuming this behavior is intended for MutableSeries
    }

    override fun minus(item: T): MutableSeries<T> {
        // remove() in COWSeriesBody returns a new COWSeriesBody, so this assignment is correct.
        letter = letter.remove(item); return this  // Assuming this behavior is intended for MutableSeries
    }

    override fun plusAssign(item: T) {
        letter = letter.append(item) // COWSeriesBody.append returns a new COWSeriesBody
    }

    override fun minusAssign(item: T) {
        letter = letter.remove(item) // COWSeriesBody.remove returns a new COWSeriesBody
    }

    // This get(range) returns COWSeriesBody, not Series<T> or MutableSeries<T>.
    // This is specific to CowSeriesHandle's API.
    operator fun get(range: IntRange): COWSeriesBody<T> = letter.get(range) // Delegates to COWSeriesBody

    //version from backing
    // COWSeriesBody has 'version', and also 'size'/'get' which makes it Series-like.
    val version: Any get() = letter.version ?: letter.toString() // Delegates to COWSeriesBody

    // Helper to expose SeriesData for observer
    fun asSeriesData(): SeriesData<T> = letter.asSeriesData()
}

// Extension to get SeriesData from COWSeriesBody
fun <T> COWSeriesBody<T>.asSeriesData(): SeriesData<T> = this.backing // Assuming backing is now SeriesData


/**
 * an immutable CopyOnWriteSwappingSeries (letter) which returns a new copy of itself on all add,set,append,remove,clear methods.
 *
 * this is the letter-and-envelope pattern for a mutable series.
 *
 * this contains a Long? immutable version attribute which is incremented during cloning, or stays null if the
 * object-identity is good enough for unordered version discriminator
 */
class COWSeriesBody<T>(
    val backing: SeriesData<T> = emptySeries<T>(), // Changed Series<T> to SeriesData<T>, and default
    override val version: Long? = null
) : VersionedSeries<T> { // VersionedSeries extends Series<T> (core)

    // Explicit implementation of Series<T> (from VersionedSeries<T>)
    override val size: Int get() = backing.first
    override operator fun get(index: Int): T = backing.second(index)

    /** create a new copy of this, with the given item inserted at the given index */
    fun set(index: Int, item: T): COWSeriesBody<T> {
        return copy(backing = size j { i: Int -> if (i == index) item else this[i] }, version = version?.inc())
    }

    /** create a new copy of this, with the given item appended */
    fun append(item: T): COWSeriesBody<T> {
        // Reconstruct as a list, append, then convert back to SeriesData
        val newList = List(size) { this[it] } + item
        return copy(backing = newList.size j newList::get)
    }

    /** create a new copy of this, with the given item removed */
    fun remove(item: T): COWSeriesBody<T> {
        val currentList = List(size) { this[it] }
        val i = currentList.indexOf(item)
        return if (i != -1) removeAt(i) else this
    }

    fun insert(index: Int, item: T): COWSeriesBody<T> {
        val newSize = size + 1
        val newBacking: SeriesData<T> = newSize to { i ->
            when {
                i < index -> this[i]
                i > index -> this[i - 1]
                else -> item
            }
        }
        return copy(backing = newBacking)
    }

    /** create a new copy of this, with the given item removed at the given index */
    fun removeAt(index: Int): COWSeriesBody<T> {
        val newSize = size - 1
        val newBacking: SeriesData<T> = newSize to { i ->
            if (i < index) this[i] else this[i + 1]
        }
        return copy(backing = newBacking)
    }

    fun clear(): COWSeriesBody<T> = copy(backing = emptySeries<T>())

    operator fun get(range: IntRange): COWSeriesBody<T> {
        val newSize = range.last - range.first + 1
        require(newSize >= 0) { "Range must not be empty" }
        val offset = range.first
        return copy(backing = newSize to { i -> this[offset + i] })
    }

    /** create a new copy of this, with potentially new backing and version */
    private fun copy(backing: SeriesData<T> = this.backing, version: Long? = this.version?.inc()): COWSeriesBody<T> =
        COWSeriesBody(backing, version)
}

