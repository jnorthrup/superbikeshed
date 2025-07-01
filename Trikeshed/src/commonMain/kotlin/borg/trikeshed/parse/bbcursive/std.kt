package borg.trikeshed.parse.bbcursive

import borg.trikeshed.lib.ByteIndexedBuffer
import borg.trikeshed.lib.CharIndexedBuffer
import borg.trikeshed.lib.toByteIndexedBuffer
import borg.trikeshed.lib.decodeUtf8
import borg.trikeshed.lib.toCharIndexedBuffer
import borg.trikeshed.parse.bbcursive.SessionContext.Key
import kotlin.coroutines.coroutineContext
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation

// Assuming these annotations will be defined in Kotlin
import borg.trikeshed.parse.bbcursive.ann.Backtracking
import borg.trikeshed.parse.bbcursive.ann.ForwardOnly
import borg.trikeshed.parse.bbcursive.ann.Infix
import borg.trikeshed.parse.bbcursive.ann.Skipper

// Placeholder for WantsZeroCopy, assuming it will be adapted or replaced
interface WantsZeroCopy {
    fun asByteIndexedBuffer(): ByteIndexedBuffer
}

object std {

    private const val debug_bbcursive = true // Objects.equals("true", System.getenv("debug_bbcursive"));

    // Allocator will be re-evaluated later, for now, assume direct ByteIndexedBuffer creation
    // private var allocator: Allocator? = null

    /**
     * The main byteindexedbuffer io parser most easily coded for.
     */
    suspend fun bb(b: ByteIndexedBuffer, vararg ops: UnaryOperator<ByteIndexedBuffer>): ByteIndexedBuffer? {
        var r: ByteIndexedBuffer? = null
        var restoration: MutableSet<Traits>? = null
        var op: UnaryOperator<ByteIndexedBuffer>? = null

        val sessionContext = coroutineContext[Key]
            ?: throw IllegalStateException("SessionContext not found in CoroutineContext")

        if (ops.isNotEmpty() && ops[0] != null) {
            op = ops[0]
            if (debug_bbcursive) System.err.println("??? $op")
            val startPosition = b.pos

            if (sessionContext.flags.contains(Traits.SKIPPER)) {
                // Use ByteIndexedBuffer's skipWs property
                b.skipWs
            }
            restoration = induct(op::class)

            r = when (ops.size) {
                0 -> b
                1 -> op.invoke(b)
                else -> bb(bb(b, op), *ops.copyOfRange(1, ops.size))
            }

            if (r == null && sessionContext.flags.contains(Traits.BACKTRACKING)) {
                if (debug_bbcursive)
                    System.err.println("--- [${startPosition}, ${b.pos}] $op")
                r = b.pos(startPosition) // Reset position
            } else if (r != null) { // Only publish on success
                sessionContext.outbox(ParseResult(b, op, startPosition, b.pos, sessionContext.flags.toSet()))
            }
        }
        restoration?.let { sessionContext.flags.apply { clear(); addAll(it) } }
        return r
    }

    fun induct(aClass: KClass<out UnaryOperator<ByteIndexedBuffer>>): MutableSet<Traits> {
        val sessionContext = coroutineContext[Key]
            ?: throw IllegalStateException("SessionContext not found in CoroutineContext")

        val c = sessionContext.flags
        val traitses = c.toMutableSet() // Copy for restoration
        var dirty = false

        if (aClass.hasAnnotation<Skipper>()) {
            dirty = true
            c.add(Traits.SKIPPER)
        } else if (aClass.hasAnnotation<Infix>()) {
            dirty = true
            c.remove(Traits.SKIPPER)
        }
        if (aClass.hasAnnotation<Backtracking>()) {
            dirty = true
            c.add(Traits.BACKTRACKING)
        } else if (aClass.hasAnnotation<ForwardOnly>()) {
            dirty = true
            c.remove(Traits.BACKTRACKING)
        }
        return if (!dirty) mutableSetOf() else traitses
    }

    suspend fun <S : WantsZeroCopy> bb(b: S, vararg ops: UnaryOperator<ByteIndexedBuffer>): ByteIndexedBuffer? {
        var b1: ByteIndexedBuffer? = b.asByteIndexedBuffer()
        for (op in ops) {
            if (b1 == null) break
            b1 = op.invoke(b1)
        }
        return b1
    }

    // FastBuffer related methods might need to be re-evaluated or replaced with Kotlin-native solutions
    // For now, keeping them as placeholders or removing if not directly applicable
    /*
    public static <S extends WantsZeroCopy> ByteBufferReader fast(S zc) {
        return fast(zc.asByteBuffer());
    }

    public static ByteBufferReader fast(ByteBuffer buf) {
        ByteBufferReader r;
        try {
            if (buf.hasArray())
                r = new UnsafeHeapByteBufferReader(buf);
            else
                r = new UnsafeDirectByteBufferReader(buf);

        } catch (UnsupportedOperationException e) {
            r = new JavaByteBufferReader(buf);
        }
        return r;
    }
    */

    fun str(bytes: ByteIndexedBuffer, vararg operations: UnaryOperator<ByteIndexedBuffer>): String {
        val bb = bb(bytes, *operations)
        return bb?.decodeUtf8()?.asString() ?: "" // Handle null case
    }

    fun str(something: WantsZeroCopy, vararg atoms: UnaryOperator<ByteIndexedBuffer>): String {
        return str(something.asByteIndexedBuffer(), *atoms)
    }

    fun str(something: CharSequence, vararg atoms: UnaryOperator<ByteIndexedBuffer>): String {
        return str(something.toString().encodeToByteArray().toByteIndexedBuffer(), *atoms)
    }

    fun str(something: Any?): String {
        return something.toString()
    }

    fun <T : CharSequence> bb(src: T, vararg operations: UnaryOperator<ByteIndexedBuffer>): ByteIndexedBuffer? {
        return bb(src.toString().encodeToByteArray().toByteIndexedBuffer(), *operations)
    }

    fun grow(src: ByteIndexedBuffer): ByteIndexedBuffer {
        return src.grow() // Assuming a grow method exists in ByteIndexedBuffer
    }

    fun cat(byteIndexedBuffers: List<ByteIndexedBuffer>): ByteIndexedBuffer {
        return cat(*byteIndexedBuffers.toTypedArray())
    }

    fun cat(vararg src: ByteIndexedBuffer): ByteIndexedBuffer {
        var cursor: ByteIndexedBuffer
        var total = 0
        if (src.size <= 1) {
            cursor = src[0]
        } else {
            for (byteIndexedBuffer in src) {
                total += byteIndexedBuffer.rem
            }
            // Assuming alloc will be handled by ByteIndexedBuffer's internal mechanisms or a dedicated factory
            // For now, creating a new ByteIndexedBuffer from a new ByteArray
            cursor = ByteIndexedBuffer(ByteArray(total))
            for (byteIndexedBuffer in src) {
                cursor.put(byteIndexedBuffer) // Assuming put(ByteIndexedBuffer) exists
            }
            cursor.rew()
        }
        return cursor
    }

    // Allocator related methods will be re-evaluated
    /*
    public static ByteIndexedBuffer alloc(int size) {
        return null != getAllocator() ? getAllocator().allocate(size).toByteIndexedBuffer() : new ByteIndexedBuffer(ByteBuffer.allocateDirect(size));
    }

    public static ByteBufferReader alloca(int size) {
        return fast(alloc(size).asByteBuffer());
    }

    public static Allocator getAllocator() {
        return allocator;
    }

    public static void setAllocator(Allocator allocator) {
        std.allocator = allocator;
    }
    */

    fun consumeString(buffer: ByteIndexedBuffer): ByteIndexedBuffer? {
        //TODO unicode wat?
        while (buffer.hasRemaining) {
            val current = buffer.get()
            when (current.toInt().toChar()) {
                '"' -> return buffer
                '\' -> {
                    val next = buffer.get()
                    when (next.toInt().toChar()) {
                        'u' -> buffer.pos(buffer.pos + 4)
                        else -> {}
                    }
                }
            }
        }
        return buffer
    }

    fun consumeNumber(slice: ByteIndexedBuffer): ByteIndexedBuffer? {
        val b = slice.mk.get

        val sign = '-' == b.toInt().toChar() || '+' == b.toInt().toChar()
        if (!sign) {
            slice.rew()
        }

        var dot = false
        var etoken = false
        var esign = false
        var r: ByteIndexedBuffer? = null
        while (slice.hasRemaining) {
            while (slice.hasRemaining && Character.isDigit(slice.mk.get.toInt().toChar())) {
                // consume digit
            }
            when (b.toInt().toChar()) {
                '.' -> {
                    assert(!dot) { "extra dot" }
                    dot = true
                }
                'E', 'e' -> {
                    assert(!etoken) { "missing digits or redundant exponent" }
                    etoken = true
                }
                '+', '-' -> {
                    assert(!esign) { "bad exponent sign" }
                    esign = true
                }
                else -> {
                    if (!Character.isDigit(b.toInt().toChar())) r = slice.rew()
                }
            }
        }
        return r
    }
}

infix fun ByteIndexedBuffer.invoke(op: UnaryOperator<ByteIndexedBuffer>): ByteIndexedBuffer = op(this)
