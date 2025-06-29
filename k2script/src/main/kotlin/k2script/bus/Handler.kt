package k2script.bus

import k2script.engine.Component

/**
 * A component that handles messages sent to a specific address on the bus.
 * Handlers are registered with the central `Registry` and are discovered
 * by the `Router` at dispatch time.
 */
interface Handler : Component {
    /**
     * The unique address that this handler listens on.
     */
    val address: Address

    /**
     * Processes an incoming message. This function is suspendable, allowing for
     * long-running asynchronous operations without blocking the router.
     *
     * The handler is responsible for completing the message's `reply` deferred.
     *
     * @param message The message to process.
     */
    suspend fun handle(message: Message<*, *>)
} 