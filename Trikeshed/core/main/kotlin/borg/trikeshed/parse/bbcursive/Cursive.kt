package borg.trikeshed.parse.bbcursive

import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.util.function.UnaryOperator

import borg.trikeshed.parse.bbcursive.std.bb

/**
 * some kind of less painful way to do byteBuffer operations and a few new ones thrown in.
 * <p/>
 * evidence that this can be more terse than what jdk pre-8 allows:
 * <pre>
 *
 * res.add(bb(nextChunk, rewind));
 * res.add((ByteBuffer) nextChunk.rewind());
 *
 *
 * </pre>
 */
@FunctionalInterface
interface Cursive : UnaryOperator<ByteBuffer> {
    enum class pre : UnaryOperator<ByteBuffer> {
        duplicate {

            override fun apply(target: ByteBuffer): ByteBuffer {
                return target.duplicate()
            }
        }, flip {

            override fun apply(target: ByteBuffer): ByteBuffer {
                return target.flip() as ByteBuffer
            }
        }, slice {

            override fun apply(target: ByteBuffer): ByteBuffer {
                return target.slice()
            }
        }, mark {

            override fun apply(target: ByteBuffer): ByteBuffer {
                return target.mark() as ByteBuffer
            }
        }, reset {

            override fun apply(target: ByteBuffer): ByteBuffer {
                return target.reset() as ByteBuffer
            },
        },
        /**
         * exists in both pre and post Cursive atoms.
         */
        rewind {

            override fun apply(target: ByteBuffer): ByteBuffer {
                return target.rewind() as ByteBuffer
            }
        },
        /**
         * rewinds, dumps to console but returns unchanged buffer
         */
        debug {

            override fun apply(target: ByteBuffer): ByteBuffer {
                System.err.println("%%: " + std.str(target, duplicate, rewind))
                return target
            }
        }, ro {

            override fun apply(target: ByteBuffer): ByteBuffer {
                return target.asReadOnlyBuffer()
            }
        },

        /**
         * perfoms get until non-ws returned.  then backtracks.by one.
         * <p/>
         * <p/>
         * resets position and throws BufferUnderFlow if runs out of space before success
         */


        forceSkipWs {
            override fun apply(target: ByteBuffer): ByteBuffer? {
                val position = target.position()

                while (target.hasRemaining() && Character.isWhitespace(target.get().toInt()));
                if (!target.hasRemaining()) {
                    target.position(position)
                    throw BufferUnderflowException()
                }
                return bb(target, back1)
            }
        },
        skipWs {
            override fun apply(target: ByteBuffer): ByteBuffer? {
                var rem: Boolean
                var captured = false
                var r: Boolean
                while (run {
                            rem = target.hasRemaining()
                            rem && run {
                                r = Character.isWhitespace(0xff and (target.mark() as ByteBuffer).get().toInt())
                                captured = captured or r
                                r
                            }
                        });
                return if (captured && rem) target.reset() as ByteBuffer else if (captured) target else null
            }
        },
        toWs {

            override fun apply(target: ByteBuffer): ByteBuffer {
                while (target.hasRemaining() && !Character.isWhitespace(target.get().toInt())) {
                }
                return target
            }
        },
        /**
         * @throws java.nio.BufferUnderflowException if EOL was not reached
         */
        forceToEol {

            override fun apply(target: ByteBuffer): ByteBuffer {
                while (target.hasRemaining() && '\n'.code != target.get().toInt()) {
                }
                if (!target.hasRemaining()) {
                    throw BufferUnderflowException()
                }
                return target
            }
        },
        /**
         * makes best-attempt at reaching eol or returns end of buffer
         */
        toEol {

            override fun apply(target: ByteBuffer): ByteBuffer {
                while (target.hasRemaining() && '\n'.code != target.get().toInt()) { }
                return target
            }
        },
        back1 {

            override fun apply(target: ByteBuffer): ByteBuffer {
                val position = target.position()
                return (if (0 < position) target.position(position - 1) else target) as ByteBuffer
            }
        },
        /**
         * reverses position _up to_ 2.
         */
        back2 {

            override fun apply(target: ByteBuffer): ByteBuffer {
                val position = target.position()
                return (if (1 < position) target.position(position - 2) else bb(target, back1)) as ByteBuffer
            }
        }, /**
         * reduces the position of target until the character is non-white.
         */rtrim {

            override fun apply(target: ByteBuffer): ByteBuffer {
                val start = target.position()
                var i = start
                while (0 <= --i && Character.isWhitespace(target.get(i).toInt())) {
                }

                return target.position(++i) as ByteBuffer
            }
        },

        /**
         * noop
         */
        noop {
            override fun apply(target: ByteBuffer): ByteBuffer {
                return target
            }
        }, skipDigits {

            override fun apply(target: ByteBuffer): ByteBuffer {
                while (target.hasRemaining() && Character.isDigit(target.get().toInt())) {
                }
                return target
            }
        }
    }

    enum class post : Cursive {
        compact {
            override fun apply(target: ByteBuffer): ByteBuffer {
                return target.compact()
            }
        }, reset {

            override fun apply(target: ByteBuffer): ByteBuffer {
                return target.reset() as ByteBuffer
            }
        }, rewind {

            override fun apply(target: ByteBuffer): ByteBuffer {
                return target.rewind() as ByteBuffer
            }
        }, clear {

            override fun apply(target: ByteBuffer): ByteBuffer {
                return target.clear() as ByteBuffer
            }

        }, grow {

            override fun apply(target: ByteBuffer): ByteBuffer {
                return std.grow(target)
            }

        }, ro {

            override fun apply(target: ByteBuffer): ByteBuffer {
                return target.asReadOnlyBuffer()
            }
        },
        /**
         * fills remainder of buffer to 0's
         */
        pad0 {

            override fun apply(target: ByteBuffer): ByteBuffer {
                while (target.hasRemaining()) {
                    target.put(0)
                }
                return target
            }
        },
        /**
         * fills prior bytes to current position with 0's
         */
        pad0Until {
            override fun apply(target: ByteBuffer): ByteBuffer {
                val limit = target.limit()
                target.flip()
                while (target.hasRemaining()) {
                    target.put(0)
                }
                return target.limit(limit) as ByteBuffer
            }
        }
    }
}