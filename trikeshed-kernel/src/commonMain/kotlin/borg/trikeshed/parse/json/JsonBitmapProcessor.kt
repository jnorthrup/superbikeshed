package borg.trikeshed.parse.json

import kotlin.math.ceil

/**
 * Contains the platform-agnostic logic for interpreting and processing JSON bitmaps.
 */
@OptIn(ExperimentalUnsignedTypes::class)
object JsonBitmapProcessor {

    // --- Semantic Layer: Enums defining the meaning of the bitmap bits ---
    
    enum class JsStateEvent {
        Unchanged, ScopeOpen, ScopeClose, ValueDelim;
    }

    enum class LexerEvents {
        Unchanged, QuoteIncrement, EscapeIncrement;
    }

    // --- Parser: The state machine that decodes the bitmap ---

    /**
     * Decodes a 4-bit-per-byte bitmap into a final 2-bit-per-byte structural bitmap.
     * This function contains the core state machine logic for handling quotes and escapes.
     *
     * @param bitmap The ULongArray bitmap produced by `JsonBitmapSimd.createBitmap`.
     * @param inputSize The original size of the JSON input in bytes.
     * @return A UByteArray where each byte packs four 2-bit structural results.
     */
    fun decodeToStructuralBits(bitmap: ULongArray, inputSize: Int): UByteArray {
        var quoteCounter = 0
        var escapeCounter = 0
        val output = UByteArray(ceil(inputSize / 4.0).toInt())

        for (i in 0 until inputSize) {
            val ulongIndex = i / 16
            val bitPosition = (i % 16) * 4
            val pixel = ((bitmap[ulongIndex] shr bitPosition) and 0b1111uL).toInt()

            val jsStateBits = pixel and 0b11
            val lexerEventBits = (pixel shr 2) and 0b11

            // State machine logic for quotes and escapes
            if ((quoteCounter % 2) != 0) { // Inside a quote
                when {
                    (escapeCounter % 2) != 0 -> escapeCounter = 0
                    lexerEventBits == LexerEvents.EscapeIncrement.ordinal -> escapeCounter = 1
                    lexerEventBits == LexerEvents.QuoteIncrement.ordinal -> quoteCounter++
                }
            } else if (lexerEventBits == LexerEvents.QuoteIncrement.ordinal) {
                quoteCounter++
            }

            // Structural events are ignored inside quotes
            val finalStructuralBits = if ((quoteCounter % 2) != 0) 0u else jsStateBits.toUInt()

            // Pack the 2-bit result into the output UByteArray
            val outputIndex = i / 4
            val outputShift = (3 - (i % 4)) * 2
            val writeMask = (0b11u shl outputShift).inv()
            output[outputIndex] = (output[outputIndex].toUInt() and writeMask).toUByte()
            output[outputIndex] = (output[outputIndex].toUInt() or (finalStructuralBits shl outputShift)).toUByte()
        }
        return output
    }
}