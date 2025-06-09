package borg.trikeshed.lib

package borg.trikeshed.lib

// Import Series, Join, and related functions directly from borg.trikeshed.core
import borg.trikeshed.core.Series // Still needed for extension functions at top/bottom of file
import borg.trikeshed.core.Join // Potentially needed if Series is Pair<Int, Join<...>> or similar complex cases
import borg.trikeshed.core.j // For the infix j function
import borg.trikeshed.core.toSeries // For ByteArray.toSeries() and String.toSeries()
import borg.trikeshed.core.α // For the infix α function

import borg.trikeshed.lib.CZero.nz // This seems to be a local utility

// Type alias for the underlying structure of Series<Byte> to improve readability locally
private typealias ByteSeriesData = Pair<Int, (Int) -> Byte>

// Extension functions defined on borg.trikeshed.core.Series<Byte> remain unchanged at the top
fun Series<Byte>.decodeUtf8(charArray: CharArray? = null): Series<Char> =
    charArray?.let { decodeDirtyUtf8(it) } ?: if (isDirtyUTF8()) decodeDirtyUtf8() else (this α {
        it.toInt().toChar()
    })

fun Series<Byte>.decodeDirtyUtf8(charArray: CharArray = CharArray(size)): Series<Char> {
    //does not use StringBuilder, but is faster than String(bytes, Charsets.UTF_8)
    var y = 0
    var w = 0
    while (y < this.size && w < charArray.size) {
        val c = this[y++].toInt()
        /* 0xxxxxxx */
        when (c shr 4) {
            in 0..7 -> charArray[w++] = c.toChar() // 0xxxxxxx

            /*12, 13*/ 0x0C, 0x0D -> {
            // 110x xxxx   10xx xxxx
            val c2 = this[y++].toInt()
            charArray[w++] = ((c and 0x1F) shl 6 or (c2 and 0x3F)).toChar()
        }

            /*14*/ 0x0E -> {
            // 1110 xxxx  10xx xxxx  10xx xxxx
            val c2 = this[y++].toInt()
            val c3 = this[y++].toInt()
            charArray[w++] = ((c and 0x0F) shl 12 or (c2 and 0x3F) shl 6 or (c3 and 0x3F)).toChar()
        }
        }
    }
    return w j charArray::get
}

fun Series<Byte>.asString(): String = toArray().decodeToString()

/**
 * byte based spiritual successor to ByteBuffer for parsing
 */
class ByteSeries(
    // bufParameter type changed from Series<Byte> to ByteSeriesData (Pair<Int, (Int) -> Byte>)
    bufParameter: ByteSeriesData,

    /** the mutable position accessor */
    var pos: Int = 0,

    /** the limit accessor */
    var limit: Int = bufParameter.first, //initialized to size from the Pair's first element

    /** the mark accessor */
    var mark: Int = -1,
    // Store the original series data internally
    private val internalSeriesData: ByteSeriesData
) { // No longer implements Series<Byte> in the signature

    val size: Int get() = internalSeriesData.first
    operator fun get(index: Int): Byte = internalSeriesData.second(index)

    /** get, the verb - the char at the current position and increment position */
    inline val get: Byte
        get() {
            if (!hasRemaining) throw IndexOutOfBoundsException("pos: $pos, limit: $limit")
            val c = this[pos]; pos++; return c
        }

    //string ctor
    // s.toSeries() returns Series<Char>, encodeToByteArray() is on String or CharSequence, then .toSeries() returns Series<Byte>
    // This Series<Byte> (which is Pair<Int, (Int)->Byte>) is then passed to the primary constructor.
    constructor(s: String) : this(s.toSeries().encodeToByteArray().toSeries())

    // Secondary constructor for ByteArray
    constructor(buf: ByteArray, pos: Int = 0, limit: Int = buf.size) : this(
        buf.toSeries(), // buf.toSeries() returns Series<Byte> (Pair<Int, (Int)->Byte>)
        pos,
        limit
    )

    // Primary constructor now takes ByteSeriesData (Pair<Int, (Int) -> Byte>)
    constructor(
        buf: ByteSeriesData, // Changed from Series<Byte>
        pos: Int = 0,
        limit: Int = buf.first, // Use buf.first (size) for limit default
        mark: Int = -1
    ) : this(pos, limit, mark, internalSeriesData = buf)


    /**remaining chars*/
    val rem: Int get() = limit - pos

    /** immutable max capacity of this buffer, alias for size*/
    val cap: Int get() = size // Use explicit size

    /** boolean indicating if there are remaining chars */
    val hasRemaining: Boolean get() = rem.nz

    /** mark, the verb - marks the current position */
    val mk: ByteSeries
        get() = apply {
            mark = pos
        }

    /** reset pos to mark */
    val res: ByteSeries
        get() = apply {
            pos = if (mark < 0) pos else mark
        }

    /** flip the buffer, limit becomes pos, pos becomes 0 -- made into a function for possible side effects in debugger */
    fun flip(): ByteSeries = apply {
        limit = pos
        pos = 0
        mark = -1
    }

    /**rewind to 0*/
    val rew: ByteSeries
        get() = apply {
            pos = 0
        }

    /** clears the mark,pos, and sets limit to size */
    val clr: ByteSeries
        get() = apply {
            pos = 0
            limit = size
            mark = -1
        }

    /** position, the verb - holds the position that will be returned by the next get */
    fun pos(p: Int): ByteSeries = apply {
        pos = p
    }

    /** slice creates/returns a subrange ByteSeries from pos until limit */
    val slice: ByteSeries
        get() {
            val pos1 = this.pos
            val limit1 = this.limit
            val rangeSize = limit1 - pos1
            // Create a new Pair representing the slice
            val slicedData: ByteSeriesData = rangeSize to { indexInSlice -> this[pos1 + indexInSlice] }
            return ByteSeries(slicedData, 0, rangeSize)
        }

    /** limit, the verb - redefines the last position accessable by get and redefines remaining accordingly*/
    fun lim(i: Int): ByteSeries = apply { limit = i }

    /** skip whitespace */
    val skipWs: ByteSeries get() = apply { while (hasRemaining && mk.get.toInt().toChar().isWhitespace()); res }

    val rtrim: ByteSeries get() = apply { while (rem > 0 && b(limit - 1).toInt().toChar().isWhitespace()) limit-- }


    fun clone(): ByteSeries = ByteSeries(internalSeriesData).also { it.pos = pos; it.limit = limit; it.mark = mark }


    /** a hash of contents only. not position, limit, mark */
    val cacheCode: Int
        get() {
            var h = 1
            for (i in pos until limit) {
                // Use explicit get
                h = 31 * h + this[i].hashCode()
            }
            return h
        }

    override fun equals(other: Any?): Boolean {
        when {
            this === other -> return true
            other !is ByteSeries -> return false
            pos != other.pos -> return false
            limit != other.limit -> return false
            mark != other.mark -> return false
            size != other.size -> return false // Uses explicit size
            else -> {
                // Use explicit get
                for (i in 0 until size) if (this[i] != other[i]) return false
                return true
            }
        }
    }

    /** idempotent, a cache can contain this hash and safely deduce the result from previous inserts */
    override fun hashCode(): Int {
        var result = pos
        result = 31 * result + limit
        result = 31 * result + mark
        result = 31 * result + size
//include cachecode
        result = 31 * result + cacheCode
        return result
    }


    fun asString(upto: Int = Int.MAX_VALUE): String = toArray().decodeToChars().asString().take(upto)

    override fun toString(): String {
        val take = asString().take(4)
        return "ByteSeries(position=$pos, limit=$limit, mark=$mark, cacheCode=$cacheCode,take-4=${take})"
    }

    /** skipws and rtrim */
    val trim: ByteSeries
        get() = apply {
            var p = pos
            var l = limit
            while (p < l && (0xff and get(p).toInt()).toChar().isWhitespace()) p++
            while (l > p && (0xff and get(l.dec()).toInt()).toChar().isWhitespace()) l--
            lim(l)
            pos(p)
        }


    //isEmpty override
    val isEmpty: Boolean get() = pos == limit

    /** success move position to the char after found (exclusive) and returns true.
     *  fail returns false and leaves position unchanged */
    fun seekTo(
        /**target*/
        target: Byte,
    ): Boolean {
        val anchor = pos
        var escaped = false
        while (hasRemaining) {
            val c = get
            if (c == target)
                return true
        }
        pos = anchor
        return false
    }

    /** success move position to the char after found and returns true.
     *  fail returns false and leaves position unchanged */
    fun seekTo(
        /**target*/
        target: Byte,
        /**if present this escapes one char*/
        escape: Byte,
    ): Boolean {
        val anchor = pos
        var escaped = false
        while (hasRemaining) get.let { c ->
            if (escaped) escaped = false
            else when (c) {
                target -> return true
                escape -> escaped = true
            }
        }
        pos = anchor
        return false
    }

    fun seekTo(lit: ByteSeriesData): Boolean { // Parameter changed to ByteSeriesData
        val anchor = pos
        var i = 0
        val litSize = lit.first
        val litGetter = lit.second
        while (hasRemaining) {
            if (get == litGetter(i)) {
                i++
                if (i == litSize) return true
            } else {
                // If mismatch, reset sequence matching and rewind our main series position
                // to where it was before starting to match this instance of 'lit'.
                // This is tricky. A simple i=0 is not enough if 'lit' can have repeating prefixes.
                // For now, a simple reset. More robust would be KMP algo style.
                // Current 'get' has advanced pos. We need to backtrack.
                // This naive approach might be buggy for overlapping patterns.
                // However, the original code also had a simple i=0.
                pos -= i // backtrack what we consumed from main series
                i = 0
                // Re-evaluate the current char from main series against start of lit, if possible
                if (hasRemaining && get == litGetter(i)) {
                     i++
                     if (i == litSize) return true
                } else {
                    // if current char also doesn't match start of lit, then advance main series
                    // (implicit in next loop of while(hasRemaining))
                    // and reset i. (already done by i=0 if previous get failed)
                }
            }
        }
        pos = anchor
        return false
    }

    /**backtrack 1*/
    operator fun dec(): ByteSeries = apply { require(pos > 0) { "Underflow" }; pos-- }

    /** advance 1*/
    operator fun inc(): ByteSeries = apply { require(hasRemaining) { "Overflow" };pos++ }

    /**
     * this rewrites the Series default toArray() to use the position and limit
     */
    fun toArray(): ByteArray = ByteArray(rem) { this[pos + it] } // Use explicit get, relative to current pos

}
/**
 * Checks if the `Series<Byte>` contains dirty UTF-8 encoding.
 * Dirty UTF-8 encoding is identified by the presence of certain byte patterns.
 *
 * @return `true` if the `Series<Byte>` contains dirty UTF-8 encoding, `false` otherwise.
 */
fun Series<Byte>.isDirtyUTF8(): Boolean {
    var dirty = false
    val bsz = this.size
    // If there is one more byte to test and the first byte is in the range of 110x xxxx
    // What `shr 4` proves: 110x xxxx
    val barLen = bsz.dec()
    for (b in 0 until barLen)
        if ((this[b].toInt() shr 4) in 0x0C..0x0E) {
            // What `shr 4` proves: 1110 xxxx
            val byte = this[b.inc()]
            if ((byte.toInt() shr 6) == 0x02) {
                dirty = true
                break
                // What `shr 6` proves: 10xx xxxx
            }
        }
    return dirty
}

fun ByteSeries.decodeToString() = decodeUtf8().asString()

fun Series<Byte>.startsWith(s: String): Boolean {
    val join = s.encodeToByteArray() α { it }
    return join.size <= size && join.zip(this).`▶`.all { it.first == it.second }
}

fun Series<Byte>.endsWith(s: String): Boolean {
    val join = s.encodeToByteArray() α { it }
    // Assuming borg.trikeshed.core.`▶` is available or this zip().all pattern works with core.Series
    return join.size <= size && join.zip(this.reversed()).all { it.first == it.second }
}
// Removed local typealias Series<T> = Join<Int, (Int) -> T>
// The file will now use borg.trikeshed.core.Series and borg.trikeshed.core.Join via imports.

/**
 * Extension function to split a `Series<Byte>` by a given delimiter.
 *
 * @receiver The `Series<Byte>` to be split.
 * @param delim The byte delimiter to split the series by.
 * @return A `Series<Series<Byte>>` where each sub-series is a segment of the original series split by the delimiter.
 */
operator fun Series<Byte>.div(delim: Byte): Series<Series<Byte>> { //lazy split
    // List to hold the indices of delimiter positions
    val intList = mutableListOf<Int>()
    // Iterate over the series and add the index of each delimiter to the list
    for (x in 0 until size) if (this[x] == delim) intList.add(x)

    /**
     * iarr is an index of delimited endings of the ByteSeries.
     */
    val iarr: IntArray = intList.toIntArray()

    // Create and return a series of sub-series split by the delimiter
    return iarr α { x ->
        // Determine the start position of the next segment
        val p = if (x == 0) 0 else iarr[x.dec()].inc() //start of next
        // Determine the end position of the current segment
        val l = //is x last index?
            if (x == iarr.lastIndex)
                this.size
            else
                iarr[x].dec()
        // Return the sub-series from start to end position
        this[p until l]
    }
}