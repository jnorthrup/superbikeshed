package borg.trikeshed.lib

import borg.trikeshed.core.Series
import borg.trikeshed.core.j
import borg.trikeshed.core.toSeries
import borg.trikeshed.core.α
import borg.trikeshed.lib.CZero.nz

private typealias ByteSeriesData = Series<Byte>
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

    val skipWs: ByteSeries get() = apply { while (hasRemaining && mk.get.toInt().toChar().isWhitespace()); res }
    val rtrim: ByteSeries get() = apply { while (rem > 0 && get(limit - 1).toInt().toChar().isWhitespace()) limit-- }

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
        var i = 0
        val litSize = lit.a
        val litGetter = lit.b
        while (hasRemaining) {
            if (get == litGetter(i)) {
                i++
                if (i == litSize) return true
            } else {
                pos -= i
                i = 0
                if (hasRemaining && get == litGetter(i)) {
                     i++
                     if (i == litSize) return true
                }
            }
        }
        if (i == litSize) return true
        pos = anchor
        return false
    }

    operator fun dec(): ByteSeries = apply { require(pos > 0) { "Underflow" }; pos-- }
    operator fun inc(): ByteSeries = apply { require(hasRemaining) { "Overflow" };pos++ }

    fun toArray(): ByteArray = ByteArray(rem) { this[pos + it] }
}

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
