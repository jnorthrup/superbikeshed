package borg.trikeshed.net.socks

import borg.trikeshed.lib.*
import borg.trikeshed.io.IOContext
import borg.trikeshed.reactor.SelectableChannel
import kotlinx.coroutines.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * SOCKS5 Client implementation with attention-based proxy support
 * Implements RFC 1928 SOCKS Protocol Version 5
 */
class Socks5Client(
    private val proxyHost: String,
    private val proxyPort: Int,
    private val ioContext: IOContext
) {
    private var proxyChannel: SelectableChannel? = null
    private var authenticated = false
    
    // Configuration
    var connectTimeout: Duration = 30.seconds
    var authTimeout: Duration = 10.seconds
    
    // Authentication methods
    sealed class AuthMethod {
        object NoAuth : AuthMethod()
        data class UserPass(val username: String, val password: String) : AuthMethod()
        object GSSAPI : AuthMethod()  // Not implemented
    }
    
    /**
     * Connect to SOCKS5 proxy and authenticate
     */
    suspend fun connect(authMethod: AuthMethod = AuthMethod.NoAuth) {
        // Create connection to proxy
        proxyChannel = createChannel(proxyHost, proxyPort)
        
        // Send greeting
        val greeting = buildGreeting(authMethod)
        proxyChannel?.write(greeting)
        
        // Read server choice
        val choiceBuffer = ByteArray(2)
        val read = proxyChannel?.read(choiceBuffer) ?: 0
        if (read < 2) throw SocksException("Invalid server response")
        
        val version = choiceBuffer[0]
        val method = choiceBuffer[1]
        
        if (version != 0x05.toByte()) {
            throw SocksException("Server returned version $version, expected 5")
        }
        
        // Authenticate based on chosen method
        when (method.toInt() and 0xFF) {
            0x00 -> {
                // No authentication required
                authenticated = true
            }
            0x02 -> {
                // Username/password authentication
                if (authMethod !is AuthMethod.UserPass) {
                    throw SocksException("Server requires username/password auth")
                }
                authenticateUserPass(authMethod.username, authMethod.password)
            }
            0xFF -> {
                throw SocksException("No acceptable authentication methods")
            }
            else -> {
                throw SocksException("Unsupported authentication method: $method")
            }
        }
    }
    
    /**
     * Connect through SOCKS5 proxy to target host
     */
    suspend fun connectTo(
        targetHost: String,
        targetPort: Int
    ): SocksConnection {
        if (!authenticated) {
            throw SocksException("Not authenticated with proxy")
        }
        
        // Build connect request
        val request = buildConnectRequest(targetHost, targetPort)
        proxyChannel?.write(request)
        
        // Read response
        val response = readConnectResponse()
        
        if (response.status != 0x00.toByte()) {
            throw SocksException("Connect failed: ${getErrorMessage(response.status)}")
        }
        
        // Return established connection
        return SocksConnection(
            proxyChannel!!,
            targetHost,
            targetPort,
            response.boundAddress,
            response.boundPort
        )
    }
    
    /**
     * Bind through SOCKS5 proxy (for incoming connections)
     */
    suspend fun bind(
        bindHost: String = "0.0.0.0",
        bindPort: Int = 0
    ): SocksBind {
        if (!authenticated) {
            throw SocksException("Not authenticated with proxy")
        }
        
        // Build bind request
        val request = buildBindRequest(bindHost, bindPort)
        proxyChannel?.write(request)
        
        // Read response
        val response = readConnectResponse()
        
        if (response.status != 0x00.toByte()) {
            throw SocksException("Bind failed: ${getErrorMessage(response.status)}")
        }
        
        return SocksBind(
            proxyChannel!!,
            response.boundAddress,
            response.boundPort
        )
    }
    
    /**
     * UDP associate through SOCKS5 proxy
     */
    suspend fun udpAssociate(
        localHost: String = "0.0.0.0",
        localPort: Int = 0
    ): SocksUDP {
        if (!authenticated) {
            throw SocksException("Not authenticated with proxy")
        }
        
        // Build UDP associate request
        val request = buildUDPAssociateRequest(localHost, localPort)
        proxyChannel?.write(request)
        
        // Read response
        val response = readConnectResponse()
        
        if (response.status != 0x00.toByte()) {
            throw SocksException("UDP associate failed: ${getErrorMessage(response.status)}")
        }
        
        return SocksUDP(
            proxyChannel!!,
            response.boundAddress,
            response.boundPort,
            ioContext
        )
    }
    
    /**
     * Close proxy connection
     */
    fun close() {
        proxyChannel?.close()
        proxyChannel = null
        authenticated = false
    }
    
    private fun buildGreeting(authMethod: AuthMethod): ByteArray {
        return when (authMethod) {
            is AuthMethod.NoAuth -> byteArrayOf(
                0x05,  // Version
                0x01,  // Number of methods
                0x00   // No authentication
            )
            is AuthMethod.UserPass -> byteArrayOf(
                0x05,  // Version
                0x01,  // Number of methods
                0x02   // Username/password
            )
            is AuthMethod.GSSAPI -> byteArrayOf(
                0x05,  // Version
                0x01,  // Number of methods
                0x01   // GSSAPI
            )
        }
    }
    
    private suspend fun authenticateUserPass(username: String, password: String) {
        val userBytes = username.toByteArray()
        val passBytes = password.toByteArray()
        
        if (userBytes.size > 255 || passBytes.size > 255) {
            throw SocksException("Username or password too long")
        }
        
        val authRequest = ByteArray(3 + userBytes.size + passBytes.size)
        authRequest[0] = 0x01  // Subnegotiation version
        authRequest[1] = userBytes.size.toByte()
        System.arraycopy(userBytes, 0, authRequest, 2, userBytes.size)
        authRequest[2 + userBytes.size] = passBytes.size.toByte()
        System.arraycopy(passBytes, 0, authRequest, 3 + userBytes.size, passBytes.size)
        
        proxyChannel?.write(authRequest)
        
        // Read response
        val response = ByteArray(2)
        val read = proxyChannel?.read(response) ?: 0
        if (read < 2) throw SocksException("Invalid auth response")
        
        if (response[0] != 0x01.toByte()) {
            throw SocksException("Invalid auth response version")
        }
        
        if (response[1] != 0x00.toByte()) {
            throw SocksException("Authentication failed")
        }
        
        authenticated = true
    }
    
    private fun buildConnectRequest(host: String, port: Int): ByteArray {
        return buildRequest(0x01, host, port)  // CMD = CONNECT
    }
    
    private fun buildBindRequest(host: String, port: Int): ByteArray {
        return buildRequest(0x02, host, port)  // CMD = BIND
    }
    
    private fun buildUDPAssociateRequest(host: String, port: Int): ByteArray {
        return buildRequest(0x03, host, port)  // CMD = UDP ASSOCIATE
    }
    
    private fun buildRequest(cmd: Byte, host: String, port: Int): ByteArray {
        val hostBytes = host.toByteArray()
        val request = ByteArray(7 + hostBytes.size)
        
        request[0] = 0x05  // Version
        request[1] = cmd   // Command
        request[2] = 0x00  // Reserved
        request[3] = 0x03  // ATYP = Domain name
        request[4] = hostBytes.size.toByte()
        System.arraycopy(hostBytes, 0, request, 5, hostBytes.size)
        request[5 + hostBytes.size] = (port shr 8).toByte()
        request[6 + hostBytes.size] = (port and 0xFF).toByte()
        
        return request
    }
    
    private suspend fun readConnectResponse(): ConnectResponse {
        val header = ByteArray(4)
        var read = proxyChannel?.read(header) ?: 0
        if (read < 4) throw SocksException("Invalid response header")
        
        val version = header[0]
        val status = header[1]
        val addressType = header[3]
        
        if (version != 0x05.toByte()) {
            throw SocksException("Invalid response version: $version")
        }
        
        // Read bound address
        val (boundAddress, boundPort) = when (addressType.toInt() and 0xFF) {
            0x01 -> {
                // IPv4
                val addr = ByteArray(4)
                proxyChannel?.read(addr)
                val portBytes = ByteArray(2)
                proxyChannel?.read(portBytes)
                val port = ((portBytes[0].toInt() and 0xFF) shl 8) or (portBytes[1].toInt() and 0xFF)
                addr.joinToString(".") { (it.toInt() and 0xFF).toString() } to port
            }
            0x03 -> {
                // Domain name
                val lenByte = ByteArray(1)
                proxyChannel?.read(lenByte)
                val len = lenByte[0].toInt() and 0xFF
                val domain = ByteArray(len)
                proxyChannel?.read(domain)
                val portBytes = ByteArray(2)
                proxyChannel?.read(portBytes)
                val port = ((portBytes[0].toInt() and 0xFF) shl 8) or (portBytes[1].toInt() and 0xFF)
                String(domain) to port
            }
            0x04 -> {
                // IPv6
                val addr = ByteArray(16)
                proxyChannel?.read(addr)
                val portBytes = ByteArray(2)
                proxyChannel?.read(portBytes)
                val port = ((portBytes[0].toInt() and 0xFF) shl 8) or (portBytes[1].toInt() and 0xFF)
                // Simplified IPv6 representation
                "[IPv6]" to port
            }
            else -> throw SocksException("Unknown address type: $addressType")
        }
        
        return ConnectResponse(status, boundAddress, boundPort)
    }
    
    private fun getErrorMessage(status: Byte): String = when (status.toInt() and 0xFF) {
        0x01 -> "General SOCKS server failure"
        0x02 -> "Connection not allowed by ruleset"
        0x03 -> "Network unreachable"
        0x04 -> "Host unreachable"
        0x05 -> "Connection refused"
        0x06 -> "TTL expired"
        0x07 -> "Command not supported"
        0x08 -> "Address type not supported"
        else -> "Unknown error: $status"
    }
    
    private suspend fun createChannel(host: String, port: Int): SelectableChannel {
        // Create channel based on IOContext
        // This would be implemented based on platform
        TODO("Platform-specific channel creation")
    }
    
    private data class ConnectResponse(
        val status: Byte,
        val boundAddress: String,
        val boundPort: Int
    )
}

/**
 * Established SOCKS5 connection
 */
class SocksConnection(
    private val channel: SelectableChannel,
    val targetHost: String,
    val targetPort: Int,
    val boundAddress: String,
    val boundPort: Int
) {
    suspend fun write(data: ByteArray) = channel.write(data)
    suspend fun read(buffer: ByteArray) = channel.read(buffer)
    fun close() = channel.close()
}

/**
 * SOCKS5 bind for incoming connections
 */
class SocksBind(
    private val channel: SelectableChannel,
    val boundAddress: String,
    val boundPort: Int
) {
    suspend fun accept(): SocksConnection {
        // Wait for incoming connection notification
        val response = ByteArray(1024)
        val read = channel.read(response)
        // Parse response and return connection
        TODO("Parse incoming connection")
    }
    
    fun close() = channel.close()
}

/**
 * SOCKS5 UDP association
 */
class SocksUDP(
    private val tcpChannel: SelectableChannel,
    val relayAddress: String,
    val relayPort: Int,
    private val ioContext: IOContext
) {
    private var udpSocket: Any? = null  // Platform-specific UDP socket
    
    suspend fun send(data: ByteArray, destHost: String, destPort: Int) {
        // Encapsulate in SOCKS5 UDP format
        val packet = encapsulateUDP(data, destHost, destPort)
        // Send to relay
        TODO("UDP send implementation")
    }
    
    suspend fun receive(): Triple<ByteArray, String, Int> {
        // Receive and decapsulate
        TODO("UDP receive implementation")
    }
    
    fun close() {
        tcpChannel.close()
        // Close UDP socket
    }
    
    private fun encapsulateUDP(data: ByteArray, host: String, port: Int): ByteArray {
        // SOCKS5 UDP encapsulation
        val hostBytes = host.toByteArray()
        val header = ByteArray(10 + hostBytes.size)
        
        header[0] = 0x00  // RSV
        header[1] = 0x00  // RSV
        header[2] = 0x00  // FRAG
        header[3] = 0x03  // ATYP = Domain
        header[4] = hostBytes.size.toByte()
        System.arraycopy(hostBytes, 0, header, 5, hostBytes.size)
        header[5 + hostBytes.size] = (port shr 8).toByte()
        header[6 + hostBytes.size] = (port and 0xFF).toByte()
        
        return header + data
    }
}

/**
 * SOCKS5 exception
 */
class SocksException(message: String) : Exception(message)

/**
 * SOCKS5 client builder
 */
class Socks5ClientBuilder {
    private var proxyHost: String = ""
    private var proxyPort: Int = 1080
    private var ioContext: IOContext? = null
    private var connectTimeout: Duration = 30.seconds
    
    fun proxy(host: String, port: Int = 1080) = apply {
        this.proxyHost = host
        this.proxyPort = port
    }
    
    fun ioContext(context: IOContext) = apply { this.ioContext = context }
    fun connectTimeout(timeout: Duration) = apply { this.connectTimeout = timeout }
    
    fun build(): Socks5Client {
        require(proxyHost.isNotEmpty()) { "Proxy host must be specified" }
        val context = ioContext ?: IOContext.NioContext("socks5-client")
        
        return Socks5Client(proxyHost, proxyPort, context).apply {
            this.connectTimeout = this@Socks5ClientBuilder.connectTimeout
        }
    }
}