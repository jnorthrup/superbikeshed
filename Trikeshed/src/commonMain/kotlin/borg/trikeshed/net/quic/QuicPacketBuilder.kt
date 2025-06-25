package borg.trikeshed.net.quic

import borg.trikeshed.lib.*

/**
 * Minimal placeholder QUIC packet builder for compilation
 */
class QuicPacketBuilder {
    fun buildPacket(data: Indexed<Byte>): QuicPacket {
        // Placeholder implementation
        return QuicPacket(
            header = QuicHeader(
                type = QuicPacketType.SHORT_HEADER,
                version = 1L,
                destinationConnectionId = ConnectionId(8 j { 0.toByte() }),
                sourceConnectionId = ConnectionId(8 j { 0.toByte() }),
                packetNumber = 0L
            ),
            frames = 1 j { 
                StreamFrame(
                    streamId = 1L,
                    offset = 0L,
                    data = data,
                    fin = false
                )
            },
            payload = data
        )
    }
} 