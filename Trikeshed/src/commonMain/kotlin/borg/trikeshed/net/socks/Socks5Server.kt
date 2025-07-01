package borg.trikeshed.net.socks

import borg.trikeshed.net.quic.QuicConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

class Socks5Server(
    private val scope: CoroutineScope,
    private val quicConnection: QuicConnection // The underlying QUIC connection to use
) {
    private var serverJob: Job? = null

    suspend fun start(port: Int) {
        if (serverJob != null && serverJob!!.isActive) {
            println("SOCKS5 Server is already running.")
            return
        }

        println("Starting SOCKS5 Server on port $port...")
        serverJob = scope.launch {
            // Accept incoming QUIC streams as SOCKS5 client connections
            // Assuming QuicConnection provides a way to accept new streams
            // For now, this is a conceptual loop.
            while (true) { // This loop needs a proper termination condition
                val clientStream = quicConnection.acceptStream() // Conceptual: QuicConnection needs an acceptStream()
                if (clientStream == null) {
                    // Handle no new stream or server shutdown
                    continue
                }

                launch { // Handle each client connection in a new coroutine
                    println("SOCKS5: New client connection from ${clientStream.remoteAddress}")
                    val inputStream = clientStream.internalReceiveChannel
                    val outputStream = clientStream.internalSendChannel // Conceptual: QuicStream needs an internalSendChannel

                    try {
                        // SOCKS5 Handshake: Method Negotiation
                        val version = inputStream.receive().array()[0] // VER
                        val nmethods = inputStream.receive().array()[0] // NMETHODS

                        if (version != Socks5Protocol.VERSION) {
                            println("SOCKS5: Unsupported SOCKS version: $version")
                            // Send NO_ACCEPTABLE_METHODS and close
                            outputStream.send(byteArrayOf(Socks5Protocol.VERSION, Socks5Protocol.Methods.NO_ACCEPTABLE_METHODS).toPlatformByteBuffer())
                            clientStream.close()
                            return@launch
                        }

                        val methods = inputStream.receive().array().sliceArray(0 until nmethods.toInt()) // METHODS

                        if (Socks5Protocol.Methods.NO_AUTHENTICATION_REQUIRED in methods) {
                            // Send NO_AUTHENTICATION_REQUIRED
                            outputStream.send(byteArrayOf(Socks5Protocol.VERSION, Socks5Protocol.Methods.NO_AUTHENTICATION_REQUIRED).toPlatformByteBuffer())
                        } else {
                            println("SOCKS5: No acceptable authentication methods.")
                            // Send NO_ACCEPTABLE_METHODS and close
                            outputStream.send(byteArrayOf(Socks5Protocol.VERSION, Socks5Protocol.Methods.NO_ACCEPTABLE_METHODS).toPlatformByteBuffer())
                            clientStream.close()
                            return@launch
                        }

                        // TODO: Implement SOCKS5 Request (Command Processing)

                    } catch (e: Exception) {
                        println("SOCKS5: Error handling client connection: ${e.message}")
                        clientStream.close()
                    }
                }
            }
        }
    }

    suspend fun stop() {
        if (serverJob == null || !serverJob!!.isActive) {
            println("SOCKS5 Server is not running.")
            return
        }
        println("Stopping SOCKS5 Server...")
        serverJob?.cancelAndJoin()
        serverJob = null
        println("SOCKS5 Server stopped.")
    }
}
