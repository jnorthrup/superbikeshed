package borg.trikeshed.parse.bbcursive.lib

import borg.trikeshed.parse.bbcursive.Cursive.pre
import borg.trikeshed.parse.bbcursive.ann.Backtracking
import borg.trikeshed.parse.bbcursive.vtables._edge
import borg.trikeshed.parse.bbcursive.vtables._ptr

import java.nio.ByteBuffer
import java.util.Arrays
import java.util.EnumSet
import java.util.Set
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.UnaryOperator
import java.util.stream.IntStream

import borg.trikeshed.parse.bbcursive.std.*
import java.util.Arrays.binarySearch
import java.util.Arrays.deepToString

/**
 * Created by jim on 1/17/16.
 */
object anyOf_ {

    val NONE_OF: EnumSet<std.traits> = EnumSet.noneOf(std.traits::class.java)

    fun anyOf(vararg anyOf: UnaryOperator<ByteBuffer>): UnaryOperator<ByteBuffer> {


        return object : UnaryOperator<ByteBuffer> {

            override fun toString(): String {
                return "any" + deepToString(anyOf)
            }

            override fun apply(buffer: ByteBuffer): ByteBuffer? {
                var mark = buffer.position()
                if (flags.get().contains(std.traits.skipper)) {
                    val apply = pre.skipWs.apply(buffer)
                    buffer = apply ?: buffer.position(mark) as ByteBuffer
                    if (!buffer.hasRemaining()) {
                        return null
                    }
                }
                mark = buffer.position()
                val offsets = intArrayOf(mark, mark)
                val flaggs = arrayOf(NONE_OF)


                val r = arrayOf<ByteBuffer?>(null)
                val finalBuffer = arrayOf(buffer)

                Arrays.stream(anyOf)/*.parallel()*/
                    .map(Function<UnaryOperator<ByteBuffer>, _edge<_edge<Set<std.traits>,
                            _edge<UnaryOperator<ByteBuffer>, Int>>, _ptr>> {
                        op ->
                        object : _edge<_edge<Set<std.traits>, _edge<UnaryOperator<ByteBuffer>, Int>>, _ptr>() {
                            private val buffer: ByteBuffer = finalBuffer[0]

                            override fun at(): _ptr {
                                return r$()
                            }

                            override fun goTo(ptr: _ptr): _ptr {
                                throw Error("trifling with an immutable pointer")
                            }

                            /**
                             * this binds a pointer to a pair of ByteBuffer and Integer.  note the bytebuffer
                             * is mutated by this operation and will corrupt the source stream if this isn't
                             * a slice or a duplicate
                             *
                             * @return the _ptr
                             */
                            override

                            fun r$(): _ptr {

                                return _ptr().bind(
                                    buffer.duplicate().position(offsets[1]) as ByteBuffer, offsets[0]) as _ptr
                            }

                            override fun core(vararg e: _edge<_edge<Set<std.traits>, _edge<UnaryOperator<ByteBuffer>, Int>>, _ptr>): _edge<Set<std.traits>, _edge<UnaryOperator<ByteBuffer>, Int>>? {
                                return object : _edge<Set<std.traits>, _edge<UnaryOperator<ByteBuffer>, Int>>() {
                                    override fun core(vararg e: _edge<Set<std.traits>, _edge<UnaryOperator<ByteBuffer>, Int>>): Set<std.traits> {
                                        return flaggs[0]
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
                                                return op
                                            }

                                            override fun r$(): Int {
                                                return offsets[1]
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }).filter(
                        { ed ->
                            val op = ed.core()!!.location()!!.core()
                            val newPosition = ed.location()!!.location()
                            val byteBuffer = ed.location()!!.core()!!.duplicate().position(newPosition) as ByteBuffer
                            val res = op.apply(byteBuffer)
                            if (null != res) {
                                offsets[1] = res.position()
                                flaggs[0] = EnumSet.copyOf(flags.get())
                                true
                            } else false
                        })
                    .findFirst().ifPresent(
                        { edge_ptr_edge ->
                            val edgeConsumer = outbox.get()
                            edgeConsumer.accept(edge_ptr_edge)
                            r[0] = finalBuffer[0].position(offsets[1]) as ByteBuffer
                        })

                return r[0]
            }
        }
    }


    @Backtracking
    fun anyIn(s: CharSequence): UnaryOperator<ByteBuffer> {
        val ints = s.chars().sorted().toArray()
        return object : UnaryOperator<ByteBuffer> {
            override fun toString(): String {
                val b = StringBuilder()
                IntStream.of(*ints).forEach { i -> b.append(i.toChar()) }
                return "in" + Arrays.deepToString(arrayOf(b.toString()))
            }

            override fun apply(b: ByteBuffer): ByteBuffer? {
                var r: ByteBuffer? = null
                if (null != b && b.hasRemaining()) {
                    val b1 = b.get()
                    if (-1 < binarySearch(ints, b1.toInt() and 0xff))
                        r = b
                }
                return r
            }
        }
    }
}
