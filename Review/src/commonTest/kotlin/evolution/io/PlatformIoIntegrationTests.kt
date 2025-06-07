package evolution.io.test

import evolution.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.coroutines.CoroutineContext
import kotlin.test.*
import kotlinx.coroutines.test.runTest

// expect/actual for platform type for conditional delays
internal expect val currentPlatformIsJs: Boolean
internal expect val currentPlatformIsNative: Boolean

internal fun String.decodeHex(): ByteArray {
    check(length % 2 == 0) { "Must have an even length" }
    return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

abstract class AbstractPlatformIoIntegrationTests {
    protected abstract fun getPlatformIoService(scope: CoroutineScope): PlatformIoService

    private lateinit var testScope: CoroutineScope
    protected lateinit var ioService: PlatformIoService

    @BeforeTest
    fun setUp() {
        // Using StandardTestDispatcher for more control in tests if needed,
        // but for IO, a real dispatcher might also work if tests are robust against it.
        testScope = CoroutineScope(kotlinx.coroutines.test.StandardTestDispatcher() + SupervisorJob())
        ioService = getPlatformIoService(testScope)
    }

    @AfterTest
    fun tearDown() {
        ioService.close()
        testScope.cancel() // Cancel the scope to clean up any child coroutines
    }

    @Test
    fun testUdpSendReceiveLoopback() = runTest { // runTest provides its own scope, but we use testScope for ioService
        withContext(testScope.coroutineContext) { // Ensure test runs within our testScope
            val serverChannel = ioService.createUdpChannel()
            assertNotNull(serverChannel, "Server channel should be created.")

            val clientChannel = ioService.createUdpChannel()
            assertNotNull(clientChannel, "Client channel should be created.")

            // Bind server to a specific loopback address and an ephemeral port (0)
            val serverBindAddress = SocketAddress("127.0.0.1", 0)
            val serverBindSuccess = serverChannel.bind(serverBindAddress)
            assertTrue(serverBindSuccess, "Server channel should bind successfully.")

            val actualServerAddress = serverChannel.getLocalAddress()
            assertNotNull(actualServerAddress, "Actual server address should be available after bind.")
            assertTrue(actualServerAddress.getPort() > 0, "Server port (${actualServerAddress.getPort()}) should be ephemeral and > 0.")

            // Verify hostname after bind. For "0.0.0.0" it might resolve to a specific loopback.
            // For "127.0.0.1" it should remain "127.0.0.1".
            val serverHost = actualServerAddress.getHostName()
            val isLoopbackHost = serverHost == "127.0.0.1" || serverHost == "0.0.0.0" || serverHost.startsWith("::1") || serverHost == "[::1]" || serverHost == "localhost" || serverHost.startsWith("0:0:0:0:0:0:0:1")
            assertTrue(isLoopbackHost, "Server host (${serverHost}) should be a loopback or wildcard address after bind.")

            val receivedDataChannel = Channel<Pair<ByteArray, SocketAddress>>(Channel.CONFLATED)

            val selectorJob = launch {
                try {
                    ioService.runSelectorLoop { event ->
                        // Ensure correct channel and operation before proceeding
                        if (event.channelKey == serverChannel.getNativeKey() && event.interestOp == InterestOp.READ) {
                            val buffer = ByteArray(1024)
                            // Use testScope for receive as well, or ensure runSelectorLoop uses an appropriate scope
                            // that allows suspend calls within its handler.
                            // The handler itself is suspend, so it inherits scope from runSelectorLoop's context.
                            val (bytesRead, sourceAddress) = serverChannel.receive(buffer)
                            if (bytesRead > 0 && sourceAddress != null) {
                                receivedDataChannel.trySend(buffer.copyOfRange(0, bytesRead) to sourceAddress)
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    // Expected when selectorJob is cancelled
                } catch (e: Exception) {
                    // Log or print for debugging in actual test environment
                    // println("Selector loop failed unexpectedly: ${e.message}")
                    // e.printStackTrace()
                    fail("Selector loop failed: ${e.message}")
                }
            }

            val serverRegKey = serverChannel.register(InterestOp.READ, "server_attachment")
            assertNotNull(serverRegKey, "Server channel registration should succeed.")

            val message = "Hello CCEK IO Loopback Test!".encodeToByteArray()
            var sendSuccess = false
            // Retry loop for sending, especially for JS where bind might be async and selector loop starting up
            for(i in 1..10) {
                val clientSendSuccessBytes = clientChannel.send(message, actualServerAddress)
                if (clientSendSuccessBytes == message.size) {
                    sendSuccess = true
                    break
                }
                // Conditional delay based on platform to allow network stack to settle
                val delayMs = if (currentPlatformIsJs) 250L else if (currentPlatformIsNative) 150L else 50L
                delay(delayMs)
            }
            assertTrue(sendSuccess, "Client should send data successfully to ${actualServerAddress} after retries.")

            val timeoutMs = if (currentPlatformIsJs) 20000L else 15000L
            val receivedResult = withTimeoutOrNull(timeoutMs) {
                receivedDataChannel.receive()
            }

            assertNotNull(receivedResult, "Server did not receive data on port ${actualServerAddress.getPort()}. Selector job active: ${selectorJob.isActive}, Cancelled: ${selectorJob.isCancelled}, Completed: ${selectorJob.isCompleted}")
            if (receivedResult != null) {
                val (receivedBytes, sourceAddress) = receivedResult
                assertContentEquals(message, receivedBytes, "Received data does not match sent data.")

                // Check source address from received packet
                val sourceHost = sourceAddress.getHostName()
                // Client port is ephemeral, so we don't check it.
                // Source host should also be a loopback variant.
                val isSourceLoopback = sourceHost == "127.0.0.1" || sourceHost.startsWith("::1") || sourceHost == "[::1]" || sourceHost == actualServerAddress.getHostName() || sourceHost == "localhost" || sourceHost.startsWith("0:0:0:0:0:0:0:1")
                assertTrue(isSourceLoopback, "Source host (${sourceHost}) from received packet should be a loopback address.")
            }

            // Cleanup
            clientChannel.close()
            serverChannel.close() // This should also unregister from selector implicitly or explicitly
            ioService.wakeupSelector() // Signal selector loop to exit or process pending changes
            selectorJob.cancelAndJoin() // Ensure selector loop coroutine is fully stopped
            receivedDataChannel.close()
        }
    }
}
