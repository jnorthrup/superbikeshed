package borg.trikeshed.net.http3

import borg.trikeshed.net.http.QuicCurlException // Assuming this is in net.http
import borg.trikeshed.net.quic.util.BufferReader

/**
 * Parses HTTP/3 frames from a BufferReader.
 */
object Http3FrameParser {

    /**
     * Parses a single HTTP/3 frame from the provided BufferReader.
     * The reader's offset will be advanced past the consumed frame.
     *
     * @param reader The BufferReader to read frame data from.
     * @return The parsed [Http3Frame], or null if not enough data to parse a complete frame header.
     * @throws QuicCurlException for parsing errors like unknown critical frames, invalid frame lengths,
     *                           or insufficient data for payload.
     */
    fun parseFrame(reader: BufferReader): Http3Frame? {
        if (!reader.hasRemaining()) {
            return null // No data to read
        }

        val frameTypeLong: Long
        val frameLengthLong: Long

        try {
            frameTypeLong = reader.readVarint()
            frameLengthLong = reader.readVarint()
        } catch (e: IllegalArgumentException) {
            // This typically means not enough bytes for varint headers themselves
            throw QuicCurlException("Failed to read HTTP/3 frame type or length: ${e.message}", cause = e)
        } catch (e: IndexOutOfBoundsException) {
            throw QuicCurlException("Buffer underflow while reading HTTP/3 frame type or length", cause = e)
        }


        val frameType = Http3FrameType.fromValue(frameTypeLong.toULong())
            ?: throw QuicCurlException("Unknown HTTP/3 frame type: 0x${frameTypeLong.toString(16)}")

        if (frameLengthLong < 0) {
            throw QuicCurlException("Invalid negative HTTP/3 frame length: $frameLengthLong for type $frameType")
        }
        // HTTP/3 frames have a max length of 2^62-1, but practically limited by stream capacity and memory.
        // For now, check against Int.MAX_VALUE as payloads are read into ByteArrays.
        if (frameLengthLong > Int.MAX_VALUE.toLong()) {
             throw QuicCurlException("HTTP/3 frame length $frameLengthLong exceeds Int.MAX_VALUE for type $frameType")
        }
        val frameLengthInt = frameLengthLong.toInt()

        if (reader.bytes.size - reader.offset < frameLengthInt) {
            throw QuicCurlException(
                "Insufficient data for HTTP/3 frame payload. Type: $frameType, Expected length: $frameLengthInt, " +
                "Bytes remaining after headers: ${reader.bytes.size - reader.offset}"
            )
        }

        // Create a new BufferReader for the frame payload to ensure frames don't over-read.
        // This is good practice, though readBytes(count) also respects its count.
        val payloadBytes = reader.readBytes(frameLengthInt)
        val payloadReader = BufferReader(payloadBytes)

        return when (frameType) {
            Http3FrameType.DATA -> {
                // The entire payload of a DATA frame is its data.
                DataFrame(payloadReader.readBytes(payloadReader.bytes.size - payloadReader.offset))
            }
            Http3FrameType.HEADERS -> {
                // The entire payload of a HEADERS frame is the encoded header block.
                HeadersFrame(payloadReader.readBytes(payloadReader.bytes.size - payloadReader.offset))
            }
            Http3FrameType.SETTINGS -> {
                val settingsMap = mutableMapOf<Long, Long>()
                while (payloadReader.hasRemaining()) {
                    try {
                        val id = payloadReader.readVarint()
                        val value = payloadReader.readVarint()
                        settingsMap[id] = value
                    } catch (e: Exception) { // Catches IAE or IOOBE from readVarint if settings format is wrong
                        throw QuicCurlException("Error parsing SETTINGS frame payload: ${e.message}", cause = e)
                    }
                }
                SettingsFrame(settingsMap)
            }
            Http3FrameType.GOAWAY -> {
                try {
                    GoAwayFrame(payloadReader.readVarint())
                } catch (e: Exception) {
                     throw QuicCurlException("Error parsing GOAWAY frame payload: ${e.message}", cause = e)
                }
            }
            // TODO: Implement parsing for other frame types (CANCEL_PUSH, PUSH_PROMISE, MAX_PUSH_ID)
            // For now, unhandled known types will throw an exception here due to when not being exhaustive.
            // Or, they would have been caught by the unknown frameType earlier if not in enum.
            else -> throw QuicCurlException("Unsupported HTTP/3 frame type for parsing: $frameType")
        }
    }

    /**
     * Parses all HTTP/3 frames from a given ByteArray, typically representing data received on a stream.
     *
     * @param streamData The ByteArray containing one or more HTTP/3 frames.
     * @return A list of parsed [Http3Frame] objects.
     * @throws QuicCurlException if any frame is malformed or an error occurs during parsing.
     */
    fun parseAllFrames(streamData: ByteArray): List<Http3Frame> {
        val frames = mutableListOf<Http3Frame>()
        val reader = BufferReader(streamData)
        while (reader.hasRemaining()) {
            // If there are padding bytes at the end of a stream (shouldn't happen with HTTP/3 frames typically),
            // hasRemaining might be true but we can't parse a full frame header.
            // parseFrame handles initial !reader.hasRemaining().
            // If readVarint for type/length fails due to insufficient bytes, it throws.
            try {
                 val frame = parseFrame(reader) // parseFrame will throw if it can't read type/length or payload
                 frame?.let { frames.add(it) }
                   ?: break // Null means clean end of readable frames (e.g. only padding left, though not typical for H3)
            } catch (e: QuicCurlException) {
                // Depending on policy, might stop or try to skip. For now, rethrow.
                // If a frame is malformed, it's often a connection error.
                throw QuicCurlException("Failed to parse frame in stream data: ${e.message}", cause = e.cause ?: e)
            }

        }
        return frames
    }
}
