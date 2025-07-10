@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.reactor

/**
 * Socket channel - minimal TCP implementation 
 */
expect class SocketChannel() : ReadableChannel, WritableChannel, ConnectableChannel {
    override val isOpen: Boolean
    override suspend fun close()
    override suspend fun read(buffer: ByteBuffer): Int
    override suspend fun write(buffer: ByteBuffer): Int
    override suspend fun connect(host: String, port: Int)
}