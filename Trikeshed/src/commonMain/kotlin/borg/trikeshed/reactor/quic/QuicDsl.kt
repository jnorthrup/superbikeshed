package borg.trikeshed.reactor.quic

import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

data class QuicServerConfig(
    var port: Int = 4433,
    var host: String = "0.0.0.0",
    var mtu: Int = 1500,
    var onStreamHandler: (QuicStream) -> Unit = {}
)

class QuicDsl {
    private val config = QuicServerConfig()

    infix fun listen(on: Int) {
        config.port = on
    }
    
    fun onStream(handler: (QuicStream) -> Unit) {
        config.onStreamHandler = handler
    }

    fun build(): QuicServerConfig = config
}

fun quicd(block: QuicDsl.() -> Unit) {
    val dsl = QuicDsl().apply(block)
    val config = dsl.build()

    // Minimal working in-memory QUIC server for integration testing
    val serverJob = CoroutineScope(Dispatchers.Default).launch {
        val socket = DatagramSocket(config.port, InetSocketAddress(config.host, config.port).address)
        try {
            while (isActive) {
                val buffer = ByteArray(config.mtu)
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)
                val data = packet.data.copyOfRange(0, packet.length)
                val stream = object : QuicStream(0L, this@launch.hashCode(), 1024, 0) {
                    override suspend fun readAll(): borg.trikeshed.lib.Indexed<Byte> {
                        return data.size j { data[it] }
                    }
                    override fun write(data: borg.trikeshed.lib.Indexed<Byte>) {
                        val response = data.toArray()
                        val responsePacket = DatagramPacket(response, response.size, packet.socketAddress)
                        socket.send(responsePacket)
                    }
                    override fun close() {}
                }
                config.onStreamHandler(stream)
            }
        } catch (e: Exception) {
            if (e is CancellationException) return@launch
            println("QUIC server error: ${e.message}")
        } finally {
            socket.close()
        }
    }
    Runtime.getRuntime().addShutdownHook(Thread { serverJob.cancel() })
} 