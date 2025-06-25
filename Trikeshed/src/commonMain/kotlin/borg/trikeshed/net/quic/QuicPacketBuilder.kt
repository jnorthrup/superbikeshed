package borg.trikeshed.net.quic

import borg.trikeshed.lib.*

/**
 * Build actual QUIC packets for HTTP/3 responses
 */
object QuicPacketBuilder {
    
    /**
     * Create HTTP/3 200 OK response packet
     */
    fun buildHttp3Response200(): QuicPacket {
        // Build HTTP/3 HEADERS frame
        val headers = buildHttp3Headers()
        
        // Create QUIC packet
        return QuicPacket(
            header = QuicHeader(
                type = QuicPacketType.SHORT_HEADER,
                version = 0x00000001,
                destinationConnectionId = ConnectionId(
                    bytes = byteArrayOf(
                        0x4D, 0x63, 0x44, 0x6F, 0x6E, 0x61, 0x6C, 0x64  // "McDonald"
                    ).let { it.size j { i -> it[i] } }
                ),
                sourceConnectionId = ConnectionId(
                    bytes = byteArrayOf(
                        0x54, 0x72, 0x69, 0x6B, 0x65, 0x53, 0x68, 0x64  // "TrikeSHd"
                    ).let { it.size j { i -> it[i] } }
                ),
                packetNumber = System.currentTimeMillis() and 0xFFFFFF
            ),
            frames = 1 j {
                StreamFrame(
                    streamId = 0,
                    offset = 0,
                    data = headers,
                    fin = true
                )
            },
            payload = headers
        )
    }
    
    private fun buildHttp3Headers(): Indexed<Byte> {
        val buffer = mutableListOf<Byte>()
        
        // HTTP/3 HEADERS frame
        buffer.add(0x01) // Type: HEADERS
        
        // QPACK encoded headers
        // :status = 200 (static table index 24)
        buffer.add(0x58.toByte()) // 0101 1000 - indexed header field
        buffer.add(0x18) // Index 24
        
        // content-type: text/html
        buffer.add(0x00) // Literal with name reference
        buffer.add(0x05) // Name index 5 (content-type)
        buffer.add(0x09) // Value length 9
        buffer.addAll("text/html".toByteArray().toList())
        
        // server: TrikeShed-QUIC
        buffer.add(0x00) // Literal with name reference  
        buffer.add(0x54) // Name index 84 (server)
        buffer.add(0x0E) // Value length 14
        buffer.addAll("TrikeShed-QUIC".toByteArray().toList())
        
        // date: [current date]
        val dateStr = "Sat, 22 Dec 2024 00:00:00 GMT"
        buffer.add(0x00) // Literal
        buffer.add(0x21) // Name index 33 (date)
        buffer.add(dateStr.length.toByte())
        buffer.addAll(dateStr.toByteArray().toList())
        
        // Body separator and content
        buffer.addAll("\r\n\r\n<h1>McDonald's WiFi Portal</h1>".toByteArray().toList())
        
        return buffer.size j { buffer[it] }
    }
    
    /**
     * Serialize packet to wire format
     */
    fun serializePacket(packet: QuicPacket): Indexed<Byte> {
        val buffer = mutableListOf<Byte>()
        
        // Header flags and type
        val headerByte = when (packet.header.type) {
            QuicPacketType.SHORT_HEADER -> 0x40
            QuicPacketType.INITIAL -> 0xC0.toByte()
            else -> 0x80.toByte()
        }
        buffer.add(headerByte)
        
        // Destination connection ID
        buffer.add(packet.header.destinationConnectionId.length.toByte())
        for (i in 0 until packet.header.destinationConnectionId.bytes.a) {
            buffer.add(packet.header.destinationConnectionId.bytes.b(i))
        }
        
        // Source connection ID  
        buffer.add(packet.header.sourceConnectionId.length.toByte())
        for (i in 0 until packet.header.sourceConnectionId.bytes.a) {
            buffer.add(packet.header.sourceConnectionId.bytes.b(i))
        }
        
        // Packet number (variable length)
        val pn = packet.header.packetNumber
        when {
            pn < 0x40 -> {
                buffer.add(pn.toByte())
            }
            pn < 0x4000 -> {
                buffer.add((0x40 or (pn shr 8)).toByte())
                buffer.add((pn and 0xFF).toByte())
            }
            else -> {
                buffer.add((0x80 or (pn shr 16)).toByte())
                buffer.add(((pn shr 8) and 0xFF).toByte())
                buffer.add((pn and 0xFF).toByte())
            }
        }
        
        // Payload
        for (i in 0 until packet.payload.a) {
            buffer.add(packet.payload.b(i))
        }
        
        return buffer.size j { buffer[it] }
    }
}