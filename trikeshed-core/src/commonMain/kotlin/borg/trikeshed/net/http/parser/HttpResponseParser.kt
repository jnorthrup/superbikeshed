package borg.trikeshed.net.http.parser

import borg.trikeshed.foundation.common.brandt.ActualCoreTensorCursor // Assuming this constructor exists
import borg.trikeshed.foundation.common.brandt.CoreTensorCursorWithMeta
import borg.trikeshed.foundation.common.series.Series
import borg.trikeshed.foundation.common.series.SeriesConstructors // For .j constructor
import borg.trikeshed.foundation.common.series.asString
import borg.trikeshed.foundation.common.series.emptySeries
import borg.trikeshed.foundation.common.series.plus
import borg.trikeshed.foundation.common.series.size
import borg.trikeshed.foundation.common.series.slice
import borg.trikeshed.foundation.common.series.toByteArray
import borg.trikeshed.foundation.common.series.toSeries
import borg.trikeshed.net.http.types.* // All our Http types

// --- HttpParsingException is already defined in HttpRequestParser.kt, assuming same module/package visibility ---
// If not, it should be defined here or in a common file. For now, assume visible.

// --- Parser State (can be reused if in same package, or redefined) ---
// enum class HttpResponseParsingState { STATUS_LINE, HEADERS, BODY, COMPLETE, ERROR }
// For now, assume HttpParsingState from HttpRequestParser is visible and applicable.


/**
 * A parser for HTTP/1.1 responses.
 * This parser is designed to be incremental, allowing data to be fed in chunks.
 */
class HttpResponseParser {

    private var currentState: HttpParsingState = HttpParsingState.REQUEST_LINE // Reuse enum, but means STATUS_LINE conceptually
    private var bufferedData: Series<Byte> = emptySeries()

    private var parsedVersion: HttpVersion? = null
    private var parsedStatusCode: HttpStatusCode? = null
    private var parsedReasonPhrase: HttpReasonPhrase? = null
    private val parsedHeadersList = mutableListOf<Pair<HttpHeaderName, HttpHeaderValue>>()
    private var expectedBodyLength: Long? = null
    private var isChunkedTransfer: Boolean = false
    private var parsedBody: HttpBody = HttpBody.Empty

    private companion object {
        const val SP: Byte = 0x20
        const val CR: Byte = 0x0D
        const val LF: Byte = 0x0A
        const val COLON: Byte = 0x3A
    }

    init {
        // Adjust initial state name for clarity if using shared enum
        currentState = HttpParsingState.REQUEST_LINE // Represents the "STATUS_LINE" phase
    }

    fun parse(newData: Series<Byte>): Result<HttpResponse?> {
        if (currentState == HttpParsingState.ERROR || currentState == HttpParsingState.COMPLETE) {
            return Result.failure(HttpParsingException("Parser is in a terminal state: \$currentState. Reset before parsing new response."))
        }

        bufferedData = bufferedData + newData

        try {
            while (true) {
                when (currentState) {
                    HttpParsingState.REQUEST_LINE -> { // Interpreted as STATUS_LINE
                        if (!processStatusLine()) return Result.success(null)
                        currentState = HttpParsingState.HEADERS
                    }
                    HttpParsingState.HEADERS -> {
                        if (!processHeaders()) return Result.success(null)

                        if (isChunkedTransfer) {
                            currentState = HttpParsingState.BODY
                        } else if (expectedBodyLength != null && expectedBodyLength!! > 0L) {
                            currentState = HttpParsingState.BODY
                        } else {
                            parsedBody = HttpBody.Empty
                            currentState = HttpParsingState.COMPLETE
                        }
                    }
                    HttpParsingState.BODY -> {
                        if (!processBody()) return Result.success(null)
                        currentState = HttpParsingState.COMPLETE
                    }
                    HttpParsingState.COMPLETE -> {
                        val finalHeaders = constructHttpHeaders()
                        val response = HttpResponse(
                            version = parsedVersion!!,
                            statusCode = parsedStatusCode!!,
                            reasonPhrase = parsedReasonPhrase!!,
                            headers = finalHeaders,
                            body = parsedBody
                        )
                        // Remaining bufferedData is for the next response if pipelining (not typical for client).
                        return Result.success(response)
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

    private fun processStatusLine(): Boolean {
        val crlfIndex = findCRLF(bufferedData, 0)
        if (crlfIndex == -1) return false

        val statusLineBytes = bufferedData.slice(0 until crlfIndex)
        val lineAndTerminatorLength = crlfIndex + 2

        bufferedData = bufferedData.slice(lineAndTerminatorLength until bufferedData.size)

        val statusLineString = try {
            statusLineBytes.toSeriesOfChars().asString()
        } catch (e: Exception) {
            throw HttpParsingException("Status line contains invalid characters", e)
        }

        val parts = statusLineString.split(' ', limit = 3)
        if (parts.size < 2) { // Reason phrase can be empty
            throw HttpParsingException("Malformed status line: '\$statusLineString'. Expected at least 2 parts.")
        }

        parsedVersion = HttpVersion(parts[0].uppercase())
        parsedStatusCode = HttpStatusCode(parts[1].toIntOrNull()
            ?: throw HttpParsingException("Invalid status code: \${parts[1]}"))
        parsedReasonPhrase = HttpReasonPhrase(if (parts.size == 3) parts[2] else "")

        if (!parts[0].startsWith("HTTP/")) throw HttpParsingException("Invalid HTTP version format: '\${parts[0]}'")

        return true
    }

    private fun processHeaders(): Boolean { // Same as HttpRequestParser, adapted for Series
        var currentParseOffset = 0
        while (true) {
            if (bufferedData.size < currentParseOffset + 2) return false

            val crlfIndex = findCRLF(bufferedData, currentParseOffset)
            if (crlfIndex == -1) return false

            val headerLineBytes = bufferedData.slice(currentParseOffset until crlfIndex)
            val consumedThisLineAndTerminatorLength = (crlfIndex - currentParseOffset) + 2

            if (headerLineBytes.isEmpty()) { // Empty line (CRLFCRLF) found
                bufferedData = bufferedData.slice(currentParseOffset + consumedThisLineAndTerminatorLength until bufferedData.size)

                parsedHeadersList.forEach { (name, value) ->
                    if (name.value.equals("content-length", ignoreCase = true)) {
                        expectedBodyLength = value.value.toLongOrNull()
                            ?: throw HttpParsingException("Invalid Content-Length value: \${value.value}")
                        if (expectedBodyLength!! < 0) throw HttpParsingException("Content-Length cannot be negative.")
                    } else if (name.value.equals("transfer-encoding", ignoreCase = true)) {
                        if (value.value.contains("chunked", ignoreCase = true)) {
                            isChunkedTransfer = true
                            expectedBodyLength = null
                        }
                    }
                }
                return true // End of headers
            }

            val colonIndex = headerLineBytes.indexOf(COLON)
            if (colonIndex == -1 || colonIndex == 0) {
                throw HttpParsingException("Malformed header line: \${headerLineBytes.toSeriesOfChars().asString()}")
            }

            val name = HttpHeaderName(headerLineBytes.slice(0 until colonIndex).toSeriesOfChars().asString().trim())
            val value = HttpHeaderValue(headerLineBytes.slice(colonIndex + 1 until headerLineBytes.size).toSeriesOfChars().asString().trim())

            if (name.value.isEmpty()) throw HttpParsingException("Header name cannot be empty.")
            parsedHeadersList.add(name to value)
            currentParseOffset += consumedThisLineAndTerminatorLength
        }
    }

    private fun processBody(): Boolean { // Same as HttpRequestParser, adapted for Series
        if (isChunkedTransfer) {
            // TODO: Implement chunked body parsing
            println("HttpResponseParser: Chunked transfer encoding not fully implemented.")
            parsedBody = HttpBody.Empty
            bufferedData = emptySeries()
            return true
        } else if (expectedBodyLength != null && expectedBodyLength!! > 0L) {
            val bodyLen = expectedBodyLength!!.toInt() // Safe cast due to typical HTTP body sizes
            if (bufferedData.size >= bodyLen) {
                val bodyBytesArray = bufferedData.slice(0 until bodyLen).toByteArray()
                parsedBody = HttpBody.Bytes(bodyBytesArray.toSeries())
                bufferedData = bufferedData.slice(bodyLen until bufferedData.size)
                return true
            } else {
                return false // Need more data
            }
        } else {
            parsedBody = HttpBody.Empty
            return true
        }
    }

    private fun constructHttpHeaders(): HttpHeaders { // Same as HttpRequestParser
        val headerStrings = parsedHeadersList.map { "\${it.first.value}: \${it.second.value}" }
        val headersSeries = headerStrings.toSeries()

        // Placeholder for ActualCoreTensorCursor construction or Series.asCoreTensorCursor()
        val cursor = ActualCoreTensorCursor(headersSeries)
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
        currentState = HttpParsingState.REQUEST_LINE // for STATUS_LINE
        bufferedData = emptySeries()
        parsedVersion = null
        parsedStatusCode = null
        parsedReasonPhrase = null
        parsedHeadersList.clear()
        expectedBodyLength = null
        isChunkedTransfer = false
        parsedBody = HttpBody.Empty
    }
}

// Assuming Series<Byte>.slice is available from foundation or via HttpRequestParser's context (if in same file/module)
// If not, it would need to be redefined or imported here too.
// internal fun Series<Byte>.slice(range: IntRange): Series<Byte> { ... } // from HttpRequestParser
