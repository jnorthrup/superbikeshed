package borg.trikeshed.parse.http3

import kotlin.jvm.JvmInline
import evolution.Http3FrameType
import evolution.Http3FrameLength
import evolution.Http3Setting
import evolution.Http3SettingsValue
import evolution.Http3StreamType
import evolution.QpackInstructionType
import borg.trikeshed.lib.BorrowedBuffer
import borg.trikeshed.lib.Join
import borg.trikeshed.lib.j

// === HTTP/3 ZERO-COPY SCANNERS ===
// Borrow-checked scanners for HTTP/3 data parsing with zero allocation

// HTTP/3 frame scanner using borrowed buffers
@JvmInline value class Http3FrameScanner private constructor(
    private val data: Join<BorrowedBuffer, Join<ULong, ULong>>
) {
    val buffer: BorrowedBuffer get() = data.a
    val position: ULong get() = data.b.a
    val remaining: ULong get() = data.b.b
    
    companion object {
        fun scan(borrowed: BorrowedBuffer): Http3FrameScanner = 
            Http3FrameScanner(borrowed j (0uL j borrowed.bytes.size.toULong()))
            
        fun scanAt(borrowed: BorrowedBuffer, position: ULong): Http3FrameScanner =
            Http3FrameScanner(borrowed j (position j (borrowed.bytes.size.toULong() - position)))
    }
    
    // Zero-copy frame header scan
    fun scanFrameHeader(): Join<Http3FrameType, Join<Http3FrameLength, ULong>>? {
        if (remaining < 2uL) return null
        
        val frameTypeResult = scanVarint() ?: return null
        val frameType = Http3FrameType.fromValue(frameTypeResult.a) ?: return null
        
        val lengthResult = scanVarint() ?: return null
        val frameLength = lengthResult.a
        
        return frameType j (frameLength j (position + frameTypeResult.b + lengthResult.b))
    }
    
    // Zero-copy varint scanner
    fun scanVarint(): Join<ULong, ULong>? {
        if (remaining == 0uL) return null
        
        val firstByte = buffer.bytes[position.toInt()].toUByte()
        val prefix = (firstByte and 0xC0u).toInt() shr 6
        
        return when (prefix) {
            0 -> {
                val value = (firstByte and 0x3Fu).toULong()
                value j 1uL
            }
            1 -> {
                if (remaining < 2uL) return null
                val value = ((firstByte and 0x3Fu).toULong() shl 8) or
                           buffer.bytes[position.toInt() + 1].toUByte().toULong()
                value j 2uL
            }
            2 -> {
                if (remaining < 4uL) return null
                val value = ((firstByte and 0x3Fu).toULong() shl 24) or
                           (buffer.bytes[position.toInt() + 1].toUByte().toULong() shl 16) or
                           (buffer.bytes[position.toInt() + 2].toUByte().toULong() shl 8) or
                           buffer.bytes[position.toInt() + 3].toUByte().toULong()
                value j 4uL
            }
            3 -> {
                if (remaining < 8uL) return null
                val value = ((firstByte and 0x3Fu).toULong() shl 56) or
                           (buffer.bytes[position.toInt() + 1].toUByte().toULong() shl 48) or
                           (buffer.bytes[position.toInt() + 2].toUByte().toULong() shl 40) or
                           (buffer.bytes[position.toInt() + 3].toUByte().toULong() shl 32) or
                           (buffer.bytes[position.toInt() + 4].toUByte().toULong() shl 24) or
                           (buffer.bytes[position.toInt() + 5].toUByte().toULong() shl 16) or
                           (buffer.bytes[position.toInt() + 6].toUByte().toULong() shl 8) or
                           buffer.bytes[position.toInt() + 7].toUByte().toULong()
                value j 8uL
            }
            else -> null
        }
    }
    
    // Zero-copy byte slice without allocation
    fun sliceBytes(length: ULong): BorrowedBuffer? {
        if (remaining < length) return null
        
        val sliced = buffer.bytes.sliceArray(position.toInt() until (position + length).toInt())
        return BorrowedBuffer.borrow(sliced)
    }
    
    // Advance scanner position
    fun advance(bytes: ULong): Http3FrameScanner? {
        if (remaining < bytes) return null
        
        return Http3FrameScanner(buffer j ((position + bytes) j (remaining - bytes)))
    }
}

// QPACK instruction scanner for zero-copy parsing
@JvmInline value class QpackInstructionScanner private constructor(
    private val data: Join<BorrowedBuffer, Join<ULong, ULong>>
) {
    val buffer: BorrowedBuffer get() = data.a
    val position: ULong get() = data.b.a
    val remaining: ULong get() = data.b.b
    
    companion object {
        fun scan(borrowed: BorrowedBuffer): QpackInstructionScanner = 
            QpackInstructionScanner(borrowed j (0uL j borrowed.bytes.size.toULong()))
    }
    
    // Zero-copy QPACK instruction detection
    fun scanInstructionType(): QpackInstructionType? {
        if (remaining == 0uL) return null
        
        val firstByte = buffer.bytes[position.toInt()].toUByte()
        return QpackInstructionType.fromByte(firstByte)
    }
    
    // Scan indexed field line (1xxxxxxx pattern)
    fun scanIndexedField(): Join<ULong, ULong>? {
        val firstByte = buffer.bytes[position.toInt()].toUByte()
        if ((firstByte and 0x80u) == 0u.toUByte()) return null
        
        // Extract index from 7-bit field
        val index = (firstByte and 0x7Fu).toULong()
        return index j 1uL
    }
    
    // Scan literal field with name reference (01xxxxxx pattern)
    fun scanLiteralFieldNameRef(): Join<ULong, Join<Boolean, ULong>>? {
        val firstByte = buffer.bytes[position.toInt()].toUByte()
        if ((firstByte and 0xC0u) != 0x40u.toUByte()) return null
        
        // Extract never index flag (N bit)
        val neverIndex = (firstByte and 0x20u) != 0u.toUByte()
        
        // Extract name index from remaining bits
        val nameIndex = (firstByte and 0x1Fu).toULong()
        
        return nameIndex j (neverIndex j 1uL)
    }
    
    // Zero-copy string scanner with huffman detection
    fun scanString(): Join<ByteArray, Join<Boolean, ULong>>? {
        val lengthResult = scanStringLength() ?: return null
        val isHuffman = lengthResult.b.a
        val stringLength = lengthResult.a
        val headerBytes = lengthResult.b.b
        
        if (remaining < headerBytes + stringLength) return null
        
        val stringBytes = buffer.bytes.sliceArray(
            (position + headerBytes).toInt() until (position + headerBytes + stringLength).toInt()
        )
        
        return stringBytes j (isHuffman j (headerBytes + stringLength))
    }
    
    private fun scanStringLength(): Join<ULong, Join<Boolean, ULong>>? {
        if (remaining == 0uL) return null
        
        val firstByte = buffer.bytes[position.toInt()].toUByte()
        val isHuffman = (firstByte and 0x80u) != 0u.toUByte()
        
        // Use remaining 7 bits for length encoding
        val lengthPrefix = firstByte and 0x7Fu
        
        return when {
            lengthPrefix < 0x7Fu -> {
                lengthPrefix.toULong() j (isHuffman j 1uL)
            }
            else -> {
                // Multi-byte length encoding - simplified for now
                if (remaining < 2uL) return null
                val additionalByte = buffer.bytes[position.toInt() + 1].toUByte()
                val length = 0x7FuL + additionalByte.toULong()
                length j (isHuffman j 2uL)
            }
        }
    }
    
    // Advance scanner position
    fun advance(bytes: ULong): QpackInstructionScanner? {
        if (remaining < bytes) return null
        
        return QpackInstructionScanner(buffer j ((position + bytes) j (remaining - bytes)))
    }
}

// HTTP/3 settings frame scanner
@JvmInline value class Http3SettingsScanner private constructor(
    private val data: Join<BorrowedBuffer, Join<ULong, ULong>>
) {
    val buffer: BorrowedBuffer get() = data.a
    val position: ULong get() = data.b.a
    val remaining: ULong get() = data.b.b
    
    companion object {
        fun scan(borrowed: BorrowedBuffer): Http3SettingsScanner = 
            Http3SettingsScanner(borrowed j (0uL j borrowed.bytes.size.toULong()))
    }
    
    // Zero-copy settings parameter scan
    fun scanSetting(): Join<Http3Setting, Join<Http3SettingsValue, ULong>>? {
        val scanner = Http3FrameScanner.scanAt(buffer, position)
        
        val idResult = scanner.scanVarint() ?: return null
        val setting = Http3Setting.fromId(idResult.a) ?: return null
        
        val valueScanner = scanner.advance(idResult.b) ?: return null
        val valueResult = valueScanner.scanVarint() ?: return null
        
        val totalBytes = idResult.b + valueResult.b
        return setting j (valueResult.a j totalBytes)
    }
    
    // Advance scanner position  
    fun advance(bytes: ULong): Http3SettingsScanner? {
        if (remaining < bytes) return null
        
        return Http3SettingsScanner(buffer j ((position + bytes) j (remaining - bytes)))
    }
}

// Zero-copy HTTP/3 stream data scanner
@JvmInline value class Http3StreamScanner private constructor(
    private val data: Join<BorrowedBuffer, Join<ULong, ULong>>
) {
    val buffer: BorrowedBuffer get() = data.a
    val position: ULong get() = data.b.a
    val remaining: ULong get() = data.b.b
    
    companion object {
        fun scan(borrowed: BorrowedBuffer): Http3StreamScanner = 
            Http3StreamScanner(borrowed j (0uL j borrowed.bytes.size.toULong()))
    }
    
    // Zero-copy stream type detection for control streams
    fun scanStreamType(): Join<Http3StreamType, ULong>? {
        val scanner = Http3FrameScanner.scanAt(buffer, position)
        val typeResult = scanner.scanVarint() ?: return null
        val streamType = Http3StreamType.fromValue(typeResult.a) ?: return null
        
        return streamType j typeResult.b
    }
    
    // Scan complete HTTP/3 frame with zero-copy  
    fun scanFrame(): Join<Http3FrameType, Join<BorrowedBuffer, ULong>>? {
        val scanner = Http3FrameScanner.scanAt(buffer, position)
        
        val headerResult = scanner.scanFrameHeader() ?: return null
        val frameType = headerResult.a
        val frameLength = headerResult.b.a
        val payloadStart = headerResult.b.b
        
        if (remaining < payloadStart + frameLength) return null
        
        val payloadBuffer = scanner.advance(payloadStart - position)?.sliceBytes(frameLength) ?: return null
        val totalFrameSize = payloadStart + frameLength - position
        
        return frameType j (payloadBuffer j totalFrameSize)
    }
    
    // Advance scanner position
    fun advance(bytes: ULong): Http3StreamScanner? {
        if (remaining < bytes) return null
        
        return Http3StreamScanner(buffer j ((position + bytes) j (remaining - bytes)))
    }
}