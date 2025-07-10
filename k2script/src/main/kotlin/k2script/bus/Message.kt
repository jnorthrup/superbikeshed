@file:OptIn(kotlin.kotlin.ExperimentalStdlibApi::class)
package k2script.bus

import borg.trikeshed.lib.j
import borg.trikeshed.lib.Join
import kotlinx.coroutines.CompletableDeferred

/**
 * A unique address for routing messages to handlers.
 * Essentially a topic or a key.
 */
@kotlin.jvm.JvmInline
value class Address(val value: String)

/**
 * The core message type for the router. All communications on the bus are messages.
 * This is a sealed interface to allow for a typed hierarchy of commands, events, and queries.
 *
 * @param A The type of the message payload.
 * @param R The type of the expected reply.
 */
sealed interface Message<A, R> {
    /**
     * The unique address this message is sent to. The router uses this
     * to find the appropriate handler.
     */
    val address: Address

    /**
     * The data payload of the message.
     */
    val payload: A
    
    /**
     * A deferred reply handle. The receiving handler is responsible for completing
     * this deferred value. This allows for asynchronous request-reply patterns.
     */
    val reply: CompletableDeferred<R>
}

/**
 * A command is a message that instructs a component to perform an action.
 * It expects a simple `Boolean` reply to indicate success or failure.
 */
interface Command<A> : Message<A, Boolean>

/**
 * A query is a message that requests data from a component.
 *
 * @param Q The query payload type.
 * @param R The expected result type.
 */
interface Query<Q, R> : Message<Q, R>

/**
 * A simple way to create a fire-and-forget command.
 */
fun <A> commandOf(addr: Address, body: A): Message<A, Boolean> =
    object : Command<A> {
        override val address: Address = addr
        override val payload: A = body
        override val reply: CompletableDeferred<Boolean> = CompletableDeferred()
    }

/**
 * A simple way to create a request-reply query.
 */
fun <Q, R> queryOf(addr: Address, body: Q): Message<Q, R> =
    object : Query<Q, R> {
        override val address: Address = addr
        override val payload: Q = body
        override val reply: CompletableDeferred<R> = CompletableDeferred()
    } 