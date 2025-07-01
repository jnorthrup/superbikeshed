package borg.trikeshed.reactor.socks

import borg.trikeshed.lib.*

/**
 * Simple SOCKS5 Protocol Evolution using ByteIndexedBuffer
 * 
 * Demonstrates minimal forward scans and protocol fragment transformation
 * without complex type inference issues.
 */
object Socks5Simple {

    // === SOCKS5 PROTOCOL CONSTANTS ===
    
    const val SOCKS_VERSION_5: Byte = 0x05
    const val AUTH_METHOD_NO_AUTH: Byte = 0x00
    const val CMD_CONNECT: Byte = 0x01
    const val ATYP_IPV4: Byte = 0x01
    const val REPLY_SUCCESS: Byte = 0x00

    /**
     * Parse SOCKS5 handshake with minimal forward scans
     */
    fun parseHandshake(buffer: ByteIndexedBuffer): HandshakeResult? {
        if (buffer.rem < 2) return null
        
        val version = buffer.get
        if (version != SOCKS_VERSION_5) return null
        
        val methodCount = buffer.get.toInt() and 0xFF
        if (buffer.rem < methodCount) return null
        
        val methods = methodCount j { i -> buffer.get }
        
        return HandshakeResult(version, methods, methodCount + 2)
    }

    /**
     * Parse SOCKS5 request with minimal forward scans
     */
    fun parseRequest(buffer: ByteIndexedBuffer): RequestResult? {
        if (buffer.rem < 4) return null
        
        val version = buffer.get
        if (version != SOCKS_VERSION_5) return null
        
        val command = buffer.get
        val reserved = buffer.get
        val addressType = buffer.get
        
        when (addressType) {
            ATYP_IPV4 -> {
                if (buffer.rem < 6) return null // 4 bytes IP + 2 bytes port
                
                val address = 4 j { i -> buffer.get }
                val portHigh = buffer.get.toInt() and 0xFF
                val portLow = buffer.get.toInt() and 0xFF
                val port = (portHigh shl 8) or portLow
                
                return RequestResult(version, command, addressType, address, port, 10)
            }
            else -> return null
        }
    }

    /**
     * Create SOCKS5 handshake response
     */
    fun createHandshakeResponse(method: Byte): ByteIndexed {
        return 2 j { i ->
            when (i) {
                0 -> SOCKS_VERSION_5
                1 -> method
                else -> 0x00
            }
        }
    }

    /**
     * Create SOCKS5 request response
     */
    fun createRequestResponse(
        reply: Byte,
        addressType: Byte,
        boundAddress: ByteIndexed,
        boundPort: Int
    ): ByteIndexed {
        val headerSize = 4 // VER + REP + RSV + ATYP
        val addressSize = boundAddress.a
        val portSize = 2
        val totalSize = headerSize + addressSize + portSize
        
        return totalSize j { i ->
            when {
                i == 0 -> SOCKS_VERSION_5
                i == 1 -> reply
                i == 2 -> 0x00 // Reserved
                i == 3 -> addressType
                i < headerSize + addressSize -> boundAddress[i - headerSize]
                else -> {
                    val portIndex = i - headerSize - addressSize
                    when (portIndex) {
                        0 -> (boundPort shr 8).toByte()
                        1 -> (boundPort and 0xFF).toByte()
                        else -> 0x00
                    }
                }
            }
        }
    }

    /**
     * Convert ByteIndexed to IP address string
     */
    fun ByteIndexed.toIpAddress(): String {
        return when (a) {
            4 -> toArray().joinToString(".") { (it.toInt() and 0xFF).toString() }
            else -> toDebugString()
        }
    }

    /**
     * Convert ByteIndexed to debug string
     */
    fun ByteIndexed.toDebugString(): String {
        return toArray().joinToString(", ") { "0x%02X".format(it.toInt() and 0xFF) }
    }

    // === RESULT CLASSES ===

    data class HandshakeResult(
        val version: Byte,
        val methods: ByteIndexed,
        val scanCount: Int
    )

    data class RequestResult(
        val version: Byte,
        val command: Byte,
        val addressType: Byte,
        val destinationAddress: ByteIndexed,
        val destinationPort: Int,
        val scanCount: Int
    )

    // === DEMO FUNCTIONS ===

    /**
     * Run simple SOCKS5 demo
     */
    fun runDemo() {
        println("=== Simple SOCKS5 Evolution Demo ===")
        println()
        
        // Demo handshake
        demoHandshake()
        println()
        
        // Demo request
        demoRequest()
        println()
        
        // Demo complete flow
        demoCompleteFlow()
    }

    private fun demoHandshake() {
        println("1. SOCKS5 Handshake")
        println("   Input: 0x05 0x02 0x00 0x02")
        
        val handshakeData = byteArrayOf(0x05, 0x02, 0x00, 0x02)
        val buffer = ByteIndexedBuffer(handshakeData.toSeries())
        
        val result = parseHandshake(buffer)
        if (result != null) {
            println("   Parsed:")
            println("     Version: 0x%02X".format(result.version.toInt() and 0xFF))
            println("     Methods: ${result.methods.toDebugString()}")
            println("     Scan Count: ${result.scanCount}")
            
            val response = createHandshakeResponse(AUTH_METHOD_NO_AUTH)
            println("   Response: ${response.toDebugString()}")
        }
    }

    private fun demoRequest() {
        println("2. SOCKS5 Request")
        println("   Input: 0x05 0x01 0x00 0x01 0x7F 0x00 0x00 0x01 0x00 0x50")
        
        val requestData = byteArrayOf(
            0x05, 0x01, 0x00, 0x01,  // Version, CONNECT, Reserved, IPv4
            0x7F, 0x00, 0x00, 0x01,  // 127.0.0.1
            0x00, 0x50               // Port 80
        )
        val buffer = ByteIndexedBuffer(requestData.toSeries())
        
        val result = parseRequest(buffer)
        if (result != null) {
            println("   Parsed:")
            println("     Version: 0x%02X".format(result.version.toInt() and 0xFF))
            println("     Command: 0x%02X (CONNECT)".format(result.command.toInt() and 0xFF))
            println("     Address Type: 0x%02X (IPv4)".format(result.addressType.toInt() and 0xFF))
            println("     Destination: ${result.destinationAddress.toIpAddress()}:${result.destinationPort}")
            println("     Scan Count: ${result.scanCount}")
            
            val response = createRequestResponse(
                reply = REPLY_SUCCESS,
                addressType = result.addressType,
                boundAddress = result.destinationAddress,
                boundPort = result.destinationPort
            )
            println("   Response: ${response.toDebugString()}")
        }
    }

    private fun demoCompleteFlow() {
        println("3. Complete SOCKS5 Flow")
        
        var totalScans = 0
        
        // Handshake
        val handshakeData = byteArrayOf(0x05, 0x02, 0x00, 0x02)
        val handshakeBuffer = ByteIndexedBuffer(handshakeData.toSeries())
        val handshakeResult = parseHandshake(handshakeBuffer)
        
        if (handshakeResult != null) {
            totalScans += handshakeResult.scanCount
            println("   Handshake: ${handshakeResult.scanCount} scans")
        }
        
        // Request
        val requestData = byteArrayOf(
            0x05, 0x01, 0x00, 0x01,  // Version, CONNECT, Reserved, IPv4
            0x7F, 0x00, 0x00, 0x01,  // 127.0.0.1
            0x00, 0x50               // Port 80
        )
        val requestBuffer = ByteIndexedBuffer(requestData.toSeries())
        val requestResult = parseRequest(requestBuffer)
        
        if (requestResult != null) {
            totalScans += requestResult.scanCount
            println("   Request: ${requestResult.scanCount} scans")
        }
        
        println("   Total Scans: $totalScans")
        println("   Average Scans per Step: ${totalScans / 2}")
    }
} 