package borg.trikeshed.net.http

import borg.trikeshed.ccek.HttpService
import borg.trikeshed.reactor.ServerChannel
import borg.trikeshed.reactor.PlatformIO
import kotlin.coroutines.CoroutineContext

// HttpServer now implements the HttpService CCEK interface
class CcekHttpServer(
    private val config: HttpServerConfig,
    private val handler: suspend (HttpRequest, CoroutineContext) -> HttpResponse
) : HttpService {

    override val key: CoroutineContext.Key<*> get() = HttpService.Key

    private lateinit var serverChannel: ServerChannel

    override suspend fun start() {
        serverChannel = PlatformIO.create().createServerChannel().apply {
            configureBlocking(false)
            bind(config.port.value)
        }
        
        // Simplified implementation for now
        println("TrikeShed HTTP Server started on ${config.host.value}:${config.port.value}")
    }

    override suspend fun stop() {
        if (::serverChannel.isInitialized) {
            serverChannel.close()
            println("TrikeShed HTTP Server stopped.")
        }
    }
}