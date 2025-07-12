@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.reactor

/**
 * Minimal channel abstraction - one responsibility per class
 * Renamed to avoid conflicts with borg.trikeshed.channel.api
 */
interface ReactorChannel {
    val isOpen: Boolean
    suspend fun close()
}

interface ReactorReadableChannel : ReactorChannel {
    suspend fun read(buffer: ByteBuffer): Int
}

interface ReactorWritableChannel : ReactorChannel {
    suspend fun write(buffer: ByteBuffer): Int  
}

interface ConnectableChannel : Channel {
    suspend fun connect(host: String, port: Int)
}

interface AcceptingChannel : Channel {
    suspend fun accept(): ConnectableChannel?
}