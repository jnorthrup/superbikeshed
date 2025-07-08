@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.reactor

/**
 * Minimal channel abstraction - one responsibility per class
 */
interface Channel {
    val isOpen: Boolean
    suspend fun close()
}

interface ReadableChannel : Channel {
    suspend fun read(buffer: ByteBuffer): Int
}

interface WritableChannel : Channel {
    suspend fun write(buffer: ByteBuffer): Int  
}

interface ConnectableChannel : Channel {
    suspend fun connect(host: String, port: Int)
}

interface AcceptingChannel : Channel {
    suspend fun accept(): ConnectableChannel?
}