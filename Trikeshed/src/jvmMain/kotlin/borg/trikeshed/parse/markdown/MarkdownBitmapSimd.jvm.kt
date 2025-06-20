package borg.trikeshed.parse.markdown

/**
 * JVM implementation of MarkdownBitmapSimd using platform-specific optimizations.
 */
@OptIn(ExperimentalUnsignedTypes::class)
actual object MarkdownBitmapSimd {
    
    /**
     * Creates a raw 4-bit-per-byte structural bitmap using JVM-specific optimizations.
     * This implementation focuses on detecting markdown code block patterns.
     */
    actual fun createBitmap(input: UByteArray): ULongArray {
        val inputSize = input.size
        val bitmapSize = (inputSize + 15) / 16 // Round up to nearest 16-byte boundary
        val bitmap = ULongArray(bitmapSize)
        
        for (i in 0 until inputSize) {
            val ulongIndex = i / 16
            val bitPosition = (i % 16) * 4
            val byte = input[i]
            
            // Create 4-bit pixel based on markdown patterns
            val pixel = createMarkdownPixel(byte, i, input)
            
            // Set the pixel in the bitmap
            val mask = 0b1111uL shl bitPosition
            val clearMask = mask.inv()
            bitmap[ulongIndex] = (bitmap[ulongIndex] and clearMask) or (pixel.toULong() shl bitPosition)
        }
        
        return bitmap
    }
    
    /**
     * Creates a 4-bit pixel representing markdown structural information.
     * Bits 0-1: Markdown state (Unchanged, CodeBlockStart, CodeBlockEnd, LanguageSpec)
     * Bits 2-3: Lexer events (Unchanged, BacktickIncrement, NewlineIncrement)
     */
    private fun createMarkdownPixel(byte: UByte, index: Int, input: UByteArray): UByte {
        val char = byte.toInt().toChar()
        var markdownState = 0 // Unchanged
        var lexerEvent = 0 // Unchanged
        
        when (char) {
            '`' -> {
                // Check for backtick patterns
                val backtickCount = countConsecutiveBackticks(index, input)
                when {
                    backtickCount >= 3 -> {
                        markdownState = if (hasLanguageSpec(index + 3, input)) {
                            3 // LanguageSpec
                        } else {
                            1 // CodeBlockStart
                        }
                    }
                    else -> {
                        lexerEvent = 1 // BacktickIncrement
                    }
                }
            }
            '\n' -> {
                lexerEvent = 2 // NewlineIncrement
            }
        }
        
        return ((lexerEvent shl 2) or markdownState).toUByte()
    }
    
    /**
     * Counts consecutive backticks starting from the given index.
     */
    private fun countConsecutiveBackticks(startIndex: Int, input: UByteArray): Int {
        var count = 0
        var index = startIndex
        
        while (index < input.size && input[index].toInt().toChar() == '`') {
            count++
            index++
        }
        
        return count
    }
    
    /**
     * Checks if there's a language specification after the backticks.
     */
    private fun hasLanguageSpec(startIndex: Int, input: UByteArray): Boolean {
        var index = startIndex
        
        // Skip whitespace
        while (index < input.size && input[index].toInt().toChar().isWhitespace()) {
            index++
        }
        
        // Check for word characters (language spec)
        while (index < input.size) {
            val char = input[index].toInt().toChar()
            when {
                char.isLetterOrDigit() -> {
                    // Found language spec
                    return true
                }
                char == '\n' -> {
                    // End of line without language spec
                    return false
                }
                else -> {
                    // Skip other characters
                    index++
                }
            }
        }
        
        return false
    }
} 