@file:OptIn(ExperimentalForeignApi::class)

package borg.trikeshed.nio

import borg.trikeshed.core.Tensor
import borg.trikeshed.core.internal.TensorUtils // Placeholder for Tensor <-> ByteArray conversion
import evolution.io.SocketAddress // Reusing common expect class
import kotlinx.cinterop.*
import platform.posix.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO // Specific K/N IO dispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// --- Native Actual for SocketAddress (if not already provided by evolution.io for Native) ---
// Assuming evolution.io.SocketAddress has a Native actual that can be converted to/from sockaddr_in.
// Add internal helpers if needed.

internal fun SocketAddress.toSockaddrIn(scope: MemScope): sockaddr_in {
    val addr = scope.alloc<sockaddr_in>()
    memset(addr.ptr, 0, sockaddr_in.size.convert())
    addr.sin_family = AF_INET.convert()
    addr.sin_port = posix_htons(this.port.toShort()) // Corrected: use posix_htons
    if (inet_pton(AF_INET, this.host, addr.sin_addr.ptr) != 1) {
        throw NioException("Failed to convert host '\${this.host}' to network address: \${strerror(errno)?.toKString()}")
    }
    return addr
}

internal fun sockaddr_in.toSocketAddress(): SocketAddress {
    memScoped {
        val hostBuffer = allocArray<ByteVar>(INET_ADDRSTRLEN)
        val hostStr = inet_ntop(AF_INET, this@toSocketAddress.sin_addr.ptr, hostBuffer, INET_ADDRSTRLEN.convert())
            ?.toKString() ?: throw NioException("Failed to convert network address to host string: \${strerror(errno)?.toKString()}")
        val port = posix_ntohs(this@toSocketAddress.sin_port).toInt() // Corrected: use posix_ntohs
        return SocketAddress(hostStr, port)
    }
}

// Helper for htons/ntohs (assuming these might not be directly in platform.posix or for clarity)
// These are simplified and assume current system is little-endian if different from network order.
// A robust solution would check actual system endianness.
internal fun posix_htons(value: Short): UShort {
    // Network byte order is big-endian.
    // If system is little-endian, swap bytes. Otherwise, no change.
    // For simplicity, assume we might need to swap. Kotlin/Native doesn't expose endianness easily.
    // This is a common pattern for manual big-endian conversion:
    return (((value.toInt() and 0xFF) shl 8) or ((value.toInt() shr 8) and 0xFF)).toUShort()
}
internal fun posix_ntohs(value: UShort): Short {
    return (((value.toInt() and 0xFF) shl 8) or ((value.toInt() shr 8) and 0xFF)).toShort()
}


actual interface ServerSocketChannel : NioSelectable {
    // Actual implementations will be in ActualServerSocketChannel
}

actual class ActualServerSocketChannel : ServerSocketChannel {
    private var fd: Int = -1
    private var _isOpen: Boolean = false
    private var _localAddress: SocketAddress? = null

    init {
        fd = socket(AF_INET, SOCK_STREAM, 0)
        if (fd < 0) {
            throw NioException("Failed to create server socket: \${strerror(errno)?.toKString()}")
        }
        _isOpen = true
        configureBlocking(false) // Default to non-blocking
    }

    actual override suspend fun bind(host: String, port: Int, backlog: Int): SocketAddress = withContext(Dispatchers.IO) {
        if (!_isOpen) throw NioException("Socket is closed.")
        memScoped {
            val servAddr = SocketAddress(host, port).toSockaddrIn(this)
            if (platform.posix.bind(fd, servAddr.ptr.reinterpret(), sockaddr_in.size.convert()) < 0) {
                throw NioException("Bind failed for \$host:\$port: \${strerror(errno)?.toKString()}")
            }
            if (listen(fd, backlog) < 0) {
                throw NioException("Listen failed: \${strerror(errno)?.toKString()}")
            }
            // Get the actual bound address
            val actualAddrLen = alloc<socklen_tVar>()
            actualAddrLen.value = sockaddr_in.size.convert()
            val actualAddr = alloc<sockaddr_in>()
            if (getsockname(fd, actualAddr.ptr.reinterpret(), actualAddrLen.ptr) < 0) {
                throw NioException("getsockname failed: \${strerror(errno)?.toKString()}")
            }
            _localAddress = actualAddr.toSocketAddress()
            _localAddress!!
        }
    }

    actual override suspend fun accept(): ClientSocketChannel? = withContext(Dispatchers.IO) {
        if (!_isOpen) return@withContext null
        memScoped {
            val clientAddr = alloc<sockaddr_in>()
            val clientAddrLen = alloc<socklen_tVar>()
            clientAddrLen.value = sockaddr_in.size.convert()

            var clientFd = -1
            // Loop for non-blocking accept
            while (isActive) {
                clientFd = platform.posix.accept(fd, clientAddr.ptr.reinterpret(), clientAddrLen.ptr)
                if (clientFd >= 0) {
                    break // Accepted a connection
                }
                if (errno == EAGAIN || errno == EWOULDBLOCK) {
                    // No pending connections, suspend and retry (conceptual, real selector needed for efficiency)
                    delay(10) // Small delay to avoid busy spinning; replace with selector logic
                    continue
                } else {
                    // Actual error
                    throw NioException("Accept failed: \${strerror(errno)?.toKString()}")
                }
            }
            if (!isActive && clientFd < 0) return@withContext null // Coroutine cancelled

            ActualClientSocketChannel(clientFd, clientAddr.toSocketAddress())
        }
    }

    actual override fun localAddress(): SocketAddress? = _localAddress

    actual override fun close() {
        if (_isOpen) {
            _isOpen = false
            platform.posix.close(fd)
            fd = -1
        }
    }

    actual override fun isOpen(): Boolean = _isOpen

    actual override fun configureBlocking(block: Boolean) {
        if (!_isOpen) throw NioException("Socket is closed.")
        val flags = fcntl(fd, F_GETFL, 0)
        if (flags < 0) throw NioException("fcntl(F_GETFL) failed: \${strerror(errno)?.toKString()}")
        val newFlags = if (block) flags and O_NONBLOCK.inv() else flags or O_NONBLOCK
        if (fcntl(fd, F_SETFL, newFlags) < 0) {
            throw NioException("fcntl(F_SETFL) failed: \${strerror(errno)?.toKString()}")
        }
    }
}

actual interface ClientSocketChannel : NioSelectable {
    // Actual implementations will be in ActualClientSocketChannel
}

actual class ActualClientSocketChannel internal constructor(
    private var fd: Int,
    private var _remoteAddress: SocketAddress? = null // Known if created via accept
) : ClientSocketChannel {

    constructor() : this(socket(AF_INET, SOCK_STREAM, 0).also {
        if (it < 0) throw NioException("Failed to create client socket: \${strerror(errno)?.toKString()}")
    })

    private var _isOpen: Boolean = true
    private var _isConnected: Boolean = (_remoteAddress != null)
    private var _localAddress: SocketAddress? = null


    init {
        if (_isOpen) configureBlocking(false) // Default to non-blocking
    }

    actual override suspend fun connect(host: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        if (!_isOpen) throw NioException("Socket is closed.")
        if (_isConnected) return@withContext true // Already connected

        memScoped {
            val remoteSockAddr = SocketAddress(host, port).toSockaddrIn(this)
            val result = platform.posix.connect(fd, remoteSockAddr.ptr.reinterpret(), sockaddr_in.size.convert())

            if (result == 0) {
                _isConnected = true
                _remoteAddress = SocketAddress(host, port) // Store after successful connect
                return@withContext true // Connected immediately
            } else {
                if (errno == EINPROGRESS) {
                    return@withContext false // Connection pending
                } else {
                    throw NioException("Connect failed for \$host:\$port: \${strerror(errno)?.toKString()}")
                }
            }
        }
    }

    actual override suspend fun finishConnect(): Boolean = withContext(Dispatchers.IO) {
        if (!_isOpen) throw NioException("Socket is closed.")
        if (_isConnected) return@withContext true

        memScoped {
            val error = alloc<IntVar>()
            val errorLen = alloc<socklen_tVar>()
            errorLen.value = sizeOf<IntVar>().convert()

            // Use select/poll to check for writability, which indicates connection success or error
            val fds = alloc<fd_set>()
            FD_ZERO(fds.ptr)
            FD_SET(fd, fds.ptr)
            val timeout = alloc<timeval>()
            timeout.tv_sec = 0
            timeout.tv_usec = 0 // Non-blocking check

            val ret = select(fd + 1, null, fds.ptr, null, timeout.ptr)
            when {
                ret < 0 -> throw NioException("select for finishConnect failed: \${strerror(errno)?.toKString()}")
                ret == 0 -> return@withContext false // Connection still pending
                else -> { // Socket is writable or has an error
                    if (getsockopt(fd, SOL_SOCKET, SO_ERROR, error.ptr, errorLen.ptr) < 0) {
                        throw NioException("getsockopt(SO_ERROR) failed: \${strerror(errno)?.toKString()}")
                    }
                    if (error.value == 0) {
                        _isConnected = true
                        // remoteAddress should have been set by connect if it was immediate,
                        // or we need to get it now. For simplicity, assume it was set or connect needs to return it.
                        // If not, use getsockpeername if needed.
                        return@withContext true
                    } else {
                        throw NioException("Connect failed: \${strerror(error.value)?.toKString()}")
                    }
                }
            }
        }
    }

    actual override suspend fun read(buffer: Tensor<Byte>, offset: Int, length: Int): Int = withContext(Dispatchers.IO) {
        if (!_isOpen || !_isConnected) throw NioException("Socket not open or not connected for read.")
        require(buffer.rank == 1) { "Buffer tensor must be rank 1." }
        require(offset >= 0 && length > 0 && offset + length <= buffer.totalSize) { "Invalid offset/length for buffer." }

        // Create a temporary ByteArray to read into from the Tensor
        // This is inefficient if Tensor is already ByteArray-backed.
        // TensorUtils.tensorToByteArray(buffer, offset, length) would be ideal if it could get underlying array.
        // For now, copy from Tensor to a temp sendable array if Tensor isn't directly a ByteArray.
        // This part depends heavily on the actual Tensor<Byte> implementation and how to get its raw bytes.
        // Placeholder: assuming TensorUtils can copy a slice of Tensor to a new ByteArray.
        // OR, if read needs to populate the Tensor, we read into a temp array then update Tensor.
        val tempByteArray = ByteArray(length)

        val bytesRead = tempByteArray.usePinned { pinned ->
            platform.posix.read(fd, pinned.addressOf(0), length.convert())
        }.toInt()

        when {
            bytesRead < 0 -> {
                if (errno == EAGAIN || errno == EWOULDBLOCK) return@withContext 0 // Non-blocking, no data
                throw NioException("Read failed: \${strerror(errno)?.toKString()}")
            }
            bytesRead == 0 -> return@withContext -1 // EOF
            else -> {
                // Copy data from tempByteArray to Tensor<Byte>
                TensorUtils.updateTensorFromByteArray(buffer, offset, tempByteArray, bytesRead)
                return@withContext bytesRead
            }
        }
    }

    actual override suspend fun write(buffer: Tensor<Byte>, offset: Int, length: Int): Int = withContext(Dispatchers.IO) {
        if (!_isOpen || !_isConnected) throw NioException("Socket not open or not connected for write.")
        require(buffer.rank == 1) { "Buffer tensor must be rank 1." }
        require(offset >= 0 && length > 0 && offset + length <= buffer.totalSize) { "Invalid offset/length for buffer." }

        val dataToWrite = TensorUtils.tensorToByteArray(buffer, offset, length)

        val bytesWritten = dataToWrite.usePinned { pinned ->
            platform.posix.write(fd, pinned.addressOf(0), length.convert())
        }.toInt()

        if (bytesWritten < 0) {
            if (errno == EAGAIN || errno == EWOULDBLOCK) return@withContext 0 // Non-blocking, would block
            throw NioException("Write failed: \${strerror(errno)?.toKString()}")
        }
        return@withContext bytesWritten
    }


    actual override fun isConnected(): Boolean = _isConnected
    actual override fun remoteAddress(): SocketAddress? = _remoteAddress
    actual override fun localAddress(): SocketAddress? {
        if (_localAddress == null && _isOpen) {
            memScoped {
                val addr = alloc<sockaddr_in>()
                val len = alloc<socklen_tVar>()
                len.value = sockaddr_in.size.convert()
                if (getsockname(fd, addr.ptr.reinterpret(), len.ptr) == 0) {
                    _localAddress = addr.toSocketAddress()
                }
            }
        }
        return _localAddress
    }


    actual override fun close() {
        if (_isOpen) {
            _isOpen = false
            _isConnected = false
            platform.posix.close(fd)
            fd = -1
        }
    }

    actual override fun isOpen(): Boolean = _isOpen

    actual override fun configureBlocking(block: Boolean) {
        if (!_isOpen) throw NioException("Socket is closed.")
        val flags = fcntl(fd, F_GETFL, 0)
        if (flags < 0) throw NioException("fcntl(F_GETFL) failed: \${strerror(errno)?.toKString()}")
        val newFlags = if (block) flags and O_NONBLOCK.inv() else flags or O_NONBLOCK
        if (fcntl(fd, F_SETFL, newFlags) < 0) {
            throw NioException("fcntl(F_SETFL) failed: \${strerror(errno)?.toKString()}")
        }
    }
}


// --- TensorUtils Placeholder for Native ---
// This would be in a separate utility file, e.g., borg.trikeshed.core.internal.TensorUtilsNative
// For this subtask, it's included here to make the NioChannelsNative.kt self-contained.
package borg.trikeshed.core.internal

import borg.trikeshed.core.Tensor
import borg.trikeshed.core.TensorConstruct
import kotlinx.cinterop.*

object TensorUtils {
    /**
     * Converts a slice of a Tensor<Byte> (rank 1) to a new ByteArray.
     */
    fun tensorToByteArray(tensor: Tensor<Byte>, offset: Int, length: Int): ByteArray {
        require(tensor.rank == 1) { "Input tensor must be rank 1." }
        require(offset >= 0 && length >= 0 && offset + length <= tensor.shape[0]) { "Invalid offset/length." }

        return ByteArray(length) { i ->
            tensor(intArrayOf(offset + i)) // Access tensor element
        }
    }

    /**
     * Updates a Tensor<Byte> (rank 1) from a ByteArray.
     * This is a conceptual placeholder. See comments in the JVM version.
     * For an immutable Tensor, this would typically mean creating a new Tensor.
     * If `targetTensor` is mutable and backed by an array, one could write to it.
     * For now, this function doesn't actually modify `targetTensor` due to its abstract nature.
     * It implies that the `read` operation in ClientSocketChannel should manage how the
     * read bytes (in `sourceByteArray`) are exposed, possibly by returning them or a new Tensor.
     */
    fun updateTensorFromByteArray(
        targetTensor: Tensor<Byte>, // The Tensor conceptually being updated
        tensorOffset: Int,          // Starting offset in the targetTensor
        sourceByteArray: ByteArray, // ByteArray containing data to "write" into the Tensor
        bytesToCopy: Int            // Number of bytes to copy from sourceByteArray
    ) {
        require(targetTensor.rank == 1) { "Target tensor must be rank 1." }
        require(tensorOffset >= 0 && bytesToCopy >= 0 && tensorOffset + bytesToCopy <= targetTensor.shape[0]) {
            "Invalid offset/length for target tensor."
        }
        require(sourceByteArray.size >= bytesToCopy) { "Source ByteArray does not have enough bytes."}

        // Placeholder: In a real scenario with a mutable Tensor, one would copy
        // `sourceByteArray.copyOfRange(0, bytesToCopy)` into `targetTensor`'s backing store
        // starting at `tensorOffset`.
        // For immutable Tensor, this operation is not directly possible on `targetTensor`.
        // The `read` method would construct a new Tensor from `sourceByteArray.copyOfRange(0, bytesToCopy)`.
        println("TensorUtils.updateTensorFromByteArray: Read $bytesToCopy bytes into a temp array. Logic to update Tensor from this array needs concrete Tensor impl details for native.")
        // For example, if targetTensor had a set method:
        // for (i in 0 until bytesToCopy) {
        //    targetTensor.set(intArrayOf(tensorOffset + i), sourceByteArray[i])
        // }
    }
}
