@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.reactor

import java.nio.channels.SelectableChannel as JavaSelectableChannel
import java.nio.channels.SocketChannel as JavaSocketChannel
import java.nio.ByteBuffer as JavaByteBuffer

actual class ClientChannel(internal val javaChannel: JavaSocketChannel = JavaSocketChannel.open()) : ReadableChannel, WritableChannel {
    override val isOpen: Boolean get() = javaChannel.isOpen
    
    override suspend fun close() {
        javaChannel.close()
    }
    
    override fun configureBlocking(block: Boolean): SelectableChannel {
        javaChannel.configureBlocking(block)
        return this
    }
    
    override val isBlocking: Boolean get() = javaChannel.isBlocking
    
    actual suspend fun connect(host: String, port: Int) {
        javaChannel.connect(java.net.InetSocketAddress(host, port))
    }
    
    override suspend fun write(buffer: ByteBuffer): Int {
        return javaChannel.write(JavaByteBuffer.wrap(buffer.array()))
    }
    
    override suspend fun read(buffer: ByteBuffer): Int {
        return javaChannel.read(JavaByteBuffer.wrap(buffer.array()))
    }
}

actual class SelectionKey(internal val javaKey: java.nio.channels.SelectionKey) {
    actual fun isValid(): Boolean = javaKey.isValid
    actual fun cancel() { javaKey.cancel() }
    actual fun interestOps(): Int = javaKey.interestOps()
    actual fun interestOps(ops: Int): SelectionKey {
        javaKey.interestOps(ops)
        return this
    }
    actual fun readyOps(): Int = javaKey.readyOps()
    actual fun channel(): Any = javaKey.channel()
    actual fun selector(): Any = javaKey.selector()
    actual fun isReadable(): Boolean = javaKey.isReadable
    actual fun isWritable(): Boolean = javaKey.isWritable
    actual fun isConnectable(): Boolean = javaKey.isConnectable
    actual fun isAcceptable(): Boolean = javaKey.isAcceptable
    actual fun attachment(): Any? = javaKey.attachment()
    actual fun attach(ob: Any?): Any? = javaKey.attach(ob)
}

actual class SelectorInterface actual constructor() {
    internal val javaSelector = java.nio.channels.Selector.open()
    
    actual fun select(): Int = javaSelector.select()
    actual fun wakeup() { javaSelector.wakeup() }
    actual fun register(channel: SelectableChannel, ops: Int, attachment: Any?): SelectionKey {
        val javaChannel = when (channel) {
            is ClientChannel -> channel.javaChannel
            else -> throw IllegalArgumentException("Unsupported channel type")
        }
        return SelectionKey(javaChannel.register(javaSelector, ops, attachment))
    }
    actual fun selectedKeys(): Set<SelectionKey> = 
        javaSelector.selectedKeys().map { SelectionKey(it) }.toSet()
    actual suspend fun close() { javaSelector.close() }
}

actual class IOOperation actual constructor(actual val value: Int) {
    actual companion object {
        actual val Read = IOOperation(1)
        actual val Write = IOOperation(4)
        actual val Accept = IOOperation(16)
        actual val Connect = IOOperation(8)
    }
}

actual fun currentTimeMillis(): Long = System.currentTimeMillis()