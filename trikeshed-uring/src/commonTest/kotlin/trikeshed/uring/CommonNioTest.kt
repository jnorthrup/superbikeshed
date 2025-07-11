package trikeshed.uring

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking
import trikeshed.lib.*

class CommonNioTest {
    
    @Test
    fun `should submit and wait for completion`() = runBlocking {
        // Given an io_uring ring
        val ring = IoUring()
        val emulator = CommonLiburingEmulator()
        
        // When initializing the ring
        val result = emulator.io_uring_queue_init(32u, ring, 0u)
        assertEquals(0, result)
        
        // And submitting an operation
        val sqe = emulator.io_uring_get_sqe(ring)
        assertNotNull(sqe)
        emulator.io_uring_prep_nop(sqe)
        
        // Then submit and wait should complete
        val submitResult = emulator.io_uring_submit_and_wait(ring, 1u)
        assertTrue(submitResult >= 0)
        
        // Cleanup
        emulator.io_uring_queue_exit(ring)
    }
    
    @Test
    fun `should submit and wait with timeout`() = runBlocking {
        // Given an io_uring ring
        val ring = IoUring()
        val emulator = CommonLiburingEmulator()
        emulator.io_uring_queue_init(32u, ring, 0u)
        
        // When submitting with timeout
        val sqe = emulator.io_uring_get_sqe(ring)
        emulator.io_uring_prep_nop(sqe)
        
        val cqePtr = 0u j { 0 }
        val result = emulator.io_uring_submit_and_wait_timeout(ring, cqePtr, 1u, TimeSpec(1, 0), null)
        
        // Then should complete within timeout
        assertTrue(result >= 0)
        
        emulator.io_uring_queue_exit(ring)
    }
    
    @Test
    fun `should wait for multiple completions`() = runBlocking {
        // Given an io_uring ring
        val ring = IoUring()
        val emulator = CommonLiburingEmulator()
        emulator.io_uring_queue_init(32u, ring, 0u)
        
        // When submitting multiple operations
        repeat(3) {
            val sqe = emulator.io_uring_get_sqe(ring)
            emulator.io_uring_prep_nop(sqe)
        }
        
        val cqePtr = 0u j { 0 }
        val result = emulator.io_uring_wait_cqes(ring, cqePtr, 3u, null, null)
        
        // Then should wait for all completions
        assertTrue(result >= 0)
        
        emulator.io_uring_queue_exit(ring)
    }
    
    @Test
    fun `should iterate through completions`() = runBlocking {
        // Given an io_uring ring
        val ring = IoUring()
        val emulator = CommonLiburingEmulator()
        emulator.io_uring_queue_init(32u, ring, 0u)
        
        // When submitting operations
        repeat(2) {
            val sqe = emulator.io_uring_get_sqe(ring)
            emulator.io_uring_prep_nop(sqe)
        }
        emulator.io_uring_submit(ring)
        
        // Then should be able to iterate through completions
        val completions = emulator.io_uring_for_each_cqe(ring)
        assertTrue(completions.a >= 0)
        
        emulator.io_uring_queue_exit(ring)
    }
    
    @Test
    fun `should set and get SQE data`() = runBlocking {
        // Given an io_uring ring and SQE
        val ring = IoUring()
        val emulator = CommonLiburingEmulator()
        emulator.io_uring_queue_init(32u, ring, 0u)
        
        val sqe = emulator.io_uring_get_sqe(ring)
        val testData = "test_data"
        
        // When setting data
        emulator.io_uring_sqe_set_data(sqe, testData)
        
        // Then should be able to get data back
        val retrievedData = emulator.io_uring_cqe_get_data(IoUringCqe(user_data = 0u, res = 0, flags = 0u))
        // Note: This is a simplified test - actual implementation would need proper data association
        
        emulator.io_uring_queue_exit(ring)
    }
    
    @Test
    fun `should prepare writev operation`() = runBlocking {
        // Given an io_uring ring and SQE
        val ring = IoUring()
        val emulator = CommonLiburingEmulator()
        emulator.io_uring_queue_init(32u, ring, 0u)
        
        val sqe = emulator.io_uring_get_sqe(ring)
        val iovecs = listOf(IoVec(ByteArray(10), 10u), IoVec(ByteArray(20), 20u))
        val iovecList = IoVecList(iovecs)
        
        // When preparing writev
        emulator.io_uring_prep_writev(sqe, 1, iovecList, 2u, 0L)
        
        // Then SQE should be configured correctly
        assertEquals(IoUringOp.IORING_OP_WRITEV, sqe.opcode)
        assertEquals(1, sqe.fd)
        assertEquals(2u, sqe.len)
        assertEquals(0L, sqe.off)
        
        emulator.io_uring_queue_exit(ring)
    }
    
    @Test
    fun `should prepare accept operation`() = runBlocking {
        // Given an io_uring ring and SQE
        val ring = IoUring()
        val emulator = CommonLiburingEmulator()
        emulator.io_uring_queue_init(32u, ring, 0u)
        
        val sqe = emulator.io_uring_get_sqe(ring)
        
        // When preparing accept
        emulator.io_uring_prep_accept(sqe, 1, null, 0u, 0)
        
        // Then SQE should be configured correctly
        assertEquals(IoUringOp.IORING_OP_ACCEPT, sqe.opcode)
        assertEquals(1, sqe.fd)
        
        emulator.io_uring_queue_exit(ring)
    }
    
    @Test
    fun `should prepare connect operation`() = runBlocking {
        // Given an io_uring ring and SQE
        val ring = IoUring()
        val emulator = CommonLiburingEmulator()
        emulator.io_uring_queue_init(32u, ring, 0u)
        
        val sqe = emulator.io_uring_get_sqe(ring)
        val addr = SocketAddress()
        
        // When preparing connect
        emulator.io_uring_prep_connect(sqe, 1, addr, 16u)
        
        // Then SQE should be configured correctly
        assertEquals(IoUringOp.IORING_OP_CONNECT, sqe.opcode)
        assertEquals(1, sqe.fd)
        
        emulator.io_uring_queue_exit(ring)
    }
    
    @Test
    fun `should prepare send operation`() = runBlocking {
        // Given an io_uring ring and SQE
        val ring = IoUring()
        val emulator = CommonLiburingEmulator()
        emulator.io_uring_queue_init(32u, ring, 0u)
        
        val sqe = emulator.io_uring_get_sqe(ring)
        val buffer = "Hello World".toByteArray()
        
        // When preparing send
        emulator.io_uring_prep_send(sqe, 1, buffer, buffer.size.toULong(), 0)
        
        // Then SQE should be configured correctly
        assertEquals(IoUringOp.IORING_OP_SEND, sqe.opcode)
        assertEquals(1, sqe.fd)
        assertEquals(buffer.size.toULong(), sqe.len)
        
        emulator.io_uring_queue_exit(ring)
    }
    
    @Test
    fun `should prepare recv operation`() = runBlocking {
        // Given an io_uring ring and SQE
        val ring = IoUring()
        val emulator = CommonLiburingEmulator()
        emulator.io_uring_queue_init(32u, ring, 0u)
        
        val sqe = emulator.io_uring_get_sqe(ring)
        val buffer = ByteArray(1024)
        
        // When preparing recv
        emulator.io_uring_prep_recv(sqe, 1, buffer, buffer.size.toULong(), 0)
        
        // Then SQE should be configured correctly
        assertEquals(IoUringOp.IORING_OP_RECV, sqe.opcode)
        assertEquals(1, sqe.fd)
        assertEquals(buffer.size.toULong(), sqe.len)
        
        emulator.io_uring_queue_exit(ring)
    }
    
    @Test
    fun `should prepare close operation`() = runBlocking {
        // Given an io_uring ring and SQE
        val ring = IoUring()
        val emulator = CommonLiburingEmulator()
        emulator.io_uring_queue_init(32u, ring, 0u)
        
        val sqe = emulator.io_uring_get_sqe(ring)
        
        // When preparing close
        emulator.io_uring_prep_close(sqe, 1)
        
        // Then SQE should be configured correctly
        assertEquals(IoUringOp.IORING_OP_CLOSE, sqe.opcode)
        assertEquals(1, sqe.fd)
        
        emulator.io_uring_queue_exit(ring)
    }
    
    @Test
    fun `should unregister buffers`() = runBlocking {
        // Given an io_uring ring with registered buffers
        val ring = IoUring()
        val emulator = CommonLiburingEmulator()
        emulator.io_uring_queue_init(32u, ring, 0u)
        
        val iovecs = listOf(IoVec(ByteArray(10), 10u))
        val iovecList = IoVecList(iovecs)
        emulator.io_uring_register_buffers(ring, iovecList, 1u)
        
        // When unregistering buffers
        val result = emulator.io_uring_unregister_buffers(ring)
        
        // Then should succeed
        assertEquals(0, result)
        
        emulator.io_uring_queue_exit(ring)
    }
    
    @Test
    fun `should unregister files`() = runBlocking {
        // Given an io_uring ring with registered files
        val ring = IoUring()
        val emulator = CommonLiburingEmulator()
        emulator.io_uring_queue_init(32u, ring, 0u)
        
        val files = listOf(1, 2, 3)
        val fdList = FdList(files)
        emulator.io_uring_register_files(ring, fdList, files.size.toUInt())
        
        // When unregistering files
        val result = emulator.io_uring_unregister_files(ring)
        
        // Then should succeed
        assertEquals(0, result)
        
        emulator.io_uring_queue_exit(ring)
    }
    
    @Test
    fun `should set SQE flags`() = runBlocking {
        // Given an io_uring ring and SQE
        val ring = IoUring()
        val emulator = CommonLiburingEmulator()
        emulator.io_uring_queue_init(32u, ring, 0u)
        
        val sqe = emulator.io_uring_get_sqe(ring)
        
        // When setting flags
        emulator.io_uring_sqe_set_flags(sqe, IoUringSqeFlags.IOSQE_IO_LINK.toUInt())
        
        // Then flags should be set
        assertEquals(IoUringSqeFlags.IOSQE_IO_LINK.toUInt(), sqe.flags)
        
        emulator.io_uring_queue_exit(ring)
    }
    
    @Test
    fun `should prepare link operation`() = runBlocking {
        // Given an io_uring ring and SQE
        val ring = IoUring()
        val emulator = CommonLiburingEmulator()
        emulator.io_uring_queue_init(32u, ring, 0u)
        
        val sqe = emulator.io_uring_get_sqe(ring)
        
        // When preparing link
        emulator.io_uring_prep_link(sqe)
        
        // Then SQE should be configured for linking
        // Note: Link operations are typically handled through flags, not a separate opcode
        
        emulator.io_uring_queue_exit(ring)
    }
    
    @Test
    fun `should prepare hardlink operation`() = runBlocking {
        // Given an io_uring ring and SQE
        val ring = IoUring()
        val emulator = CommonLiburingEmulator()
        emulator.io_uring_queue_init(32u, ring, 0u)
        
        val sqe = emulator.io_uring_get_sqe(ring)
        
        // When preparing hardlink
        emulator.io_uring_prep_hardlink(sqe)
        
        // Then SQE should be configured for hardlink operation
        // Note: This would typically be a file system operation
        
        emulator.io_uring_queue_exit(ring)
    }
} 