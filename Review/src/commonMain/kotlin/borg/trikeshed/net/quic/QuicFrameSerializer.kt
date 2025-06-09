package borg.trikeshed.net.quic

import borg.trikeshed.net.quic.util.BufferWriter // Ensure this path is correct

/**
 * Serializes a list of QUIC frames into a single byte array.
 *
 * @param frames The list of [QuicFrame] objects to serialize.
 * @param bufferWriter An optional existing [BufferWriter] to append to. If not provided, a new one is created.
 * @return A ByteArray containing the serialized QUIC frames.
 */
fun serializeFrames(frames: List<QuicFrame>, bufferWriter: BufferWriter = BufferWriter()): ByteArray {
    for (frame in frames) {
        when (frame) {
            is PaddingFrame -> serializePaddingFrame(frame, bufferWriter)
            is PingFrame -> serializePingFrame(frame, bufferWriter)
            is UnknownFrame -> {
                // Serialize unknown frames by writing their type and then their raw data.
                // This is useful if an UnknownFrame was parsed and needs to be re-serialized (e.g. for proxying),
                // though typically one might not want to send frames the application doesn't understand unless specifically designed to.
                bufferWriter.writeByte(frame.type.toByte()) // Convert UByte to Byte for writing
                bufferWriter.writeBytes(frame.rawData)
            }
            is CryptoFrame -> serializeCryptoFrame(frame, bufferWriter)
            is StreamFrame -> serializeStreamFrame(frame, bufferWriter)
            is AckFrame -> serializeAckFrame(frame, bufferWriter)
            is ConnectionCloseFrame -> serializeConnectionCloseFrame(frame, bufferWriter)
            is HandshakeDoneFrame -> serializeHandshakeDoneFrame(frame, bufferWriter)
            is MaxDataFrame -> serializeMaxDataFrame(frame, bufferWriter)
            is MaxStreamDataFrame -> serializeMaxStreamDataFrame(frame, bufferWriter)
            is MaxStreamsFrame -> serializeMaxStreamsFrame(frame, bufferWriter)
            is DataBlockedFrame -> serializeDataBlockedFrame(frame, bufferWriter)
            is StreamDataBlockedFrame -> serializeStreamDataBlockedFrame(frame, bufferWriter)
            is StreamsBlockedFrame -> serializeStreamsBlockedFrame(frame, bufferWriter)
            is NewConnectionIdFrame -> serializeNewConnectionIdFrame(frame, bufferWriter)
            is RetireConnectionIdFrame -> serializeRetireConnectionIdFrame(frame, bufferWriter)
            is PathChallengeFrame -> serializePathChallengeFrame(frame, bufferWriter)
            is PathResponseFrame -> serializePathResponseFrame(frame, bufferWriter)
            is NewTokenFrame -> serializeNewTokenFrame(frame, bufferWriter)
            is ResetStreamFrame -> serializeResetStreamFrame(frame, bufferWriter)
            is StopSendingFrame -> serializeStopSendingFrame(frame, bufferWriter)
            // All known QuicFrame subtypes must have a serialization case.
            else -> throw IllegalArgumentException("Unsupported frame type for serialization: ${frame::class.simpleName}")
        }
    }
    return bufferWriter.toByteArray()
}

/**
 * Serializes a PADDING frame.
 * @param frame The [PaddingFrame] to serialize.
 * @param bufferWriter The [BufferWriter] to write to.
 */
private fun serializePaddingFrame(frame: PaddingFrame, bufferWriter: BufferWriter) {
    bufferWriter.writeByte(QuicFrameType.PADDING.value.toByte()) // Convert UByte to Byte
}

/**
 * Serializes a PING frame.
 * @param frame The [PingFrame] to serialize.
 * @param bufferWriter The [BufferWriter] to write to.
 */
private fun serializePingFrame(frame: PingFrame, bufferWriter: BufferWriter) {
    bufferWriter.writeByte(QuicFrameType.PING.value.toByte()) // Convert UByte to Byte
}

/**
 * Serializes a CRYPTO frame.
 * CRYPTO frame format: Type (i) | Offset (i) | Length (i) | Crypto Data (*)
 *
 * @param frame The [CryptoFrame] to serialize.
 * @param bufferWriter The [BufferWriter] to write to.
 */
private fun serializeCryptoFrame(frame: CryptoFrame, bufferWriter: BufferWriter) {
    bufferWriter.writeByte(QuicFrameType.CRYPTO.value.toByte()) // Convert UByte to Byte
    bufferWriter.writeVarint(frame.offset)
    bufferWriter.writeVarint(frame.data.size.toLong()) // Length of the crypto data
    bufferWriter.writeBytes(frame.data)
}

/**
 * Serializes an ACK frame.
 * ACK frame format: Type (i) | Largest Acknowledged (i) | ACK Delay (i) | ACK Range Count (i) | First ACK Range (i) | ACK Range (*) | [ECN Counts (*)]
 *
 * @param frame The [AckFrame] to serialize.
 * @param bufferWriter The [BufferWriter] to write to.
 */
private fun serializeAckFrame(frame: AckFrame, bufferWriter: BufferWriter) {
    val typeByte = if (frame.ecnCounts != null) {
        QuicFrameType.ACK_ECN.value
    } else {
        QuicFrameType.ACK.value
    }
    bufferWriter.writeByte(typeByte.toByte())

    bufferWriter.writeVarint(frame.largestAcked)
    bufferWriter.writeVarint(frame.ackDelay)
    bufferWriter.writeVarint(frame.additionalAckRanges.size.toLong()) // ACK Range Count
    bufferWriter.writeVarint(frame.firstAckRangePacketCount)

    for (ackRange in frame.additionalAckRanges) {
        bufferWriter.writeVarint(ackRange.gap)
        bufferWriter.writeVarint(ackRange.ackedPacketsInThisRange)
    }

    frame.ecnCounts?.let { ecn ->
        bufferWriter.writeVarint(ecn.ect0)
        bufferWriter.writeVarint(ecn.ect1)
        bufferWriter.writeVarint(ecn.ce)
    }
}

/**
 * Serializes a CONNECTION_CLOSE frame.
 * Format for 0x1c: Type | Error Code (i) | Frame Type (i) | Reason Phrase Length (i) | Reason Phrase (*)
 * Format for 0x1d: Type | Error Code (i) | Reason Phrase Length (i) | Reason Phrase (*)
 *
 * @param frame The [ConnectionCloseFrame] to serialize.
 * @param bufferWriter The [BufferWriter] to write to.
 */
private fun serializeConnectionCloseFrame(frame: ConnectionCloseFrame, bufferWriter: BufferWriter) {
    val typeByteUByte = if (frame.isApplicationError) {
        QuicFrameType.CONNECTION_CLOSE_APP.value
    } else {
        QuicFrameType.CONNECTION_CLOSE_QUIC.value
    }
    bufferWriter.writeByte(typeByteUByte.toByte())

    bufferWriter.writeVarint(frame.errorCode)

    if (!frame.isApplicationError) {
        // For a QUIC layer error (0x1c), offendingFrameType should ideally be present.
        // If it's null, writing 0 as per the note in the instructions.
        bufferWriter.writeVarint(frame.offendingFrameType ?: 0L)
    }

    val reasonPhraseBytes = frame.reasonPhrase.encodeToByteArray() // Default UTF-8
    bufferWriter.writeVarint(reasonPhraseBytes.size.toLong())
    bufferWriter.writeBytes(reasonPhraseBytes)
}

/**
 * Serializes a HANDSHAKE_DONE frame.
 * HANDSHAKE_DONE frame format: Type (i)
 *
 * @param frame The [HandshakeDoneFrame] to serialize.
 * @param bufferWriter The [BufferWriter] to write to.
 */
private fun serializeHandshakeDoneFrame(frame: HandshakeDoneFrame, bufferWriter: BufferWriter) {
    bufferWriter.writeByte(QuicFrameType.HANDSHAKE_DONE.value.toByte())
}

/**
 * Serializes a STREAM frame.
 * STREAM frame format: Type (i) | Stream ID (i) | [Offset (i)] | [Length (i)] | Stream Data (*)
 *
 * @param frame The [StreamFrame] to serialize.
 * @param bufferWriter The [BufferWriter] to write to.
 */
private fun serializeStreamFrame(frame: StreamFrame, bufferWriter: BufferWriter) {
    var typeByteValue = 0x08 // Base for STREAM frames

    if (frame.offset > 0L) {
        typeByteValue = typeByteValue or 0x04 // Set OFF bit
    }
    if (frame.sendLengthExplicitly) {
        typeByteValue = typeByteValue or 0x02 // Set LEN bit
    }
    if (frame.isFin) {
        typeByteValue = typeByteValue or 0x01 // Set FIN bit
    }

    bufferWriter.writeByte(typeByteValue.toByte())
    bufferWriter.writeVarint(frame.streamId)

    if ((typeByteValue and 0x04) != 0) { // If OFF bit was set
        bufferWriter.writeVarint(frame.offset)
    }

    if ((typeByteValue and 0x02) != 0) { // If LEN bit was set
        bufferWriter.writeVarint(frame.data.size.toLong())
    }

    bufferWriter.writeBytes(frame.data)
}

// Serializers for the newly added frame types

private fun serializeMaxDataFrame(frame: MaxDataFrame, writer: BufferWriter) {
    writer.writeByte(QuicFrameType.MAX_DATA.value.toByte())
    writer.writeVarint(frame.maximumData)
}

private fun serializeMaxStreamDataFrame(frame: MaxStreamDataFrame, writer: BufferWriter) {
    writer.writeByte(QuicFrameType.MAX_STREAM_DATA.value.toByte())
    writer.writeVarint(frame.streamId)
    writer.writeVarint(frame.maximumStreamData)
}

private fun serializeMaxStreamsFrame(frame: MaxStreamsFrame, writer: BufferWriter) {
    val typeByte = if (frame.isBidirectional) QuicFrameType.MAX_STREAMS_BIDI.value else QuicFrameType.MAX_STREAMS_UNI.value
    writer.writeByte(typeByte.toByte())
    writer.writeVarint(frame.maximumStreams)
}

private fun serializeDataBlockedFrame(frame: DataBlockedFrame, writer: BufferWriter) {
    writer.writeByte(QuicFrameType.DATA_BLOCKED.value.toByte())
    writer.writeVarint(frame.dataLimit)
}

private fun serializeStreamDataBlockedFrame(frame: StreamDataBlockedFrame, writer: BufferWriter) {
    writer.writeByte(QuicFrameType.STREAM_DATA_BLOCKED.value.toByte())
    writer.writeVarint(frame.streamId)
    writer.writeVarint(frame.streamDataLimit)
}

private fun serializeStreamsBlockedFrame(frame: StreamsBlockedFrame, writer: BufferWriter) {
    val typeByte = if (frame.streamType == StreamType.BIDIRECTIONAL) QuicFrameType.STREAMS_BLOCKED_BIDI.value else QuicFrameType.STREAMS_BLOCKED_UNI.value
    writer.writeByte(typeByte.toByte())
    writer.writeVarint(frame.streamLimit)
}

private fun serializeNewConnectionIdFrame(frame: NewConnectionIdFrame, writer: BufferWriter) {
    writer.writeByte(QuicFrameType.NEW_CONNECTION_ID.value.toByte())
    writer.writeVarint(frame.sequenceNumber)
    writer.writeVarint(frame.retirePriorTo)
    writer.writeByte(frame.connectionId.size.toByte()) // Length of Connection ID (must be 1-20)
    writer.writeBytes(frame.connectionId)
    writer.writeBytes(frame.statelessResetToken) // 16 bytes
}

private fun serializeRetireConnectionIdFrame(frame: RetireConnectionIdFrame, writer: BufferWriter) {
    writer.writeByte(QuicFrameType.RETIRE_CONNECTION_ID.value.toByte())
    writer.writeVarint(frame.sequenceNumber)
}

private fun serializePathChallengeFrame(frame: PathChallengeFrame, writer: BufferWriter) {
    writer.writeByte(QuicFrameType.PATH_CHALLENGE.value.toByte())
    writer.writeBytes(frame.data) // 8 bytes
}

private fun serializePathResponseFrame(frame: PathResponseFrame, writer: BufferWriter) {
    writer.writeByte(QuicFrameType.PATH_RESPONSE.value.toByte())
    writer.writeBytes(frame.data) // 8 bytes
}

private fun serializeNewTokenFrame(frame: NewTokenFrame, writer: BufferWriter) {
    writer.writeByte(QuicFrameType.NEW_TOKEN.value.toByte())
    writer.writeVarint(frame.token.size.toLong())
    writer.writeBytes(frame.token)
}

private fun serializeResetStreamFrame(frame: ResetStreamFrame, writer: BufferWriter) {
    writer.writeByte(QuicFrameType.RESET_STREAM.value.toByte())
    writer.writeVarint(frame.streamId)
    writer.writeVarint(frame.applicationProtocolErrorCode)
    writer.writeVarint(frame.finalSize)
}

private fun serializeStopSendingFrame(frame: StopSendingFrame, writer: BufferWriter) {
    writer.writeByte(QuicFrameType.STOP_SENDING.value.toByte())
    writer.writeVarint(frame.streamId)
    writer.writeVarint(frame.applicationProtocolErrorCode)
}
