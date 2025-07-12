@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.lib.format

import kotlin.time.TimeSource

/**
 * Common cross-platform formatters for TrikeShed
 * Eliminates platform-specific formatting dependencies
 */
object CommonFormatters {
    
    /**
     * Format byte as zero-padded hex string (e.g., 0A, FF)
     */
    fun formatByteHex(byte: Byte): String {
        val hex = byte.toUByte().toString(16).uppercase()
        return if (hex.length == 1) "0$hex" else hex
    }
    
    /**
     * Format ByteArray as hex string
     */
    fun formatBytesHex(bytes: ByteArray): String = 
        bytes.joinToString("") { formatByteHex(it) }
    
    /**
     * Format integer with zero padding
     */
    fun formatInt(value: Int, width: Int = 2): String {
        val str = value.toString()
        return if (str.length < width) {
            "0".repeat(width - str.length) + str
        } else str
    }
    
    /**
     * Format long with zero padding  
     */
    fun formatLong(value: Long, width: Int = 2): String {
        val str = value.toString()
        return if (str.length < width) {
            "0".repeat(width - str.length) + str
        } else str
    }
    
    /**
     * Cross-platform current time in milliseconds
     */
    fun currentTimeMillis(): Long = 
        TimeSource.Monotonic.markNow().elapsedNow().inWholeMilliseconds
        
    /**
     * Cross-platform current time in nanoseconds  
     */
    fun currentTimeNanos(): Long =
        TimeSource.Monotonic.markNow().elapsedNow().inWholeNanoseconds
}

/**
 * Extension functions for common formatting
 */
fun ByteArray.toHexString(): String = CommonFormatters.formatBytesHex(this)
fun Byte.toHexString(): String = CommonFormatters.formatByteHex(this)
fun Int.toZeroPaddedString(width: Int = 2): String = CommonFormatters.formatInt(this, width)
fun Long.toZeroPaddedString(width: Int = 2): String = CommonFormatters.formatLong(this, width)