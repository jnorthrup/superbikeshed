package borg.trikeshed.parse.markdown

import borg.trikeshed.reactor.currentTimeMillis
import borg.trikeshed.lib.*
import kotlin.math.ceil

/**
 * Contains the platform-agnostic logic for interpreting and processing markdown bitmaps.
 */
@OptIn(ExperimentalUnsignedTypes::class)
object MarkdownBitmapProcessor {
    // --- Semantic Layer: Enums defining the meaning of the bitmap bits ---

    enum class MarkdownStateEvent {
        Unchanged,
        CodeBlockStart,
        CodeBlockEnd,
        LanguageSpec,
    }

    enum class LexerEvents {
        Unchanged,
        BacktickIncrement,
        NewlineIncrement,
    }

    // --- Parser: The state machine that decodes the bitmap ---

    /**
     * Decodes a 4-bit-per-byte bitmap into a final 2-bit-per-byte structural bitmap.
     * This function contains the core state machine logic for handling backticks and newlines.
     *
     * @param bitmap The ULongArray bitmap produced by `MarkdownBitmapSimd.createBitmap`.
     * @param inputSize The original size of the markdown input in bytes.
     * @return A UByteArray where each byte packs four 2-bit structural results.
     */
    fun decodeToStructuralBits(
        bitmap: ULongArray,
        inputSize: Int,
    ): UByteArray {
        var backtickCounter = 0
        var newlineCounter = 0
        val output = UByteArray(ceil(inputSize / 4.0).toInt())

        for (i in 0 until inputSize) {
            val ulongIndex = i / 16
            val bitPosition = (i % 16) * 4
            val pixel = ((bitmap[ulongIndex] shr bitPosition) and 0b1111uL).toInt()

            val markdownStateBits = pixel and 0b11
            val lexerEventBits = (pixel shr 2) and 0b11

            // State machine logic for backticks and newlines
            when {
                lexerEventBits == LexerEvents.BacktickIncrement.ordinal -> backtickCounter++
                lexerEventBits == LexerEvents.NewlineIncrement.ordinal -> newlineCounter++
            }

            // Structural events are processed based on backtick patterns
            val finalStructuralBits =
                when {
                    backtickCounter >= 3 -> {
                        when (markdownStateBits) {
                            MarkdownStateEvent.CodeBlockStart.ordinal -> MarkdownStateEvent.CodeBlockStart.ordinal
                            MarkdownStateEvent.CodeBlockEnd.ordinal -> MarkdownStateEvent.CodeBlockEnd.ordinal
                            MarkdownStateEvent.LanguageSpec.ordinal -> MarkdownStateEvent.LanguageSpec.ordinal
                            else -> MarkdownStateEvent.Unchanged.ordinal
                        }
                    }
                    else -> MarkdownStateEvent.Unchanged.ordinal
                }

            // Pack the 2-bit result into the output UByteArray
            val outputIndex = i / 4
            val outputShift = (3 - (i % 4)) * 2
            val writeMask = (0b11u shl outputShift).inv()
            output[outputIndex] = (output[outputIndex].toUInt() and writeMask).toUByte()
            output[outputIndex] = (output[outputIndex].toUInt() or (finalStructuralBits.toUInt() shl outputShift)).toUByte()
        }
        return output
    }
}

/**
 * A multiplatform, SIMD-accelerated engine for creating a structural bitmap of markdown data.
 *
 * Simplified implementation for now - can be optimized later with platform-specific SIMD.
 */
@OptIn(ExperimentalUnsignedTypes::class)
object MarkdownBitmapSimd {
    /**
     * Creates a raw 4-bit-per-byte structural bitmap.
     * Simplified implementation - can be enhanced with platform-specific SIMD later.
     */
    fun createBitmap(input: UByteArray): ULongArray {
        // Simple implementation for now - creates a basic bitmap
        val outputSize = (input.size + 15) / 16
        val bitmap = ULongArray(outputSize)

        for (i in input.indices) {
            val char = input[i].toInt().toChar()
            val ulongIndex = i / 16
            val bitPosition = (i % 16) * 4

            // Simple bit patterns for markdown characters
            val pixel =
                when (char) {
                    '`' -> 0b1001uL // Backtick - potential code block marker
                    '\n' -> 0b0010uL // Newline
                    '{', '}', '[', ']' -> 0b0100uL // Structural characters
                    else -> 0b0000uL // Normal character
                }

            bitmap[ulongIndex] = bitmap[ulongIndex] or (pixel shl bitPosition)
        }

        return bitmap
    }
}

/**
 * Lightning-fast markdown parser using SIMD bitmap and Indexed<T> for TrikeShed integration.
 */
@OptIn(ExperimentalUnsignedTypes::class)
object LightningMarkdown {
    /**
     * Parse markdown string to structural bitmap using lightning-fast SIMD processing.
     */
    fun parseToBitmap(markdownString: String): Indexed<UByte> {
        val markdownBytes = markdownString.encodeToByteArray().toUByteArray()
        return createBitmapAsSeries(markdownBytes)
    }

    /**
     * Find all code block boundaries in markdown.
     */
    fun findCodeBlockBoundaries(markdownString: String): Indexed<Int> {
        val bitmap = parseToBitmap(markdownString)
        val boundaries = mutableListOf<Int>()

        bitmap.play.forEachIndexed { index, pixel ->
            val markdownState = pixel.toInt() and 0b11
            if (markdownState != MarkdownBitmapProcessor.MarkdownStateEvent.Unchanged.ordinal) {
                boundaries.add(index)
            }
        }

        return boundaries.toList().toIdx()
    }

    /**
     * Extract code blocks using Indexed<T> operations - pure TrikeShed style.
     */
    fun extractCodeBlocks(markdownString: String): Indexed<MarkdownCodeBlock> {
        val boundaries = findCodeBlockBoundaries(markdownString)
        val markdownChars = markdownString.toIdx()

        val codeBlocks = mutableListOf<MarkdownCodeBlock>()
        var currentStart = -1
        var currentLanguage: String? = null

        boundaries.play.forEachIndexed { index, boundary ->
            val char = markdownChars[boundary]

            when {
                char == '`' && currentStart == -1 -> {
                    // Start of code block
                    currentStart = boundary
                }
                char == '`' && currentStart != -1 -> {
                    // End of code block - extract content using Indexed range
                    val startIndex = currentStart + 1
                    val endIndex = boundary - 1
                    val rangeSize = endIndex - startIndex + 1

                    val contentSlice =
                        if (rangeSize > 0) {
                            rangeSize j { i: Int -> markdownChars[startIndex + i] }
                        } else {
                            emptyIndex<Char>()
                        }

                    val contentString = contentSlice.play.joinToString("").trim()

                    // Extract language if present
                    val lines = contentString.lines()
                    if (lines.isNotEmpty()) {
                        val firstLine = lines[0].trim()
                        if (firstLine.matches(Regex("^\\w+$"))) {
                            currentLanguage = firstLine
                        }
                    }

                    codeBlocks.add(
                        MarkdownCodeBlock(
                            language = currentLanguage,
                            content = contentString,
                            startLine = currentStart,
                            endLine = boundary,
                            isStart = true,
                            isEnd = true,
                        ),
                    )

                    currentStart = -1
                    currentLanguage = null
                }
            }
        }

        return codeBlocks.toList().toIdx()
    }

    /**
     * Extract only Kotlin code blocks from markdown.
     */
    fun extractKotlinCodeBlocks(markdownString: String): Indexed<MarkdownCodeBlock> {
        val allBlocks = extractCodeBlocks(markdownString)
        val kotlinBlocks =
            allBlocks.play
                .filter { block ->
                    block.language?.equals("kotlin", ignoreCase = true) == true
                }.toList()
        return kotlinBlocks.toIdx()
    }

    /**
     * Extract code block content as Indexed<String>.
     */
    fun extractCodeBlockContent(markdownString: String): Indexed<String> {
        val blocks = extractCodeBlocks(markdownString)
        val content = blocks.play.map { it.content }.toList()
        return content.toIdx()
    }

    /**
     * Extract Kotlin code block content as Indexed<String>.
     */
    fun extractKotlinCodeBlockContent(markdownString: String): Indexed<String> {
        val kotlinBlocks = extractKotlinCodeBlocks(markdownString)
        val content = kotlinBlocks.play.map { it.content }.toList()
        return content.toIdx()
    }
}

/**
 * Creates a lazy, tensor-native view of a pre-computed markdown bitmap using TrikeShed's Indexed<T>.
 *
 * @param input The raw UByteArray of markdown data.
 * @return A Indexed<UByte> where each element is a 4-bit pixel from the bitmap.
 *         The accessor function performs the necessary bit-shifting to read from the
 *         underlying ULongArray on demand.
 */
@OptIn(ExperimentalUnsignedTypes::class)
fun createBitmapAsSeries(input: UByteArray): Indexed<UByte> {
    // 1. Eagerly create the bitmap using the hyper-optimized `actual` implementation.
    val bitmapArray = MarkdownBitmapSimd.createBitmap(input)
    val inputSize = input.size

    // 2. Return a lazy Indexed view over the materialized array.
    return inputSize j { i: Int ->
        val ulongIndex = i / 16
        val bitPosition = (i % 16) * 4

        // The accessor's logic is to simply read the pre-computed pixel.
        ((bitmapArray[ulongIndex] shr bitPosition) and 0b1111uL).toUByte()
    }
}

/**
 * Represents a markdown code block with language specification
 * @property language The language identifier (e.g., "kotlin", "java", "bash")
 * @property content The code content within the block
 * @property startLine The line number where the code block starts
 * @property endLine The line number where the code block ends
 * @property isStart Whether this is the start of a code block
 * @property isEnd Whether this is the end of a code block
 */
data class MarkdownCodeBlock(
    val language: String? = null,
    val content: String = "",
    val startLine: Int = 0,
    val endLine: Int = 0,
    val isStart: Boolean = false,
    val isEnd: Boolean = false,
) {
    val isComplete: Boolean
        get() = startLine > 0 && endLine > 0 && endLine >= startLine

    val lineCount: Int
        get() = if (isComplete) endLine - startLine + 1 else 0
}

/**
 * Statistics about code blocks in markdown content
 */
data class MarkdownCodeBlockStats(
    val totalBlocks: Int,
    val totalLines: Int,
    val languageCounts: Map<String, Int>,
    val averageLinesPerBlock: Double,
)

// Extension function for String to Indexed<Char>
fun String.toIdx(): Indexed<Char> = length j { index: Int -> this[index] }
