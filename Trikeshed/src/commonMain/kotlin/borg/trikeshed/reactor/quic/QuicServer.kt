package borg.trikeshed.reactor.quic

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.toArray
import borg.trikeshed.lib.j
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import java.net.InetSocketAddress
import java.nio.channels.DatagramChannel

interface ByteBuffer {
    fun remaining(): Int
    fun hasRemaining(): Boolean
    fun get(): Byte
    fun put(byte: Byte)
    fun array(): ByteArray
}

interface QuicServer {
    suspend fun send(data: ByteArray): Int
    suspend fun receive(): ByteArray
    fun close()
}

class QuicServerImpl(private val config: QuicServerConfig) {

    suspend fun start() = coroutineScope {
        val channel = DatagramChannel.open()
        channel.configureBlocking(false)
        channel.bind(InetSocketAddress(config.host, config.port))

        println("QUIC server started on port ${config.port}")

        val connections = mutableMapOf<InetSocketAddress, QuicConnection>()

        while (true) {
            val buffer = ByteBuffer.allocate(config.mtu)
            val clientAddress = channel.receive(buffer) as? InetSocketAddress

            if (clientAddress != null) {
                val connection = connections.getOrPut(clientAddress) {
                    println("New QUIC connection from $clientAddress")
                    QuicConnection(clientAddress, channel, config)
                }
                buffer.flip()
                connection.processIncoming(buffer)
            }
        }
    }
}

class QuicConnection(
    private val remoteAddress: InetSocketAddress,
    private val channel: DatagramChannel,
    private val config: QuicServerConfig
) {
    private val streams = mutableMapOf<Long, QuicStream>()
    private var nextStreamId = 0L

    fun processIncoming(buffer: ByteBuffer) {
        // In a real implementation, we'd parse QUIC packets and stream frames here.
        // For this demo, we assume the entire packet is for one stream.
        val stream = streams.getOrPut(0L) { // Default to stream 0 for simplicity
            val newStream = QuicStream(0L, this)
            config.onStreamHandler(newStream)
            newStream
        }
        stream.receiveData(buffer)
    }

    fun send(streamId: Long, data: ByteBuffer) {
        // A real implementation would frame this data into a QUIC packet
        channel.send(data, remoteAddress)
    }

    fun createStream(): QuicStream {
        val streamId = nextStreamId++
        val stream = QuicStream(streamId, this)
        streams[streamId] = stream
        return stream
    }
}

class QuicStream(val streamId: Long, private val connection: QuicConnection) {
    private val incoming = Channel<ByteBuffer>(Channel.UNLIMITED)

    fun receiveData(buffer: ByteBuffer) {
        // Must copy buffer as it will be reused
        val copy = ByteBuffer.allocate(buffer.remaining())
        copy.put(buffer)
        copy.flip()
        incoming.trySend(copy)
    }

    suspend fun read(): ByteBuffer = incoming.receive()

    suspend fun readAll(): Indexed<Byte> {
        val bytes = mutableListOf<Byte>()
        // This is a simplified version. A real version would handle stream termination.
        val buffer = read()
        while(buffer.hasRemaining()) {
            bytes.add(buffer.get())
        }
        val byteArray = bytes.toByteArray()
        return byteArray.size j { byteArray[it] }
    }

    fun write(data: Indexed<Byte>) {
        // Simplified: assumes data fits in one buffer
        val bytes = data.toArray()
        connection.send(streamId, ByteBuffer.wrap(bytes))
    }

    fun close() {
        incoming.close()
        // Send FIN frame in a real implementation
    }
} 