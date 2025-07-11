package trikeshed.uring

/**
 * Represents the result of a completed asynchronous I/O operation.
 */
data class IoCompletion(
    /**
     * The user-defined data that was passed with the corresponding IoOperation.
     */
    val userData: Long,
    
    /**
     * For read/write/send/recv operations: number of bytes transferred.
     * For fsync: 0 on success.
     * On error: negative value, typically representing -errno.
     */
    val result: Int
)

/**
 * Completion Queue Entry (CQE) - represents the result of completed operations.
 * This is the Kotlin equivalent of io_uring's cqe structure.
 */
sealed class Cqe(
    open val userData: Long,
    open val result: Int
) {
    val isSuccess: Boolean get() = result >= 0
    val isError: Boolean get() = result < 0
    val error: PosixError? get() = if (isError) PosixError.fromErrno(-result) else null
}

// File I/O Completions

data class ReadResult(
    override val userData: Long,
    override val result: Int,
    val bytesRead: Int = if (result >= 0) result else 0
) : Cqe(userData, result)

data class WriteResult(
    override val userData: Long,
    override val result: Int,
    val bytesWritten: Int = if (result >= 0) result else 0
) : Cqe(userData, result)

data class FsyncResult(
    override val userData: Long,
    override val result: Int
) : Cqe(userData, result)

// Network Completions

data class AcceptResult(
    override val userData: Long,
    override val result: Int,
    val clientFd: Int = if (result >= 0) result else -1,
    val clientAddress: SocketAddress? = null
) : Cqe(userData, result)

data class ConnectResult(
    override val userData: Long,
    override val result: Int
) : Cqe(userData, result)

data class SendResult(
    override val userData: Long,
    override val result: Int,
    val bytesSent: Int = if (result >= 0) result else 0
) : Cqe(userData, result)

data class ReceiveResult(
    override val userData: Long,
    override val result: Int,
    val bytesReceived: Int = if (result >= 0) result else 0
) : Cqe(userData, result)

data class CloseResult(
    override val userData: Long,
    override val result: Int
) : Cqe(userData, result)

/**
 * Represents POSIX error codes.
 */
enum class PosixError(val errno: Int) {
    EPERM(1),
    ENOENT(2),
    ESRCH(3),
    EINTR(4),
    EIO(5),
    ENXIO(6),
    E2BIG(7),
    ENOEXEC(8),
    EBADF(9),
    ECHILD(10),
    EAGAIN(11),
    ENOMEM(12),
    EACCES(13),
    EFAULT(14),
    ENOTBLK(15),
    EBUSY(16),
    EEXIST(17),
    EXDEV(18),
    ENODEV(19),
    ENOTDIR(20),
    EISDIR(21),
    EINVAL(22),
    ENFILE(23),
    EMFILE(24),
    ENOTTY(25),
    ETXTBSY(26),
    EFBIG(27),
    ENOSPC(28),
    ESPIPE(29),
    EROFS(30),
    EMLINK(31),
    EPIPE(32),
    EDOM(33),
    ERANGE(34),
    EWOULDBLOCK(35),
    EINPROGRESS(36),
    EALREADY(37),
    ENOTSOCK(38),
    EDESTADDRREQ(39),
    EMSGSIZE(40),
    EPROTOTYPE(41),
    ENOPROTOOPT(42),
    EPROTONOSUPPORT(43),
    ESOCKTNOSUPPORT(44),
    EOPNOTSUPP(45),
    EPFNOSUPPORT(46),
    EAFNOSUPPORT(47),
    EADDRINUSE(48),
    EADDRNOTAVAIL(49),
    ENETDOWN(50),
    ENETUNREACH(51),
    ENETRESET(52),
    ECONNABORTED(53),
    ECONNRESET(54),
    ENOBUFS(55),
    EISCONN(56),
    ENOTCONN(57),
    ESHUTDOWN(58),
    ETOOMANYREFS(59),
    ETIMEDOUT(60),
    ECONNREFUSED(61),
    ELOOP(62),
    ENAMETOOLONG(63),
    EHOSTDOWN(64),
    EHOSTUNREACH(65),
    ENOTEMPTY(66),
    EPROCLIM(67),
    EUSERS(68),
    EDQUOT(69),
    ESTALE(70),
    EREMOTE(71),
    EBADRPC(72),
    ERPCMISMATCH(73),
    EPROGUNAVAIL(74),
    EPROGMISMATCH(75),
    EPROCUNAVAIL(76),
    ENOLCK(77),
    ENOSYS(78),
    EFTYPE(79),
    EAUTH(80),
    ENEEDAUTH(81),
    EIDRM(82),
    ENOMSG(83),
    EOVERFLOW(84),
    ECANCELED(85),
    EILSEQ(86),
    ENOATTR(87),
    EDOOFUS(88),
    EBADMSG(89),
    EMULTIHOP(90),
    ENOLINK(91),
    EPROTO(92),
    ENOTCAPABLE(93),
    ECAPMODE(94),
    ENOTRECOVERABLE(95),
    EOWNERDEAD(96);
    
    companion object {
        private val errorMap = values().associateBy { it.errno }
        
        fun fromErrno(errno: Int): PosixError? = errorMap[errno]
    }
}