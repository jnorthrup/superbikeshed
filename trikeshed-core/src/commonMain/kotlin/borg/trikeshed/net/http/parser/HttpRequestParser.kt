package borg.trikeshed.net.http.parser

import borg.trikeshed.core.Series
import borg.trikeshed.core.Tensor
import borg.trikeshed.core.TensorConstruct // For creating Tensor<Byte>
import borg.trikeshed.core.asString
import borg.trikeshed.core.emptySeries
import borg.trikeshed.core.j
import borg.trikeshed.core.plus
import borg.trikeshed.core.size
import borg.trikeshed.core.slice // If Series has a slice operation like tensor
import borg.trikeshed.core.toSeries // For ByteArray.toSeries()
import borg.trikeshed.net.http.types.* // All our Http types

// --- HttpParsingException ---
class HttpParsingException(message: String, cause: Throwable? = null) : Exception(message, cause)

// --- Parser State ---
enum class HttpParsingState {
    REQUEST_LINE,
    HEADERS,
    BODY,
    COMPLETE,
    ERROR
}

/**
 * A parser for HTTP/1.1 requests.
 * This parser is designed to be incremental, allowing data to be fed in chunks.
 */
class HttpRequestParser {

    private var currentState: HttpParsingState = HttpParsingState.REQUEST_LINE
    private var bufferedData: Series<Byte> = emptySeries() // Accumulates bytes across parse calls

    private var parsedMethod: HttpMethod? = null
    private var parsedPath: HttpRequestPath? = null
    private var parsedVersion: HttpVersion? = null
    private val parsedHeadersList = mutableListOf<Pair<HttpHeaderName, HttpHeaderValue>>()
    private var expectedBodyLength: Long? = null
    private var isChunkedTransfer: Boolean = false
    private var parsedBody: HttpBody = HttpBody.Empty // Store parsed body

    private companion object {
        const val SP: Byte = 0x20
        const val CR: Byte = 0x0D
        const val LF: Byte = 0x0A
        const val COLON: Byte = 0x3A
    }

    fun parse(newData: Series<Byte>): Result<HttpRequest?> {
        if (currentState == HttpParsingState.ERROR || currentState == HttpParsingState.COMPLETE) {
            return Result.failure(HttpParsingException("Parser is in a terminal state: \$currentState. Reset before parsing new request."))
        }

        bufferedData = bufferedData + newData

        try {
            while (true) {
                when (currentState) {
                    HttpParsingState.REQUEST_LINE -> {
                        if (!processRequestLine()) return Result.success(null)
                        currentState = HttpParsingState.HEADERS
                    }
                    HttpParsingState.HEADERS -> {
                        val headersProcessed = processHeaders() // processHeaders now returns Boolean
                        if (!headersProcessed) return Result.success(null)

                        if (isChunkedTransfer) {
                            currentState = HttpParsingState.BODY
                        } else if (expectedBodyLength != null && expectedBodyLength!! > 0L) {
                            currentState = HttpParsingState.BODY
                        } else {
                            parsedBody = HttpBody.Empty // Ensure body is set to Empty if no body expected
                            currentState = HttpParsingState.COMPLETE
                        }
                    }
                    HttpParsingState.BODY -> {
                        if (!processBody()) return Result.success(null) // More data needed for body
                        currentState = HttpParsingState.COMPLETE
                    }
                    HttpParsingState.COMPLETE -> {
                        val finalHeaders = constructHttpHeaders()
                        val request = HttpRequest(
                            method = parsedMethod!!,
                            path = parsedPath!!,
                            version = parsedVersion!!,
                            headers = finalHeaders,
                            body = parsedBody
                        )
                        // Data for this request is now fully parsed.
                        // The remaining bufferedData (if any) is for the next request.
                        // No need to clear bufferedData here; it holds the start of the next request.
                        return Result.success(request)
                    }
                    HttpParsingState.ERROR -> {
                        return Result.failure(HttpParsingException("Parser is in an error state."))
                    }
                }
            }
        } catch (e: HttpParsingException) {
            currentState = HttpParsingState.ERROR
            return Result.failure(e)
        } catch (e: Exception) {
            currentState = HttpParsingState.ERROR
            return Result.failure(HttpParsingException("Unexpected error during parsing: \${e.message}", e))
        }
    }

    /**
     * Attempts to parse the request line from the buffered data.
     * @return `true` if the request line was successfully parsed, `false` if more data is needed.
     * @throws HttpParsingException if the request line is malformed.
     */
    private fun processRequestLine(): Boolean {
        // Find CRLF
        val crlfIndex = findCRLF(bufferedData, 0)
        if (crlfIndex == -1) {
            return false
        }

        val requestLineBytes = bufferedData.slice(0 until crlfIndex)
        val lineAndTerminatorLength = crlfIndex + 2 // +2 for CRLF
        if (bufferedData.size < lineAndTerminatorLength) return false // Should not happen if crlfIndex is valid

        bufferedData = bufferedData.slice(lineAndTerminatorLength until bufferedData.size)

        // Split requestLineBytes by SP
        // This is inefficient. A direct byte Series search for SP would be better.
        // For now, converting to String for simplicity of split, but this is not ideal for performance/raw bytes.
        // A proper byte-level split or tokenization is needed for robustness.
        val requestLineString = try {
            requestLineBytes.toSeriesOfChars().asString() // Assuming toSeriesOfChars and asString
        } catch (e: Exception) {
            throw HttpParsingException("Request line contains invalid characters", e)
        }


        val parts = requestLineString.split(' ', limit = 3)
        if (parts.size != 3) {
            throw HttpParsingException("Malformed request line: '\$requestLineString'. Expected 3 parts.")
        }

        parsedMethod = HttpMethod(parts[0].uppercase())
        parsedPath = HttpRequestPath(parts[1])
        parsedVersion = HttpVersion(parts[2].uppercase())

        // Basic validation
        if (parts[0].isEmpty()) throw HttpParsingException("Request method cannot be empty.")
        if (parts[1].isEmpty()) throw HttpParsingException("Request path cannot be empty.")
        if (!parts[2].startsWith("HTTP/")) throw HttpParsingException("Invalid HTTP version format: '\${parts[2]}'")

        return true
    }

    /**
     * Attempts to parse header lines from the buffered data.
     * @return `true` if the end of headers (CRLFCRLF) was found and processed,
     *         `false` if more data is needed.
     * @throws HttpParsingException if headers are malformed.
     */
    private fun processHeaders(): Boolean {
        var currentParseOffset = 0 // Offset within the current bufferedData being scanned in this call
        while (true) {
            // Ensure there's enough data to find a CRLF and potentially the one after it for empty line
            if (bufferedData.size < currentParseOffset + 2) return false // Need at least CRLF

            val crlfIndex = findCRLF(bufferedData, currentParseOffset)
            if (crlfIndex == -1) {
                return false // Need more data for a full header line or end of headers
            }

            val headerLineBytes = bufferedData.slice(currentParseOffset until crlfIndex)
            val consumedThisLineAndTerminatorLength = (crlfIndex - currentParseOffset) + 2

            if (headerLineBytes.isEmpty()) { // Empty line (CRLFCRLF) found
                bufferedData = bufferedData.slice(currentParseOffset + consumedThisLineAndTerminatorLength until bufferedData.size)

                for ((name, value) in parsedHeadersList) {
                    if (name.normalized() == "content-length") {
                        expectedBodyLength = value.value.toLongOrNull()
                            ?: throw HttpParsingException("Invalid Content-Length value: \${value.value}")
                        if (expectedBodyLength!! < 0) throw HttpParsingException("Content-Length cannot be negative.")
                    } else if (name.normalized() == "transfer-encoding") {
                        // Simple check for "chunked". Does not handle multiple encodings.
                        if (value.value.contains("chunked", ignoreCase = true)) {
                            isChunkedTransfer = true
                            expectedBodyLength = null
                        }
                    }
                }
                return true // End of headers found
            }

            val colonIndex = headerLineBytes.indexOf(COLON)
            if (colonIndex == -1 || colonIndex == 0) {
                throw HttpParsingException("Malformed header line (missing colon or empty name): \${headerLineBytes.toSeriesOfChars().asString()}")
            }

            val nameBytes = headerLineBytes.slice(0 until colonIndex)
            val valueBytes = headerLineBytes.slice(colonIndex + 1 until headerLineBytes.size)

            val name = HttpHeaderName(nameBytes.toSeriesOfChars().asString().trim())
            val value = HttpHeaderValue(valueBytes.toSeriesOfChars().asString().trim())

            if (name.name.isEmpty()) throw HttpParsingException("Header name cannot be empty.")

            parsedHeadersList.add(name to value)
            currentParseOffset += consumedThisLineAndTerminatorLength

            // After parsing one header, we don't slice bufferedData yet.
            // We only slice bufferedData once all headers are processed or more data is needed.
            // If we return false (need more data), bufferedData remains as is.
            // If we find CRLFCRLF (return true), then we slice bufferedData to remove all processed headers.
        }
    }

    /**
     * Attempts to parse the HTTP body from the buffered data.
     * @return `true` if the body was successfully parsed or no body is expected,
     *         `false` if more data is needed for the body.
     * @throws HttpParsingException if body parsing fails (e.g., chunked encoding error).
     */
    private fun processBody(): Boolean {
        if (isChunkedTransfer) {
            // TODO: Implement chunked body parsing
            // This is complex: read chunk-size CRLF, read chunk-data CRLF, repeat until 0 CRLF CRLF
            // For now, placeholder assumes body is complete.
            println("HttpRequestParser: Chunked transfer encoding not fully implemented. Assuming body complete.")
            parsedBody = HttpBody.Empty // Placeholder for chunked body
            bufferedData = emptySeries() // Consume all data for now as placeholder
            return true
        } else if (expectedBodyLength != null && expectedBodyLength!! > 0L) {
            val bodyLen = expectedBodyLength!!.toInt()
            if (bufferedData.size >= bodyLen) {
                val bodyBytes = bufferedData.slice(0 until bodyLen).toArray() // Convert Series<Byte> to ByteArray
                // Construct Tensor<Byte> (rank 1)
                val bodyTensor = TensorConstruct(intArrayOf(bodyLen)) { idxArray -> bodyBytes[idxArray[0]] }
                parsedBody = HttpBody.Bytes(bodyTensor)
                bufferedData = bufferedData.slice(bodyLen until bufferedData.size)
                return true
            } else {
                return false // Need more data for body
            }
        } else {
            // No body expected (Content-Length was 0 or not present, and not chunked)
            parsedBody = HttpBody.Empty
            return true
        }
    }

    private fun constructHttpHeaders(): HttpHeaders {
        val numHeaders = parsedHeadersList.size
        val headersCursor = if (numHeaders > 0) {
            borg.trikeshed.core.TensorCursor(numHeaders, 2) { r, c -> // Use core.TensorCursor
                val headerPair = parsedHeadersList[r]
                if (c == 0) headerPair.first.name else headerPair.second.value
            }
        } else {
            emptyHttpHeadersCursor()
        }
        // Assuming HttpHeadersMeta defines "Name" and "Value" columns.
        val headersMeta = emptyHttpHeadersMeta()
        return headersCursor j headersMeta
    }

    private fun Series<Byte>.indexOf(byte: Byte, startIndex: Int = 0): Int {
        for (i in startIndex until this.size) {
            if (this[i] == byte) return i
        }
        return -1
    }

    private fun findCRLF(data: Series<Byte>, startIndex: Int): Int {
        for (i in startIndex until data.size - 1) {
            if (data[i] == CR && data[i + 1] == LF) return i
        }
        return -1
    }

    private fun Series<Byte>.toSeriesOfChars(): Series<Char> {
        return this.size j { i -> this[i].toInt().toChar() }
    }

    fun reset() {
        currentState = HttpParsingState.REQUEST_LINE
        bufferedData = emptySeries()
        parsedMethod = null
        parsedPath = null
        parsedVersion = null
        parsedHeadersList.clear()
        expectedBodyLength = null
        isChunkedTransfer = false
    }
}

// --- Extension for Series<Byte> slice (if not available from core) ---
// This is a simplified slice. A proper one would handle negative indices or steps.
internal fun Series<Byte>.slice(range: IntRange): Series<Byte> {
    val start = range.first.coerceAtLeast(0)
    val end = range.last.coerceAtMost(this.size - 1)
    if (start > end) return emptySeries()
    val newSize = end - start + 1
    return newSize j { i -> this[start + i] }
}
