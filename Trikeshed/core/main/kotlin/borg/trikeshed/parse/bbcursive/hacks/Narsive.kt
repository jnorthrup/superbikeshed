package borg.trikeshed.parse.bbcursive.hacks

import borg.trikeshed.parse.bbcursive.Cursive
import borg.trikeshed.parse.bbcursive.Cursive.pre.mark
import borg.trikeshed.parse.bbcursive.Cursive.pre.noop
import borg.trikeshed.parse.bbcursive.Cursive.pre.skipWs
import borg.trikeshed.parse.bbcursive.std
import borg.trikeshed.parse.bbcursive.std.bb
import borg.trikeshed.parse.bbcursive.lib.opt_.opt
import borg.trikeshed.parse.bbcursive.lib.allOf_.allOf
import borg.trikeshed.parse.bbcursive.lib.confix_.confixChars
import borg.trikeshed.parse.bbcursive.lib.pos.pos

import java.nio.ByteBuffer
import java.nio.IntBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.util.ArrayList
import java.util.EnumSet.of
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.UnaryOperator

enum class Narsive : Cursive {
    task {
        override fun apply(byteBuffer: ByteBuffer): ByteBuffer? {
            return bb(byteBuffer, opt(budget), sentence)
        }
    },
    sentence() {


        override fun apply(buffer: ByteBuffer): ByteBuffer? {
            return bb(buffer, allOf(judgement, goal, question, desire))
        }
    },
    judgement {
        override fun apply(byteBuffer: ByteBuffer): ByteBuffer? {
            return bb(byteBuffer, statement, dot, opt(tense), opt(truth))
        }
    },
    goal {
        override fun apply(byteBuffer: ByteBuffer): ByteBuffer? {
            return bb(byteBuffer, exclamation, opt(truth))
        }
    },
    desire{
        override fun apply(byteBuffer: ByteBuffer): ByteBuffer? {
            return bb(byteBuffer, amp, opt(tense))
        }
    },
    question {
        override fun apply(byteBuffer: ByteBuffer): ByteBuffer? {
            return bb(byteBuffer, questionMark, opt(tense))
        }
    },

    listEnd {
        override fun apply(byteBuffer: ByteBuffer): ByteBuffer? {
            return null//todo: never
        }
    },
    relationship {
        override fun apply(byteBuffer: ByteBuffer): ByteBuffer? {
            return bb(byteBuffer, lt, term, copula, term, gt)
        }
    },
    statement {
        override fun apply(buffer: ByteBuffer): ByteBuffer? {
            return terminatingOr(buffer, of(relationship, operation, term))
        }
    }, tense {
        override fun apply(buffer: ByteBuffer): ByteBuffer? {
            return bb(buffer, allOf(Tense.values()))
        }

    },
    truth {
        override fun apply(byteBuffer: ByteBuffer): ByteBuffer? {
            return bb(byteBuffer, percent, frequency, percent, frequency, opt(semicolon, confidence), percent)
        }
    },
    budget {
        override fun apply(byteBuffer: ByteBuffer): ByteBuffer? {
            return bb(byteBuffer, dollarSign, priority, opt(semicolon, durability), dollarSign)
        }
    },
    copula {
        override fun apply(buffer: ByteBuffer): ByteBuffer? {
            return bb(buffer, allOf(Copula.values()))
        }
    }, term {
        override fun apply(buffer: ByteBuffer): ByteBuffer? {

            return bb(buffer, allOf(word, variable, compoundTerm, statement))
        }
    },

    operation {
        override fun fun apply(buffer: ByteBuffer): ByteBuffer? {
            return bb(buffer, ListParser("(^", word, term, ")"))
        }
    },

    variable {
        override fun apply(buffer: ByteBuffer): ByteBuffer? {
            return bb(buffer, allOf(NarVar.values()))
        }
    }, compoundTerm {
        override fun apply(buffer: ByteBuffer): ByteBuffer? {
            return bb(buffer, ListParser("(", conjunction, term, ")"))

        }
    }, conjunction {
        override fun apply(buffer: ByteBuffer): ByteBuffer? {
            return bb(buffer, allOf(Conjunction.values()))
        }
    },
    frequency{
        override fun apply(byteBuffer: ByteBuffer): ByteBuffer? {
            return bb(byteBuffer, numeric)
        }
    }, confidence {
        override fun apply(byteBuffer: ByteBuffer): ByteBuffer? {
            return bb(byteBuffer, numeric)
        }
    }, priority {
        override fun apply(byteBuffer: ByteBuffer): ByteBuffer? {
            return bb(byteBuffer, numeric)
        }
    }, durability {
        override fun apply(byteBuffer: ByteBuffer): ByteBuffer? {
            return bb(byteBuffer, numeric)
        }
    },
    quotedString {
        override fun apply(buffer: ByteBuffer): ByteBuffer? {
            bb(buffer, quot, UnaryOperator { b ->
                std.consumeString(b)
                b
            })
            return buffer
        }
    },
    numeric{
        override fun apply(byteBuffer: ByteBuffer): ByteBuffer? {
            val b = (byteBuffer.mark() as ByteBuffer).get()

            val sign = b == '-'.code.toByte() || b == '+'.code.toByte()
            if (!sign) byteBuffer.reset()

            var dot1 = false
            var etoken = false
            var esign = false
            while (byteBuffer.hasRemaining()) {
                var c=0
                while (byteBuffer.hasRemaining() && Character.isDigit(byteBuffer.mark().get().toInt())) c++

                when (byteBuffer.get().toInt()) {
                    '.'.code -> {
                        assert(!dot1) { "extra dot" }
                        dot1 = true
                    }
                    'E'.code, 'e'.code -> {
                        assert(!etoken) { "missing digits or redundant exponent" }
                        etoken = true
                    }
                    '+'.code, '-'.code -> {
                        assert(!esign) { "bad exponent sign" }
                        esign = true
                    }
                    else -> {
                        if (!Character.isDigit(b.toInt()))
                            return if (c > 0) bb(byteBuffer, if (byteBuffer.hasRemaining()) Cursive.pre.back1 else noop) else null
                    }
                }
            }
            return null
        }
    },
    word {
        override fun apply(buffer: ByteBuffer): ByteBuffer? {
            return bb(buffer, allOf(UnaryOperator { buf ->
                var c = 0
                while (buf.hasRemaining() && isValidAtomChar(buf.get().toInt())) c++
                if (c > 0) bb(buf, Cursive.pre.back1) else null
            }, quotedString))
        }
    },
    ;

    private fun anyOf(vararg anyOf: Cursive): Cursive {

        return Cursive { b ->
            for (o in anyOf) {
                val bb = bb(b, o)
                if (null != bb) {
                    return@Cursive bb
                }
            }

            null
        }
    }

    private fun zeroOrMore(sep: Cursive, listNode: Narsive, integers: ArrayList<Int>): Cursive {

        return Cursive { buf ->
            val rollback = buf.position()
            var c = 0

            while (null != bb(buf, sep)) {
                val position = buf.position()
                if (null == bb(buf, listNode)) return@Cursive bb(buf, pos(rollback), null)

                integers.add(position)
                c++
            }

            if (c > 0) buf else bb(buf, pos(rollback), null)
        }

    }

    fun confix(begin: String, clause: Cursive, end: String, contentIndex: AtomicInteger): Cursive {
        return Cursive { buf ->
            val position = bb(buf, skipWs, mark)!!.position()
            val gotBegin = genericAdvance(begin.toByteArray())
            if (null != gotBegin) {
                contentIndex.set(buf.position())
                if (null != bb(buf, clause, skipWs)) {
                    val byteBuffer = genericAdvance(end.toByteArray())
                    if (null != byteBuffer) buf else null
                } else null
            } else null
        }

    }


    val MASK24BITS = 0xffffff


    fun recordFeature(position: Int) {
        features.put(ordinal * (1 shl 24) or (position and 0xffffff))
    }


    var features: IntBuffer = IntBuffer.allocate(1000) //8/24 bit flags/offsets


    fun terminatingOr(buffer: ByteBuffer, judgement: Iterable<Cursive>): ByteBuffer? {
        val markedBuffer = bb(buffer, skipWs, mark)
        val position = markedBuffer!!.position()
        for (cursive in judgement) {
            val bb = bb(markedBuffer, Cursive.pre.reset, cursive)
            if (null != bb) {
                recordFeature(position)
                listEnd.recordFeature(bb.position())
                return bb
            }
        }
        return null
    }

    private class Constants {
        companion object {
            var compundListParser: ListParser? = null
        }
    }


    private inner class ListParser(private val begin: String, private val firstFeature: Narsive, private val listNode: Narsive, private val end: String) : Cursive {


        override fun apply(buffer: ByteBuffer): ByteBuffer? {
            val middlePosition = AtomicInteger()
            val integers = ArrayList<Int>()
            val rollback = buffer.position()
            if (null != bb(buffer, confixChars(UnaryOperator { buf -> bb(buf, firstFeature, zeroOrMore(comma, listNode, integers)) }, *begin.toCharArray()), *end.toCharArray())) {
                recordFeature(rollback)
                firstFeature.recordFeature(middlePosition.get())
                for (integer in integers) listNode.recordFeature(integer)
                listEnd.recordFeature(buffer.position())
                return buffer
            }
            return std.bb(buffer, pos(rollback), null)
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val input = "(a --> b)"
            val encode = UTF_8.encode(input)


        }

        fun opt(vararg allOrPrevious: Cursive): Cursive {
            return Cursive { byteBuffer ->

                val bb = bb(byteBuffer, *allOrPrevious)
                if (null == bb)
                    byteBuffer
                else bb
            }
        }

        fun bb(b: ByteBuffer, vararg allOrNull: Cursive): ByteBuffer? {
            val position = b.position()

            val bb = std.bb(
                b, *allOrNull
            )
            return bb ?: std.bb(b, pos(position), null)
        }
    }
}
