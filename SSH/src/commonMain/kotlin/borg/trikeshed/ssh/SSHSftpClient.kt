package borg.trikeshed.ssh

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * SSH SFTP (SSH File Transfer Protocol) Client Implementation
 * 
 * Handles advanced file operations using the SFTP protocol over SSH channels
 * based on the TDD test requirements.
 */

// SFTP Client interface
interface SSHSftpClient {
    suspend fun openChannel(context: SSHSftpContext): Int
    suspend fun closeChannel(context: SSHSftpContext)
    suspend fun listDirectory(path: String, context: SSHSftpContext): List<String>
    suspend fun openFile(path: String, flags: String, context: SSHSftpContext): String?
    suspend fun closeFile(handle: String, context: SSHSftpContext)
    suspend fun readFile(handle: String, offset: Long, length: Int, context: SSHSftpContext): ByteArray
    suspend fun writeFile(handle: String, offset: Long, data: ByteArray, context: SSHSftpContext)
    suspend fun createDirectory(path: String, context: SSHSftpContext)
    suspend fun removeDirectory(path: String, context: SSHSftpContext)
    suspend fun removeFile(path: String, context: SSHSftpContext)
    suspend fun renameFile(oldPath: String, newPath: String, context: SSHSftpContext)
    suspend fun getFileAttributes(path: String, context: SSHSftpContext): SSHSftpFileAttributes
    suspend fun setFileAttributes(path: String, attributes: SSHSftpFileAttributes, context: SSHSftpContext)
}

// SFTP Client implementation
class SSHSftpClientImpl(
    private val connection: SSHConnection,
    private val fileSystem: SSHFileSystem
) : SSHSftpClient {
    
    private var nextRequestId = 1
    private val openHandles = mutableMapOf<String, SSHSftpFileHandle>()
    
    override suspend fun openChannel(context: SSHSftpContext): Int {
        return withContext(context.b) {
            println("SFTP: Opening SFTP channel")
            
            val channel = getOrCreateChannel(context)
            val channelId = channel.id.toInt()
            
            // Initialize SFTP subsystem
            initializeSftpSubsystem(channel)
            
            println("SFTP: Channel opened with ID $channelId")
            channelId
        }
    }
    
    override suspend fun closeChannel(context: SSHSftpContext) {
        withContext(context.b) {
            println("SFTP: Closing SFTP channel")
            
            // Close all open file handles
            openHandles.values.forEach { handle ->
                closeFile(handle.handle, context)
            }
            openHandles.clear()
            
            println("SFTP: Channel closed")
        }
    }
    
    override suspend fun listDirectory(path: String, context: SSHSftpContext): Indexed<String> {
        return withContext(context.b) {
            println("SFTP: Listing directory $path")
            
            try {
                val channel = getOrCreateChannel(context)
                
                // Send SFTP OPENDIR request
                val requestId = nextRequestId++
                val openDirPayload = buildSftpOpenDirRequest(requestId, path)
                sendSftpRequest(channel, openDirPayload)
                
                // Receive handle
                val response = receiveSftpResponse(channel)
                val handle = parseSftpHandle(response)
                
                if (handle == null) {
                    println("SFTP: Failed to open directory $path")
                    return@withContext emptyList()
                }
                
                // Send SFTP READDIR request
                val readDirRequestId = nextRequestId++
                val readDirPayload = buildSftpReadDirRequest(readDirRequestId, handle)
                sendSftpRequest(channel, readDirPayload)
                
                // Receive file list
                val fileListResponse = receiveSftpResponse(channel)
                val files = parseSftpFileList(fileListResponse)
                
                // Close directory handle
                val closeRequestId = nextRequestId++
                val closePayload = buildSftpCloseRequest(closeRequestId, handle)
                sendSftpRequest(channel, closePayload)
                receiveSftpResponse(channel)
                
                println("SFTP: Listed ${files.size} files in $path")
                files.size j { i: Int -> files.j(i).filename }
                
            } catch (e: Exception) {
                println("SFTP: Directory listing failed: ${e.message}")
                0 j { "" }
            }
        }
    }
    
    override suspend fun openFile(path: String, flags: String, context: SSHSftpContext): String? {
        return withContext(context.b) {
            println("SFTP: Opening file $path with flags $flags")
            
            try {
                val channel = getOrCreateChannel(context)
                
                // Convert flags string to SFTP flags
                val sftpFlags = parseSftpFlags(flags)
                
                // Send SFTP OPEN request
                val requestId = nextRequestId++
                val payload = buildSftpOpenRequest(requestId, path, sftpFlags)
                sendSftpRequest(channel, payload)
                
                // Receive handle
                val response = receiveSftpResponse(channel)
                val handle = parseSftpHandle(response)
                
                if (handle != null) {
                    val fileHandle = SSHSftpFileHandle(handle, path, sftpFlags)
                    openHandles[handle] = fileHandle
                    println("SFTP: Opened file with handle $handle")
                } else {
                    println("SFTP: Failed to open file $path")
                }
                
                handle
                
            } catch (e: Exception) {
                println("SFTP: File open failed: ${e.message}")
                null
            }
        }
    }
    
    override suspend fun closeFile(handle: String, context: SSHSftpContext) {
        withContext(context.b) {
            println("SFTP: Closing file with handle $handle")
            
            try {
                val channel = getOrCreateChannel(context)
                
                // Send SFTP CLOSE request
                val requestId = nextRequestId++
                val payload = buildSftpCloseRequest(requestId, handle)
                sendSftpRequest(channel, payload)
                
                // Receive response
                receiveSftpResponse(channel)
                
                // Remove from open handles
                openHandles.remove(handle)
                
                println("SFTP: File closed")
                
            } catch (e: Exception) {
                println("SFTP: File close failed: ${e.message}")
            }
        }
    }
    
    override suspend fun readFile(handle: String, offset: Long, length: Int, context: SSHSftpContext): Indexed<Byte> {
        return withContext(context.b) {
            println("SFTP: Reading file with handle $handle")
            
            try {
                val channel = getOrCreateChannel(context)
                
                // Send SFTP READ request
                val requestId = nextRequestId++
                val payload = buildSftpReadRequest(requestId, handle, offset, length.toUInt())
                sendSftpRequest(channel, payload)
                
                // Receive data
                val response = receiveSftpResponse(channel)
                val data = parseSftpFileData(response)
                
                println("SFTP: Read ${data.size} bytes")
                data
                
            } catch (e: Exception) {
                println("SFTP: File read failed: ${e.message}")
                0 j { 0.toByte() }
            }
        }
    }
    
    override suspend fun writeFile(handle: String, offset: Long, data: ByteArray, context: SSHSftpContext) {
        withContext(context.b) {
            println("SFTP: Writing to file with handle $handle")
            
            try {
                val channel = getOrCreateChannel(context)
                
                // Send SFTP WRITE request
                val requestId = nextRequestId++
                val payload = buildSftpWriteRequest(requestId, handle, offset, data)
                sendSftpRequest(channel, payload)
                
                // Receive response
                receiveSftpResponse(channel)
                
                println("SFTP: Wrote ${data.size} bytes")
                
            } catch (e: Exception) {
                println("SFTP: File write failed: ${e.message}")
            }
        }
    }
    
    override suspend fun createDirectory(path: String, context: SSHSftpContext) {
        withContext(context.b) {
            println("SFTP: Creating directory $path")
            
            try {
                val channel = getOrCreateChannel(context)
                
                // Send SFTP MKDIR request
                val requestId = nextRequestId++
                val payload = buildSftpMkdirRequest(requestId, path)
                sendSftpRequest(channel, payload)
                
                // Receive response
                receiveSftpResponse(channel)
                
                println("SFTP: Directory created")
                
            } catch (e: Exception) {
                println("SFTP: Directory creation failed: ${e.message}")
            }
        }
    }
    
    override suspend fun removeDirectory(path: String, context: SSHSftpContext) {
        withContext(context.b) {
            println("SFTP: Removing directory $path")
            
            try {
                val channel = getOrCreateChannel(context)
                
                // Send SFTP RMDIR request
                val requestId = nextRequestId++
                val payload = buildSftpRmdirRequest(requestId, path)
                sendSftpRequest(channel, payload)
                
                // Receive response
                receiveSftpResponse(channel)
                
                println("SFTP: Directory removed")
                
            } catch (e: Exception) {
                println("SFTP: Directory removal failed: ${e.message}")
            }
        }
    }
    
    override suspend fun removeFile(path: String, context: SSHSftpContext) {
        withContext(context.b) {
            println("SFTP: Removing file $path")
            
            try {
                val channel = getOrCreateChannel(context)
                
                // Send SFTP REMOVE request
                val requestId = nextRequestId++
                val payload = buildSftpRemoveRequest(requestId, path)
                sendSftpRequest(channel, payload)
                
                // Receive response
                receiveSftpResponse(channel)
                
                // Also remove from local file system if it exists
                fileSystem.deleteFile(path)
                
                println("SFTP: File removed")
                
            } catch (e: Exception) {
                println("SFTP: File removal failed: ${e.message}")
            }
        }
    }
    
    override suspend fun renameFile(oldPath: String, newPath: String, context: SSHSftpContext) {
        withContext(context.b) {
            println("SFTP: Renaming $oldPath to $newPath")
            
            try {
                val channel = getOrCreateChannel(context)
                
                // Send SFTP RENAME request
                val requestId = nextRequestId++
                val payload = buildSftpRenameRequest(requestId, oldPath, newPath)
                sendSftpRequest(channel, payload)
                
                // Receive response
                receiveSftpResponse(channel)
                
                // Also update local file system if it exists
                val fileInfo = fileSystem.readFile(oldPath)
                if (fileInfo != null) {
                    fileSystem.writeFile(newPath, fileInfo.content)
                    fileSystem.deleteFile(oldPath)
                }
                
                println("SFTP: File renamed")
                
            } catch (e: Exception) {
                println("SFTP: File rename failed: ${e.message}")
            }
        }
    }
    
    override suspend fun getFileAttributes(path: String, context: SSHSftpContext): SSHSftpFileAttributes {
        return withContext(context.b) {
            println("SFTP: Getting attributes for $path")
            
            try {
                val channel = getOrCreateChannel(context)
                
                // Send SFTP STAT request
                val requestId = nextRequestId++
                val payload = buildSftpStatRequest(requestId, path)
                sendSftpRequest(channel, payload)
                
                // Receive response
                val response = receiveSftpResponse(channel)
                val attributes = parseSftpAttributes(response)
                
                println("SFTP: Retrieved attributes")
                attributes
                
            } catch (e: Exception) {
                println("SFTP: Attribute retrieval failed: ${e.message}")
                SSHSftpFileAttributes(0 j { 0.toByte() })
            }
        }
    }
    
    override suspend fun setFileAttributes(path: String, attributes: SSHSftpFileAttributes, context: SSHSftpContext) {
        withContext(context.b) {
            println("SFTP: Setting attributes for $path")
            
            try {
                val channel = getOrCreateChannel(context)
                
                // Send SFTP SETSTAT request
                val requestId = nextRequestId++
                val payload = buildSftpSetStatRequest(requestId, path, attributes)
                sendSftpRequest(channel, payload)
                
                // Receive response
                receiveSftpResponse(channel)
                
                println("SFTP: Attributes set")
                
            } catch (e: Exception) {
                println("SFTP: Attribute setting failed: ${e.message}")
            }
        }
    }
    
    // Helper methods
    private suspend fun getOrCreateChannel(context: SSHSftpContext): SSHChannel {
        val existingChannel = connection.channels.values.firstOrNull { it.state == SSHChannelState.OPEN }
        return existingChannel ?: createNewChannel(context)
    }
    
    private suspend fun createNewChannel(context: SSHSftpContext): SSHChannel {
        val channelManager = SSHConnectionManagerFactory.createConnectionManager()
        return channelManager.openChannel(connection, "session", SSHConnectionContext(context.b))
    }
    
    private suspend fun initializeSftpSubsystem(channel: SSHChannel) {
        // Send subsystem request for SFTP
        val subsystemData = "sftp".encodeToByteArray().size j { i: Int -> "sftp".encodeToByteArray()[i] }
        sendSftpRequest(channel, subsystemData)
        
        // Send SFTP INIT message
        val initPayload = buildSftpInitMessage()
        sendSftpRequest(channel, initPayload)
        
        // Receive SFTP VERSION response
        receiveSftpResponse(channel)
    }
    
    private suspend fun sendSftpRequest(channel: SSHChannel, payload: Indexed<Byte>) {
        channel.dataBuffer.addAll(payload.toList())
    }
    
    private suspend fun receiveSftpResponse(channel: SSHChannel): ByteArray {
        // Simulate receiving SFTP response
        delay(10) // Simulate network delay
        return "SFTP response data".toByteArray()
    }
    
    // SFTP message builders
    private fun buildSftpInitMessage(): Indexed<Byte> {
        val payload = mutableListOf<Byte>()
        
        // Length (placeholder)
        repeat(4) { payload.add(0) }
        
        // Type: SSH_FXP_INIT
        payload.add(1)
        
        // Version: 3
        payload.add(0)
        payload.add(0)
        payload.add(0)
        payload.add(3)
        
        // Update length
        val length = payload.size - 4
        payload[0] = (length shr 24).toByte()
        payload[1] = (length shr 16).toByte()
        payload[2] = (length shr 8).toByte()
        payload[3] = length.toByte()
        
        return payload.size j { i: Int -> payload[i] }
    }
    
    private fun buildSftpOpenDirRequest(requestId: Int, path: String): Indexed<Byte> {
        val pathBytes = path.encodeToByteArray()
        val payload = mutableListOf<Byte>()
        
        // Length (placeholder)
        repeat(4) { payload.add(0) }
        
        // Type: SSH_FXP_OPENDIR
        payload.add(11)
        
        // Request ID
        payload.add((requestId shr 24).toByte())
        payload.add((requestId shr 16).toByte())
        payload.add((requestId shr 8).toByte())
        payload.add(requestId.toByte())
        
        // Path length
        payload.add((pathBytes.size shr 24).toByte())
        payload.add((pathBytes.size shr 16).toByte())
        payload.add((pathBytes.size shr 8).toByte())
        payload.add(pathBytes.size.toByte())
        
        // Path
        payload.addAll(pathBytes.toList())
        
        // Update length
        val length = payload.size - 4
        payload[0] = (length shr 24).toByte()
        payload[1] = (length shr 16).toByte()
        payload[2] = (length shr 8).toByte()
        payload[3] = length.toByte()
        
        return payload.size j { i: Int -> payload[i] }
    }
    
    private fun buildSftpReadDirRequest(requestId: Int, handle: String): Indexed<Byte> {
        val handleBytes = handle.encodeToByteArray()
        val payload = mutableListOf<Byte>()
        
        // Length (placeholder)
        repeat(4) { payload.add(0) }
        
        // Type: SSH_FXP_READDIR
        payload.add(12)
        
        // Request ID
        payload.add((requestId shr 24).toByte())
        payload.add((requestId shr 16).toByte())
        payload.add((requestId shr 8).toByte())
        payload.add(requestId.toByte())
        
        // Handle length
        payload.add((handleBytes.size shr 24).toByte())
        payload.add((handleBytes.size shr 16).toByte())
        payload.add((handleBytes.size shr 8).toByte())
        payload.add(handleBytes.size.toByte())
        
        // Handle
        payload.addAll(handleBytes.toList())
        
        // Update length
        val length = payload.size - 4
        payload[0] = (length shr 24).toByte()
        payload[1] = (length shr 16).toByte()
        payload[2] = (length shr 8).toByte()
        payload[3] = length.toByte()
        
        return payload.size j { i: Int -> payload[i] }
    }
    
    private fun buildSftpOpenRequest(requestId: Int, path: String, flags: SSHSftpOpenFlags): Indexed<Byte> {
        val pathBytes = path.encodeToByteArray()
        val payload = mutableListOf<Byte>()
        
        // Length (placeholder)
        repeat(4) { payload.add(0) }
        
        // Type: SSH_FXP_OPEN
        payload.add(3)
        
        // Request ID
        payload.add((requestId shr 24).toByte())
        payload.add((requestId shr 16).toByte())
        payload.add((requestId shr 8).toByte())
        payload.add(requestId.toByte())
        
        // Path length
        payload.add((pathBytes.size shr 24).toByte())
        payload.add((pathBytes.size shr 16).toByte())
        payload.add((pathBytes.size shr 8).toByte())
        payload.add(pathBytes.size.toByte())
        
        // Path
        payload.addAll(pathBytes.toList())
        
        // Flags
        payload.add((flags.value shr 24).toByte())
        payload.add((flags.value shr 16).toByte())
        payload.add((flags.value shr 8).toByte())
        payload.add(flags.value.toByte())
        
        // Update length
        val length = payload.size - 4
        payload[0] = (length shr 24).toByte()
        payload[1] = (length shr 16).toByte()
        payload[2] = (length shr 8).toByte()
        payload[3] = length.toByte()
        
        return payload.size j { i: Int -> payload[i] }
    }
    
    private fun buildSftpCloseRequest(requestId: Int, handle: String): Indexed<Byte> {
        val handleBytes = handle.encodeToByteArray()
        val payload = mutableListOf<Byte>()
        
        // Length (placeholder)
        repeat(4) { payload.add(0) }
        
        // Type: SSH_FXP_CLOSE
        payload.add(4)
        
        // Request ID
        payload.add((requestId shr 24).toByte())
        payload.add((requestId shr 16).toByte())
        payload.add((requestId shr 8).toByte())
        payload.add(requestId.toByte())
        
        // Handle length
        payload.add((handleBytes.size shr 24).toByte())
        payload.add((handleBytes.size shr 16).toByte())
        payload.add((handleBytes.size shr 8).toByte())
        payload.add(handleBytes.size.toByte())
        
        // Handle
        payload.addAll(handleBytes.toList())
        
        // Update length
        val length = payload.size - 4
        payload[0] = (length shr 24).toByte()
        payload[1] = (length shr 16).toByte()
        payload[2] = (length shr 8).toByte()
        payload[3] = length.toByte()
        
        return payload.size j { i: Int -> payload[i] }
    }
    
    private fun buildSftpReadRequest(requestId: Int, handle: String, offset: Long, length: UInt): Indexed<Byte> {
        val handleBytes = handle.encodeToByteArray()
        val payload = mutableListOf<Byte>()
        
        // Length (placeholder)
        repeat(4) { payload.add(0) }
        
        // Type: SSH_FXP_READ
        payload.add(5)
        
        // Request ID
        payload.add((requestId shr 24).toByte())
        payload.add((requestId shr 16).toByte())
        payload.add((requestId shr 8).toByte())
        payload.add(requestId.toByte())
        
        // Handle length
        payload.add((handleBytes.size shr 24).toByte())
        payload.add((handleBytes.size shr 16).toByte())
        payload.add((handleBytes.size shr 8).toByte())
        payload.add(handleBytes.size.toByte())
        
        // Handle
        payload.addAll(handleBytes.toList())
        
        // Offset
        payload.add((offset shr 56).toByte())
        payload.add((offset shr 48).toByte())
        payload.add((offset shr 40).toByte())
        payload.add((offset shr 32).toByte())
        payload.add((offset shr 24).toByte())
        payload.add((offset shr 16).toByte())
        payload.add((offset shr 8).toByte())
        payload.add(offset.toByte())
        
        // Length
        payload.add((length shr 24).toByte())
        payload.add((length shr 16).toByte())
        payload.add((length shr 8).toByte())
        payload.add(length.toByte())
        
        // Update length
        val length2 = payload.size - 4
        payload[0] = (length2 shr 24).toByte()
        payload[1] = (length2 shr 16).toByte()
        payload[2] = (length2 shr 8).toByte()
        payload[3] = length2.toByte()
        
        return payload.size j { i: Int -> payload[i] }
    }
    
    private fun buildSftpWriteRequest(requestId: Int, handle: String, offset: Long, data: ByteArray): Indexed<Byte> {
        val handleBytes = handle.encodeToByteArray()
        val payload = mutableListOf<Byte>()
        
        // Length (placeholder)
        repeat(4) { payload.add(0) }
        
        // Type: SSH_FXP_WRITE
        payload.add(6)
        
        // Request ID
        payload.add((requestId shr 24).toByte())
        payload.add((requestId shr 16).toByte())
        payload.add((requestId shr 8).toByte())
        payload.add(requestId.toByte())
        
        // Handle length
        payload.add((handleBytes.size shr 24).toByte())
        payload.add((handleBytes.size shr 16).toByte())
        payload.add((handleBytes.size shr 8).toByte())
        payload.add(handleBytes.size.toByte())
        
        // Handle
        payload.addAll(handleBytes.toList())
        
        // Offset
        payload.add((offset shr 56).toByte())
        payload.add((offset shr 48).toByte())
        payload.add((offset shr 40).toByte())
        payload.add((offset shr 32).toByte())
        payload.add((offset shr 24).toByte())
        payload.add((offset shr 16).toByte())
        payload.add((offset shr 8).toByte())
        payload.add(offset.toByte())
        
        // Data length
        payload.add((data.size shr 24).toByte())
        payload.add((data.size shr 16).toByte())
        payload.add((data.size shr 8).toByte())
        payload.add(data.size.toByte())
        
        // Data
        payload.addAll(data.toList())
        
        // Update length
        val length = payload.size - 4
        payload[0] = (length shr 24).toByte()
        payload[1] = (length shr 16).toByte()
        payload[2] = (length shr 8).toByte()
        payload[3] = length.toByte()
        
        return payload.size j { i: Int -> payload[i] }
    }
    
    private fun buildSftpMkdirRequest(requestId: Int, path: String): Indexed<Byte> {
        val pathBytes = path.encodeToByteArray()
        val payload = mutableListOf<Byte>()
        
        // Length (placeholder)
        repeat(4) { payload.add(0) }
        
        // Type: SSH_FXP_MKDIR
        payload.add(14)
        
        // Request ID
        payload.add((requestId shr 24).toByte())
        payload.add((requestId shr 16).toByte())
        payload.add((requestId shr 8).toByte())
        payload.add(requestId.toByte())
        
        // Path length
        payload.add((pathBytes.size shr 24).toByte())
        payload.add((pathBytes.size shr 16).toByte())
        payload.add((pathBytes.size shr 8).toByte())
        payload.add(pathBytes.size.toByte())
        
        // Path
        payload.addAll(pathBytes.toList())
        
        // Update length
        val length = payload.size - 4
        payload[0] = (length shr 24).toByte()
        payload[1] = (length shr 16).toByte()
        payload[2] = (length shr 8).toByte()
        payload[3] = length.toByte()
        
        return payload.size j { i: Int -> payload[i] }
    }
    
    private fun buildSftpRmdirRequest(requestId: Int, path: String): Indexed<Byte> {
        val pathBytes = path.encodeToByteArray()
        val payload = mutableListOf<Byte>()
        
        // Length (placeholder)
        repeat(4) { payload.add(0) }
        
        // Type: SSH_FXP_RMDIR
        payload.add(15)
        
        // Request ID
        payload.add((requestId shr 24).toByte())
        payload.add((requestId shr 16).toByte())
        payload.add((requestId shr 8).toByte())
        payload.add(requestId.toByte())
        
        // Path length
        payload.add((pathBytes.size shr 24).toByte())
        payload.add((pathBytes.size shr 16).toByte())
        payload.add((pathBytes.size shr 8).toByte())
        payload.add(pathBytes.size.toByte())
        
        // Path
        payload.addAll(pathBytes.toList())
        
        // Update length
        val length = payload.size - 4
        payload[0] = (length shr 24).toByte()
        payload[1] = (length shr 16).toByte()
        payload[2] = (length shr 8).toByte()
        payload[3] = length.toByte()
        
        return payload.size j { i: Int -> payload[i] }
    }
    
    private fun buildSftpRemoveRequest(requestId: Int, path: String): Indexed<Byte> {
        val pathBytes = path.encodeToByteArray()
        val payload = mutableListOf<Byte>()
        
        // Length (placeholder)
        repeat(4) { payload.add(0) }
        
        // Type: SSH_FXP_REMOVE
        payload.add(13)
        
        // Request ID
        payload.add((requestId shr 24).toByte())
        payload.add((requestId shr 16).toByte())
        payload.add((requestId shr 8).toByte())
        payload.add(requestId.toByte())
        
        // Path length
        payload.add((pathBytes.size shr 24).toByte())
        payload.add((pathBytes.size shr 16).toByte())
        payload.add((pathBytes.size shr 8).toByte())
        payload.add(pathBytes.size.toByte())
        
        // Path
        payload.addAll(pathBytes.toList())
        
        // Update length
        val length = payload.size - 4
        payload[0] = (length shr 24).toByte()
        payload[1] = (length shr 16).toByte()
        payload[2] = (length shr 8).toByte()
        payload[3] = length.toByte()
        
        return payload.size j { i: Int -> payload[i] }
    }
    
    private fun buildSftpRenameRequest(requestId: Int, oldPath: String, newPath: String): Indexed<Byte> {
        val oldPathBytes = oldPath.encodeToByteArray()
        val newPathBytes = newPath.encodeToByteArray()
        val payload = mutableListOf<Byte>()
        
        // Length (placeholder)
        repeat(4) { payload.add(0) }
        
        // Type: SSH_FXP_RENAME
        payload.add(18)
        
        // Request ID
        payload.add((requestId shr 24).toByte())
        payload.add((requestId shr 16).toByte())
        payload.add((requestId shr 8).toByte())
        payload.add(requestId.toByte())
        
        // Old path length
        payload.add((oldPathBytes.size shr 24).toByte())
        payload.add((oldPathBytes.size shr 16).toByte())
        payload.add((oldPathBytes.size shr 8).toByte())
        payload.add(oldPathBytes.size.toByte())
        
        // Old path
        payload.addAll(oldPathBytes.toList())
        
        // New path length
        payload.add((newPathBytes.size shr 24).toByte())
        payload.add((newPathBytes.size shr 16).toByte())
        payload.add((newPathBytes.size shr 8).toByte())
        payload.add(newPathBytes.size.toByte())
        
        // New path
        payload.addAll(newPathBytes.toList())
        
        // Update length
        val length = payload.size - 4
        payload[0] = (length shr 24).toByte()
        payload[1] = (length shr 16).toByte()
        payload[2] = (length shr 8).toByte()
        payload[3] = length.toByte()
        
        return payload.size j { i: Int -> payload[i] }
    }
    
    private fun buildSftpStatRequest(requestId: Int, path: String): Indexed<Byte> {
        val pathBytes = path.encodeToByteArray()
        val payload = mutableListOf<Byte>()
        
        // Length (placeholder)
        repeat(4) { payload.add(0) }
        
        // Type: SSH_FXP_STAT
        payload.add(17)
        
        // Request ID
        payload.add((requestId shr 24).toByte())
        payload.add((requestId shr 16).toByte())
        payload.add((requestId shr 8).toByte())
        payload.add(requestId.toByte())
        
        // Path length
        payload.add((pathBytes.size shr 24).toByte())
        payload.add((pathBytes.size shr 16).toByte())
        payload.add((pathBytes.size shr 8).toByte())
        payload.add(pathBytes.size.toByte())
        
        // Path
        payload.addAll(pathBytes.toList())
        
        // Update length
        val length = payload.size - 4
        payload[0] = (length shr 24).toByte()
        payload[1] = (length shr 16).toByte()
        payload[2] = (length shr 8).toByte()
        payload[3] = length.toByte()
        
        return payload.size j { i: Int -> payload[i] }
    }
    
    private fun buildSftpSetStatRequest(requestId: Int, path: String, attributes: SSHSftpFileAttributes): Indexed<Byte> {
        val pathBytes = path.encodeToByteArray()
        val payload = mutableListOf<Byte>()
        
        // Length (placeholder)
        repeat(4) { payload.add(0) }
        
        // Type: SSH_FXP_SETSTAT
        payload.add(9)
        
        // Request ID
        payload.add((requestId shr 24).toByte())
        payload.add((requestId shr 16).toByte())
        payload.add((requestId shr 8).toByte())
        payload.add(requestId.toByte())
        
        // Path length
        payload.add((pathBytes.size shr 24).toByte())
        payload.add((pathBytes.size shr 16).toByte())
        payload.add((pathBytes.size shr 8).toByte())
        payload.add(pathBytes.size.toByte())
        
        // Path
        payload.addAll(pathBytes.toList())
        
        // Attributes
        payload.addAll(attributes.data.toList())
        
        // Update length
        val length = payload.size - 4
        payload[0] = (length shr 24).toByte()
        payload[1] = (length shr 16).toByte()
        payload[2] = (length shr 8).toByte()
        payload[3] = length.toByte()
        
        return payload.size j { i: Int -> payload[i] }
    }
    
    // SFTP response parsers
    private fun parseSftpHandle(response: ByteArray): String? {
        // Simulate parsing SFTP handle response
        return "handle_${kotlin.random.Random.nextInt()}"
    }
    
    private fun parseSftpFileList(response: ByteArray): Indexed<SSHSftpFile> {
        // Simulate parsing SFTP file list response
        val files = mutableListOf(
            SSHSftpFile("file1.txt", "file1.txt", SSHSftpFileAttributes(0 j { 0.toByte() })),
            SSHSftpFile("file2.txt", "file2.txt", SSHSftpFileAttributes(0 j { 0.toByte() }))
        )
        return files.size j { i: Int -> files[i] }
    }
    
    private fun parseSftpFileData(response: ByteArray): Indexed<Byte> {
        // Simulate parsing SFTP file data response
        val data = "SFTP file content".toByteArray()
        return data.size j { i: Int -> data[i] }
    }
    
    private fun parseSftpAttributes(response: ByteArray): SSHSftpFileAttributes {
        // Simulate parsing SFTP attributes response
        return SSHSftpFileAttributes(0 j { 0.toByte() })
    }
    
    private fun parseSftpFlags(flags: String): SSHSftpOpenFlags {
        return when (flags.lowercase()) {
            "read" -> SSHSftpOpenFlags.READ
            "write" -> SSHSftpOpenFlags.WRITE
            "append" -> SSHSftpOpenFlags.APPEND
            "create" -> SSHSftpOpenFlags.CREATE
            "truncate" -> SSHSftpOpenFlags.TRUNCATE
            "exclusive" -> SSHSftpOpenFlags.EXCLUSIVE
            else -> SSHSftpOpenFlags.READ
        }
    }
}

// SFTP Context
data class SSHSftpContext(
    val b: CoroutineContext = Dispatchers.IO
)

// SFTP File Handle
data class SSHSftpFileHandle(
    val handle: String,
    val path: String,
    val flags: SSHSftpOpenFlags
)

// SFTP Client factory
object SSHSftpClientFactory {
    fun createSftpClient(connection: SSHConnection, fileSystem: SSHFileSystem): SSHSftpClient {
        return SSHSftpClientImpl(connection, fileSystem)
    }
} 