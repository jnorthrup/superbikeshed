package borg.trikeshed.net.quic

import kotlin.test.*

/**
 * Tests serialization and deserialization of various QUIC frame types.
 */
class QuicFrameSerDesTest {

    private fun <T : QuicFrame> assertFrameSerDes(frame: T, typeForParse: QuicPacketType = QuicPacketType.HANDSHAKE) {
        val serializedBytes = serializeFrame(frame)
        assertNotNull(serializedBytes)
        assertTrue(serializedBytes.isNotEmpty())

        val parsedFrames = parseFrames(serializedBytes, typeForParse)
        assertNotNull(parsedFrames)
        assertEquals(1, parsedFrames.size, "Expected a single frame to be parsed back.")
        assertEquals(frame, parsedFrames[0], "Deserialized frame should match original for type ${frame::class.simpleName}")
    }

    @Test fun testMaxDataFrame() = assertFrameSerDes(MaxDataFrame(1234567890L))
    @Test fun testMaxStreamDataFrame() = assertFrameSerDes(MaxStreamDataFrame(123L, 9876543210L))
    @Test fun testMaxStreamsBidiFrame() = assertFrameSerDes(MaxStreamsFrame(100L, true))
    @Test fun testMaxStreamsUniFrame() = assertFrameSerDes(MaxStreamsFrame(50L, false))
    @Test fun testDataBlockedFrame() = assertFrameSerDes(DataBlockedFrame(12345L))
    @Test fun testStreamDataBlockedFrame() = assertFrameSerDes(StreamDataBlockedFrame(456L, 67890L))
    @Test fun testStreamsBlockedBidiFrame() = assertFrameSerDes(StreamsBlockedFrame(StreamType.BIDIRECTIONAL, 20L))
    @Test fun testStreamsBlockedUniFrame() = assertFrameSerDes(StreamsBlockedFrame(StreamType.UNIDIRECTIONAL, 10L))

    @Test
    fun testNewConnectionIdFrame() {
        val frame = NewConnectionIdFrame(
            sequenceNumber = 5L,
            retirePriorTo = 2L,
            connectionId = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08),
            statelessResetToken = ByteArray(16) { it.toByte() }
        )
        assertFrameSerDes(frame)
    }

    @Test fun testRetireConnectionIdFrame() = assertFrameSerDes(RetireConnectionIdFrame(3L))

    @Test
    fun testPathChallengeFrame() {
        val frame = PathChallengeFrame(ByteArray(8) { (it + 0xA0).toByte() })
        assertFrameSerDes(frame)
    }

    @Test
    fun testPathResponseFrame() {
        val frame = PathResponseFrame(ByteArray(8) { (it + 0xB0).toByte() })
        assertFrameSerDes(frame)
    }

    @Test
    fun testNewTokenFrame() {
        val frame = NewTokenFrame(token = "this is a new token".encodeToByteArray())
        assertFrameSerDes(frame)
    }

    @Test fun testResetStreamFrame() = assertFrameSerDes(ResetStreamFrame(streamId = 4L, applicationProtocolErrorCode = 0x0101L, finalSize = 1024L))
    @Test fun testStopSendingFrame() = assertFrameSerDes(StopSendingFrame(streamId = 6L, applicationProtocolErrorCode = 0x0102L))

    // Test existing frames as well for completeness with the helper
    @Test fun testPaddingFrame() = assertFrameSerDes(PaddingFrame) // Padding might be tricky if parser consumes all padding
    @Test fun testPingFrame() = assertFrameSerDes(PingFrame)

    @Test
    fun testAckFrameSimple() {
        val frame = AckFrame(largestAcked = 100L, ackDelay = 20L, firstAckRangePacketCount = 10, additionalAckRanges = emptyList(), ecnCounts = null)
        assertFrameSerDes(frame)
    }

    @Test
    fun testAckFrameWithRangesAndEcn() {
        val frame = AckFrame(
            largestAcked = 200L,
            ackDelay = 30L,
            firstAckRangePacketCount = 5,
            additionalAckRanges = listOf(AckRange(gap = 2, ackedPacketsInThisRange = 3)), // Acking 192-194 (200-5-1 -2 -3 +1 to 200-5-1 -2)
            ecnCounts = EcnCounts(1, 2, 3)
        )
        assertFrameSerDes(frame)
    }

    @Test
    fun testCryptoFrame() {
        val frame = CryptoFrame(offset = 100L, data = "crypto data".encodeToByteArray())
        assertFrameSerDes(frame)
    }

    @Test
    fun testStreamFrameWithLengthAndOffset() {
        val frame = StreamFrame(streamId = 1L, offset = 200L, data = "stream data".encodeToByteArray(), isFin = true, sendLengthExplicitly = true)
        assertFrameSerDes(frame)
    }

    @Test
    fun testStreamFrameFinOnlyNoLengthNoOffset() {
        // Type: 0x09 (FIN)
        val frame = StreamFrame(streamId = 1L, offset = 0L, data = byteArrayOf(), isFin = true, sendLengthExplicitly = false)
        assertFrameSerDes(frame)
    }

    @Test
    fun testStreamFrameDataOnlyNoLengthNoOffset() {
        // Type: 0x08 (Data, no FIN, no Length, no Offset)
        // This frame implies data extends to end of packet. Parser needs to handle this.
        // Our simplified assertFrameSerDes might struggle if parser/serializer handle "read/write to end" differently.
        // The parser was noted to be DANGEROUS for implicit length. This test will likely highlight that.
        // For this test to pass, the serializer should produce a frame that the parser can consume fully.
        // If serialize writes 0 for length if sendLengthExplicitly=false, and parser reads 0 if no LEN, it might pass.
        val frame = StreamFrame(streamId = 1L, offset = 0L, data = "implicit length".encodeToByteArray(), isFin = false, sendLengthExplicitly = false)

        // Serializer will write: Type (0x08), StreamID (varint), Data ("implicit length") because offset is 0 and sendLengthExplicitly is false
        // Parser for 0x08 will read StreamID, then Offset (0 as OFF=0), then if LEN=0, it reads remaining.
        // This should work if only one such frame is serialized.
        assertFrameSerDes(frame)
    }


    @Test
    fun testConnectionCloseQuicFrame() {
        val frame = ConnectionCloseFrame(errorCode = 0x1AL, offendingFrameType = QuicFrameType.STREAM_DATA_BLOCKED.value.toLong(), reasonPhrase = "QUIC error", isApplicationError = false)
        assertFrameSerDes(frame)
    }

    @Test
    fun testConnectionCloseAppFrame() {
        val frame = ConnectionCloseFrame(errorCode = 0xBEEF, offendingFrameType = null, reasonPhrase = "Application error", isApplicationError = true)
        assertFrameSerDes(frame)
    }

    @Test fun testHandshakeDoneFrame() = assertFrameSerDes(HandshakeDoneFrame)

    // Note on PADDING: parseFrames currently consumes individual PADDING bytes.
    // serializeFrame for PaddingFrame writes a single 0x00 byte.
    // So, assertFrameSerDes(PaddingFrame) should work for a single padding byte.
    // Testing multiple padding frames would require serializeFrames(listOf(PaddingFrame, PaddingFrame))
    // and checking if parseFrames returns two PaddingFrame objects.
    // The current parser structure might group contiguous PADDING bytes or handle them one by one.
    // The `parsePaddingFrame` is simple and returns one PaddingFrame per 0x00 byte.
    // The `serializePaddingFrame` writes one 0x00 byte. So, it's consistent for single byte.
}

```
