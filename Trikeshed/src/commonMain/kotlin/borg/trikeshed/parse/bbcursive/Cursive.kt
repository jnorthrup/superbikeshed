package borg.trikeshed.parse.bbcursive

import borg.trikeshed.lib.ByteIndexedBuffer
import kotlin.jvm.JvmInline

/**
 * some kind of less painful way to do byteIndexedBuffer operations and a few new ones thrown in.
 */
fun interface Cursive : UnaryOperator<ByteIndexedBuffer> {
    enum class pre : Cursive {
        duplicate {
            override fun invoke(target: ByteIndexedBuffer): ByteIndexedBuffer = target.dup()
        },
        flip {
            override fun invoke(target: ByteIndexedBuffer): ByteIndexedBuffer = target.flip()
        },
        slice {
            override fun invoke(target: ByteIndexedBuffer): ByteIndexedBuffer = target.slice()
        },
        mark {
            override fun invoke(target: ByteIndexedBuffer): ByteIndexedBuffer = target.mk
        },
        reset {
            override fun invoke(target: ByteIndexedBuffer): ByteIndexedBuffer = target.rew()
        },
        /**
         * exists in both pre and post Cursive atoms.
         */
        rewind {
            override fun invoke(target: ByteIndexedBuffer): ByteIndexedBuffer = target.rew()
        },
        /**
         * rewinds, dumps to console but returns unchanged buffer
         */
        debug {
            override fun invoke(target: ByteIndexedBuffer): ByteIndexedBuffer {
                System.err.println("%%: " + std.str(target, duplicate, rewind))
                return target
            }
        },
        ro {
            override fun invoke(target: ByteIndexedBuffer): ByteIndexedBuffer = target.ro()
        },

        /**
         * performs get until non-ws returned. then backtracks.by one.
         *
         * resets position and throws BufferUnderFlow if runs out of space before success
         */
        forceSkipWs {
            override fun invoke(target: ByteIndexedBuffer): ByteIndexedBuffer {
                val position = target.pos
                while (target.hasRemaining && target.get().toInt().toChar().isWhitespace());
                if (!target.hasRemaining) {
                    target.pos(position)
                    throw BufferUnderflowException()
                }
                return std.bb(target, back1)!! // Assuming bb returns non-null here
            }
        },
        skipWs {
            override fun invoke(target: ByteIndexedBuffer): ByteIndexedBuffer = target.skipWs
        },
        toWs {
            override fun invoke(target: ByteIndexedBuffer): ByteIndexedBuffer {
                while (target.hasRemaining && !target.get().toInt().toChar().isWhitespace()) {
                }
                return target
            }
        },
        /**
         * @throws BufferUnderflowException if EOL was not reached
         */
        forceToEol {
            override fun invoke(target: ByteIndexedBuffer): ByteIndexedBuffer {
                while (target.hasRemaining && '\n' != target.get().toInt().toChar()) {
                }
                if (!target.hasRemaining) {
                    throw BufferUnderflowException()
                }
                return target
            }
        },
        toEol {
            override fun invoke(target: ByteIndexedBuffer): ByteIndexedBuffer {
                while (target.hasRemaining && '\n' != target.get().toInt().toChar()) { }
                return target
            },
        back1 {
            override fun invoke(target: ByteIndexedBuffer): ByteIndexedBuffer = target.dec()
        },
        /**
         * reverses position _up to_ 2.
         */
        back2 {
            override fun invoke(target: ByteIndexedBuffer): ByteIndexedBuffer = target.dec().dec()
        },
        /**
         * reduces the position of target until the character is non-white.
         */
        rtrim {
            override fun invoke(target: ByteIndexedBuffer): ByteIndexedBuffer = target.rtrim
        },

        /**
         * noop
         */
        noop {
            override fun invoke(target: ByteIndexedBuffer): ByteIndexedBuffer = target
        },
        skipDigits {
            override fun invoke(target: ByteIndexedBuffer): ByteIndexedBuffer {
                while (target.hasRemaining && Character.isDigit(target.get().toInt().toChar())) {
                }
                return target
            }
        }
    }

    enum class post : Cursive {
        compact {
            override fun invoke(target: ByteIndexedBuffer): ByteIndexedBuffer = target.compact() // Assuming compact method exists
        },
        reset {
            override fun invoke(target: ByteIndexedBuffer): ByteIndexedBuffer = target.rew()
        },
        rewind {
            override fun invoke(target: ByteIndexedBuffer): ByteIndexedBuffer = target.rew()
        },
        clear {
            override fun invoke(target: ByteIndexedBuffer): ByteIndexedBuffer = target.clr()
        },
        grow {
            override fun invoke(target: ByteIndexedBuffer): ByteIndexedBuffer = std.grow(target)
        },
        ro {
            override fun invoke(target: ByteIndexedBuffer): ByteIndexedBuffer = target.ro()
        },
        /**
         * fills remainder of buffer to 0's
         */
        pad0 {
            override fun invoke(target: ByteIndexedBuffer): ByteIndexedBuffer {
                while (target.hasRemaining) {
                    target.put(0)
                }
                return target
            }
        },
        /**
         * fills prior bytes to current position with 0's
         */
        pad0Until {
            override fun invoke(target: ByteIndexedBuffer): ByteIndexedBuffer {
                val limit = target.lim
                target.flip()
                while (target.hasRemaining) {
                    target.put(0)
                }
                return target.lim(limit)
            }
        }
    }
}

// Placeholder for BufferUnderflowException, if not available in Kotlin Common
class BufferUnderflowException : RuntimeException()