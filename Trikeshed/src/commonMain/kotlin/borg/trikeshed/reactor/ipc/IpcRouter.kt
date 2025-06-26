@file:OptIn(ExperimentalUnsignedTypes::class)
package borg.trikeshed.reactor.ipc


import borg.trikeshed.reactor.currentTimeMillis
import borg.trikeshed.reactor.*
import borg.trikeshed.lib.*
import kotlin.coroutines.CoroutineContext

class IpcRouter(
    private val parent: IpcRouter? = null
) : Reactor<IpcEvent>("ipc-router") {
    private val handlers = mutableMapOf<String, MutableList<suspend (IpcEvent, CoroutineContext) -> Unit>>()

    fun on(type: String, handler: suspend (IpcEvent, CoroutineContext) -> Unit) {
        handlers.getOrPut(type) { mutableListOf() }.add(handler)
    }

    suspend fun route(event: IpcEvent, context: CoroutineContext) {
        var handled = false
        handlers[event.type]?.forEach { handler ->
            handler(event, context)
            handled = true
        }
        if (!handled && parent != null) {
            parent.route(event, context) // bubble context up
        }
    }
    // TODO: Handler registration accepting context
}

data class IpcEvent(
    val type: String,
    val data: Any? = null
) 