@file:Suppress("SpellCheckingInspection", "ControlFlowWithEmptyBody")

package borg.trikeshed.lib

import borg.trikeshed.lib.CZero.nz

/**
 * CharIndexedBuffer - Optimized Indexed<Char> for text processing
 * Maximized for minimal forward scans and token fragment transformation
 * Spiritual successor to CharBuffer for parsing with Indexed<T> foundation
 */
class CharIndexedBuffer(
    buf: Indexed<Char>,

    /** the mutable position accessor */
    var pos: Int = 0,

    /** the limit accessor */
    var limit: Int = buf.a, //initialized to size

    /** the mark accessor */
    var mark: Int = -1,
) : Indexed<Char> by buf { //delegate to the underlying indexed

    /** get, the verb - the char at the current position and increment position */
    inline val get: Char
        get() {
            if (!hasRemaining) throw IndexOutOfBoundsException("pos: $pos, limit: $limit")
            val c = b(pos); pos++; return c
        }

    //string ctor
    constructor(s: String) : this(s.toIndexed())

    /**remaining chars*/
    val rem: Int get() = limit - pos

    /** immutable max capacity of this buffer, alias for size*/
    val cap: Int by ::a

    /** boolean indicating if there are remaining chars */
    val hasRemaining: Boolean get() = rem.nz

    /** mark, the verb - marks the current position */
    val mk: CharIndexedBuffer
        get() = apply {
            mark = pos
        }

    /** reset pos to mark */
    val res: CharIndexedBuffer
        get() = apply {
            pos = if (mark < 0) pos else mark
        }

    /** flip the buffer, limit becomes pos, pos becomes 0 -- made into a function for possible side effects in debugger */
    fun flip(): CharIndexedBuffer = apply {
        limit = pos
        pos = 0
        mark = -1
    }

    /**rewind to 0*/
    val rew: CharIndexedBuffer
        get() = apply {
            pos = 0
        }

    /** clears the mark,pos, and sets limit to size */
    val clr: CharIndexedBuffer
        get() = apply {
            pos = 0
            limit = a
            mark = -1
        }

    /** position, the verb - holds the position that will be returned by the next get */
    fun pos(p: Int): CharIndexedBuffer = apply {
        pos = p
    }

    /** slice creates/returns a subrange CharIndexedBuffer from pos until limit */
    val slice: CharIndexedBuffer
        get() {
            val pos1 = this.pos
            val limit1 = this.limit
            val intRange = pos1 until limit1
            val buf = (this)[intRange]
            return CharIndexedBuffer(buf)
        }

    /** limit, the verb - redefines the last position accessable by get and redefines remaining accordingly*/
    fun lim(i: Int): CharIndexedBuffer = apply { limit = i }

    /** skip whitespace */
    val skipWs: CharIndexedBuffer get() = apply { while (hasRemaining && mk.get.isWhitespace()); res }

    val rtrim: CharIndexedBuffer get() = apply { while (rem > 0 && b(limit - 1).isWhitespace()) limit-- }

    fun clone(): CharIndexedBuffer = CharIndexedBuffer(a j b).also { it.pos = pos; it.limit = limit; it.mark = mark }

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
            other !is CharIndexedBuffer -> return false
            pos != other.pos -> return false
            limit != other.limit -> return false
            mark != other.mark -> return false
            a != other.a -> return false
            else -> {
                for (i in 0 until a) if (b(i) != other.b(i)) return false
                return true
            }
        }
    }

    /** idempotent, a cache can contain this hash and safely deduce the result from previous inserts */
    override fun hashCode(): Int {
        var result = pos
        result = 31 * result + limit
        result = 31 * result + mark
        result = 31 * result + a
        //include cachecode
        result = 31 * result + cacheCode
        return result
    }

    fun asString(upto: Int = Int.MAX_VALUE): String {
        val chars = CharArray(limit - pos) { x -> this[x + pos] }
        return chars.concatToString()
    }

    override fun toString(): String {
        val take = asString().take(4)
        return "CharIndexedBuffer(position=$pos, limit=$limit, mark=$mark, cacheCode=$cacheCode,take-4=${take})"
    }

    /**
     * Extension property to trim whitespace from both ends of a `CharIndexedBuffer`.
     *
     * @receiver The `CharIndexedBuffer` to be trimmed.
     * @return The `CharIndexedBuffer` with leading and trailing whitespace removed.
     */
    val trim: CharIndexedBuffer
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
        var escaped = false
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
    fun seekTo(lit: Indexed<Char>): Boolean {
        val anchor = pos
        var i = 0
        while (hasRemaining) {
            if (get == lit[i]) {
                i++
                if (i == lit.a) return true
            } else {
                i = 0
            }
        }
        pos = anchor
        return false
    }

    /**
     * Moves the position back by one character.
     *
     * @return The `CharIndexedBuffer` with the position moved back by one character.
     * @throws IllegalArgumentException if the position is already at the start.
     */
    operator fun dec(): CharIndexedBuffer = apply { require(pos > 0) { "Underflow" }; pos-- }

    /**
     * Moves the position forward by one character.
     *
     * @return The `CharIndexedBuffer` with the position moved forward by one character.
     * @throws IllegalArgumentException if there are no remaining characters.
     */
    operator fun inc(): CharIndexedBuffer = apply { require(hasRemaining) { "Overflow" }; pos++ }

    /**
     * Converts the remaining characters in the `CharIndexedBuffer` to a `CharArray`.
     *
     * @return A `CharArray` containing the remaining characters.
     * @throws IllegalStateException if the `CharIndexedBuffer` is empty.
     */
    fun toArray(): CharArray {
        require(rem > 0) { "heads up: using an empty stateful CharIndexedBuffer toArray()" }
        return CharArray(rem, ::get)
    }

    /**
     * Protocol-specific scanning for minimal forward scans
     * Counts the smallest number of scans needed to process text protocol fragments
     */
    fun scanTextFragment(): TextFragment {
        val startPos = pos
        var scanCount = 0
        
        while (hasRemaining) {
            scanCount++
            
            when {
                // HTTP Method (GET, POST, etc.)
                pos == startPos -> {
                    val method = StringBuilder()
                    while (hasRemaining && get != ' ') {
                        method.append(get)
                    }
                    if (method.isNotEmpty()) {
                        return TextFragment.HTTP_METHOD(startPos, pos, scanCount, method.toString())
                    }
                }
                
                // HTTP Path
                pos > startPos && b(pos - 1) == ' ' -> {
                    val path = StringBuilder()
                    while (hasRemaining && get != ' ') {
                        path.append(get)
                    }
                    if (path.isNotEmpty()) {
                        return TextFragment.HTTP_PATH(startPos, pos, scanCount, path.toString())
                    }
                }
                
                // HTTP Headers
                pos > startPos && b(pos - 1) == '\n' -> {
                    val header = StringBuilder()
                    while (hasRemaining && get != '\n') {
                        header.append(get)
                    }
                    if (header.isNotEmpty()) {
                        return TextFragment.HTTP_HEADER(startPos, pos, scanCount, header.toString())
                    }
                }
                
                else -> {
                    get // advance
                }
            }
        }
        
        return TextFragment.INCOMPLETE(startPos, pos, scanCount)
    }

    /**
     * Safe sliced string view - returns a CharIndexed view without String allocation
     * This provides zero-copy string operations for protocol parsing
     */
    fun sliceView(start: Int = pos, end: Int = limit): CharIndexed {
        val safeStart = start.coerceIn(0, a)
        val safeEnd = end.coerceIn(safeStart, a)
        return (safeEnd - safeStart) j { i -> b(safeStart + i) }
    }

    /**
     * Safe sliced string view from current position to limit
     * Returns a CharIndexed view of remaining characters
     */
    val remainingView: CharIndexed
        get() = sliceView(pos, limit)

    /**
     * Safe sliced string view of consumed characters
     * Returns a CharIndexed view from start to current position
     */
    val consumedView: CharIndexed
        get() = sliceView(0, pos)

    /**
     * Safe string comparison without String allocation
     * Compares this CharIndexedBuffer view with another CharIndexed
     */
    fun contentEquals(other: CharIndexed): Boolean {
        if (rem != other.a) return false
        for (i in 0 until rem) {
            if (b(pos + i) != other[i]) return false
        }
        return true
    }

    /**
     * Safe string comparison with String (when absolutely necessary)
     * But prefers CharIndexed comparison for performance
     */
    fun contentEquals(other: String): Boolean {
        if (rem != other.length) return false
        for (i in 0 until rem) {
            if (b(pos + i) != other[i]) return false
        }
        return true
    }

    /**
     * Safe startsWith check using CharIndexed view
     */
    fun startsWith(prefix: CharIndexed): Boolean {
        if (prefix.a > rem) return false
        for (i in 0 until prefix.a) {
            if (b(pos + i) != prefix[i]) return false
        }
        return true
    }

    /**
     * Safe endsWith check using CharIndexed view
     */
    fun endsWith(suffix: CharIndexed): Boolean {
        if (suffix.a > rem) return false
        val start = pos + rem - suffix.a
        for (i in 0 until suffix.a) {
            if (b(start + i) != suffix[i]) return false
        }
        return true
    }

    /**
     * Safe substring operation that returns CharIndexed view
     * No String allocation - just a view into the underlying buffer
     */
    fun substring(start: Int = 0, end: Int = rem): CharIndexed {
        val safeStart = start.coerceIn(0, rem)
        val safeEnd = end.coerceIn(safeStart, rem)
        return (safeEnd - safeStart) j { i -> b(pos + safeStart + i) }
    }

    /**
     * Safe trim operation that returns CharIndexed view
     * Removes leading and trailing whitespace without String allocation
     */
    val trimmedView: CharIndexed
        get() {
            var start = pos
            var end = limit
            
            // Trim leading whitespace
            while (start < end && b(start).isWhitespace()) start++
            
            // Trim trailing whitespace
            while (end > start && b(end - 1).isWhitespace()) end--
            
            return (end - start) j { i -> b(start + i) }
        }

    /**
     * Safe split operation that returns CharIndexed views
     * No String allocation - just views into the underlying buffer
     */
    fun splitView(delimiter: Char): Indexed<CharIndexed> {
        val positions = mutableListOf<Int>()
        
        // Find all delimiter positions
        for (i in pos until limit) {
            if (b(i) == delimiter) positions.add(i)
        }
        
        // Create views for each segment
        return (positions.size + 1) j { segmentIndex ->
            val segmentStart = if (segmentIndex == 0) pos else positions[segmentIndex - 1] + 1
            val segmentEnd = if (segmentIndex == positions.size) limit else positions[segmentIndex]
            (segmentEnd - segmentStart) j { i -> b(segmentStart + i) }
        }
    }

    companion object {

        /**returns true and advances the position if the confix is {}*/
        fun unbrace(it: CharIndexedBuffer): Boolean {
            val chlit = "{} "
            return confixFeature(it, chlit)
        }

        /**returns true and advances the position if the confix is []*/
        fun unbracket(it: CharIndexedBuffer): Boolean {
            val chlit = "[] "
            return confixFeature(it, chlit)
        }

        /**returns true and advances the position if the series is quoted */
        fun unquote(it: CharIndexedBuffer): Boolean {
            val chlit = "\"\" "
            return confixFeature(it, chlit)
        }

        /**
         * Applies a confix feature to the given CharIndexedBuffer.
         *
         * @param client The CharIndexedBuffer to apply the confix feature to.
         * @param chlit A string representing the confix characters.
         * @return True if the confix feature was successfully applied, false otherwise.
         */
        private fun confixFeature(client: CharIndexedBuffer, chlit: String): Boolean {
            // Log the initial state of the CharIndexedBuffer
            // logNone { "confix $chlit before: ${client.asString()}" }
            var x = 0
            // Apply the confix scope to the CharIndexedBuffer
            client.confixScope { test: Char ->
                val target = chlit[x]
                // Check if the current character matches the target character
                (target == test && x < 2).apply { if (this) x++ }
            }
            // Return true if the confix feature was successfully applied, false otherwise
            return x == 2
            // .debug {
            //     // Log the final state of the CharIndexedBuffer
            //     logNone { "confix $chlit  after: ${client.asString()}" }
            // }
        }
    }
}

/**
 * Text Fragment - Represents a parsed text protocol fragment with scan optimization
 */
sealed class TextFragment(val startPos: Int, val endPos: Int, val scanCount: Int) {
    data class HTTP_METHOD(val sPos: Int, val ePos: Int, val sCount: Int, val method: String) : TextFragment(sPos, ePos, sCount)
    data class HTTP_PATH(val sPos: Int, val ePos: Int, val sCount: Int, val path: String) : TextFragment(sPos, ePos, sCount)
    data class HTTP_HEADER(val sPos: Int, val ePos: Int, val sCount: Int, val header: String) : TextFragment(sPos, ePos, sCount)
    data class INCOMPLETE(val sPos: Int, val ePos: Int, val sCount: Int) : TextFragment(sPos, ePos, sCount)
}

/**
 * Extension functions for CharIndexedBuffer text processing
 */
fun Indexed<Char>.toCharIndexedBuffer(pos: Int = 0, limit: Int = this.a): CharIndexedBuffer = CharIndexedBuffer(this, pos, limit)

/**
 * Extension function to split a `Indexed<Char>` by a given delimiter.
 *
 * @receiver The `Indexed<Char>` to be split.
 * @param delim The character delimiter to split the indexed by.
 * @return A `Indexed<Indexed<Char>>` where each sub-indexed is a segment of the original indexed split by the delimiter.
 */
operator fun Indexed<Char>.div(delim: Char): Indexed<Indexed<Char>> { // lazy split
    // List to hold the indices of delimiter positions
    val intList = mutableListOf<Int>()
    // Iterate over the indexed and add the index of each delimiter to the list
    for (x in 0 until a) if (this[x] == delim) intList.add(x)

    /**
     * iarr is an index of delimited endings of the CharIndexedBuffer.
     */
    val iarr: IntArray = intList.toIntArray()

    // Create and return a indexed of sub-indexed split by the delimiter
    return iarr α { x ->
        // Determine the start position of the next segment
        val p = if (x == 0) 0 else iarr[x.dec()].inc() // start of next
        // Determine the end position of the current segment
        val l = // is x last index?
            if (x == iarr.lastIndex)
                this.a
            else
                iarr[x].dec()
        // Return the sub-indexed from start to end position
        this[p until l]
    }
}

val Indexed<Char>.cs: CharSequence
    get() = object : CharSequence {
        override val length: Int by ::a
        override fun get(index: Int) = b(index)
        override fun toString(): String = toArray().concatToString()
        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = this@cs[startIndex until endIndex].cs
    } 