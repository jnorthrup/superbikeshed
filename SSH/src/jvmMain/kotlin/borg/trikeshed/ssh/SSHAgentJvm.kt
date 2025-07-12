@file:Suppress("UNCHECKED_CAST", "FunctionName", "NonAsciiCharacters", "NOTHING_TO_INLINE")
package borg.trikeshed.ssh

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import java.io.File
import java.net.Socket
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * JVM SSH Agent implementation
 * 
 * Communicates with ssh-agent via Unix domain socket
 */

// SSH Agent protocol constants
object SSHAgentProtocol {
    const val SSH_AGENTC_REQUEST_IDENTITIES: Byte = 11
    const val SSH_AGENT_IDENTITIES_ANSWER: Byte = 12
    const val SSH_AGENTC_SIGN_REQUEST: Byte = 13
    const val SSH_AGENT_SIGN_RESPONSE: Byte = 14
    const val SSH_AGENT_FAILURE: Byte = 5
    
    const val SSH_AGENT_RSA_SHA2_256: Int = 2
    const val SSH_AGENT_RSA_SHA2_512: Int = 4
}

actual fun getSSHAgent(): SSHAgent? {
    val authSock = System.getenv("SSH_AUTH_SOCK") ?: return null
    
    return try {
        JvmSSHAgent(authSock)
    } catch (e: Exception) {
        null
    }
}

class JvmSSHAgent(internal val socketPath: String) : SSHAgent {
    
    override suspend fun listIdentities(): List<SSHAgentIdentity> = withContext(Dispatchers.IO) {
        val identities = mutableListOf<SSHAgentIdentity>()
        
        withAgentConnection { channel ->
            // Send request identities message
            val request = createAgentMessage(SSHAgentProtocol.SSH_AGENTC_REQUEST_IDENTITIES)
            channel.write(request)
            
            // Read response
            val response = readAgentMessage(channel)
            if (response.messageType != SSHAgentProtocol.SSH_AGENT_IDENTITIES_ANSWER) {
                return@withAgentConnection identities
            }
            
            // Parse identities
            val data = response.data
            var offset = 0
            
            // Read number of identities
            if (offset + 4 > data.component1()) return@withAgentConnection identities
            val numIdentities = readUInt32(data, offset)
            offset += 4
            
            // Read each identity
            repeat(numIdentities) {
                // Read key blob
                val keyBlob = readSSHString(data, offset) ?: return@repeat
                offset = keyBlob.component2()
                
                // Read comment
                val comment = readSSHString(data, offset) ?: return@repeat
                offset = comment.component2()
                
                identities.add(SSHAgentIdentity(
                    publicKey = keyBlob.component1(),
                    comment = String(ByteArray(comment.component1().component1()) { i -> comment.component1()[i] })
                ))
            }
        }
        
        return@withContext identities
    }
    
    override suspend fun signData(
        identity: SSHAgentIdentity,
        data: Indexed<Byte>
    ): SSHSignature = withContext(Dispatchers.IO) {
        withAgentConnection { channel ->
            // Build sign request
            val request = buildSignRequest(identity.publicKey, data)
            channel.write(request)
            
            // Read response
            val response = readAgentMessage(channel)
            if (response.messageType != SSHAgentProtocol.SSH_AGENT_SIGN_RESPONSE) {
                throw IllegalStateException("Agent sign failed")
            }
            
            // Parse signature
            val sigData = readSSHString(response.data, 0)?.component1()
                ?: throw IllegalStateException("Invalid signature response")
            
            sigData
        }
    }
    
    internal suspend fun <T> withAgentConnection(block: suspend (SocketChannel) -> T): T {
        // Use Unix domain socket on Java 16+
        return if (isUnixDomainSocketSupported()) {
            val address = UnixDomainSocketAddress.of(socketPath)
            SocketChannel.open(address).use { channel ->
                block(channel)
            }
        } else {
            // Fallback to regular socket if available
            throw UnsupportedOperationException("Unix domain sockets not supported")
        }
    }
    
    internal fun isUnixDomainSocketSupported(): Boolean {
        return try {
            Class.forName("java.net.UnixDomainSocketAddress")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }
    
    internal fun createAgentMessage(messageType: Byte, data: Indexed<Byte>? = null): ByteBuffer {
        val dataSize = data?.component1() ?: 0
        val totalSize = 1 + dataSize // message type + data
        
        val buffer = ByteBuffer.allocate(4 + totalSize)
        
        // Length (big-endian)
        buffer.putInt(totalSize)
        
        // Message type
        buffer.put(messageType)
        
        // Data
        if (data != null) {
            for (i in 0 until data.component1()) {
                buffer.put(data[i])
            }
        }
        
        buffer.flip()
        return buffer
    }
    
    internal fun buildSignRequest(
        publicKey: SSHPublicKey,
        data: Indexed<Byte>,
        flags: Int = 0
    ): ByteBuffer {
        // Calculate size
        val keySize = 4 + publicKey.component1()
        val dataSize = 4 + data.component1()
        val totalSize = 1 + keySize + dataSize + 4 // type + key + data + flags
        
        val buffer = ByteBuffer.allocate(4 + totalSize)
        
        // Length
        buffer.putInt(totalSize)
        
        // Message type
        buffer.put(SSHAgentProtocol.SSH_AGENTC_SIGN_REQUEST)
        
        // Key blob
        buffer.putInt(publicKey.component1())
        for (i in 0 until publicKey.component1()) {
            buffer.put(publicKey[i])
        }
        
        // Data to sign
        buffer.putInt(data.component1())
        for (i in 0 until data.component1()) {
            buffer.put(data[i])
        }
        
        // Flags
        buffer.putInt(flags)
        
        buffer.flip()
        return buffer
    }
    
    internal suspend fun readAgentMessage(channel: SocketChannel): AgentMessage {
        // Read length
        val lengthBuffer = ByteBuffer.allocate(4)
        var bytesRead = 0
        while (bytesRead < 4) {
            val n = channel.read(lengthBuffer)
            if (n < 0) throw IllegalStateException("Agent connection closed")
            bytesRead += n
        }
        
        lengthBuffer.flip()
        val length = lengthBuffer.getInt()
        
        if (length < 1 || length > 256 * 1024) {
            throw IllegalStateException("Invalid agent message length: $length")
        }
        
        // Read message
        val messageBuffer = ByteBuffer.allocate(length)
        bytesRead = 0
        while (bytesRead < length) {
            val n = channel.read(messageBuffer)
            if (n < 0) throw IllegalStateException("Agent connection closed")
            bytesRead += n
        }
        
        messageBuffer.flip()
        
        val messageType = messageBuffer.get()
        val remaining = messageBuffer.remaining()
        val data = remaining j { i: Int -> messageBuffer.get() }
        
        return AgentMessage(messageType, data)
    }
    
    internal fun readUInt32(data: Indexed<Byte>, offset: Int): Int {
        if (offset + 4 > data.component1()) return 0
        
        return ((data[offset].toInt() and 0xFF) shl 24) or
               ((data[offset + 1].toInt() and 0xFF) shl 16) or
               ((data[offset + 2].toInt() and 0xFF) shl 8) or
               (data[offset + 3].toInt() and 0xFF)
    }
    
    internal fun readSSHString(data: Indexed<Byte>, offset: Int): Join<Indexed<Byte>, Int>? {
        if (offset + 4 > data.component1()) return null
        
        val length = readUInt32(data, offset)
        if (offset + 4 + length > data.component1()) return null
        
        val string = length j { i: Int -> data[offset + 4 + i] }
        return string j offset + 4 + length
    }
    
    internal data class AgentMessage(
        val messageType: Byte,
        val data: Indexed<Byte>
    )
}

// Windows named pipe support for Pageant
class WindowsSSHAgent(internal val pipeName: String = "\\\\.\\pipe\\openssh-ssh-agent") : SSHAgent {
    override suspend fun listIdentities(): List<SSHAgentIdentity> {
        // Windows named pipe implementation
        TODO("Windows SSH agent support")
    }
    
    override suspend fun signData(identity: SSHAgentIdentity, data: Indexed<Byte>): SSHSignature {
        TODO("Windows SSH agent support")
    }
}

// SSH key utility functions
object SSHKeyUtils {
    fun parsePublicKey(keyData: String): SSHPublicKey? {
        val parts = keyData.trim().split(" ", limit = 3)
        if (parts.size < 2) return null
        
        val keyType = parts[0]
        val base64Data = parts[1]
        
        return try {
            val decoded = decodeBase64(base64Data)
            decoded.size j { i: Int -> decoded[i] }
        } catch (e: Exception) {
            null
        }
    }
    
    fun formatFingerprint(publicKey: SSHPublicKey, algorithm: String = "SHA256"): String {
        // Compute fingerprint
        val hash = when (algorithm.uppercase()) {
            "SHA256" -> computeSHA256(publicKey)
            "MD5" -> computeMD5(publicKey)
            else -> throw IllegalArgumentException("Unknown fingerprint algorithm: $algorithm")
        }
        
        // Format based on algorithm
        return when (algorithm.uppercase()) {
            "SHA256" -> {
                val base64 = encodeBase64(hash).trimEnd('=')
                "SHA256:$base64"
            }
            "MD5" -> {
                hash.joinToString(":") { byte ->
                    "%02x".format(byte)
                }
            }
            else -> ""
        }
    }
    
    internal fun computeSHA256(data: Indexed<Byte>): ByteArray {
        // Would use actual SHA256
        return ByteArray(32) { i -> (i * 7).toByte() }
    }
    
    internal fun computeMD5(data: Indexed<Byte>): ByteArray {
        // Would use actual MD5
        return ByteArray(16) { i -> (i * 13).toByte() }
    }
    
    internal fun encodeBase64(data: ByteArray): String {
        // Would use actual base64 encoding
        return "placeholder"
    }
}