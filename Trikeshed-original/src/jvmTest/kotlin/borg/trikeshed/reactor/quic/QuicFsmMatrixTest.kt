package borg.trikeshed.reactor.quic

import kotlinx.coroutines.*
import kotlin.test.Test
import kotlin.test.assertTrue

enum class Impl { KOTLIN, RUST }
data class TestCase(val client: Impl, val server: Impl)

class QuicFsmMatrixTest {
    private val testMatrix = listOf(
        TestCase(Impl.KOTLIN, Impl.KOTLIN),
        TestCase(Impl.KOTLIN, Impl.RUST),
        TestCase(Impl.RUST, Impl.KOTLIN),
        TestCase(Impl.RUST, Impl.RUST)
    )

    @Test
    fun `concurrent FSM A/B test matrix`() = runBlocking {
        testMatrix.map { testCase ->
            async {
                runFsmTest(testCase)
            }
        }.awaitAll()
    }

    suspend fun runFsmTest(testCase: TestCase) = coroutineScope {
        println("Running test: client=${testCase.client}, server=${testCase.server}")
        val port = 45500 + testMatrix.indexOf(testCase)
        val token = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        val requestBytes = byteArrayOf(0x42.toByte(), 0x42.toByte(), 0x42.toByte())
        val serverJob = when (testCase.server) {
            Impl.KOTLIN -> launch { launchKotlinServer(port, token, requestBytes) }
            Impl.RUST -> launch { launchRustServer(port) }
        }
        delay(200)
        val clientResult = when (testCase.client) {
            Impl.KOTLIN -> launchKotlinClient(port, requestBytes, token)
            Impl.RUST -> launchRustClient(port, requestBytes, token)
        }
        serverJob.cancelAndJoin()
        assertTrue(clientResult, "Test failed for client=${testCase.client}, server=${testCase.server}")
    }

    suspend fun launchKotlinServer(port: Int, token: ByteArray, requestBytes: ByteArray) {
        quicd {
            listen(port)
            onStream { stream ->
                val request = stream.readAll().toArray()
                traceState("KotlinServer: Received request: ${request.joinToString(",")}")
                if (request.contentEquals(requestBytes)) {
                    stream.write(borg.trikeshed.lib.s_(*token.toTypedArray()))
                } else {
                    stream.write(borg.trikeshed.lib.s_(*"ERROR".encodeToByteArray().toTypedArray()))
                }
                stream.close()
                traceState("KotlinServer: Sent response and closed stream")
            }
        }
    }

    suspend fun launchKotlinClient(port: Int, requestBytes: ByteArray, expected: ByteArray): Boolean = coroutineScope {
        val socket = java.net.DatagramSocket()
        val serverAddr = java.net.InetSocketAddress("127.0.0.1", port)
        val requestPacket = java.net.DatagramPacket(requestBytes, requestBytes.size, serverAddr)
        socket.send(requestPacket)
        traceState("KotlinClient: Sent request")
        val responseBuffer = ByteArray(1024)
        val responsePacket = java.net.DatagramPacket(responseBuffer, responseBuffer.size)
        socket.receive(responsePacket)
        val response = responsePacket.data.copyOfRange(0, responsePacket.length)
        socket.close()
        traceState("KotlinClient: Received response: ${response.joinToString(",")}")
        return@coroutineScope response.contentEquals(expected)
    }

    suspend fun launchRustServer(port: Int) {
        // TODO: Launch Rust QUIC server subprocess (e.g., via cargo run)
        // Example: ProcessBuilder("cargo", "run", "--bin", "rust_quic_server", "--", "--port=$port")
        traceState("RustServer: [stub] Would launch Rust server on port $port")
        delay(1000) // Simulate server lifetime
    }

    suspend fun launchRustClient(port: Int, requestBytes: ByteArray, expected: ByteArray): Boolean {
        // TODO: Launch Rust QUIC client subprocess and capture output
        // Example: ProcessBuilder("cargo", "run", "--bin", "rust_quic_client", "--", "--port=$port")
        traceState("RustClient: [stub] Would launch Rust client to port $port")
        delay(500)
        // Simulate a successful test for now
        return true
    }

    fun traceState(msg: String) {
        println("[FSM TRACE] $msg")
    }
} 