package com.superbikeshed.trikeshed.native

import com.superbikeshed.trikeshed.native.uring.*
import kotlinx.cinterop.*
import platform.posix.stat
import platform.posix.S_IFREG

// Wrapper functions for liburing operations.
// These will replace the TODO() stubs or provide a cleaner API.

/**
 * Prepares a read operation.
 * Assumes `sqe` is a valid submission queue entry.
 * `fd` is the file descriptor.
 * `buf` is the buffer to read into.
 * `len` is the number of bytes to read.
 * `offset` is the file offset.
 */
inline fun io_uring_prep_read_wrapper(sqe: CPointer<io_uring_sqe>, fd: Int, buf: CValuesRef<ByteVarOf<Byte>>, len: UInt, offset: ULong) {
    io_uring_prep_read(sqe, fd, buf, len, offset)
}

/**
 * Prepares a write operation.
 * Assumes `sqe` is a valid submission queue entry.
 * `fd` is the file descriptor.
 * `buf` is the buffer to write from.
 * `len` is the number of bytes to write.
 * `offset` is the file offset.
 */
inline fun io_uring_prep_write_wrapper(sqe: CPointer<io_uring_sqe>, fd: Int, buf: CValuesRef<ByteVarOf<Byte>>, len: UInt, offset: ULong) {
    io_uring_prep_write(sqe, fd, buf, len, offset)
}

/**
 * Prepares a readv operation.
 * `iov` is an array of iovec structures.
 * `nr_vecs` is the number of iovec structures.
 */
inline fun io_uring_prep_readv_wrapper(sqe: CPointer<io_uring_sqe>, fd: Int, iov: CValuesRef<iovec>, nr_vecs: Int, offset: ULong) {
    io_uring_prep_readv(sqe, fd, iov, nr_vecs, offset)
}

/**
 * Prepares a writev operation.
 * `iov` is an array of iovec structures.
 * `nr_vecs` is the number of iovec structures.
 */
inline fun io_uring_prep_writev_wrapper(sqe: CPointer<io_uring_sqe>, fd: Int, iov: CValuesRef<iovec>, nr_vecs: Int, offset: ULong) {
    io_uring_prep_writev(sqe, fd, iov, nr_vecs, offset)
}

/**
 * Prepares an fsync operation.
 */
inline fun io_uring_prep_fsync_wrapper(sqe: CPointer<io_uring_sqe>, fd: Int, fsync_flags: UInt) {
    io_uring_prep_fsync(sqe, fd, fsync_flags)
}

/**
 * Prepares a generic openat operation.
 * `dfd` is the directory file descriptor.
 * `path` is the file path.
 * `flags` are the open flags.
 * `mode` is the file mode.
 */
inline fun io_uring_prep_openat_wrapper(sqe: CPointer<io_uring_sqe>, dfd: Int, path: String, flags: Int, mode: UInt) {
    io_uring_prep_openat(sqe, dfd, path, flags, mode)
}

/**
 * Prepares a close operation.
 */
inline fun io_uring_prep_close_wrapper(sqe: CPointer<io_uring_sqe>, fd: Int) {
    io_uring_prep_close(sqe, fd)
}

/**
 * Prepares a statx operation.
 * `dfd` is the directory file descriptor.
 * `path` is the file path.
 * `flags` are the statx flags.
 * `mask` is the statx mask.
 * `statxbuf` is a pointer to the statx buffer.
 */
inline fun io_uring_prep_statx_wrapper(
    sqe: CPointer<io_uring_sqe>,
    dfd: Int,
    path: String,
    flags: Int,
    mask: UInt,
    statxbuf: CValuesRef<statx>
) {
    io_uring_prep_statx(sqe, dfd, path, flags, mask, statxbuf)
}

/**
 * Helper to get file size using stat.
 * Returns -1 if not a regular file or error.
 */
fun get_file_size(fd: Int): Long = memScoped {
    val st = alloc<stat>()
    if (platform.posix.fstat(fd, st.ptr) != 0) {
        return -1L
    }
    return if ((st.st_mode.toInt() and S_IFREG) != 0) st.st_size else -1L
}

/**
 * Prepares a socket operation.
 * `domain` is the communication domain (e.g., AF_INET).
 * `type` is the communication semantics (e.g., SOCK_STREAM).
 * `protocol` is the protocol to be used (e.g., IPPROTO_TCP).
 * `flags` are additional flags for the socket.
 */
inline fun io_uring_prep_socket_wrapper(sqe: CPointer<io_uring_sqe>, domain: Int, type: Int, protocol: Int, flags: UInt) {
    io_uring_prep_socket(sqe, domain, type, protocol, flags)
}

/**
 * Prepares a connect operation.
 * `fd` is the socket file descriptor.
 * `addr` is a pointer to the sockaddr structure.
 * `addrlen` is the length of the sockaddr structure.
 */
inline fun io_uring_prep_connect_wrapper(sqe: CPointer<io_uring_sqe>, fd: Int, addr: CValuesRef<sockaddr>, addrlen: socklen_t) {
    io_uring_prep_connect(sqe, fd, addr, addrlen)
}

/**
 * Prepares a send operation.
 * `sockfd` is the socket file descriptor.
 * `buf` is the buffer containing the data to send.
 * `len` is the length of the data in the buffer.
 * `flags` are flags for the send operation.
 */
inline fun io_uring_prep_send_wrapper(sqe: CPointer<io_uring_sqe>, sockfd: Int, buf: CValuesRef<ByteVarOf<Byte>>, len: size_t, flags: Int) {
    io_uring_prep_send(sqe, sockfd, buf, len, flags)
}

/**
 * Prepares a recv operation.
 * `sockfd` is the socket file descriptor.
 * `buf` is the buffer to receive data into.
 * `len` is the length of the buffer.
 * `flags` are flags for the recv operation.
 */
inline fun io_uring_prep_recv_wrapper(sqe: CPointer<io_uring_sqe>, sockfd: Int, buf: CValuesRef<ByteVarOf<Byte>>, len: size_t, flags: Int) {
    io_uring_prep_recv(sqe, sockfd, buf, len, flags)
}

/**
 * Prepares a multishot accept operation.
 * `fd` is the listening socket file descriptor.
 * `addr` is a pointer to store the client's address.
 * `addrlen` is a pointer to store the length of the client's address.
 * `flags` are flags for the accept operation.
 */
inline fun io_uring_prep_multishot_accept_wrapper(sqe: CPointer<io_uring_sqe>, fd: Int, addr: CValuesRef<sockaddr>?, addrlen: CValuesRef<socklen_tVar>?, flags: Int) {
    io_uring_prep_multishot_accept_direct(sqe, fd, addr, addrlen, flags)
}

/**
 * Prepares a sendmsg operation.
 * `fd` is the socket file descriptor.
 * `msg` is a pointer to the msghdr structure.
 * `flags` are flags for the sendmsg operation.
 */
inline fun io_uring_prep_sendmsg_wrapper(sqe: CPointer<io_uring_sqe>, fd: Int, msg: CValuesRef<msghdr>, flags: Int) {
    io_uring_prep_sendmsg(sqe, fd, msg, flags)
}

/**
 * Prepares a recvmsg operation.
 * `fd` is the socket file descriptor.
 * `msg` is a pointer to the msghdr structure.
 * `flags` are flags for the recvmsg operation.
 */
inline fun io_uring_prep_recvmsg_wrapper(sqe: CPointer<io_uring_sqe>, fd: Int, msg: CValuesRef<msghdr>, flags: UInt) {
    io_uring_prep_recvmsg(sqe, fd, msg, flags)
}

// Note: The actual liburing functions (e.g., io_uring_prep_read) are expected to be
// available through the cinterop bindings generated from uring.def.
// These wrappers ensure they are inlined and provide a slightly more Kotlin-idiomatic way
// to call them if needed, though direct calls to cinterop functions are also fine.
// The main purpose here is to centralize the functions we intend to use from liburing.
// Adding more functions here as they are needed by the launcher or other components.
// For example, operations for timeouts, linking SQEs, etc.
//
// It is crucial that the `uring.def` file correctly generates bindings for all these
// `io_uring_prep_*` functions. If any are missing, the cinterop tool might not have
// found them in `liburing.h` or they might be macros that need special handling
// in the .def file (e.g. using `compilerOpts` to define them or manually defining wrappers).

// Example of how to use io_uring_get_sqe, submit, and wait_cqe (conceptual)
/*
fun exampleUsage(ring: CPointer<io_uring>, fd: Int, data: ByteArray, offset: ULong) = memScoped {
    val sqe: CPointer<io_uring_sqe> = io_uring_get_sqe(ring) ?: error("SQE unavailable")

    val buffer = data.toCValues()
    io_uring_prep_write_wrapper(sqe, fd, buffer, data.size.toUInt(), offset)

    // Set user data if needed: sqe.pointed.user_data = some_value

    val ret = io_uring_submit(ring)
    if (ret < 0) {
        error("io_uring_submit failed: $ret")
    }

    val cqe = alloc<CPointerVar<io_uring_cqe>>()
    val wait_ret = io_uring_wait_cqe(ring, cqe.ptr)
    if (wait_ret < 0) {
        error("io_uring_wait_cqe failed: $wait_ret")
    }

    val cqe_ptr = cqe.value ?: error("CQE pointer is null")
    val result = cqe_ptr.pointed.res
    // Handle result, check for errors (result < 0)

    io_uring_cqe_seen(ring, cqe_ptr)
}
*/

// It's important to handle the lifecycle of the io_uring instance:
// 1. io_uring_queue_init or io_uring_queue_init_params
// 2. Operations (get_sqe, prep_*, submit, wait_cqe, cqe_seen)
// 3. io_uring_queue_exit
// This lifecycle management will be part of the PlatformLauncher or a dedicated UringManager class.
