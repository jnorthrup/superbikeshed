package borg.trikeshed.review.http3server

import borg.trikeshed.net.quic.QuicConnectionManager // For role definition
import evolution.AesService
import evolution.HkdfService
import evolution.RealUdpSocketFactory
import evolution.UdpSocket
import evolution.DefaultAesService // Assuming these defaults exist
import evolution.DefaultHkdfService // Assuming these defaults exist
import kotlinx.coroutines.*
import java.net.InetSocketAddress

class Http3TestServer(
    private val host: String,
    private val port: Int,
    private val aesService: AesService,
    private val hkdfService: HkdfService,
    private val udpSocketFactory: evolution.UdpSocketFactory
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()) // IO dispatcher for network operations
    private var udpSocket: UdpSocket? = null

    // TODO: Store and manage multiple client connections
    // private val activeConnections = mutableMapOf<InetSocketAddress, QuicConnectionManager>()

    fun start() {
        scope.launch {
            try {
                udpSocket = udpSocketFactory.create(port, evolution.getIODispatcher(), host) // Bind to specific host and port
                println("Http3TestServer listening on ${udpSocket?.getLocalAddress()}")

                val receiveBuffer = ByteArray(4096) // Standard buffer size for UDP packets

                while (isActive && udpSocket?.isClosed == false) {
                    val receivedPacketInfo = udpSocket?.receive(receiveBuffer) // Make UdpSocket.receive suspendable or use a loop with timeout

                    if (receivedPacketInfo != null && receivedPacketInfo.size > 0) {
                        val packetData = receiveBuffer.copyOfRange(0, receivedPacketInfo.size)
                        val senderAddress = InetSocketAddress(receivedPacketInfo.senderAddress, receivedPacketInfo.senderPort)

                        println("Received ${packetData.size} bytes from ${senderAddress}")

                        // TODO:
                        // 1. Identify if this is for an existing connection or a new one.
                        //    - For new: parse Initial packet, create QuicConnection, QuicConnectionManager, QuicTlsHandler (server role).
                        //    - For existing: retrieve connection manager.
                        // 2. Dispatch packet to the appropriate QuicConnectionManager/QuicPacketProcessor.
                        // 3. Handle sending response packets.
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) {
                    println("Http3TestServer scope cancelled.")
                } else {
                    println("Http3TestServer error: ${e.message}")
                    e.printStackTrace()
                }
            } finally {
                println("Http3TestServer stopped.")
                udpSocket?.close()
            }
        }
    }

    fun stop() {
        println("Stopping Http3TestServer...")
        scope.cancel() // Cancel all coroutines in the scope
        udpSocket?.close() // Ensure socket is closed
    }
}

fun main() = runBlocking {
    // TODO: Load certs/keys paths from config or args
    val host = "0.0.0.0"
    val port = 12347 // Example port, make configurable

    // Assuming DefaultAesService and DefaultHkdfService are available
    val aesService = DefaultAesService()
    val hkdfService = DefaultHkdfService()
    val udpSocketFactory = RealUdpSocketFactory()

    val server = Http3TestServer(host, port, aesService, hkdfService, udpSocketFactory)

    server.start()
    println("Http3TestServer started on $host:$port. Press Ctrl+C to stop.")

    // Keep server running until manually stopped or an error occurs
    // For interactive test, a delay or a readLine might be used.
    // For now, rely on Ctrl+C to stop the runBlocking scope.
    try {
        while(true) { // Poor man's keep-alive for testing in IDE
            delay(60000) // Check every minute
            if (!coroutineContext.isActive) break
        }
    } catch (e: CancellationException) {
        println("Main loop cancelled.")
    } finally {
        server.stop()
    }
}
