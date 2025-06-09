package borg.trikeshed.net.http.server

import borg.trikeshed.core.Tensor
import borg.trikeshed.core.TensorConstruct
import borg.trikeshed.core.toSeries
import borg.trikeshed.net.http.parser.HttpParsingException
import borg.trikeshed.net.http.parser.HttpRequestParser
import borg.trikeshed.net.http.serializer.HttpResponseSerializer
import borg.trikeshed.net.http.types.* // All Http types
import borg.trikeshed.nio.ClientSocketChannel
import borg.trikeshed.nio.NioException
import borg.trikeshed.nio.services.NioService
import kotlinx.coroutines.* // For CoroutineScope, launch, Dispatchers, isActive
import kotlin.coroutines.CoroutineContext // For CoroutineContext.Element

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
        // Allocate a reusable buffer (Tensor<Byte>) for reads.
        // Size can be tuned. Using a common network buffer size.
        val readBufferArray = ByteArray(4096)
        val readBufferTensor = TensorConstruct(intArrayOf(readBufferArray.size)) { idxArray -> readBufferArray[idxArray[0]] }


        try {
            while (isRunning && clientSocket.isOpen() && clientSocket.isConnected() && serverScope.isActive) {
                val bytesRead = try {
                    // Pass a view or copy of the tensor if its accessor isn't safe for direct modification by read
                    // For now, assume read can populate the backing array of readBufferTensor if designed so.
                    // The current read() in NioChannels*Native/Jvm.kt uses TensorUtils to copy into a temp array,
                    // then conceptually updates the Tensor. This needs a mutable Tensor or read returning data.
                    // Let's assume for now, read will write into readBufferArray which backs readBufferTensor.
                    // A better model: clientSocket.read(destinationByteArray): Int
                    clientSocket.read(readBufferTensor, 0, readBufferArray.size)
                } catch (e: NioException) {
                    println("NIO Error during read from \${clientSocket.remoteAddress()}: \${e.message}")
                    break // Exit loop on read error
                }

                if (bytesRead == -1) {
                    println("Client \${clientSocket.remoteAddress()} closed connection (EOF).")
                    break // EOF
                }
                if (bytesRead == 0) {
                    // Non-blocking read returned 0, means no data currently available.
                    // This can happen in non-blocking IO. Yield to allow other coroutines to run.
                    yield()
                    continue
                }
                if (bytesRead > 0) {
                    // Feed the read data (only the part that was read) to the parser.
                    // Create a Series<Byte> from the relevant part of readBufferArray.
                    val newDataSeries = readBufferArray.copyOfRange(0, bytesRead).toSeries()
                    val parseResult = parser.parse(newDataSeries)

                    when {
                        parseResult.isSuccess -> {
                            val request = parseResult.getOrNull()
                            if (request != null) { // Complete request parsed
                                println("Received request from \${clientSocket.remoteAddress()}: \${request.a.a.name} \${request.a.b.a.value}")

                                // Simple hardcoded response
                                val responseBodyStr = "Hello from TrikeShed HTTP/1.1 Server! You asked for: \${request.a.b.a.value}"
                                val responseBodyBytes = responseBodyStr.encodeToByteArray()
                                val bodyTensor = TensorConstruct(intArrayOf(responseBodyBytes.size)) { idx -> responseBodyBytes[idx[0]] }

                                val responseHeadersList = listOf(
                                    HttpHeaderName("Content-Type") to HttpHeaderValue("text/plain; charset=utf-8"),
                                    HttpHeaderName("Content-Length") to HttpHeaderValue(responseBodyBytes.size.toString()),
                                    HttpHeaderName("Connection") to HttpHeaderValue("close") // Default to close for simplicity
                                )
                                val headersCursor = borg.trikeshed.core.TensorCursor(responseHeadersList.size, 2) { r, c ->
                                    if (c == 0) responseHeadersList[r].first.name else responseHeadersList[r].second.value
                                }
                                val headersMeta = emptyHttpHeadersMeta()
                                val finalHeaders = headersCursor j headersMeta


                                val response = HttpResponse(
                                    HttpVersion.HTTP_1_1,
                                    HttpStatusCode.OK,
                                    HttpReasonPhrase("OK"),
                                    finalHeaders,
                                    HttpBody.Bytes(bodyTensor)
                                )

                                val serializedResponse = serializer.serialize(response)
                                // Convert Series<Byte> to Tensor<Byte> for writing
                                val responseTensorData = ByteArray(serializedResponse.size) { i -> serializedResponse[i] }
                                val responseTensor = TensorConstruct(intArrayOf(responseTensorData.size)) { idx -> responseTensorData[idx[0]] }

                                try {
                                    clientSocket.write(responseTensor, 0, responseTensorData.size)
                                } catch (e: NioException) {
                                    println("NIO Error during write to \${clientSocket.remoteAddress()}: \${e.message}")
                                    break // Exit loop on write error
                                }

                                // For simplicity, close after one request.
                                // TODO: Implement keep-alive based on request.headers["Connection"]
                                println("Response sent to \${clientSocket.remoteAddress()}. Closing connection.")
                                break // Exit loop, will close socket in finally
                            } else {
                                // Need more data, continue reading
                            }
                        }
                        parseResult.isFailure -> {
                            val error = parseResult.exceptionOrNull() as HttpParsingException
                            println("HTTP Parsing Error from \${clientSocket.remoteAddress()}: \${error.message}")
                            // Send 400 Bad Request
                            val errResponse = HttpResponse(
                                HttpVersion.HTTP_1_1,
                                HttpStatusCode.BAD_REQUEST,
                                HttpReasonPhrase("Bad Request"),
                                emptyHttpHeaders(),
                                HttpBody.Text(TensorConstruct(intArrayOf("Bad Request".length)) { idx -> "Bad Request"[idx[0]] })
                            )
                             try {
                                val serializedErrResponse = serializer.serialize(errResponse)
                                val errTensorData = ByteArray(serializedErrResponse.size) { i -> serializedErrResponse[i] }
                                val errTensor = TensorConstruct(intArrayOf(errTensorData.size)) { idx -> errTensorData[idx[0]] }
                                clientSocket.write(errTensor, 0, errTensor.totalSize)
                            } catch (e: NioException) {
                                println("NIO Error sending 400 response to \${clientSocket.remoteAddress()}: \${e.message}")
                            }
                            break // Close connection on parse error
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            println("Connection handler for \${clientSocket.remoteAddress()} cancelled.")
        } catch (e: Exception) {
            // Catch any other unexpected errors during client handling
            println("Unexpected error handling client \${clientSocket.remoteAddress()}: \${e.message}")
        } finally {
            println("Closing client connection: \${clientSocket.remoteAddress()}")
            clientSocket.close()
        }
    }
}

// Helper to convert ByteArray to Series<Byte>
// This should ideally be in core or a common utility.
internal fun ByteArray.toSeries(): Series<Byte> = this.size j { i -> this[i] }
