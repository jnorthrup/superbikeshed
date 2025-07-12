package borg.trikeshed.lib.bbcursive

import borg.trikeshed.lib.simd.*
import borg.trikeshed.lib.*
import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.j
// Conversion utilities

// Explicitly import only the expect declaration for createSimdStrategy
import borg.trikeshed.lib.simd.createSimdStrategy as expectCreateSimdStrategy

/**
 * Strategy for SIMD/autovec scanning
 */
enum class ScanStrategy {
    SCALAR,     // Pure scalar implementation
    VECTOR,     // Vector operations (e.g., NEON, SSE)
    SIMD,       // Explicit SIMD intrinsics
    AUTOVEC     // Auto-vectorization (default)
}

/**
 * BBCursive SIMD/Autovec Utility (commonMain)
 *
 * Provides SIMD/autovec register-at-a-time scanning for bbcursive patterns.
 * - Adaptive strategy selection (scalar, vector, SIMD, autovec)
 * - Integration with SimdStrategy abstraction
 * - Example: SIMD-accelerated quote/structural scan for JSON/KIF
 * - API for use in bbcursive parsers
 * - Pure Kotlin, multiplatform (expect/actual for platform impls if needed)
 */

object BBCursiveSimdAutovec {
    /**
     * Adaptive SIMD/autovec scan for a target byte (e.g., quote, paren, etc.)
     * Returns all positions of the target byte in the data.
     *
     * @param data The input ByteArray
     * @param target The byte to scan for (e.g., '"'.code.toByte())
     * @param strategy Optional: override strategy (default: autovec)
     * @return IntArray of positions where target occurs
     */
    fun scanForByte(
        data: ByteArray,
        target: Byte,
        strategy: ScanStrategy = ScanStrategy.AUTOVEC
    ): IntArray {
        return when (strategy) {
            ScanStrategy.SCALAR -> scanForByteScalar(data, target)
            ScanStrategy.SIMD, ScanStrategy.VECTOR, ScanStrategy.AUTOVEC ->
                expectCreateSimdStrategy().findByte(data.toIndexed(), target).toIntArray()
            else -> expectCreateSimdStrategy().findByte(data.toIndexed(), target).toIntArray()
        }
    }

    /**
     * Scalar fallback for scanForByte
     */
    fun scanForByteScalar(data: ByteArray, target: Byte): IntArray {
        val positions = mutableListOf<Int>()
        for (i in data.indices) {
            if (data[i] == target) positions.add(i)
        }
        return positions.toIntArray()
    }

    /**
     * Adaptive SIMD/autovec scan for any of multiple target bytes
     * (e.g., for JSON: '{', '}', '[', ']', ':', ',', '"')
     */
    fun scanForAnyByte(
        data: ByteArray,
        targets: ByteArray,
        strategy: ScanStrategy = ScanStrategy.AUTOVEC
    ): IntArray {
        return when (strategy) {
            ScanStrategy.SCALAR -> scanForAnyByteScalar(data, targets)
            ScanStrategy.SIMD, ScanStrategy.VECTOR, ScanStrategy.AUTOVEC ->
                expectCreateSimdStrategy().findAnyByte(data.toIndexed(), targets.toIndexed()).toIntArray()
            else -> expectCreateSimdStrategy().findAnyByte(data.toIndexed(), targets.toIndexed()).toIntArray()
        }
    }

    fun scanForAnyByteScalar(data: ByteArray, targets: ByteArray): IntArray {
        val positions = mutableListOf<Int>()
        for (i in data.indices) {
            if (targets.contains(data[i])) positions.add(i)
        }
        return positions.toIntArray()
    }

    /**
     * SIMD/autovec quote scan for JSON/KIF string detection
     * Returns all quote positions
     */
    fun scanQuotes(
        data: ByteArray,
        strategy: ScanStrategy = ScanStrategy.AUTOVEC
    ): IntArray = scanForByte(data, '"'.code.toByte(), strategy)

    /**
     * SIMD/autovec scan for structural characters (JSON/KIF)
     * Returns all positions of structural chars
     * @param structChars e.g. byteArrayOf('{','}','[',']',':',',','"')
     */
    fun scanStructural(
        data: ByteArray,
        structChars: ByteArray = byteArrayOf(
            '{'.code.toByte(), '}'.code.toByte(),
            '['.code.toByte(), ']'.code.toByte(),
            ':'.code.toByte(), ','.code.toByte(),
            '"'.code.toByte()
        ),
        strategy: ScanStrategy = ScanStrategy.AUTOVEC
    ): IntArray = scanForAnyByte(data, structChars, strategy)

    /**
     * Example: SIMD/autovec string extraction (gather bytes at given positions)
     */
    fun gatherBytes(
        data: ByteArray,
        positions: IntArray,
        strategy: ScanStrategy = ScanStrategy.AUTOVEC
    ): ByteArray {
        return when (strategy) {
            ScanStrategy.SCALAR -> positions.map { data[it] }.toByteArray()
            ScanStrategy.SIMD, ScanStrategy.VECTOR, ScanStrategy.AUTOVEC ->
                expectCreateSimdStrategy().gatherBytes(data.toIndexed(), positions.toIndexed()).toByteArray()
            else -> expectCreateSimdStrategy().gatherBytes(data.toIndexed(), positions.toIndexed()).toByteArray()
        }
    }

    /**
     * Example: SIMD/autovec popcount (count set bits in a bitmap)
     */
    fun popcount(
        bitmap: IntArray,
        strategy: ScanStrategy = ScanStrategy.AUTOVEC
    ): Int {
        return when (strategy) {
            ScanStrategy.SCALAR -> bitmap.sum()
            ScanStrategy.SIMD, ScanStrategy.VECTOR, ScanStrategy.AUTOVEC ->
                expectCreateSimdStrategy().popcount(bitmap.toIndexed())
            else -> expectCreateSimdStrategy().popcount(bitmap.toIndexed())
        }
    }

    // === Extension API for bbcursive parsers ===

    /**
     * Extension: scan for quotes in ByteArray
     */
    fun ByteArray.simdScanQuotes(strategy: ScanStrategy = ScanStrategy.AUTOVEC): IntArray =
        scanQuotes(this, strategy)

    /**
     * Extension: scan for structural chars in ByteArray
     */
    fun ByteArray.simdScanStructural(
        structChars: ByteArray = byteArrayOf(
            '{'.code.toByte(), '}'.code.toByte(),
            '['.code.toByte(), ']'.code.toByte(),
            ':'.code.toByte(), ','.code.toByte(),
            '"'.code.toByte()
        ),
        strategy: ScanStrategy = ScanStrategy.AUTOVEC
    ): IntArray = scanStructural(this, structChars, strategy)

    /**
     * SIMD/autovec scan for KIF structural characters
     * Returns all positions of KIF structural chars: '(', ')', '"', ';'
     * @param structChars e.g. byteArrayOf('(', ')', '"', ';')
     */
    fun scanKifStructural(
        data: ByteArray,
        structChars: ByteArray = byteArrayOf(
            '('.code.toByte(), ')'.code.toByte(),
            '"'.code.toByte(), ';'.code.toByte()
        ),
        strategy: ScanStrategy = ScanStrategy.AUTOVEC
    ): IntArray = scanForAnyByte(data, structChars, strategy)

    /**
     * SIMD/autovec scan for KIF comment start (semicolon ';')
     * Returns all positions of ';'
     */
    fun scanKifComments(
        data: ByteArray,
        strategy: ScanStrategy = ScanStrategy.AUTOVEC
    ): IntArray = scanForByte(data, ';'.code.toByte(), strategy)

    /**
     * Extension: scan for KIF structural chars in ByteArray
     */
    fun ByteArray.simdScanKifStructural(
        structChars: ByteArray = byteArrayOf(
            '('.code.toByte(), ')'.code.toByte(),
            '"'.code.toByte(), ';'.code.toByte()
        ),
        strategy: ScanStrategy = ScanStrategy.AUTOVEC
    ): IntArray = scanKifStructural(this, structChars, strategy)

    /**
     * Extension: scan for KIF comment start in ByteArray
     */
    fun ByteArray.simdScanKifComments(
        strategy: ScanStrategy = ScanStrategy.AUTOVEC
    ): IntArray = scanKifComments(this, strategy)
}

/**
 * Usage:
 *   val quotePositions = BBCursiveSimdAutovec.scanQuotes(data)
 *   val structPositions = BBCursiveSimdAutovec.scanStructural(data)
 *   val gathered = BBCursiveSimdAutovec.gatherBytes(data, positions)
 *
 * For custom SIMD strategies, use the SimdStrategy interface and pass to these methods.
 */

/**
 * Usage for KIF parsing:
 *   val kifStructs = BBCursiveSimdAutovec.scanKifStructural(data)
 *   val kifComments = BBCursiveSimdAutovec.scanKifComments(data)
 *   // or as extensions:
 *   val kifStructs2 = data.simdScanKifStructural()
 *   val kifComments2 = data.simdScanKifComments()
 */ 