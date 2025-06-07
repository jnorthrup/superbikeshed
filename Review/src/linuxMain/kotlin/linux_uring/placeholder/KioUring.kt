package linux_uring.placeholder

import borg.trikeshed.lib.*
import borg.trikeshed.native.HasPosixErr.Companion.posixRequires
import kotlinx.cinterop.*
import linux_uring.include.*
import platform.posix.*
import platform.posix.mmap as posix_mmap
import platform.posix.munmap as posix_munmap
import platform.posix.close as posix_close
import platform.posix.open as posix_open
import platform.posix.stat as posix_stat
import platform.posix.fstat as posix_fstat
import platform.posix.ioctl as posix_ioctl
import platform.posix.syscall as posix_syscall
import platform.posix.off_t as posix_off_t
import platform.linux.BLKGETSIZE64 as PlatformLinuxBLKGETSIZE64
import simple.PosixStatMode
import kotlin.math.min
import zlinux_uring.*

private const val CATQUEUE_DEPTH = 32U
private const val BLOCK_SZ = 512UL // Typical block size for I/O operations

// Helper to get file size, handling regular files and block devices
private fun get_file_size(fd: Int): posix_off_t = memScoped {
    val st: posix_stat = alloc()
    if (posix_fstat(fd, st.ptr as CValuesRef<posix_stat>) >= 0) {
        if (PosixStatMode.S_ISBLK(st.st_mode)) {
            val bytes: ULongVar = alloc()
            posixRequires(posix_ioctl(fd, PlatformLinuxBLKGETSIZE64.toULong(), bytes.ptr) == 0) { ("ioctl BLKGETSIZE64") }
            return bytes.value.toLong()
        } else {
            posixRequires(PosixStatMode.S_ISREG(st.st_mode)) { "file handle invalid: not a regular file or block device" }
        }
    } else {
        posixRequires(false) { "fstat failed: ${strerror(errno)?.toKString()}" }
    }
    return st.st_size
}

// Simple io_uring wrapper for this example
class KioUring {
    val ring_fd: Int
    private val p: io_uring_params // Store params to access sizes for unmapping
    private val sq_ring_ptr: COpaquePointer
    private val cq_ring_ptr: COpaquePointer
    private val sqes_ptr: CPointer<io_uring_sqe>

    private val sq_head_ptr: CPointer<UIntVar>
    private val sq_tail_ptr: CPointer<UIntVar>
    private val sq_mask: UInt
    private val sq_entries: UInt

    private val cq_head_ptr: CPointer<UIntVar>
    private val cq_tail_ptr: CPointer<UIntVar>
    private val cq_mask: UInt
    private val cq_entries: UInt
    private val cqes_array_ptr: CPointer<io_uring_cqe>

    init {
        memScoped {
            val params = alloc<io_uring_params>()
            ring_fd = io_uring_setup(CATQUEUE_DEPTH, params.ptr).also {
                posixRequires(it >= 0) { "io_uring_setup failed: ${strerror(errno)?.toKString()}" }
            }

            this@KioUring.p = params.pointed // Assign to class member
            val p = this@KioUring.p // Use the class member

            val single_mmap = (p.features and UringSetupFeatures.featSingle_mmap.src) != 0U

            // Map submission queue ring
            val sring_sz = p.sq_off.array + p.sq_entries * sizeOf<UIntVar>().toUInt()
            sq_ring_ptr = posix_mmap(
                null, sring_sz.toULong(), PROT_READ or PROT_WRITE,
                MAP_SHARED or MAP_POPULATE, ring_fd, IORING_OFF_SQ_RING.toLong()
            )!!.also {
                posixRequires(it != MAP_FAILED) { "mmap SQ ring failed: ${strerror(errno)?.toKString()}" }
            }
            sq_head_ptr = (sq_ring_ptr.toLong() + p.sq_off.head.toLong()).toCPointer()!!
            sq_tail_ptr = (sq_ring_ptr.toLong() + p.sq_off.tail.toLong()).toCPointer()!!
            sq_mask = p.sq_off.ring_mask
            sq_entries = p.sq_off.ring_entries

            // Map completion queue ring
            val cring_sz = p.cq_off.cqes + p.cq_entries * sizeOf<io_uring_cqe>().toUInt()
            cq_ring_ptr = if (single_mmap) sq_ring_ptr
            else posix_mmap(
                null, cring_sz.toULong(),
                PROT_READ or PROT_WRITE, MAP_SHARED or MAP_POPULATE, ring_fd, IORING_OFF_CQ_RING.toLong()
            )!!.also {
                posixRequires(it != MAP_FAILED) { "mmap CQ ring failed: ${strerror(errno)?.toKString()}" }
            }
            cq_head_ptr = (cq_ring_ptr.toLong() + p.cq_off.head.toLong()).toCPointer()!!
            cq_tail_ptr = (cq_ring_ptr.toLong() + p.cq_off.tail.toLong()).toCPointer()!!
            cq_mask = p.cq_off.ring_mask
            cq_entries = p.cq_off.ring_entries
            cqes_array_ptr = (cq_ring_ptr.toLong() + p.cq_off.cqes.toLong()).toCPointer()!!

            // Map submission queue entries array
            sqes_ptr = posix_mmap(
                null, (p.sq_entries * sizeOf<io_uring_sqe>().toUInt()).toULong(),
                PROT_READ or PROT_WRITE, MAP_SHARED or MAP_POPULATE, ring_fd, IORING_OFF_SQES.toLong()
            )!!.reinterpret<io_uring_sqe>().also {
                posixRequires(it != MAP_FAILED) { "mmap SQ entries failed: ${strerror(errno)?.toKString()}" }
            }
        }
    }

    // Submit a read request for the whole file
    fun submitReadRequest(file_fd: Int, userData: ULong): Unit = memScoped {
        val file_sz = get_file_size(file_fd)
        val num_blocks = ((file_sz + BLOCK_SZ - 1) / BLOCK_SZ).toInt() // Round up

        val iovecs = allocArray<iovec>(num_blocks)
        for (i in 0 until num_blocks) {
            val iov_len = min(BLOCK_SZ.toLong(), file_sz - i * BLOCK_SZ.toLong()).toULong()
            iovecs[i].iov_base = memalign(BLOCK_SZ.toULong(), iov_len)!!.also {
                posixRequires(it != null) { "memalign failed for iovec" }
            }
            iovecs[i].iov_len = iov_len
        }

        val head = sq_head_ptr.pointed.value
        val tail = sq_tail_ptr.pointed.value
        posixRequires(tail - head < sq_entries) { "SQ is full, cannot submit more requests" }

        val index = tail and sq_mask
        val sqe = sqes_ptr[index.toInt()]

        sqe.pointed.apply {
            opcode = IORING_OP_READV.toUByte()
            flags = 0U // No special flags for this basic read
            fd = file_fd
            addr = iovecs.toLong().toULong() // Pointer to iovec array
            len = num_blocks.toUInt()
            off = 0uL // Start reading from offset 0
            user_data = userData // User data for completion identification
        }

        sq_tail_ptr.pointed.value = (tail + 1U)
        // Ensure kernel sees the updated tail
        io_uring_enter(ring_fd, 1U, 0U, 0U) // Submit 1 entry, wait for 0 completion
    }

    // Wait for completion and process result
    fun waitForCompletion(): CPointer<io_uring_cqe>? = memScoped {
        val cqe_ptr_var = alloc<CPointerVar<io_uring_cqe>>()
        val ret = io_uring_wait_cqe(ring_fd, cqe_ptr_var.ptr)
        posixRequires(ret == 0) { "io_uring_wait_cqe failed: ${strerror(errno)?.toKString()}" }

        val cqe = cqe_ptr_var.value
        posixRequires(cqe != null) { "Received null cqe" }
        posixRequires(cqe.pointed.res >= 0) { "I/O error in CQE: ${cqe.pointed.res}, ${strerror(-cqe.pointed.res)?.toKString()}" }

        // Advance CQ ring head
        val head = cq_head_ptr.pointed.value
        cq_head_ptr.pointed.value = (head + 1U)
        io_uring_cqe_seen(ring_fd, cqe) // Mark CQE as seen

        return cqe
    }

    // Clean up allocated resources
    fun close() {
        val sring_sz = p.sq_off.array + p.sq_entries * sizeOf<UIntVar>().toUInt()
        posix_munmap(sq_ring_ptr, sring_sz.toULong())

        val cring_sz = p.cq_off.cqes + p.cq_entries * sizeOf<io_uring_cqe>().toUInt()
        if (cq_ring_ptr != sq_ring_ptr) { // Only unmap if it's a separate mapping
            posix_munmap(cq_ring_ptr, cring_sz.toULong())
        }
        posix_munmap(sqes_ptr, (p.sq_entries * sizeOf<io_uring_sqe>().toUInt()).toULong())
        posix_close(ring_fd)
    }
}

// Function to read a file using KioUring
fun readFileWithIoUring(filePath: String) = memScoped {
    val ring = KioUring()

    val file_fd = posix_open(filePath, O_RDONLY)
    posixRequires(file_fd >= 0) { "Failed to open file $filePath: ${strerror(errno)?.toKString()}" }

    val user_data = 12345uL // Arbitrary user data for tracking

    println("Submitting read request for $filePath...")
    ring.submitReadRequest(file_fd, user_data)
    println("Waiting for I/O completion...")

    val cqe = ring.waitForCompletion()
    println("I/O completed. User data: ${cqe?.pointed?.user_data}, Result: ${cqe?.pointed?.res} bytes read")

    // In a real application, you would retrieve the data from the iovecs
    // associated with this request. For this example, we'll just confirm
    // that the read operation itself completed successfully.
    // The iovecs were allocated and associated with the SQE when submitReadRequest was called.
    // To access the read data, you would need to store the iovecs pointer
    // using the user_data field, and then retrieve and process them here.
    // For simplicity, we just print the success.

    posix_close(file_fd)
    ring.close()
    println("Io_uring and file descriptor closed.")
}

// main function to demonstrate KioUring
fun main(args: Array<String>) {
    val filePath = args.firstOrNull() ?: "/etc/hosts" // Default file to read

    println("Attempting to read file: $filePath using io_uring")
    try {
        readFileWithIoUring(filePath)
    } catch (e: Exception) {
        println("An error occurred: ${e.message}")
        e.printStackTrace()
    }
}
