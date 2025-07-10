@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.reactor

import java.nio.channels.SocketChannel as JvmSocketChannel
import java.nio.ByteBuffer as JvmByteBuffer
import java.net.InetSocketAddress

actual class SocketChannel actual constructor() : ReadableChannel, WritableChannel, ConnectableChannel {
    internal val jvmChannel = JvmSocketChannel.open()
    
    actual override val isOpen: Boolean get() = jvmChannel.isOpen
    
    actual override suspend fun close() {
        jvmChannel.close()
    }
    
    actual override suspend fun read(buffer: ByteBuffer): Int {
        return jvmChannel.read(JvmByteBuffer.wrap(buffer.array()))
    }
    
    actual override suspend fun write(buffer: ByteBuffer): Int {
        return jvmChannel.write(JvmByteBuffer.wrap(buffer.array()))
    }
    
    actual override suspend fun connect(host: String, port: Int) {
        jvmChannel.connect(InetSocketAddress(host, port))
    }
}