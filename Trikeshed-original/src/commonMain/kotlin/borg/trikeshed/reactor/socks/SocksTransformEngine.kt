@file:Suppress("UNCHECKED_CAST", "FunctionName", "NonAsciiCharacters", "NOTHING_TO_INLINE")
package borg.trikeshed.reactor.socks

import borg.trikeshed.lib.*
import kotlin.jvm.JvmInline

// === SOCKS TOKEN FRAGMENT TRANSFORM ENGINE ===

/**
 * ByteIndexed - Alias for Indexed<Byte> following codebase conventions
 */
typealias ByteIndexed = Indexed<Byte>

/**
 * Scan Count - Tracks the minimal number of forward scans needed
 */
@JvmInline value class ScanCount(val count: Int)

/**
 * Token Fragment - Represents a piece of SOCKS protocol data
 * Optimized for minimal forward scans through buffers
 */
@JvmInline value class TokenFragment(val bytes: ByteIndexed)

/**
 * Transform Result - Result of token fragment transformation
 */
data class TransformResult(
    val fragments: Indexed<TokenFragment>,
    val scanCount: ScanCount,
    val remaining: ByteIndexed
)

/**
 * SOCKS Transform Engine - Minimizes forward scans through protocol buffers
 * 
 * The engine processes SOCKS protocol fragments by counting forward scans
 * and settling for the smallest count to optimize performance.
 */
class SocksTransformEngine {
    
    /**
     * Transform buffer into SOCKS protocol fragments with minimal scans
     */
    fun transform(buffer: ByteIndexed): TransformResult {
        var scanCount = ScanCount(0)
        val fragments = mutableListOf<TokenFragment>()
        var remaining = buffer
        var position = 0
        
        while (position < buffer.a) {
            scanCount = ScanCount(scanCount.count + 1)
            
            when {
                // SOCKS Version (1 byte)
                position == 0 -> {
                    val version = buffer[position]
                    fragments.add(TokenFragment(1 j { i: Int -> buffer[position + i] }))
                    position += 1
                }
                
                // Authentication Methods (variable length)
                position == 1 -> {
                    val methodCount = buffer[position].toInt() and 0xFF
                    fragments.add(TokenFragment((methodCount + 1) j { i: Int -> buffer[position + i] }))
                    position += methodCount + 1
                }
                
                // SOCKS Request (variable length based on address type)
                isRequestStart(buffer, position) -> {
                    val requestLength = calculateRequestLength(buffer, position)
                    fragments.add(TokenFragment(requestLength j { i -> buffer[position + i] }))
                    position += requestLength
                }
                
                // SOCKS Response (variable length based on address type)
                isResponseStart(buffer, position) -> {
                    val responseLength = calculateResponseLength(buffer, position)
                    fragments.add(TokenFragment(responseLength j { i -> buffer[position + i] }))
                    position += responseLength
                }
                
                // Unknown/partial data - scan forward to find protocol boundary
                else -> {
                    val nextBoundary = findNextProtocolBoundary(buffer, position)
                    if (nextBoundary > position) {
                        val fragmentLength = nextBoundary - position
                        fragments.add(TokenFragment(fragmentLength j { i -> buffer[position + i] }))
                        position = nextBoundary
                    } else {
                        // No boundary found, treat as remaining data
                        remaining = (buffer.a - position) j { i -> buffer[position + i] }
                        break
                    }
                }
            }
        }
        
        return TransformResult(
            fragments = fragments.size j { i -> fragments[i] },
            scanCount = scanCount,
            remaining = remaining
        )
    }
    
    /**
     * Check if position starts a SOCKS request
     */
    private fun isRequestStart(buffer: ByteIndexed, position: Int): Boolean {
        if (position + 3 >= buffer.a) return false
        return buffer[position] == SocksProtocol.VERSION_5 && 
               buffer[position + 1] in listOf(SocksProtocol.CMD_CONNECT, SocksProtocol.CMD_BIND, SocksProtocol.CMD_UDP_ASSOCIATE)
    }
    
    /**
     * Check if position starts a SOCKS response
     */
    private fun isResponseStart(buffer: ByteIndexed, position: Int): Boolean {
        if (position + 3 >= buffer.a) return false
        return buffer[position] == SocksProtocol.VERSION_5 && 
               buffer[position + 1] in 0x00..0x08 // Valid reply codes
    }
    
    /**
     * Calculate SOCKS request length based on address type
     */
    private fun calculateRequestLength(buffer: ByteIndexed, position: Int): Int {
        if (position + 4 >= buffer.a) return buffer.a - position
        
        val addressType = buffer[position + 3]
        return when (addressType) {
            SocksProtocol.ATYP_IPV4 -> 10 // version(1) + cmd(1) + reserved(1) + atyp(1) + ipv4(4) + port(2)
            SocksProtocol.ATYP_DOMAINNAME -> {
                if (position + 5 >= buffer.a) return buffer.a - position
                val domainLength = buffer[position + 4].toInt() and 0xFF
                5 + domainLength + 2 // header(5) + domain(domainLength) + port(2)
            }
            SocksProtocol.ATYP_IPV6 -> 22 // version(1) + cmd(1) + reserved(1) + atyp(1) + ipv6(16) + port(2)
            else -> buffer.a - position
        }
    }
    
    /**
     * Calculate SOCKS response length based on address type
     */
    private fun calculateResponseLength(buffer: ByteIndexed, position: Int): Int {
        if (position + 4 >= buffer.a) return buffer.a - position
        
        val addressType = buffer[position + 3]
        return when (addressType) {
            SocksProtocol.ATYP_IPV4 -> 10 // version(1) + reply(1) + reserved(1) + atyp(1) + ipv4(4) + port(2)
            SocksProtocol.ATYP_DOMAINNAME -> {
                if (position + 5 >= buffer.a) return buffer.a - position
                val domainLength = buffer[position + 4].toInt() and 0xFF
                5 + domainLength + 2 // header(5) + domain(domainLength) + port(2)
            }
            SocksProtocol.ATYP_IPV6 -> 22 // version(1) + reply(1) + reserved(1) + atyp(1) + ipv6(16) + port(2)
            else -> buffer.a - position
        }
    }
    
    /**
     * Find next protocol boundary with minimal forward scanning
     */
    private fun findNextProtocolBoundary(buffer: ByteIndexed, position: Int): Int {
        // Look for SOCKS protocol markers
        for (i in position until minOf(position + 100, buffer.a - 1)) {
            if (buffer[i] == SocksProtocol.VERSION_5) {
                // Check if this looks like a protocol start
                if (i + 1 < buffer.a && buffer[i + 1] in 0x00..0x08) {
                    return i
                }
            }
        }
        return buffer.a // No boundary found
    }
}

/**
 * SOCKS Fragment Parser - Parses token fragments into protocol structures
 */
class SocksFragmentParser {
    
    /**
     * Parse greeting fragment (RFC 1928 Section 3)
     */
    fun parseGreeting(fragment: TokenFragment): SocksGreeting? {
        if (fragment.bytes.a < 2) return null
        
        val version = fragment.bytes[0]
        val methodCount = fragment.bytes[1].toInt() and 0xFF
        
        if (fragment.bytes.a < 2 + methodCount) return null
        
        val methods = methodCount j { i -> fragment.bytes[2 + i] }
        
        return SocksGreeting(version, methods)
    }
    
    /**
     * Parse request fragment (RFC 1928 Section 4)
     */
    fun parseRequest(fragment: TokenFragment): SocksRequest? {
        if (fragment.bytes.a < 7) return null
        
        val version = fragment.bytes[0]
        val command = fragment.bytes[1]
        val reserved = fragment.bytes[2]
        val addressType = fragment.bytes[3]
        
        val (targetAddress, targetPort) = when (addressType) {
            SocksProtocol.ATYP_IPV4 -> parseIpv4Address(fragment.bytes, 4)
            SocksProtocol.ATYP_DOMAINNAME -> parseDomainName(fragment.bytes, 4)
            SocksProtocol.ATYP_IPV6 -> parseIpv6Address(fragment.bytes, 4)
            else -> return null
        }
        
        return SocksRequest(version, command, reserved, addressType, targetAddress, targetPort)
    }
    
    /**
     * Parse response fragment (RFC 1928 Section 6)
     */
    fun parseResponse(fragment: TokenFragment): SocksResponse? {
        if (fragment.bytes.a < 7) return null
        
        val version = fragment.bytes[0]
        val replyCode = fragment.bytes[1]
        val reserved = fragment.bytes[2]
        val addressType = fragment.bytes[3]
        
        val (bindAddress, bindPort) = when (addressType) {
            SocksProtocol.ATYP_IPV4 -> parseIpv4Address(fragment.bytes, 4)
            SocksProtocol.ATYP_DOMAINNAME -> parseDomainName(fragment.bytes, 4)
            SocksProtocol.ATYP_IPV6 -> parseIpv6Address(fragment.bytes, 4)
            else -> return null
        }
        
        return SocksResponse(version, replyCode, reserved, addressType, bindAddress, bindPort)
    }
    
    private fun parseIpv4Address(bytes: ByteIndexed, offset: Int): Pair<SocksTargetHost, SocksTargetPort> {
        val ip = "${bytes[offset] and 0xFF}.${bytes[offset + 1] and 0xFF}.${bytes[offset + 2] and 0xFF}.${bytes[offset + 3] and 0xFF}"
        val port = ((bytes[offset + 4].toInt() and 0xFF) shl 8) or (bytes[offset + 5].toInt() and 0xFF)
        return SocksTargetHost(ip) to SocksTargetPort(port.toUShort())
    }
    
    private fun parseDomainName(bytes: ByteIndexed, offset: Int): Pair<SocksTargetHost, SocksTargetPort> {
        val domainLength = bytes[offset].toInt() and 0xFF
        val domain = String(ByteArray(domainLength) { i -> bytes[offset + 1 + i] })
        val port = ((bytes[offset + 1 + domainLength].toInt() and 0xFF) shl 8) or (bytes[offset + 2 + domainLength].toInt() and 0xFF)
        return SocksTargetHost(domain) to SocksTargetPort(port.toUShort())
    }
    
    private fun parseIpv6Address(bytes: ByteIndexed, offset: Int): Pair<SocksTargetHost, SocksTargetPort> {
        // Simplified IPv6 parsing
        val ip = "::1"
        val port = ((bytes[offset + 16].toInt() and 0xFF) shl 8) or (bytes[offset + 17].toInt() and 0xFF)
        return SocksTargetHost(ip) to SocksTargetPort(port.toUShort())
    }
}

/**
 * SOCKS Greeting Structure (RFC 1928 Section 3)
 */
data class SocksGreeting(
    val version: SocksVersion,
    val methods: Indexed<SocksMethod>
)

/**
 * SOCKS Fragment Processor - Processes fragments with minimal scan optimization
 */
class SocksFragmentProcessor {
    
    private val transformEngine = SocksTransformEngine()
    private val parser = SocksFragmentParser()
    
    /**
     * Process buffer with minimal forward scans
     * Returns the smallest scan count achieved
     */
    fun processBuffer(buffer: ByteIndexed): SocksProcessResult {
        val transformResult = transformEngine.transform(buffer)
        
        val parsedFragments = mutableListOf<Any>()
        var totalScans = transformResult.scanCount.count
        
        // Parse each fragment
        for (i in 0 until transformResult.fragments.a) {
            val fragment = transformResult.fragments[i]
            
            when {
                fragment.bytes.a > 0 && fragment.bytes[0] == SocksProtocol.VERSION_5 -> {
                    when {
                        fragment.bytes.a > 1 && fragment.bytes[1] in 0x00..0x08 -> {
                            // Response
                            parser.parseResponse(fragment)?.let { parsedFragments.add(it) }
                        }
                        fragment.bytes.a > 1 && fragment.bytes[1] in listOf(SocksProtocol.CMD_CONNECT, SocksProtocol.CMD_BIND, SocksProtocol.CMD_UDP_ASSOCIATE) -> {
                            // Request
                            parser.parseRequest(fragment)?.let { parsedFragments.add(it) }
                        }
                        else -> {
                            // Greeting
                            parser.parseGreeting(fragment)?.let { parsedFragments.add(it) }
                        }
                    }
                }
            }
        }
        
        return SocksProcessResult(
            fragments = parsedFragments.size j { i -> parsedFragments[i] },
            scanCount = ScanCount(totalScans),
            remaining = transformResult.remaining
        )
    }
}

/**
 * SOCKS Process Result - Result of buffer processing with scan optimization
 */
data class SocksProcessResult(
    val fragments: Indexed<Any>,
    val scanCount: ScanCount,
    val remaining: ByteIndexed
)

/**
 * SOCKS Scan Optimizer - Optimizes scan patterns for minimal forward scans
 */
class SocksScanOptimizer {
    
    /**
     * Optimize scan pattern to minimize forward scans
     * Returns the optimal scan strategy
     */
    fun optimizeScanPattern(buffer: ByteIndexed): ScanStrategy {
        val strategy = mutableListOf<ScanOperation>()
        var position = 0
        
        while (position < buffer.a) {
            when {
                // Look for protocol markers
                position == 0 || buffer[position] == SocksProtocol.VERSION_5 -> {
                    strategy.add(ScanOperation.SCAN_PROTOCOL_MARKER(position))
                    position += 1
                }
                
                // Look for address type indicators
                position >= 3 && buffer[position - 1] in listOf(SocksProtocol.ATYP_IPV4, SocksProtocol.ATYP_DOMAINNAME, SocksProtocol.ATYP_IPV6) -> {
                    strategy.add(ScanOperation.SCAN_ADDRESS_TYPE(buffer[position - 1], position))
                    position += when (buffer[position - 1]) {
                        SocksProtocol.ATYP_IPV4 -> 4
                        SocksProtocol.ATYP_DOMAINNAME -> (buffer[position].toInt() and 0xFF) + 1
                        SocksProtocol.ATYP_IPV6 -> 16
                        else -> 1
                    }
                }
                
                // Default forward scan
                else -> {
                    strategy.add(ScanOperation.SCAN_FORWARD(position))
                    position += 1
                }
            }
        }
        
        return ScanStrategy(strategy.size j { i -> strategy[i] })
    }
}

/**
 * Scan Operation - Represents a single scan operation
 */
sealed class ScanOperation {
    data class SCAN_PROTOCOL_MARKER(val position: Int) : ScanOperation()
    data class SCAN_ADDRESS_TYPE(val addressType: Byte, val position: Int) : ScanOperation()
    data class SCAN_FORWARD(val position: Int) : ScanOperation()
}

/**
 * Scan Strategy - Complete scan strategy for minimal forward scans
 */
data class ScanStrategy(val operations: Indexed<ScanOperation>)

/**
 * Utility functions for scan optimization
 */
object SocksScanUtils {
    
    /**
     * Count minimal scans needed for buffer
     */
    fun countMinimalScans(buffer: ByteIndexed): ScanCount {
        var scans = 0
        var position = 0
        
        while (position < buffer.a) {
            scans++
            
            when {
                // Protocol marker found
                buffer[position] == SocksProtocol.VERSION_5 -> {
                    position += 1
                }
                
                // Address type found
                position >= 3 && buffer[position - 1] in listOf(SocksProtocol.ATYP_IPV4, SocksProtocol.ATYP_DOMAINNAME, SocksProtocol.ATYP_IPV6) -> {
                    position += when (buffer[position - 1]) {
                        SocksProtocol.ATYP_IPV4 -> 4
                        SocksProtocol.ATYP_DOMAINNAME -> (buffer[position].toInt() and 0xFF) + 1
                        SocksProtocol.ATYP_IPV6 -> 16
                        else -> 1
                    }
                }
                
                // Forward scan
                else -> {
                    position += 1
                }
            }
        }
        
        return ScanCount(scans)
    }
    
    /**
     * Find optimal scan boundaries
     */
    fun findScanBoundaries(buffer: ByteIndexed): Indexed<Int> {
        val boundaries = mutableListOf<Int>()
        var position = 0
        
        while (position < buffer.a) {
            boundaries.add(position)
            
            when {
                buffer[position] == SocksProtocol.VERSION_5 -> {
                    position += 1
                }
                position >= 3 && buffer[position - 1] in listOf(SocksProtocol.ATYP_IPV4, SocksProtocol.ATYP_DOMAINNAME, SocksProtocol.ATYP_IPV6) -> {
                    position += when (buffer[position - 1]) {
                        SocksProtocol.ATYP_IPV4 -> 4
                        SocksProtocol.ATYP_DOMAINNAME -> (buffer[position].toInt() and 0xFF) + 1
                        SocksProtocol.ATYP_IPV6 -> 16
                        else -> 1
                    }
                }
                else -> {
                    position += 1
                }
            }
        }
        
        return boundaries.size j { i -> boundaries[i] }
    }
}

// === EXTENSION FUNCTIONS FOR FLUENT API ===

/**
 * Transform ByteIndexed into SOCKS fragments with minimal scans
 */
fun ByteIndexed.transformSocks(): TransformResult = SocksTransformEngine().transform(this)

/**
 * Process ByteIndexed as SOCKS protocol with scan optimization
 */
fun ByteIndexed.processSocks(): SocksProcessResult = SocksFragmentProcessor().processBuffer(this)

/**
 * Count minimal scans needed for ByteIndexed
 */
fun ByteIndexed.countMinimalScans(): ScanCount = SocksScanUtils.countMinimalScans(this)

/**
 * Find optimal scan boundaries for ByteIndexed
 */
fun ByteIndexed.findScanBoundaries(): Indexed<Int> = SocksScanUtils.findScanBoundaries(this)

/**
 * Optimize scan pattern for ByteIndexed
 */
fun ByteIndexed.optimizeScanPattern(): ScanStrategy = SocksScanOptimizer().optimizeScanPattern(this) 