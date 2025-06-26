package borg.trikeshed.reactor.http

import borg.trikeshed.reactor.currentTimeMillis
import borg.trikeshed.reactor.*
import borg.trikeshed.lib.*
import kotlin.coroutines.CoroutineContext

class Http2ProxyReactor(
    private val context: HttpServerContext,
    private val listenPort: Int = 3128,
    private val listenAddress: String = "0.0.0.0"
) : Reactor<Http2ProxyEvent>("http2-proxy") {
    // TODO: Bind to listenAddress:listenPort using context.ioModel
    // TODO: Accept connections, parse HTTP/2 frames, and forward as proxy
    // TODO: Emit EventType.CONNECT, EventType.DATA, EventType.ERROR, etc.
    // TODO: Integrate with ReactorNetwork for event routing
}

data class Http2ProxyEvent(
    val type: String = "",
    val data: Any? = null,
    val error: String? = null
) 