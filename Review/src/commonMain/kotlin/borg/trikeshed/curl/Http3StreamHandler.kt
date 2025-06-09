package borg.trikeshed.curl

import borg.trikeshed.net.http.HttpHeaders
import borg.trikeshed.net.http.HttpResponse
import borg.trikeshed.net.http.ResponseBody
import borg.trikeshed.net.http.QuicCurlException
import borg.trikeshed.net.http3.qpack.QpackDecoder
import borg.trikeshed.net.http3.Http3FrameParser
import borg.trikeshed.net.http3.HeadersFrame
import borg.trikeshed.net.http3.DataFrame
import borg.trikeshed.net.http3.GoAwayFrame
import borg.trikeshed.net.quic.QuicConnection // Conceptual import
import borg.trikeshed.net.quic.util.BufferReader
import borg.trikeshed.net.quic.util.BufferWriter
import kotlinx.coroutines.CompletableDeferred

/**
 * Handles incoming HTTP/3 frames for a single QUIC stream, parses them,
 * and eventually completes a response future.
 *
 * @param streamId The ID of the QUIC stream this handler is responsible for.
 * @param qpackDecoder A QPACK decoder instance.
 * @param responseDeferred The [CompletableDeferred] to complete with the [HttpResponse] or an exception.
 * @param cleanupCallback A callback function to invoke when this handler is done (either success or error),
 *                        passing the streamId for cleanup in the calling component (e.g., QuicCurlImpl).
 * @param connectionForCleanup The QUIC connection associated with this stream, used for context during cleanup.
 */
internal class Http3StreamHandler(
    private val streamId: Long,
    private val qpackDecoder: QpackDecoder,
    private val responseDeferred: CompletableDeferred<Result<HttpResponse>>,
    private val cleanupCallback: (streamId: Long) -> Unit,
    private val connectionForCleanup: QuicConnection // Used for context in cleanup, not directly operated on here
) {
    private val receivedByteAccumulator = BufferWriter()
    private var responseHeaders: HttpHeaders? = null
    private val responseBodyBytes = BufferWriter()
    private var responseStatusCode: Int? = null
    private var _isResponseFullyProcessed: Boolean = false
    private var quicStreamFinReceived: Boolean = false // True when the QUIC stream carrying H3 frames has FINned.

    /**
     * Processes incoming data chunks from the QUIC stream.
     * Appends data to an internal buffer and attempts to parse HTTP/3 frames.
     * @param data The byte chunk received from the QUIC stream.
     * @param isFinOnQuicStream True if the FIN flag was set on the QUIC stream for this chunk or previously.
     */
    fun processIncomingData(data: ByteArray, isFinOnQuicStream: Boolean) {
        if (_isResponseFullyProcessed) return

        if (data.isNotEmpty()) {
            receivedByteAccumulator.writeBytes(data)
        }
        this.quicStreamFinReceived = this.quicStreamFinReceived || isFinOnQuicStream

        tryParseFrames()
    }

    private fun tryParseFrames() {
        if (_isResponseFullyProcessed) return

        // Create a reader for the current accumulated data. This is somewhat inefficient
        // as it re-reads from the beginning, but simpler than managing partial frame states here.
        // A more advanced implementation would use a read-only view into receivedByteAccumulator
        // or a more complex state machine for parsing partial frames.
        val currentBufferContent = receivedByteAccumulator.toByteArray()
        if (currentBufferContent.isEmpty() && !quicStreamFinReceived) {
            // Nothing to parse and no FIN yet to indicate end of message without data
            return
        }
        val frameReader = BufferReader(currentBufferContent)
        var bytesConsumedThisPass = 0

        while (frameReader.hasRemaining()) {
            val initialOffset = frameReader.offset
            try {
                val frame = Http3FrameParser.parseFrame(frameReader) ?: break // Incomplete frame data in buffer
                bytesConsumedThisPass += (frameReader.offset - initialOffset)

                when (frame) {
                    is HeadersFrame -> {
                        if (responseHeaders != null) {
                            completeWithError(QuicCurlException("Duplicate HEADERS frame received on stream $streamId"))
                            return
                        }
                        // TODO: QPACK decoding might raise exceptions.
                        val decoded = qpackDecoder.decode(frame.encodedHeaderData, streamId)
                        responseHeaders = decoded
                        responseStatusCode = decoded[":status"]?.firstOrNull()?.toIntOrNull()
                        if (responseStatusCode == null) {
                            completeWithError(QuicCurlException("Missing or invalid :status in HEADERS on stream $streamId"))
                            return
                        }
                        // If it's an informational response (1xx), don't mark as headers processed for completion check yet.
                        // Real 1xx handling is more complex (e.g. Expect: 100-continue).
                        // if (responseStatusCode!! in 100..199) { responseHeaders = null; responseStatusCode = null; /* continue reading */ }
                    }
                    is DataFrame -> {
                        if (responseHeaders == null && responseStatusCode == null) { // Allow DATA after informational 1xx
                            completeWithError(QuicCurlException("DATA frame received before HEADERS on stream $streamId"))
                            return
                        }
                        responseBodyBytes.writeBytes(frame.payload)
                    }
                    is GoAwayFrame -> { // GOAWAY on a request stream is a connection error
                        completeWithError(QuicCurlException("GOAWAY frame received on request stream $streamId (Peer Last Stream ID: ${frame.streamId})", TransportErrorCode.PROTOCOL_VIOLATION.value))
                        return
                    }
                    is SettingsFrame -> { // SETTINGS on a request stream is a connection error
                         completeWithError(QuicCurlException("SETTINGS frame received on request/response stream $streamId. SETTINGS are only expected on control streams.", TransportErrorCode.FRAME_UNEXPECTED.value))
                        return
                    }
                    // TODO: Handle other frame types like PUSH_PROMISE if client supports server push.
                    // Any other known frame type received on a client request/response stream that isn't DATA or HEADERS
                    // (or trailers, when implemented) is generally unexpected.
                    else -> {
                         completeWithError(QuicCurlException("Unexpected frame type ${frame.type} received on request/response stream $streamId.", TransportErrorCode.FRAME_UNEXPECTED.value))
                        return
                    }
                }
            } catch (e: QuicCurlException) { // Errors from Http3FrameParser.parseFrame
                completeWithError(e)
                return
            } catch (e: Exception) { // Catch parsing errors from BufferReader (e.g. underflow if frameLength was too large)
                completeWithError(QuicCurlException("Error parsing HTTP/3 frame on stream $streamId", cause = e))
                return
            }
        }

        // Update accumulator with remaining unparsed bytes
        if (bytesConsumedThisPass > 0) {
            val remainingData = frameReader.readBytes(frameReader.bytes.size - frameReader.offset)
            receivedByteAccumulator.clear()
            receivedByteAccumulator.writeBytes(remainingData)
        }

        // Check for response completion:
        // Response is complete if we've received headers and the QUIC stream has FINned,
        // AND all data buffered in receivedByteAccumulator has been parsed.
        if (responseHeaders != null && quicStreamFinReceived && receivedByteAccumulator.size == 0) {
            completeSuccessfully()
        }
    }

    private fun completeSuccessfully() {
        if (_isResponseFullyProcessed) return
        if (responseStatusCode != null && responseHeaders != null) {
            val finalBody = ResponseBody.Bytes(responseBodyBytes.toByteArray())
            responseDeferred.complete(Result.success(HttpResponse(responseStatusCode!!, responseHeaders!!, finalBody)))
            _isResponseFullyProcessed = true
            cleanupCallback(streamId)
        } else {
            // This state should ideally not be reached if logic is correct.
            // It means we thought the response was complete but lacked essential parts.
            completeWithError(QuicCurlException("Internal error: Attempted to complete successfully without status/headers on stream $streamId."))
        }
    }

    fun forceCompleteWithError(error: QuicCurlException) {
        completeWithError(error)
    }

    private fun completeWithError(error: QuicCurlException) {
        if (_isResponseFullyProcessed) return
        if (responseDeferred.isActive) { // Check if deferred is still active
            responseDeferred.complete(Result.failure(error))
        }
        _isResponseFullyProcessed = true
        cleanupCallback(streamId)
    }

    fun isResponseFullyProcessed(): Boolean = _isResponseFullyProcessed
}
