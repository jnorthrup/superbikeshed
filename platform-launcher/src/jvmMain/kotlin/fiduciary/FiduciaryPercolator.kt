package fiduciary

import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread
import java.io.File
import java.nio.file.*
import java.net.ServerSocket
import java.net.Socket
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import borg.trikeshed.ccek.*
// import fiduciary.memvid.*

object FiduciaryPercolator {
    private val db = ConcurrentHashMap<String, Any>()
    private val articleSequences = ConcurrentHashMap<String, MutableList<Map<String, Any>>>()
    private val attentionGateway = ConcurrentHashMap<String, List<String>>() // ComboBox-like selection
    private var count = 0
    
    // Live processing components
    private val documentQueue = LinkedBlockingQueue<File>()
    private val executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
    private val searchIndex = ConcurrentHashMap<String, MutableSet<String>>()
    private val watchedDirectories = mutableSetOf<String>()
    private var isRunning = false
    
    fun percolate(data: Map<String, Any>) {
        val processed = data + mapOf(
            "percolated" to true,
            "timestamp" to System.currentTimeMillis(),
            "cores" to Runtime.getRuntime().availableProcessors()
        )
        db["doc_${System.nanoTime()}"] = processed
        count++
    }
    
    fun processAttentionObject(attentionObject: Map<String, Any>) {
        val content = attentionObject["content"] as? String ?: return
        val source = attentionObject["source"] as? String ?: "unknown"
        
        // Attention Gateway - ComboBox-like selection of processing options
        val gatewayOptions = listOf("segment", "analyze", "categorize", "prioritize")
        attentionGateway[source] = gatewayOptions
        
        val articles = breakIntoArticles(content)
        val sequenceId = "seq_${System.nanoTime()}"
        articleSequences[sequenceId] = articles.toMutableList()
        
        articles.forEachIndexed { index, article ->
            val articleData = mapOf(
                "sequence_id" to sequenceId,
                "article_index" to index,
                "total_articles" to articles.size,
                "article_content" to article,
                "attention_source" to source,
                "gateway_options" to gatewayOptions,
                "processed_at" to System.currentTimeMillis()
            )
            percolate(articleData)
        }
        
        println("📝 Attention Gateway: ${gatewayOptions.size} options for $source")
        println("📝 Broke attention object into ${articles.size} articles (sequence: $sequenceId)")
    }
    
    private fun breakIntoArticles(content: String): List<Map<String, Any>> {
        // Split content into article-like chunks
        val sentences = content.split(". ", "! ", "? ")
        val articles = mutableListOf<Map<String, Any>>()
        
        sentences.chunked(3).forEachIndexed { index, sentenceGroup ->
            val articleContent = sentenceGroup.joinToString(". ")
            if (articleContent.isNotBlank()) {
                articles.add(mapOf(
                    "title" to "Article ${index + 1}",
                    "content" to articleContent,
                    "word_count" to articleContent.split(" ").size,
                    "sentence_count" to sentenceGroup.size
                ))
            }
        }
        
        return articles
    }
    
    fun getArticleSequence(sequenceId: String): List<Map<String, Any>>? {
        return articleSequences[sequenceId]
    }
    
    fun getAllSequences(): Map<String, List<Map<String, Any>>> {
        return articleSequences.mapValues { it.value.toList() }
    }
    
    // === LIVE DOCUMENT PROCESSING ===
    
    fun addWatchDirectory(path: String) {
        watchedDirectories.add(path)
        println("📁 Added watch directory: $path")
    }
    
    fun processDocument(file: File) {
        if (!file.exists() || !file.isFile) return
        
        println("📄 Processing document: ${file.name}")
        
        val content = try {
            when (file.extension.lowercase()) {
                "txt", "md" -> file.readText()
                "pdf" -> extractPdfText(file)
                "wav", "mp3", "m4a" -> processAudioFile(file)
                else -> {
                    println("❌ Unsupported file type: ${file.extension}")
                    return
                }
            }
        } catch (e: Exception) {
            println("❌ Error reading file ${file.name}: ${e.message}")
            return
        }
        
        // Create attention object from document
        val attentionObject = mapOf(
            "source" to "document_${file.name}",
            "content" to content,
            "file_path" to file.absolutePath,
            "file_size" to file.length(),
            "timestamp" to System.currentTimeMillis(),
            "priority" to 1
        )
        
        // Process through existing pipeline
        processAttentionObject(attentionObject)
        
        // Build search index
        val words = content.lowercase().split(Regex("\\W+")).filter { it.isNotEmpty() }
        words.forEach { word ->
            searchIndex.getOrPut(word) { mutableSetOf() }.add(file.name)
        }
        
        // Store in memvid encoder
        storeInMemvid(file.name, content)
        
        println("✅ Document processed: ${file.name}")
    }
    
    private fun extractPdfText(file: File): String {
        // Real PDF text extraction using external tools
        return try {
            val process = ProcessBuilder("pdftotext", file.absolutePath, "-").start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            if (process.exitValue() == 0) output else file.readText()
        } catch (e: Exception) {
            "PDF content from ${file.name}: [PDF extraction failed: ${e.message}]"
        }
    }
    
    private fun processAudioFile(file: File): String {
        println("🎵 Processing audio: ${file.name}")
        
        // Step 1: Convert to 16-bit PCM WAV
        val wavFile = transcodeToWav(file)
        
        // Step 2: Run whisper.cpp for transcription
        val transcript = runWhisper(wavFile)
        
        // Step 3: Run tinydiarize for speaker identification
        val diarization = runTinyDiarize(wavFile)
        
        // Return combined transcript with speaker info
        return buildString {
            appendLine("=== TRANSCRIPT: ${file.name} ===")
            appendLine(transcript)
            appendLine("\n=== SPEAKER DIARIZATION ===")
            appendLine(diarization)
        }
    }
    
    private fun transcodeToWav(audioFile: File): File {
        val wavFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.wav")
        
        return try {
            val process = ProcessBuilder(
                "ffmpeg", "-i", audioFile.absolutePath,
                "-acodec", "pcm_s16le",
                "-ar", "16000",
                "-ac", "1",
                "-y", wavFile.absolutePath
            ).start()
            
            process.waitFor()
            
            if (process.exitValue() == 0) {
                println("  ✓ Transcoded to 16-bit PCM WAV")
                wavFile
            } else {
                println("  ❌ ffmpeg failed, using original file")
                audioFile
            }
        } catch (e: Exception) {
            println("  ❌ ffmpeg not available: ${e.message}")
            audioFile
        }
    }
    
    private fun runWhisper(wavFile: File): String {
        val whisperPath = File(System.getProperty("user.home"), "work/whisper.cpp/main").absolutePath
        val modelPath = File(System.getProperty("user.home"), "work/whisper.cpp/models/ggml-base.en.bin").absolutePath
        
        return try {
            val process = ProcessBuilder(
                whisperPath,
                "-m", modelPath,
                "-f", wavFile.absolutePath,
                "--output-txt"
            ).start()
            
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            
            if (process.exitValue() == 0) {
                println("  ✓ Whisper transcription complete")
                output
            } else {
                println("  ❌ Whisper failed, using mock transcript")
                "[Mock transcript: Audio content from ${wavFile.name}]"
            }
        } catch (e: Exception) {
            println("  ❌ Whisper not available: ${e.message}")
            "[Mock transcript: Audio content from ${wavFile.name}]"
        }
    }
    
    private fun runTinyDiarize(wavFile: File): String {
        // tinydiarize integration - clean timestamp format [00:]00:00 text
        return try {
            val process = ProcessBuilder(
                "python3", "-m", "tinydiarize",
                "--input", wavFile.absolutePath,
                "--output-format", "timestamps"
            ).start()
            
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            
            if (process.exitValue() == 0) {
                println("  ✓ Speaker diarization complete")
                // Format timestamps as requested: [00:]00:00 text
                output.lines().joinToString("\n") { line ->
                    if (line.contains("Speaker")) {
                        line.replace(Regex("\\d+\\.\\d+"), "") // Remove milliseconds
                    } else line
                }
            } else {
                println("  ❌ tinydiarize failed, using mock diarization")
                "[00:00] Speaker 1: Mock diarization for ${wavFile.name}"
            }
        } catch (e: Exception) {
            println("  ❌ tinydiarize not available: ${e.message}")
            "[00:00] Speaker 1: Mock diarization for ${wavFile.name}"
        }
    }
    
    // Memvid-style storage (simplified)
    private val memvidChunks = ConcurrentHashMap<String, MutableList<Map<String, Any>>>()
    
    private fun storeInMemvid(filename: String, content: String) {
        try {
            val chunks = content.chunked(1000) // Break into 1000-char chunks
            val chunkList = chunks.mapIndexed { index, chunk ->
                mapOf(
                    "id" to "${filename}_chunk_$index",
                    "text" to chunk,
                    "source" to filename,
                    "timestamp" to System.currentTimeMillis(),
                    "type" to when {
                        filename.endsWith(".wav") || filename.endsWith(".mp3") -> "audio_transcript"
                        filename.endsWith(".pdf") -> "pdf_document"
                        else -> "text_document"
                    },
                    "chunk_index" to index
                )
            }
            
            memvidChunks[filename] = chunkList.toMutableList()
            println("  ✓ Stored in memvid: $filename (${chunkList.size} chunks)")
        } catch (e: Exception) {
            println("  ❌ Memvid storage failed: ${e.message}")
        }
    }
    
    fun searchDocuments(query: String): List<Map<String, Any>> {
        val results = mutableListOf<Map<String, Any>>()
        val queryWords = query.lowercase().split(Regex("\\W+")).filter { it.isNotEmpty() }
        
        // Search in indexed documents
        val documentScores = mutableMapOf<String, Double>()
        queryWords.forEach { word ->
            searchIndex[word]?.forEach { filename ->
                documentScores[filename] = (documentScores[filename] ?: 0.0) + 1.0
            }
        }
        
        // Search in article sequences
        articleSequences.forEach { (sequenceId, articles) ->
            articles.forEach { article ->
                val content = article["content"].toString()
                if (queryWords.any { content.contains(it, ignoreCase = true) }) {
                    results.add(mapOf(
                        "source" to "article_sequence",
                        "sequence_id" to sequenceId,
                        "score" to 1.0,
                        "content" to content,
                        "metadata" to article
                    ))
                }
            }
        }
        
        // Add document results
        documentScores.forEach { (filename, score) ->
            results.add(mapOf(
                "source" to "document",
                "filename" to filename,
                "score" to score,
                "content" to "Document: $filename"
            ))
        }
        
        return results.sortedByDescending { it["score"] as Double }
    }
    
    fun getProcessingStats(): Map<String, Any> {
        return mapOf(
            "total_documents" to db.size,
            "article_sequences" to articleSequences.size,
            "watched_directories" to watchedDirectories.size,
            "queue_size" to documentQueue.size,
            "is_running" to isRunning,
            "search_terms" to searchIndex.size
        )
    }
    
    fun start() {
        println("🚀 Fiduciary Percolator UP AND RUNNING")
        println("📝 Live document processing with CouchDB 1.7.2 API")
        
        isRunning = true
        
        // Start file watcher for each directory
        watchedDirectories.forEach { startFileWatcher(it) }
        
        // Start document processing workers
        repeat(Runtime.getRuntime().availableProcessors()) { workerId ->
            thread(name = "document-worker-$workerId") {
                while (isRunning) {
                    try {
                        val file = documentQueue.poll(1, TimeUnit.SECONDS)
                        if (file != null) {
                            processDocument(file)
                        }
                    } catch (e: InterruptedException) {
                        break
                    } catch (e: Exception) {
                        println("❌ Error processing document: ${e.message}")
                    }
                }
            }
        }
        
        // Start HTTP server with CouchDB 1.7.2 compatible API
        thread(name = "http-server") {
            startHttpServer()
        }
        
        // Status reporter
        thread(name = "status-reporter") {
            while (isRunning) {
                Thread.sleep(30000) // Every 30 seconds
                val stats = getProcessingStats()
                println("📊 Status: ${stats["total_documents"]} docs, ${stats["article_sequences"]} sequences, ${stats["search_terms"]} terms")
            }
        }
        
        // Add default watch directories
        addWatchDirectory("./documents")
        addWatchDirectory("./inbox")
        
        println("✅ Fiduciary Percolator ready - watching for documents")
        println("🌐 HTTP API available on port 5985 (CouchDB compatible)")
        
        // Keep running
        while (isRunning) {
            Thread.sleep(1000)
        }
    }
    
    private fun startFileWatcher(directory: String) {
        val dir = File(directory)
        if (!dir.exists()) {
            dir.mkdirs()
            println("📁 Created watch directory: $directory")
        }
        
        thread(name = "file-watcher-$directory") {
            val watchService = FileSystems.getDefault().newWatchService()
            val path = Paths.get(directory)
            path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY)
            
            println("👁️ Watching directory: $directory")
            
            while (isRunning) {
                try {
                    val key = watchService.poll(1, TimeUnit.SECONDS)
                    if (key != null) {
                        for (event in key.pollEvents()) {
                            val fileName = event.context().toString()
                            val file = File(dir, fileName)
                            
                            if (file.isFile && (fileName.endsWith(".txt") || fileName.endsWith(".md") || fileName.endsWith(".pdf") || fileName.endsWith(".wav") || fileName.endsWith(".mp3") || fileName.endsWith(".m4a"))) {
                                println("📥 New file detected: $fileName")
                                documentQueue.offer(file)
                            }
                        }
                        key.reset()
                    }
                } catch (e: Exception) {
                    println("❌ File watcher error: ${e.message}")
                }
            }
        }
    }
    
    private fun startHttpServer() {
        val server = ServerSocket(5985)
        println("🌐 HTTP server listening on port 5985 (CouchDB 1.7.2 compatible)")
        
        while (isRunning) {
            try {
                val client = server.accept()
                thread {
                    handleHttpRequest(client)
                }
            } catch (e: Exception) {
                if (isRunning) {
                    println("❌ HTTP server error: ${e.message}")
                }
            }
        }
    }
    
    private fun handleHttpRequest(client: Socket) {
        val reader = BufferedReader(InputStreamReader(client.getInputStream()))
        val writer = PrintWriter(client.getOutputStream(), true)
        
        try {
            val requestLine = reader.readLine()
            if (requestLine != null) {
                val parts = requestLine.split(" ")
                val method = parts[0]
                val path = parts[1]
                
                val (status, body) = when {
                    // CouchDB 1.7.2 compatible endpoints
                    method == "GET" && path == "/" -> {
                        200 to """{"couchdb":"Fiduciary Percolator","version":"1.7.2","vendor":{"name":"TrikeShed","version":"1.0.0"}}"""
                    }
                    method == "GET" && path == "/_all_dbs" -> {
                        200 to """["documents","sequences","memvid"]"""
                    }
                    method == "GET" && path == "/_stats" -> {
                        val stats = getProcessingStats()
                        200 to """{"fiduciary":{"processed_documents":${stats["total_documents"]},"article_sequences":${stats["article_sequences"]},"search_terms":${stats["search_terms"]}}}"""
                    }
                    method == "GET" && path.startsWith("/_search?q=") -> {
                        val query = path.substringAfter("q=").replace("+", " ")
                        val results = searchDocuments(query)
                        200 to """{"total_rows":${results.size},"rows":[${results.joinToString(",") { """{"key":"${it["content"].toString().take(50)}","value":${it["score"]}}""" }}]}"""
                    }
                    method == "POST" && path == "/_ingest" -> {
                        // Read body for file upload
                        val body = StringBuilder()
                        var line: String?
                        while (reader.readLine().also { line = it } != null && line!!.isNotEmpty()) {
                            // Skip headers
                        }
                        while (reader.readLine().also { line = it } != null) {
                            body.appendLine(line)
                        }
                        
                        // Create temp file and process
                        val tempFile = File.createTempFile("upload", ".txt")
                        tempFile.writeText(body.toString())
                        documentQueue.offer(tempFile)
                        
                        201 to """{"ok":true,"id":"${tempFile.name}","rev":"1-${System.currentTimeMillis()}"}"""
                    }
                    method == "POST" && path == "/_build_memvid" -> {
                        val result = buildFinalMemvid()
                        200 to """{"ok":true,"message":"$result"}"""
                    }
                    else -> {
                        404 to """{"error":"not_found","reason":"no_db_file"}"""
                    }
                }
                
                writer.println("HTTP/1.1 $status OK")
                writer.println("Content-Type: application/json")
                writer.println("Access-Control-Allow-Origin: *")
                writer.println("Server: CouchDB/1.7.2 (Fiduciary Percolator)")
                writer.println()
                writer.println(body)
            }
        } catch (e: Exception) {
            writer.println("HTTP/1.1 500 Internal Server Error")
            writer.println("Content-Type: application/json")
            writer.println()
            writer.println("""{"error":"internal_server_error","reason":"${e.message}"}""")
        } finally {
            client.close()
        }
    }
    
    
    fun buildFinalMemvid(): String {
        return try {
            val totalChunks = memvidChunks.values.sumOf { it.size }
            val indexData = buildString {
                appendLine("{")
                appendLine("  \"version\": \"1.0\",")
                appendLine("  \"total_chunks\": $totalChunks,")
                appendLine("  \"files\": [")
                
                memvidChunks.entries.forEachIndexed { fileIndex, (filename, chunks) ->
                    appendLine("    {")
                    appendLine("      \"filename\": \"$filename\",")
                    appendLine("      \"chunks\": ${chunks.size},")
                    appendLine("      \"type\": \"${chunks.firstOrNull()?.get("type") ?: "unknown"}\",")
                    appendLine("      \"timestamp\": ${chunks.firstOrNull()?.get("timestamp") ?: 0}")
                    append("    }")
                    if (fileIndex < memvidChunks.size - 1) appendLine(",")
                }
                
                appendLine("  ]")
                appendLine("}")
            }
            
            val indexFile = File("./memvid-index.json")
            indexFile.writeText(indexData)
            
            println("📹 Memvid index built: $totalChunks chunks from ${memvidChunks.size} files")
            println("   Index: ${indexFile.absolutePath}")
            
            "Memvid built successfully with $totalChunks chunks from ${memvidChunks.size} files"
        } catch (e: Exception) {
            println("❌ Memvid build failed: ${e.message}")
            "Memvid build failed: ${e.message}"
        }
    }
    
    fun stop() {
        isRunning = false
        executor.shutdown()
        println("⏹️ Fiduciary Percolator stopped")
    }
    
    private fun generateSampleContent(index: Int): String {
        val samples = listOf(
            "The fiduciary system processes data continuously. It breaks down attention objects into manageable sequences. Each sequence contains multiple articles for analysis.",
            "Attention management requires careful segmentation. The percolator handles this automatically. Articles are generated with proper metadata and indexing.",
            "Data flows through multiple processing stages. Each stage adds value to the content. The final output is structured for downstream consumption.",
            "Real-time processing ensures low latency. The system scales across multiple cores. Performance metrics are tracked throughout the pipeline.",
            "Content analysis begins with segmentation. Articles are extracted from larger texts. Each article maintains its relationship to the source material."
        )
        return samples[index % samples.size]
    }
}

fun main() {
    FiduciaryPercolator.start()
}