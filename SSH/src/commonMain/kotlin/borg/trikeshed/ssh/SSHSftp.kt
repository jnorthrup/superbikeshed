package borg.trikeshed.ssh

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * SFTP (SSH File Transfer Protocol) Implementation
 * 
 * Handles file transfer operations over SSH channels including
 * file operations, directory operations, and file attributes.
 */

// SFTP interface
interface SSHSftp {
    suspend fun openChannel(context: SSHChannelContext): SSHChannelID
    suspend fun closeChannel(context: SSHChannelContext)
    suspend fun listDirectory(path: String, context: SSHChannelContext): List<SSHSftpFile>
    suspend fun openFile(path: String, flags: SSHSftpOpenFlags, context: SSHChannelContext): SSHSftpFileHandle
    suspend fun closeFile(handle: SSHSftpFileHandle, context: SSHChannelContext)
    suspend fun readFile(handle: SSHSftpFileHandle, offset: Long, length: UInt, context: SSHChannelContext): Indexed<Byte>
    suspend fun writeFile(handle: SSHSftpFileHandle, offset: Long, data: Indexed<Byte>, context: SSHChannelContext)
    suspend fun createDirectory(path: String, context: SSHChannelContext)
    suspend fun removeDirectory(path: String, context: SSHChannelContext)
    suspend fun removeFile(path: String, context: SSHChannelContext)
    suspend fun renameFile(oldPath: String, newPath: String, context: SSHChannelContext)
    suspend fun getFileAttributes(path: String, context: SSHChannelContext): SSHSftpFileAttributes
    suspend fun setFileAttributes(path: String, attributes: SSHSftpFileAttributes, context: SSHChannelContext)
    suspend fun getFileInfo(path: String, context: SSHChannelContext): SSHSftpFileInfo
}

// SFTP implementation
class SSHSftpImpl(
    internal val channelManager: SSHChannelManager
) : SSHSftp {
    
    internal var sftpChannelId: SSHChannelID? = null
    internal var nextRequestId: UInt = 0u
    internal val openFiles = mutableMapOf<String, SSHSftpFileHandle>()
    
    override suspend fun openChannel(context: SSHChannelContext): SSHChannelID {
        val channelId = channelManager.openChannel(SSHChannelType.SESSION, context)
        sftpChannelId = channelId
        
        // Start SFTP subsystem
        val request = SubsystemRequest("sftp")
        channelManager.handleChannelRequest(request, context)
        
        return channelId
    }
    
    override suspend fun closeChannel(context: SSHChannelContext) {
        sftpChannelId?.let { channelId ->
            channelManager.closeChannel(channelId, context)
            sftpChannelId = null
        }
    }
    
    override suspend fun listDirectory(path: String, context: SSHChannelContext): List<SSHSftpFile> {
        val requestId = nextRequestId++
        val payload = buildReadDirRequest(requestId, path)
        
        // Send request
        sendSftpRequest(payload, context)
        
        // Receive response
        val response = receiveSftpResponse(context)
        
        // Parse file list
        return parseFileList(response)
    }
    
    override suspend fun openFile(path: String, flags: SSHSftpOpenFlags, context: SSHChannelContext): SSHSftpFileHandle {
        val requestId = nextRequestId++
        val payload = buildOpenFileRequest(requestId, path, flags)
        
        // Send request
        sendSftpRequest(payload, context)
        
        // Receive response
        val response = receiveSftpResponse(context)
        
        // Parse file handle
        val handle = parseFileHandle(response)
        val fileHandle = SSHSftpFileHandle(handle, path, flags)
        openFiles[handle] = fileHandle
        
        return fileHandle
    }
    
    override suspend fun closeFile(handle: SSHSftpFileHandle, context: SSHChannelContext) {
        val requestId = nextRequestId++
        val payload = buildCloseFileRequest(requestId, handle.handle)
        
        // Send request
        sendSftpRequest(payload, context)
        
        // Receive response
        receiveSftpResponse(context)
        
        // Remove from open files
        openFiles.remove(handle.handle)
    }
    
    override suspend fun readFile(handle: SSHSftpFileHandle, offset: Long, length: UInt, context: SSHChannelContext): Indexed<Byte> {
        val requestId = nextRequestId++
        val payload = buildReadFileRequest(requestId, handle.handle, offset, length)
        
        // Send request
        sendSftpRequest(payload, context)
        
        // Receive response
        val response = receiveSftpResponse(context)
        
        // Parse data
        return parseFileData(response)
    }
    
    override suspend fun writeFile(handle: SSHSftpFileHandle, offset: Long, data: Indexed<Byte>, context: SSHChannelContext) {
        val requestId = nextRequestId++
        val payload = buildWriteFileRequest(requestId, handle.handle, offset, data)
        
        // Send request
        sendSftpRequest(payload, context)
        
        // Receive response
        receiveSftpResponse(context)
    }
    
    override suspend fun createDirectory(path: String, context: SSHChannelContext) {
        val requestId = nextRequestId++
        val payload = buildMkdirRequest(requestId, path)
        
        // Send request
        sendSftpRequest(payload, context)
        
        // Receive response
        receiveSftpResponse(context)
    }
    
    override suspend fun removeDirectory(path: String, context: SSHChannelContext) {
        val requestId = nextRequestId++
        val payload = buildRmdirRequest(requestId, path)
        
        // Send request
        sendSftpRequest(payload, context)
        
        // Receive response
        receiveSftpResponse(context)
    }
    
    override suspend fun removeFile(path: String, context: SSHChannelContext) {
        val requestId = nextRequestId++
        val payload = buildRemoveRequest(requestId, path)
        
        // Send request
        sendSftpRequest(payload, context)
        
        // Receive response
        receiveSftpResponse(context)
    }
    
    override suspend fun renameFile(oldPath: String, newPath: String, context: SSHChannelContext) {
        val requestId = nextRequestId++
        val payload = buildRenameRequest(requestId, oldPath, newPath)
        
        // Send request
        sendSftpRequest(payload, context)
        
        // Receive response
        receiveSftpResponse(context)
    }
    
    override suspend fun getFileAttributes(path: String, context: SSHChannelContext): SSHSftpFileAttributes {
        val requestId = nextRequestId++
        val payload = buildStatRequest(requestId, path)
        
        // Send request
        sendSftpRequest(payload, context)
        
        // Receive response
        val response = receiveSftpResponse(context)
        
        // Parse attributes
        return parseFileAttributes(response)
    }
    
    override suspend fun setFileAttributes(path: String, attributes: SSHSftpFileAttributes, context: SSHChannelContext) {
        val requestId = nextRequestId++
        val payload = buildSetStatRequest(requestId, path, attributes)
        
        // Send request
        sendSftpRequest(payload, context)
        
        // Receive response
        receiveSftpResponse(context)
    }
    
    override suspend fun getFileInfo(path: String, context: SSHChannelContext): SSHSftpFileInfo {
        val attributes = getFileAttributes(path, context)
        return SSHSftpFileInfo(path, attributes)
    }
    
    // Helper methods
    internal suspend fun sendSftpRequest(payload: SSHPayload, context: SSHChannelContext) {
        val channelId = sftpChannelId ?: throw SSHException("SFTP channel not open")
        val data = payload.size j { i: Int -> payload[i] }
        channelManager.sendChannelData(channelId, data, context)
    }
    
    internal suspend fun receiveSftpResponse(context: SSHChannelContext): SSHPayload {
        val channelId = sftpChannelId ?: throw SSHException("SFTP channel not open")
        val data = channelManager.receiveChannelData(channelId, context)
        return data?.size j { i: Int -> data[i] } ?: throw SSHException("No SFTP response received")
    }
    
    // Request builders
    internal fun buildReadDirRequest(requestId: UInt, path: String): SSHPayload {
        val pathBytes = path.encodeToByteArray()
        val size = 1 + 4 + 4 + pathBytes.size
        
        return size j { i: Int ->
            when {
                i == 0 -> SSHSftpMessageType.READDIR.value
                i < 5 -> ((requestId shr ((4 - i) * 8)) and 0xFFu).toByte()
                i < 9 -> ((pathBytes.size shr ((8 - i) * 8)) and 0xFF).toByte()
                else -> pathBytes[i - 9]
            }
        }
    }
    
    internal fun buildOpenFileRequest(requestId: UInt, path: String, flags: SSHSftpOpenFlags): SSHPayload {
        val pathBytes = path.encodeToByteArray()
        val size = 1 + 4 + 4 + pathBytes.size + 4 + 4
        
        return size j { i: Int ->
            when {
                i == 0 -> SSHSftpMessageType.OPEN.value
                i < 5 -> ((requestId shr ((4 - i) * 8)) and 0xFFu).toByte()
                i < 9 -> ((pathBytes.size shr ((8 - i) * 8)) and 0xFF).toByte()
                i < 9 + pathBytes.size -> pathBytes[i - 9]
                i < 13 + pathBytes.size -> ((flags.value shr ((12 + pathBytes.size - i) * 8)) and 0xFFu).toByte()
                i < 17 + pathBytes.size -> ((0u shr ((16 + pathBytes.size - i) * 8)) and 0xFFu).toByte() // pflags
                else -> 0
            }
        }
    }
    
    internal fun buildCloseFileRequest(requestId: UInt, handle: String): SSHPayload {
        val handleBytes = handle.encodeToByteArray()
        val size = 1 + 4 + 4 + handleBytes.size
        
        return size j { i: Int ->
            when {
                i == 0 -> SSHSftpMessageType.CLOSE.value
                i < 5 -> ((requestId shr ((4 - i) * 8)) and 0xFFu).toByte()
                i < 9 -> ((handleBytes.size shr ((8 - i) * 8)) and 0xFF).toByte()
                else -> handleBytes[i - 9]
            }
        }
    }
    
    internal fun buildReadFileRequest(requestId: UInt, handle: String, offset: Long, length: UInt): SSHPayload {
        val handleBytes = handle.encodeToByteArray()
        val size = 1 + 4 + 4 + handleBytes.size + 8 + 4
        
        return size j { i: Int ->
            when {
                i == 0 -> SSHSftpMessageType.READ.value
                i < 5 -> ((requestId shr ((4 - i) * 8)) and 0xFFu).toByte()
                i < 9 -> ((handleBytes.size shr ((8 - i) * 8)) and 0xFF).toByte()
                i < 9 + handleBytes.size -> handleBytes[i - 9]
                i < 17 + handleBytes.size -> ((offset shr ((16 + handleBytes.size - i) * 8)) and 0xFF).toByte()
                i < 21 + handleBytes.size -> ((length shr ((20 + handleBytes.size - i) * 8)) and 0xFFu).toByte()
                else -> 0
            }
        }
    }
    
    internal fun buildWriteFileRequest(requestId: UInt, handle: String, offset: Long, data: Indexed<Byte>): SSHPayload {
        val handleBytes = handle.encodeToByteArray()
        val size = 1 + 4 + 4 + handleBytes.size + 8 + 4 + data.a
        
        return size j { i: Int ->
            when {
                i == 0 -> SSHSftpMessageType.WRITE.value
                i < 5 -> ((requestId shr ((4 - i) * 8)) and 0xFFu).toByte()
                i < 9 -> ((handleBytes.size shr ((8 - i) * 8)) and 0xFF).toByte()
                i < 9 + handleBytes.size -> handleBytes[i - 9]
                i < 17 + handleBytes.size -> ((offset shr ((16 + handleBytes.size - i) * 8)) and 0xFF).toByte()
                i < 21 + handleBytes.size -> ((data.a shr ((20 + handleBytes.size - i) * 8)) and 0xFFu).toByte()
                else -> data[i - 21 - handleBytes.size]
            }
        }
    }
    
    internal fun buildMkdirRequest(requestId: UInt, path: String): SSHPayload {
        val pathBytes = path.encodeToByteArray()
        val size = 1 + 4 + 4 + pathBytes.size + 4
        
        return size j { i: Int ->
            when {
                i == 0 -> SSHSftpMessageType.MKDIR.value
                i < 5 -> ((requestId shr ((4 - i) * 8)) and 0xFFu).toByte()
                i < 9 -> ((pathBytes.size shr ((8 - i) * 8)) and 0xFF).toByte()
                i < 9 + pathBytes.size -> pathBytes[i - 9]
                i < 13 + pathBytes.size -> ((0 shr ((12 + pathBytes.size - i) * 8)) and 0xFFu).toByte() // flags
                else -> 0
            }
        }
    }
    
    internal fun buildRmdirRequest(requestId: UInt, path: String): SSHPayload {
        val pathBytes = path.encodeToByteArray()
        val size = 1 + 4 + 4 + pathBytes.size
        
        return size j { i: Int ->
            when {
                i == 0 -> SSHSftpMessageType.RMDIR.value
                i < 5 -> ((requestId shr ((4 - i) * 8)) and 0xFFu).toByte()
                i < 9 -> ((pathBytes.size shr ((8 - i) * 8)) and 0xFF).toByte()
                else -> pathBytes[i - 9]
            }
        }
    }
    
    internal fun buildRemoveRequest(requestId: UInt, path: String): SSHPayload {
        val pathBytes = path.encodeToByteArray()
        val size = 1 + 4 + 4 + pathBytes.size
        
        return size j { i: Int ->
            when {
                i == 0 -> SSHSftpMessageType.REMOVE.value
                i < 5 -> ((requestId shr ((4 - i) * 8)) and 0xFFu).toByte()
                i < 9 -> ((pathBytes.size shr ((8 - i) * 8)) and 0xFF).toByte()
                else -> pathBytes[i - 9]
            }
        }
    }
    
    internal fun buildRenameRequest(requestId: UInt, oldPath: String, newPath: String): SSHPayload {
        val oldPathBytes = oldPath.encodeToByteArray()
        val newPathBytes = newPath.encodeToByteArray()
        val size = 1 + 4 + 4 + oldPathBytes.size + 4 + newPathBytes.size
        
        return size j { i: Int ->
            when {
                i == 0 -> SSHSftpMessageType.RENAME.value
                i < 5 -> ((requestId shr ((4 - i) * 8)) and 0xFFu).toByte()
                i < 9 -> ((oldPathBytes.size shr ((8 - i) * 8)) and 0xFF).toByte()
                i < 9 + oldPathBytes.size -> oldPathBytes[i - 9]
                i < 13 + oldPathBytes.size -> ((newPathBytes.size shr ((12 + oldPathBytes.size - i) * 8)) and 0xFF).toByte()
                else -> newPathBytes[i - 13 - oldPathBytes.size]
            }
        }
    }
    
    internal fun buildStatRequest(requestId: UInt, path: String): SSHPayload {
        val pathBytes = path.encodeToByteArray()
        val size = 1 + 4 + 4 + pathBytes.size
        
        return size j { i: Int ->
            when {
                i == 0 -> SSHSftpMessageType.STAT.value
                i < 5 -> ((requestId shr ((4 - i) * 8)) and 0xFFu).toByte()
                i < 9 -> ((pathBytes.size shr ((8 - i) * 8)) and 0xFF).toByte()
                else -> pathBytes[i - 9]
            }
        }
    }
    
    internal fun buildSetStatRequest(requestId: UInt, path: String, attributes: SSHSftpFileAttributes): SSHPayload {
        val pathBytes = path.encodeToByteArray()
        val size = 1 + 4 + 4 + pathBytes.size + 4 + attributes.size
        
        return size j { i: Int ->
            when {
                i == 0 -> SSHSftpMessageType.SETSTAT.value
                i < 5 -> ((requestId shr ((4 - i) * 8)) and 0xFFu).toByte()
                i < 9 -> ((pathBytes.size shr ((8 - i) * 8)) and 0xFF).toByte()
                i < 9 + pathBytes.size -> pathBytes[i - 9]
                i < 13 + pathBytes.size -> ((attributes.size shr ((12 + pathBytes.size - i) * 8)) and 0xFFu).toByte()
                else -> attributes.data[i - 13 - pathBytes.size]
            }
        }
    }
    
    // Response parsers
    internal fun parseFileList(response: SSHPayload): List<SSHSftpFile> {
        // TODO: Parse SFTP file list response
        return emptyList()
    }
    
    internal fun parseFileHandle(response: SSHPayload): String {
        // TODO: Parse SFTP file handle response
        return "handle_${kotlin.random.Random.nextInt()}"
    }
    
    internal fun parseFileData(response: SSHPayload): Indexed<Byte> {
        // TODO: Parse SFTP file data response
        return 0 j { 0.toByte() }
    }
    
    internal fun parseFileAttributes(response: SSHPayload): SSHSftpFileAttributes {
        // TODO: Parse SFTP file attributes response
        return SSHSftpFileAttributes(0 j { 0.toByte() })
    }
}

// SFTP data structures
data class SSHSftpFile(
    val filename: String,
    val longname: String,
    val attributes: SSHSftpFileAttributes
)

data class SSHSftpFileHandle(
    val handle: String,
    val path: String,
    val flags: SSHSftpOpenFlags
)

data class SSHSftpFileAttributes(
    val data: Indexed<Byte>
) {
    val size: Int get() = data.a
}

data class SSHSftpFileInfo(
    val path: String,
    val attributes: SSHSftpFileAttributes
)

enum class SSHSftpOpenFlags(val value: UInt) {
    READ(0x00000001u),
    WRITE(0x00000002u),
    APPEND(0x00000004u),
    CREATE(0x00000008u),
    TRUNCATE(0x00000010u),
    EXCLUSIVE(0x00000020u)
}

enum class SSHSftpMessageType(val value: Byte) {
    INIT(1),
    VERSION(2),
    OPEN(3),
    CLOSE(4),
    READ(5),
    WRITE(6),
    LSTAT(7),
    STAT(8),
    FSTAT(9),
    SETSTAT(10),
    FSETSTAT(11),
    OPENDIR(12),
    READDIR(13),
    REMOVE(14),
    MKDIR(15),
    RMDIR(16),
    REALPATH(17),
    STAT(18),
    RENAME(19),
    READLINK(20),
    SYMLINK(21),
    STATUS(101),
    HANDLE(102),
    DATA(103),
    NAME(104),
    ATTRS(105),
    EXTENDED(200),
    EXTENDED_REPLY(201)
}

// SFTP factory
object SSHSftpFactory {
    fun createSftp(channelManager: SSHChannelManager): SSHSftp {
        return SSHSftpImpl(channelManager)
    }
} 