package borg.trikeshed.parse.bbcursive

import borg.trikeshed.parse.bbcursive.ann.Backtracking
import borg.trikeshed.parse.bbcursive.ann.ForwardOnly
import borg.trikeshed.parse.bbcursive.ann.Infix
import borg.trikeshed.parse.bbcursive.ann.Skipper
import borg.trikeshed.parse.bbcursive.lib.u8tf
import borg.trikeshed.parse.bbcursive.vtables._edge
import borg.trikeshed.parse.bbcursive.vtables._ptr
import org.jetbrains.annotations.NotNull

import java.nio.ByteBuffer
import java.util.List
import java.util.Map
import java.util.Set
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.function.BiFunction
import java.util.function.Consumer
import java.util.function.Supplier
import java.util.function.UnaryOperator

import java.lang.Character.isDigit
import java.lang.Character.isWhitespace
import java.nio.ByteBuffer.allocateDirect
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Arrays.copyOfRange
import java.util.Arrays.deepToString
import java.util.EnumSet.copyOf
import java.util.EnumSet.noneOf


/**
 * Created by jim on 8/8/14.
 */
object std {


    private val debug_bbcursive = true// Objects.equals("true", System.getenv("debug_bbcursive"));
    private var allocator: Allocator? = null

    /**
     * the outbox -- when a parse term successfully returns and a {@link Consumer}is installed as the outbox the
     * following state is published allowing for a recreation of the event elsewhere within the jvm
     * <p>
     * in reverse order of resolution:
     * <p>
     * flags -- from annotations from lambda class
     * UnaryOperator -- the lambda that fired,
     * Integer -- length, to save time moving and scoring the artifact
     * _ptr -- _edge[ByteBuffer,Integer] state pair
     */
    val outbox: ThreadLocal<Consumer<_edge<_edge<Set<traits>,
            _edge<UnaryOperator<ByteBuffer>, Int>>, _ptr>>> =
        ThreadLocal.withInitial(Supplier { Consumer { edge_ptr_edge ->
            // exhaust core()+location() fanout in intellij for a representational constant
            // automate later.
            val edge_ptr_edge1 = edge_ptr_edge
            val location = edge_ptr_edge1.location()
            val startPosition = location.location()
            val set_edge_edge = edge_ptr_edge1.core()
            val traitsSet = set_edge_edge!!.core()
            val operatorIntegerEdge = set_edge_edge.location()
            val endPosition = operatorIntegerEdge!!.location()
            val unaryOperator = operatorIntegerEdge.core()
            val s = deepToString(arrayOf(startPosition, endPosition))
            System.err.println("+++ " + s + unaryOperator + " " + traitsSet)

        } })

    /**
     * when you want to change the behaviors of the main IO parser, insert a new {@link BiFunction} to intercept
     * parameters and returns to fire events and clean up using {@link ThreadLocal#set(Object)}
     */
    enum class traits {
        debug, backtracking, skipper;

    }


    val flags: ThreadLocal<Set<traits>> = ThreadLocal.withInitial(Supplier { noneOf(traits::class.java) })


    /**
     * this is the main bytebuffer io parser most easily coded for.
     *
     * @param b   the bytebuffer
     * @param ops
     * @return
     */
    fun bb(b: ByteBuffer?, vararg ops: UnaryOperator<ByteBuffer>): ByteBuffer? {
        var r: ByteBuffer? = null
        var restoration: Set<traits>? = null
        var op: UnaryOperator<ByteBuffer>? = null
        if (null != b && 0 < ops.size && null != ops[0].also { op = it }) {
            ;
            if (debug_bbcursive) System.err.println("??? " + op)
            val startPosition = b.position()

            if (flags.get().contains(traits.skipper)) {
                var rem:
                Boolean
                while (run { rem = b.hasRemaining(); rem } && isWhitespace((b.mark() as ByteBuffer).get().toInt()));
                if (rem) b.reset()
            }
            restoration = induct(op!!::class.java)
            when (ops.size) {
                0 -> r = b
                1 -> r = op!!.apply(b)

/*save
                case 2:
                    r = bb(bb(b, op), ops[1]);
                    break;
                case 3:
                    r = bb(bb(bb(b, op), ops[1]), ops[2]);
                    break;
                case 4:
                    r = bb(bb(bb(bb(b, op), ops[1]), ops[2]), ops[3]);
                    break;
                case 5:
                    r = bb(bb(bb(bb(bb(b, op), ops[1]), ops[2]), ops[3]), ops[4]);
                    break;
                case 6:
                    r = bb(bb(bb(bb(bb(bb(b, op), ops[1]), ops[2]), ops[3]), ops[4]), ops[5]);
                    break;
*/

                else -> r = bb(bb(b, op!!), *copyOfRange(ops, 1, ops.size))
            }

            if (null == r && flags.get().contains(traits.backtracking)) {
                if (debug_bbcursive)
                    System.err.println("--- " + deepToString(arrayOf(startPosition, b.position())) + " " + op.toString())
                r = b.position(startPosition)

            } else if (null != outbox.get()) {
                onSuccess(b, op!!, startPosition)
            }

        }
        if (restoration != null)
            flags.set(restoration)
        return r
    }

    fun onSuccess(b: ByteBuffer, byteBufferUnaryOperator: UnaryOperator<ByteBuffer>, startPosition: Int) {
        val endPos = b.position()
        val immutableTraits = copyOf(flags.get())

        /**
         * creates a slice.  probably a bad idea due to array() b000gz
         */
        std.outbox.get().accept(createSuccessTuple(b, byteBufferUnaryOperator, startPosition, endPos, immutableTraits))
    }

    @NotNull
    fun createSuccessTuple(b: ByteBuffer, byteBufferUnaryOperator: UnaryOperator<ByteBuffer>, startPosition: Int, endPos: Int, immutableTraits: Set<traits>): _edge<_edge<Set<traits>, _edge<UnaryOperator<ByteBuffer>, Int>>, _ptr> {
        return object : _edge<_edge<Set<traits>, _edge<UnaryOperator<ByteBuffer>, Int>>, _ptr>() {
            override fun at(): _ptr {
                return r$()
            }

            override fun goTo(ptr: _ptr): _ptr {
                throw Error("trifling with an immutable pointer")
            }

            /**
             * this binds a pointer to a pair of ByteBuffer and Integer.  note the bytebuffer is mutated by this
             * operation and will corrupt the source stream if this isn't a slice or a duplicate
             *
             *
             * @return the _ptr
             */
            override

            fun r$(): _ptr {

                return _ptr().bind(
                    b.duplicate().limit(endPos) as ByteBuffer, startPosition) as _ptr
            }

            override fun core(vararg e: _edge<_edge<Set<traits>, _edge<UnaryOperator<ByteBuffer>, Int>>, _ptr>): _edge<Set<traits>, _edge<UnaryOperator<ByteBuffer>, Int>>? {
                return object : _edge<Set<traits>, _edge<UnaryOperator<ByteBuffer>, Int>>() {
                    override fun core(vararg e: _edge<Set<traits>, _edge<UnaryOperator<ByteBuffer>, Int>>): Set<traits> {
                        return immutableTraits
                    }

                    override fun at(): _edge<UnaryOperator<ByteBuffer>, Int> {
                        return r$()
                    }

                    override fun goTo(unaryOperatorInteger_edge: _edge<UnaryOperator<ByteBuffer>, Int>): _edge<UnaryOperator<ByteBuffer>, Int> {
                        throw Error("cant move this")
                    }

                    override fun r$(): _edge<UnaryOperator<ByteBuffer>, Int> {
                        return object : _edge<UnaryOperator<ByteBuffer>, Int>() {
                            override fun at(): Int {
                                return r$()
                            }

                            override fun goTo(integer: Int): Int {
                                throw Error("immutable")
                            }

                            override fun core(vararg e: _edge<UnaryOperator<ByteBuffer>, Int>): UnaryOperator<ByteBuffer> {
                                return byteBufferUnaryOperator
                            }

                            override fun r$(): Int {
                                return endPos
                            }
                        }
                    }
                }
            }
        }
    }


    var termCache: Map<Class<*>, Set<traits>> = WeakHashMap()

    /**
     * cache terminal flags and use them by class.
     * <p>
     * if class is gc'd, no leak.
     *
     * @param aClass
     * @return the previous (restoration) state
     */
    fun induct(aClass: Class<out UnaryOperator<*>>): Set<traits>? {
        val c = flags.get()
        val traitses = copyOf(c)
        val dirty = AtomicBoolean(false)
        if (aClass.isAnnotationPresent(Skipper::class.java)) {
            dirty.set(true)
            c.add(traits.skipper)
        } else if (aClass.isAnnotationPresent(Infix::class.java)) {
            dirty.set(true)
            c.remove(traits.skipper)
        }
        if (aClass.isAnnotationPresent(Backtracking::class.java)) {
            dirty.set(true)
            c.add(traits.backtracking)
        } else if (aClass.isAnnotationPresent(ForwardOnly::class.java)) {
            dirty.set(true)
            c.remove(traits.backtracking)
        }
        return if (!dirty.get()) null else traitses
    }


    fun <S> bb(b: S, vararg ops: UnaryOperator<ByteBuffer>): ByteBuffer? where S : WantsZeroCopy {
        var b1 = b.asByteBuffer()
        for (op in ops) {
            if (null == op) {
                b1 = null
                break
            }
            b1 = op.apply(b1)
        }
        return b1
    }

    // Removed fastbuffer imports and related methods as they are not available

    /**
     * convenience method
     *
     * @param bytes
     * @param operations
     * @return
     */
    fun str(bytes: ByteBuffer, vararg operations: UnaryOperator<ByteBuffer>): String {
        val bb = bb(bytes, *operations)
        return UTF_8.decode(bb).toString()
    }

    /**
     * just saves a few chars
     *
     * @param something toString will run on this
     * @param atoms
     * @return
     */
    fun str(something: WantsZeroCopy, vararg atoms: UnaryOperator<ByteBuffer>): String {
        return str(something.asByteBuffer(), *atoms)
    }

    /**
     * just saves a few chars
     *
     * @param something toString will run on this
     * @param atoms
     * @return
     */
    fun str(something: AtomicReference<out WantsZeroCopy>, vararg atoms: UnaryOperator<ByteBuffer>): String {
        return str(something.get(), *atoms)
    }

    /**
     * just saves a few chars
     *
     * @param something toString will run on this
     * @return
     */
    fun str(something: Any): String {
        return something.toString()
    }

    /**
     * convenience method
     *
     * @param src
     * @param operations
     * @return
     */
    fun <T> bb(src: T, vararg operations: UnaryOperator<ByteBuffer>): ByteBuffer? where T : CharSequence {
        return bb(u8tf.c2b(src.toString()), *operations)
    }

    fun grow(src: ByteBuffer): ByteBuffer {
        return allocateDirect(src.capacity() shl 1).put(src)
    }

    fun cat(byteBuffers: List<ByteBuffer>): ByteBuffer {
        val byteBuffers1 = byteBuffers.toTypedArray()
        return cat(*byteBuffers1)
    }

    fun cat(vararg src: ByteBuffer): ByteBuffer {
        var cursor: ByteBuffer
        var total = 0
        if (1 >= src.size) {
            cursor = src[0]
        } else {
            for (byteBuffer in src) {
                total += byteBuffer.remaining()
            }
            cursor.put(byteBuffer)
            cursor.rewind()
        }
        return cursor
    }

    fun alloc(size: Int): ByteBuffer {
        return if (null != getAllocator()) getAllocator()!!.allocate(size) else allocateDirect(size)
    }

    // Removed alloca as it depends on fastbuffer

    fun consumeString(buffer: ByteBuffer): ByteBuffer {
        //TODO unicode wat?
        while (buffer.hasRemaining()) {
            val current = buffer.get()
            when (current.toInt()) {
                '"'.toInt() -> return buffer
                '\''.toInt() -> {
                    val next = buffer.get()
                    when (next.toInt()) {
                        'u'.toInt() -> {
                            buffer.position(buffer.position() + 4)
                        }
                        else -> {}
                    }
                }
            }
        }
        return buffer
    }

    fun consumeNumber(slice: ByteBuffer): ByteBuffer? {
        val b = (slice.mark() as ByteBuffer).get()

        val sign = '-'.toInt() == b.toInt() || '+'.toInt() == b.toInt()
        if (!sign) {
            slice.reset()
        }

        var dot = false
        var etoken = false
        var esign = false
        var r: ByteBuffer? = null
        while (slice.hasRemaining()) {
            while (slice.hasRemaining() && isDigit((slice.mark() as ByteBuffer).get().toInt())) {
            }
            when ((slice.mark() as ByteBuffer).get().toInt()) {
                '.'.toInt() -> {
                    assert(!dot) { "extra dot" }
                    dot = true
                }
                'E'.toInt(), 'e'.toInt() -> {
                    assert(!etoken) { "missing digits or redundant exponent" }
                    etoken = true
                }
                '+'.toInt(), '-'.toInt() -> {
                    assert(!esign) { "bad exponent sign" }
                    esign = true
                }
                else -> {
                    if (!isDigit((slice.mark() as ByteBuffer).get().toInt())) r = slice.reset() as ByteBuffer
                }
            }
        }
        return r
    }

    fun getAllocator(): Allocator? {
        return allocator
    }

    fun setAllocator(allocator: Allocator) {
        std.allocator = allocator
    }


}

interface WantsZeroCopy {
    fun asByteBuffer(): ByteBuffer
}
