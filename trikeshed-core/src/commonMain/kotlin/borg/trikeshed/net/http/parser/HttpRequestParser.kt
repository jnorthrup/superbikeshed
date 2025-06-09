package borg.trikeshed.net.http.parser

package borg.trikeshed.net.http.parser // Ensure package declaration is present

import borg.trikeshed.foundation.common.series.Series
import borg.trikeshed.foundation.common.series.asString
import borg.trikeshed.foundation.common.series.emptySeries
import borg.trikeshed.foundation.common.series.plus
import borg.trikeshed.foundation.common.series.size // Assuming this is how Series.size is accessed if not a direct member
import borg.trikeshed.foundation.common.series.slice // Assuming Series.slice extension
import borg.trikeshed.foundation.common.series.toSeries // Assuming ByteArray.toSeries and List.toSeries extensions
import borg.trikeshed.foundation.common.series.SeriesConstructors // For the 'j' series constructor

import borg.trikeshed.foundation.common.brandt.CoreTensorCursor
import borg.trikeshed.foundation.common.brandt.CoreTensorCursorWithMeta
// HttpTypes already imported via borg.trikeshed.net.http.types.*

import borg.trikeshed.net.http.types.*


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

    private var parsedMethod: borg.trikeshed.net.http.types.HttpMethod? = null // Updated type
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
                            method = parsedMethod!!, // Already correct enum type from previous change
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

        parsedMethod = try {
            borg.trikeshed.net.http.types.HttpMethod.valueOf(parts[0].uppercase())
        } catch (e: IllegalArgumentException) {
            throw HttpParsingException("Invalid HTTP method: \${parts[0]}", e)
        }
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
                    if (name.value.equals("content-length", ignoreCase = true)) {
                        expectedBodyLength = value.value.toLongOrNull()
                            ?: throw HttpParsingException("Invalid Content-Length value: \${value.value}")
                        if (expectedBodyLength!! < 0) throw HttpParsingException("Content-Length cannot be negative.")
                    } else if (name.value.equals("transfer-encoding", ignoreCase = true)) {
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
                val bodyBytesArray = bufferedData.slice(0 until bodyLen).toArray()
                val bodySeries = bodyBytesArray.toSeries() // Convert ByteArray to Series<Byte>
                parsedBody = HttpBody.Bytes(bodySeries)
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
        if (parsedHeadersList.isEmpty()) {
            // Create an empty CoreTensorCursor<String>
            // This requires knowing how to construct an empty CoreTensorCursor from foundation.
            // Assuming an extension `emptySeries<String>().asCoreTensorCursor()` or similar.
            // For now, creating a conceptual empty cursor.
            val emptyHeaderSeries = emptySeries<String>()
            // This is a placeholder for actual conversion/factory method from foundation.
            val emptyCursor = object : CoreTensorCursor<String> {
                override val meta: borg.trikeshed.foundation.common.brandt.DslHandle = borg.trikeshed.foundation.common.brandt.DslHandle.NONE
                override val columns: Int get() = 1 // Even if empty, schema might imply 1 column for "Name: Value" string
                override val rows: Int get() = 0
                override fun get(row: Int, col: Int): String = throw IndexOutOfBoundsException()
                override fun getColumn(col: Int): Series<String> = emptySeries()
                override fun getRow(row: Int): Series<String> = emptySeries()
            }
            return CoreTensorCursorWithMeta(emptyCursor, HttpHeadersMeta())
        }

        val headerStrings = parsedHeadersList.map { "\${it.first.value}: \${it.second.value}" }
        val headersSeries: Series<String> = headerStrings.toSeries() // Foundation List.toSeries()

        // Convert Series<String> to CoreTensorCursor<String>
        // This is a placeholder for actual foundation API.
        val cursor = object : CoreTensorCursor<String> {
            override val meta: borg.trikeshed.foundation.common.brandt.DslHandle = borg.trikeshed.foundation.common.brandt.DslHandle.NONE
            override val columns: Int get() = 1 // Each string is one "Name: Value" line
            override val rows: Int get() = headersSeries.size
            override fun get(row: Int, col: Int): String {
                if (col != 0) throw IndexOutOfBoundsException("Only one column for header strings")
                return headersSeries[row]
            }
            override fun getColumn(col: Int): Series<String> {
                 if (col != 0) throw IndexOutOfBoundsException("Only one column for header strings")
                return headersSeries
            }
            override fun getRow(row: Int): Series<String> = SeriesConstructors.j(1) { headersSeries[row] }
        }
        return CoreTensorCursorWithMeta(cursor, HttpHeadersMeta())
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
        return SeriesConstructors.j(this.size) { i -> this[i].toInt().toChar() }
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
        parsedBody = HttpBody.Empty // Reset parsedBody as well
    }
}

// --- Extension for Series<Byte> slice (if not available from core) ---
// This is a simplified slice. A proper one would handle negative indices or steps.
// Should use foundation series slice if available and compatible.
// For now, assuming this local one or SeriesConstructors.j based one is needed.
internal fun Series<Byte>.slice(range: IntRange): Series<Byte> {
    val start = range.first.coerceAtLeast(0).coerceAtMost(this.size) // Ensure start is not > size
    val end = range.last.coerceAtMost(this.size - 1)
    if (start > end || start >= this.size) return emptySeries() // Check start >= this.size for empty slice
    val newSize = end - start + 1
    return SeriesConstructors.j(newSize) { i -> this[start + i] }
}

// Helper to convert Series<Byte> to ByteArray for HttpBody.Bytes
// This should ideally be part of Series or a common utility.
internal fun Series<Byte>.toArray(): ByteArray {
    if (this.size == 0) return ByteArray(0)
    return ByteArray(this.size) { i -> this[i] }
}
