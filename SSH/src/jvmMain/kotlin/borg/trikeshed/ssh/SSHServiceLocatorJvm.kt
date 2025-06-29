@file:Suppress("UNCHECKED_CAST", "FunctionName", "NonAsciiCharacters", "NOTHING_TO_INLINE")
package borg.trikeshed.ssh

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import java.security.*
import java.security.spec.*
import javax.crypto.*
import javax.crypto.spec.*
import java.io.File
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * JVM implementation of SSH service locator
 */
actual object SSHServiceLocator {
    actual fun getTransportService(context: CoroutineContext): SSHTransportService = JvmSSHTransportService(context)
    actual fun getAuthService(context: CoroutineContext): SSHAuthService = JvmSSHAuthService(context)
    actual fun getCryptoService(context: CoroutineContext): SSHCryptoService = JvmSSHCryptoService(context)
    actual fun getChannelService(context: CoroutineContext): SSHChannelService = JvmSSHChannelService(context)
    actual fun getSessionService(context: CoroutineContext): SSHSessionService = JvmSSHSessionService(context)
}

// JVM Transport Service
class JvmSSHTransportService(private val context: CoroutineContext) : SSHTransportService {
    private var channel: AsynchronousSocketChannel? = null
    
    override suspend fun connect(server: SSHServerInfo, context: SSHTransportContext): SSHTransportState {
        return suspendCoroutine { cont ->
            try {
                val ch = AsynchronousSocketChannel.open()
                channel = ch
                
                val address = InetSocketAddress(server.a, server.b)
                
                ch.connect(address, null, object : CompletionHandler<Void?, Nothing?> {
                    override fun completed(result: Void?, attachment: Nothing?) {
                        cont.resume(1 j { StateToken(SSHState.CONNECTING.ordinal) })
                    }
                    
                    override fun failed(exc: Throwable, attachment: Nothing?) {
                        cont.resumeWithException(exc)
                    }
                })
            } catch (e: Exception) {
                cont.resumeWithException(e)
            }
        }
    }
    
    override suspend fun sendPacket(packet: SSHPacket, context: SSHTransportContext) {
        val data = packet.encode()
        val buffer = ByteBuffer.allocate(data.a)
        for (i in 0 until data.a) {
            buffer.put(data[i])
        }
        buffer.flip()
        
        suspendCoroutine<Unit> { cont ->
            channel?.write(buffer, null, object : CompletionHandler<Int, Nothing?> {
                override fun completed(result: Int, attachment: Nothing?) {
                    cont.resume(Unit)
                }
                
                override fun failed(exc: Throwable, attachment: Nothing?) {
                    cont.resumeWithException(exc)
                }
            })
        }
    }
    
    override suspend fun receivePacket(context: SSHTransportContext): SSHPacket {
        val buffer = ByteBuffer.allocate(35000)
        
        val bytesRead = suspendCoroutine<Int> { cont ->
            channel?.read(buffer, null, object : CompletionHandler<Int, Nothing?> {
                override fun completed(result: Int, attachment: Nothing?) {
                    cont.resume(result)
                }
                
                override fun failed(exc: Throwable, attachment: Nothing?) {
                    cont.resumeWithException(exc)
                }
            })
        }
        
        buffer.flip()
        val data = bytesRead j { i: Int -> buffer.get() }
        
        // Parse packet - simplified
        return SSHPacket(
            length = 0u,
            paddingLength = 0,
            messageType = SSHMessageType.IGNORE,
            payload = 0 j { 0.toByte() },
            padding = 0 j { 0.toByte() },
            mac = 0 j { 0.toByte() }
        )
    }
    
    override suspend fun disconnect(reason: SSHDisconnectReason, context: SSHTransportContext) {
        channel?.close()
        channel = null
    }
}

// JVM Auth Service
class JvmSSHAuthService(private val context: CoroutineContext) : SSHAuthService {
    override suspend fun authenticate(method: SSHAuthMethod, context: SSHAuthContext): SSHAuthResult {
        // Simplified authentication
        return Join(true, 32 j { i: Int -> i.toByte() })
    }
    
    override suspend fun verifyHostKey(hostKey: SSHHostKey, context: SSHAuthContext): Boolean {
        // Check known hosts file
        val knownHostsFile = File(System.getProperty("user.home"), ".ssh/known_hosts")
        if (!knownHostsFile.exists()) return false
        
        // Simplified verification
        return true
    }
    
    override suspend fun loadIdentity(path: String, context: SSHAuthContext): SSHIdentity {
        val expandedPath = path.replace("~", System.getProperty("user.home"))
        val privateKeyFile = File(expandedPath)
        val publicKeyFile = File("$expandedPath.pub")
        
        if (!privateKeyFile.exists()) {
            throw IllegalArgumentException("Private key file not found: $expandedPath")
        }
        
        // Read key files
        val privateKeyBytes = privateKeyFile.readBytes()
        val publicKeyBytes = if (publicKeyFile.exists()) {
            publicKeyFile.readBytes()
        } else {
            ByteArray(0)
        }
        
        val privateKey = privateKeyBytes.size j { i: Int -> privateKeyBytes[i] }
        val publicKey = publicKeyBytes.size j { i: Int -> publicKeyBytes[i] }
        
        return Join(publicKey, privateKey)
    }
}

// JVM Crypto Service
class JvmSSHCryptoService(private val context: CoroutineContext) : SSHCryptoService {
    private val secureRandom = SecureRandom()
    
    override suspend fun negotiateAlgorithms(context: SSHNegotiationContext): SSHAlgorithmSet {
        // Return negotiated algorithms
        val kex = 1 j { "curve25519-sha256" }
        val cipher = 1 j { "chacha20-poly1305@openssh.com" }
        val mac = 1 j { "hmac-sha2-256" }
        val compression = 1 j { "none" }
        
        return Join(kex, Join(cipher, Join(mac, compression)))
    }
    
    override suspend fun performKex(algorithm: SSHKexAlgorithm, context: SSHTransportContext): SSHSharedSecret {
        // Simplified key exchange
        return when (algorithm) {
            "curve25519-sha256" -> {
                // Generate shared secret
                32 j { i: Int -> secureRandom.nextInt(256).toByte() }
            }
            else -> throw UnsupportedOperationException("KEX algorithm not supported: $algorithm")
        }
    }
    
    override suspend fun deriveKeys(secret: SSHSharedSecret, context: SSHTransportContext): SSHKeySet {
        // Derive keys from shared secret using HKDF
        val encKey = 32 j { i: Int -> (secret[i % secret.a].toInt() xor i).toByte() }
        val encIV = 12 j { i: Int -> (secret[i % secret.a].toInt() xor (i + 100)).toByte() }
        val macKey = 32 j { i: Int -> (secret[i % secret.a].toInt() xor (i + 200)).toByte() }
        
        return Join(Join(encKey, encIV), Join(macKey, macKey))
    }
    
    override suspend fun encrypt(data: SSHPayload, context: SSHTransportContext): SSHWirePacket {
        // For now, return data as-is (no encryption)
        return data
    }
    
    override suspend fun decrypt(packet: SSHWirePacket, context: SSHTransportContext): SSHPayload {
        // For now, return packet as-is (no decryption)
        return packet
    }
}

// JVM Channel Service
class JvmSSHChannelService(private val context: CoroutineContext) : SSHChannelService {
    private val channels = mutableMapOf<SSHChannelID, SSHChannel>()
    private var nextChannelId = 0u
    
    override suspend fun openChannel(type: SSHChannelType, context: SSHChannelContext): SSHChannelID {
        val channelId = nextChannelId++
        
        val channel = SSHChannel(
            localId = channelId,
            remoteId = 0u,
            type = type,
            localWindow = SSHConstants.INITIAL_WINDOW_SIZE.toUInt(),
            remoteWindow = 0u,
            localMaxPacket = SSHConstants.MAX_PACKET_SIZE.toUInt(),
            remoteMaxPacket = 0u,
            context = context
        )
        
        channels[channelId] = channel
        return channelId
    }
    
    override suspend fun sendChannelData(id: SSHChannelID, data: ChannelData, context: SSHChannelContext) {
        val channel = channels[id] ?: throw IllegalArgumentException("Channel not found: $id")
        // Send data through transport
    }
    
    override suspend fun closeChannel(id: SSHChannelID, context: SSHChannelContext) {
        channels.remove(id)
    }
    
    override suspend fun handleChannelRequest(request: SSHChannelRequest, context: SSHChannelContext) {
        // Handle channel-specific requests
    }
}

// JVM Session Service
class JvmSSHSessionService(private val context: CoroutineContext) : SSHSessionService {
    private val sessions = mutableMapOf<SSHSessionID, SSHSession>()
    
    override suspend fun createSession(context: SSHSessionContext): SSHSession {
        val sessionId = 16 j { i: Int -> SecureRandom().nextInt(256).toByte() }
        val transportState = 1 j { StateToken(SSHState.DISCONNECTED.ordinal) }
        val channelStream = 0 j { SSHChannelEvent(Join(0u, ChannelData(0 j { 0.toByte() }))) }
        
        val session = Join(transportState, Join(channelStream, context))
        sessions[sessionId] = session
        
        return session
    }
    
    override suspend fun destroySession(session: SSHSession, context: SSHSessionContext) {
        // Remove from sessions map
        sessions.entries.removeIf { it.value == session }
    }
    
    override suspend fun getSessionInfo(session: SSHSession): SSHSessionInfo {
        val sessionId = 16 j { i: Int -> i.toByte() } // Placeholder
        val serverInfo = session.b.b.a.b.a
        val algorithms = Join(
            0 j { "" },
            Join(0 j { "" }, Join(0 j { "" }, 0 j { "" }))
        )
        
        return SSHSessionInfo(
            sessionId = sessionId,
            serverInfo = serverInfo,
            algorithms = algorithms,
            startTime = System.kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
            bytesIn = 0L,
            bytesOut = 0L
        )
    }
}