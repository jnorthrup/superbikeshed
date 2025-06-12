package borg.trikeshed.net.http.server

import borg.trikeshed.lib.Series
import borg.trikeshed.lib.toSeries
import borg.trikeshed.lib.asString
import borg.trikeshed.lib.emptySeries
import borg.trikeshed.lib.j
import borg.trikeshed.lib.TensorConstruct
import borg.trikeshed.lib.CoreTensorCursor
import borg.trikeshed.lib.CoreTensorCursorWithMeta
import borg.trikeshed.net.http.parser.HttpParsingException
import borg.trikeshed.net.http.parser.HttpRequestParser
import borg.trikeshed.net.http.serializer.HttpResponseSerializer
import borg.trikeshed.net.http.types.*
import borg.trikeshed.net.http.indexing.*
import borg.trikeshed.nio.ClientSocketChannel
import borg.trikeshed.nio.NioException
import borg.trikeshed.nio.services.NioService
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * Handles incoming HTTP/1.1 client connections with CCEK context-aware capabilities.
 *
 * @param nioService The NIO service used to create server and client socket channels.
 * @param CCEKContext The CoroutineContext this handler and its spawned coroutines will run in.
 *                    This context provides HTTP clients with contextual capabilities and scoped access patterns.
 */
class HttpConnectionHandler(
    private val nioService: NioService,
    private val CCEKContext: CoroutineContext
) {
    private val serializer = HttpResponseSerializer()
    private val serverJob = SupervisorJob()
    private val serverScope = CoroutineScope(CCEKContext + serverJob + CoroutineName("HttpConnectionHandlerScope"))

    private var isRunning = false

    suspend fun start(host: String, port: Int) {
        if (isRunning) {
            println("HttpConnectionHandler is already running.")
            return
        }
        isRunning = true
        println("HttpConnectionHandler starting on $host:$port...")

        val serverSocket = nioService.createServerSocketChannel()
        try {
            serverSocket.bind(host, port)
            println("HttpConnectionHandler bound to ${serverSocket.localAddress()}")

            while (isRunning && serverScope.isActive) {
                try {
                    val clientSocket = serverSocket.accept()
                    if (clientSocket != null && serverScope.isActive) {
                        println("Accepted connection from: ${clientSocket.remoteAddress()}")
                        serverScope.launch(CoroutineName("HttpClient-${clientSocket.remoteAddress()}")) {
                            handleClientConnection(clientSocket)
                        }
                    } else if (!isRunning || !serverScope.isActive) {
                        break
                    }
                } catch (e: NioException) {
                    if (isRunning && serverScope.isActive) {
                        println("Error accepting connection: ${e.message}")
                        delay(100)
                    } else {
                        break
                    }
                } catch (e: CancellationException) {
                    println("Accept loop cancelled.")
                    break
                } catch (e: Exception) {
                    println("Unexpected error in accept loop: ${e.message}")
                    isRunning = false
                    break
                }
            }
        } catch (e: NioException) {
            println("Failed to start HttpConnectionHandler: ${e.message}")
            isRunning = false
        } catch (e: Exception) {
            println("Critical error starting HttpConnectionHandler: ${e.message}")
            isRunning = false
        } finally {
            println("HttpConnectionHandler shutting down...")
            serverSocket.close()
            isRunning = false
            println("HttpConnectionHandler stopped.")
        }
    }

    fun stop() {
        println("HttpConnectionHandler.stop() called.")
        isRunning = false
        serverJob.cancel()
    }

    private suspend fun handleClientConnection(clientSocket: ClientSocketChannel) {
        val parser = HttpRequestParser()
        val readByteArray = ByteArray(4096)

        try {
            while (isRunning && clientSocket.isOpen() && clientSocket.isConnected() && serverScope.isActive) {
                val bytesRead: Int
                run {
                    val tempTensor = TensorConstruct(intArrayOf(readByteArray.size)) { idx -> readByteArray[idx[0]] }
                    bytesRead = clientSocket.read(tempTensor, 0, readByteArray.size)
                    if (bytesRead > 0) {
                        for(i in 0 until bytesRead) readByteArray[i] = tempTensor[intArrayOf(i)]
                    }
                }

                if (bytesRead == -1) {
                    println("Client ${clientSocket.remoteAddress()} closed connection (EOF).")
                    break
                }
                if (bytesRead == 0) {
                    yield()
                    continue
                }
                if (bytesRead > 0) {
                    val newDataSeries = readByteArray.copyOfRange(0, bytesRead).toSeries()
                    val parseResult = parser.parse(newDataSeries)

                    when {
                        parseResult.isSuccess -> {
                            val request = parseResult.getOrNull()
                            if (request != null) {
                                println("Received request from ${clientSocket.remoteAddress()}: ${request.method.name} ${request.path.value}")

                                // Use indexed header lookup (relaxfactory pattern)
                                val headerIndex = request.headers.buildIndex(CommonHeaders.BASIC_REQUEST)
                                val contentType = request.headers.lookup(HttpHeaderName("Content-Type"), headerIndex, CommonHeaders.BASIC_REQUEST)
                                val cookieIndex = request.headers.buildCookieIndex(CommonCookies.SESSION_COOKIES)
                                val sessionCookie = request.headers.lookupCookie(CookieName("sessionid"), cookieIndex, CommonCookies.SESSION_COOKIES)

                                val responseBodyStr = buildString {
                                    append("Hello from TrikeShed HTTP/1.1 Server! You requested: ${request.path.value}\n")
                                    contentType?.let { append("Content-Type: ${it.value}\n") }
                                    sessionCookie?.let { append("Session: ${it.value.value}\n") }
                                }
                                val responseBodySeries = responseBodyStr.encodeToByteArray().toSeries()

                                val responseHeadersList = listOf(
                                    "${HttpHeaderName.CONTENT_TYPE}: text/plain; charset=utf-8",
                                    "${HttpHeaderName.CONTENT_LENGTH}: ${responseBodySeries.size}",
                                    "${HttpHeaderName.CONNECTION}: close"
                                )
                                val headersSeries = responseHeadersList.toSeries()

                                val headersCursor = object : CoreTensorCursor<String> {
                                    override val meta = borg.trikeshed.lib.DslHandle.NONE
                                    override val columns: Int get() = 1
                                    override val rows: Int get() = headersSeries.size
                                    override fun get(row: Int, col: Int): String = if (col == 0) headersSeries[row] else throw IndexOutOfBoundsException()
                                    override fun getColumn(col: Int): Series<String> = if (col == 0) headersSeries else emptySeries()
                                    override fun getRow(row: Int): Series<String> = borg.trikeshed.lib.j(1){ headersSeries[row] }
                                }
                                val httpHeaders = CoreTensorCursorWithMeta(headersCursor, HttpHeadersMeta())

                                val response = HttpResponse(
                                    version = HttpVersion.HTTP_1_1,
                                    statusCode = HttpStatusCode(200),
                                    reasonPhrase = HttpReasonPhrase("OK"),
                                    headers = httpHeaders,
                                    body = HttpBody.Bytes(responseBodySeries)
                                )

                                val serializedResponse = serializer.serialize(response)
                                val responseBytes = ByteArray(serializedResponse.size) { i -> serializedResponse[i] }

                                run {
                                    val tempWriteTensor = TensorConstruct(intArrayOf(responseBytes.size)) {idx -> responseBytes[idx[0]]}
                                    clientSocket.write(tempWriteTensor, 0, responseBytes.size)
                                }

                                println("Response sent to ${clientSocket.remoteAddress()}. Closing connection.")
                                break
                            }
                        }
                        parseResult.isFailure -> {
                            val error = parseResult.exceptionOrNull() as? HttpParsingException
                            println("HTTP Parsing Error from ${clientSocket.remoteAddress()}: ${error?.message ?: "Unknown parsing error"}")

                            val errorBodySeries = "Bad Request".toSeries()
                            val errorHeadersList = listOf(
                                 "${HttpHeaderName.CONTENT_TYPE}: text/plain; charset=utf-8",
                                 "${HttpHeaderName.CONTENT_LENGTH}: ${errorBodySeries.size * 2}",
                                 "${HttpHeaderName.CONNECTION}: close"
                            )
                            val errorHeadersSeries = errorHeadersList.toSeries()
                            val errorHeadersCursor = object : CoreTensorCursor<String> {
                                override val meta = borg.trikeshed.lib.DslHandle.NONE
                                override val columns: Int get() = 1
                                override val rows: Int get() = errorHeadersSeries.size
                                override fun get(row: Int, col: Int): String = if (col == 0) errorHeadersSeries[row] else throw IndexOutOfBoundsException()
                                override fun getColumn(col: Int): Series<String> = if (col == 0) errorHeadersSeries else emptySeries()
                                override fun getRow(row: Int): Series<String> = borg.trikeshed.lib.j(1){ errorHeadersSeries[row] }
                            }
                            val errorHttpHeaders = CoreTensorCursorWithMeta(errorHeadersCursor, HttpHeadersMeta())

                            val errResponse = HttpResponse(
                                version = HttpVersion.HTTP_1_1,
                                statusCode = HttpStatusCode(400),
                                reasonPhrase = HttpReasonPhrase("Bad Request"),
                                headers = errorHttpHeaders,
                                body = HttpBody.Text(errorBodySeries)
                            )
                             try {
                                val serializedErrResponse = serializer.serialize(errResponse)
                                val errBytes = ByteArray(serializedErrResponse.size) { i -> serializedErrResponse[i] }
                                run {
                                     val tempErrWriteTensor = TensorConstruct(intArrayOf(errBytes.size)) {idx -> errBytes[idx[0]]}
                                     clientSocket.write(tempErrWriteTensor, 0, errBytes.size)
                                }
                            } catch (e: NioException) {
                                println("NIO Error sending 400 response to ${clientSocket.remoteAddress()}: ${e.message}")
                            }
                            break
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            println("Connection handler for ${clientSocket.remoteAddress()} cancelled.")
        } catch (e: Exception) {
            println("Unexpected error handling client ${clientSocket.remoteAddress()}: ${e.message}")
            e.printStackTrace()
        } finally {
            println("Closing client connection: ${clientSocket.remoteAddress()}")
            clientSocket.close()
        }
    }
}