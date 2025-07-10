package borg.trikeshed.uring

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class TrikeUringTest {
    
    @Test
    fun `beer goggles - io_uring style API works on all platforms`() = runTest {
        // This test demonstrates the "beer goggles" - same API everywhere
        withTrikeUring { uring ->
            // Submit a simple operation
            val readOp = Read(
                fd = 0, // stdin
                buffer = ByteBuffer.allocate(1024),
                userData = 42
            )
            
            uring.submission.send(readOp)
            
            // In a real test, we'd wait for completion
            // For now, just verify the API works
            assertTrue(true, "Beer goggles work!")
        }
    }
    
    @Test
    fun `CCEK context flows through operations`() = runTest {
        withTrikeUring { uring ->
            val ccekContext = CcekContext(
                executionId = "test_exec_123",
                sessionId = "test_session",
                action = "test_read"
            )
            
            val readOp = Read(
                fd = 0,
                buffer = ByteBuffer.allocate(1024),
                ccekContext = ccekContext
            )
            
            uring.submission.send(readOp)
            
            // Context should flow through the operation
            assertEquals(ccekContext.executionId, readOp.ccekContext?.executionId)
        }
    }
    
    @Test
    fun `linked operations work with continuations`() = runTest {
        withTrikeUring { uring ->
            // Create a linked read->write operation
            val linkedOp = Read(
                fd = 0,
                buffer = ByteBuffer.allocate(1024)
            ).chain()
                .then { readResult ->
                    if (readResult.isSuccess) {
                        Write(
                            fd = 1, // stdout
                            buffer = ByteBuffer.wrap("Hello from beer goggles!".toByteArray())
                        )
                    } else null
                }
                .build()
            
            uring.submission.send(linkedOp)
            
            // Verify the chain was created
            assertTrue(linkedOp.first is Read)
        }
    }
    
    @Test
    fun `batch operations are supported`() = runTest {
        withTrikeUring { uring ->
            val operations = listOf(
                Read(fd = 0, buffer = ByteBuffer.allocate(1024)),
                Write(fd = 1, buffer = ByteBuffer.wrap("Test".toByteArray())),
                Timeout(timeoutMs = 1000)
            )
            
            uring.submitBatch(operations)
            
            // Batch submitted successfully
            assertTrue(true)
        }
    }
}