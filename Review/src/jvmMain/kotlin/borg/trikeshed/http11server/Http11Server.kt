package borg.trikeshed.http11server

import borg.trikeshed.net.http.server.HttpConnectionHandler
import borg.trikeshed.nio.services.ActualNioService // JVM actual implementation
import borg.trikeshed.nio.services.NioService
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking

fun main() = runBlocking { // runBlocking to keep the main thread alive for the server
    println("Starting TrikeShed HTTP/1.1 Test Server...")

    // 1. Create the NioService for JVM
    val nioService = ActualNioService()

    // 2. Create a root CoroutineContext for the server
    // Using SupervisorJob so failure of one client connection doesn't bring down the whole server.
    // HttpConnectionHandler itself creates a CoroutineScope with a SupervisorJob,
    // so this top-level one is for the main server setup.
    val serverRootContext = Dispatchers.Default + SupervisorJob() + CoroutineName("Http11ServerMain")

    // 3. Add NioService to the context
    val CCEKContext = serverRootContext + nioService

    // 4. Instantiate HttpConnectionHandler
    val connectionHandler = HttpConnectionHandler(
        nioService = nioService, // Or nioService = CCEKContext[NioService.Key]!!
        CCEKContext = CCEKContext // Pass the context for handler's internal scope
    )

    // 5. Start the server
    val host = "0.0.0.0"
    val port = 8080

    val serverJob = CoroutineScope(CCEKContext).launch {
        try {
            connectionHandler.start(host, port) // This will suspend until server is stopped
        } catch (e: Exception) {
            println("Exception in connectionHandler.start: \${e.message}")
            e.printStackTrace()
        } finally {
            println("Connection handler job finished or cancelled.")
        }
    }

    println("HTTP/1.1 Server listening on \$host:\$port. Press Ctrl+C to stop.")

    // Keep the server running until the job is cancelled (e.g., by Ctrl+C in terminal)
    // In a real application, you'd have a more graceful shutdown mechanism.
    try {
        while (serverJob.isActive && isActive) { // Check both serverJob and runBlocking scope
            kotlinx.coroutines.delay(1000L) // Keep alive, checking status
        }
    } catch (e: CancellationException) {
        println("Main server loop cancelled.")
    } finally {
        println("Shutting down HTTP/1.1 server...")
        connectionHandler.stop() // Signal the handler to stop
        serverJob.cancelAndJoin() // Ensure the server job completes
        // (CCEKContext as? CoroutineScope)?.cancel() // Cancel the context scope if it's a CoroutineScope
        // defaultAsyncChannelGroup in NioChannelsJvm.kt uses a cached thread pool.
        // For a clean shutdown, that thread pool should be shut down.
        // This is an advanced topic usually handled by application lifecycle management.
        // For this test server, letting the JVM exit will terminate daemon threads.
        println("HTTP/1.1 Server shut down complete.")
    }
}
