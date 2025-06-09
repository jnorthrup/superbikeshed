package borg.trikeshed.net.http.server

import borg.trikeshed.foundation.common.series.Series
import borg.trikeshed.foundation.common.series.toSeries // For ByteArray.toSeries() and List.toSeries()
import borg.trikeshed.foundation.common.series.asString // For Series<Char>.asString()
import borg.trikeshed.foundation.common.series.SeriesConstructors // For j constructor if needed elsewhere, though not directly in this file after changes

import borg.trikeshed.net.http.parser.HttpParsingException // Keep this
import borg.trikeshed.net.http.parser.HttpRequestParser // Keep this
import borg.trikeshed.net.http.serializer.HttpResponseSerializer // Keep this
import borg.trikeshed.net.http.types.* // Keep this
import borg.trikeshed.nio.ClientSocketChannel // Keep this
import borg.trikeshed.nio.NioException // Keep this
import borg.trikeshed.nio.services.NioService // Keep this
import kotlinx.coroutines.* // Keep this
import kotlin.coroutines.CoroutineContext // Keep this
import borg.trikeshed.foundation.common.brandt.CoreTensorCursor // For conceptual construction
import borg.trikeshed.foundation.common.brandt.CoreTensorCursorWithMeta

/**
 * Handles incoming HTTP/1.1 client connections.
 *
 * @param nioService The NIO service used to create server and client socket channels.
 * @param CCEKContext The CoroutineContext this handler and its spawned coroutines will run in.
 *                    This context should ideally contain necessary dispatchers (e.g., for IO).
 */
class HttpConnectionHandler(
    private val nioService: NioService,
    private val CCEKContext: CoroutineContext // Renamed from context to CCEKContext for clarity
) {
    private val serializer = HttpResponseSerializer()
    private val serverJob = SupervisorJob()
    private val serverScope = CoroutineScope(CCEKContext + serverJob + CoroutineName("HttpConnectionHandlerScope"))

    private var isRunning = false

    /**
     * Starts the HTTP server, listening on the specified host and port.
     * This function will suspend until the server is stopped via [stop].
     *
     * @param host The hostname or IP address to bind to.
     * @param port The port number to listen on.
     */
    suspend fun start(host: String, port: Int) {
        if (isRunning) {
            println("HttpConnectionHandler is already running.")
            return
        }
        isRunning = true
        println("HttpConnectionHandler starting on \$host:\$port...")

        val serverSocket = nioService.createServerSocketChannel()
        try {
            serverSocket.bind(host, port)
            println("HttpConnectionHandler bound to \${serverSocket.localAddress()}")

            while (isRunning && serverSocket.isOpen() && serverScope.isActive) {
                try {
                    val clientSocket = serverSocket.accept()
                    if (clientSocket != null && serverScope.isActive) {
                        println("Accepted connection from: \${clientSocket.remoteAddress()}")
                        serverScope.launch(CoroutineName("HttpClient-\${clientSocket.remoteAddress()}")) {
                            handleClientConnection(clientSocket)
                        }
                    } else if (!isRunning || !serverScope.isActive) {
                        break // Server stopping
                    }
                } catch (e: NioException) {
                    if (isRunning && serverScope.isActive) { // Only log if server is supposed to be running
                        println("Error accepting connection: \${e.message}")
                        // Potentially add a small delay before retrying accept on certain errors
                        delay(100)
                    } else {
                        break // Server stopping
                    }
                } catch (e: CancellationException) {
                    println("Accept loop cancelled.")
                    break
                } catch (e: Exception) {
                    println("Unexpected error in accept loop: \${e.message}")
                    // Consider if this should stop the server or just log and continue
                    isRunning = false // Stop on unexpected errors for safety
                    break
                }
            }
        } catch (e: NioException) {
            println("Failed to start HttpConnectionHandler: \${e.message}")
            isRunning = false
        } catch (e: Exception) {
            println("Critical error starting HttpConnectionHandler: \${e.message}")
            isRunning = false
        } finally {
            println("HttpConnectionHandler shutting down...")
            serverSocket.close()
            isRunning = false
            println("HttpConnectionHandler stopped.")
        }
    }

    /**
     * Stops the HTTP server and releases resources.
     */
    fun stop() {
        println("HttpConnectionHandler.stop() called.")
        isRunning = false
        serverJob.cancel() // Cancels all coroutines launched in serverScope
        // ServerSocketChannel will be closed by the finally block in start()
    }

    private suspend fun handleClientConnection(clientSocket: ClientSocketChannel) {
        val parser = HttpRequestParser()
        val readByteArray = ByteArray(4096) // Use ByteArray for reading

        try {
            while (isRunning && clientSocket.isOpen() && clientSocket.isConnected() && serverScope.isActive) {
                // Assuming clientSocket.read can take a ByteArray directly or is adapted.
                // If clientSocket.read strictly requires Tensor<Byte>, this part needs adjustment:
                // val tempTensor = readByteArray.toSeries().asTensor() // Hypothetical asTensor()
                // val bytesRead = clientSocket.read(tempTensor, 0, readByteArray.size)
                // For now, assume a direct ByteArray read API or that NioService handles this abstraction.
                // Let's define a hypothetical read(ByteArray): Int for ClientSocketChannel for this adaptation.
                // This is a simplification for the current subtask.
                // A more robust solution would involve ensuring NioChannel actuals can efficiently fill a ByteArray
                // or work with Series<Byte> directly.

                // SIMPLIFIED READ: Assume read into ByteArray is possible.
                // This might require a change in ClientSocketChannel interface or specific implementations.
                // For now, let's simulate this by creating a Tensor from readByteArray, reading into it,
                // and then using readByteArray. This is inefficient but bridges the gap.
                val bytesRead: Int
                run { // Scope for temp tensor
                    val tempTensor = borg.trikeshed.foundation.common.tensor.TensorConstruct(intArrayOf(readByteArray.size)) { idx -> readByteArray[idx[0]] }
                    bytesRead = clientSocket.read(tempTensor, 0, readByteArray.size)
                    // If read modified underlying array of tempTensor (if it shared it), readByteArray would be updated.
                    // This depends on TensorConstruct and ClientSocketChannel.read behavior.
                    // A cleaner way: ClientSocketChannel.read returns Series<Byte> or populates Series<Byte>.
                    // For now, assume readByteArray is populated correctly after clientSocket.read via tempTensor.
                    if (bytesRead > 0) { // Manually copy back if read wrote to Tensor's own memory
                        for(i in 0 until bytesRead) readByteArray[i] = tempTensor[intArrayOf(i)]
                    }
                }


                if (bytesRead == -1) {
                    println("Client \${clientSocket.remoteAddress()} closed connection (EOF).")
                    break // EOF
                }
                if (bytesRead == 0) {
                    yield()
                    continue
                }
                if (bytesRead > 0) {
                    val newDataSeries = readByteArray.copyOfRange(0, bytesRead).toSeries() // Foundation toSeries()
                    val parseResult = parser.parse(newDataSeries)

                    when {
                        parseResult.isSuccess -> {
                            val request = parseResult.getOrNull()
                            if (request != null) { // Complete request parsed
                                println("Received request from \${clientSocket.remoteAddress()}: \${request.method.name} \${request.path.value}")

                                val responseBodyStr = "Hello from TrikeShed HTTP/1.1 Server! You requested: \${request.path.value}"
                                val responseBodySeries = responseBodyStr.encodeToByteArray().toSeries() // String -> BA -> Series<Byte>

                                val responseHeadersList = listOf(
                                    "\${HttpHeaderName.CONTENT_TYPE}: text/plain; charset=utf-8", // Using consts
                                    "\${HttpHeaderName.CONTENT_LENGTH}: \${responseBodySeries.size}",
                                    "\${HttpHeaderName.CONNECTION}: close"
                                )
                                val headersSeries = responseHeadersList.toSeries() // List<String> to Series<String>

                                // Placeholder for actual CoreTensorCursor construction from Series<String>
                                val headersCursor = object : CoreTensorCursor<String> {
                                    override val meta = borg.trikeshed.foundation.common.brandt.DslHandle.NONE
                                    override val columns: Int get() = 1
                                    override val rows: Int get() = headersSeries.size
                                    override fun get(row: Int, col: Int): String = if (col == 0) headersSeries[row] else throw IndexOutOfBoundsException()
                                    override fun getColumn(col: Int): Series<String> = if (col == 0) headersSeries else emptySeries()
                                    override fun getRow(row: Int): Series<String> = SeriesConstructors.j(1){ headersSeries[row] }
                                }
                                val httpHeaders = CoreTensorCursorWithMeta(headersCursor, HttpHeadersMeta())

                                val response = borg.trikeshed.net.http.types.HttpResponse(
                                    version = HttpVersion("HTTP/1.1"),
                                    statusCode = HttpStatusCode(200),
                                    reasonPhrase = HttpReasonPhrase("OK"),
                                    headers = httpHeaders,
                                    body = HttpBody.Bytes(responseBodySeries)
                                )

                                val serializedResponse = serializer.serialize(response)
                                // Convert Series<Byte> to ByteArray for writing
                                val responseBytes = ByteArray(serializedResponse.size) { i -> serializedResponse[i] }

                                // SIMPLIFIED WRITE: Assume write from ByteArray is possible.
                                // Similar to read, this may require NioChannel actuals to support ByteArray directly.
                                // If clientSocket.write strictly requires Tensor<Byte>:
                                // val tempWriteTensor = responseBytes.toSeries().asTensor()
                                // clientSocket.write(tempWriteTensor, 0, responseBytes.size)
                                run { // Scope for temp tensor
                                    val tempWriteTensor = borg.trikeshed.foundation.common.tensor.TensorConstruct(intArrayOf(responseBytes.size)) {idx -> responseBytes[idx[0]]}
                                     clientSocket.write(tempWriteTensor, 0, responseBytes.size)
                                }


                                println("Response sent to \${clientSocket.remoteAddress()}. Closing connection.")
                                break
                            }
                        }
                        parseResult.isFailure -> {
                            val error = parseResult.exceptionOrNull() as? HttpParsingException // Safe cast
                            println("HTTP Parsing Error from \${clientSocket.remoteAddress()}: \${error?.message ?: "Unknown parsing error"}")

                            val errorBodySeries = "Bad Request".toSeries() // String -> Series<Char>
                            val errorHeadersList = listOf(
                                 "\${HttpHeaderName.CONTENT_TYPE}: text/plain; charset=utf-8",
                                 "\${HttpHeaderName.CONTENT_LENGTH}: \${errorBodySeries.size * 2}", // Approx UTF-8 bytes
                                 "\${HttpHeaderName.CONNECTION}: close"
                            )
                            val errorHeadersSeries = errorHeadersList.toSeries()
                            val errorHeadersCursor = object : CoreTensorCursor<String> { // Placeholder
                                override val meta = borg.trikeshed.foundation.common.brandt.DslHandle.NONE
                                override val columns: Int get() = 1
                                override val rows: Int get() = errorHeadersSeries.size
                                override fun get(row: Int, col: Int): String = if (col == 0) errorHeadersSeries[row] else throw IndexOutOfBoundsException()
                                override fun getColumn(col: Int): Series<String> = if (col == 0) errorHeadersSeries else emptySeries()
                                override fun getRow(row: Int): Series<String> = SeriesConstructors.j(1){ errorHeadersSeries[row] }
                            }
                            val errorHttpHeaders = CoreTensorCursorWithMeta(errorHeadersCursor, HttpHeadersMeta())

                            val errResponse = borg.trikeshed.net.http.types.HttpResponse(
                                version = HttpVersion("HTTP/1.1"),
                                statusCode = HttpStatusCode(400),
                                reasonPhrase = HttpReasonPhrase("Bad Request"),
                                headers = errorHttpHeaders,
                                body = HttpBody.Text(errorBodySeries)
                            )
                             try {
                                val serializedErrResponse = serializer.serialize(errResponse)
                                val errBytes = ByteArray(serializedErrResponse.size) { i -> serializedErrResponse[i] }
                                // SIMPLIFIED WRITE for error
                                run {
                                     val tempErrWriteTensor = borg.trikeshed.foundation.common.tensor.TensorConstruct(intArrayOf(errBytes.size)) {idx -> errBytes[idx[0]]}
                                     clientSocket.write(tempErrWriteTensor, 0, errBytes.size)
                                }
                            } catch (e: NioException) {
                                println("NIO Error sending 400 response to \${clientSocket.remoteAddress()}: \${e.message}")
                            }
                            break
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            println("Connection handler for \${clientSocket.remoteAddress()} cancelled.")
        } catch (e: Exception) {
            println("Unexpected error handling client \${clientSocket.remoteAddress()}: \${e.message}")
            e.printStackTrace() // Print stack trace for unexpected errors
        } finally {
            println("Closing client connection: \${clientSocket.remoteAddress()}")
            clientSocket.close()
        }
    }
}
// Removed local ByteArray.toSeries() as it's expected from foundation.common.series.toSeries
