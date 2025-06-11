package borg.trikeshed.lib

import borg.trikeshed.core.Series
import borg.trikeshed.core.j
import borg.trikeshed.core.toSeries
import borg.trikeshed.core.α

import borg.trikeshed.lib.CZero.nz

private typealias ByteSeriesData = Series<Byte>
//region ByteSeries specific utilities
fun Series<Byte>.decodeUtf8(charArray: CharArray? = null): Series<Char> =
    charArray?.let { decodeDirtyUtf8(it) } ?: if (isDirtyUTF8()) decodeDirtyUtf8() else (this α {
        it.toInt().toChar()
    })

fun Series<Byte>.decodeDirtyUtf8(charArray: CharArray = CharArray(size)): Series<Char> {
    var y = 0
    var w = 0
    while (y < this.size && w < charArray.size) {
        val c = this[y++].toInt()
        when (c shr 4) {
            in 0..7 -> charArray[w++] = c.toChar()
            0x0C, 0x0D -> {
                val c2 = this[y++].toInt()
                charArray[w++] = ((c and 0x1F) shl 6 or (c2 and 0x3F)).toChar()
            }
            0x0E -> {
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
class ByteSeries internal constructor(
    private val internalSeriesData: Series<Byte>,
    var pos: Int = 0,
    var limit: Int = internalSeriesData.a,
    var mark: Int = -1
) {

    val size: Int get() = internalSeriesData.a
    operator fun get(index: Int): Byte = internalSeriesData.b(index)

    /** get, the verb - the char at the current position and increment position */
    inline val get: Byte
        get() {
            if (!hasRemaining) throw IndexOutOfBoundsException("pos: $pos, limit: $limit")
            val c = this[pos]; pos++; return c
        }

    //string ctor
    constructor(s: String) : this(s.encodeToByteArray().toSeries())

    constructor(buf: ByteArray, pos: Int = 0, limit: Int = buf.size) : this(
        buf.toSeries(),
        pos,
        limit
    )

    /**remaining chars*/
    val rem: Int get() = limit - pos
    val cap: Int get() = size
    val hasRemaining: Boolean get() = rem.nz

    val mk: ByteSeries get() = apply { mark = pos }
    val res: ByteSeries get() = apply { pos = if (mark < 0) pos else mark }

    fun flip(): ByteSeries = apply {
        limit = pos
        pos = 0
        mark = -1
    }

    val rew: ByteSeries get() = apply { pos = 0 }
    val clr: ByteSeries get() = apply {
        pos = 0
        limit = size
        mark = -1
    }

    fun pos(p: Int): ByteSeries = apply { pos = p }

    /** slice creates/returns a subrange ByteSeries from pos until limit */
    val slice: ByteSeries
        get() {
            val pos1 = this.pos
            val limit1 = this.limit
            val rangeSize = limit1 - pos1
            val slicedData: Series<Byte> = rangeSize j { indexInSlice -> this[pos1 + indexInSlice] }
            return ByteSeries(slicedData, 0, rangeSize)
        }

    fun lim(i: Int): ByteSeries = apply { limit = i }

    val skipWs: ByteSeries
        get() = apply {
            while (hasRemaining && this[pos].toInt().toChar().isWhitespace()) {
                pos++
            }
        }
    val rtrim: ByteSeries
        get() = apply {
            while (rem > 0 && this[limit - 1].toInt().toChar().isWhitespace()) limit--
        }

    fun clone(): ByteSeries = ByteSeries(internalSeriesData).also { it.pos = pos; it.limit = limit; it.mark = mark }

    val cacheCode: Int get() {
        var h = 1
        for (i in pos until limit) {
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
            size != other.size -> return false
            else -> {
                for (i in 0 until size) if (this[i] != other[i]) return false
                return true
            }
        }
    }

    override fun hashCode(): Int {
        var result = pos
        result = 31 * result + limit
        result = 31 * result + mark
        result = 31 * result + size
        result = 31 * result + cacheCode
        return result
    }

    fun asString(upto: Int = Int.MAX_VALUE): String = toArray().decodeToString().take(upto)

    override fun toString(): String {
        val take = asString().take(4)
        return "ByteSeries(position=$pos, limit=$limit, mark=$mark, cacheCode=$cacheCode,take-4=${take})"
    }

    val trim: ByteSeries get() = apply {
        var p = pos
        var l = limit
        while (p < l && (0xff and get(p).toInt()).toChar().isWhitespace()) p++
        while (l > p && (0xff and get(l - 1).toInt()).toChar().isWhitespace()) l--
        lim(l)
        pos(p)
    }

    val isEmpty: Boolean get() = pos == limit

    fun seekTo(target: Byte): Boolean {
        val anchor = pos
        while (hasRemaining) {
            if (get == target) return true
        }
        pos = anchor
        return false
    }

    fun seekTo(target: Byte, escape: Byte): Boolean {
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

    fun seekTo(lit: Series<Byte>): Boolean {
        val anchor = pos
        if (lit.a == 0) return true // Empty literal always found at current position
        if (lit.a > rem) return false // Literal longer than remaining data

        val litSize = lit.a
        val litGetter = lit.b

        for (startIdx in pos..(limit - litSize)) {
            var match = true
            for (k in 0 until litSize) {
                if (this[startIdx + k] != litGetter(k)) {
                    match = false
                    break
                }
            }
            if (match) {
                pos = startIdx + litSize
                return true
            }
        }
        return false
    }

    operator fun dec(): ByteSeries = apply { require(pos > 0) { "Underflow" }; pos-- }
    operator fun inc(): ByteSeries = apply { require(hasRemaining) { "Overflow" };pos++ }

    fun toArray(): ByteArray = ByteArray(rem) { this[pos + it] }
}

// This function is used by ByteSeries.decodeUtf8
fun Series<Byte>.isDirtyUTF8(): Boolean {
    var dirty = false
    val bsz = this.size
    val barLen = if (bsz > 0) bsz - 1 else 0
    for (b in 0 until barLen)
        if ((this[b].toInt() shr 4) in 0x0C..0x0E) {
            val byte = this[b + 1]
            if ((byte.toInt() shr 6) == 0x02) {
                dirty = true
                break
            }
        }
    return dirty
}

fun ByteSeries.decodeToString() = decodeUtf8().asString()

// These are extension functions on Series<Byte>, not ByteSeries
fun Series<Byte>.startsWith(s: String): Boolean {
    val stringAsSeries = s.encodeToByteArray().toSeries()
    return stringAsSeries.size <= size && stringAsSeries.zip(this).`▶`.all { it.first == it.second }
}

fun Series<Byte>.endsWith(s: String): Boolean {
    val stringAsSeries = s.encodeToByteArray().toSeries()
    return stringAsSeries.size <= size && stringAsSeries.zip(this.reversed()).`▶`.all { it.first == it.second }
}


operator fun Series<Byte>.div(delim: Byte): Series<Series<Byte>> {
    val intList = mutableListOf<Int>()
    for (x in 0 until size) if (this[x] == delim) intList.add(x)
    val iarr: IntArray = intList.toIntArray()
    return iarr.size j { x ->
        val p = if (x == 0) 0 else iarr[x - 1] + 1
        val l = if (x < iarr.size) iarr[x] else this.size
        (l - p) j { offset -> this[p + offset] }
    }
}
//endregion

//region CharSeries specific utilities
/**
 * char based spiritual successor to ByteBuffer for parsing
 */
class CharSeries(
    buf: Series<Char>,

    /** the mutable position accessor */
    var pos: Int = 0,

    /** the limit accessor */
    var limit: Int = buf.size, //initialized to size

    /** the mark accessor */
    var mark: Int = -1,
) : Series<Char> by buf { //delegate to the underlying series


    /** get, the verb - the char at the current position and increment position */
    inline val get: Char
        get() {
            if (!hasRemaining) throw IndexOutOfBoundsException("pos: $pos, limit: $limit")
            val c = get(pos); pos++; return c
        }

    //string ctor
    constructor(s: String) : this(s.toSeries())

    /**remaining chars*/
    val rem: Int get() = limit - pos

    /** immutable max capacity of this buffer, alias for size*/
    val cap: Int by ::size

    /** boolean indicating if there are remaining chars */
    val hasRemaining: Boolean get() = rem.nz

    /** mark, the verb - marks the current position */
    val mk: CharSeries
        get() = apply {
            mark = pos
        }

    /** reset pos to mark */
    val res: CharSeries
        get() = apply {
            pos = if (mark < 0) pos else mark
        }

    /** flip the buffer, limit becomes pos, pos becomes 0 -- made into a function for possible side effects in debugger */
    fun flip(): CharSeries = apply {
        limit = pos
        pos = 0
        mark = -1
    }

    /**rewind to 0*/
    val rew: CharSeries
        get() = apply {
            pos = 0
        }

    /** clears the mark,pos, and sets limit to size */
    val clr: CharSeries
        get() = apply {
            pos = 0
            limit = size
            mark = -1
        }

    /** position, the verb - holds the position that will be returned by the next get */
    fun pos(p: Int): CharSeries = apply {
        pos = p
    }

    /** slice creates/returns a subrange CharSeries from pos until limit */
    val slice: CharSeries
        get() {
            val pos1 = this.pos
            val limit1 = this.limit
            val intRange = pos1 until limit1
            val buf = (this)[intRange]
            return CharSeries(buf)
        }

    /** limit, the verb - redefines the last position accessable by get and redefines remaining accordingly*/
    fun lim(i: Int): CharSeries = apply { limit = i }

    /** skip whitespace */ // This is an extension property on CharSeries
    val skipWs: CharSeries
        get() = apply {
            while (hasRemaining && this[pos].isWhitespace()) {
                pos++
            }
        }

    val rtrim: CharSeries get() = apply { while (rem > 0 && b(limit - 1).isWhitespace()) limit-- }


    fun clone(): CharSeries = CharSeries(a j b).also { it.pos = pos; it.limit = limit; it.mark = mark }


    /** a hash of contents only. not position, limit, mark */
    val cacheCode: Int
        get() {
            var h = 1
            for (i in pos until limit) {
                h = 31 * h + b(i).hashCode()
            }
            return h
        }

    override fun equals(other: Any?): Boolean {
        when {
            this === other -> return true
            other !is CharSeries -> return false
            pos != other.pos -> return false
            limit != other.limit -> return false
            mark != other.mark -> return false
            size != other.size -> return false
            else -> {
                for (i in 0 until size) if (b(i) != other.b(i)) return false
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


    fun asString(upto: Int = Int.MAX_VALUE): String =
        ((limit - pos) j { x: Int -> this[x + pos] }).toArray().concatToString()

    override fun toString(): String {
        val take = asString().take(4)
        return "CharSeries(position=$pos, limit=$limit, mark=$mark, cacheCode=$cacheCode,take-4=${take})"
    }

    /**
     * Extension property to trim whitespace from both ends of a `CharSeries`.
     *
     * @receiver The `CharSeries` to be trimmed.
     * @return The `CharSeries` with leading and trailing whitespace removed.
     */
    val trim: CharSeries
        get() = apply { confixScope(Char::isWhitespace) }

    /**
     * Mutating operation to shrink the buffer.
     *
     * @param pred A predicate function that takes a `Char` and returns a `Boolean`.
     *             The buffer will be shrunk by removing characters from the start and end
     *             that satisfy this predicate.
     */
    fun confixScope(pred: (Char) -> Boolean) {
        var p = pos
        var l = limit
        // Increment the start position while the predicate is true
        while (p < l && pred(get(p))) p++
        // Decrement the end position while the predicate is true
        while (l > p && pred(get(l.dec()))) l--
        // Set the new limit
        lim(l)
        // Set the new position
        pos(p)
    }

    //isEmpty override
    val isEmpty: Boolean get() = pos == limit

    /**
     * Moves the position to the character after the target character if found.
     *
     * @param target The character to seek.
     * @return `true` if the target character is found and the position is moved to the character after it,
     *         `false` if the target character is not found and the position remains unchanged.
     */
    fun seekTo(
        /** The target character to seek. */
        target: Char,
    ): Boolean {
        val anchor = pos
        while (hasRemaining) {
            val c = get
            if (c == target) return true
        }
        pos = anchor
        return false
    }

    /**
     * Moves the position to the character after the target character if found.
     *
     * @param target The character to seek.
     * @param escape If present, this character escapes one character.
     * @return `true` if the target character is found and the position is moved to the character after it,
     *         `false` if the target character is not found and the position remains unchanged.
     *         Note: The original `CharSeries.kt` had `var escaped = false` but it was unused in this overload.
     */
    fun seekTo(
        /** The target character to seek. */
        target: Char,
        /** If present, this character escapes one character. */
        escape: Char,
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

    /**
     * Moves the position to the end of the given literal if found.
     *
     * @param lit The series of characters to seek.
     * @return `true` if the literal is found and the position is moved to the end of it,
     *         `false` if the literal is not found and the position remains unchanged.
     */
    fun seekTo(lit: Series<Char>): Boolean {
        if (lit.size == 0) return true // Empty literal always found at current position
        if (lit.size > rem) return false // Literal longer than remaining data

        val litSize = lit.size
        val litGetter = lit.b

        for (startIdx in pos..(limit - litSize)) {
            var match = true
            for (k in 0 until litSize) {
                if (this[startIdx + k] != litGetter(k)) {
                    match = false
                    break
                }
            }
            if (match) {
                pos = startIdx + litSize
                return true
            }
        }
        return false
    }
    /**
     * Moves the position back by one character.
     *
     * @return The `CharSeries` with the position moved back by one character.
     * @throws IllegalArgumentException if the position is already at the start.
     */
    operator fun dec(): CharSeries = apply { require(pos > 0) { "Underflow" }; pos-- }

    /**
     * Moves the position forward by one character.
     *
     * @return The `CharSeries` with the position moved forward by one character.
     * @throws IllegalArgumentException if there are no remaining characters.
     */
    operator fun inc(): CharSeries = apply { require(hasRemaining) { "Overflow" }; pos++ }

    /**
     * Converts the remaining characters in the `CharSeries` to a `CharArray`.
     *
     * @return A `CharArray` containing the remaining characters.
     * @throws IllegalStateException if the `CharSeries` is empty.
     */
    fun toArray(): CharArray {
        require(rem > 0) { "heads up: using an empty stateful CharSeries toArray()" }
        return CharArray(rem, ::get)
    }

    companion object {

        /**returns true and advances the position if the confix is {}*/
        fun unbrace(it: CharSeries): Boolean {
            val chlit = "{} "
            return confixFeature(it, chlit)
        }

        /**returns true and advances the position if the confix is []*/
        fun unbracket(it: CharSeries): Boolean {
            val chlit = "[] "
            return confixFeature(it, chlit)

        }

        /**returns true and advances the position if the series is quoted */
        fun unquote(it: CharSeries): Boolean {
            val chlit = "\"\" "
            return confixFeature(it, chlit)

        }

        /**
         * Applies a confix feature to the given CharSeries.
         *
         * @param client The CharSeries to apply the confix feature to.
         * @param chlit A string representing the confix characters.
         * @return True if the confix feature was successfully applied, false otherwise.
         */
        private fun confixFeature(client: CharSeries, chlit: String): Boolean {
            // Log the initial state of the CharSeries
            // logNone { "confix $chlit before: ${client.asString()}" } // Commented out due to missing logNone/debug
            var x = 0
            // Apply the confix scope to the CharSeries
            client.confixScope { test: Char ->
                val target = chlit[x]
                // Check if the current character matches the target character
                (target == test && x < 2).apply { if (this) x++ }
            }
            // Return true if the confix feature was successfully applied, false otherwise
            return x == 2 // .debug { // Commented out due to missing logNone/debug
                // Log the final state of the CharSeries
                // logNone { "confix $chlit  after: ${client.asString()}" } // Commented out due to missing logNone/debug
            // }
        }
    }
}

/**
 * Extension function to split a `Series<Char>` by a given delimiter.
 *
 * @receiver The `Series<Char>` to be split.
 * @param delim The character delimiter to split the series by.
 * @return A `Series<Series<Char>>` where each sub-series is a segment of the original series split by the delimiter.
 */
operator fun Series<Char>.div(delim: Char): Series<Series<Char>> { // lazy split
    // List to hold the indices of delimiter positions
    val intList = mutableListOf<Int>()
    // Iterate over the series and add the index of each delimiter to the list
    for (x in 0 until size) if (this[x] == delim) intList.add(x)

    /**
     * iarr is an index of delimited endings of the CharSeries.
     */
    val iarr: IntArray = intList.toIntArray()

    // Create and return a series of sub-series split by the delimiter
    return iarr α { x ->
        // Determine the start position of the next segment
        val p = if (x == 0) 0 else iarr[x.dec()].inc() // start of next
        // Determine the end position of the current segment
        val l = // is x last index?
            if (x == iarr.lastIndex)
                this.size
            else
                iarr[x].dec()
        // Return the sub-series from start to end position
        this[p until l]
    }
}

val Series<Char>.cs: CharSequence
    get() = object : CharSequence {
        override val length: Int by ::a
        override fun get(index: Int) = b(index)
        override fun toString(): String = asString()
        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = this@cs[startIndex until endIndex].cs
    }
//endregion
