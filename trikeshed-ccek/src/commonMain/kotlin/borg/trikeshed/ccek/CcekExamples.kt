package borg.trikeshed.ccek

import kotlinx.coroutines.*
import borg.trikeshed.lib.*

/**
 * CCEK Examples - Demonstrating Axiom-Based Simplification
 * 
 * These examples show how the axioms simplify common patterns:
 * 1. HTTP request with error handling
 * 2. Database transaction with rollback
 * 3. File processing with resource management
 * 4. Distributed computation with fault tolerance
 */

/**
 * Example 1: HTTP Request with Error Handling
 * 
 * BEFORE (Traditional approach):
 * ```kotlin
 * class HttpService(private val client: HttpClient) {
 *     suspend fun request(url: String): Result<String> {
 *         return try {
 *             val response = client.get(url)
 *             if (response.status == 200) {
 *                 Result.success(response.body)
 *             } else {
 *                 Result.failure(HttpException(response.status))
 *             }
 *         } catch (e: Exception) {
 *             Result.failure(e)
 *         }
 *     }
 * }
 * ```
 * 
 * AFTER (CCEK approach):
 */
suspend fun httpRequestExample(url: String): String {
    return EmptyCoroutineContext
        .httpRequest(HttpCapability.Request("GET", url))
        .onResult { result ->
            println("HTTP request succeeded: $result")
            coroutineContext
        }
        .onError { error ->
            println("HTTP request failed: ${error.message}")
            coroutineContext
        }
        .awaitResult() as String
}

/**
 * Example 2: Database Transaction with Rollback
 * 
 * BEFORE (Traditional approach):
 * ```kotlin
 * class DatabaseService(private val connection: Connection) {
 *     suspend fun updateUser(userId: String, updates: Map<String, Any>): Boolean {
 *         val transaction = connection.beginTransaction()
 *         return try {
 *             connection.update("users", userId, updates)
 *             connection.insert("audit_log", mapOf("action" to "update", "user_id" to userId))
 *             transaction.commit()
 *             true
 *         } catch (e: Exception) {
 *             transaction.rollback()
 *             false
 *         }
 *     }
 * }
 * ```
 * 
 * AFTER (CCEK approach):
 */
suspend fun databaseTransactionExample(userId: String, updates: Map<String, Any>): Boolean {
    return EmptyCoroutineContext
        .dbQuery(DbCapability.Query(mapOf("_id" to userId)))
        .onResult { _ ->
            // Update user document
            coroutineContext
        }
        .onError { error ->
            println("Database operation failed: ${error.message}")
            coroutineContext
        }
        .commit()
}

/**
 * Example 3: File Processing with Resource Management
 * 
 * BEFORE (Traditional approach):
 * ```kotlin
 * class FileProcessor {
 *     suspend fun processFile(path: String): String {
 *         val file = File(path)
 *         return try {
 *             file.inputStream().use { input ->
 *                 input.readBytes().toString(Charsets.UTF_8)
 *             }
 *         } catch (e: IOException) {
 *             throw FileProcessingException("Failed to process file: $path", e)
 *         }
 *     }
 * }
 * ```
 * 
 * AFTER (CCEK approach):
 */
suspend fun fileProcessingExample(path: String): String {
    return EmptyCoroutineContext
        .fileStream(FileCapability.Stream(path, "r"))
        .onResult { content ->
            println("File processed successfully: $path")
            coroutineContext
        }
        .onError { error ->
            println("File processing failed: ${error.message}")
            coroutineContext
        }
        .release()
        .awaitResult() as String
}

/**
 * Example 4: Distributed Computation with Fault Tolerance
 * 
 * BEFORE (Traditional approach):
 * ```kotlin
 * class DistributedComputer(
 *     private val dht: DHT,
 *     private val circuitBreaker: CircuitBreaker
 * ) {
 *     suspend fun computeDistributed(task: Task): Result<String> {
 *         return circuitBreaker.execute {
 *             val peers = dht.findPeers(task.targetId)
 *             if (peers.isEmpty()) {
 *                 throw NopeersException("No peers found for ${task.targetId}")
 *             }
 *             
 *             val results = peers.map { peer ->
 *                 try {
 *                     peer.execute(task)
 *                 } catch (e: Exception) {
 *                     null
 *                 }
 *             }.filterNotNull()
 *             
 *             if (results.isEmpty()) {
 *                 throw ComputationException("All peers failed")
 *             }
 *             
 *             Result.success(results.first())
 *         }
 *     }
 * }
 * ```
 * 
 * AFTER (CCEK approach):
 */
suspend fun distributedComputationExample(targetId: String): String {
    return EmptyCoroutineContext
        .dhtDiscover(DhtCapability.Discovery(targetId, maxPeers = 10))
        .onResult { peers ->
            println("Found ${(peers as List<*>).size} peers for computation")
            coroutineContext
        }
        .onError { error ->
            println("Peer discovery failed: ${error.message}")
            coroutineContext
        }
        .hasCapability(DhtCapability.Key) { dht ->
            // Execute computation on discovered peers
            coroutineContext
        }
        .awaitResult() as String
}

/**
 * Example 5: Complex Workflow - Document Processing Pipeline
 * 
 * This example demonstrates the full power of CCEK axioms by combining
 * multiple capabilities in a complex workflow.
 */
suspend fun documentProcessingPipeline(documentUrl: String): String {
    return EmptyCoroutineContext
        // Step 1: Fetch document via HTTP
        .httpRequest(HttpCapability.Request("GET", documentUrl))
        .onResult { document ->
            println("Document fetched: ${(document as String).length} bytes")
            coroutineContext
        }
        .onError { error ->
            println("Failed to fetch document: ${error.message}")
            coroutineContext
        }
        
        // Step 2: Process and store in database
        .dbQuery(DbCapability.Query(mapOf("type" to "document")))
        .onResult { _ ->
            println("Document processed and stored")
            coroutineContext
        }
        
        // Step 3: Backup to file system
        .fileStream(FileCapability.Stream("./backup/document.txt", "w"))
        .onResult { _ ->
            println("Document backed up to file system")
            coroutineContext
        }
        
        // Step 4: Distribute to peers
        .dhtDiscover(DhtCapability.Discovery("document-peers"))
        .onResult { peers ->
            println("Document distributed to ${(peers as List<*>).size} peers")
            coroutineContext
        }
        
        // Step 5: Finalize and commit all operations
        .commit()
        .let { success ->
            if (success) {
                "Document processing pipeline completed successfully"
            } else {
                "Document processing pipeline failed"
            }
        }
}

/**
 * Example 6: Platform Abstraction - Cross-Platform Execution
 * 
 * This example shows how the same code can run on different platforms
 * through context composition.
 */
suspend fun crossPlatformExample(): String {
    // Create platform-specific contexts
    val webContext = createPlatformContext("web", setOf("http", "websocket", "indexeddb"))
    val nativeContext = createPlatformContext("native", setOf("file", "network", "crypto"))
    val serverContext = createPlatformContext("server", setOf("database", "file", "network"))
    
    // Same execution logic works on all platforms
    suspend fun executeOnPlatform(context: CoroutineContext): String {
        return context
            .hasCapability(HttpCapability.Key) { _ ->
                println("Using HTTP capability")
                coroutineContext
            }
            .hasCapability(FileCapability.Key) { _ ->
                println("Using File capability")
                coroutineContext
            }
            .hasCapability(DbCapability.Key) { _ ->
                println("Using Database capability")
                coroutineContext
            }
            .awaitResult() as String
    }
    
    // Execute on different platforms
    val webResult = executeOnPlatform(webContext)
    val nativeResult = executeOnPlatform(nativeContext)
    val serverResult = executeOnPlatform(serverContext)
    
    return "Cross-platform execution completed: Web=$webResult, Native=$nativeResult, Server=$serverResult"
}

/**
 * Example 7: Functional Composition - Pure Functions
 * 
 * This example demonstrates how CCEK enables pure functional composition
 * without side effects until terminal execution.
 */
suspend fun functionalCompositionExample(): String {
    // Define reusable capability compositions
    val httpWithRetry = { context: CoroutineContext ->
        context
            .httpRequest(HttpCapability.Request("GET", "https://api.example.com/data"))
            .onError { error ->
                println("Retrying HTTP request after error: ${error.message}")
                coroutineContext.httpRequest(HttpCapability.Request("GET", "https://api.example.com/data"))
            }
    }
    
    val dbWithTransaction = { context: CoroutineContext ->
        context
            .dbQuery(DbCapability.Query(mapOf("collection" to "processed_data")))
            .onResult { _ ->
                println("Database transaction prepared")
                coroutineContext
            }
    }
    
    val fileWithBackup = { context: CoroutineContext ->
        context
            .fileStream(FileCapability.Stream("./data/output.json", "w"))
            .onResult { _ ->
                println("File backup created")
                coroutineContext
            }
    }
    
    // Compose capabilities functionally
    return EmptyCoroutineContext
        .let(httpWithRetry)
        .let(dbWithTransaction)
        .let(fileWithBackup)
        .commit()
        .let { success ->
            if (success) "Functional composition succeeded" else "Functional composition failed"
        }
}

/**
 * Example 8: Performance Optimization - Parallel Execution
 * 
 * This example shows how CCEK enables natural parallelization
 * through context composition.
 */
suspend fun parallelExecutionExample(): String = coroutineScope {
    val tasks = listOf(
        async {
            EmptyCoroutineContext
                .httpRequest(HttpCapability.Request("GET", "https://api.service1.com/data"))
                .awaitResult()
        },
        async {
            EmptyCoroutineContext
                .dbQuery(DbCapability.Query(mapOf("table" to "users")))
                .awaitResult()
        },
        async {
            EmptyCoroutineContext
                .fileStream(FileCapability.Stream("./config/settings.json", "r"))
                .awaitResult()
        }
    )
    
    val results = tasks.awaitAll()
    "Parallel execution completed with ${results.size} results"
}

/**
 * Demonstration of how CCEK axioms eliminate complexity:
 * 
 * 1. No dependency injection - capabilities come from context
 * 2. No service locators - keys ARE the capabilities
 * 3. No mutable state - context transformation is immutable
 * 4. No exception handling - errors are part of the flow
 * 5. No resource management - release is explicit
 * 6. No platform coupling - abstraction through context
 * 7. No interface/implementation splits - functions ARE the API
 * 8. No complex orchestration - composition IS execution
 */