package borg.trikeshed.nio

/**
 * ByteBufferFactory: Platform-Agnostic Buffer Creation
 * 
 * Factory for creating ByteBuffer instances across different platforms.
 * This abstracts the platform-specific buffer implementations.
 */
expect object ByteBufferFactory {
    /**
     * Allocate a new ByteBuffer with the specified capacity.
     */
    fun allocate(capacity: Int): ByteBuffer
    
    /**
     * Allocate a direct ByteBuffer with the specified capacity.
     * Note: On some platforms, this may be the same as allocate().
     */
    fun allocateDirect(capacity: Int): ByteBuffer
    
    /**
     * Wrap an existing byte array in a ByteBuffer.
     */
    fun wrap(array: ByteArray): ByteBuffer
} 