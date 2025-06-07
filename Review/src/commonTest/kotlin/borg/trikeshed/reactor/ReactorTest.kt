package borg.trikeshed.reactor

import borg.trikeshed.lib.IOConstants.OP_ACCEPT
import borg.trikeshed.lib.IOConstants.OP_READ
import borg.trikeshed.nio.ByteBufferFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest // Assuming this import for runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReactorTest {

    @Test
    fun testBasicServerClientCommunication() = runTest {
        val reactor = createTestReactor()
        val platform = PlatformIO.create()
        val serverChannel = platform.createServerSocket().apply { // Changed method name
            configureBlocking(false)
            bind(0) // Use port 0 to get random available port
        }
        
        // Get the actual port that was assigned using the platform-agnostic way
        val port = platform.getLocalPort(serverChannel)
            
        val clientChannel = platform.createClientSocket(port).apply { // Changed method name, pass port
            configureBlocking(false)
            connect("localhost", port)
        }
        
        reactor.registerChannel(serverChannel, OP_ACCEPT) { key ->
            val server = key.channel() as ServerChannel
            val client = server.accept()
            client?.configureBlocking(false)
            client?.let { channel ->
                reactor.registerChannel(channel as ClientChannel, OP_READ) { clientKey -> // Cast for registerChannel
                    val buffer = ByteBufferFactory.allocate(1024)
                    val readChannel = clientKey.channel() as ClientChannel
                    val bytesRead = readChannel.read(buffer)
                    
                    if (bytesRead > 0) {
                        buffer.flip()
                        val receivedData = ByteArray(bytesRead)
                        buffer.get(receivedData)
                        
                        assertEquals("Hello Reactor!", receivedData.decodeToString())
                        
                        // Echo back
                        val responseBuffer = ByteBufferFactory.wrap("Echo: ${receivedData.decodeToString()}".encodeToByteArray())
                        readChannel.write(responseBuffer)
                    }
                }
            }
        }

        // Test communication
        val testData = "Hello Reactor!".toByteArray()
        val writeBuffer = ByteBufferFactory.wrap(testData)
        // With TestPlatform returning ClientChannel, explicit cast is no longer needed
        clientChannel.write(writeBuffer)
        
        delay(100) // Give time for processing
        
        val readBuffer = ByteBufferFactory.allocate(1024)
        // With TestPlatform returning ClientChannel, explicit cast is no longer needed
        val bytesRead = clientChannel.read(readBuffer)
        readBuffer.flip()
        val response = ByteArray(bytesRead)
        readBuffer.get(response)

        assertEquals("Echo: Hello Reactor!", response.decodeToString())

        // Clean up
        clientChannel.close()
        serverChannel.close()
        reactor.close()
    }

    private fun createTestReactor(): Reactor {
        return Reactor() // Assuming a default constructor or factory
    }
}
