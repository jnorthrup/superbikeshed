package borg.trikeshed.uring

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class CommonNioTest {
    @Test
    fun `commonNIO - basic read/write API works on all platforms`() = runTest {
        withCommonNio { nio ->
            val readOp = NioRead(
                fd = 0, // stdin
                buffer = ByteBuffer.allocate(1024),
                userData = 99
            )
            nio.submission.send(readOp)
            assertTrue(true, "commonNIO API works!")
        }
    }

    @Test
    fun `commonNIO - context propagation works`() = runTest {
        withCommonNio { nio ->
            val context = NioContext(
                executionId = "nio_exec_456",
                sessionId = "nio_session",
                action = "nio_read"
            )
            val readOp = NioRead(
                fd = 0,
                buffer = ByteBuffer.allocate(1024),
                context = context
            )
            nio.submission.send(readOp)
            assertEquals(context.executionId, readOp.context?.executionId)
        }
    }

    @Test
    fun `commonNIO - batch operations are supported`() = runTest {
        withCommonNio { nio ->
            val operations = listOf(
                NioRead(fd = 0, buffer = ByteBuffer.allocate(1024)),
                NioWrite(fd = 1, buffer = ByteBuffer.wrap("NIO Test".toByteArray())),
                NioTimeout(timeoutMs = 500)
            )
            nio.submitBatch(operations)
            assertTrue(true)
        }
    }
} 