package borg.trikeshed.ssh

import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * SSH Core Types and Dependencies
 * 
 * Provides the missing core types and dependencies needed for SSH implementation
 * to compile and function properly.
 */

// Core type aliases to replace missing borg.trikeshed.lib types
typealias Indexed<T> = List<T>
typealias Join<A, B> = Pair<A, B>

// Extension functions to replace missing operators
fun <T> Int.j(init: (Int) -> T): Indexed<T> {
    return (0 until this).map { init(it) }
}

fun <T> Indexed<T>.a(): Int = this.size
fun <T> Indexed<T>.b(): T = this.first()

// Channel data type
data class ChannelData(
    val a: Int,
    val data: ByteArray
) {
    operator fun get(index: Int): Byte = data[index]
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ChannelData
        return a == other.a && data.contentEquals(other.data)
    }
    override fun hashCode(): Int {
        var result = a
        result = 31 * result + data.contentHashCode()
        return result
    }
}

// SSH Channel Request types
sealed class SSHChannelRequest {
    abstract val requestType: String
}

class SubsystemRequest(val subsystem: String) : SSHChannelRequest() {
    override val requestType = "subsystem"
}

// SSH Constants
object SSHConstants {
    const val INITIAL_WINDOW_SIZE: UInt = 2097152u // 2MB
    const val MAX_PACKET_SIZE: UInt = 32768u // 32KB
    const val DEFAULT_PORT: Int = 22
}

// SSH Channel State
enum class SSHChannelState {
    OPENING,
    OPEN,
    CLOSING,
    CLOSED
}

// SSH Connection State
enum class SSHConnectionState {
    INIT,
    CONNECTING,
    CONNECTED,
    AUTHENTICATING,
    AUTHENTICATED,
    DISCONNECTED
}

// SSH Channel ID
typealias SSHChannelID = UInt

// SSH Channel
data class SSHChannel(
    val id: SSHChannelID,
    val type: String,
    var state: SSHChannelState = SSHChannelState.OPENING,
    val dataBuffer: MutableList<Byte> = mutableListOf()
)

// SSH Connection
data class SSHConnection(
    val host: String,
    val port: Int,
    var isConnected: Boolean = false,
    var isAuthenticated: Boolean = false,
    var state: SSHConnectionState = SSHConnectionState.INIT,
    val channels: MutableMap<SSHChannelID, SSHChannel> = mutableMapOf(),
    var nextChannelId: SSHChannelID = 0u
)

// SSH Exception
class SSHException(message: String) : Exception(message)

// Context types
data class SSHConnectionContext(
    val b: CoroutineContext = Dispatchers.IO
)

data class SSHScpContext(
    val b: CoroutineContext = Dispatchers.IO
)

data class SSHSftpContext(
    val b: CoroutineContext = Dispatchers.IO
)

data class SSHRsyncContext(
    val b: CoroutineContext = Dispatchers.IO
)

data class SSHWorkflowContext(
    val b: CoroutineContext = Dispatchers.IO
)

// SFTP specific types
enum class SSHSftpOpenFlags(val value: UInt) {
    READ(0x00000001u),
    WRITE(0x00000002u),
    APPEND(0x00000004u),
    CREATE(0x00000008u),
    TRUNCATE(0x00000010u),
    EXCLUSIVE(0x00000020u)
}

data class SSHSftpFileAttributes(
    val data: Indexed<Byte>
) {
    val size: Int get() = data.size
}

data class SSHSftpFileHandle(
    val handle: String,
    val path: String,
    val flags: SSHSftpOpenFlags
)

// File System interface
interface SSHFileSystem {
    fun readFile(path: String): FileInfo?
    fun writeFile(path: String, content: ByteArray): Boolean
    fun fileExists(path: String): Boolean
    fun deleteFile(path: String): Boolean
    fun listFiles(directory: String): List<String>
}

// File Info
data class FileInfo(
    val path: String,
    val content: ByteArray,
    val size: Int = content.size
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FileInfo
        return path == other.path && content.contentEquals(other.content)
    }
    override fun hashCode(): Int {
        var result = path.hashCode()
        result = 31 * result + content.contentHashCode()
        return result
    }
}

// Transfer Method enum
enum class TransferMethod {
    SCP, SFTP, RSYNC
}

// Transfer Result
data class TransferResult(
    val success: Boolean,
    val sourcePath: String,
    val destinationPath: String,
    val method: TransferMethod,
    val output: String = "",
    val errorMessage: String? = null
)

// Sync Result
data class SyncResult(
    val success: Boolean,
    val sourcePath: String,
    val destinationPath: String,
    val filesTransferred: Int = 0,
    val bytesTransferred: Long = 0L,
    val output: String = "",
    val errorMessage: String? = null
) 