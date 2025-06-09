package evolution

import kotlinx.coroutines.*
import borg.trikeshed.lib.Join
import borg.trikeshed.lib.j

// This file now serves as the main entry point for the JVM application,
// orchestrating the QUIC connection using the refactored CoroutineContext.

fun main(args: Array<String>) = runBlocking {
    val host = "www.google.com"
    val port = 443

    val connectionState = QuicConnection()
    val socket = createUdpSocket() // Use the actual platform-specific UDP socket

    val context = SimpleQuicContextBuilder()
        .connection(JvmQuicConnectionContextValue(connectionState))
        .crypto(JvmQuicCryptoContextValue())
        .socket(JvmQuicSocketContextValue(socket, host, port))
        .protection(JvmQuicProtectionContextValue())
        .observability(JvmQuicObservabilityContextValue())
        .build()

    try {
        println("Attempting QUIC handshake to $host:$port...")
        val handshakeSuccess = context.performQuicHandshake()
        
        if (handshakeSuccess) {
            println("✅ QUIC Handshake successful.")
            println("Current Connection State: ${connectionState.state}")

            // Example of sending data after handshake
            val dataToSend = "Hello QUIC from JVM!".encodeToByteArray()
            val dataSendSuccess = context.sendQuicData(dataToSend)
            if (dataSendSuccess) {
                println("✅ Data sent successfully.")
            } else {
                System.err.println("❌ Failed to send data.")
            }

        } else {
            System.err.println("❌ QUIC Handshake failed.")
            System.exit(1)
        }
    } catch (e: Exception) {
        System.err.println("An error occurred during QUIC operation: ${e.message}")
        e.printStackTrace()
        System.exit(1)
    } finally {
        socket.close()
        println("Socket closed.")
    }
}
