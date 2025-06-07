package borg.trikeshed.net.quic

import borg.trikeshed.net.quic.util.BufferReader // Ensure this path is correct

/**
 * Parses a byte array payload into a list of QUIC frames.
 *
 * @param payload The byte array containing the QUIC frame data.
 * @param packetTypeForContext The type of QUIC packet this payload belongs to. This might be used
 *                             in the future for context-sensitive frame parsing rules.
 * @return A list of parsed [QuicFrame] objects.
 */
fun parseFrames(payload: ByteArray, packetTypeForContext: QuicPacketType): List<QuicFrame> {
    val bufferReader = BufferReader(payload)
    val frames = mutableListOf<QuicFrame>()

    while (bufferReader.hasRemaining()) {
        val frameTypeUByte = bufferReader.readByte().toUByte() // Read type and convert to UByte for comparison

        val frame: QuicFrame = when {
            frameTypeUByte == QuicFrameType.PADDING.value -> parsePaddingFrame(bufferReader)
            frameTypeUByte == QuicFrameType.PING.value -> parsePingFrame(bufferReader)
            frameTypeUByte == QuicFrameType.ACK.value -> parseAckFrame(bufferReader, hasEcn = false)
            frameTypeUByte == QuicFrameType.ACK_ECN.value -> parseAckFrame(bufferReader, hasEcn = true)
            frameTypeUByte == QuicFrameType.CONNECTION_CLOSE_QUIC.value -> parseConnectionCloseFrame(bufferReader, isApplicationError = false)
            frameTypeUByte == QuicFrameType.CONNECTION_CLOSE_APP.value -> parseConnectionCloseFrame(bufferReader, isApplicationError = true)
            frameTypeUByte == QuicFrameType.HANDSHAKE_DONE.value -> parseHandshakeDoneFrame(bufferReader)
            frameTypeUByte == QuicFrameType.CRYPTO.value -> parseCryptoFrame(bufferReader)
            frameTypeUByte == QuicFrameType.NEW_TOKEN.value -> parseNewTokenFrame(bufferReader)
            frameTypeUByte in 0x08u..0x0Fu -> parseStreamFrame(bufferReader, frameTypeUByte.toByte()) // STREAM frames
            frameTypeUByte == QuicFrameType.MAX_DATA.value -> parseMaxDataFrame(bufferReader)
            frameTypeUByte == QuicFrameType.MAX_STREAM_DATA.value -> parseMaxStreamDataFrame(bufferReader)
            frameTypeUByte == QuicFrameType.MAX_STREAMS_BIDI.value -> parseMaxStreamsFrame(bufferReader, isBidirectional = true)
            frameTypeUByte == QuicFrameType.MAX_STREAMS_UNI.value -> parseMaxStreamsFrame(bufferReader, isBidirectional = false)
            frameTypeUByte == QuicFrameType.DATA_BLOCKED.value -> parseDataBlockedFrame(bufferReader)
            frameTypeUByte == QuicFrameType.STREAM_DATA_BLOCKED.value -> parseStreamDataBlockedFrame(bufferReader)
            frameTypeUByte == QuicFrameType.STREAMS_BLOCKED_BIDI.value -> parseStreamsBlockedFrame(bufferReader, isBidirectional = true)
            frameTypeUByte == QuicFrameType.STREAMS_BLOCKED_UNI.value -> parseStreamsBlockedFrame(reader = bufferReader, isBidirectional = false)
            frameTypeUByte == QuicFrameType.NEW_CONNECTION_ID.value -> parseNewConnectionIdFrame(bufferReader)
            frameTypeUByte == QuicFrameType.RETIRE_CONNECTION_ID.value -> parseRetireConnectionIdFrame(bufferReader)
            frameTypeUByte == QuicFrameType.PATH_CHALLENGE.value -> parsePathChallengeFrame(bufferReader)
            frameTypeUByte == QuicFrameType.PATH_RESPONSE.value -> parsePathResponseFrame(bufferReader)
            frameTypeUByte == QuicFrameType.RESET_STREAM.value -> parseResetStreamFrame(bufferReader)
            frameTypeUByte == QuicFrameType.STOP_SENDING.value -> parseStopSendingFrame(bufferReader)
            else -> {
                // Robust unknown handling: consume rest of payload and break.
                // Comment: This behavior can be problematic if a packet legitimately contains multiple frames
                // after an unknown frame that this parser doesn't support, as it will consume them all.
                // However, for critical unknown frames, QUIC often requires treating this as a connection error.
                // as frame boundaries are lost.
                val unknownFrame = parseUnknownFrame(bufferReader, frameTypeUByte)
                frames.add(unknownFrame)
                break // Stop parsing after an definitively unknown frame that isn't PADDING
            }
        }
        frames.add(frame)
    }
    return frames
}

/**
 * Parses a HANDSHAKE_DONE frame from the buffer.
 * HANDSHAKE_DONE frame format: Type (i)
 *
 * @param bufferReader The buffer reader, positioned right after the HANDSHAKE_DONE frame type byte.
 * @return A [HandshakeDoneFrame] object.
 */
private fun parseHandshakeDoneFrame(bufferReader: BufferReader): HandshakeDoneFrame {
    // HANDSHAKE_DONE frame (type 0x1e) has no payload.
    // The type byte itself constitutes the entire frame.
    return HandshakeDoneFrame
}

/**
 * Parses a CONNECTION_CLOSE frame from the buffer.
 * Format for 0x1c: Type | Error Code (i) | Frame Type (i) | Reason Phrase Length (i) | Reason Phrase (*)
 * Format for 0x1d: Type | Error Code (i) | Reason Phrase Length (i) | Reason Phrase (*)
 *
 * @param bufferReader The buffer reader, positioned right after the frame type byte.
 * @param isApplicationError True if the frame type indicates an application error (0x1d).
 * @return A [ConnectionCloseFrame] object.
 * @throws IllegalArgumentException if frame data is invalid.
 */
private fun parseConnectionCloseFrame(bufferReader: BufferReader, isApplicationError: Boolean): ConnectionCloseFrame {
    val errorCode = bufferReader.readVarint()
    var parsedOffendingFrameType: Long? = null

    if (!isApplicationError) {
        parsedOffendingFrameType = bufferReader.readVarint()
    }

    val reasonPhraseLength = bufferReader.readVarint()

    if (reasonPhraseLength < 0) {
        throw IllegalArgumentException("Invalid Reason Phrase Length: $reasonPhraseLength (negative)")
    }
    if (reasonPhraseLength > Int.MAX_VALUE.toLong()) { // Practical limit for ByteArray and String
        throw IllegalArgumentException("Invalid Reason Phrase Length: $reasonPhraseLength (exceeds Int.MAX_VALUE)")
    }
    val reasonPhraseLengthInt = reasonPhraseLength.toInt()

    if (bufferReader.bytes.size - bufferReader.offset < reasonPhraseLengthInt) {
        throw IllegalArgumentException(
            "Insufficient data for CONNECTION_CLOSE reason phrase. Expected $reasonPhraseLengthInt bytes, " +
            "but only ${bufferReader.bytes.size - bufferReader.offset} bytes remaining."
        )
    }

    val reasonPhraseBytes = bufferReader.readBytes(reasonPhraseLengthInt)
    val reasonPhrase = try {
        reasonPhraseBytes.decodeToString() // Default is UTF-8
    } catch (e: CharacterCodingException) {
        throw IllegalArgumentException("Invalid UTF-8 sequence in reason phrase", e)
    }

    return ConnectionCloseFrame(
        errorCode = errorCode,
        offendingFrameType = parsedOffendingFrameType,
        reasonPhrase = reasonPhrase,
        isApplicationError = isApplicationError
    )
}

/**
 * Parses an ACK frame from the buffer.
 * ACK frame format: Type (i) | Largest Acknowledged (i) | ACK Delay (i) | ACK Range Count (i) | First ACK Range (i) | ACK Range (*) | [ECN Counts (*)]
 *
 * @param bufferReader The buffer reader, positioned right after the ACK frame type byte.
 * @param hasEcn True if the frame type is ACK_ECN, indicating ECN counts are present.
 * @return An [AckFrame] object.
 * @throws IllegalArgumentException if frame data is invalid.
 */
private fun parseAckFrame(bufferReader: BufferReader, hasEcn: Boolean): AckFrame {
    val largestAcked = bufferReader.readVarint()
    val ackDelay = bufferReader.readVarint()
    val additionalAckRangeCount = bufferReader.readVarint()
    val firstAckRangePacketCount = bufferReader.readVarint()

    if (firstAckRangePacketCount < 0) throw IllegalArgumentException("First ACK Range Packet Count cannot be negative")
    if (additionalAckRangeCount < 0) throw IllegalArgumentException("Additional ACK Range Count cannot be negative")


    val parsedAdditionalAckRanges = mutableListOf<AckRange>()
    // Limit loop iterations to prevent excessive memory allocation from malicious count
    val safeAdditionalAckRangeCount = kotlin.math.min(additionalAckRangeCount, (bufferReader.bytes.size - bufferReader.offset) / 2L) // Each range needs at least 2 bytes for gap and count if varints are 1 byte each

    for (i in 0 until safeAdditionalAckRangeCount) {
        // Ensure there's enough data to read two varints (at least 1 byte each)
        if (bufferReader.bytes.size - bufferReader.offset < 2 && safeAdditionalAckRangeCount > 0) { // Check remaining bytes if count suggests more data
             throw IllegalArgumentException("Insufficient data for additional ACK ranges. Loop $i of $safeAdditionalAckRangeCount")
        }
        val gap = bufferReader.readVarint()
        val ackedPackets = bufferReader.readVarint()
        if (gap < 0 || ackedPackets < 0) throw IllegalArgumentException("ACK range gap or acked packets cannot be negative")
        parsedAdditionalAckRanges.add(AckRange(gap, ackedPackets))
    }
    // If additionalAckRangeCount was larger than safeAdditionalAckRangeCount, it means not all ranges were parsed.
    // This could indicate a malformed packet or an issue with remaining buffer size calculation.
    // For now, we proceed with what was safely parsed. A stricter parser might throw an error here if counts mismatch.

    var parsedEcnCounts: EcnCounts? = null
    if (hasEcn) {
        // Ensure there's enough data for 3 varints (at least 1 byte each)
        if (bufferReader.bytes.size - bufferReader.offset < 3) {
            throw IllegalArgumentException("Insufficient data for ECN counts.")
        }
        val ect0 = bufferReader.readVarint()
        val ect1 = bufferReader.readVarint()
        val ce = bufferReader.readVarint()
        if (ect0 < 0 || ect1 < 0 || ce < 0) throw IllegalArgumentException("ECN counts cannot be negative")
        parsedEcnCounts = EcnCounts(ect0, ect1, ce)
    }

    return AckFrame(
        largestAcked = largestAcked,
        ackDelay = ackDelay,
        firstAckRangePacketCount = firstAckRangePacketCount,
        additionalAckRanges = parsedAdditionalAckRanges,
        ecnCounts = parsedEcnCounts
    )
}

/**
 * Parses a PADDING frame.
 * PADDING frames effectively just consume bytes until a non-PADDING frame is found.
 * The loop in `parseFrames` handles advancing past PADDING bytes one by one.
 * This function is called when a PADDING type byte is identified.
 *
 * @param bufferReader The buffer reader, positioned right after the PADDING type byte.
 * @param typeByte The frame type byte already read (must be PADDING type).
 * @return A [PaddingFrame] object.
 */
private fun parsePaddingFrame(bufferReader: BufferReader /* typeByte: UByte */): PaddingFrame {
    // PADDING frames have no content beyond their type byte.
    // The typeByte (0x00) itself signifies a 1-byte PADDING frame.
    // Multiple PADDING frames are represented by multiple 0x00 bytes.
    // The loop in parseFrames will continue to call this if subsequent bytes are also 0x00.
    return PaddingFrame
}

/**
 * Parses a PING frame.
 *
 * @param bufferReader The buffer reader, positioned right after the PING type byte.
 * @return A [PingFrame] object.
 */
private fun parsePingFrame(bufferReader: BufferReader /* typeByte: UByte */): PingFrame {
    // PING frames (type 0x01) under RFC 9000 have no payload.
    return PingFrame
}

/**
 * Parses a CRYPTO frame from the buffer.
 * CRYPTO frame format: Type (i) | Offset (i) | Length (i) | Crypto Data (*)
 *
 * @param bufferReader The buffer reader, positioned right after the CRYPTO frame type byte.
 * @return A [CryptoFrame] object.
 * @throws IllegalArgumentException if frame data is invalid (e.g., length mismatch).
 */
private fun parseCryptoFrame(bufferReader: BufferReader): CryptoFrame {
    val offset = bufferReader.readVarint()
    val length = bufferReader.readVarint()

    if (length < 0) {
        throw IllegalArgumentException("Invalid CRYPTO frame data length: $length (negative)")
    }
    if (length > Int.MAX_VALUE.toLong()) {
        throw IllegalArgumentException("Invalid CRYPTO frame data length: $length (exceeds Int.MAX_VALUE)")
    }
    val dataLengthInt = length.toInt()

    // Check remaining bytes against the actual required for data, not offset + dataLengthInt vs size
    // because offset for readBytes is internal to bufferReader.
    if (bufferReader.bytes.size - bufferReader.offset < dataLengthInt) {
        throw IllegalArgumentException(
            "Insufficient data for CRYPTO frame payload. Expected $dataLengthInt bytes, " +
            "but only ${bufferReader.bytes.size - bufferReader.offset} bytes remaining."
        )
    }

    val data = bufferReader.readBytes(dataLengthInt)
    return CryptoFrame(offset, data)
}

/**
 * Parses a STREAM frame from the buffer.
 * STREAM frame format: Type (i) | Stream ID (i) | [Offset (i)] | [Length (i)] | Stream Data (*)
 *
 * @param bufferReader The buffer reader, positioned right after the STREAM frame type byte.
 * @param typeByte The frame type byte already read (0x08-0x0F).
 * @return A [StreamFrame] object.
 * @throws IllegalArgumentException if frame data is invalid.
 */
private fun parseStreamFrame(bufferReader: BufferReader, typeByte: Byte): StreamFrame {
    val typeByteInt = typeByte.toInt() and 0xFF // Ensure positive int value if Byte is signed

    val hasOffset = (typeByteInt and 0x04) != 0 // OFF bit
    val hasLength = (typeByteInt and 0x02) != 0 // LEN bit
    val isFin = (typeByteInt and 0x01) != 0     // FIN bit

    val streamId = bufferReader.readVarint()
    val offset = if (hasOffset) bufferReader.readVarint() else 0L

    val data: ByteArray
    if (hasLength) {
        val length = bufferReader.readVarint()
        if (length < 0) {
            throw IllegalArgumentException("Invalid STREAM frame data length: $length (negative)")
        }
        if (length > Int.MAX_VALUE.toLong()) { // Practical limit for ByteArray
            throw IllegalArgumentException("Invalid STREAM frame data length: $length (exceeds Int.MAX_VALUE)")
        }
        val dataLengthInt = length.toInt()
        if (bufferReader.bytes.size - bufferReader.offset < dataLengthInt) {
            throw IllegalArgumentException(
                "Insufficient data for STREAM frame payload. Expected $dataLengthInt bytes, " +
                "but only ${bufferReader.bytes.size - bufferReader.offset} bytes remaining."
            )
        }
        data = bufferReader.readBytes(dataLengthInt)
    } else {
        // If no length is specified, STREAM frame consumes the remainder of the packet payload.
        // This implies it must be the last frame.
        val remainingDataLength = bufferReader.bytes.size - bufferReader.offset
        data = bufferReader.readBytes(remainingDataLength)
    }

    return StreamFrame(
        streamId = streamId,
        offset = offset,
        data = data,
        isFin = isFin,
        sendLengthExplicitly = hasLength // Record if length was explicit for potential re-serialization logic
    )
}

/**
 * Parses an Unknown frame. This is called when a frame type is encountered that
 * is not recognized by the parser (and is not PADDING). It consumes the remainder
 * of the payload.
 *
 * @param bufferReader The buffer reader, positioned right after the unknown frame type byte.
 * @param typeByte The unknown frame type byte that was read.
 * @return An [UnknownFrame] object containing the type and the rest of the payload.
 */
private fun parseUnknownFrame(bufferReader: BufferReader, typeByte: UByte): UnknownFrame {
    // Comment: This behavior can be problematic if a packet legitimately contains multiple frames
    // after an unknown frame that this parser doesn't support, as it will consume them all.
    // However, for critical unknown frames, QUIC often requires treating this as a connection error.
    println("Warning: Parsing unknown frame type 0x${typeByte.toString(16)}. Consuming remaining data.")
    val remainingBytesCount = bufferReader.bytes.size - bufferReader.offset
    val rawData = bufferReader.readBytes(remainingBytesCount)
    return UnknownFrame(typeByte, rawData)
}

// Helper parsing functions for each new frame type

private fun parseNewTokenFrame(bufferReader: BufferReader): NewTokenFrame {
    val tokenLength = bufferReader.readVarint()
    if (tokenLength < 0 || tokenLength > bufferReader.bytes.size - bufferReader.offset) {
        throw IllegalArgumentException("NEW_TOKEN frame: Invalid token length $tokenLength, remaining bytes: ${bufferReader.bytes.size - bufferReader.offset}")
    }
    val token = bufferReader.readBytes(tokenLength.toInt())
    return NewTokenFrame(token)
}

private fun parseMaxDataFrame(bufferReader: BufferReader): MaxDataFrame {
    val maximumData = bufferReader.readVarint()
    return MaxDataFrame(maximumData)
}

private fun parseMaxStreamDataFrame(bufferReader: BufferReader): MaxStreamDataFrame {
    val streamId = bufferReader.readVarint()
    val maximumStreamData = bufferReader.readVarint()
    return MaxStreamDataFrame(streamId, maximumStreamData)
}

private fun parseMaxStreamsFrame(bufferReader: BufferReader, isBidirectional: Boolean): MaxStreamsFrame {
    val maximumStreams = bufferReader.readVarint()
    return MaxStreamsFrame(maximumStreams, isBidirectional)
}

private fun parseDataBlockedFrame(bufferReader: BufferReader): DataBlockedFrame {
    val dataLimit = bufferReader.readVarint()
    return DataBlockedFrame(dataLimit)
}

private fun parseStreamDataBlockedFrame(bufferReader: BufferReader): StreamDataBlockedFrame {
    val streamId = bufferReader.readVarint()
    val streamDataLimit = bufferReader.readVarint()
    return StreamDataBlockedFrame(streamId, streamDataLimit)
}

private fun parseStreamsBlockedFrame(reader: BufferReader, isBidirectional: Boolean): StreamsBlockedFrame {
    val streamLimit = reader.readVarint() // Field changed to Long in QuicFrames.kt
    return StreamsBlockedFrame(if (isBidirectional) StreamType.BIDIRECTIONAL else StreamType.UNIDIRECTIONAL, streamLimit)
}

private fun parseNewConnectionIdFrame(bufferReader: BufferReader): NewConnectionIdFrame {
    val sequenceNumber = bufferReader.readVarint()
    val retirePriorTo = bufferReader.readVarint()
    val length = bufferReader.readByte().toInt() and 0xFF // Length is a single byte
    if (length < 1 || length > 20) { // As per RFC 9000
        throw IllegalArgumentException("NEW_CONNECTION_ID frame: Invalid connection ID length $length (must be 1-20)")
    }
    if (length > bufferReader.bytes.size - bufferReader.offset) {
         throw IllegalArgumentException("NEW_CONNECTION_ID frame: Not enough bytes for CID of length $length, remaining: ${bufferReader.bytes.size - bufferReader.offset}")
    }
    val connectionId = bufferReader.readBytes(length)

    val expectedStatelessResetTokenLength = 16
    if (expectedStatelessResetTokenLength > bufferReader.bytes.size - bufferReader.offset) {
         throw IllegalArgumentException("NEW_CONNECTION_ID frame: Not enough bytes for 16-byte stateless reset token, remaining: ${bufferReader.bytes.size - bufferReader.offset}")
    }
    val statelessResetToken = bufferReader.readBytes(expectedStatelessResetTokenLength)
    return NewConnectionIdFrame(sequenceNumber, retirePriorTo, connectionId, statelessResetToken)
}

private fun parseRetireConnectionIdFrame(bufferReader: BufferReader): RetireConnectionIdFrame {
    val sequenceNumber = bufferReader.readVarint()
    return RetireConnectionIdFrame(sequenceNumber)
}

private fun parsePathChallengeFrame(bufferReader: BufferReader): PathChallengeFrame {
    val expectedDataLength = 8
    if (expectedDataLength > bufferReader.bytes.size - bufferReader.offset) {
        throw IllegalArgumentException("PATH_CHALLENGE frame: Not enough bytes for challenge data (expected $expectedDataLength), remaining: ${bufferReader.bytes.size - bufferReader.offset}")
    }
    val data = bufferReader.readBytes(expectedDataLength)
    return PathChallengeFrame(data)
}

private fun parsePathResponseFrame(bufferReader: BufferReader): PathResponseFrame {
    val expectedDataLength = 8
    if (expectedDataLength > bufferReader.bytes.size - bufferReader.offset) {
        throw IllegalArgumentException("PATH_RESPONSE frame: Not enough bytes for response data (expected $expectedDataLength), remaining: ${bufferReader.bytes.size - bufferReader.offset}")
    }
    val data = bufferReader.readBytes(expectedDataLength)
    return PathResponseFrame(data)
}

private fun parseResetStreamFrame(bufferReader: BufferReader): ResetStreamFrame {
    val streamId = bufferReader.readVarint()
    val applicationErrorCode = bufferReader.readVarint()
    val finalSize = bufferReader.readVarint()
    return ResetStreamFrame(streamId, applicationErrorCode, finalSize)
}

private fun parseStopSendingFrame(bufferReader: BufferReader): StopSendingFrame {
    val streamId = bufferReader.readVarint()
    val applicationErrorCode = bufferReader.readVarint()
    return StopSendingFrame(streamId, applicationErrorCode)
}
