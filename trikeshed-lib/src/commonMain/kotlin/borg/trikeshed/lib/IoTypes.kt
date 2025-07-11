package borg.trikeshed.lib

import borg.trikeshed.lib.Indexed
 

class ByteIndexed(
    val buf: Indexed<Byte>,
    var pos: Int = 0,
    var limit: Int = buf.a,
    var mark: Int = -1
) : Indexed<Byte> by buf {
    val rem: Int get() = limit - pos
    val cap: Int by ::a
    val hasRemaining: Boolean get() = rem > 0
    inline val get: Byte get() { if (!hasRemaining) throw IndexOutOfBoundsException("pos: $pos, limit: $limit"); return buf.b(pos++) }
    fun pos(p: Int): ByteIndexed = apply { pos = p }
    fun lim(l: Int): ByteIndexed = apply { limit = l }
    val mk: ByteIndexed get() = apply { mark = pos }
    val res: ByteIndexed get() = apply { pos = if (mark < 0) pos else mark }
    fun flip(): ByteIndexed = apply { limit = pos; pos = 0; mark = -1 }
    val rew: ByteIndexed get() = apply { pos = 0 }
    val clr: ByteIndexed get() = apply { pos = 0; limit = a; mark = -1 }
    val slice: ByteIndexed get() { val pos1 = pos; val limit1 = limit; return ByteIndexed(buf, pos1, limit1) }
    val isEmpty: Boolean get() = pos == limit
    val trim: ByteIndexed get() = apply {
        var p = pos; var l = limit
        while (p < l && (0xff and buf.b(p).toInt()).toChar().isWhitespace()) p++
        while (l > p && (0xff and buf.b(l-1).toInt()).toChar().isWhitespace()) l--
        lim(l); pos(p)
    }
    val rtrim: ByteIndexed get() = apply { while (rem > 0 && buf.b(limit-1).toInt().toChar().isWhitespace()) limit-- }
    fun clone(): ByteIndexed = ByteIndexed(buf, pos, limit).also { it.mark = mark }
    val cacheCode: Int get() { var h = 1; for (i in pos until limit) { h = 31 * h + buf.b(i).hashCode() }; return h }
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ByteIndexed) return false
        if (pos != other.pos || limit != other.limit || mark != other.mark || a != other.a) return false
        for (i in 0 until a) if (buf.b(i) != other.buf.b(i)) return false
        return true
    }
    override fun hashCode(): Int {
        var result = pos; result = 31 * result + limit; result = 31 * result + mark; result = 31 * result + a; result = 31 * result + cacheCode; return result
    }
    fun asString(upto: Int = Int.MAX_VALUE): String = (pos until limit).map { buf.b(it).toInt().toChar() }.joinToString("").take(upto)
    override fun toString(): String { val take = asString().take(4); return "ByteIndexed(position=$pos, limit=$limit, mark=$mark, cacheCode=$cacheCode,take-4=${take})" }
    fun seekTo(target: Byte): Boolean { val anchor = pos; while (hasRemaining) { if (get == target) return true }; pos = anchor; return false }
    fun seekTo(target: Byte, escape: Byte): Boolean { val anchor = pos; var escaped = false; while (hasRemaining) get.let { c -> if (escaped) escaped = false else when (c) { target -> return true; escape -> escaped = true } }; pos = anchor; return false }
    fun seekTo(lit: Indexed<Byte>): Boolean { val anchor = pos; var i = 0; while (hasRemaining) { if (get == lit.b(i)) { i++; if (i == lit.a) return true } else { i = 0 } }; pos = anchor; return false }
    operator fun dec(): ByteIndexed = apply { require(pos > 0) { "Underflow" }; pos-- }
    operator fun inc(): ByteIndexed = apply { require(hasRemaining) { "Overflow" }; pos++ }
    fun toArray(): ByteArray = ByteArray(rem) { buf.b(pos + it) }
}

class CharIndexed(
    val buf: Indexed<Char>,
    var pos: Int = 0,
    var limit: Int = buf.a,
    var mark: Int = -1
) : Indexed<Char> by buf {
    val rem: Int get() = limit - pos
    val cap: Int by ::a
    val hasRemaining: Boolean get() = rem > 0
    inline val get: Char get() { if (!hasRemaining) throw IndexOutOfBoundsException("pos: $pos, limit: $limit"); return buf.b(pos++) }
    fun pos(p: Int): CharIndexed = apply { pos = p }
    fun lim(l: Int): CharIndexed = apply { limit = l }
    val mk: CharIndexed get() = apply { mark = pos }
    val res: CharIndexed get() = apply { pos = if (mark < 0) pos else mark }
    fun flip(): CharIndexed = apply { limit = pos; pos = 0; mark = -1 }
    val rew: CharIndexed get() = apply { pos = 0 }
    val clr: CharIndexed get() = apply { pos = 0; limit = a; mark = -1 }
    val slice: CharIndexed get() { val pos1 = pos; val limit1 = limit; return CharIndexed(buf, pos1, limit1) }
    val isEmpty: Boolean get() = pos == limit
    val trim: CharIndexed get() = apply {
        var p = pos; var l = limit
        while (p < l && buf.b(p).isWhitespace()) p++
        while (l > p && buf.b(l-1).isWhitespace()) l--
        lim(l); pos(p)
    }
    val rtrim: CharIndexed get() = apply { while (rem > 0 && buf.b(limit-1).isWhitespace()) limit-- }
    fun clone(): CharIndexed = CharIndexed(buf, pos, limit).also { it.mark = mark }
    val cacheCode: Int get() { var h = 1; for (i in pos until limit) { h = 31 * h + buf.b(i).hashCode() }; return h }
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CharIndexed) return false
        if (pos != other.pos || limit != other.limit || mark != other.mark || a != other.a) return false
        for (i in 0 until a) if (buf.b(i) != other.buf.b(i)) return false
        return true
    }
    override fun hashCode(): Int {
        var result = pos; result = 31 * result + limit; result = 31 * result + mark; result = 31 * result + a; result = 31 * result + cacheCode; return result
    }
    fun asString(upto: Int = Int.MAX_VALUE): String = (pos until limit).map { buf.b(it) }.joinToString("").take(upto)
    override fun toString(): String { val take = asString().take(4); return "CharIndexed(position=$pos, limit=$limit, mark=$mark, cacheCode=$cacheCode,take-4=${take})" }
    fun seekTo(target: Char): Boolean { val anchor = pos; while (hasRemaining) { if (get == target) return true }; pos = anchor; return false }
    fun seekTo(target: Char, escape: Char): Boolean { val anchor = pos; var escaped = false; while (hasRemaining) get.let { c -> if (escaped) escaped = false else when (c) { target -> return true; escape -> escaped = true } }; pos = anchor; return false }
    fun seekTo(lit: Indexed<Char>): Boolean { val anchor = pos; var i = 0; while (hasRemaining) { if (get == lit.b(i)) { i++; if (i == lit.a) return true } else { i = 0 } }; pos = anchor; return false }
    operator fun dec(): CharIndexed = apply { require(pos > 0) { "Underflow" }; pos-- }
    operator fun inc(): CharIndexed = apply { require(hasRemaining) { "Overflow" }; pos++ }
    fun toArray(): CharArray = CharArray(rem) { buf.b(pos + it) }
}

// ByteIndexedBuffer wrapper for ByteIndexed
class ByteIndexedBuffer(
    internal val buffer: ByteIndexed
) {
    constructor(bytes: ByteArray) : this(ByteIndexed(bytes.toIndexed()))
    constructor(input: String) : this(input.encodeToByteArray())
    constructor(indexed: Indexed<Byte>) : this(ByteIndexed(indexed))
    
    val pos: Int get() = buffer.pos
    val limit: Int get() = buffer.limit
    val mark: Int get() = buffer.mark

    val rem: Int get() = buffer.rem
    val hasRemaining: Boolean get() = buffer.hasRemaining
    val peek: Byte get() = if (buffer.hasRemaining) buffer.buf.b(buffer.pos) else -1
    
    val get: Byte get() {
        if (!buffer.hasRemaining) throw IndexOutOfBoundsException("pos: ${buffer.pos}, limit: ${buffer.limit}")
        return buffer.get
    }
    
    fun pos(newPos: Int) { buffer.pos(newPos) }
    
    // Whitespace operations
    val skipWs: ByteIndexedBuffer get() {
        buffer.trim
        return this
    }
    
    // Access to underlying buffer properties
    val a: Int get() = buffer.a
    val cap: Int get() = buffer.cap
    
    // Delegate other operations to the underlying ByteIndexed
    fun flip() = buffer.flip()
    fun clear() = buffer.clr
    fun rewind() = buffer.rew
    fun reset() = buffer.res
    fun mark() = buffer.mk
    fun slice() = buffer.slice
    
    // String conversion
    fun asString(upto: Int = Int.MAX_VALUE): String = buffer.asString(upto)
    fun toArray(): ByteArray = buffer.toArray()
}

// Extension functions for ByteIndexedBuffer
fun ByteArray.toByteIndexedBuffer(): ByteIndexedBuffer = ByteIndexedBuffer(this)
fun Indexed<Byte>.toByteIndexedBuffer(): ByteIndexedBuffer = ByteIndexedBuffer(this)
