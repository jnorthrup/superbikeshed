package borg.trikeshed.reactor.quic

import borg.trikeshed.lib.s_
import borg.trikeshed.lib.toArray
import kotlinx.coroutines.*
import org.junit.Test
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.DatagramPacket
import kotlin.test.assertContentEquals

class QuicIntegrationTest {
    @Test
    fun `quicd server and client can fetch binary token`() = runBlocking {
        val port = 45433
        val token = byteArrayOf(0x01, 0x02, 0x03, 0x7F, 0x00, 0x10, 0x20, 0x30)
        val requestBytes = byteArrayOf(0x42, 0x42, 0x42) // 'BBB' in ASCII
        val serverJob = launch {
            quicd {
                listen on port
                onStream { stream ->
                    val request = stream.readAll().toArray()
                    if (request.contentEquals(requestBytes)) {
                        stream.write(s_(*token.toTypedArray()))
                    } else {
                        stream.write(s_(*"ERROR".encodeToByteArray().toTypedArray()))
                    }
                    stream.close()
                }
            }
        }
        delay(200) // Give server time to start

        val clientJob = async {
            val socket = DatagramSocket()
            val serverAddr = InetSocketAddress("127.0.0.1", port)
            val requestPacket = DatagramPacket(requestBytes, requestBytes.size, serverAddr)
            socket.send(requestPacket)

            val responseBuffer = ByteArray(1024)
            val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
            socket.receive(responsePacket)
            val response = responsePacket.data.copyOfRange(0, responsePacket.length)
            socket.close()
            response
        }

        val response = clientJob.await()
        assertContentEquals(token, response)
        serverJob.cancelAndJoin()
    }

    // TODO: Evolve this test to:
    // 1. Launch a Rust (cargo) QUIC server and A/B test against TrikeShed's quicd
    // 2. Launch a Rust (cargo) QUIC client and A/B test against TrikeShed's quicd
    // 3. Parameterize the test to run both implementations and compare results
    // 4. Add more complex token exchange and feedback scenarios
} 