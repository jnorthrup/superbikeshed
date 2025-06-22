package borg.trikeshed.net

import borg.trikeshed.net.quic.*
import borg.trikeshed.lib.*

/**
 * QUIC curl example - how to test our QUIC server
 */
object QuicCurlExample {
    
    fun generateCurlCommands() {
        println("# Test TrikeShed QUIC server (HTTP/3)")
        println()
        
        // Using curl with HTTP/3 support
        println("# 1. Basic HTTP/3 request (requires curl 7.66+ with --http3 support)")
        println("curl --http3 https://localhost:4433/")
        println()
        
        // Using curl with QUIC draft versions
        println("# 2. Force QUIC v1")
        println("curl --http3 --tlsv1.3 --tls-max 1.3 https://localhost:4433/")
        println()
        
        // Test our McDonald's response
        println("# 3. Test McDonald's WiFi portal response")
        println("curl --http3 -H 'Host: mcdonalds.wifi' https://localhost:4433/portal")
        println()
        
        // Using nghttp3 client (more detailed QUIC info)
        println("# 4. Using nghttp3 client for detailed QUIC info")
        println("nghttp -n -v https://localhost:4433/")
        println()
        
        // Raw QUIC packet inspection
        println("# 5. Capture QUIC packets with tcpdump")
        println("sudo tcpdump -i lo -w quic.pcap 'udp port 4433'")
        println()
        
        // Using quiche-client
        println("# 6. Using quiche HTTP/3 client")
        println("quiche-client https://localhost:4433/ --no-verify")
        println()
        
        // Test with real McDonald's WiFi captive portal behavior
        println("# 7. Simulate McDonald's captive portal detection")
        println("curl --http3 -H 'User-Agent: CaptiveNetworkSupport' \\")
        println("     -H 'Host: captive.apple.com' \\")
        println("     https://localhost:4433/hotspot-detect.html")
        println()
        
        // Show our actual QUIC response
        println("# 8. What our server returns (hex dump):")
        val packet = QuicPacketBuilder.buildHttp3Response200()
        val serialized = QuicPacketBuilder.serializePacket(packet)
        
        println("# QUIC Packet (${serialized.a} bytes):")
        printHexDump(serialized)
        
        println()
        println("# 9. Send raw QUIC packet using netcat (UDP)")
        println("echo -n '${toHexString(serialized)}' | xxd -r -p | nc -u localhost 4433")
    }
    
    private fun printHexDump(data: Indexed<Byte>) {
        val bytesPerLine = 16
        var offset = 0
        
        while (offset < data.a) {
            // Print offset
            print(String.format("%08X  ", offset))
            
            // Print hex bytes
            for (i in 0 until bytesPerLine) {
                if (offset + i < data.a) {
                    print(String.format("%02X ", data.b(offset + i).toInt() and 0xFF))
                } else {
                    print("   ")
                }
                if (i == 7) print(" ")
            }
            
            print(" |")
            
            // Print ASCII
            for (i in 0 until bytesPerLine) {
                if (offset + i < data.a) {
                    val b = data.b(offset + i).toInt() and 0xFF
                    val c = if (b in 32..126) b.toChar() else '.'
                    print(c)
                } else {
                    print(" ")
                }
            }
            
            println("|")
            offset += bytesPerLine
        }
    }
    
    private fun toHexString(data: Indexed<Byte>): String {
        val sb = StringBuilder()
        for (i in 0 until data.a) {
            sb.append(String.format("%02x", data.b(i).toInt() and 0xFF))
        }
        return sb.toString()
    }
    
    @JvmStatic
    fun main(args: Array<String>) {
        generateCurlCommands()
    }
}