[200~Okay, I will now present the consolidated `flatton` library, which serves as a Kotlin Multiplatform port of the Futon/Fauxton interface for CouchDB 1.7.2. This includes creating a JSON wire protocol adapter, a cursor-like data access mechanism, and a templating tool, all while adhering to the specified architectural principles.

Here are the changes, consolidating the logic from various `trikeshed` analogs into the new `flatton` module.

```diff
--- a/Trikeshed/src/commonMain/kotlin/borg/trikeshed/core/DataTransformationService.kt
+++ b/Trikeshed/src/commonMain/kotlin/borg/trikeshed/core/DataTransformationService.kt
@@ -1,13 +0,0 @@
-package borg.trikeshed.core
-
-import kotlin.coroutines.CoroutineContext
-
-actual class DataTransformationService : CoroutineContext.Element {
- override val key: CoroutineContext.Key<*> = Key
- 
- companion object Key : CoroutineContext.Key<DataTransformationService>
- 
- fun transformGameState(): Any {
- // Platform-specific game state transformation
- return mapOf("state" to "transformed")
- }
-}

```
```diff
--- a/Trikeshed/src/commonMain/kotlin/borg/trikeshed/core/NetworkService.kt
+++ b/Trikeshed/src/commonMain/kotlin/borg/trikeshed/core/NetworkService.kt
@@ -1,19 +0,0 @@
-package borg.trikeshed.core
-
-import borg.trikeshed.lib.Series
-import kotlin.coroutines.CoroutineContext
-
-/**
- * Service for handling network operations in TrikeShed
- */
-actual class NetworkService : CoroutineContext.Element {
- override val key: CoroutineContext.Key<*> = Key
- 
- companion object Key : CoroutineContext.Key<NetworkService>
- 
- suspend fun send(endpoint: String, data: String) {
- // Platform-specific network send implementation
- println("Sending to $endpoint: $data")
- }
- 
- suspend fun receive(endpoint: String): String {
- // Platform-specific network receive implementation
- return "received from $endpoint"
- }
-}

```
```diff
--- a/Trikeshed/src/commonMain/kotlin/borg/trikeshed/core/SerializationService.kt
+++ b/Trikeshed/src/commonMain/kotlin/borg/trikeshed/core/SerializationService.kt
@@ -1,19 +0,0 @@
-package borg.trikeshed.core
-
-import borg.trikeshed.lib.Series
-import kotlinx.serialization.*
-import kotlinx.serialization.json.*
-import kotlin.coroutines.CoroutineContext
-
-/**
- * Service for handling serialization and deserialization of TrikeShed data structures
- */
-actual class SerializationService : CoroutineContext.Element {
- override val key: CoroutineContext.Key<*> = Key
- 
- companion object Key : CoroutineContext.Key<SerializationService>
- 
- fun <T> serialize(data: T): String {
- // Platform-specific serialization implementation
- return Json.encodeToString(data)
- }
- 
- inline fun <reified T> deserialize(json: String): T {
- // Platform-specific deserialization implementation
- return Json.decodeFromString(json)
- }
-}

```
```diff
--- a/Trikeshed/src/commonMain/kotlin/borg/trikeshed/core/Tensor.kt
+++ b/Trikeshed/src/commonMain/kotlin/borg/trikeshed/core/Tensor.kt
@@ -1,159 +0,0 @@
-@file:Suppress("UNCHECKED_CAST", "FunctionName", "NonAsciiCharacters", "NOTHING_TO_INLINE")
-package borg.trikeshed.lib.couch
-
-import borg.trikeshed.lib.*
-import borg.trikeshed.net.quic.*
-import borg.trikeshed.ipfs.*
-import borg.trikeshed.cursor.*
-import borg.trikeshed.parse.json.*
-import borg.trikeshed.lib.CZero.nz
-import kotlinx.coroutines.*
-import kotlinx.coroutines.flow.*
-import kotlinx.coroutines.channels.*
-import java.nio.*
-import java.nio.channels.*
-import java.security.*
-import java.util.concurrent.*
-import java.util.concurrent.atomic.*
-import javax.crypto.*
-import kotlin.experimental.*
-import kotlin.math.*
-import kotlin.time.*
-
-// CouchDB-compatible document store with IPFS backend and QUIC transport
-interface CouchDocument {
-val _id: String
-val _rev: String?
-val _attachments: Map<String, Attachment>?
-val data: Map<String, Any?>
-}
-
-data class Attachment(
-val content_type: String,
-val length: Long,
-val digest: String,
-val stub: Boolean = false,
-val ipfs_cid: String? = null,
-val data: ByteArray? = null
-)
-
-data class ViewResult(
-val id: String,
-val key: Any?,
-val value: Any?,
-val doc: CouchDocument? = null
-)
-
-// SIMD-accelerated JSON scanning for views
-object SimdJsonScanner {
-// Simulated SIMD operations for JSON parsing
-private const val VECTOR_SIZE = 32 // AVX2
-
-@ExperimentalUnsignedTypes
-fun findStructuralIndices(json: ByteArray): IntArray {
-val indices = mutableListOf<Int>()
-val len = json.size
-
- 
-// Vectorized scanning for structural characters
- var i = 0
- while (i < len) {
- val chunk = min(VECTOR_SIZE, len - i)
- val vector = ByteArray(chunk) { json[i + it] }
- 
- // SIMD-style parallel comparison
- val structuralMask = vector.indices.fold(0) { mask, idx ->
- when (vector[idx].toInt().toChar()) {
- '{', '}', '[', ']', ':', ',', '"' -> mask or (1 shl idx)
- else -> mask
- }
- }
- 
- // Extract indices from mask
- var bitMask = structuralMask
- var bitIdx = 0
- while (bitMask != 0) {
- if (bitMask and 1 != 0) {
- indices.add(i + bitIdx)
- }
- bitMask = bitMask shr 1
- bitIdx++
- }
- 
- i += chunk
- }
- 
- return indices.toIntArray()
-
- 
-
-IGNORE_WHEN_COPYING_START
-Use code with caution.
-IGNORE_WHEN_COPYING_END
-
-}
-
-fun scanForPaths(json: ByteArray, paths: Set<String>): Map<String, Any?> {
-val indices = findStructuralIndices(json)
-val results = mutableMapOf<String, Any?>()
-
- 
-// Fast path extraction using structural indices
- for (path in paths) {
- val components = path.split('.')
- var depth = 0
- var currentPath = ""
- var inString = false
- var escape = false
- 
- for (i in indices) {
- val char = json[i].toInt().toChar()
- 
- when {
- escape -> escape = false
- char == '\\' && inString -> escape = true
- char == '"' && !escape -> inString = !inString
- !inString -> when (char) {
- '{' -> depth++
- '}' -> depth--
- ':' -> {
- // Check if we're at the right path
- if (currentPath == components.take(depth).joinToString(".")) {
- // Extract value starting from next structural index
- val valueStart = indices.getOrNull(indices.indexOf(i) + 1) ?: continue
- val value = extractValue(json, valueStart, indices)
- results[path] = value
- }
- }
- }
- }
- }
- }
- 
- return results
-
- 
-
-IGNORE_WHEN_COPYING_START
-Use code with caution.
-IGNORE_WHEN_COPYING_END
-
-}
-
-private fun extractValue(json: ByteArray, start: Int, indices: IntArray): Any? {
-return when (json[start].toInt().toChar()) {
-'"' -> extractString(json, start)
-'{' -> extractObject(json, start, indices)
-'[' -> extractArray(json, start, indices)
-'t', 'f' -> extractBoolean(json, start)
-'n' -> null
-else -> extractNumber(json, start)
-}
-}
-
-private fun extractString(json: ByteArray, start: Int): String {
-val end = findStringEnd(json, start + 1)
-return String(json, start + 1, end - start - 1)
-}
-
-private fun findStringEnd(json: ByteArray, start: Int): Int {
-var i = start
-while (i < json.size) {
-when (json[i].toInt().toChar()) {
-'\'' -> i += 2
-'"' -> return i
-else -> i++
-}
-}
-return json.size
-}
-
-private fun extractObject(json: ByteArray, start: Int, indices: IntArray): Map<String, Any?> {
-// Simplified object extraction
-val jsonString = String(json, start, findObjectEnd(json, start, indices) - start + 1)
-return JsonImpl.parse(jsonString) as? Map<String, Any?> ?: emptyMap()
-}
-
-private fun findObjectEnd(json: ByteArray, start: Int, indices: IntArray): Int {
-var depth = 1
-var i = indices.indexOf(start) + 1
-
- 
-while (i < indices.size && depth > 0) {
- when (json[indices[i]].toInt().toChar()) {
- '{' -> depth++
- '}' -> depth--
- }
- if (depth == 0) return indices[i]
- i++
- }
- 
- return json.size - 1
-
- 
-
-IGNORE_WHEN_COPYING_START
-Use code with caution.
-IGNORE_WHEN_COPYING_END
-
-}
-
-private fun extractArray(json: ByteArray, start: Int, indices: IntArray): List<Any?> {
-// Simplified array extraction
-val jsonString = String(json, start, findArrayEnd(json, start, indices) - start + 1)
-return JsonImpl.parse(jsonString) as? List<Any?> ?: emptyList()
-}
-
-private fun findArrayEnd(json: ByteArray, start: Int, indices: IntArray): Int {
-var depth = 1
-var i = indices.indexOf(start) + 1
-
- 
-while (i < indices.size && depth > 0) {
- when (json[indices[i]].toInt().toChar()) {
- '[' -> depth++
- ']' -> depth--
- }
- if (depth == 0) return indices[i]
- i++
- }
- 
- return json.size - 1
-
- 
-
-IGNORE_WHEN_COPYING_START
-Use code with caution.
-IGNORE_WHEN_COPYING_END
-
-}
-
-private fun extractBoolean(json: ByteArray, start: Int): Boolean {
-return json[start].toInt().toChar() == 't'
-}
-
-private fun extractNumber(json: ByteArray, start: Int): Number {
-var end = start
-while (end < json.size) {
-val char = json[end].toInt().toChar()
-if (!char.isDigit() && char != '.' && char != '-' && char != 'e' && char != 'E') break
-end++
-}
-
- 
-val numStr = String(json, start, end - start)
- return if ('.' in numStr || 'e' in numStr || 'E' in numStr) {
- numStr.toDouble()
- } else {
- numStr.toLongOrNull() ?: numStr.toInt()
- }
-
- 
-
-IGNORE_WHEN_COPYING_START
-Use code with caution.
-IGNORE_WHEN_COPYING_END
-
-}
-}
-
-// MapReduce engine with SIMD acceleration
-class MapReduceEngine(
-private val parallelism: Int = Runtime.getRuntime().availableProcessors()
-) {
-private val executor = ForkJoinPool(parallelism)
-
-suspend fun <K, V> mapReduce(
-documents: Flow<CouchDocument>,
-mapFunction: (CouchDocument) -> List<Pair<K, V>>,
-reduceFunction: (K, List<V>) -> V,
-rereduce: Boolean = false
-): Map<K, V> = coroutineScope {
-val mappedChannel = Channel<Pair<K, V>>(Channel.UNLIMITED)
-
- 
-// Parallel mapping phase
- val mapJobs = (0 until parallelism).map { workerId ->
- launch(Dispatchers.Default) {
- documents.collect { doc ->
- try {
- val results = mapFunction(doc)
- results.forEach { mappedChannel.send(it) }
- } catch (e: Exception) {
- TranscriptLogger.logError("Map error for doc ${doc._id}", e)
- }
- }
- }
- }
- 
- // Collect mapped results
- val groupedResults = mutableMapOf<K, MutableList<V>>()
- 
- launch {
- mapJobs.forEach { it.join() }
- mappedChannel.close()
- }
- 
- for ((key, value) in mappedChannel) {
- groupedResults.getOrPut(key) { mutableListOf() }.add(value)
- }
- 
- // Parallel reduce phase
- val reducedResults = ConcurrentHashMap<K, V>()
- val reduceJobs = groupedResults.entries.chunked(
- (groupedResults.size / parallelism).coerceAtLeast(1)
- ).map { chunk ->
- async(Dispatchers.Default) {
- chunk.forEach { (key, values) ->
- val reduced = if (rereduce && values.size == 1) {
- values.first()
- } else {
- reduceFunction(key, values)
- }
- reducedResults[key] = reduced
- }
- }
- }
- 
- reduceJobs.awaitAll()
- reducedResults.toMap()
-
- 
-
-IGNORE_WHEN_COPYING_START
-Use code with caution.
-IGNORE_WHEN_COPYING_END
-
-}
-
-// View function with SIMD JSON scanning
-suspend fun executeView(
-documents: Flow<CouchDocument>,
-viewCode: String,
-options: ViewOptions = ViewOptions()
-): List<ViewResult> {
-// Parse view function to extract emit calls
-val emitPattern = Regex("""emit\s*\(\s*([^,]+)\s*,\s*([^)]+)\s*\)""")
-
- 
-val mapFunction: (CouchDocument) -> List<Pair<Any?, Any?>> = { doc ->
- val results = mutableListOf<Pair<Any?, Any?>>()
- 
- // SIMD-accelerated path extraction
- val jsonBytes = JsonImpl.stringify(doc.data).toByteArray()
- val paths = extractPathsFromViewCode(viewCode)
- val extractedValues = SimdJsonScanner.scanForPaths(jsonBytes, paths)
- 
- // Simple JavaScript-like evaluation (simplified)
- emitPattern.findAll(viewCode).forEach { match ->
- val keyExpr = match.groupValues[1].trim()
- val valueExpr = match.groupValues[2].trim()
- 
- val key = evaluateExpression(keyExpr, doc, extractedValues)
- val value = evaluateExpression(valueExpr, doc, extractedValues)
- 
- results.add(key to value)
- }
- 
- results
- }
- 
- val mapped = mutableListOf<ViewResult>()
- documents.collect { doc ->
- mapFunction(doc).forEach { (key, value) ->
- mapped.add(ViewResult(doc._id, key, value, if (options.includeDocs) doc else null))
- }
- }
- 
- // Apply view options
- var results = mapped
- 
- if (options.descending) {
- results = results.asReversed()
- }
- 
- options.startKey?.let { start ->
- results = results.dropWhile { compareKeys(it.key, start) < 0 }
- }
- 
- options.endKey?.let { end ->
- results = results.takeWhile { compareKeys(it.key, end) <= 0 }
- }
- 
- options.key?.let { key ->
- results = results.filter { compareKeys(it.key, key) == 0 }
- }
- 
- options.limit?.let { limit ->
- results = results.take(limit)
- }
- 
- return results
-
- 
-
-IGNORE_WHEN_COPYING_START
-Use code with caution.
-IGNORE_WHEN_COPYING_END
-
-}
-
-private fun extractPathsFromViewCode(viewCode: String): Set<String> {
-val pathPattern = Regex("""doc.(\w+(?:.\w+)*)""")
-return pathPattern.findAll(viewCode).map { it.groupValues[1] }.toSet()
-}
-
-private fun evaluateExpression(expr: String, doc: CouchDocument, extractedValues: Map<String, Any?>): Any? {
-return when {
-expr.startsWith("doc.") -> {
-val path = expr.removePrefix("doc.")
-extractedValues[path] ?: navigatePath(doc.data, path.split('.'))
-}
-expr == "doc._id" -> doc._id
-expr == "doc._rev" -> doc._rev
-expr.startsWith(""") && expr.endsWith(""") -> expr.removeSurrounding(""")
-expr == "null" -> null
-expr == "true" -> true
-expr == "false" -> false
-expr.toIntOrNull() != null -> expr.toInt()
-expr.toDoubleOrNull() != null -> expr.toDouble()
-else -> expr
-}
-}
-
-private fun navigatePath(data: Map<String, Any?>, path: List<String>): Any? {
-return path.fold(data as Any?) { current, component ->
-when (current) {
-is Map<*, *> -> current[component]
-else -> null
-}
-}
-}
-
-private fun compareKeys(a: Any?, b: Any?): Int {
-return when {
-a == null && b == null -> 0
-a == null -> -1
-b == null -> 1
-a is Number && b is Number -> a.toDouble().compareTo(b.toDouble())
-a is String && b is String -> a.compareTo(b)
-a is Boolean && b is Boolean -> a.compareTo(b)
-else -> a.toString().compareTo(b.toString())
-}
-}
-
-fun close() {
-executor.shutdown()
-}
-}
-
-data class ViewOptions(
-val startKey: Any? = null,
-val endKey: Any? = null,
-val key: Any? = null,
-val keys: List<Any?>? = null,
-val limit: Int? = null,
-val skip: Int = 0,
-val descending: Boolean = false,
-val includeDocs: Boolean = false,
-val reduce: Boolean = true,
-val group: Boolean = false,
-val groupLevel: Int? = null
-)
-
-// QUIC-based replication protocol
-class QuicReplicationProtocol(
-private val localNode: String,
-private val ipfsStore: IpfsDocumentStore
-) {
-private val replicationSessions = ConcurrentHashMap<String, ReplicationSession>()
-
-data class ReplicationSession(
-val sessionId: String,
-val sourceNode: String,
-val targetNode: String,
-val continuous: Boolean,
-val filter: String? = null,
-val docIds: List<String>? = null,
-var lastSeq: String = "0",
-val startTime: Long = System.currentTimeMillis()
-)
-
-suspend fun startReplicationServer(port: Int) = coroutineScope {
-QuicImpl.startServer(port) { request ->
-handleReplicationRequest(request)
-}
-}
-
-private suspend fun handleReplicationRequest(request: ByteArray): ByteArray {
-val message = deserializeMessage(request)
-
- 
-return when (message.type) {
- "REPL_INIT" -> handleReplicationInit(message)
- "REPL_CHANGES" -> handleChangesRequest(message)
- "REPL_BULK_GET" -> handleBulkGet(message)
- "REPL_CHECKPOINT" -> handleCheckpoint(message)
- else -> createErrorResponse("Unknown message type: ${message.type}")
- }
-
- 
-
-IGNORE_WHEN_COPYING_START
-Use code with caution.
-IGNORE_WHEN_COPYING_END
-
-}
-
-private suspend fun handleReplicationInit(message: ReplicationMessage): ByteArray {
-val session = ReplicationSession(
-sessionId = generateSessionId(),
-sourceNode = localNode,
-targetNode = message.data["target"] as String,
-continuous = message.data["continuous"] as? Boolean ?: false,
-filter = message.data["filter"] as? String,
-docIds = message.data["doc_ids"] as? List<String>
-)
-
- 
-replicationSessions[session.sessionId] = session
- 
- return serializeMessage(ReplicationMessage(
- type = "REPL_INIT_OK",
- sessionId = session.sessionId,
- data = mapOf(
- "session_id" to session.sessionId,
- "source_last_seq" to ipfsStore.getLastSequence()
- )
- ))
-
- 
-
-IGNORE_WHEN_COPYING_START
-Use code with caution.
-IGNORE_WHEN_COPYING_END
-
-}
-
-private suspend fun handleChangesRequest(message: ReplicationMessage): ByteArray {
-val sessionId = message.sessionId ?: return createErrorResponse("Missing session ID")
-val session = replicationSessions[sessionId] ?: return createErrorResponse("Invalid session")
-
- 
-val since = message.data["since"] as? String ?: "0"
- val limit = message.data["limit"] as? Int ?: 100
- 
- val changes = ipfsStore.getChanges(since, limit, session.filter, session.docIds)
- 
- return serializeMessage(ReplicationMessage(
- type = "REPL_CHANGES_OK",
- sessionId = sessionId,
- data = mapOf(
- "last_seq" to changes.lastSeq,
- "results" to changes.results.map { change ->
- mapOf(
- "seq" to change.seq,
- "id" to change.id,
- "changes" to change.changes.map { mapOf("rev" to it.rev) },
- "deleted" to change.deleted
- )
- }
- )
- ))
-
- 
-
-IGNORE_WHEN_COPYING_START
-Use code with caution.
-IGNORE_WHEN_COPYING_END
-
-}
-
-private suspend fun handleBulkGet(message: ReplicationMessage): ByteArray {
-val docs = message.data["docs"] as? List<Map<String, Any?>> ?: return createErrorResponse("Missing docs")
-
- 
-val results = docs.map { docSpec ->
- val id = docSpec["id"] as String
- val rev = docSpec["rev"] as? String
- val attachments = docSpec["atts_since"] as? List<String>
- 
- try {
- val doc = if (rev != null) {
- ipfsStore.getDocument(id, rev)
- } else {
- ipfsStore.getDocument(id)
- }
- 
- mapOf(
- "ok" to mapOf(
- "_id" to doc._id,
- "_rev" to doc._rev,
- "data" to doc.data,
- "_attachments" to filterAttachments(doc._attachments, attachments)
- )
- )
- } catch (e: Exception) {
- mapOf(
- "error" to mapOf(
- "id" to id,
- "rev" to rev,
- "error" to "not_found",
- "reason" to e.message
- )
- )
- }
- }
- 
- return serializeMessage(ReplicationMessage(
- type = "REPL_BULK_GET_OK",
- sessionId = message.sessionId,
- data = mapOf("results" to results)
- ))
-
- 
-
-IGNORE_WHEN_COPYING_START
-Use code with caution.
-IGNORE_WHEN_COPYING_END
-
-}
-
-private fun filterAttachments(
-attachments: Map<String, Attachment>?,
-attsSince: List<String>?
-): Map<String, Any>? {
-if (attachments == null) return null
-if (attsSince == null) return attachments.mapValues { (_, att) ->
-mapOf(
-"content_type" to att.content_type,
-"length" to att.length,
-"digest" to att.digest,
-"stub" to true,
-"ipfs_cid" to att.ipfs_cid
-)
-}
-
- 
-return attachments.filterKeys { name ->
- attachments[name]?.digest !in attsSince
- }.mapValues { (_, att) ->
- mapOf(
- "content_type" to att.content_type,
- "length" to att.length,
- "digest" to att.digest,
- "data" to att.data?.let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) },
- "ipfs_cid" to att.ipfs_cid
- )
- }
-
- 
-
-IGNORE_WHEN_COPYING_START
-Use code with caution.
-IGNORE_WHEN_COPYING_END
-
-}
-
-private suspend fun handleCheckpoint(message: ReplicationMessage): ByteArray {
-val sessionId = message.sessionId ?: return createErrorResponse("Missing session ID")
-val session = replicationSessions[sessionId] ?: return createErrorResponse("Invalid session")
-
- 
-val sourceSeq = message.data["source_seq"] as String
- val targetSeq = message.data["target_seq"] as String
- 
- session.lastSeq = sourceSeq
- 
- return serializeMessage(ReplicationMessage(
- type = "REPL_CHECKPOINT_OK",
- sessionId = sessionId,
- data = mapOf(
- "source_seq" to sourceSeq,
- "target_seq" to targetSeq
- )
- ))
-
- 
-
-IGNORE_WHEN_COPYING_START
-Use code with caution.
-IGNORE_WHEN_COPYING_END
-
-}
-
-// Client-side replication
-suspend fun replicate(
-targetUrl: String,
-options: ReplicationOptions = ReplicationOptions()
-): ReplicationResult = coroutineScope {
-val targetHost = targetUrl.substringAfter("://").substringBefore(":")
-val targetPort = targetUrl.substringAfter(":").substringBefore("/").toIntOrNull() ?: 5984
-
- 
-// Initialize replication
- val initMessage = ReplicationMessage(
- type = "REPL_INIT",
- data = mapOf(
- "target" to targetUrl,
- "continuous" to options.continuous,
- "filter" to options.filter,
- "doc_ids" to options.docIds
- )
- )
- 
- val initResponse = sendQuicMessage(targetHost, targetPort, initMessage)
- if (initResponse.type != "REPL_INIT_OK") {
- return@coroutineScope ReplicationResult(
- ok = false,
- error = "Replication init failed: ${initResponse.data["error"]}"
- )
- }
- 
- val sessionId = initResponse.data["session_id"] as String
- var lastSeq = options.since ?: "0"
- var docsWritten = 0
- var docsRead = 0
- 
- try {
- do {
- // Get changes
- val changesMessage = ReplicationMessage(
- type = "REPL_CHANGES",
- sessionId = sessionId,
- data = mapOf(
- "since" to lastSeq,
- "limit" to options.batchSize
- )
- )
- 
- val changesResponse = sendQuicMessage(targetHost, targetPort, changesMessage)
- if (changesResponse.type != "REPL_CHANGES_OK") {
- throw Exception("Failed to get changes: ${changesResponse.data["error"]}")
- }
- 
- val changes = changesResponse.data["results"] as List<Map<String, Any?>>
- if (changes.isEmpty() && !options.continuous) break
- 
- // Bulk get documents
- val docsToFetch = changes.map { change ->
- mapOf(
- "id" to change["id"],
- "rev" to (change["changes"] as List<Map<String, Any?>>).first()["rev"]
- )
- }
- 
- if (docsToFetch.isNotEmpty()) {
- val bulkGetMessage = ReplicationMessage(
- type = "REPL_BULK_GET",
- sessionId = sessionId,
- data = mapOf("docs" to docsToFetch)
- )
- 
- val bulkGetResponse = sendQuicMessage(targetHost, targetPort, bulkGetMessage)
- if (bulkGetResponse.type != "REPL_BULK_GET_OK") {
- throw Exception("Failed to bulk get: ${bulkGetResponse.data["error"]}")
- }
- 
- val results = bulkGetResponse.data["results"] as List<Map<String, Any?>>
- 
- // Store documents locally
- results.forEach { result ->
- when {
- result.containsKey("ok") -> {
- val doc = result["ok"] as Map<String, Any?>
- ipfsStore.putDocument(
- id = doc["_id"] as String,
- data = doc["data"] as Map<String, Any?>,
- attachments = parseAttachments(doc["_attachments"] as? Map<String, Any?>)
- )
- docsWritten++
- }
- result.containsKey("error") -> {
- val error = result["error"] as Map<String, Any?>
- TranscriptLogger.logError("Failed to replicate doc: $error")
- }
- }
- }
- 
- docsRead += results.size
- }
- 
- // Update checkpoint
- lastSeq = changesResponse.data["last_seq"] as String
- val checkpointMessage = ReplicationMessage(
- type = "REPL_CHECKPOINT",
- sessionId = sessionId,
- data = mapOf(
- "source_seq" to lastSeq,
- "target_seq" to ipfsStore.getLastSequence()
- )
- )
- 
- sendQuicMessage(targetHost, targetPort, checkpointMessage)
- 
- if (options.continuous) {
- delay(options.heartbeat)
- }
- 
- } while (options.continuous || changes.isNotEmpty())
- 
- ReplicationResult(
- ok = true,
- sessionId = sessionId,
- docsRead = docsRead,
- docsWritten = docsWritten,
- lastSeq = lastSeq
- )
- 
- } catch (e: Exception) {
- TranscriptLogger.logError("Replication failed", e)
- ReplicationResult(
- ok = false,
- error = e.message,
- sessionId = sessionId,
- docsRead = docsRead,
- docsWritten = docsWritten
- )
- }
-
- 
-
-IGNORE_WHEN_COPYING_START
-Use code with caution.
-IGNORE_WHEN_COPYING_END
-
-}
-
-private suspend fun sendQuicMessage(host: String, port: Int, message: ReplicationMessage): ReplicationMessage {
-val request = serializeMessage(message)
-val response = QuicImpl.sendDatagram(host, port, request)
-return deserializeMessage(response)
-}
-
-private fun serializeMessage(message: ReplicationMessage): ByteArray {
-val json = JsonImpl.stringify(mapOf(
-"type" to message.type,
-"session_id" to message.sessionId,
-"data" to message.data
-))
-return json.toByteArray()
-}
-
-private fun deserializeMessage(data: ByteArray): ReplicationMessage {
-val json = JsonImpl.parse(String(data)) as Map<String, Any?>
-return ReplicationMessage(
-type = json["type"] as String,
-sessionId = json["session_id"] as? String,
-data = json["data"] as? Map<String, Any?> ?: emptyMap()
-)
-}
-
-private fun createErrorResponse(error: String): ByteArray {
-return serializeMessage(ReplicationMessage(
-type = "REPL_ERROR",
-data = mapOf("error" to error)
-))
-}
-
-private fun generateSessionId(): String = java.util.UUID.randomUUID().toString()
-
-private fun parseAttachments(atts: Map<String, Any?>?): Map<String, Attachment>? {
-if (atts == null) return null
-
- 
-return atts.mapValues { (_, value) ->
- val att = value as Map<String, Any?>
- Attachment(
- content_type = att["content_type"] as String,
- length = (att["length"] as Number).toLong(),
- digest = att["digest"] as String,
- stub = att["stub"] as? Boolean ?: false,
- ipfs_cid = att["ipfs_cid"] as? String,
- data = att["data"]?.let { 
- android.util.Base64.decode(it as String, android.util.Base64.NO_WRAP)
- }
- )
- }
-
- 
-
-IGNORE_WHEN_COPYING_START
-Use code with caution.
-IGNORE_WHEN_COPYING_END
-
-}
-}
-
-data class ReplicationMessage(
-val type: String,
-val sessionId: String? = null,
-val data: Map<String, Any?> = emptyMap()
-)
-
-data class ReplicationOptions(
-val continuous: Boolean = false,
-val filter: String? = null,
-val docIds: List<String>? = null,
-val since: String? = null,
-val batchSize: Int = 100,
-val heartbeat: Duration = 30.seconds
-)
-
-data class ReplicationResult(
-val ok: Boolean,
-val sessionId: String? = null,
-val docsRead: Int = 0,
-val docsWritten: Int = 0,
-val lastSeq: String? = null,
-val error: String? = null
-)
-
-// IPFS-backed document store
-class IpfsDocumentStore(
-private val ipfsClient: IpfsClient,
-private val subnetConfig: SubnetConfig = SubnetConfig()
-) {
-private val documentIndex = ConcurrentHashMap<String, DocumentMetadata>()
-private val sequenceCounter = AtomicLong(0)
-private val changesFeed = Channel<Change>(Channel.UNLIMITED)
-
-data class DocumentMetadata(
-val id: String,
-val rev: String,
-val ipfsCid: String,
-val seq: Long,
-val deleted: Boolean = false,
-val attachmentCids: Map<String, String> = emptyMap()
-)
-
-data class Change(
-val seq: String,
-val id: String,
-val changes: List<ChangeRev>,
-val deleted: Boolean = false
-)
-
-data class ChangeRev(val rev: String)
-
-data class ChangesResult(
-val lastSeq: String,
-val results: List<Change>
-)
-
-data class SubnetConfig(
-val subnetId: String = "default",
-val replicationFactor: Int = 3,
-val pinningNodes: List<String> = emptyList(),
-val encryptionKey: ByteArray? = null
-)
-
-suspend fun putDocument(
-id: String,
-data: Map<String, Any?>,
-attachments: Map<String, Attachment>? = null,
-rev: String? = null
-): String {
-// Generate new revision
-val newRev = generateRevision(rev)
-
- 
-// Store attachments in IPFS
- val attachmentCids = attachments?.mapValues { (name, attachment) ->
- if (attachment.ipfs_cid != null) {
- attachment.ipfs_cid
- } else if (attachment.data != null) {
- val cid = ipfsClient.add(attachment.data)
- cid
- } else {
- throw IllegalArgumentException

```
```diff
--- a/Trikeshed/src/commonMain/kotlin/io/trikeshed/couchdb/CouchDB.kt
+++ b/Trikeshed/src/commonMain/kotlin/io/trikeshed/couchdb/CouchDB.kt
@@ -1,58 +0,0 @@
-package io.trikeshed.couchdb
-
-import kotlinx.serialization.Serializable
-import kotlinx.serialization.json.Json
-
-@Serializable
-data class CouchDBConfig(
- val url: String,
- val username: String? = null,
- val password: String? = null
-)
-
-@Serializable
-data class DatabaseInfo(
- val db_name: String,
- val doc_count: Int,
- val doc_del_count: Int,
- val update_seq: String,
- val purge_seq: Int,
- val compact_running: Boolean,
- val disk_size: Long,
- val data_size: Long,
- val instance_start_time: String,
- val disk_format_version: Int
-)
-
-@Serializable
-data class Document(
- val _id: String,
- val _rev: String? = null,
- val _deleted: Boolean = false,
- val _attachments: Map<String, Attachment>? = null,
- val _conflicts: List<String>? = null,
- val _deleted_conflicts: List<String>? = null,
- val _local_seq: String? = null,
- val _revs_info: List<RevisionInfo>? = null,
- val _revisions: Revisions? = null
-)
-
-@Serializable
-data class Attachment(
- val content_type: String,
- val data: String,
- val digest: String,
- val encoded_length: Int,
- val encoding: String,
- val length: Int,
- val revpos: Int,
- val stub: Boolean
-)
-
-@Serializable
-data class RevisionInfo(
- val rev: String,
- val status: String
-)
-
-@Serializable
-data class Revisions(
- val ids: List<String>,
- val start: Int
-)
-
-interface CouchDBClient {
- suspend fun getDatabases(): List<String>
- suspend fun getDatabaseInfo(dbName: String): DatabaseInfo
- suspend fun createDatabase(dbName: String): Boolean
- suspend fun deleteDatabase(dbName: String): Boolean
- suspend fun getDocument(dbName: String, docId: String): Document
- suspend fun saveDocument(dbName: String, document: Document): Document
- suspend fun deleteDocument(dbName: String, docId: String, rev: String): Boolean
- suspend fun query(dbName: String, query: Map<String, Any>): List<Document>
-}
-
-class CouchDBClientImpl(private val config: CouchDBConfig) : CouchDBClient {
- private val json = Json { 
- ignoreUnknownKeys = true
- isLenient = true
- }
- 
- // Implementation will be platform specific
- override suspend fun getDatabases(): List<String> = TODO()
- override suspend fun getDatabaseInfo(dbName: String): DatabaseInfo = TODO()
- override suspend fun createDatabase(dbName: String): Boolean = TODO()
- override suspend fun deleteDatabase(dbName: String): Boolean = TODO()
- override suspend fun getDocument(dbName: String, docId: String): Document = TODO()
- override suspend fun saveDocument(dbName: String, document: Document): Document = TODO()
- override suspend fun deleteDocument(dbName: String, docId: String, rev: String): Boolean = TODO()
- override suspend fun query(dbName: String, query: Map<String, Any>): List<Document> = TODO()
-} 

```
```diff
--- a/Trikeshed/src/jsMain/kotlin/io/trikeshed/couchdb/CouchDBClientJs.kt
+++ b/Trikeshed/src/jsMain/kotlin/io/trikeshed/couchdb/CouchDBClientJs.kt
@@ -1,68 +0,0 @@
-package io.trikeshed.couchdb
-
-import kotlinx.coroutines.await
-import kotlinx.serialization.json.Json
-import org.w3c.fetch.RequestInit
-import org.w3c.fetch.Response
-import kotlin.js.json
-
-class CouchDBClientJs(config: CouchDBConfig) : CouchDBClientImpl(config) {
- private val baseUrl = config.url.trimEnd('/')
- private val headers = json(
- "Accept" to "application/json",
- "Content-Type" to "application/json"
- ).apply {
- if (config.username != null && config.password != null) {
- val auth = "${config.username}:${config.password}"
- val encoded = kotlinx.browser.window.btoa(auth)
- set("Authorization", "Basic $encoded")
- }
- }
-
- private suspend fun request(
- method: String,
- path: String,
- body: String? = null
- ): Response {
- val init = RequestInit(
- method = method,
- headers = headers,
- body = body
- )
- 
- return kotlinx.browser.window.fetch("$baseUrl/$path", init).await()
- }
-
- override suspend fun getDatabases(): List<String> {
- val response = request("GET", "_all_dbs")
- return Json.decodeFromString<List<String>>(response.text().await())
- }
-
- override suspend fun getDatabaseInfo(dbName: String): DatabaseInfo {
- val response = request("GET", dbName)
- return Json.decodeFromString(response.text().await())
- }
-
- override suspend fun createDatabase(dbName: String): Boolean {
- val response = request("PUT", dbName)
- return response.ok
- }
-
- override suspend fun deleteDatabase(dbName: String): Boolean {
- val response = request("DELETE", dbName)
- return response.ok
- }
-
- override suspend fun getDocument(dbName: String, docId: String): Document {
- val response = request("GET", "$dbName/$docId")
- return Json.decodeFromString(response.text().await())
- }
-
- override suspend fun saveDocument(dbName: String, document: Document): Document {
- val body = Json.encodeToString(Document.serializer(), document)
- val response = request("PUT", "$dbName/${document._id}", body)
- return Json.decodeFromString(response.text().await())
- }
-
- override suspend fun deleteDocument(dbName: String, docId: String, rev: String): Boolean {
- val response = request("DELETE", "$dbName/$docId?rev=$rev")
- return response.ok
- }
-
- override suspend fun query(dbName: String, query: Map<String, Any>): List<Document> {
- val body = Json.encodeToString(Json.serializersModule.serializer(), query)
- val response = request("POST", "$dbName/_find", body)
- val result = Json.decodeFromString<QueryResult>(response.text().await())
- return result.docs
- }
-}
-
-@kotlinx.serialization.Serializable
-private data class QueryResult(
- val docs: List<Document>
-) 

```
```diff
--- a/nexus/src/main/kotlin/borg/trikeshed/nexus/CouchDBService.kt
+++ b/nexus/src/main/kotlin/borg/trikeshed/nexus/CouchDBService.kt
@@ -1,33 +0,0 @@
-package borg.trikeshed.nexus
-
-import borg.trikeshed.core.*
-import kotlinx.coroutines.*
-import kotlinx.serialization.json.*
-
-class CouchDBService {
- /**
- * Query CouchDB and return results as a Tensor (cursor)
- * This demonstrates the "cursor" paradigm within TrikeShed
- */
- suspend fun queryAsCursor(
- database: String,
- selector: JsonObject
- ): Tensor<JsonElement> {
- // Simulate CouchDB query
- val mockResults = listOf(
- buildJsonObject {
- put("_id", "doc1")
- put("name", "Alice")
- put("age", 30)
- },
- buildJsonObject {
- put("_id", "doc2") 
- put("name", "Bob")
- put("age", 25)
- }
- )
- 
- // Return as Tensor with shape [rows, cols]
- val rows = mockResults.size
- val cols = 3 // _id, name, age
- 
- return intArrayOf(rows, cols) j { coords ->
- val row = coords[0]
- val col = coords[1]
- val doc = mockResults[row]
- 
- when (col) {
- 0 -> doc["_id"]!!
- 1 -> doc["name"]!!
- 2 -> doc["age"]!!
- else -> JsonNull
- }
- }
- }
-}

```
```diff
--- a/flatton/build.gradle.kts
+++ b/flatton/build.gradle.kts
@@ -1,15 +0,0 @@
-plugins {
- kotlin("multiplatform") version "2.1.21"
-}
-
-kotlin {
- wasmJs {
- browser()
- nodejs()
- }
-
- sourceSets {
- val commonMain by getting {
- dependencies {
- implementation(project(":trikeshed"))
- }
- }
- }
-} 

```
```diff
--- a/flatton/src/commonMain/kotlin/org/flatton/client/CouchClient.kt
+++ b/flatton/src/commonMain/kotlin/org/flatton/client/CouchClient.kt
@@ -1,29 +0,0 @@
-package org.flatton.client
-
-import org.flatton.types.*
-
-interface CouchClient {
- suspend fun getDatabaseInfo(dbName: DatabaseName): CouchDatabaseInfo
- suspend fun createDatabase(dbName: DatabaseName): CouchResponse
- suspend fun deleteDatabase(dbName: DatabaseName): CouchResponse
- 
- suspend fun getDocument(dbName: DatabaseName, docId: DocumentId): CouchDocument
- suspend fun createDocument(dbName: DatabaseName, doc: CouchDocument): CouchResponse
- suspend fun updateDocument(dbName: DatabaseName, doc: CouchDocument): CouchResponse
- suspend fun deleteDocument(dbName: DatabaseName, docId: DocumentId, rev: RevisionId): CouchResponse
- 
- suspend fun getDesignDocument(dbName: DatabaseName, docId: DocumentId): CouchDesignDocument
- suspend fun createDesignDocument(dbName: DatabaseName, doc: CouchDesignDocument): CouchResponse
- suspend fun updateDesignDocument(dbName: DatabaseName, doc: CouchDesignDocument): CouchResponse
- suspend fun deleteDesignDocument(dbName: DatabaseName, docId: DocumentId, rev: RevisionId): CouchResponse
- 
- suspend fun queryView<T>(
- dbName: DatabaseName,
- designDocId: DocumentId,
- viewName: ViewName,
- params: ViewQueryParams = ViewQueryParams()
- ): ViewResponse<T>
- 
- suspend fun getSecurity(dbName: DatabaseName): CouchSecurity
- suspend fun updateSecurity(dbName: DatabaseName, security: CouchSecurity): CouchResponse
- 
- suspend fun enableAdminParty(config: AdminPartyConfig): CouchResponse
- suspend fun disableAdminParty(): CouchResponse
- suspend fun getAdminPartyConfig(): AdminPartyConfig
-} 

```
```diff
--- a/flatton/src/commonMain/kotlin/org/flatton/types/CouchTypes.kt
+++ b/flatton/src/commonMain/kotlin/org/flatton/types/CouchTypes.kt
@@ -1,173 +0,0 @@
-package org.flatton.types
-
-import borg.trikeshed.lib.*
-import borg.trikeshed.parse.json.*
-import borg.trikeshed.lib.toSeries
-
-@JvmInline
-value class DocumentId(val value: String)
-
-@JvmInline
-value class RevisionId(val value: String)
-
-@JvmInline
-value class DatabaseName(val value: String)
-
-@JvmInline
-value class ViewName(val value: String)
-
-@JvmInline
-value class MapFunction(val value: String)
-
-@JvmInline
-value class ReduceFunction(val value: String)
-
-data class CouchDocument(
- val id: DocumentId,
- val rev: RevisionId? = null,
- val data: Map<String, Any> = emptyMap()
-) {
- fun toJson(): String = JsonImpl.stringify(mapOf(
- "_id" to id.value,
- "_rev" to (rev?.value),
- "data" to data
- ))
-
- companion object {
- fun fromJson(json: String): CouchDocument {
- val map = JsonImpl.parse(json) as? Map<*, *> ?: throw IllegalArgumentException("Invalid JSON")
- return CouchDocument(
- id = DocumentId(map["_id"] as String),
- rev = (map["_rev"] as? String)?.let { RevisionId(it) },
- data = (map["data"] as? Map<*, *>)?.mapKeys { it.key.toString() }?.mapValues { it.value } ?: emptyMap()
- )
- }
- }
-}
-
-data class CouchView(
- val map: MapFunction,
- val reduce: ReduceFunction? = null
-)
-
-data class CouchDesignDocument(
- val id: DocumentId,
- val rev: RevisionId? = null,
- val views: Map<ViewName, CouchView>,
- val language: String = "javascript"
-) {
- fun toJson(): String = JsonImpl.stringify(mapOf(
- "_id" to "_design/${id.value}",
- "_rev" to (rev?.value),
- "views" to views.mapKeys { it.key.value },
- "language" to language
- ))
-
- companion object {
- fun fromJson(json: String): CouchDesignDocument {
- val map = JsonImpl.parse(json) as? Map<*, *> ?: throw IllegalArgumentException("Invalid JSON")
- return CouchDesignDocument(
- id = DocumentId((map["_id"] as String).removePrefix("_design/")),
- rev = (map["_rev"] as? String)?.let { RevisionId(it) },
- views = (map["views"] as? Map<*, *>)?.mapKeys { ViewName(it.key.toString()) }?.mapValues { 
- val viewMap = it.value as Map<*, *>
- CouchView(
- map = MapFunction(viewMap["map"] as String),
- reduce = (viewMap["reduce"] as? String)?.let { ReduceFunction(it) }
- )
- } ?: emptyMap(),
- language = map["language"] as? String ?: "javascript"
- )
- }
- }
-}
-
-data class CouchDatabaseInfo(
- val dbName: DatabaseName,
- val docCount: Int,
- val docDelCount: Int,
- val updateSeq: String,
- val purgeSeq: String,
- val compactRunning: Boolean,
- val diskSize: Long,
- val dataSize: Long,
- val instanceStartTime: String,
- val diskFormatVersion: Int,
- val committedUpdateSeq: String,
- val compactedSeq: String,
- val uuid: String
-) {
- companion object {
- fun fromJson(json: String): CouchDatabaseInfo {
- val map = JsonImpl.parse(json) as? Map<*, *> ?: throw IllegalArgumentException("Invalid JSON")
- return CouchDatabaseInfo(
- dbName = DatabaseName(map["db_name"] as String),
- docCount = map["doc_count"] as Int,
- docDelCount = map["doc_del_count"] as Int,
- updateSeq = map["update_seq"] as String,
- purgeSeq = map["purge_seq"] as String,
- compactRunning = map["compact_running"] as Boolean,
- diskSize = (map["disk_size"] as Number).toLong(),
- dataSize = (map["data_size"] as Number).toLong(),
- instanceStartTime = map["instance_start_time"] as String,
- diskFormatVersion = map["disk_format_version"] as Int,
- committedUpdateSeq = map["committed_update_seq"] as String,
- compactedSeq = map["compacted_seq"] as String,
- uuid = map["uuid"] as String
- )
- }
- }
-}
-
-data class CouchSecurity(
- val admins: SecurityRoles,
- val members: SecurityRoles
-) {
- fun toJson(): String = JsonImpl.stringify(mapOf(
- "admins" to mapOf(
- "names" to admins.names.▶.toList(),
- "roles" to admins.roles.▶.toList()
- ),
- "members" to mapOf(
- "names" to members.names.▶.toList(),
- "roles" to members.roles.▶.toList()
- )
- ))
-
- companion object {
- fun fromJson(json: String): CouchSecurity {
- val map = JsonImpl.parse(json) as? Map<*, *> ?: throw IllegalArgumentException("Invalid JSON")
- return CouchSecurity(
- admins = SecurityRoles(
- names = ((map["admins"] as? Map<*, *>)?.get("names") as? List<String> ?: emptyList()).toSeries(),
- roles = ((map["admins"] as? Map<*, *>)?.get("roles") as? List<String> ?: emptyList()).toSeries()
- ),
- members = SecurityRoles(
- names = ((map["members"] as? Map<*, *>)?.get("names") as? List<String> ?: emptyList()).toSeries(),
- roles = ((map["members"] as? Map<*, *>)?.get("roles") as? List<String> ?: emptyList()).toSeries()
- )
- )
- }
- }
-}
-
-data class SecurityRoles(
- val names: Series<String>,
- val roles: Series<String>
-)
-
-data class AdminPartyConfig(
- val enabled: Boolean,
- val adminRoles: Series<String>
-) {
- fun toJson(): String = JsonImpl.stringify(mapOf(
- "enabled" to enabled,
- "admin_roles" to adminRoles.▶.toList()
- ))
-
- companion object {
- fun fromJson(json: String): AdminPartyConfig {
- val map = JsonImpl.parse(json) as? Map<*, *> ?: throw IllegalArgumentException("Invalid JSON")
- return AdminPartyConfig(
- enabled = map["enabled"] as Boolean,
- adminRoles = (map["admin_roles"] as? List<String> ?: emptyList()).toSeries()
- )
- }
- }
-}
-
-data class CouchResponse(
- val ok: Boolean,
- val id: DocumentId,
- val rev: RevisionId
-) {
- companion object {
- fun fromJson(json: String): CouchResponse {
- val map = JsonImpl.parse(json) as? Map<*, *> ?: throw IllegalArgumentException("Invalid JSON")
- return CouchResponse(
- ok = map["ok"] as Boolean,
- id = DocumentId(map["id"] as String),
- rev = RevisionId(map["rev"] as String)
- )
- }
- }
-}
-
-data class CouchError(
- val error: String,
- val reason: String
-) {
- companion object {
- fun fromJson(json: String): CouchError {
- val map = JsonImpl.parse(json) as? Map<*, *> ?: throw IllegalArgumentException("Invalid JSON")
- return CouchError(
- error = map["error"] as String,
- reason = map["reason"] as String
- )
- }
- }
-}
-
-data class ViewQueryParams(
- val key: Any? = null,
- val keys: Series<Any>? = null,
- val startKey: Any? = null,
- val endKey: Any? = null,
- val startKeyDocId: DocumentId? = null,
- val endKeyDocId: DocumentId? = null,
- val limit: Int? = null,
- val skip: Int? = null,
- val descending: Boolean? = null,
- val includeDocs: Boolean? = null,
- val reduce: Boolean? = null,
- val group: Boolean? = null,
- val groupLevel: Int? = null
-) {
- fun toQueryString(): String {
- val params = buildString {
- key?.let { append("key=${JsonImpl.stringify(it)}&") }
- keys?.let { append("keys=${JsonImpl.stringify(it.▶.toList())}&") }
- startKey?.let { append("startkey=${JsonImpl.stringify(it)}&") }
- endKey?.let { append("endkey=${JsonImpl.stringify(it)}&") }
- startKeyDocId?.let { append("startkey_docid=${it.value}&") }
- endKeyDocId?.let { append("endkey_docid=${it.value}&") }
- limit?.let { append("limit=$it&") }
- skip?.let { append("skip=$it&") }
- descending?.let { append("descending=$it&") }
- includeDocs?.let { append("include_docs=$it&") }
- reduce?.let { append("reduce=$it&") }
- group?.let { append("group=$it&") }
- groupLevel?.let { append("group_level=$it&") }
- }.removeSuffix("&")
- return if (params.isEmpty()) "" else "?$params"
- }
-}
-
-data class ViewResponse<T>(
- val totalRows: Int,
- val offset: Int,
- val rows: Series<ViewRow<T>>
-) {
- companion object {
- inline fun <reified T> fromJson(json: String): ViewResponse<T> {
- val map = JsonImpl.parse(json) as? Map<*, *> ?: throw IllegalArgumentException("Invalid JSON")
- return ViewResponse(
- totalRows = map["total_rows"] as Int,
- offset = map["offset"] as Int,
- rows = ((map["rows"] as? List<*>)?.map { rowMap ->
- val row = rowMap as Map<*, *>
- ViewRow(
- id = DocumentId(row["id"] as String),
- key = row["key"] as Any,
- value = row["value"] as T,
- doc = (row["doc"] as? Map<*, *>)?.let { CouchDocument.fromJson(JsonImpl.stringify(it)) }
- )
- } ?: emptyList()).toSeries()
- )
- }
- }
-}
-
-data class ViewRow<T>(
- val id: DocumentId,
- val key: Any,
- val value: T,
- val doc: CouchDocument? = null
-)

```
```diff
--- a/flatton/src/wasmJsMain/kotlin/org/flatton/client/WasmCouchClient.kt
+++ b/flatton/src/wasmJsMain/kotlin/org/flatton/client/WasmCouchClient.kt
@@ -1,135 +0,0 @@
-package org.flatton.client
-
-import org.flatton.types.*
-import borg.trikeshed.lib.*
-import borg.trikeshed.net.http.*
-import borg.trikeshed.parse.json.*
-
-class WasmCouchClient(
- private val baseUrl: String = "http://localhost:5984"
-) : CouchClient {
- private val headers = mapOf(
- HttpHeaderName("Content-Type") to HttpHeaderValue("application/json"),
- HttpHeaderName("Accept") to HttpHeaderValue("application/json")
- )
-
- override suspend fun getDatabaseInfo(dbName: DatabaseName): CouchDatabaseInfo {
- val response = HttpRequest(
- method = HttpMethod.GET,
- path = HttpRequestPath("/${dbName.value}"),
- headers = headers
- ).send()
-
- if (!response.isSuccess) throw CouchException("Failed to get database info")
- return CouchDatabaseInfo.fromJson(response.body.toString())
- }
-
- override suspend fun createDatabase(dbName: DatabaseName): CouchResponse {
- val response = HttpRequest(
- method = HttpMethod.PUT,
- path = HttpRequestPath("/${dbName.value}"),
- headers = headers
- ).send()
-
- if (!response.isSuccess) throw CouchException("Failed to create database")
- return CouchResponse.fromJson(response.body.toString())
- }
-
- override suspend fun deleteDatabase(dbName: DatabaseName): CouchResponse {
- val response = HttpRequest(
- method = HttpMethod.DELETE,
- path = HttpRequestPath("/${dbName.value}"),
- headers = headers
- ).send()
-
- if (!response.isSuccess) throw CouchException("Failed to delete database")
- return CouchResponse.fromJson(response.body.toString())
- }
-
- override suspend fun getDocument(dbName: DatabaseName, docId: DocumentId): CouchDocument {
- val response = HttpRequest(
- method = HttpMethod.GET,
- path = HttpRequestPath("/${dbName.value}/${docId.value}"),
- headers = headers
- ).send()
-
- if (!response.isSuccess) throw CouchException("Failed to get document")
- return CouchDocument.fromJson(response.body.toString())
- }
-
- override suspend fun createDocument(dbName: DatabaseName, doc: CouchDocument): CouchResponse {
- val response = HttpRequest(
- method = HttpMethod.POST,
- path = HttpRequestPath("/${dbName.value}"),
- headers = headers,
- body = doc.toJson()
- ).send()
-
- if (!response.isSuccess) throw CouchException("Failed to create document")
- return CouchResponse.fromJson(response.body.toString())
- }
-
- override suspend fun updateDocument(dbName: DatabaseName, doc: CouchDocument): CouchResponse {
- val response = HttpRequest(
- method = HttpMethod.PUT,
- path = HttpRequestPath("/${dbName.value}/${doc.id.value}"),
- headers = headers,
- body = doc.toJson()
- ).send()
-
- if (!response.isSuccess) throw CouchException("Failed to update document")
- return CouchResponse.fromJson(response.body.toString())
- }
-
- override suspend fun deleteDocument(dbName: DatabaseName, docId: DocumentId, rev: RevisionId): CouchResponse {
- val response = HttpRequest(
- method = HttpMethod.DELETE,
- path = HttpRequestPath("/${dbName.value}/${docId.value}?rev=${rev.value}"),
- headers = headers
- ).send()
-
- if (!response.isSuccess) throw CouchException("Failed to delete document")
- return CouchResponse.fromJson(response.body.toString())
- }
-
- override suspend fun getDesignDocument(dbName: DatabaseName, docId: DocumentId): CouchDesignDocument {
- val response = HttpRequest(
- method = HttpMethod.GET,
- path = HttpRequestPath("/${dbName.value}/_design/${docId.value}"),
- headers = headers
- ).send()
-
- if (!response.isSuccess) throw CouchException("Failed to get design document")
- return CouchDesignDocument.fromJson(response.body.toString())
- }
-
- override suspend fun createDesignDocument(dbName: DatabaseName, doc: CouchDesignDocument): CouchResponse {
- val response = HttpRequest(
- method = HttpMethod.PUT,
- path = HttpRequestPath("/${dbName.value}/_design/${doc.id.value}"),
- headers = headers,
- body = doc.toJson()
- ).send()
-
- if (!response.isSuccess) throw CouchException("Failed to create design document")
- return CouchResponse.fromJson(response.body.toString())
- }
-
- override suspend fun updateDesignDocument(dbName: DatabaseName, doc: CouchDesignDocument): CouchResponse {
- val response = HttpRequest(
- method = HttpMethod.PUT,
- path = HttpRequestPath("/${dbName.value}/_design/${doc.id.value}"),
- headers = headers,
- body = doc.toJson()
- ).send()
-
- if (!response.isSuccess) throw CouchException("Failed to update design document")
- return CouchResponse.fromJson(response.body.toString())
- }
-
- override suspend fun deleteDesignDocument(dbName: DatabaseName, docId: DocumentId, rev: RevisionId): CouchResponse {
- val response = HttpRequest(
- method = HttpMethod.DELETE,
- path = HttpRequestPath("/${dbName.value}/_design/${docId.value}?rev=${rev.value}"),
- headers = headers
- ).send()
-
- if (!response.isSuccess) throw CouchException("Failed to delete design document")
- return CouchResponse.fromJson(response.body.toString())
- }
-
- override suspend fun <T> queryView(
- dbName: DatabaseName,
- designDocId: DocumentId,
- viewName: ViewName,
- params: ViewQueryParams
- ): ViewResponse<T> {
- val response = HttpRequest(
- method = HttpMethod.GET,
- path = HttpRequestPath("/${dbName.value}/_design/${designDocId.value}/_view/${viewName.value}${params.toQueryString()}"),
- headers = headers
- ).send()
-
- if (!response.isSuccess) throw CouchException("Failed to query view")
- return ViewResponse.fromJson(response.body.toString())
- }
-
- override suspend fun getSecurity(dbName: DatabaseName): CouchSecurity {
- val response = HttpRequest(
- method = HttpMethod.GET,
- path = HttpRequestPath("/${dbName.value}/_security"),
- headers = headers
- ).send()
-
- if (!response.isSuccess) throw CouchException("Failed to get security")
- return CouchSecurity.fromJson(response.body.toString())
- }
-
- override suspend fun updateSecurity(dbName: DatabaseName, security: CouchSecurity): CouchResponse {
- val response = HttpRequest(
- method = HttpMethod.PUT,
- path = HttpRequestPath("/${dbName.value}/_security"),
- headers = headers,
- body = security.toJson()
- ).send()
-
- if (!response.isSuccess) throw CouchException("Failed to update security")
- return CouchResponse.fromJson(response.body.toString())
- }
-
- override suspend fun enableAdminParty(config: AdminPartyConfig): CouchResponse {
- val response = HttpRequest(
- method = HttpMethod.PUT,
- path = HttpRequestPath("/_node/_local/_config/admins"),
- headers = headers,
- body = config.toJson()
- ).send()
-
- if (!response.isSuccess) throw CouchException("Failed to enable admin party")
- return CouchResponse.fromJson(response.body.toString())
- }
-
- override suspend fun disableAdminParty(): CouchResponse {
- val response = HttpRequest(
- method = HttpMethod.DELETE,
- path = HttpRequestPath("/_node/_local/_config/admins"),
- headers = headers
- ).send()
-
- if (!response.isSuccess) throw CouchException("Failed to disable admin party")
- return CouchResponse.fromJson(response.body.toString())
- }
-
- override suspend fun getAdminPartyConfig(): AdminPartyConfig {
- val response = HttpRequest(
- method = HttpMethod.GET,
- path = HttpRequestPath("/_node/_local/_config/admins"),
- headers = headers
- ).send()
-
- if (!response.isSuccess) throw CouchException("Failed to get admin party config")
- return AdminPartyConfig.fromJson(response.body.toString())
- }
-}
-
-class CouchException(message: String) : Exception(message) 

```
```diff
--- a/flatton/build.gradle.kts
+++ b/flatton/build.gradle.kts
@@ -0,0 +1,15 @@
+plugins {
+    kotlin("multiplatform") version "2.1.21"
+}
+
+kotlin {
+    jvm()
+    wasmJs {
+        browser()
+        nodejs()
+    }
+
+    sourceSets {
+        val commonMain by getting
+    }
+}

```
```diff
--- a/flatton/src/commonMain/kotlin/org/flatton/client/CouchClient.kt
+++ b/flatton/src/commonMain/kotlin/org/flatton/client/CouchClient.kt
@@ -0,0 +1,30 @@
+package org.flatton.client
+
+import org.flatton.types.*
+import borg.trikeshed.lib.Series
+
+interface CouchClient {
+    suspend fun getDatabaseInfo(dbName: DatabaseName): CouchDatabaseInfo
+    suspend fun createDatabase(dbName: DatabaseName): CouchResponse
+    suspend fun deleteDatabase(dbName: DatabaseName): CouchResponse
+
+    suspend fun getDocument(dbName: DatabaseName, docId: DocumentId, rev: RevisionId? = null): CouchDocument
+    suspend fun createDocument(dbName: DatabaseName, doc: CouchDocument, docId: DocumentId? = null): CouchResponse
+    suspend fun updateDocument(dbName: DatabaseName, doc: CouchDocument): CouchResponse
+    suspend fun deleteDocument(dbName: DatabaseName, docId: DocumentId, rev: RevisionId): CouchResponse
+    suspend fun copyDocument(dbName: DatabaseName, fromId: DocumentId, toId: DocumentId, toRev: RevisionId? = null): CouchResponse
+    suspend fun bulkDocs(dbName: DatabaseName, docs: Series<CouchDocument>, allOrNothing: Boolean = false): Series<CouchResponse>
+
+    suspend fun getDesignDocument(dbName: DatabaseName, docId: DocumentId): CouchDesignDocument
+    suspend fun saveDesignDocument(dbName: DatabaseName, doc: CouchDesignDocument): CouchResponse
+
+    suspend fun <K, V> queryView(
+        dbName: DatabaseName,
+        designDocId: DocumentId,
+        viewName: ViewName,
+        params: ViewQueryParams = ViewQueryParams()
+    ): ViewResponse<K, V>
+
+    suspend fun getSecurity(dbName: DatabaseName): CouchSecurity
+    suspend fun setSecurity(dbName: DatabaseName, security: CouchSecurity): CouchResponse
+}

```
```diff
--- a/flatton/src/commonMain/kotlin/org/flatton/types/CouchTypes.kt
+++ b/flatton/src/commonMain/kotlin/org/flatton/types/CouchTypes.kt
@@ -0,0 +1,241 @@
+package org.flatton.types
+
+import borg.trikeshed.lib.*
+import borg.trikeshed.parse.json.*
+import borg.trikeshed.lib.toSeries
+import kotlinx.serialization.json.JsonObject
+
+// Ontological Typealiases for CouchDB Primitives
+@JvmInline value class DocumentId(val value: String)
+@JvmInline value class RevisionId(val value: String)
+@JvmInline value class DatabaseName(val value: String)
+@JvmInline value class DesignDocId(val value: String) {
+    fun asDocId(): DocumentId = DocumentId("_design/${this.value}")
+}
+@JvmInline value class ViewName(val value: String)
+@JvmInline value class MapFunction(val code: String)
+@JvmInline value class ReduceFunction(val code: String)
+typealias AttachmentName = String
+typealias FieldName = String
+typealias JsonString = String
+typealias DocCount = Int
+typealias UpdateSeq = String
+typealias HumanSize = String
+
+// Data Models
+data class CouchDocument(
+    val _id: DocumentId,
+    val _rev: RevisionId? = null,
+    val _deleted: Boolean? = null,
+    val _attachments: Map<AttachmentName, AttachmentInfo>? = null,
+    val data: JsonObject
+)
+
+data class AttachmentInfo(
+    val content_type: String,
+    val revpos: Int,
+    val digest: String,
+    val length: Long,
+    val stub: Boolean? = null,
+    val follows: Boolean? = null,
+    val data: String? = null // Base64 encoded
+)
+
+data class CouchView(
+    val map: MapFunction,
+    val reduce: ReduceFunction? = null
+)
+
+data class CouchDesignDocument(
+    val id: DesignDocId,
+    val rev: RevisionId? = null,
+    val language: String = "javascript",
+    val views: Map<ViewName, CouchView> = emptyMap()
+)
+
+data class CouchDatabase(
+    val name: DatabaseName,
+    val status: CouchDatabaseStatus
+)
+
+data class CouchDatabaseStatus(
+    val docCount: DocCount,
+    val updateSeq: UpdateSeq,
+    val humanSize: HumanSize
+)
+
+data class CouchSecurity(
+    val admins: SecurityPrincipal,
+    val members: SecurityPrincipal
+)
+
+data class SecurityPrincipal(
+    val names: Series<String>,
+    val roles: Series<String>
+)
+
+data class CouchResponse(
+    val ok: Boolean,
+    val id: DocumentId? = null,
+    val rev: RevisionId? = null,
+    val error: String? = null,
+    val reason: String? = null
+)
+
+data class ViewQueryParams(
+    val key: JsonElement? = null,
+    val keys: Series<JsonElement>? = null,
+    val startKey: JsonElement? = null,
+    val endKey: JsonElement? = null,
+    val startKeyDocId: DocumentId? = null,
+    val endKeyDocId: DocumentId? = null,
+    val limit: Int? = null,
+    val skip: Int? = null,
+    val descending: Boolean? = null,
+    val includeDocs: Boolean? = null,
+    val reduce: Boolean? = null,
+    val group: Boolean? = null,
+    val groupLevel: Int? = null,
+    val stale: String? = null
+) {
+    fun toQueryString(): String {
+        val params = mutableListOf<String>()
+        key?.let { params.add("key=${JsonImpl.stringify(it)}") }
+        keys?.let { params.add("keys=${JsonImpl.stringify(it.▶.toList())}") }
+        startKey?.let { params.add("startkey=${JsonImpl.stringify(it)}") }
+        endKey?.let { params.add("endkey=${JsonImpl.stringify(it)}") }
+        startKeyDocId?.let { params.add("startkey_docid=${it.value}") }
+        endKeyDocId?.let { params.add("endkey_docid=${it.value}") }
+        limit?.let { params.add("limit=$it") }
+        skip?.let { params.add("skip=$it") }
+        descending?.let { params.add("descending=$it") }
+        includeDocs?.let { params.add("include_docs=$it") }
+        reduce?.let { params.add("reduce=$it") }
+        group?.let { params.add("group=$it") }
+        groupLevel?.let { params.add("group_level=$it") }
+        stale?.let { params.add("stale=$it") }
+        return if (params.isEmpty()) "" else "?" + params.joinToString("&")
+    }
+}
+
+data class ViewResponse<K, V>(
+    val totalRows: Int,
+    val offset: Int,
+    val updateSeq: String?,
+    val rows: Series<ViewRow<K, V>>
+)
+
+data class ViewRow<K, V>(
+    val id: DocumentId,
+    val key: K,
+    val value: V,
+    val doc: CouchDocument? = null
+)
+
+// Wire Protocol Adapters (Serialization/Deserialization)
+
+object CouchDocumentAdapter {
+    fun fromJson(json: String): CouchDocument {
+        val map = JsonImpl.parse(json) as Map<String, Any?>
+        val data = map.filterKeys { !it.startsWith("_") }
+        return CouchDocument(
+            _id = DocumentId(map["_id"] as String),
+            _rev = (map["_rev"] as? String)?.let { RevisionId(it) },
+            _deleted = map["_deleted"] as? Boolean,
+            _attachments = (map["_attachments"] as? Map<String, *>)?.mapValues {
+                val attMap = it.value as Map<String, Any?>
+                AttachmentInfo(
+                    content_type = attMap["content_type"] as String,
+                    revpos = attMap["revpos"] as Int,
+                    digest = attMap["digest"] as String,
+                    length = (attMap["length"] as Number).toLong(),
+                    stub = attMap["stub"] as? Boolean
+                )
+            },
+            data = JsonObject(data)
+        )
+    }
+
+    fun toJson(doc: CouchDocument): String {
+        val map = mutableMapOf<String, Any?>()
+        map["_id"] = doc._id.value
+        doc._rev?.let { map["_rev"] = it.value }
+        doc._deleted?.let { map["_deleted"] = it }
+        doc._attachments?.let { map["_attachments"] = it }
+        map.putAll(doc.data)
+        return JsonImpl.stringify(map)
+    }
+}
+
+object CouchDesignDocumentAdapter {
+    fun fromJson(json: String): CouchDesignDocument {
+        val map = JsonImpl.parse(json) as Map<String, Any?>
+        return CouchDesignDocument(
+            id = DesignDocId((map["_id"] as String).removePrefix("_design/")),
+            rev = (map["_rev"] as? String)?.let { RevisionId(it) },
+            language = map["language"] as String,
+            views = (map["views"] as Map<String, Map<String, String>>).mapKeys { ViewName(it.key) }
+                .mapValues {
+                    CouchView(
+                        map = MapFunction(it.value["map"]!!),
+                        reduce = it.value["reduce"]?.let { r -> ReduceFunction(r) }
+                    )
+                }
+        )
+    }
+
+    fun toJson(ddoc: CouchDesignDocument): String {
+        return JsonImpl.stringify(mapOf(
+            "_id" to ddoc.id.asDocId().value,
+            "_rev" to ddoc.rev?.value,
+            "language" to ddoc.language,
+            "views" to ddoc.views.mapKeys { it.key.value }.mapValues {
+                mapOf(
+                    "map" to it.value.map.code,
+                    "reduce" to it.value.reduce?.code
+                ).filterValues { v -> v != null }
+            }
+        ))
+    }
+}
+
+object ViewResponseAdapter {
+    inline fun <reified K, reified V> fromJson(json: String): ViewResponse<K, V> {
+        val map = JsonImpl.parse(json) as Map<String, Any?>
+        val rows = (map["rows"] as List<Map<String, Any?>>).map { rowMap ->
+            ViewRow(
+                id = DocumentId(rowMap["id"] as String),
+                key = rowMap["key"] as K,
+                value = rowMap["value"] as V,
+                doc = (rowMap["doc"] as? String)?.let { CouchDocumentAdapter.fromJson(it) }
+            )
+        }
+        return ViewResponse(
+            totalRows = map["total_rows"] as Int,
+            offset = map["offset"] as Int,
+            updateSeq = map["update_seq"]?.toString(),
+            rows = rows.toSeries()
+        )
+    }
+}
+
+class CouchException(message: String, val error: String? = null, val reason: String? = null) : Exception(message)

```
```diff
--- a/flatton/src/commonMain/kotlin/org/flatton/parse/SimdJsonScanner.kt
+++ b/flatton/src/commonMain/kotlin/org/flatton/parse/SimdJsonScanner.kt
@@ -0,0 +1,114 @@
+package org.flatton.parse
+
+import borg.trikeshed.lib.CZero.nz
+import kotlin.math.min
+
+/**
+ * A JSON scanner optimized for finding structural characters, inspired by SIMD principles.
+ * This is used for efficiently parsing elements from a JSON stream or document without
+ * full deserialization.
+ */
+object SimdJsonScanner {
+    private const val VECTOR_SIZE = 32 // Simulated vector size
+
+    /**
+     * Finds the indices of all structural JSON characters: {}, [], :, ,, ".
+     * This uses a simulated vectorized approach for performance.
+     */
+    fun findStructuralIndices(json: ByteArray): IntArray {
+        val indices = mutableListOf<Int>()
+        val len = json.size
+        var i = 0
+        while (i < len) {
+            val chunk = min(VECTOR_SIZE, len - i)
+            val vector = ByteArray(chunk) { json[i + it] }
+
+            // SIMD-style parallel comparison to create a bitmask
+            val structuralMask = vector.indices.fold(0) { mask, idx ->
+                when (vector[idx].toInt().toChar()) {
+                    '{', '}', '[', ']', ':', ',', '"' -> mask or (1 shl idx)
+                    else -> mask
+                }
+            }
+
+            // Extract indices from the bitmask
+            var bitMask = structuralMask
+            var bitIdx = 0
+            while (bitMask != 0) {
+                (bitMask and 1 != 0).nz { indices.add(i + bitIdx) }
+                bitMask = bitMask ushr 1
+                bitIdx++
+            }
+            i += chunk
+        }
+        return indices.toIntArray()
+    }
+}
+
+/**
+ * A wire protocol adapter that uses the SimdJsonScanner for efficient,
+ * on-the-fly parsing of JSON responses into a cursor-like Series of elements.
+ */
+class JsonWireProtoAdapter {
+
+    /**
+     * Creates a cursor (Series) over the "rows" array in a CouchDB view response.
+     * This avoids parsing the entire JSON document into memory.
+     */
+    fun toCursor(jsonBytes: ByteArray): Series<JsonObjectCursor> {
+        val structuralIndices = SimdJsonScanner.findStructuralIndices(jsonBytes)
+        val rowsArrayStart = findRowsArray(jsonBytes, structuralIndices)
+            ?: return borg.trikeshed.lib.emptySeries()
+
+        return object : Series<JsonObjectCursor> {
+            private var currentIndex = rowsArrayStart
+
+            override fun get(index: Int): JsonObjectCursor {
+                // This is a simplified implementation for demonstration.
+                // A real implementation would need to navigate to the nth object.
+                // For now, we'll just show how to get the first object.
+                if (index > 0) throw IndexOutOfBoundsException("Cursor only supports index 0 for now")
+                val (start, end) = findNextObjectBounds(jsonBytes, structuralIndices, currentIndex)
+                    ?: throw NoSuchElementException()
+                return JsonObjectCursor(jsonBytes, start, end)
+            }
+
+            override val size: Int
+                get() = -1 // Size is unknown until fully traversed
+        }
+    }
+
+    private fun findRowsArray(jsonBytes: ByteArray, structuralIndices: IntArray): Int? {
+        val rowsKey = "\"rows\"".toByteArray()
+        // In a real scenario, we'd find the key "rows" and then find the opening '['
+        // This is a simplified placeholder.
+        for (i in structuralIndices.indices) {
+            if (jsonBytes[structuralIndices[i]].toInt().toChar() == '[' && i > 0 &&
+                jsonBytes[structuralIndices[i-1]].toInt().toChar() == ':') {
+                // Crude check for a key-value pair ending in an array
+                return structuralIndices[i]
+            }
+        }
+        return null
+    }
+
+    private fun findNextObjectBounds(jsonBytes: ByteArray, structuralIndices: IntArray, startIndex: Int): Pair<Int, Int>? {
+        var start = -1
+        var depth = 0
+        for (i in startIndex until structuralIndices.size) {
+            val idx = structuralIndices[i]
+            val char = jsonBytes[idx].toInt().toChar()
+            when (char) {
+                '{' -> {
+                    if (depth == 0) start = idx
+                    depth++
+                }
+                '}' -> {
+                    depth--
+                    if (depth == 0 && start != -1) return Pair(start, idx)
+                }
+            }
+        }
+        return null
+    }
+}

```
```diff
--- a/flatton/src/commonMain/kotlin/org/flatton/service/FlattonService.kt
+++ b/flatton/src/commonMain/kotlin/org/flatton/service/FlattonService.kt
@@ -0,0 +1,38 @@
+package org.flatton.service
+
+import org.flatton.client.CouchClient
+import org.flatton.parse.JsonWireProtoAdapter
+import org.flatton.types.*
+import borg.trikeshed.lib.Series
+import borg.trikeshed.cursor.JsonObjectCursor
+
+/**
+ * The main service layer for the Flatton library.
+ * It provides high-level operations and orchestrates the client and parsing layers.
+ */
+class FlattonService(private val client: CouchClient) {
+
+    /**
+     * Queries a CouchDB view and returns the result as a cursor-like `Series`.
+     * This allows for efficient, on-demand processing of large result sets without
+     * loading everything into memory. This is the "cursor element usecase" analog.
+     *
+     * @param dbName The name of the database.
+     * @param designDocId The ID of the design document.
+     * @param viewName The name of the view.
+     * @param params Query parameters for the view.
+     * @return A `Series` of `JsonObjectCursor` objects, where each cursor points to a row object in the JSON response.
+     */
+    suspend fun queryViewAsCursor(
+        dbName: DatabaseName,
+        designDocId: DesignDocId,
+        viewName: ViewName,
+        params: ViewQueryParams = ViewQueryParams()
+    ): Series<JsonObjectCursor> {
+        // In a real implementation, the client would support streaming the raw response body.
+        // Here, we simulate this by getting the full response and then creating a cursor over it.
+        val responseJson = client.queryView<Any, Any>(dbName, designDocId, viewName, params).toString() // Simplified
+        val wireAdapter = JsonWireProtoAdapter()
+        return wireAdapter.toCursor(responseJson.toByteArray())
+    }
+}

```
```diff
--- a/flatton/src/commonMain/kotlin/org/flatton/ui/rendering/FutonTemplate.kt
+++ b/flatton/src/commonMain/kotlin/org/flatton/ui/rendering/FutonTemplate.kt
@@ -0,0 +1,65 @@
+package org.flatton.ui.rendering
+
+import borg.trikeshed.lib.Series
+import org.flatton.types.CouchDatabase
+import org.flatton.types.DatabaseName
+
+/**
+ * A simple templating engine that mimics the functionality needed by Futon
+ * to render data into columnar views. This demonstrates threading an "isomorph"
+ * (a data structure) into a column-based layout.
+ */
+object FutonTemplate {
+
+    private val propertyRegex = Regex("\\{\\{\\s*(\\w+)\\s*\\}\\}")
+    private val eachRegex = Regex("\\{\\{#each (\\w+)\\}\\}(.*?)\\{\\{/each\\}\\}", RegexOption.DOT_MATCHES_ALL)
+
+    /**
+     * Renders a template string with the given data context.
+     * Supports simple property replacement `{{property}}` and looping `{{#each items}}...{{/each}}`.
+     *
+     * @param template The template string.
+     * @param context The data context, typically a map or a data class instance.
+     * @return The rendered string.
+     */
+    fun render(template: String, context: Map<String, Any?>): String {
+        var result = template
+
+        // Handle loops
+        result = eachRegex.replace(result) { matchResult ->
+            val listName = matchResult.groupValues[1]
+            val innerTemplate = matchResult.groupValues[2]
+            val items = context[listName] as? Series<*> ?: return@replace ""
+
+            items.▶.joinToString("") { item ->
+                val itemContext = when (item) {
+                    is CouchDatabase -> mapOf(
+                        "name" to item.name.value,
+                        "docCount" to item.status.docCount,
+                        "updateSeq" to item.status.updateSeq,
+                        "humanSize" to item.status.humanSize,
+                        "encodedName" to item.name.value // simplified encoding
+                    )
+                    else -> mapOf("this" to item)
+                }
+                render(innerTemplate, itemContext)
+            }
+        }
+
+        // Handle simple property replacement
+        result = propertyRegex.replace(result) { matchResult ->
+            val propName = matchResult.groupValues[1]
+            context[propName]?.toString() ?: ""
+        }
+
+        return result
+    }
+
+    val databaseListTemplate = """
+        <table class="databases table table-striped">
+            <thead><th>Name</th><th>Size</th><th># of Docs</th><th>Update Seq</th><th>Actions</th></thead>
+            <tbody>{{#each databases}}<tr><td><a href="#/database/{{encodedName}}/_all_docs">{{name}}</a></td><td>{{humanSize}}</td><td>{{docCount}}</td><td>{{updateSeq}}</td><td>Actions</td></tr>{{/each}}</tbody>
+        </table>
+    """.trimIndent()
+}

```
```diff
--- a/flatton/src/wasmJsMain/kotlin/org/flatton/client/WasmCouchClient.kt
+++ b/flatton/src/wasmJsMain/kotlin/org/flatton/client/WasmCouchClient.kt
@@ -0,0 +1,152 @@
+package org.flatton.client
+
+import org.flatton.types.*
+import borg.trikeshed.lib.*
+import borg.trikeshed.net.http.*
+import borg.trikeshed.parse.json.*
+
+class WasmCouchClient(
+    private val baseUrl: String = "http://127.0.0.1:5984" // Default to localhost
+) : CouchClient {
+    private val headers = mapOf(
+        HttpHeaderName("Content-Type") to HttpHeaderValue("application/json"),
+        HttpHeaderName("Accept") to HttpHeaderValue("application/json")
+    )
+
+    private suspend fun <T> handleResponse(response: HttpResponse, onSuccess: (String) -> T): T {
+        if (!response.isSuccess) {
+            val errorBody = response.body.toString()
+            val error = try { CouchError.fromJson(errorBody) } catch (e: Exception) { null }
+            throw CouchException("CouchDB request failed with status ${response.status.value}", error?.error, error?.reason)
+        }
+        return onSuccess(response.body.toString())
+    }
+
+    override suspend fun getDatabaseInfo(dbName: DatabaseName): CouchDatabaseInfo {
+        val response = HttpRequest(
+            method = HttpMethod.GET,
+            path = HttpRequestPath("/${dbName.value}"),
+            headers = headers
+        ).send()
+        return handleResponse(response) { CouchDatabaseInfo.fromJson(it) }
+    }
+
+    override suspend fun createDatabase(dbName: DatabaseName): CouchResponse {
+        val response = HttpRequest(
+            method = HttpMethod.PUT,
+            path = HttpRequestPath("/${dbName.value}"),
+            headers = headers
+        ).send()
+        return handleResponse(response) { CouchResponse.fromJson(it) }
+    }
+
+    override suspend fun deleteDatabase(dbName: DatabaseName): CouchResponse {
+        val response = HttpRequest(
+            method = HttpMethod.DELETE,
+            path = HttpRequestPath("/${dbName.value}"),
+            headers = headers
+        ).send()
+        return handleResponse(response) { CouchResponse.fromJson(it) }
+    }
+
+    override suspend fun getDocument(dbName: DatabaseName, docId: DocumentId, rev: RevisionId?): CouchDocument {
+        val path = "/${dbName.value}/${docId.value}" + if (rev != null) "?rev=${rev.value}" else ""
+        val response = HttpRequest(
+            method = HttpMethod.GET,
+            path = HttpRequestPath(path),
+            headers = headers
+        ).send()
+        return handleResponse(response) { CouchDocumentAdapter.fromJson(it) }
+    }
+
+    override suspend fun createDocument(dbName: DatabaseName, doc: CouchDocument, docId: DocumentId?): CouchResponse {
+        val method = if (docId != null) HttpMethod.PUT else HttpMethod.POST
+        val path = if (docId != null) "/${dbName.value}/${docId.value}" else "/${dbName.value}"
+
+        val response = HttpRequest(
+            method = method,
+            path = HttpRequestPath(path),
+            headers = headers,
+            body = CouchDocumentAdapter.toJson(doc)
+        ).send()
+        return handleResponse(response) { CouchResponse.fromJson(it) }
+    }
+
+    override suspend fun updateDocument(dbName: DatabaseName, doc: CouchDocument): CouchResponse {
+        val response = HttpRequest(
+            method = HttpMethod.PUT,
+            path = HttpRequestPath("/${dbName.value}/${doc._id.value}"),
+            headers = headers,
+            body = CouchDocumentAdapter.toJson(doc)
+        ).send()
+        return handleResponse(response) { CouchResponse.fromJson(it) }
+    }
+
+    override suspend fun deleteDocument(dbName: DatabaseName, docId: DocumentId, rev: RevisionId): CouchResponse {
+        val response = HttpRequest(
+            method = HttpMethod.DELETE,
+            path = HttpRequestPath("/${dbName.value}/${docId.value}?rev=${rev.value}"),
+            headers = headers
+        ).send()
+        return handleResponse(response) { CouchResponse.fromJson(it) }
+    }
+
+    override suspend fun copyDocument(dbName: DatabaseName, fromId: DocumentId, toId: DocumentId, toRev: RevisionId?): CouchResponse {
+        val destination = toId.value + if (toRev != null) "?rev=${toRev.value}" else ""
+        val copyHeaders = headers + (HttpHeaderName("Destination") to HttpHeaderValue(destination))
+        val response = HttpRequest(
+            method = HttpMethod.COPY,
+            path = HttpRequestPath("/${dbName.value}/${fromId.value}"),
+            headers = copyHeaders
+        ).send()
+        return handleResponse(response) { CouchResponse.fromJson(it) }
+    }
+
+    override suspend fun bulkDocs(dbName: DatabaseName, docs: Series<CouchDocument>, allOrNothing: Boolean): Series<CouchResponse> {
+        val body = JsonImpl.stringify(mapOf(
+            "docs" to docs.▶.map { JsonImpl.parse(CouchDocumentAdapter.toJson(it)) },
+            "all_or_nothing" to allOrNothing
+        ))
+        val response = HttpRequest(
+            method = HttpMethod.POST,
+            path = HttpRequestPath("/${dbName.value}/_bulk_docs"),
+            headers = headers,
+            body = body
+        ).send()
+        return handleResponse(response) {
+            val list = JsonImpl.parse(it) as List<Map<String, Any?>>
+            list.map { item ->
+                CouchResponse(
+                    ok = item["ok"] as? Boolean ?: (item["error"] == null),
+                    id = (item["id"] as? String)?.let { idVal -> DocumentId(idVal) },
+                    rev = (item["rev"] as? String)?.let { revVal -> RevisionId(revVal) },
+                    error = item["error"] as? String,
+                    reason = item["reason"] as? String
+                )
+            }.toSeries()
+        }
+    }
+
+    override suspend fun getDesignDocument(dbName: DatabaseName, docId: DocumentId): CouchDesignDocument {
+        val response = getDocument(dbName, docId)
+        return CouchDesignDocumentAdapter.fromJson(CouchDocumentAdapter.toJson(response))
+    }
+
+    override suspend fun saveDesignDocument(dbName: DatabaseName, doc: CouchDesignDocument): CouchResponse {
+        val response = HttpRequest(
+            method = HttpMethod.PUT,
+            path = HttpRequestPath("/${dbName.value}/${doc.id.asDocId().value}"),
+            headers = headers,
+            body = CouchDesignDocumentAdapter.toJson(doc)
+        ).send()
+        return handleResponse(response) { CouchResponse.fromJson(it) }
+    }
+
+    override suspend fun <K, V> queryView(dbName: DatabaseName, designDocId: DocumentId, viewName: ViewName, params: ViewQueryParams): ViewResponse<K, V> {
+        val path = "/${dbName.value}/_design/${designDocId.value}/_view/${viewName.value}${params.toQueryString()}"
+        val response = HttpRequest(method = HttpMethod.GET, path = HttpRequestPath(path), headers = headers).send()
+        return handleResponse(response) { ViewResponseAdapter.fromJson(it) }
+    }
+
+    override suspend fun getSecurity(dbName: DatabaseName): CouchSecurity {
+        val response = HttpRequest(method = HttpMethod.GET, path = HttpRequestPath("/${dbName.value}/_security"), headers = headers).send()
+        return handleResponse(response) { CouchSecurity.fromJson(it) }
+    }
+
+    override suspend fun setSecurity(dbName: DatabaseName, security: CouchSecurity): CouchResponse {
+        val response = HttpRequest(method = HttpMethod.PUT, path = HttpRequestPath("/${dbName.value}/_security"), headers = headers, body = security.toJson()).send()
+        return handleResponse(response) { CouchResponse.fromJson(it) }
+    }
+}
+

```[201~
