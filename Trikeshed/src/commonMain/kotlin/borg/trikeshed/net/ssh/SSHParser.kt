package borg.trikeshed.net.ssh

import borg.trikeshed.lib.ByteIndexedBuffer
import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.toUInt
import borg.trikeshed.parse.bbcursive.UnaryOperator
import borg.trikeshed.parse.bbcursive.std.bb

// Parser for SSHPacket
object SSHPacketParser {

    private fun ByteIndexedBuffer.readUInt32(): Pair<UInt, ByteIndexedBuffer>? {
        if (this.rem < 4) return null
        val value = this.get().toUInt() shl 24 or
                (this.get().toUInt() shl 16) or
                (this.get().toUInt() shl 8) or
                this.get().toUInt()
        return Pair(value, this)
    }

    private fun ByteIndexedBuffer.readByte(): Pair<Byte, ByteIndexedBuffer>? {
        if (this.rem < 1) return null
        val value = this.get()
        return Pair(value, this)
    }

    private fun ByteIndexedBuffer.readBytes(count: Int): Pair<Indexed<Byte>, ByteIndexedBuffer>? {
        if (this.rem < count) return null
        val value = this.slice(this.pos, count)
        this.pos(this.pos + count)
        return Pair(value, this)
    }

    suspend fun parse(buffer: ByteIndexedBuffer): SSHPacket? {
        var currentBuffer: ByteIndexedBuffer = buffer

        // Packet Length (uint32)
        val (packetLength, buf1) = currentBuffer.readUInt32() ?: return null
        currentBuffer = buf1

        // Padding Length (byte)
        val (paddingLength, buf2) = currentBuffer.readByte() ?: return null
        currentBuffer = buf2

        // Payload and Padding
        val payloadAndPaddingLength = packetLength.toInt() - 1 // -1 for padding_length field
        val (payloadAndPadding, buf3) = currentBuffer.readBytes(payloadAndPaddingLength) ?: return null
        currentBuffer = buf3

        val payload = payloadAndPadding.slice(0, payloadAndPaddingLength - paddingLength)
        val padding = payloadAndPadding.slice(payloadAndPaddingLength - paddingLength, paddingLength)

        // MAC (if present, not part of packetLength)
        // For now, assume MAC is handled externally or not present in initial parsing
        val mac = 0 j { 0.toByte() } // Placeholder

        return SSHPacket(
            packetLength = packetLength,
            paddingLength = paddingLength,
            payload = payload,
            padding = padding,
            mac = mac
        )
    }
}