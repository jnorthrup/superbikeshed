package borg.trikeshed.net.http

import borg.trikeshed.lib.*
import kotlinx.serialization.Serializable

/**
 * A type-safe batched HTTP client that follows TrikeShed patterns.
 * Commands are collected and sent in a single HTTP request.
 */
class BatchClient(
    private val host: HttpServerHost,
    private val port: HttpServerPort
) {
    // Command queue using Series for zero-copy batch assembly
    private var commandQueue = 0 j { Command(CommandType.Noop, "", "") }
    private var callbackQueue = 0 j { { _: Response -> Unit } }
    
    @Serializable
    enum class CommandType {
        Noop, FindDeal, FindDealsByProduct, PersistDeal, GetVendors
    }

    @Serializable
    data class Command(
        val type: CommandType,
        val path: String,
        val payload: String
    )

    @Serializable
    data class Response(
        val success: Boolean,
        val data: String,
        val error: String? = null
    )

    /**
     * Queue a command for batched execution
     */
    fun queueCommand(type: CommandType, path: String, payload: String, callback: (Response) -> Unit) {
        val newCommand = Command(type, path, payload)
        val newSize = commandQueue.size + 1
        
        commandQueue = newSize j { i -> 
            if (i < commandQueue.size) commandQueue[i] else newCommand 
        }
        
        callbackQueue = newSize j { i ->
            if (i < callbackQueue.size) callbackQueue[i] else callback
        }
    }

    /**
     * Execute all queued commands in a single HTTP request
     */
    suspend fun fire() {
        if (commandQueue.size == 0) return

        // Take snapshot of queues
        val commands = commandQueue
        val callbacks = callbackQueue

        // Clear queues for next batch
        commandQueue = 0 j { Command(CommandType.Noop, "", "") }
        callbackQueue = 0 j { { _: Response -> Unit } }

        // Build batch request using our HttpRequest model
        val request = HttpRequest(
            method = HttpMethod.POST,
            path = HttpRequestPath("/api/batch"),
            headers = 2 j { i -> when(i) {
                0 -> HttpHeaderName("Content-Type") j HttpHeaderValue("application/json")
                1 -> HttpHeaderName("Accept") j HttpHeaderValue("application/json")
                else -> throw IndexOutOfBoundsException()
            }},
            body = JsonSerializer.serializeCommands(commands).encodeToByteArray().toSeries(),
            version = HttpVersion("HTTP/1.1")
        )

        try {
            // Send request using our HTTP client
            val response = request.send()
            
            // Parse response and dispatch to callbacks
            val responses = JsonParser.parseResponses(response.body.▶.toByteArray().decodeToString())
            responses.▶.forEachIndexed { index, response ->
                callbacks[index](response)
            }
        } catch (e: Exception) {
            // If batch fails, notify all callbacks
            commands.▶.forEachIndexed { index, _ ->
                callbacks[index](Response(false, "", "Batch request failed: ${e.message}"))
            }
        }
    }

    // Convenience methods for common operations
    fun findDeal(id: String, callback: (Result<DealProxy?>) -> Unit) {
        queueCommand(
            CommandType.FindDeal,
            "/api/deals/$id",
            "",
            { response ->
                if (response.success) {
                    callback(Result.success(JsonParser.parseDeal(response.data)))
                } else {
                    callback(Result.failure(Exception(response.error ?: "Unknown error")))
                }
            }
        )
    }

    fun findDealsByProduct(query: String, callback: (Result<Series<DealProxy>>) -> Unit) {
        queueCommand(
            CommandType.FindDealsByProduct,
            "/api/deals",
            query,
            { response ->
                if (response.success) {
                    callback(Result.success(JsonParser.parseDeals(response.data)))
                } else {
                    callback(Result.failure(Exception(response.error ?: "Unknown error")))
                }
            }
        )
    }
}

// Example usage:
/*
suspend fun main() {
    val client = BatchClient(HttpServerHost("localhost"), HttpServerPort(8080))

    // Queue multiple operations - no I/O yet
    client.findDeal("deal1") { result ->
        result.onSuccess { deal -> println("Found deal: $deal") }
        result.onFailure { error -> println("Error: $error") }
    }

    client.findDealsByProduct("widget") { result ->
        result.onSuccess { deals -> 
            deals.▶.forEach { deal -> println("Found product deal: $deal") }
        }
        result.onFailure { error -> println("Error: $error") }
    }

    // Single HTTP request for all operations
    client.fire()
}
*/ 