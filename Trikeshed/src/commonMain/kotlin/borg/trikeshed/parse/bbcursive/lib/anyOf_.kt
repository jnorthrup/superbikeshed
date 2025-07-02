package borg.trikeshed.parse.bbcursive.lib

import borg.trikeshed.lib.ByteIndexedBuffer
import borg.trikeshed.lib.decodeUtf8
import borg.trikeshed.lib.toByteIndexedBuffer
import borg.trikeshed.parse.bbcursive.Cursive
import borg.trikeshed.parse.bbcursive.ParseResult
import borg.trikeshed.parse.bbcursive.SessionContext
import borg.trikeshed.parse.bbcursive.Traits
import borg.trikeshed.parse.bbcursive.UnaryOperator
import borg.trikeshed.parse.bbcursive.vtables._edge
import borg.trikeshed.parse.bbcursive.vtables._ptr
import kotlin.coroutines.coroutineContext
import kotlin.streams.asSequence

/**
 * Created by jim on 1/17/16.
 */
interface anyOf_ {
    companion object {
        val NONE_OF: Set<Traits> = emptySet() // Changed EnumSet.noneOf to emptySet()

        suspend fun anyOf(vararg anyOf: UnaryOperator<ByteIndexedBuffer>): UnaryOperator<ByteIndexedBuffer> {


            return object : UnaryOperator<ByteIndexedBuffer> {

                override suspend fun invoke(buffer: ByteIndexedBuffer): ByteIndexedBuffer? { // Added suspend
                    val sessionContext = coroutineContext[SessionContext.Key]
                        ?: throw IllegalStateException("SessionContext not found in CoroutineContext")

                    var mark = buffer.pos
                    if (sessionContext.flags.contains(Traits.SKIPPER)) {
                        val apply = Cursive.pre.skipWs.invoke(buffer) // Changed apply to invoke
                        val newBuffer = apply ?: buffer.pos(mark) // Reset position if apply is null
                        if (newBuffer == null || !newBuffer.hasRemaining) {
                            return null
                        }
                        buffer.pos(newBuffer.pos)
                    }
                    mark = buffer.pos
                    val offsets = intArrayOf(mark, mark)
                    val flaggs = arrayOf(NONE_OF)


                    val r = arrayOf<ByteIndexedBuffer?>(null)
                    val finalBuffer = arrayOf(buffer)

                    anyOf.asSequence() // Use asSequence for lazy evaluation
                        .map { op ->
                            object : _edge<Set<Traits>, _edge<UnaryOperator<ByteIndexedBuffer>, Int>>() { // Changed Integer to Int
                                private val currentBuffer = finalBuffer[0]

                                override fun at(): Int = r$()

                                override fun goTo(ptr: Int): Int {
                                    throw Error("trifling with an immutable pointer")
                                }

                                override fun r$(): Int {
                                    return currentBuffer?.pos ?: 0
                                }

                                override fun core(vararg e: _edge<Set<Traits>, _edge<UnaryOperator<ByteIndexedBuffer>, Int>>): _edge<UnaryOperator<ByteIndexedBuffer>, Int> { // Changed Integer to Int
                                    return object : _edge<UnaryOperator<ByteIndexedBuffer>, Int>() { // Changed Integer to Int
                                        override fun at(): Int = r$()

                                        override fun goTo(integer: Int): Int {
                                            throw Error("immutable")
                                        }

                                        override fun r$(): Int = offsets[1]

                                        override fun core(vararg e: _edge<UnaryOperator<ByteIndexedBuffer>, Int>): UnaryOperator<ByteIndexedBuffer> = op // Changed Integer to Int
                                    }
                                }
                            }
                        }
                        .filter { ed ->
                            val op = ed.core()?.core() // Access op from nested edge
                            val newPosition = ed.core()?.at() // Access newPosition from nested edge
                            val byteIndexedBuffer = finalBuffer[0]?.duplicate()?.pos(newPosition ?: 0) // Null-safe calls
                            val res = op?.invoke(byteIndexedBuffer!!) // Changed apply to invoke, added !! for non-null assertion

                            if (res != null) {
                                offsets[1] = res.pos
                                flaggs[0] = sessionContext.flags.toSet() // Copy current flags
                                true
                            } else {
                                false
                            }
                        }
                        .firstOrNull() // Use firstOrNull instead of findFirst
                        ?.let { edge_ptr_edge ->
                            sessionContext.outbox(
                                ParseResult(
                                    finalBuffer[0]!!,
                                    edge_ptr_edge.core()?.core()!!, // Access op from nested edge
                                    offsets[0],
                                    offsets[1],
                                    flaggs[0]
                                )
                            )
                            r[0] = finalBuffer[0]?.pos(offsets[1])
                        }

                    return r[0]
                }

                override fun toString(): String {
                    return "any" + anyOf.contentDeepToString()
                }
            }
        }


        fun anyIn(s: CharSequence): UnaryOperator<ByteIndexedBuffer> {
            val ints = s.chars().toArray()
            return object : UnaryOperator<ByteIndexedBuffer> {
                override fun invoke(b: ByteIndexedBuffer): ByteIndexedBuffer? {
                    var r: ByteIndexedBuffer? = null
                    if (b.hasRemaining) {
                        val b1 = b.get()
                        if (-1 < ints.binarySearch(b1.toInt())) // Use binarySearch on IntArray
                            r = b
                    }
                    return r
                }

                override fun toString(): String {
                    val sb = StringBuilder()
                    ints.forEach { i -> sb.append((i and 0xffff).toChar()) }
                    return "in" + Arrays.deepToString(arrayOf(sb.toString()))
                }
            }
        }
    }
}