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
// Changed from Pair<Int, (Int) -> T> to Series<T>
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
    var observer: ((Twin<Series<T>>) -> Unit)? = null, // Changed SeriesData<T> to Series<T>
    var versionObserver: ((Twin<Long?>) -> Unit)? = null,

    ) { // REMOVED MutableSeries<T>

    var letter: COWSeriesBody<T> by Delegates.observable(letter1) { _, old, new ->
        // old and new are COWSeriesBody.
        // observer now expects Twin<Series<T>>.
        // old.asSeries() and new.asSeries() will return Series<T>.
        observer?.invoke(old.asSeries() j new.asSeries())
    }

    // Became direct members of CowSeriesHandle after removing MutableSeries<T>
    val size: Int get() = letter.size // Delegates to COWSeriesBody's size
    operator fun get(index: Int): T = letter[index] // Delegates to COWSeriesBody's get

    // Became direct members of CowSeriesHandle
    fun set(index: Int, item: T) {
        letter = letter.set(index, item) // COWSeriesBody.set returns a new COWSeriesBody
    }

    fun add(item: T) {
        letter = letter.append(item) // COWSeriesBody.append returns a new COWSeriesBody
    }

    fun add(index: Int, item: T) {
        letter = letter.insert(index, item) // COWSeriesBody.insert returns a new COWSeriesBody
    }

    fun removeAt(index: Int): T {
        val item = letter[index] // Use new get
        letter = letter.removeAt(index) // COWSeriesBody.removeAt returns a new COWSeriesBody
        return item
    }

    fun remove(item: T): Boolean {
        var indexToRemove = -1
        // letter is COWSeriesBody, which implements Series<T> via get/size
        for (i in 0 until letter.size) {
            if (letter[i] == item) {
                indexToRemove = i
                break
            }
        }
        if (indexToRemove != -1) {
            letter = letter.removeAt(indexToRemove) // This calls COWSeriesBody.removeAt
            return true
        }
        return false
    }

    fun clear() {
        letter = letter.clear() // COWSeriesBody.clear returns a new COWSeriesBody
    }

    // Note: Standard MutableCollection operators like plus/minus typically return new collections,
    // not modify in place and return this.
    // Changed return type from MutableSeries<T> to CowSeriesHandle<T>
    fun plus(item: T): CowSeriesHandle<T> {
        letter = letter.append(item); return this
    }

    // Changed return type from MutableSeries<T> to CowSeriesHandle<T>
    fun minus(item: T): CowSeriesHandle<T> {
        // remove() in COWSeriesBody returns a new COWSeriesBody, so this assignment is correct.
        letter = letter.remove(item); return this
    }

    fun plusAssign(item: T) {
        letter = letter.append(item) // COWSeriesBody.append returns a new COWSeriesBody
    }

    fun minusAssign(item: T) {
        letter = letter.remove(item) // COWSeriesBody.remove returns a new COWSeriesBody
    }

    // This get(range) returns COWSeriesBody, not Series<T> or MutableSeries<T>.
    // This is specific to CowSeriesHandle's API.
    operator fun get(range: IntRange): COWSeriesBody<T> = letter.get(range) // Delegates to COWSeriesBody

    //version from backing
    // COWSeriesBody has 'version', and also 'size'/'get' which makes it Series-like.
    val version: Any get() = letter.version ?: letter.toString() // Delegates to COWSeriesBody

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
    val backing: Series<T> = emptySeries<T>(), // Type changed to Series<T>
    override val version: Long? = null
) : VersionedSeries<T> { // VersionedSeries extends Series<T> (core)

    // Explicit implementation of Series<T> (from VersionedSeries<T>)
    // Accessing Join components using .a and .b
    override val size: Int get() = backing.a
    override operator fun get(index: Int): T = backing.b(index)

    /** create a new copy of this, with the given item inserted at the given index */
    fun set(index: Int, item: T): COWSeriesBody<T> {
        return copy(backing = size j { i: Int -> if (i == index) item else this[i] }, version = version?.inc())
    }

    /** create a new copy of this, with the given item appended */
    fun append(item: T): COWSeriesBody<T> {
        val newSize = size + 1
        // newBacking defines how to access elements: original ones or the new item at the end
        val newBacking: SeriesData<T> = newSize j { idx ->
            if (idx < size) this[idx] else item
        }
        return copy(backing = newBacking) // version will be incremented by copy
    }

    /** create a new copy of this, with the given item removed */
    fun remove(item: T): COWSeriesBody<T> {
        var indexToRemove = -1
        for (i in 0 until size) { // Iterate directly on SeriesData via 'this' (COWSeriesBody's get)
            if (this[i] == item) {
                indexToRemove = i
                break
            }
        }
        return if (indexToRemove != -1) removeAt(indexToRemove) else this
    }

    fun insert(index: Int, item: T): COWSeriesBody<T> {
        val newSize = size + 1
        // Changed 'to' to 'j' to create Series<T> (Join)
        val newBacking: Series<T> = newSize j { i ->
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
        // Changed 'to' to 'j' to create Series<T> (Join)
        val newBacking: Series<T> = newSize j { i ->
            if (i < index) this[i] else this[i + 1]
        }
        return copy(backing = newBacking)
    }

    fun clear(): COWSeriesBody<T> = copy(backing = emptySeries<T>()) // emptySeries<T>() is already Series<T>

    operator fun get(range: IntRange): COWSeriesBody<T> {
        val newSize = range.last - range.first + 1
        require(newSize >= 0) { "Range must not be empty" }
        val offset = range.first
        // Changed 'to' to 'j' to create Series<T> (Join)
        return copy(backing = newSize j { i -> this[offset + i] })
    }

    /** create a new copy of this, with potentially new backing and version */
    // Changed backing type to Series<T>
    private fun copy(backing: Series<T> = this.backing, version: Long? = this.version?.inc()): COWSeriesBody<T> =
        COWSeriesBody(backing, version)
}

