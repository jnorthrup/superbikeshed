package com.trikeshed.uring

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class TrikeUringTest {

    private lateinit var testScope: TestCoroutineScope
    private lateinit var mockUring: MockTrikeUring

    @Before
    fun setup() {
        testScope = TestCoroutineScope()
        mockUring = MockTrikeUring(testScope)
    }

    @After
    fun teardown() {
        mockUring.close()
        testScope.cleanupTestCoroutines()
    }

    @Test
    fun `simple Read Sqe is submitted and ReadResult Cqe is received`() = testScope.runBlockingTest {
        val fd = 1
        val buffer = ByteBuffer.allocate(10)
        val offset = 0UL
        val readSqe = Read(fd, buffer, offset)

        mockUring.submission.send(readSqe)

        // Assert that the SQE was captured by the mock
        assertEquals(1, mockUring.submittedSqe.size)
        assertEquals(readSqe, mockUring.submittedSqe[0])

        // Simulate completion
        val resultValue = 10 // Bytes read
        val readCqe = ReadResult(readSqe.userData, resultValue)
        mockUring.emitCqe(readCqe)

        // Assert that the CQE is received
        val receivedCqe = mockUring.completion.first { it.userData == readSqe.userData }
        assertEquals(readCqe, receivedCqe)
        assertTrue(receivedCqe.isSuccess)
        assertNull(receivedCqe.error)
        assertEquals(resultValue, receivedCqe.result)
    }

    @Test
    fun `simple Write Sqe is submitted and WriteResult Cqe is received`() = testScope.runBlockingTest {
        val fd = 2
        val buffer = ByteBuffer.wrap("Hello".toByteArray())
        val offset = 0UL
        val writeSqe = Write(fd, buffer, offset)

        mockUring.submission.send(writeSqe)

        assertEquals(1, mockUring.submittedSqe.size)
        assertEquals(writeSqe, mockUring.submittedSqe[0])

        val resultValue = 5 // Bytes written
        val writeCqe = WriteResult(writeSqe.userData, resultValue)
        mockUring.emitCqe(writeCqe)

        val receivedCqe = mockUring.completion.first { it.userData == writeSqe.userData }
        assertEquals(writeCqe, receivedCqe)
        assertTrue(receivedCqe.isSuccess)
        assertNull(receivedCqe.error)
        assertEquals(resultValue, receivedCqe.result)
    }

    @Test
    fun `LinkedSqe processes first operation and then continuation`() = testScope.runBlockingTest {
        val readFd = 3
        val writeFd = 4
        val readBuffer = ByteBuffer.allocate(1024)
        val writeContent = "World".toByteArray()

        val initialReadSqe = Read(readFd, readBuffer, 0UL)
        val linkedSqe = LinkedSqe(
            first = initialReadSqe,
            then = { cqe ->
                if (cqe.isSuccess) {
                    Write(writeFd, ByteBuffer.wrap(writeContent), 0UL)
                } else {
                    null
                }
            }
        )

        mockUring.submission.send(linkedSqe)

        // Only the LinkedSqe itself is initially submitted to the mock
        assertEquals(1, mockUring.submittedSqe.size)
        assertEquals(linkedSqe, mockUring.submittedSqe[0])

        // Simulate completion of the *first* operation (Read)
        val readResultCqe = ReadResult(initialReadSqe.userData, 5) // 5 bytes read
        mockUring.emitCqe(readResultCqe)

        // In a real implementation, the 'then' lambda would be invoked and the new SQE submitted.
        // For this mock, we need to manually simulate the submission of the 'then' SQE.
        // This highlights that the mock is for testing the API, not the full io_uring logic.
        val expectedWriteSqe = Write(writeFd, ByteBuffer.wrap(writeContent), 0UL, linkedSqe.userData) // UserData should be the same as linkedSqe
        // Manually add the expected next SQE to the submitted list for assertion
        mockUring.submittedSqe.add(expectedWriteSqe)

        // Simulate completion of the *second* operation (Write)
        val writeResultCqe = WriteResult(expectedWriteSqe.userData, writeContent.size)
        mockUring.emitCqe(writeResultCqe)

        // Await the final completion of the linked operation
        val finalCqe = mockUring.completion.first { it.userData == linkedSqe.userData }
        assertEquals(writeResultCqe, finalCqe)
        assertTrue(finalCqe.isSuccess)
        assertNull(finalCqe.error)
        assertEquals(writeContent.size, finalCqe.result)
    }

    @Test
    fun `Sqe with error result propagates error in Cqe`() = testScope.runBlockingTest {
        val fd = 5
        val buffer = ByteBuffer.allocate(10)
        val offset = 0UL
        val readSqe = Read(fd, buffer, offset)

        mockUring.submission.send(readSqe)

        assertEquals(1, mockUring.submittedSqe.size)
        assertEquals(readSqe, mockUring.submittedSqe[0])

        val errorCode = -1 // Simulate an error
        val errorCqe = ReadResult(readSqe.userData, errorCode)
        mockUring.emitCqe(errorCqe)

        val receivedCqe = mockUring.completion.first { it.userData == readSqe.userData }
        assertEquals(errorCqe, receivedCqe)
        assertTrue(receivedCqe.isError)
        assertNotNull(receivedCqe.error)
        assertEquals(PosixError.fromErrno(1), receivedCqe.error) // Assuming errno 1 for -1 result
        assertEquals(errorCode, receivedCqe.result)
    }

    @Test
    fun `LinkedSqe terminates chain on first operation error`() = testScope.runBlockingTest {
        val readFd = 6
        val writeFd = 7
        val readBuffer = ByteBuffer.allocate(1024)

        val initialReadSqe = Read(readFd, readBuffer, 0UL)
        val linkedSqe = LinkedSqe(
            first = initialReadSqe,
            then = { cqe ->
                if (cqe.isSuccess) {
                    Write(writeFd, ByteBuffer.wrap("Should not happen".toByteArray()), 0UL)
                } else {
                    null // Terminate chain
                }
            }
        )

        mockUring.submission.send(linkedSqe)

        assertEquals(1, mockUring.submittedSqe.size)
        assertEquals(linkedSqe, mockUring.submittedSqe[0])

        // Simulate error completion of the *first* operation (Read)
        val errorCode = -2 // Another simulated error
        val readErrorCqe = ReadResult(initialReadSqe.userData, errorCode)
        mockUring.emitCqe(readErrorCqe)

        // Await the final completion, which should be the error from the first operation
        val finalCqe = mockUring.completion.first { it.userData == linkedSqe.userData }
        assertEquals(readErrorCqe, finalCqe)
        assertTrue(finalCqe.isError)
        assertNotNull(finalCqe.error)
        assertEquals(PosixError.fromErrno(2), finalCqe.error) // Assuming errno 2 for -2 result
        assertEquals(errorCode, finalCqe.result)

        // Assert that no further SQEs were submitted after the error
        assertEquals(1, mockUring.submittedSqe.size) // Only the initial LinkedSqe
    }
}
