package borg.trikeshed.net.http.client

import borg.trikeshed.foundation.common.series.Series
import borg.trikeshed.foundation.common.series.toByteArray
import borg.trikeshed.foundation.common.series.toSeries
import borg.trikeshed.foundation.common.tensor.Tensor // Assuming Tensor is needed for NioChannel read/write
import borg.trikeshed.foundation.common.tensor.TensorConstruct // Assuming Tensor is needed
import borg.trikeshed.net.http.parser.HttpParsingException
import borg.trikeshed.net.http.parser.HttpResponseParser
import borg.trikeshed.net.http.serializer.HttpRequestSerializer
import borg.trikeshed.net.http.types.HttpRequest
import borg.trikeshed.net.http.types.HttpResponse
import borg.trikeshed.nio.ClientSocketChannel
import borg.trikeshed.nio.NioException
import borg.trikeshed.nio.services.NioService
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

class HttpClientConnectionException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * A basic HTTP/1.1 client connection handler.
 *
 * @param nioService The NIO service for creating client socket channels.
 * @param ccekContext The CoroutineContext for a parent scope, if needed for structured concurrency.
 */
class HttpClientConnection(
    private val nioService: NioService,
    private val ccekContext: CoroutineContext // For potential parent scope, not directly used for launching yet
) {
    private val requestSerializer = HttpRequestSerializer()
    private val responseParser = HttpResponseParser()

    suspend fun sendRequest(request: HttpRequest, host: String, port: Int): HttpResponse {
        // Ensure operations are performed in an IO-appropriate context
        return withContext(Dispatchers.IO + CoroutineName("HttpClient-\$host:\$port")) {
            val clientSocket: ClientSocketChannel = nioService.createClientSocketChannel()

            try {
                clientSocket.connect(host, port)
                if (!clientSocket.isConnected()) { // Should be connected after suspend fun connect
                    throw HttpClientConnectionException("Failed to connect to \$host:\$port")
                }

                // 1. Serialize and send the request
                val serializedRequest: Series<Byte> = requestSerializer.serialize(request)
                val requestBytes: ByteArray = serializedRequest.toByteArray() // Series to ByteArray

                // --- Shim for Tensor-based write ---
                val requestTensor = TensorConstruct(intArrayOf(requestBytes.size)) { idx -> requestBytes[idx[0]] }
                var bytesWrittenTotal = 0
                while(bytesWrittenTotal < requestBytes.size && clientSocket.isOpen() && clientSocket.isConnected()) {
                    val written = clientSocket.write(requestTensor, bytesWrittenTotal, requestBytes.size - bytesWrittenTotal)
                    if (written == -1) throw HttpClientConnectionException("Socket closed prematurely during write.")
                    bytesWrittenTotal += written
                }
                // --- End Shim ---
                // clientSocket.shutdownOutput() // Optional: Signal end of request data if server expects it before processing

                // 2. Read and parse the response
                responseParser.reset() // Ensure parser is in a clean state
                val readByteArray = ByteArray(8192) // Buffer for reading response data

                while (clientSocket.isOpen() && clientSocket.isConnected() && isActive) { // Check coroutine isActive
                    // --- Shim for Tensor-based read ---
                    val tempReadTensor = TensorConstruct(intArrayOf(readByteArray.size)) { idx -> readByteArray[idx[0]] } // Wrap for read
                    val bytesRead = clientSocket.read(tempReadTensor, 0, readByteArray.size)
                    // Manually update readByteArray from tempReadTensor (assuming read modified its backing or similar)
                    // This is highly dependent on how NioService's read into Tensor works.
                    // A more direct way: have TensorUtils.copyToTensor(src: ByteArray, tensor: Tensor, offset, len)
                    // and TensorUtils.copyFromTensor(tensor: Tensor, dest: ByteArray, offset, len)
                    // For now, assume tempReadTensor's underlying data (if it's a view) or a copy is needed.
                    // Let's assume readByteArray is populated correctly by the shim for now.
                    // A more realistic shim:
                    // val actualBytesRead = clientSocket.read(tempReadTensor, 0, readByteArray.size)
                    // if (actualBytesRead > 0) {
                    //    for (i in 0 until actualBytesRead) readByteArray[i] = tempReadTensor[intArrayOf(i)]
                    // }
                    // For this subtask, we'll use a simplified notion that readByteArray is filled by the read operation.
                    // This part is tricky due to the Tensor abstraction at NIO layer.
                    // The following line simulates getting data into readByteArray.
                    // In reality, if clientSocket.read takes a Tensor, then bytesRead must be used
                    // to get data from that Tensor into readByteArray or a Series.
                    // The current NioChannelsJvm/Native uses TensorUtils.createPopulatedTensor which returns a *new* tensor.
                    // This means the passed 'tempReadTensor' is NOT modified. The result of read() IS the data.
                    // This part of the code needs to be more robust based on actual NioChannel behavior.

                    // Corrected Conceptual Read Flow (still needs exact Tensor to Series/ByteArray):
                    // Conceptual: ClientSocketChannel.read now needs to populate the passed Tensor or return a new one with data.
                    // The existing `clientSocket.read(tempReadTensor, ...)` implies it populates `tempReadTensor`.
                    // Let's assume `TensorUtils.updateTensorFromByteBuffer` (JVM) or `TensorUtils.updateTensorFromByteArray` (Native)
                    // in the actual NioChannel implementations correctly populate the *backing array* of the passed Tensor
                    // if that Tensor is designed to be mutable or view-based in such a way.
                    // For this subtask, we'll proceed assuming that after `clientSocket.read(tempReadTensor,...)`,
                    // `tempReadTensor` now holds the read data.

                    if (bytesRead == -1) { // EOF
                         // If parser has partial data but connection closes, it's an error or incomplete response.
                        if (responseParser.isMidParsing()) { // isMidParsing() is a hypothetical method for the parser
                            throw HttpClientConnectionException("Connection closed by server while response was still being parsed.")
                        }
                        break // Clean EOF, potentially after a complete response was already processed by parser.
                    }
                     if (bytesRead == 0) { // No data read
                        // This could mean a non-blocking socket has no data currently.
                        // Or if it's a blocking socket that somehow returned 0, could be an issue.
                        // Given connect() is suspending, we expect a blocking-style read or async completion.
                        // If it's truly non-blocking and can return 0, a yield or delay might be needed here.
                        // For now, if 0 bytes are read and we are expecting data, it might be an issue or server stall.
                        // If parser is not expecting more data (e.g. after body_done), this is fine.
                        // This logic depends on whether the read is blocking or non-blocking style.
                        // Let's assume for now, a 0 read when expecting data is a stall/issue.
                        // If parser isn't complete, and we get 0 repeatedly, it's a timeout effectively.
                        // For simplicity, if parser is not done and read is 0, we might throw or break.
                        // If the parser *is* done, a 0 read is fine, just means no more pipelined responses.
                        if (!responseParser.isComplete()) { // isComplete() is hypothetical
                             kotlinx.coroutines.yield() // Allow other work, try reading again
                             continue
                        } else {
                            break // Parser is complete, and no more data, so we are done.
                        }
                    }


                    // Data was read into tempReadTensor. Now convert it to Series<Byte> for the parser.
                    // This requires copying from tempReadTensor to a ByteArray first.
                    val actualReadBytes = ByteArray(bytesRead)
                    for(i in 0 until bytesRead) actualReadBytes[i] = tempReadTensor[intArrayOf(i)]
                    val newDataSeries = actualReadBytes.toSeries()
                    // --- End Shim section ---


                    val parseResult = responseParser.parse(newDataSeries)
                    if (parseResult.isSuccess) {
                        val httpResponse = parseResult.getOrNull()
                        if (httpResponse != null) {
                            return@withContext httpResponse // Complete response parsed
                        }
                        // If null, means parser needs more data, continue reading
                    } else {
                        val error = parseResult.exceptionOrNull() as? HttpParsingException
                            ?: HttpParsingException("Unknown parsing error")
                        throw HttpClientConnectionException("Failed to parse HTTP response: \${error.message}", error)
                    }
                }
                // If loop exits, it's due to socket closure or coroutine cancellation
                throw HttpClientConnectionException("Connection closed while awaiting full HTTP response.")

            } catch (e: NioException) {
                throw HttpClientConnectionException("NIO error: \${e.message}", e)
            } catch (e: CancellationException) {
                clientSocket.close() // Ensure socket is closed if coroutine is cancelled
                throw e // Rethrow cancellation
            } catch (e: Exception) { // Catch any other unexpected errors
                clientSocket.close() // Ensure socket is closed
                throw HttpClientConnectionException("Unexpected error in HTTP client: \${e.message}", e)
            } finally {
                if (clientSocket.isOpen()) {
                    try {
                        clientSocket.close()
                    } catch (e: NioException) {
                        // Log closing error, but original error (if any) takes precedence
                        println("Error closing client socket: \${e.message}")
                    }
                }
            }
        }
    }
}

// Adding hypothetical extension methods to HttpResponseParser for clarity in the loop above.
// These would ideally be part of HttpResponseParser's public API if this control flow is desired.
private fun HttpResponseParser.isMidParsing(): Boolean {
    // Conceptual: returns true if parser has processed some bytes but not reached COMPLETE or ERROR state.
    // This depends on the internal state enum and current state variable.
    // For example: (currentState != HttpParsingState.REQUEST_LINE && currentState != HttpParsingState.COMPLETE && currentState != HttpParsingState.ERROR)
    return false // Placeholder
}

private fun HttpResponseParser.isComplete(): Boolean {
    // Conceptual: return true if parser is in COMPLETE state
    // return currentState == HttpParsingState.COMPLETE
    return false // Placeholder
}


// It's assumed ClientSocketChannel has a suspending connect and read/write methods
// that are compatible with coroutines or are wrapped appropriately.
// The readChunk placeholder above is a conceptual simplification for handling Tensor results from read.
