package borg.trikeshed.reactor.socks

import borg.trikeshed.lib.*
import borg.trikeshed.reactor.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.*
import java.nio.channels.*
import java.nio.ByteBuffer

/**
 * JVM implementation of SOCKS Factory using RelaxFactory pattern
 * Uses simple coroutine abstractions from Trikeshed
 */
actual class JvmSocksFactory(
    private val config: SocksConfig
) : SocksFactory {
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val relaxationState = MutableStateFlow(SocksRelaxationState(
        securityLevel = config.securityLevel,
        activeConnections = 0,
        pendingRequests = 0,
        authenticationEnabled = config.authenticationMethods.a > 0,
        encryptionEnabled = false,
        rateLimitEnabled = false
    ))
    
    override suspend fun relax(): SocksServer {
        return JvmSocksServer(config, scope)
    }
    
    override suspend fun createClient(): SocksClient {
        return JvmSocksClient(config, scope)
    }
    
    override suspend fun tense(): Result<Unit> {
        relaxationState.value = relaxationState.value.copy(
            securityLevel = 1.0f,
            authenticationEnabled = true,
            encryptionEnabled = true,
            rateLimitEnabled = true
        )
        return Result.success(Unit)
    }
    
    override suspend fun equilibrium(): SocksRelaxationState {
        return relaxationState.value
    }
}

/**
 * JVM implementation of SOCKS Server
 */
actual class JvmSocksServer(
    private val config: SocksConfig,
    private val scope: CoroutineScope
) : SocksServer {
    
    private var serverSocket: ServerSocketChannel? = null
    private val isRunning = MutableStateFlow(false)
    private val connections = MutableStateFlow<Map<String, JvmSocksConnection>>(emptyMap())
    
    override suspend fun accept(): SocksConnection {
        val serverSocket = serverSocket ?: throw IllegalStateException("Server not bound")
        
        val clientSocket = withContext(Dispatchers.IO) {
            serverSocket.accept()
        }
        
        val connectionId = SocksConnectionId("conn-${System.currentTimeMillis()}")
        val connection = JvmSocksConnection(connectionId, clientSocket, config)
        
        connections.value = connections.value + (connectionId.value to connection)
        
        return connection
    }
    
    override suspend fun bind(address: SocksBindAddress, port: SocksBindPort): Result<Unit> {
        return try {
            serverSocket = ServerSocketChannel.open().apply {
                configureBlocking(false)
                bind(InetSocketAddress(address.value, port.value))
            }
            isRunning.value = true
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun close() {
        isRunning.value = false
        serverSocket?.close()
        connections.value.values.forEach { it.close() }
        connections.value = emptyMap()
    }
}

/**
 * JVM implementation of SOCKS Client
 */
actual class JvmSocksClient(
    private val config: SocksConfig,
    private val scope: CoroutineScope
) : SocksClient {
    
    private var socket: SocketChannel? = null
    private var isConnected = false
    
    override suspend fun connect(serverHost: SocksTargetHost, serverPort: SocksTargetPort): Result<Unit> {
        return try {
            socket = SocketChannel.open().apply {
                configureBlocking(false)
                connect(InetSocketAddress(serverHost.value, serverPort.value))
            }
            isConnected = true
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun authenticate(method: SocksMethod, credentials: Any?): Result<Boolean> {
        // Implement authentication based on method
        return when (method) {
            SocksProtocol.METHOD_NO_AUTHENTICATION -> Result.success(true)
            SocksProtocol.METHOD_USERNAME_PASSWORD -> {
                val authCreds = credentials as? Pair<String, String>
                if (authCreds != null) {
                    // Implement username/password authentication
                    Result.success(true)
                } else {
                    Result.failure(IllegalArgumentException("Invalid credentials"))
                }
            }
            else -> Result.failure(UnsupportedOperationException("Authentication method not supported"))
        }
    }
    
    override suspend fun request(request: SocksRequest): Result<SocksResponse> {
        val socket = socket ?: return Result.failure(IllegalStateException("Not connected"))
        
        return try {
            // Serialize and send request
            val requestData = serializeRequest(request)
            withContext(Dispatchers.IO) {
                socket.write(ByteBuffer.wrap(requestData.toArray()))
            }
            
            // Read response
            val responseBuffer = ByteBuffer.allocate(1024)
            val bytesRead = withContext(Dispatchers.IO) {
                socket.read(responseBuffer)
            }
            
            if (bytesRead > 0) {
                responseBuffer.flip()
                val responseData = ByteArray(bytesRead)
                responseBuffer.get(responseData)
                val response = parseResponse(responseData.toIndexed())
                Result.success(response)
            } else {
                Result.failure(IllegalStateException("No response received"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun close() {
        isConnected = false
        socket?.close()
    }
    
    private fun serializeRequest(request: SocksRequest): Indexed<Byte> {
        val buffer = mutableListOf<Byte>()
        buffer.add(request.version)
        buffer.add(request.command)
        buffer.add(request.reserved)
        buffer.add(request.addressType)
        
        // Serialize address based on type
        when (request.addressType) {
            SocksProtocol.ATYP_IPV4 -> {
                val parts = request.targetAddress.value.split(".")
                parts.forEach { part -> buffer.add(part.toInt().toByte()) }
            }
            SocksProtocol.ATYP_DOMAINNAME -> {
                val domain = request.targetAddress.value
                buffer.add(domain.length.toByte())
                domain.forEach { char -> buffer.add(char.code.toByte()) }
            }
            SocksProtocol.ATYP_IPV6 -> {
                // Simplified IPv6 handling
                repeat(16) { buffer.add(0) }
            }
        }
        
        // Add port (big-endian)
        buffer.add((request.targetPort.value shr 8).toByte())
        buffer.add((request.targetPort.value and 0xFF).toByte())
        
        return buffer.size j { i: Int -> buffer[i] }
    }
    
    private fun parseResponse(data: Indexed<Byte>): SocksResponse {
        val version = data[0]
        val replyCode = data[1]
        val reserved = data[2]
        val addressType = data[3]
        
        // Parse address and port based on address type
        val (bindAddress, bindPort) = when (addressType) {
            SocksProtocol.ATYP_IPV4 -> parseIpv4Address(data, 4)
            SocksProtocol.ATYP_DOMAINNAME -> parseDomainName(data, 4)
            SocksProtocol.ATYP_IPV6 -> parseIpv6Address(data, 4)
            else -> SocksBindAddress("0.0.0.0") to SocksBindPort(0)
        }
        
        return SocksResponse(version, replyCode, reserved, addressType, bindAddress, bindPort)
    }
    
    private fun parseIpv4Address(data: Indexed<Byte>, offset: Int): Pair<SocksBindAddress, SocksBindPort> {
        val ip = "${data[offset] and 0xFF}.${data[offset + 1] and 0xFF}.${data[offset + 2] and 0xFF}.${data[offset + 3] and 0xFF}"
        val port = ((data[offset + 4].toInt() and 0xFF) shl 8) or (data[offset + 5].toInt() and 0xFF)
        return SocksBindAddress(ip) to SocksBindPort(port)
    }
    
    private fun parseDomainName(data: Indexed<Byte>, offset: Int): Pair<SocksBindAddress, SocksBindPort> {
        val domainLength = data[offset].toInt() and 0xFF
        val domain = String(ByteArray(domainLength) { i -> data[offset + 1 + i] })
        val port = ((data[offset + 1 + domainLength].toInt() and 0xFF) shl 8) or (data[offset + 2 + domainLength].toInt() and 0xFF)
        return SocksBindAddress(domain) to SocksBindPort(port)
    }
    
    private fun parseIpv6Address(data: Indexed<Byte>, offset: Int): Pair<SocksBindAddress, SocksBindPort> {
        // Simplified IPv6 parsing
        val ip = "::1"
        val port = ((data[offset + 16].toInt() and 0xFF) shl 8) or (data[offset + 17].toInt() and 0xFF)
        return SocksBindAddress(ip) to SocksBindPort(port)
    }
}

/**
 * JVM implementation of SOCKS Connection
 */
actual class JvmSocksConnection(
    override val connectionId: SocksConnectionId,
    private val socket: SocketChannel,
    private val config: SocksConfig
) : SocksConnection {
    
    override var state: SocksConnectionState = SocksConnectionState.INIT
        private set
    
    private val startTime = System.currentTimeMillis()
    private var bytesReceived = 0L
    private var bytesSent = 0L
    private var packetsReceived = 0L
    private var packetsSent = 0L
    private var errors = 0L
    
    override suspend fun read(buffer: Indexed<Byte>): Int {
        return try {
            val byteBuffer = ByteBuffer.allocate(buffer.a)
            val bytesRead = withContext(Dispatchers.IO) {
                socket.read(byteBuffer)
            }
            
            if (bytesRead > 0) {
                byteBuffer.flip()
                byteBuffer.get(ByteArray(bytesRead) { i -> buffer[i] })
                bytesReceived += bytesRead
                packetsReceived++
            }
            
            bytesRead
        } catch (e: Exception) {
            errors++
            throw e
        }
    }
    
    override suspend fun write(data: Indexed<Byte>): Int {
        return try {
            val byteBuffer = ByteBuffer.wrap(data.toArray())
            val bytesWritten = withContext(Dispatchers.IO) {
                socket.write(byteBuffer)
            }
            
            bytesSent += bytesWritten
            packetsSent++
            
            bytesWritten
        } catch (e: Exception) {
            errors++
            throw e
        }
    }
    
    override suspend fun close() {
        state = SocksConnectionState.CLOSED
        withContext(Dispatchers.IO) {
            socket.close()
        }
    }
    
    override suspend fun getStats(): SocksConnectionStats {
        return SocksConnectionStats(
            bytesReceived = bytesReceived,
            bytesSent = bytesSent,
            packetsReceived = packetsReceived,
            packetsSent = packetsSent,
            errors = errors,
            uptimeMs = System.currentTimeMillis() - startTime
        )
    }
    
    fun updateState(newState: SocksConnectionState) {
        state = newState
    }
}

/**
 * JVM implementation of SOCKS Factory Factory
 */
actual class JvmSocksFactoryFactory : SocksFactoryFactory {
    
    override suspend fun createSocksFactory(config: SocksConfig): SocksFactory {
        return JvmSocksFactory(config, CoroutineScope(Dispatchers.Default))
    }
    
    override suspend fun createPerformanceSocksFactory(): SocksFactory {
        val config = SocksConfig(
            securityLevel = 0.0f,
            authenticationMethods = 1 j { i -> SocksProtocol.METHOD_NO_AUTHENTICATION },
            maxConnections = 10000,
            timeoutMs = 5000
        )
        return JvmSocksFactory(config, CoroutineScope(Dispatchers.Default))
    }
    
    override suspend fun createSecureSocksFactory(): SocksFactory {
        val config = SocksConfig(
            securityLevel = 1.0f,
            authenticationMethods = 2 j { i -> 
                when (i) {
                    0 -> SocksProtocol.METHOD_USERNAME_PASSWORD
                    else -> SocksProtocol.METHOD_GSSAPI
                }
            },
            maxConnections = 100,
            timeoutMs = 60000
        )
        return JvmSocksFactory(config, CoroutineScope(Dispatchers.Default))
    }
}

// Extension function to convert ByteArray to Indexed<Byte>
private fun ByteArray.toIndexed(): Indexed<Byte> = size j { i -> this[i] }

// Extension function to convert Indexed<Byte> to ByteArray
private fun Indexed<Byte>.toArray(): ByteArray = ByteArray(a) { i -> this[i] } 