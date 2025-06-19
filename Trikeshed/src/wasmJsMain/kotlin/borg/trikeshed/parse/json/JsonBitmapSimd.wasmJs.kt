package borg.trikeshed.parse.json

import kotlin.math.ceil

@OptIn(ExperimentalUnsignedTypes::class)
actual object JsonBitmapSimd {

    private const val JS_UNCHANGED = 0; private const val JS_SCOPE_OPEN = 1
    private const val JS_SCOPE_CLOSE = 2; private const val JS_VALUE_DELIM = 3
    private const val LEXER_UNCHANGED = 0; private const val LEXER_QUOTE_INC = 1
    private const val LEXER_ESCAPE_INC = 2

    actual fun createBitmap(input: UByteArray): ULongArray {
        val inputSize = input.size
        if (inputSize == 0) return ULongArray(0)

        val outputSize = ceil(inputSize / 16.0).toInt()
        val output = ULongArray(outputSize)

        for (i in 0 until inputSize) {
            val byte = input[i]
            
            val jsState = when (byte) {
                0x7Bu, 0x5Bu -> JS_SCOPE_OPEN    // { or [
                0x7Du, 0x5Du -> JS_SCOPE_CLOSE    // } or ]
                0x2Cu -> JS_VALUE_DELIM          // ,
                else -> JS_UNCHANGED
            }
            val lexerState = when (byte) {
                0x22u -> LEXER_QUOTE_INC         // "
                0x5Cu -> LEXER_ESCAPE_INC        // \
                else -> LEXER_UNCHANGED
            }
            
            val pixel = (jsState or (lexerState shl 2)).toULong()
            
            val ulongIndex = i / 16
            val bitPosition = (i % 16) * 4
            output[ulongIndex] = output[ulongIndex] or (pixel shl bitPosition)
        }
        return output
    }
}