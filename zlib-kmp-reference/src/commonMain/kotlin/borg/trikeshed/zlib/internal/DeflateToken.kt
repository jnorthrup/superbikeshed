package borg.trikeshed.zlib.internal

import borg.trikeshed.lib.Join
import borg.trikeshed.lib.j

/**
 * Represents a decoded token from a DEFLATE stream.
 * This sealed interface allows for type-safe handling of different token types
 * (literal byte, end-of-block, or length/distance match).
 */
sealed interface DeflateToken {
    /**
     * Represents a literal byte token.
     * Uses a value class for potential performance optimization (no object allocation).
     * @param byte The literal byte value (0-255).
     */
    @JvmInline
    value class Literal(val byte: Int) : DeflateToken {
        init {
            require(byte in 0..255) { "Literal byte must be between 0 and 255." }
        }
    }

    /**
     * Represents an end-of-block token.
     * Uses an object as it's a singleton.
     */
    object EndOfBlock : DeflateToken

    /**
     * Represents a length/distance match token.
     * Uses a value class for potential performance optimization (no object allocation).
     * The length and distance are packed into a Join for register packing advantages.
     * @param length The length of the match.
     * @param distance The distance of the match.
     */
    @JvmInline
    value class Match(private val packed: Join<Int, Int>) : DeflateToken {
        constructor(length: Int, distance: Int) : this(length j distance)

        val length: Int get() = packed.a
        val distance: Int get() = packed.b

        init {
            require(length >= 3) { "Match length must be at least 3." }
            require(distance >= 1) { "Match distance must be at least 1." }
        }
    }
}
