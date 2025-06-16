```kotlin
@file:Suppress("UNCHECKED_CAST", "FunctionName", "NonAsciiCharacters", "NOTHING_TO_INLINE")
package borg.trikeshed.main.impl

import borg.trikeshed.lib.*
import borg.trikeshed.net.quic.*
import borg.trikeshed.download.aria2c.*
import borg.trikeshed.core.http.*
import borg.trikeshed.parse.json.*
import borg.trikeshed.lib.CZero.nz
import borg.trikeshed.cursor.*
import borg.trikeshed.ipfs.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.*
import java.net.*
import java.net.http.*
import java.nio.*
import java.nio.channels.*
import java.nio.file.*
import java.util.concurrent.*
import java.util.zip.*
import javax.net.ssl.*
import kotlin.concurrent.*
import kotlin.math.*
import kotlin.time.*

// Actual HTTP/QUIC client implementation
object HttpClientImpl {
    private val httpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_2)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(30))
        .build()
    
    suspend fun execute(request: CurlDSL.CurlRequest): ByteArray = withContext(Dispatchers.IO) {
        TranscriptLogger.logDebug("HTTP ${request.method} ${request.url}")
        
        val builder = HttpRequest.newBuilder()
            .uri(URI(request.url))
            .timeout(java.time.Duration.ofMillis(request.timeout.inWholeMilliseconds))
        
        // Set method and body
        when (request.method) {
            HttpMethod.GET -> builder.GET()
            HttpMethod.POST -> builder.POST(HttpRequest.BodyPublishers.ofByteArray(request.data ?: ByteArray(0)))
            HttpMethod.PUT -> builder.PUT(HttpRequest.BodyPublishers.ofByteArray(request.data ?: ByteArray(0)))
            HttpMethod.DELETE -> builder.DELETE()
            HttpMethod.HEAD -> builder.method("HEAD", HttpRequest.BodyPublishers.noBody())
            HttpMethod.OPTIONS -> builder.method("OPTIONS", HttpRequest.BodyPublishers.noBody())
            HttpMethod.PATCH -> builder.method("PATCH", HttpRequest.BodyPublishers.ofByteArray(request.data ?: ByteArray(0)))
            else -> builder.GET()
        }
        
        // Add headers
        request.headers.forEach { (k, v) -> builder.header(k, v) }
        builder.header("User-Agent", request.userAgent)
        if (request.compressed) builder.header("Accept-Encoding", "gzip, deflate, br")
        
        val httpRequest = builder.build()
        
        if (request.verbose) {
            TranscriptLogger.logDebug("> ${request.method} ${request.url}")
            httpRequest.headers().map().forEach { (k, v) ->
                TranscriptLogger.logDebug("> $k: ${v.joinToString(", ")}")
            }
        }
        
        val response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray())
        
        if (request.verbose) {
            TranscriptLogger.logDebug("< HTTP/${response.version()} ${response.statusCode()}")
            response.headers().map().forEach { (k, v) ->
                TranscriptLogger.logDebug("< $k: ${v.joinToString(", ")}")
            }
        }
        
        val body = response.body()
        
        // Handle compression
        val contentEncoding = response.headers().firstValue("Content-Encoding").orElse("")
        val decompressed = when (contentEncoding) {
            "gzip" -> GZIPInputStream(body.inputStream()).readBytes()
            "deflate" -> InflaterInputStream(body.inputStream()).readBytes()
            else -> body
        }
        
        // Save to file if requested
        request.outputFile?.let { file ->
            File(file).writeBytes(decompressed)
            TranscriptLogger.logInfo("Saved ${decompressed.size} bytes to $file")
        }
        
        decompressed
    }
}

// QUIC implementation
object QuicImpl {
    private val factory = DefaultQuicConnectionFactory()
    private val config = QuicConfig()
    private val sessionCache = object : QuicSessionCache {
        override fun getSession(serverAddress: String, port: Int): QuicSessionData? = null
        override fun storeSession(serverAddress: String, port: Int, sessionData: QuicSessionData) {}
        override fun removeSession(serverAddress: String, port: Int) {}
    }

    suspend fun startServer(port: Int, handler: suspend (ByteArray) -> ByteArray) = coroutineScope {
        TranscriptLogger.logInfo("Starting QUIC server on port $port")
        
        val server = factory.createServer(config)
        server.start(port)
        
        launch {
            while (isActive) {
                val connection = server.accept()
                val stream = connection.accept()
                
                launch {
                    try {
                        val request = stream.receive().first().array()
                        val response = handler(request)
                        stream.send(ByteBuffer.wrap(response))
                    } catch (e: Exception) {
                        TranscriptLogger.logError("QUIC handler error", e)
                    } finally {
                        stream.close()
                        connection.close()
                    }
                }
            }
        }
    }
    
    suspend fun sendDatagram(host: String, port: Int, data: ByteArray): ByteArray {
        val connection = factory.createConnection(config, sessionCache)
        try {
            if (!connection.connect(host, port)) {
                throw Exception("Failed to connect to $host:$port")
            }
            
            val stream = connection.createStream()
            stream.send(ByteBuffer.wrap(data))
            
            return stream.receive().first().array()
        } finally {
            connection.close()
        }
    }
}

// Aria2c RPC implementation
object Aria2cImpl {
    private val downloads = ConcurrentHashMap<String, DownloadState>()
    private val executor = Executors.newFixedThreadPool(16)
    
    data class DownloadState(
        val gid: String,
        val uris: List<String>,
        val outputDir: String,
        val outputFile: String?,
        var status: String = "waiting",
        var totalLength: Long = 0,
        var completedLength: Long = 0,
        var downloadSpeed: Long = 0,
        val startTime: Long = System.currentTimeMillis()
    )
    
    suspend fun download(request: Aria2cDSL.DownloadRequest): String = withContext(Dispatchers.IO) {
        val gid = generateGid()
        
        request.uris.forEach { uri ->
            val state = DownloadState(
                gid = gid,
                uris = listOf(uri),
                outputDir = request.outputDir,
                outputFile = request.outputFile
            )
            downloads[gid] = state
            
            CompletableFuture.runAsync({
                try {
                    downloadFile(state, request)
                } catch (e: Exception) {
                    state.status = "error"
                    TranscriptLogger.logError("Download failed: $uri", e)
                }
            }, executor)
        }
        
        gid
    }
    
    private fun downloadFile(state: DownloadState, request: Aria2cDSL.DownloadRequest) {
        state.status = "active"
        val uri = state.uris.first()
        val url = URL(uri)
        
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", request.userAgent)
        request.referer?.let { connection.setRequestProperty("Referer", it) }
        request.headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
        
        if (request.continue) {
            val outputFile = File(state.outputDir, state.outputFile ?: url.file.substringAfterLast('/'))
            if (outputFile.exists()) {
                connection.setRequestProperty("Range", "bytes=${outputFile.length()}-")
            }
        }
        
        connection.connect()
        state.totalLength = connection.contentLengthLong
        
        val outputFile = File(state.outputDir, state.outputFile ?: url.file.substringAfterLast('/'))
        outputFile.parentFile?.mkdirs()
        
        connection.inputStream.use { input ->
            FileOutputStream(outputFile, request.continue && outputFile.exists()).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var lastSpeedUpdate = System.currentTimeMillis()
                var bytesInInterval = 0L
                
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    state.completedLength += bytesRead
                    bytesInInterval += bytesRead
                    
                    val now = System.currentTimeMillis()
                    if (now - lastSpeedUpdate >= 1000) {
                        state.downloadSpeed = bytesInInterval * 1000 / (now - lastSpeedUpdate)
                        bytesInInterval = 0
                        lastSpeedUpdate = now
                    }
                }
            }
        }
        
        state.status = "complete"
        TranscriptLogger.logInfo("Download complete: ${outputFile.absolutePath}")
    }
    
    private fun generateGid(): String = java.util.UUID.randomUUID().toString().replace("-", "").take(16)
    
    fun getStatus(gid: String): DownloadState? = downloads[gid]
    
    fun getAllDownloads(): List<DownloadState> = downloads.values.toList()
}

// JSON processing implementation
object JsonImpl {
    fun parse(input: String): Any? {
        val chars = input.toSeries()
        return try {
            val parsed = jsPath(chars, emptyList())
            reifyJson(parsed)
        } catch (e: Exception) {
            TranscriptLogger.logError("JSON parse error", e)
            null
        }
    }
    
    fun stringify(obj: Any?, pretty: Boolean = false): String {
        return when (obj) {
            null -> "null"
            is Boolean -> obj.toString()
            is Number -> obj.toString()
            is String -> "\"${obj.replace("\"", "\\\"")}\""
            is List<*> -> {
                val items = obj.map { stringify(it, pretty) }
                if (pretty) "[\n  ${items.joinToString(",\n  ")}\n]"
                else "[${items.joinToString(",")}]"
            }
            is Map<*, *> -> {
                val pairs = obj.entries.map { (k, v) ->
                    "\"$k\": ${stringify(v, pretty)}"
                }
                if (pretty) "{\n  ${pairs.joinToString(",\n  ")}\n}"
                else "{${pairs.joinToString(",")}}"
            }
            else -> stringify(obj.toString(), pretty)
        }
    }
    
    private fun reifyJson(node: Any?): Any? = when (node) {
        is Series<*> -> node.`▶`.map { reifyJson(it) }
        is Join<*, *> -> mapOf(node.a.toString() to reifyJson(node.b))
        else -> node
    }
}

// CSV processing implementation
object CsvImpl {
    fun parse(input: String, delimiter: Char = ',', hasHeader: Boolean = true): Cursor {
        val lines = input.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyCursor()
        
        val headers = if (hasHeader) {
            parseLine(lines.first(), delimiter)
        } else {
            (0 until parseLine(lines.first(), delimiter).size).map { "column$it" }
        }
        
        val dataStartIdx = if (hasHeader) 1 else 0
        val dataLines = lines.drop(dataStartIdx)
        
        val rows = dataLines.size
        val cols = headers.size
        
        val data = Array(rows) { r ->
            val values = parseLine(dataLines[r], delimiter)
            Array(cols) { c ->
                values.getOrElse(c) { "" }
            }
        }
        
        return rows j { r ->
            cols j { c ->
                data[r][c] j { ColumnMeta(headers[c], IOMemento.IoString) }
            }
        }
    }
    
    private fun parseLine(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var i = 0
        
        while (i < line.length) {
            when {
                line[i] == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                line[i] == delimiter && !inQuotes -> {
                    result.add(current.toString())
                    current = StringBuilder()
                }
                else -> current.append(line[i])
            }
            i++
        }
        result.add(current.toString())
        
        return result
    }
    
    fun write(cursor: Cursor, delimiter: Char = ',', writeHeader: Boolean = true): String {
        val output = StringBuilder()
        
        if (writeHeader) {
            val headers = cursor.meta.names.`▶`.joinToString(delimiter.toString()) { escapeField(it, delimiter) }
            output.appendLine(headers)
        }
        
        for (r in 0 until cursor.size) {
            val row = cursor.row(r)
            val values = (0 until row.size).map { c ->
                val value = row[c].a?.toString() ?: ""
                escapeField(value, delimiter)
            }
            output.appendLine(values.joinToString(delimiter.toString()))
        }
        
        return output.toString()
    }
    
    private fun escapeField(field: String, delimiter: Char): String {
        return if (field.contains(delimiter) || field.contains('"') || field.contains('\n')) {
            "\"${field.replace("\"", "\"\"")}\""
        } else field
    }
    
    private fun emptyCursor(): Cursor = 0 j { 0 j { throw IndexOutOfBoundsException() } }
}

// Series operations implementation
object SeriesImpl {
    fun <T> generate(size: Int, generator: (Int) -> T): Series<T> = size j generator
    
    fun <T> concat(vararg series: Series<T>): Series<T> {
        val totalSize = series.sumOf { it.size }
        var offset = 0
        val offsets = series.map { s ->
            val start = offset
            offset += s.size
            start to s
        }
        
        return totalSize j { i ->
            val (start, s) = offsets.last { (start, _) -> i >= start }
            s[i - start]
        }
    }
    
    fun <T> zip(s1: Series<T>, s2: Series<T>): Series<Join<T, T>> {
        val size = min(s1.size, s2.size)
        return size j { i -> s1[i] j s2[i] }
    }
    
    fun <T : Comparable<T>> sort(series: Series<T>): Series<T> {
        val sorted = series.`▶`.sorted()
        return series.size j { sorted[it] }
    }
    
    fun <T, K : Comparable<K>> sortBy(series: Series<T>, selector: (T) -> K): Series<T> {
        val sorted = series.`▶`.sortedBy(selector)
        return series.size j { sorted[it] }
    }
    
    fun <T> distinct(series: Series<T>): Series<T> {
        val distinct = series.`▶`.distinct()
        return distinct.size j { distinct[it] }
    }
    
    fun <T, K> groupBy(series: Series<T>, keySelector: (T) -> K): Map<K, Series<T>> {
        val groups = series.`▶`.groupBy(keySelector)
        return groups.mapValues { (_, values) ->
            values.size j { values[it] }
        }
    }
}

// Financial calculations implementation
object FinanceImpl {
    fun calculateReturns(prices: PriceSeries): ReturnSeries = IpfsFinance.netReturn(prices)
    
    fun calculateVolatility(returns: ReturnSeries, annualize: Boolean = true): Double {
        val mean = returns.`▶`.map { it.value }.average()
        val variance = returns.`▶`.map { (it.value - mean).pow(2) }.average()
        val vol = sqrt(variance)
        return if (annualize) vol * sqrt(252.0) else vol
    }
    
    fun calculateSharpe(returns: ReturnSeries, riskFreeRate: Double = 0.02): Double = 
        IpfsFinance.sharpeRatio(returns, riskFreeRate / 252)
    
    fun calculateMaxDrawdown(prices: PriceSeries): Double = IpfsFinance.maxDrawdown(prices)
    
    fun movingAverage(prices: PriceSeries, window: Int): PriceSeries = 
        IpfsFinance.simpleMovingAverage(prices, Period(window))
    
    fun exponentialMovingAverage(prices: PriceSeries, alpha: Double): PriceSeries {
        var ema = prices[0].value
        return prices.size j { i ->
            if (i == 0) prices[0]
            else {
                ema = alpha * prices[i].value + (1 - alpha) * ema
                Price(ema)
            }
        }
    }
    
    fun bollingerBands(prices: PriceSeries, window: Int = 20, stdDev: Double = 2.0): Triple<PriceSeries, PriceSeries, PriceSeries> {
        val ma = movingAverage(prices, window)
        val stdSeries = (prices.size - window + 1) j { i ->
            val windowPrices = window j { j -> prices[i + j].value }
            val mean = windowPrices.`▶`.average()
            val variance = windowPrices.`▶`.map { (it - mean).pow(2) }.average()
            sqrt(variance)
        }
        
        val upper = ma.size j { i -> Price(ma[i].value + stdDev * stdSeries[i]) }
        val lower = ma.size j { i -> Price(ma[i].value - stdDev * stdSeries[i]) }
        
        return Triple(upper, ma, lower)
    }
}

// HTTP server implementation
object HttpServerImpl {
    suspend fun startServer(port: Int, handler: suspend (HttpRequest) -> HttpResponse) = coroutineScope {
        val server = com.sun.net.httpserver.HttpServer.create(InetSocketAddress(port), 0)
        
        server.createContext("/") { exchange ->
            runBlocking {
                try {
                    val request = HttpRequest(
                        method = exchange.requestMethod,
                        path = exchange.requestURI.path,
                        headers = exchange.requestHeaders.entries.associate { it.key to it.value.joinToString(", ") },
                        body = exchange.requestBody.readBytes()
                    )
                    
                    val response = handler(request)
                    
                    response.headers.forEach { (k, v) ->
                        exchange.responseHeaders.add(k, v)
                    }
                    
                    exchange.sendResponseHeaders(response.statusCode, response.body.size.toLong())
                    exchange.responseBody.write(response.body)
                    exchange.close()
                } catch (e: Exception) {
                    TranscriptLogger.logError("HTTP handler error", e)
                    exchange.sendResponseHeaders(500, 0)
                    exchange.close()
                }
            }
        }
        
        server.executor = Executors.newFixedThreadPool(10)
        server.start()
        
        TranscriptLogger.logInfo("HTTP server started on port $port")
    }
    
    data class HttpRequest(
        val method: String,
        val path: String,
        val headers: Map<String, String>,
        val body: ByteArray
    )
    
    data class HttpResponse(
        val statusCode: Int,
        val headers: Map<String, String> = emptyMap(),
        val body: ByteArray = ByteArray(0)
    )
}

// Cursor operations implementation
object CursorImpl {
    fun filter(cursor: Cursor, predicate: (RowVec) -> Boolean): Cursor {
        val filtered = (0 until cursor.size).filter { predicate(cursor.row(it)) }
        return filtered.size j { r ->
            cursor.row(filtered[r])
        }
    }
    
    fun select(cursor: Cursor, columns: Series<String>): Cursor {
        val indices = columns.`▶`.map { col ->
            cursor.meta.names.`▶`.indexOf(col)
        }.filter { it >= 0 }
        
        return cursor.size j { r ->
            indices.size j { c ->
                cursor.row(r)[indices[c]]
            }
        }
    }
    
    fun join(left: Cursor, right: Cursor, leftKey: String, rightKey: String): Cursor {
        val leftKeyIdx = left.meta.names.`▶`.indexOf(leftKey)
        val rightKeyIdx = right.meta.names.`▶`.indexOf(rightKey)
        
        if (leftKeyIdx < 0 || rightKeyIdx < 0) return emptyCursor()
        
        val rightIndex = (0 until right.size).groupBy { r ->
            right.row(r)[rightKeyIdx].a?.toString() ?: ""
        }
        
        val joined = mutableListOf<RowVec>()
        
        for (lr in 0 until left.size) {
            val leftRow = left.row(lr)
            val key = leftRow[leftKeyIdx].a?.toString() ?: ""
            val rightRows = rightIndex[key] ?: emptyList()
            
            for (rr in rightRows) {
                val rightRow = right.row(rr)
                val combinedRow = (leftRow.size + rightRow.size - 1) j { c ->
                    if (c < leftRow.size) leftRow[c]
                    else rightRow[c - leftRow.size + 1]
                }
                joined.add(combinedRow)
            }
        }
        
        return joined.size j { joined[it] }
    }
    
    fun aggregate(cursor: Cursor, groupBy: Series<String>, agg: Map<String, (Series<Any?>) -> Any?>): Cursor {
        val groupIndices = groupBy.`▶`.map { col ->
            cursor.meta.names.`▶`.indexOf(col)
        }
        
        val groups = (0 until cursor.size).groupBy { r ->
            groupIndices.map { idx ->
                cursor.row(r)[idx].a?.toString() ?: ""
            }.joinToString("|")
        }
        
        val results = groups.map { (key, rows) ->
            val groupValues = key.split("|")
            val aggResults = agg.map { (col, fn) ->
                val colIdx = cursor.meta.names.`▶`.indexOf(col)
                val values = rows.size j { cursor.row(rows[it])[colIdx].a }
                fn(values)
            }
            
            (groupValues + aggResults).size j { i ->
                val value = (groupValues + aggResults)[i]
                val meta = if (i < groupBy.size) {
                    ColumnMeta(groupBy[i], IOMemento.IoString)
                } else {
                    ColumnMeta(agg.keys.elementAt(i - groupBy.size), IOMemento.IoDouble)
                }
                value j { meta }
            }
        }
        
        return results.size j { results[it] }
    }
    
    private fun emptyCursor(): Cursor = 0 j { 0 j { throw IndexOutOfBoundsException() } }
}

// WebSocket implementation for real-time features
object WebSocketImpl {
    private val sessions = ConcurrentHashMap<String, WebSocketSession>()
    
    data class WebSocketSession(
        val id: String,
        val channel: AsynchronousSocketChannel,
        val handler: suspend (String) -> String
    )
    
    suspend fun startWebSocketServer(port: Int, handler: suspend (String) -> String) = coroutineScope {
        val server = AsynchronousServerSocketChannel.open()
        server.bind(InetSocketAddress(port))
        
        TranscriptLogger.logInfo("WebSocket server started on port $port")
        
        while (isActive) {
            val client = server.acceptSuspending()
            launch { handleWebSocketClient(client, handler) }
        }
    }
    
    private suspend fun AsynchronousServerSocketChannel.acceptSuspending(): AsynchronousSocketChannel =
        suspendCancellableCoroutine { cont ->
            accept(null, object : CompletionHandler<AsynchronousSocketChannel, Void?> {
                override fun completed(result: AsynchronousSocketChannel, attachment: Void?) {
                    cont.resume(result)
                }
                
                override fun failed(exc: Throwable, attachment: Void?) {
                    cont.resumeWithException(exc)
                }
            })
        }
    
    private suspend fun handleWebSocketClient(channel: AsynchronousSocketChannel, handler: suspend (String) -> String) {
        val sessionId = java.util.UUID.randomUUID().toString()
        val session = WebSocketSession(sessionId, channel, handler)
        sessions[sessionId] = session
        
        try {
            // Simplified WebSocket handshake
            val buffer = ByteBuffer.allocate(1024)
            channel.read(buffer).get()
            buffer.flip()
            
            val request = String(buffer.array(), 0, buffer.limit())
            if (request.contains("Upgrade: websocket")) {
                val response = """HTTP/1.1 101 Switching Protocols
                    |Upgrade: websocket
                    |Connection: Upgrade
                    |Sec-WebSocket-Accept: ${generateWebSocketAccept(request)}
                    |
                    |""".trimMargin()
                
                channel.write(ByteBuffer.wrap(response.toByteArray())).get()
                
                // Handle WebSocket frames
                while (channel.isOpen) {
                    buffer.clear()
                    val bytesRead = channel.read(buffer).get()
                    if (bytesRead < 0) break
                    
                    buffer.flip()
                    val frame = decodeWebSocketFrame(buffer)
                    frame?.let {
                        val response = handler(it)
                        val responseFrame = encodeWebSocketFrame(response)
                        channel.write(ByteBuffer.wrap(responseFrame)).get()
                    }
                }
            }
        } catch (e: Exception) {
            TranscriptLogger.logError("WebSocket error", e)
        } finally {
            sessions.remove(sessionId)
            channel.close()
        }
    }
    
    private fun generateWebSocketAccept(request: String): String {
        // Simplified - real implementation would parse Sec-WebSocket-Key and compute SHA-1
        return "dGhlIHNhbXBsZSBub25jZQ=="
    }
    
    private fun decodeWebSocketFrame(buffer: ByteBuffer): String? {
        // Simplified WebSocket frame decoding
        if (buffer.remaining() < 2) return null
        
        val b0 = buffer.get()
        val b1 = buffer.get()
        
        val fin = (b0.toInt() and 0x80) != 0
        val opcode = b0.toInt() and 0x0F
        val masked = (b1.toInt() and 0x80) != 0
        var payloadLength = b1.toInt() and 0x7F
        
        if (payloadLength == 126) {
            payloadLength = buffer.short.toInt()
        } else if (payloadLength == 127) {
            payloadLength = buffer.long.toInt()
        }
        
        val maskKey = if (masked) ByteArray(4).also { buffer.get(it) } else null
        val payload = ByteArray(payloadLength)
        buffer.get(payload)
        
        if (masked && maskKey != null) {
            for (i in payload.indices) {
                payload[i] = (payload[i].toInt() xor maskKey[i % 4].toInt()).toByte()
            }
        }
        
        return if (opcode == 1) String(payload) else null // Text frame
    }
    
    private fun encodeWebSocketFrame(text: String): ByteArray {
        val payload = text.toByteArray()
        val frame = ByteArrayOutputStream()
        
        frame.write(0x81) // FIN + text frame
        
        when {
            payload.size < 126 -> frame.write(payload.size)
            payload.size < 65536 -> {
                frame.write(126)
                frame.write(payload.size shr 8)
                frame.write(payload.size and 0xFF)
            }
            else -> {
                frame.write(127)
                for (i in 7 downTo 0) {
                    frame.write((payload.size shr (i * 8)) and 0xFF)
                }
            }
        }
        
        frame.write(payload)
        return frame.toByteArray()
    }
    
    suspend fun broadcast(message: String) {
        sessions.values.forEach { session ->
            try {
                val frame = encodeWebSocketFrame(message)
                session.channel.write(ByteBuffer.wrap(frame)).get()
            } catch (e: Exception) {
                TranscriptLogger.logError("Broadcast error to ${session.id}", e)
            }
        }
    }
}

// Updated command implementations using the actual implementations
fun handleCurl(args: ArgvSeries, env: EnvSeries): CommandResult = runBlocking {
    try {
        val request = parseCurlArgs(args)
        val result = HttpClientImpl.execute(request)
        
        if (request.outputFile == null) {
            println(String(result))
        }
        
        ExitCode(0) j TranscriptLogger.getTranscript()
    } catch (e: Exception) {
        TranscriptLogger.logError("Request failed", e)
        ExitCode(1) j TranscriptLogger.getTranscript()
    }
}

fun handleAria2c(args: ArgvSeries, env: EnvSeries): CommandResult = runBlocking {
    try {
        val request = parseAria2cArgs(args)if (request.daemon) {
           // Start RPC server mode
           launch {
               HttpServerImpl.startServer(request.rpcPort) { httpReq ->
                   val rpcRequest = JsonImpl.parse(String(httpReq.body)) as? Map<*, *>
                   val method = rpcRequest?.get("method") as? String
                   val params = rpcRequest?.get("params") as? List<*>
                   val id = rpcRequest?.get("id")
                   
                   val result = when (method) {
                       "aria2.addUri" -> {
                           val uris = (params?.get(0) as? List<*>)?.map { it.toString() } ?: emptyList()
                           val gid = Aria2cImpl.download(request.copy(uris = uris.toMutableList()))
                           mapOf("result" to gid)
                       }
                       "aria2.tellStatus" -> {
                           val gid = params?.get(0) as? String
                           val status = gid?.let { Aria2cImpl.getStatus(it) }
                           mapOf("result" to status?.let {
                               mapOf(
                                   "gid" to it.gid,
                                   "status" to it.status,
                                   "totalLength" to it.totalLength.toString(),
                                   "completedLength" to it.completedLength.toString(),
                                   "downloadSpeed" to it.downloadSpeed.toString()
                               )
                           })
                       }
                       "aria2.tellActive" -> {
                           val active = Aria2cImpl.getAllDownloads().filter { it.status == "active" }
                           mapOf("result" to active.map { 
                               mapOf(
                                   "gid" to it.gid,
                                   "status" to it.status,
                                   "totalLength" to it.totalLength.toString(),
                                   "completedLength" to it.completedLength.toString(),
                                   "downloadSpeed" to it.downloadSpeed.toString()
                               )
                           })
                       }
                       else -> mapOf("error" to mapOf("code" to -32601, "message" to "Method not found"))
                   }
                   
                   val response = mapOf("jsonrpc" to "2.0", "id" to id) + result
                   HttpServerImpl.HttpResponse(
                       statusCode = 200,
                       headers = mapOf("Content-Type" to "application/json"),
                       body = JsonImpl.stringify(response).toByteArray()
                   )
               }
           }
           
           TranscriptLogger.logInfo("Aria2c RPC server started on port ${request.rpcPort}")
           delay(Duration.INFINITE) // Keep server running
       } else {
           // Direct download mode
           request.uris.forEach { uri ->
               val gid = Aria2cImpl.download(request)
               TranscriptLogger.logInfo("Download started: $gid for $uri")
               
               // Wait for completion if not daemon
               while (true) {
                   val status = Aria2cImpl.getStatus(gid)
                   if (status?.status in listOf("complete", "error")) break
                   
                   status?.let {
                       val progress = if (it.totalLength > 0) {
                           (it.completedLength * 100 / it.totalLength)
                       } else 0
                       val speed = it.downloadSpeed / 1024 // KB/s
                       print("\r[$gid] ${it.status} - $progress% @ ${speed}KB/s")
                   }
                   delay(1000)
               }
               println() // New line after progress
           }
       }
       
       ExitCode(0) j TranscriptLogger.getTranscript()
   } catch (e: Exception) {
       TranscriptLogger.logError("Download failed", e)
       ExitCode(1) j TranscriptLogger.getTranscript()
   }
}

fun handleQuic(args: ArgvSeries, env: EnvSeries): CommandResult = runBlocking {
   val mode = args.getOrNull(0)?.value ?: "help"
   
   when (mode) {
       "server" -> {
           val port = args.getOrNull(1)?.value?.toIntOrNull() ?: 4433
           QuicImpl.startServer(port) { request ->
               TranscriptLogger.logDebug("QUIC request: ${request.size} bytes")
               "QUIC response from TrikeShed".toByteArray()
           }
           TranscriptLogger.logInfo("QUIC server running on port $port")
           delay(Duration.INFINITE)
       }
       "client" -> {
           val host = args.getOrNull(1)?.value ?: "localhost"
           val port = args.getOrNull(2)?.value?.toIntOrNull() ?: 4433
           val message = args.drop(3).`▶`.joinToString(" ") { it.value }
           
           val response = QuicImpl.sendDatagram(host, port, message.toByteArray())
           println(String(response))
       }
       else -> {
           println("""
               QUIC usage:
                 tsquic server [port]           Start QUIC server
                 tsquic client host port msg    Send QUIC datagram
           """.trimIndent())
       }
   }
   
   ExitCode(0) j TranscriptLogger.getTranscript()
}

fun handleHttp(args: ArgvSeries, env: EnvSeries): CommandResult = runBlocking {
   val mode = args.getOrNull(0)?.value ?: "help"
   
   when (mode) {
       "server" -> {
           val port = args.getOrNull(1)?.value?.toIntOrNull() ?: 8080
           val root = args.getOrNull(2)?.value ?: "."
           
           HttpServerImpl.startServer(port) { request ->
               TranscriptLogger.logDebug("${request.method} ${request.path}")
               
               when {
                   request.path == "/health" -> {
                       HttpServerImpl.HttpResponse(
                           200,
                           mapOf("Content-Type" to "text/plain"),
                           "OK".toByteArray()
                       )
                   }
                   request.path.startsWith("/api/") -> {
                       val json = mapOf(
                           "method" to request.method,
                           "path" to request.path,
                           "timestamp" to System.currentTimeMillis()
                       )
                       HttpServerImpl.HttpResponse(
                           200,
                           mapOf("Content-Type" to "application/json"),
                           JsonImpl.stringify(json).toByteArray()
                       )
                   }
                   else -> {
                       val file = File(root, request.path.removePrefix("/"))
                       if (file.exists() && file.isFile) {
                           val mimeType = when (file.extension.lowercase()) {
                               "html", "htm" -> "text/html"
                               "css" -> "text/css"
                               "js" -> "application/javascript"
                               "json" -> "application/json"
                               "png" -> "image/png"
                               "jpg", "jpeg" -> "image/jpeg"
                               "gif" -> "image/gif"
                               else -> "application/octet-stream"
                           }
                           HttpServerImpl.HttpResponse(
                               200,
                               mapOf("Content-Type" to mimeType),
                               file.readBytes()
                           )
                       } else {
                           HttpServerImpl.HttpResponse(
                               404,
                               mapOf("Content-Type" to "text/plain"),
                               "Not Found".toByteArray()
                           )
                       }
                   }
               }
           }
           
           TranscriptLogger.logInfo("HTTP server running on port $port, serving $root")
           delay(Duration.INFINITE)
       }
       else -> {
           println("""
               HTTP usage:
                 tshttp server [port] [root]   Start HTTP server
           """.trimIndent())
       }
   }
   
   ExitCode(0) j TranscriptLogger.getTranscript()
}

fun handleJson(args: ArgvSeries, env: EnvSeries): CommandResult {
   val inputFile = args.getOrNull(0)?.value
   val pretty = args.`▶`.any { it.value == "--pretty" }
   val query = args.`▶`.find { it.value.startsWith("--query=") }?.value?.removePrefix("--query=")
   
   try {
       val input = inputFile?.let { File(it).readText() } ?: generateSequence(::readlnOrNull).joinToString("\n")
       val parsed = JsonImpl.parse(input)
       
       val result = query?.let { q ->
           // Simple JSON path query support
           q.split('.').fold(parsed) { obj, key ->
               when (obj) {
                   is Map<*, *> -> obj[key]
                   is List<*> -> key.toIntOrNull()?.let { obj.getOrNull(it) }
                   else -> null
               }
           }
       } ?: parsed
       
       println(JsonImpl.stringify(result, pretty))
       
       ExitCode(0) j TranscriptLogger.getTranscript()
   } catch (e: Exception) {
       TranscriptLogger.logError("JSON processing failed", e)
       ExitCode(1) j TranscriptLogger.getTranscript()
   }
}

fun handleCsv(args: ArgvSeries, env: EnvSeries): CommandResult {
   val inputFile = args.getOrNull(0)?.value
   val outputFile = args.`▶`.find { it.value.startsWith("--output=") }?.value?.removePrefix("--output=")
   val delimiter = args.`▶`.find { it.value.startsWith("--delimiter=") }?.value?.removePrefix("--delimiter=")?.firstOrNull() ?: ','
   val filter = args.`▶`.find { it.value.startsWith("--filter=") }?.value?.removePrefix("--filter=")
   val select = args.`▶`.find { it.value.startsWith("--select=") }?.value?.removePrefix("--select=")?.split(',')
   val hasHeader = !args.`▶`.any { it.value == "--no-header" }
   
   try {
       val input = inputFile?.let { File(it).readText() } ?: generateSequence(::readlnOrNull).joinToString("\n")
       var cursor = CsvImpl.parse(input, delimiter, hasHeader)
       
       // Apply filter if specified
       filter?.let { filterExpr ->
           // Simple filter: "column=value" or "column>value"
           val match = Regex("(\\w+)([=<>]+)(.+)").find(filterExpr)
           match?.let { m ->
               val (col, op, value) = m.destructured
               val colIdx = cursor.meta.names.`▶`.indexOf(col)
               if (colIdx >= 0) {
                   cursor = CursorImpl.filter(cursor) { row ->
                       val cellValue = row[colIdx].a?.toString() ?: ""
                       when (op) {
                           "=" -> cellValue == value
                           ">" -> cellValue.toDoubleOrNull()?.let { it > value.toDouble() } ?: false
                           "<" -> cellValue.toDoubleOrNull()?.let { it < value.toDouble() } ?: false
                           ">=" -> cellValue.toDoubleOrNull()?.let { it >= value.toDouble() } ?: false
                           "<=" -> cellValue.toDoubleOrNull()?.let { it <= value.toDouble() } ?: false
                           else -> false
                       }
                   }
               }
           }
       }
       
       // Apply column selection if specified
       select?.let { cols ->
           cursor = CursorImpl.select(cursor, cols.size j { cols[it] })
       }
       
       // Output result
       val output = CsvImpl.write(cursor, delimiter, hasHeader)
       outputFile?.let { File(it).writeText(output) } ?: print(output)
       
       ExitCode(0) j TranscriptLogger.getTranscript()
   } catch (e: Exception) {
       TranscriptLogger.logError("CSV processing failed", e)
       ExitCode(1) j TranscriptLogger.getTranscript()
   }
}

fun handleSeries(args: ArgvSeries, env: EnvSeries): CommandResult {
   val mode = args.getOrNull(0)?.value ?: "help"
   
   when (mode) {
       "generate" -> {
           val size = args.getOrNull(1)?.value?.toIntOrNull() ?: 10
           val type = args.getOrNull(2)?.value ?: "int"
           
           val series = when (type) {
               "int" -> SeriesImpl.generate(size) { it }
               "float" -> SeriesImpl.generate(size) { it.toFloat() }
               "double" -> SeriesImpl.generate(size) { it.toDouble() }
               "string" -> SeriesImpl.generate(size) { "item$it" }
               "random" -> SeriesImpl.generate(size) { kotlin.random.Random.nextDouble() }
               else -> SeriesImpl.generate(size) { it }
           }
           
           series.`▶`.forEach { println(it) }
       }
       "concat" -> {
           // Read series from stdin
           val lines = generateSequence(::readlnOrNull).toList()
           val series1 = lines.take(lines.size / 2).size j { lines[it] }
           val series2 = lines.drop(lines.size / 2).size j { lines[lines.size / 2 + it] }
           
           val concatenated = SeriesImpl.concat(series1, series2)
           concatenated.`▶`.forEach { println(it) }
       }
       "sort" -> {
           val lines = generateSequence(::readlnOrNull).toList()
           val series = lines.size j { lines[it] }
           val sorted = SeriesImpl.sort(series)
           sorted.`▶`.forEach { println(it) }
       }
       else -> {
           println("""
               Series usage:
                 tsseries generate [size] [type]  Generate series
                 tsseries concat                  Concatenate series from stdin
                 tsseries sort                    Sort series from stdin
                 
               Types: int, float, double, string, random
           """.trimIndent())
       }
   }
   
   ExitCode(0) j TranscriptLogger.getTranscript()
}

fun handleFinance(args: ArgvSeries, env: EnvSeries): CommandResult {
   val mode = args.getOrNull(0)?.value ?: "help"
   
   when (mode) {
       "returns" -> {
           val prices = generateSequence(::readlnOrNull)
               .mapNotNull { it.toDoubleOrNull() }
               .toList()
           
           if (prices.size < 2) {
               println("Need at least 2 prices")
               return ExitCode(1) j TranscriptLogger.getTranscript()
           }
           
           val priceSeries = prices.size j { Price(prices[it]) }
           val returns = FinanceImpl.calculateReturns(priceSeries)
           
           returns.`▶`.forEach { println("%.4f".format(it.value)) }
       }
       "volatility" -> {
           val returns = generateSequence(::readlnOrNull)
               .mapNotNull { it.toDoubleOrNull() }
               .toList()
           
           val returnSeries = returns.size j { Return(returns[it]) }
           val vol = FinanceImpl.calculateVolatility(returnSeries)
           
           println("Annualized Volatility: %.2f%%".format(vol * 100))
       }
       "sharpe" -> {
           val returns = generateSequence(::readlnOrNull)
               .mapNotNull { it.toDoubleOrNull() }
               .toList()
           
           val returnSeries = returns.size j { Return(returns[it]) }
           val sharpe = FinanceImpl.calculateSharpe(returnSeries)
           
           println("Sharpe Ratio: %.4f".format(sharpe))
       }
       "ma" -> {
           val window = args.getOrNull(1)?.value?.toIntOrNull() ?: 20
           val prices = generateSequence(::readlnOrNull)
               .mapNotNull { it.toDoubleOrNull() }
               .toList()
           
           val priceSeries = prices.size j { Price(prices[it]) }
           val ma = FinanceImpl.movingAverage(priceSeries, window)
           
           ma.`▶`.forEach { println("%.2f".format(it.value)) }
       }
       "bollinger" -> {
           val window = args.getOrNull(1)?.value?.toIntOrNull() ?: 20
           val stdDev = args.getOrNull(2)?.value?.toDoubleOrNull() ?: 2.0
           val prices = generateSequence(::readlnOrNull)
               .mapNotNull { it.toDoubleOrNull() }
               .toList()
           
           val priceSeries = prices.size j { Price(prices[it]) }
           val (upper, middle, lower) = FinanceImpl.bollingerBands(priceSeries, window, stdDev)
           
           for (i in 0 until upper.size) {
               println("%.2f,%.2f,%.2f".format(upper[i].value, middle[i].value, lower[i].value))
           }
       }
       else -> {
           println("""
               Finance usage:
                 tsfinance returns              Calculate returns from prices (stdin)
                 tsfinance volatility           Calculate volatility from returns (stdin)
                 tsfinance sharpe               Calculate Sharpe ratio from returns (stdin)
                 tsfinance ma [window]          Calculate moving average (stdin)
                 tsfinance bollinger [w] [std]  Calculate Bollinger bands (stdin)
           """.trimIndent())
       }
   }
   
   ExitCode(0) j TranscriptLogger.getTranscript()
}

fun handleCursor(args: ArgvSeries, env: EnvSeries): CommandResult {
   val inputFile = args.getOrNull(0)?.value
   val operation = args.getOrNull(1)?.value ?: "show"
   
   try {
       val input = inputFile?.let { File(it).readText() } ?: generateSequence(::readlnOrNull).joinToString("\n")
       val cursor = CsvImpl.parse(input) // Use CSV as default format
       
       when (operation) {
           "show" -> {
               cursor.show()
           }
           "head" -> {
               val n = args.getOrNull(2)?.value?.toIntOrNull() ?: 5
               cursor.head(n)
           }
           "filter" -> {
               val filterExpr = args.getOrNull(2)?.value ?: return ExitCode(1) j Transcript("Missing filter expression")
               // Simple filter implementation
               val filtered = CursorImpl.filter(cursor) { row ->
                   // Parse simple condition like "column=value"
                   val parts = filterExpr.split("=")
                   if (parts.size == 2) {
                       val colName = parts[0]
                       val value = parts[1]
                       val colIdx = cursor.meta.names.`▶`.indexOf(colName)
                       colIdx >= 0 && row[colIdx].a?.toString() == value
                   } else true
               }
               filtered.show()
           }
           "select" -> {
               val columns = args.drop(2).`▶`.map { it.value }
               val selected = CursorImpl.select(cursor, columns.size j { columns[it] })
               selected.show()
           }
           "join" -> {
               val rightFile = args.getOrNull(2)?.value ?: return ExitCode(1) j Transcript("Missing right file")
               val leftKey = args.getOrNull(3)?.value ?: return ExitCode(1) j Transcript("Missing left key")
               val rightKey = args.getOrNull(4)?.value ?: leftKey
               
               val rightCursor = CsvImpl.parse(File(rightFile).readText())
               val joined = CursorImpl.join(cursor, rightCursor, leftKey, rightKey)
               joined.show()
           }
           "aggregate" -> {
               val groupBy = args.getOrNull(2)?.value?.split(",") ?: emptyList()
               val aggExpr = args.getOrNull(3)?.value ?: "count"
               
               val agg = when {
                   aggExpr.startsWith("sum(") -> {
                       val col = aggExpr.removePrefix("sum(").removeSuffix(")")
                       mapOf(col to { values: Series<Any?> ->
                           values.`▶`.mapNotNull { it?.toString()?.toDoubleOrNull() }.sum()
                       })
                   }
                   aggExpr.startsWith("avg(") -> {
                       val col = aggExpr.removePrefix("avg(").removeSuffix(")")
                       mapOf(col to { values: Series<Any?> ->
                           values.`▶`.mapNotNull { it?.toString()?.toDoubleOrNull() }.average()
                       })
                   }
                   aggExpr == "count" -> {
                       mapOf("count" to { values: Series<Any?> -> values.size })
                   }
                   else -> mapOf("count" to { values: Series<Any?> -> values.size })
               }
               
               val aggregated = CursorImpl.aggregate(
                   cursor,
                   groupBy.size j { groupBy[it] },
                   agg
               )
               aggregated.show()
           }
           else -> {
               println("""
                   Cursor operations:
                     show                          Display cursor
                     head [n]                      Show first n rows
                     filter expr                   Filter rows (e.g., column=value)
                     select col1,col2,...         Select columns
                     join file leftKey [rightKey]  Join with another file
                     aggregate groupBy aggExpr     Aggregate (e.g., sum(col), avg(col), count)
               """.trimIndent())
           }
       }
       
       ExitCode(0) j TranscriptLogger.getTranscript()
   } catch (e: Exception) {
       TranscriptLogger.logError("Cursor operation failed", e)
       ExitCode(1) j TranscriptLogger.getTranscript()
   }
}

// Helper functions for parsing command line arguments
private fun parseCurlArgs(args: ArgvSeries): CurlDSL.CurlRequest {
   var url = ""
   var method = HttpMethod.GET
   val headers = mutableMapOf<String, String>()
   var data: ByteArray? = null
   var output: String? = null
   var verbose = false
   var compressed = false
   var followRedirects = true
   var maxRedirects = 50
   var timeout = 30.seconds
   var userAgent = "trikeshed-curl/1.0"
   var http2 = true
   var http3 = false
   
   var i = 0
   while (i < args.size) {
       when (val arg = args[i].value) {
           "-X", "--request" -> {
               i++
               method = HttpMethod.valueOf(args[i].value.uppercase())
           }
           "-H", "--header" -> {
               i++
               val header = args[i].value.split(":", limit = 2)
               if (header.size == 2) {
                   headers[header[0].trim()] = header[1].trim()
               }
           }
           "-d", "--data", "--data-raw" -> {
               i++
               data = args[i].value.toByteArray()
               if (method == HttpMethod.GET) method = HttpMethod.POST
           }
           "--data-binary" -> {
               i++
               data = File(args[i].value.removePrefix("@")).readBytes()
               if (method == HttpMethod.GET) method = HttpMethod.POST
           }
           "-F", "--form" -> {
               i++
               // Simplified form data handling
               val formData = args[i].value
               headers["Content-Type"] = "multipart/form-data"
               data = formData.toByteArray()
               if (method == HttpMethod.GET) method = HttpMethod.POST
           }
           "-o", "--output" -> {
               i++
               output = args[i].value
           }
           "-O", "--remote-name" -> {
               // Use remote filename
               output = "auto"
           }
           "-v", "--verbose" -> verbose = true
           "-s", "--silent" -> verbose = false
           "--compressed" -> compressed = true
           "-L", "--location" -> followRedirects = true
           "--max-redirs" -> {
               i++
               maxRedirects = args[i].value.toIntOrNull() ?: 50
           }
           "-m", "--max-time" -> {
               i++
               timeout = args[i].value.toIntOrNull()?.seconds ?: 30.seconds
           }
           "-A", "--user-agent" -> {
               i++
               userAgent = args[i].value
           }
           "-k", "--insecure" -> {
               // Skip SSL verification - not implemented
           }
           "--http1.1" -> http2 = false
           "--http2" -> http2 = true
           "--http3" -> http3 = true
           "-I", "--head" -> method = HttpMethod.HEAD
           else -> {
               if (!arg.startsWith("-")) {
                   url = arg
               }
           }
       }
       i++
   }
   
   // Auto-detect output filename if -O was used
   if (output == "auto") {
       output = url.substringAfterLast('/').ifEmpty { "index.html" }
   }
   
   return CurlDSL.CurlRequest(
       url = url,
       method = method,
       headers = headers,
       data = data,
       followRedirects = followRedirects,
       maxRedirects = maxRedirects,
       timeout = timeout,
       verbose = verbose,
       outputFile = output,
       userAgent = userAgent,
       compressed = compressed,
       http2 = http2,
       http3 = http3
   )
}

private fun parseAria2cArgs(args: ArgvSeries): Aria2cDSL.DownloadRequest {
   val uris = mutableListOf<String>()
   var outputDir = "."
   var outputFile: String? = null
   var connections = 16
   var split = 16
   var maxSpeed: String? = null
   var userAgent = "trikeshed-aria2c/1.0"
   var referer: String? = null
   val headers = mutableMapOf<String, String>()
   var torrent: String? = null
   var metalink: String? = null
   var checkIntegrity = false
   var continue = true
   var daemon = false
   var rpcPort = 6800
   var rpcSecret: String? = null
   
   var i = 0
   while (i < args.size) {
       when (val arg = args[i].value) {
           "-d", "--dir" -> {
               i++
               outputDir = args[i].value
           }
           "-o", "--out" -> {
               i++
               outputFile = args[i].value
           }
           "-x", "--max-connection-per-server" -> {
               i++
               connections = args[i].value.toIntOrNull() ?: 16
           }
           "-s", "--split" -> {
               i++
               split = args[i].value.toIntOrNull() ?: 16
           }
           "--max-download-limit" -> {
               i++
               maxSpeed = args[i].value
           }
           "-U", "--user-agent" -> {
               i++
               userAgent = args[i].value
           }
           "--referer" -> {
               i++
               referer = args[i].value
           }
           "--header" -> {
               i++
               val header = args[i].value.split(":", limit = 2)
               if (header.size == 2) {
                   headers[header[0].trim()] = header[1].trim()
               }
           }
           "-T", "--torrent-file" -> {
               i++
               torrent = args[i].value
           }
           "-M", "--metalink-file" -> {
               i++
               metalink = args[i].value
           }
           "-V", "--check-integrity" -> checkIntegrity = true
           "-c", "--continue" -> continue = true
           "--enable-rpc" -> daemon = true
           "--rpc-listen-port" -> {
               i++
               rpcPort = args[i].value.toIntOrNull() ?: 6800
           }
           "--rpc-secret" -> {
               i++
               rpcSecret = args[i].value
           }
           else -> {
               if (!arg.startsWith("-")) {
                   uris.add(arg)
               }
           }
       }
       i++
   }
   
   return Aria2cDSL.DownloadRequest(
       uris = uris,
       outputDir = outputDir,
       outputFile = outputFile,
       connections = connections,
       split = split,
       maxSpeed = maxSpeed,
       userAgent = userAgent,
       referer = referer,
       headers = headers,
       torrent = torrent,
       metalink = metalink,
       checkIntegrity = checkIntegrity,
       continue = continue,
       daemon = daemon,
       rpcPort = rpcPort,
       rpcSecret = rpcSecret
   )
}
