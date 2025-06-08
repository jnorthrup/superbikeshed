package borg.trikeshed.curl

import borg.trikeshed.net.http.HttpRequest
import borg.trikeshed.net.http.HttpResponse
import borg.trikeshed.net.http.HttpMethod
import borg.trikeshed.net.http.HttpHeaders
import borg.trikeshed.net.http.RequestBody
import borg.trikeshed.net.http.HttpAuthentication // Added import
import borg.trikeshed.net.http.QuicCurlException
import borg.trikeshed.net.http3.qpack.QpackEncoder
import borg.trikeshed.net.http3.qpack.QpackDecoder
import borg.trikeshed.net.http3.Http3Frame // For HeadersFrame, DataFrame
import borg.trikeshed.net.http3.HeadersFrame
import borg.trikeshed.net.http3.DataFrame
import borg.trikeshed.net.quic.QuicStream // Conceptual, for stream.enqueueApplicationData
import borg.trikeshed.net.quic.QuicConnection // Conceptual import
import borg.trikeshed.net.quic.QuicStreamState // For checking stream state
import kotlinx.coroutines.CompletableDeferred // For asynchronous response handling

// Assuming QuicConnection and its StreamManager are conceptually available via QuicConnectionProvider
// For actual compilation, QuicConnection and its manager would need to be concrete types.

class QuicCurlImpl(
    private val connectionProvider: QuicConnectionProvider
    // In a real app, might also take CoroutineContext for execution, logger, etc.
) : QuicCurl {
    private val qpackEncoder = QpackEncoder()
    private val qpackDecoder = QpackDecoder()

    // Manage active requests and their handlers
    private val activeRequests: MutableMap<Long, CompletableDeferred<Result<HttpResponse>>> = mutableMapOf()
    private val activeHttp3StreamHandlers: MutableMap<Long, Http3StreamHandler> = mutableMapOf()
    private val streamToConnectionMap: MutableMap<Long, QuicConnection> = mutableMapOf()

    // HTTP/3 Connection Context Management
    private data class Http3ConnectionContext(
        val connection: QuicConnection,
        val settingsExchanged: CompletableDeferred<Unit>,
        var peerSettings: SettingsFrame? = null,
        var localSettingsSent: Boolean = false,
        var peerSettingsReceived: Boolean = false,
        var isGoAwayReceived: Boolean = false,
        var lastProcessedStreamIdByPeerInGoAway: Long? = null // From GOAWAY frame
    )
    private val http3ConnectionContexts: MutableMap<String, Http3ConnectionContext> = mutableMapOf()


    private suspend fun getConnectionAndEnsureHttp3Setup(host: String, port: Int, scheme: String): Result<Http3ConnectionContext> {
        val contextKey = "$host:$port"
        var context = http3ConnectionContexts[contextKey]

        if (context != null) {
            // Conceptual check if connection is active and not draining.
            // A real QuicConnection would have methods like isActive() and isDraining().
            val connectionIsUsable = context.connection.let { conn ->
                // Replace with actual checks: conn.isActive && !conn.isDrainingDueToRemoteGoAway
                (conn as? Any) != null // Placeholder for isActive
            } && !context.isGoAwayReceived

            if (!connectionIsUsable) {
                println("Existing H3 context for $contextKey is for a dead or draining connection. Removing.")
                http3ConnectionContexts.remove(contextKey)
                // Assuming releaseConnection also handles closing if the connection is truly dead.
                connectionProvider.releaseConnection(context.connection)
                context = null // Force creation of a new context and connection
            }
        }

        if (context == null) {
            println("No usable H3 context for $contextKey, establishing new QUIC connection and H3 setup.")
            val connectionResult = connectionProvider.getConnection(host, port, scheme)
            if (connectionResult.isFailure) {
                return Result.failure(
                    connectionResult.exceptionOrNull() as? QuicCurlException
                        ?: QuicCurlException("Failed to get QUIC connection for H3 setup", cause = connectionResult.exceptionOrNull())
                )
            }
            val newConnection = connectionResult.getOrThrow()

            context = Http3ConnectionContext(newConnection, CompletableDeferred())
            http3ConnectionContexts[contextKey] = context

            // Initiate HTTP/3 SETTINGS exchange on the new connection
            initiateHttp3SettingsExchange(context, newConnection)
        }

        return try {
            context.settingsExchanged.await() // Wait for SETTINGS exchange to complete
            Result.success(context)
        } catch (e: Exception) {
            http3ConnectionContexts.remove(contextKey) // Clean up context on failure
            connectionProvider.releaseConnection(context.connection)
            Result.failure(QuicCurlException("HTTP/3 SETTINGS exchange failed or timed out for $contextKey", cause = e))
        }
    }

    private suspend fun initiateHttp3SettingsExchange(context: Http3ConnectionContext, connection: QuicConnection) {
        // 1. Client sends SETTINGS on its control stream (ID 2)
        val clientControlStream = connection.streamManager.openUnidirectionalStream()
        if (clientControlStream == null || clientControlStream.streamId != 2L) {
            val errorMsg = "Failed to open client control stream (ID 2) for SETTINGS. Stream ID: ${clientControlStream?.streamId}"
            context.settingsExchanged.completeExceptionally(QuicCurlException(errorMsg))
            cleanupFailedHttp3Setup(context)
            return
        }
        // TODO: Define actual local settings based on client capabilities / transport params
        val localSettingsMap = mapOf(
            SettingsFrame.SETTINGS_QPACK_MAX_TABLE_CAPACITY to 4096L,
            SettingsFrame.SETTINGS_MAX_FIELD_SECTION_SIZE to 32768L,
            // SettingsFrame.SETTINGS_H3_DATAGRAM_DPLPMTUD to 1L, // If supporting H3 datagrams
        )
        val localSettingsFrame = SettingsFrame(localSettingsMap)

        println("QuicCurlImpl: Sending client SETTINGS on stream ${clientControlStream.streamId}")
        sendFrameDataOnStream(clientControlStream, localSettingsFrame.toByteArray(), isFin = false) // Control streams typically not FINned early
        context.localSettingsSent = true

        // 2. Setup to handle Server's SETTINGS on its control stream (ID 3)
        // This relies on the general stream acceptance and frame dispatch mechanism.
        // We need a way to identify stream 3 as the server's control stream when it arrives.
        // This can be done by checking streamId in the general onDataForApplicationListener for new streams
        // or by having StreamManager notify about new remotely initiated uni-streams.

        // For now, the completion of context.settingsExchanged will be triggered by handleIncomingServerSettings.
        // If local settings are sent and peer settings are received, the deferred is completed.
        checkAndCompleteSettingsExchange(context)
    }

    /** Called when a SETTINGS frame is received, expectedly on server control stream (ID 3) */
    fun handleIncomingServerSettings(connectionKey: String, serverSettingsFrame: SettingsFrame, onStreamId: Long) {
        if (onStreamId != 3L) {
            println("Warning: Received SETTINGS frame on unexpected stream $onStreamId. Ignoring.")
            // TODO: This might be a connection error (FRAME_UNEXPECTED).
            return
        }
        val context = http3ConnectionContexts[connectionKey]
        if (context == null) {
            println("Warning: Received server SETTINGS for unknown connection context $connectionKey. Ignoring.")
            return
        }

        println("QuicCurlImpl: Received server SETTINGS on stream $onStreamId: ${serverSettingsFrame.settings}")
        context.peerSettings = serverSettingsFrame
        context.peerSettingsReceived = true
        // TODO: Apply peer settings to QPACK context for this connection, etc.
        // e.g., qpackDecoder.setMaxTableCapacity(serverSettingsFrame.settings[SettingsFrame.SETTINGS_QPACK_MAX_TABLE_CAPACITY] ?: 0L)
        // e.g., qpackEncoder.setPeerMaxBlockedStreams(serverSettingsFrame.settings[SettingsFrame.SETTINGS_QPACK_BLOCKED_STREAMS] ?: 0L)

        checkAndCompleteSettingsExchange(context)
    }

    private fun checkAndCompleteSettingsExchange(context: Http3ConnectionContext) {
        if (context.localSettingsSent && context.peerSettingsReceived && !context.settingsExchanged.isCompleted) {
            println("QuicCurlImpl: HTTP/3 SETTINGS exchange complete for ${context.connection}.")
            context.settingsExchanged.complete(Unit)
        }
    }

    private fun cleanupFailedHttp3Setup(context: Http3ConnectionContext) {
        http3ConnectionContexts.values.remove(context) // Remove by value comparison
        connectionProvider.releaseConnection(context.connection)
    }


    override suspend fun execute(request: HttpRequest): Result<HttpResponse> {
        // A. URL Parsing & Initial Validation
        val parsedUrlResult = parseUrl(request.url)
        if (parsedUrlResult.isFailure) { /* ... error handling as before ... */
            return Result.failure(
                parsedUrlResult.exceptionOrNull() as? QuicCurlException
                    ?: QuicCurlException("URL parsing failed", cause = parsedUrlResult.exceptionOrNull())
            )
        }
        val parsedUrl = parsedUrlResult.getOrThrow()
        if (parsedUrl.scheme.lowercase() != "https") { /* ... error handling ... */
            return Result.failure(QuicCurlException("HTTP/3 requires HTTPS scheme, got ${parsedUrl.scheme}"))
        }

        // B. Obtain QUIC Connection & Ensure HTTP/3 Setup (SETTINGS exchanged)
        val http3ContextResult = getConnectionAndEnsureHttp3Setup(parsedUrl.host, parsedUrl.port, parsedUrl.scheme)
        if (http3ContextResult.isFailure) {
            return Result.failure(
                http3ContextResult.exceptionOrNull() as? QuicCurlException
                    ?: QuicCurlException("Failed to establish HTTP/3 connection context", cause = http3ContextResult.exceptionOrNull())
            )
        }
        val http3Context = http3ContextResult.getOrThrow()
        val connection = http3Context.connection

        var stream: QuicStream? = null
        try {
            // C. Open QUIC Stream for the request
            stream = connection.streamManager.openBidirectionalStream()
            if (stream == null) {
                // No need to release connection here, as it's managed by http3ConnectionContexts
                // If stream opening fails, it's a state within an *established* H3 context.
                return Result.failure(QuicCurlException("Failed to open QUIC stream (limit reached or connection error)"))
            }

            // D. Prepare HTTP/3 Headers
            val http3Headers = mutableMapOf<String, MutableList<String>>()
            fun addHeader(name: String, value: String) = http3Headers.getOrPut(name.lowercase()) { mutableListOf() }.add(value)

            addHeader(":method", request.method.name)
            addHeader(":scheme", parsedUrl.scheme)
            val authority = parsedUrl.host + if (parsedUrl.port != 443 && parsedUrl.port != 80) ":${parsedUrl.port}" else ""
            addHeader(":authority", authority)
            val pathAndQuery = parsedUrl.path + (parsedUrl.query?.let { "?$it" } ?: "")
            addHeader(":path", if (pathAndQuery.isEmpty()) "/" else pathAndQuery)

            // Add user-provided headers
            request.headers.forEach { (name, values) ->
                values.forEach { value -> addHeader(name.lowercase(), value) }
            }
            // Ensure "host" header is set if not present, derived from authority
            if (!http3Headers.containsKey("host")) {
                 addHeader("host", authority)
            }

            // START OF NEW AUTHENTICATION LOGIC
            request.authentication?.let { auth ->
                when (auth) {
                    is HttpAuthentication.BasicAuth -> {
                        val credentials = "${auth.username}:${auth.password}"
                        // In a real scenario, use a proper Base64 encoder.
                        // For this subtask, a simple placeholder or assuming one exists.
                        // val encodedCredentials = base64Encode(credentials) // Using expect fun
                        // Simple placeholder for now:
                        val encodedCredentials = credentials.encodeToByteArray().joinToString("") { byte -> (byte.toInt() and 0xFF).toString(16).padStart(2, '0') } // NOT REAL BASE64, JUST HEX for placeholder
                        addHeader("authorization", "Basic $encodedCredentials")
                    }
                    is HttpAuthentication.BearerToken -> {
                        addHeader("authorization", "Bearer ${auth.token}")
                    }
                    is HttpAuthentication.ApiKeyAuth -> {
                        // Note: This will overwrite if the user manually provided a header with the same name.
                        // This is often desired behavior for a dedicated auth mechanism.
                        http3Headers.remove(auth.headerName.lowercase()) // Remove if manually set, to ensure our value takes precedence
                        addHeader(auth.headerName, auth.keyValue)
                    }
                }
            }
            // END OF NEW AUTHENTICATION LOGIC

            // E. Encode Headers (QPACK Placeholder)
            val encodedHeaders = qpackEncoder.encode(http3Headers.mapValues { it.value.toList() }, stream.streamId)

            // F. Send HEADERS Frame
            val headersFrame = HeadersFrame(encodedHeaders)
            var isFinForHeaders = request.body is RequestBody.Empty &&
                                  (request.method == HttpMethod.GET || request.method == HttpMethod.HEAD || request.method == HttpMethod.OPTIONS || request.method == HttpMethod.DELETE)
                                  // For POST/PUT without body, FIN might also be on HEADERS or on an empty DATA frame.
                                  // Let's assume if body is Empty, HEADERS carries FIN.

            // Conceptual call to stream's send queue.
            // stream.enqueueApplicationData(headersFrame.toByteArray(), isFin = isFinForHeaders)
            // Using a placeholder for the actual QuicStream method which might take QuicFrame directly or ByteArray
            // For this subtask, we assume QuicStream has a way to send frame bytes.
            // Let's assume it takes ByteArray and isFin.
            (stream as Any) // Cast to Any to avoid compile error if QuicStream is not fully defined with this method yet.
            // This is highly conceptual due to QuicStream not being fully implemented with send methods.
            // The following line is a placeholder for actual interaction:
            sendFrameDataOnStream(stream, headersFrame.toByteArray(), isFinForHeaders)


            // G. Send DATA Frames (if body exists)
            var isFinForData = false
            when (val body = request.body) {
                is RequestBody.Bytes -> {
                    if (body.content.isNotEmpty()) {
                        isFinForData = true // Assuming body fits in one DATA frame for now.
                        val dataFrame = DataFrame(body.content)
                        // stream.enqueueApplicationData(dataFrame.toByteArray(), isFin = true)
                        sendFrameDataOnStream(stream, dataFrame.toByteArray(), isFinForData)
                    } else if (!isFinForHeaders) { // Empty body, but HEADERS didn't send FIN (e.g. POST with empty body)
                        isFinForData = true
                        // stream.enqueueApplicationData(DataFrame(ByteArray(0)).toByteArray(), isFin = true)
                        sendFrameDataOnStream(stream, DataFrame(ByteArray(0)).toByteArray(), isFinForData)
                    }
                }
                is RequestBody.Empty -> {
                    // If isFinForHeaders was false (e.g. POST with EmptyBody), we might need to send FIN here.
                    // However, current logic for isFinForHeaders covers most common cases.
                    // If it was a method that *could* have a body, but was Empty, and HEADERS didn't FIN,
                    // an empty DATA frame with FIN might be needed.
                    if (!isFinForHeaders) { // Ensure FIN is sent if HEADERS didn't.
                         isFinForData = true
                         sendFrameDataOnStream(stream, DataFrame(ByteArray(0)).toByteArray(), isFinForData)
                    }
                }
                // TODO: Handle RequestBody.Streaming
            }

            println("QuicCurlImpl: Request HEADERS and DATA (if any) enqueued for stream ${stream.streamId}.")
            // Request sent, now set up for response.
            val responseDeferred = CompletableDeferred<Result<HttpResponse>>()
            activeRequests[stream.streamId] = responseDeferred
            streamToConnectionMap[stream.streamId] = connection // Store for cleanup

            val http3StreamHandler = getOrCreateHttp3StreamHandler(stream.streamId, connection, responseDeferred)

            stream.onDataForApplicationListener = { receivedStream, data, isFinOnQuicStream ->
                // Pass data to the Http3StreamHandler associated with this streamId
                activeHttp3StreamHandlers[receivedStream.streamId]?.processIncomingData(data, isFinOnQuicStream)
            }

            stream.onStateChangedListener = { changedStream, newState ->
                if (newState == QuicStreamState.CLOSED ||
                    newState == QuicStreamState.RESET_RECEIVED ||
                    newState == QuicStreamState.RESET_SENT) {

                    activeHttp3StreamHandlers[changedStream.streamId]?.let { handler ->
                        if (!handler.isResponseFullyProcessed()) {
                            val error = QuicCurlException("QUIC stream ${changedStream.streamId} terminated (state: $newState) before HTTP/3 response completion.")
                            handler.forceCompleteWithError(error) // This will also trigger cleanupRequest
                        }
                    }
                    // If handler is null or response already processed, cleanup might have already run or will run.
                    // If handler.forceCompleteWithError doesn't run cleanup (e.g. if deferred already done), ensure cleanup.
                    if (activeRequests.containsKey(changedStream.streamId)) { // Check if cleanup still needed
                        cleanupRequest(changedStream.streamId)
                    }
                }
            }

            // Ensure stream is active in streamManager to receive data, if not already from enqueue ops.
            // This is conceptual; actual stream activation for receive might be implicit
            // or handled by streamManager when stream is opened/accepted.
            // If QuicStream.enqueueApplicationData calls onHasFramesToSendListener, that would
            // call streamManager.notifyStreamIsActive. If that's sufficient, this explicit call might not be needed.
            // Let's assume streamManager.notifyStreamIsActive is primarily for *sending*.
            // For receiving, the stream should be ready after creation.

            return try {
                responseDeferred.await()
            } catch (e: kotlinx.coroutines.CancellationException) {
                cleanupRequest(stream.streamId) // Ensure cleanup if await is cancelled
                Result.failure(QuicCurlException("Request execution cancelled", cause = e))
            } catch (e: Exception) {
                cleanupRequest(stream.streamId)
                Result.failure(QuicCurlException("Request execution failed: ${e.message}", cause = e))
            }

        } catch (e: QuicCurlException) {
            // If stream was opened, connection was obtained. If error before stream opening, connection might not be in map.
            stream?.let { cleanupRequest(it.streamId) }
                ?: http3ContextResult.getOrNull()?.let { connectionProvider.releaseConnection(it.connection) } // Release if context was fetched but stream failed
            return Result.failure(e)
        } catch (e: Exception) {
             // Generic error before or during stream setup
            stream?.let { cleanupRequest(it.streamId) }
                ?: http3ContextResult.getOrNull()?.let { connectionProvider.releaseConnection(it.connection) }
            return Result.failure(QuicCurlException("Generic error during request execution: ${e.message}", cause = e))
        }
    }

    private fun getOrCreateHttp3StreamHandler(
        streamId: Long,
        connection: QuicConnection,
        responseDeferred: CompletableDeferred<Result<HttpResponse>>
    ): Http3StreamHandler {
        return activeHttp3StreamHandlers.getOrPut(streamId) {
            Http3StreamHandler(
                streamId = streamId,
                qpackDecoder = this.qpackDecoder,
                responseDeferred = responseDeferred,
                cleanupCallback = { sid -> this.cleanupRequest(sid) },
                connectionForCleanup = connection
            )
        }
    }

    private fun cleanupRequest(streamId: Long) {
        println("QuicCurlImpl: Cleaning up request for stream $streamId")
        activeHttp3StreamHandlers.remove(streamId)
        activeRequests.remove(streamId)
        streamToConnectionMap.remove(streamId)?.let { conn ->
            // TODO: Decide if stream closure is needed here, e.g. conn.streamManager.closeStream(streamId)
            // For now, just releasing the connection. Stream state changes should handle its closure.
            connectionProvider.releaseConnection(conn)
        }
    }


    override fun close() {
        val contextsToClose = ArrayList(http3ConnectionContexts.values) // Avoid CME
        http3ConnectionContexts.clear()

        contextsToClose.forEach { context ->
            // Complete any pending requests for this context's connection with an error
            val streamsForThisConnection = streamToConnectionMap.filter { it.value == context.connection }.keys
            streamsForThisConnection.forEach { streamId ->
                activeRequests.remove(streamId)?.let { deferred ->
                    if (deferred.isActive) {
                        deferred.complete(Result.failure(QuicCurlException("QuicCurl client closed.")))
                    }
                }
                activeHttp3StreamHandlers.remove(streamId)
                streamToConnectionMap.remove(streamId) // Clean up stream-specific map entry
            }

            // Conceptual: context.connection.closeGracefully(appErrorCode = 0, reason = "Client closed")
            // For now, just release via provider, assuming provider handles actual QUIC close or pooling.
            connectionProvider.releaseConnection(context.connection)
        }
        // Ensure all maps are clear if any entry was missed (e.g. no streams but context existed)
        activeRequests.clear()
        activeHttp3StreamHandlers.clear()
        streamToConnectionMap.clear()

        // TODO: Implement connectionProvider.closeAll() - this needs to be suspend or async.
        // This is a placeholder for proper resource shutdown.
        // GlobalScope.launch { connectionProvider.closeAll() } // If closeAll is suspend
        println("QuicCurlImpl: close() called. All contexts processed for cleanup.")
    }

    /**
     * Handles a GOAWAY frame received from the server for a specific connection.
     * This is assumed to be called by the connection layer when it parses a GOAWAY from a control stream.
     */
    fun handleGoAwayFrame(host: String, port: Int, frame: GoAwayFrame) {
        val contextKey = "$host:$port"
        val context = http3ConnectionContexts[contextKey]
        context?.let {
            println("QuicCurlImpl: GOAWAY received for $contextKey. Last processed stream ID: ${frame.streamId}")
            it.isGoAwayReceived = true
            it.lastProcessedStreamIdByPeerInGoAway = frame.streamId

            // Conceptual: Inform the QuicConnection object itself that it's draining.
            // it.connection.startDraining(frame.streamId)
            // This would make QuicStreamManager.openBidirectionalStream() start failing for this connection.

            // TODO: Potentially cancel active requests on this connection with stream IDs > frame.streamId.
            // This requires iterating `activeRequests` and checking `streamToConnectionMap` and `streamId`.
            // For now, new requests will attempt to get a new connection if this one is marked.
            // Existing requests might complete if their stream ID <= frame.streamId.
        }
    }


    // Placeholder for the actual mechanism to send frame bytes on a stream.
    private fun sendFrameDataOnStream(stream: QuicStream, data: ByteArray, isFin: Boolean) {
        // This function assumes that `QuicStream.enqueueApplicationData` is the correct method
        // for sending raw frame bytes onto the stream's send buffer. This abstraction might need
        // refinement later if H3 frames require different handling by the QuicStream layer
        // than regular application "payload" data. For now, we treat H3 frame bytes as payload.
        if (stream is borg.trikeshed.net.quic.QuicStream) {
            try {
                stream.enqueueApplicationData(data, isFin)
            } catch (e: IllegalStateException) {
                // Stream might be in a state that doesn't allow sending (e.g., already FINned, reset)
                throw QuicCurlException("Failed to send frame data on stream ${stream.streamId}: ${e.message}", cause = e)
            }
        } else {
            // This case should ideally not be reached if 'stream' is always our QuicStream type.
            throw QuicCurlException("Cannot send frame data: unknown stream type.")
        }
    }
}
