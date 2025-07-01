package borg.trikeshed.reactor.socks

import borg.trikeshed.lib.*

/**
 * Iterative SOCKS5 Protocol Parsing with ByteIndexedBuffer
 * 
 * Demonstrates:
 * 1. Iterative fragment parsing
 * 2. Automatic scan optimization
 * 3. Fragment composition
 * 4. Protocol state machine
 */
object Socks5Iterative {

    // === PROTOCOL FRAGMENT TYPES ===
    
    enum class FragmentType {
        HANDSHAKE_REQUEST,
        HANDSHAKE_RESPONSE,
        SOCKS_REQUEST,
        SOCKS_RESPONSE,
        INCOMPLETE
    }

    enum class ProtocolState {
        INITIAL,
        HANDSHAKE_REQUEST,
        HANDSHAKE_RESPONSE,
        SOCKS_REQUEST,
        SOCKS_RESPONSE,
        CONNECTED,
        ERROR
    }

    // === PROTOCOL CONSTANTS ===
    
    const val SOCKS_VERSION_5: Byte = 0x05
    const val AUTH_METHOD_NO_AUTH: Byte = 0x00
    const val CMD_CONNECT: Byte = 0x01
    const val ATYP_IPV4: Byte = 0x01
    const val REPLY_SUCCESS: Byte = 0x00

    // === ITERATIVE PARSING STRUCTURE ===

    /**
     * Parse result with automatic scan counting
     */
    data class ParseResult<T>(
        val value: T,
        val scanCount: Int,
        val nextState: ProtocolState? = null
    )

    /**
     * Protocol fragment with composition support
     */
    sealed class ProtocolFragment(
        open val fragmentType: FragmentType,
        open val scanCount: Int,
        open val startPos: Int,
        open val endPos: Int
    ) {
        data class HandshakeRequest(
            val version: Byte,
            val methods: ByteIndexed,
            override val scanCount: Int,
            override val startPos: Int,
            override val endPos: Int
        ) : ProtocolFragment(FragmentType.HANDSHAKE_REQUEST, scanCount, startPos, endPos)

        data class SocksRequest(
            val version: Byte,
            val command: Byte,
            val addressType: Byte,
            val destinationAddress: ByteIndexed,
            val destinationPort: Int,
            override val scanCount: Int,
            override val startPos: Int,
            override val endPos: Int
        ) : ProtocolFragment(FragmentType.SOCKS_REQUEST, scanCount, startPos, endPos)

        data class Incomplete(
            override val scanCount: Int,
            override val startPos: Int,
            override val endPos: Int
        ) : ProtocolFragment(FragmentType.INCOMPLETE, scanCount, startPos, endPos)
    }

    // === ITERATIVE PARSING FUNCTIONS ===

    /**
     * Detect fragment type with minimal scanning
     */
    fun detectFragmentType(buffer: ByteIndexedBuffer): FragmentType {
        if (buffer.rem < 1) return FragmentType.INCOMPLETE
        
        val version = buffer.b(buffer.pos)
        if (version != SOCKS_VERSION_5) return FragmentType.INCOMPLETE
        
        if (buffer.rem < 2) return FragmentType.INCOMPLETE
        
        val secondByte = buffer.b(buffer.pos + 1)
        return when {
            secondByte in 0x01..0xFF -> FragmentType.HANDSHAKE_REQUEST
            secondByte == CMD_CONNECT -> FragmentType.SOCKS_REQUEST
            else -> FragmentType.INCOMPLETE
        }
    }

    /**
     * Parse fragment iteratively with automatic scan optimization
     */
    fun parseFragment(buffer: ByteIndexedBuffer): ProtocolFragment {
        val startPos = buffer.pos
        val fragmentType = detectFragmentType(buffer)
        
        return when (fragmentType) {
            FragmentType.HANDSHAKE_REQUEST -> parseHandshakeFragment(buffer, startPos)
            FragmentType.SOCKS_REQUEST -> parseRequestFragment(buffer, startPos)
            FragmentType.INCOMPLETE -> ProtocolFragment.Incomplete(0, startPos, buffer.pos)
            else -> ProtocolFragment.Incomplete(0, startPos, buffer.pos)
        }
    }

    /**
     * Parse handshake fragment iteratively
     */
    private fun parseHandshakeFragment(buffer: ByteIndexedBuffer, startPos: Int): ProtocolFragment.HandshakeRequest {
        // Parse version
        val version = buffer.get
        
        // Parse method count
        val methodCount = buffer.get.toInt() and 0xFF
        
        // Parse methods iteratively
        val methods = parseMethodsIterative(buffer, methodCount)
        
        val scanCount = buffer.pos - startPos
        return ProtocolFragment.HandshakeRequest(
            version = version,
            methods = methods,
            scanCount = scanCount,
            startPos = startPos,
            endPos = buffer.pos
        )
    }

    /**
     * Parse methods iteratively
     */
    private fun parseMethodsIterative(
        buffer: ByteIndexedBuffer, 
        count: Int
    ): ByteIndexed {
        val methods = mutableListOf<Byte>()
        
        for (i in 0 until count) {
            if (buffer.hasRemaining) {
                methods.add(buffer.get)
            }
        }
        
        return methods.size j { i -> methods[i] }
    }

    /**
     * Parse request fragment iteratively
     */
    private fun parseRequestFragment(buffer: ByteIndexedBuffer, startPos: Int): ProtocolFragment.SocksRequest {
        // Parse header iteratively
        val headerResult = parseRequestHeader(buffer)
        val addressType = headerResult.value
        
        // Parse address iteratively based on type
        val (address, port) = parseAddressIterative(buffer, addressType)
        
        val scanCount = buffer.pos - startPos
        return ProtocolFragment.SocksRequest(
            version = Socks5Iterative.SOCKS_VERSION_5,
            command = Socks5Iterative.CMD_CONNECT,
            addressType = addressType,
            destinationAddress = address,
            destinationPort = port,
            scanCount = scanCount,
            startPos = startPos,
            endPos = buffer.pos
        )
    }

    /**
     * Parse request header iteratively
     */
    private fun parseRequestHeader(buffer: ByteIndexedBuffer): ParseResult<Byte> {
        val version = buffer.get
        val command = buffer.get
        val reserved = buffer.get
        val addressType = buffer.get
        
        return ParseResult(
            value = addressType,
            scanCount = 4,
            nextState = ProtocolState.SOCKS_REQUEST
        )
    }

    /**
     * Parse address iteratively based on type
     */
    private fun parseAddressIterative(buffer: ByteIndexedBuffer, addressType: Byte): Pair<ByteIndexed, Int> {
        return when (addressType) {
            ATYP_IPV4 -> parseIPv4Address(buffer)
            else -> parseUnknownAddress(buffer)
        }
    }

    /**
     * Parse IPv4 address iteratively
     */
    private fun parseIPv4Address(buffer: ByteIndexedBuffer): Pair<ByteIndexed, Int> {
        val address = 4 j { i -> buffer.get }
        val port = parsePortIterative(buffer)
        return Pair(address, port)
    }

    /**
     * Parse port iteratively
     */
    private fun parsePortIterative(buffer: ByteIndexedBuffer): Int {
        var result = 0
        
        // Read first byte (high byte)
        if (buffer.hasRemaining) {
            val highByte = buffer.get.toInt() and 0xFF
            result = highByte shl 8
        }
        
        // Read second byte (low byte)
        if (buffer.hasRemaining) {
            val lowByte = buffer.get.toInt() and 0xFF
            result = result or lowByte
        }
        
        return result
    }

    /**
     * Parse unknown address type
     */
    private fun parseUnknownAddress(buffer: ByteIndexedBuffer): Pair<ByteIndexed, Int> {
        return Pair(0 j { 0x00 }, 0)
    }

    // === FRAGMENT COMPOSITION ===

    /**
     * Compose protocol from fragments iteratively
     */
    fun composeProtocol(
        fragments: Indexed<ProtocolFragment>
    ): CompleteProtocol {
        var totalScans = 0
        var currentState = ProtocolState.INITIAL
        
        for (i in 0 until fragments.a) {
            val fragment = fragments[i]
            totalScans += fragment.scanCount
            
            currentState = when (fragment) {
                is ProtocolFragment.HandshakeRequest -> ProtocolState.HANDSHAKE_REQUEST
                is ProtocolFragment.SocksRequest -> ProtocolState.SOCKS_REQUEST
                else -> ProtocolState.ERROR
            }
        }
        
        return CompleteProtocol(fragments, totalScans, currentState)
    }

    /**
     * Complete protocol composition
     */
    data class CompleteProtocol(
        val fragments: Indexed<ProtocolFragment>,
        val totalScans: Int,
        val finalState: ProtocolState
    )

    // === PROTOCOL STATE MACHINE ===

    /**
     * Process protocol evolution with state machine
     */
    fun processProtocolEvolution(
        buffer: ByteIndexedBuffer,
        currentState: ProtocolState
    ): ParseResult<ProtocolFragment> {
        val startPos = buffer.pos
        
        return when (currentState) {
            ProtocolState.INITIAL -> {
                val fragment = parseFragment(buffer)
                val scanCount = buffer.pos - startPos
                ParseResult(fragment, scanCount, ProtocolState.HANDSHAKE_REQUEST)
            }
            ProtocolState.HANDSHAKE_REQUEST -> {
                val fragment = parseFragment(buffer)
                val scanCount = buffer.pos - startPos
                ParseResult(fragment, scanCount, ProtocolState.SOCKS_REQUEST)
            }
            ProtocolState.SOCKS_REQUEST -> {
                val fragment = parseFragment(buffer)
                val scanCount = buffer.pos - startPos
                ParseResult(fragment, scanCount, ProtocolState.CONNECTED)
            }
            else -> {
                ParseResult(
                    ProtocolFragment.Incomplete(0, startPos, buffer.pos),
                    0,
                    ProtocolState.ERROR
                )
            }
        }
    }

    // === UTILITY FUNCTIONS ===

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

    // === DEMO FUNCTIONS ===

    /**
     * Run iterative SOCKS5 demo
     */
    fun runIterativeDemo() {
        println("=== Iterative SOCKS5 Evolution Demo ===")
        println()
        
        // Demo iterative parsing
        demoIterativeParsing()
        println()
        
        // Demo fragment composition
        demoFragmentComposition()
        println()
        
        // Demo state machine evolution
        demoStateMachineEvolution()
    }

    private fun demoIterativeParsing() {
        println("1. Iterative Fragment Parsing")
        
        val handshakeData = byteArrayOf(0x05, 0x02, 0x00, 0x02)
        val buffer = ByteIndexedBuffer(handshakeData.toIndexed())
        
        val fragment = parseFragment(buffer)
        println("   Fragment Type: ${fragment.fragmentType}")
        println("   Scan Count: ${fragment.scanCount}")
        println("   Position Range: ${fragment.startPos}..${fragment.endPos}")
        
        if (fragment is ProtocolFragment.HandshakeRequest) {
            println("   Methods: ${fragment.methods.toDebugString()}")
        }
    }

    private fun demoFragmentComposition() {
        println("2. Fragment Composition")
        
        val fragments = mutableListOf<ProtocolFragment>()
        
        // Add handshake fragment
        val handshakeData = byteArrayOf(0x05, 0x02, 0x00, 0x02)
        val handshakeBuffer = ByteIndexedBuffer(handshakeData.toIndexed())
        fragments.add(parseFragment(handshakeBuffer))
        
        // Add request fragment
        val requestData = byteArrayOf(0x05, 0x01, 0x00, 0x01, 0x7F, 0x00, 0x00, 0x01, 0x00, 0x50)
        val requestBuffer = ByteIndexedBuffer(requestData.toIndexed())
        fragments.add(parseFragment(requestBuffer))
        
        val protocol = composeProtocol(fragments.toIndexed())
        println("   Total Fragments: ${protocol.fragments.a}")
        println("   Total Scans: ${protocol.totalScans}")
        println("   Final State: ${protocol.finalState}")
    }

    private fun demoStateMachineEvolution() {
        println("3. State Machine Evolution")
        
        val buffer = ByteIndexedBuffer(byteArrayOf(0x05, 0x02, 0x00, 0x02).toIndexed())
        var currentState = ProtocolState.INITIAL
        
        val result = processProtocolEvolution(buffer, currentState)
        println("   Initial State: $currentState")
        println("   Next State: ${result.nextState}")
        println("   Scan Count: ${result.scanCount}")
        println("   Fragment Type: ${result.value.fragmentType}")
    }
} 