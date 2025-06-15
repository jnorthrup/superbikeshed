package borg.trikeshed.lib

import kotlin.jvm.JvmInline

/**
 * Represents the state of an RFC 822-style header, providing zero-allocation access
 * to header name and value using byte array indices.
 *
 * This class is designed to be immutable and thread-safe, following RelaxFactory's principles.
 */
@JvmInline
value class Rfc822HeaderState(private val bufferAndIndices: Long) {

    // The buffer is stored as a pointer within the Long, and indices are packed.
    // This is a simplified representation for demonstration. In a real zero-allocation
    // scenario, this would involve unsafe memory access or a custom ByteBuffer-like
    // structure that can be passed around without copying.
    // For Kotlin Multiplatform, direct memory access is platform-specific.
    // We'll assume a mechanism to retrieve the ByteArray from the pointer.

    // Placeholder for actual buffer retrieval (platform-specific)
    private fun getBuffer(): ByteArray {
        // In a real implementation, this would retrieve the ByteArray from the pointer
        // encoded in bufferAndIndices. For now, we'll use a dummy.
        // This part needs to be implemented using actual platform-specific memory management.
        // For JVM, it could be a direct ByteBuffer. For Native, it would be CPointed.
        // For JS, it would be a TypedArray.
        throw NotImplementedError("Buffer retrieval is platform-specific and not implemented here.")
    }

    val nameStart: Int
        get() = (bufferAndIndices ushr 32).toInt()

    val nameEnd: Int
        get() = (bufferAndIndices ushr 16 and 0xFFFF).toInt()

    val valueStart: Int
        get() = (bufferAndIndices and 0xFFFF).toInt()

    val valueEnd: Int
        get() = (bufferAndIndices ushr 48).toInt() // Assuming 48-63 for valueEnd

    /**
     * Creates a new Rfc822HeaderState instance.
     *
     * @param buffer The ByteArray containing the header data.
     * @param nameStart The starting index of the header name in the buffer.
     * @param nameEnd The ending index of the header name in the buffer.
     * @param valueStart The starting index of the header value in the buffer.
     * @param valueEnd The ending index of the header value in the buffer.
     */
    constructor(buffer: ByteArray, nameStart: Int, nameEnd: Int, valueStart: Int, valueEnd: Int) : this(
        (nameStart.toLong() shl 32) or
        (nameEnd.toLong() shl 16) or
        (valueStart.toLong()) or
        (valueEnd.toLong() shl 48) // Pack valueEnd into higher bits
    ) {
        // In a real implementation, the buffer itself would need to be managed
        // (e.g., a reference counted ByteBuffer or a global memory pool ID).
        // For this example, we're just packing indices.
    }

    /**
     * Returns the header name as a String. This involves allocation.
     */
    fun getName(): String {
        val buffer = getBuffer()
        return buffer.decodeToString(nameStart, nameEnd)
    }

    /**
     * Returns the header value as a String. This involves allocation.
     */
    fun getValue(): String {
        val buffer = getBuffer()
        return buffer.decodeToString(valueStart, valueEnd)
    }

    override fun toString(): String {
        return "Rfc822HeaderState(nameStart=$nameStart, nameEnd=$nameEnd, valueStart=$valueStart, valueEnd=$valueEnd)"
    }
}