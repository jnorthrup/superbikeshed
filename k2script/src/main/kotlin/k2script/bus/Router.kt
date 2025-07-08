@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
package k2script.bus

import k2script.bus.Handler
import k2script.bus.Message
import k2script.engine.Registry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The central message router for the k2script engine.
 *
 * It dispatches messages to registered handlers, managing the coroutine
 * lifecycle for each message. This object embodies the "stackable bubbling router"
 * concept, providing a structured, asynchronous, and extensible way to manage
 * inter-component communication.
 */
object Router {
    internal val scope = CoroutineScope(Dispatchers.Default)

    /**
     * Dispatches a message to the appropriate handler and returns the deferred reply.
     *
     * This function is the primary entry point into the bus. It finds a handler
     * registered for the message's address, launches a new coroutine to handle
     * the message, and returns the reply handle to the caller.
     *
     * @param message The message to dispatch.
     * @return The `CompletableDeferred` that will eventually contain the reply.
     */
    fun <A, R> dispatch(message: Message<A, R>): kotlinx.coroutines.CompletableDeferred<R> {
        val handler = Registry.get<Handler>(message.address.value)

        if (handler == null) {
            val error = "No handler registered for address: ${message.address.value}"
            println("Router Error: $error") // Replace with proper logging
            message.reply.completeExceptionally(IllegalStateException(error))
        } else {
            scope.launch {
                try {
                    handler.handle(message)
                } catch (e: Exception) {
                    println("Handler Error: ${e.message}") // Replace with proper logging
                    message.reply.completeExceptionally(e)
                }
            }
        }

        return message.reply
    }
} 