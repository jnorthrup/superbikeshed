package borg.trikeshed.reactor

import kotlinx.coroutines.*
import borg.trikeshed.lib.*
import borg.trikeshed.net.socks.*
import borg.trikeshed.reactor.socks.*
import borg.trikeshed.reactor.http.*
import borg.trikeshed.reactor.quic.*
import borg.trikeshed.reactor.ipc.*

/**
 * Protocol Channel Context Selection Service
 * 
 * Uses MetaSeries chord sheets to manage protocol ingress/egress context selection
 * and routing. Each protocol layer is a "chord" that can be composed into complex
 * protocol assemblies.
 */
class ProtocolChannelContextService {
    
    // === PROTOCOL CHANNEL CONTEXT SELECTION CHORD SHEET ===
    
    // Channel type to context factory chord
    private val channelContextChord: MetaSeries<SelectableChannel, () -> CoroutineContext> =
        object : SelectableChannel {} j { channel ->
            when (channel) {
                is SocksChannel -> { { createSocksContext(channel) } }
                is HttpChannel -> { { createHttpContext(channel) } }
                is QuicChannel -> { { createQuicContext(channel) } }
                is IpcChannel -> { { createIpcContext(channel) } }
                else -> { { Dispatchers.IO } }
            }
        }
    
    // Protocol routing chord - maps protocol types to routing strategies
    private val protocolRoutingChord: MetaSeries<String, (CoroutineContext) -> ProtocolRoute> =
        "socks" j { protocol ->
            when (protocol) {
                "socks" -> { context -> ProtocolRoute.SOCKS(context) }
                "http" -> { context -> ProtocolRoute.HTTP(context) }
                "quic" -> { context -> ProtocolRoute.QUIC(context) }
                "ipc" -> { context -> ProtocolRoute.IPC(context) }
                else -> { context -> ProtocolRoute.DEFAULT(context) }
            }
        }
    
    // Ingress channel selection chord
    private val ingressSelectionChord: MetaSeries<ProtocolRoute, () -> SocksIngressChannel?> =
        ProtocolRoute.DEFAULT(Dispatchers.IO) j { route ->
            when (route) {
                is ProtocolRoute.SOCKS -> { { route.context.socksIngress } }
                is ProtocolRoute.HTTP -> { { route.context.socksIngress } }
                is ProtocolRoute.QUIC -> { { route.context.socksIngress } }
                is ProtocolRoute.IPC -> { { route.context.socksIngress } }
                is ProtocolRoute.DEFAULT -> { { null } }
            }
        }
    
    // Egress channel selection chord
    private val egressSelectionChord: MetaSeries<ProtocolRoute, () -> SocksEgressChannel?> =
        ProtocolRoute.DEFAULT(Dispatchers.IO) j { route ->
            when (route) {
                is ProtocolRoute.SOCKS -> { { route.context.socksEgress } }
                is ProtocolRoute.HTTP -> { { route.context.socksEgress } }
                is ProtocolRoute.QUIC -> { { route.context.socksEgress } }
                is ProtocolRoute.IPC -> { { route.context.socksEgress } }
                is ProtocolRoute.DEFAULT -> { { null } }
            }
        }
    
    // Context composition chord
    private val contextCompositionChord: MetaSeries<Join<CoroutineContext, CoroutineContext>, () -> CoroutineContext> =
        (Dispatchers.IO j Dispatchers.Default) j { (base, additional) ->
            { base + additional }
        }
    
    // === PUBLIC API ===
    
    /**
     * Select context for a channel using MetaSeries chord
     */
    fun selectChannelContext(channel: SelectableChannel): CoroutineContext {
        return channelContextChord.b(channel)()
    }
    
    /**
     * Route protocol using MetaSeries chord
     */
    fun routeProtocol(protocol: String, context: CoroutineContext): ProtocolRoute {
        return protocolRoutingChord.b(protocol)(context)
    }
    
    /**
     * Select ingress channel using MetaSeries chord
     */
    fun selectIngressChannel(route: ProtocolRoute): SocksIngressChannel? {
        return ingressSelectionChord.b(route)()
    }
    
    /**
     * Select egress channel using MetaSeries chord
     */
    fun selectEgressChannel(route: ProtocolRoute): SocksEgressChannel? {
        return egressSelectionChord.b(route)()
    }
    
    /**
     * Compose contexts using MetaSeries chord
     */
    fun composeContexts(base: CoroutineContext, additional: CoroutineContext): CoroutineContext {
        return contextCompositionChord.b(base j additional)()
    }
    
    /**
     * Create protocol-specific context with ingress/egress channels
     */
    fun createProtocolContext(
        protocol: String,
        ingressChannel: SocksIngressChannel? = null,
        egressChannel: SocksEgressChannel? = null
    ): CoroutineContext {
        val baseContext = Dispatchers.IO
        val protocolRoute = routeProtocol(protocol, baseContext)
        
        var context = baseContext
        
        if (ingressChannel != null) {
            context = context.withSocksIngress(ingressChannel)
        }
        
        if (egressChannel != null) {
            context = context.withSocksEgress(egressChannel)
        }
        
        return context
    }
    
    // === PRIVATE CONTEXT CREATION FUNCTIONS ===
    
    private fun createSocksContext(channel: SocksChannel): CoroutineContext {
        return Dispatchers.IO + channel.createSocksContext()
    }
    
    private fun createHttpContext(channel: HttpChannel): CoroutineContext {
        return Dispatchers.IO + channel.createHttpContext()
    }
    
    private fun createQuicContext(channel: QuicChannel): CoroutineContext {
        return Dispatchers.IO + channel.createQuicContext()
    }
    
    private fun createIpcContext(channel: IpcChannel): CoroutineContext {
        return Dispatchers.IO + channel.createIpcContext()
    }
}

/**
 * Protocol routing sealed class for type-safe routing
 */
sealed class ProtocolRoute(val context: CoroutineContext) {
    class SOCKS(context: CoroutineContext) : ProtocolRoute(context)
    class HTTP(context: CoroutineContext) : ProtocolRoute(context)
    class QUIC(context: CoroutineContext) : ProtocolRoute(context)
    class IPC(context: CoroutineContext) : ProtocolRoute(context)
    class DEFAULT(context: CoroutineContext) : ProtocolRoute(context)
}

/**
 * Extension functions for channel context creation
 */
interface SocksChannel : SelectableChannel {
    fun createSocksContext(): CoroutineContext
}

interface HttpChannel : SelectableChannel {
    fun createHttpContext(): CoroutineContext
}

interface QuicChannel : SelectableChannel {
    fun createQuicContext(): CoroutineContext
}

interface IpcChannel : SelectableChannel {
    fun createIpcContext(): CoroutineContext
} 