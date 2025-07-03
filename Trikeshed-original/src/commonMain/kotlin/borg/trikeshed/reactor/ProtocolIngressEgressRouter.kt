package borg.trikeshed.reactor

import kotlinx.coroutines.*
import borg.trikeshed.lib.*
import borg.trikeshed.net.socks.*
import borg.trikeshed.reactor.socks.*
import borg.trikeshed.reactor.http.*
import borg.trikeshed.reactor.quic.*
import borg.trikeshed.reactor.ipc.*

/**
 * Protocol Ingress/Egress Router
 * 
 * Uses MetaSeries chord sheets to route protocol ingress/egress traffic
 * through appropriate context selections and channel bindings.
 */
class ProtocolIngressEgressRouter(
    private val contextService: ProtocolChannelContextService = ProtocolChannelContextService()
) {
    
    // === INGRESS/EGRESS ROUTING CHORD SHEET ===
    
    // Ingress routing chord - maps ingress events to routing functions
    private val ingressRoutingChord: MetaSeries<IngressEvent, (CoroutineContext) -> IngressRoute> =
        IngressEvent.DATA(Indexed()) j { event ->
            when (event) {
                is IngressEvent.DATA -> { context ->
                    when {
                        context.socksIngress != null -> IngressRoute.SOCKS(context, event.data)
                        context.socksEgress != null -> IngressRoute.SOCKS(context, event.data)
                        else -> IngressRoute.DEFAULT(context, event.data)
                    }
                }
                is IngressEvent.CONTROL -> { context ->
                    IngressRoute.CONTROL(context, event.command)
                }
                is IngressEvent.ERROR -> { context ->
                    IngressRoute.ERROR(context, event.error)
                }
            }
        }
    
    // Egress routing chord - maps egress events to routing functions
    private val egressRoutingChord: MetaSeries<EgressEvent, (CoroutineContext) -> EgressRoute> =
        EgressEvent.DATA(Indexed()) j { event ->
            when (event) {
                is EgressEvent.DATA -> { context ->
                    when {
                        context.socksEgress != null -> EgressRoute.SOCKS(context, event.data)
                        context.socksIngress != null -> EgressRoute.SOCKS(context, event.data)
                        else -> EgressRoute.DEFAULT(context, event.data)
                    }
                }
                is EgressEvent.CONTROL -> { context ->
                    EgressRoute.CONTROL(context, event.command)
                }
                is EgressEvent.ERROR -> { context ->
                    EgressRoute.ERROR(context, event.error)
                }
            }
        }
    
    // Context selection chord - maps routing decisions to context selection
    private val contextSelectionChord: MetaSeries<Join<IngressRoute, EgressRoute>, () -> CoroutineContext> =
        (IngressRoute.DEFAULT(Dispatchers.IO, Indexed()) j EgressRoute.DEFAULT(Dispatchers.IO, Indexed())) j { (ingress, egress) ->
            when {
                ingress is IngressRoute.SOCKS && egress is EgressRoute.SOCKS -> {
                    { ingress.context + egress.context }
                }
                ingress is IngressRoute.SOCKS -> {
                    { ingress.context }
                }
                egress is EgressRoute.SOCKS -> {
                    { egress.context }
                }
                else -> {
                    { Dispatchers.IO }
                }
            }
        }
    
    // Channel binding chord - maps context to channel binding functions
    private val channelBindingChord: MetaSeries<CoroutineContext, () -> ChannelBinding> =
        Dispatchers.IO j { context ->
            when {
                context.socksIngress != null && context.socksEgress != null -> {
                    { ChannelBinding.BIDIRECTIONAL(context.socksIngress!!, context.socksEgress!!) }
                }
                context.socksIngress != null -> {
                    { ChannelBinding.INGRESS_ONLY(context.socksIngress!!) }
                }
                context.socksEgress != null -> {
                    { ChannelBinding.EGRESS_ONLY(context.socksEgress!!) }
                }
                else -> {
                    { ChannelBinding.NONE }
                }
            }
        }
    
    // === PUBLIC API ===
    
    /**
     * Route ingress event using MetaSeries chord
     */
    fun routeIngress(event: IngressEvent, context: CoroutineContext): IngressRoute {
        return ingressRoutingChord.b(event)(context)
    }
    
    /**
     * Route egress event using MetaSeries chord
     */
    fun routeEgress(event: EgressEvent, context: CoroutineContext): EgressRoute {
        return egressRoutingChord.b(event)(context)
    }
    
    /**
     * Select context for routing using MetaSeries chord
     */
    fun selectRoutingContext(ingressRoute: IngressRoute, egressRoute: EgressRoute): CoroutineContext {
        return contextSelectionChord.b(ingressRoute j egressRoute)()
    }
    
    /**
     * Bind channels using MetaSeries chord
     */
    fun bindChannels(context: CoroutineContext): ChannelBinding {
        return channelBindingChord.b(context)()
    }
    
    /**
     * Process ingress data through protocol routing
     */
    suspend fun processIngress(
        data: Indexed<Byte>,
        protocol: String,
        context: CoroutineContext
    ): IngressResult {
        val ingressEvent = IngressEvent.DATA(data)
        val ingressRoute = routeIngress(ingressEvent, context)
        val channelBinding = bindChannels(context)
        
        return when (ingressRoute) {
            is IngressRoute.SOCKS -> {
                val ingress = channelBinding.ingressChannel
                if (ingress != null) {
                    val received = ingress.receive()
                    IngressResult.SUCCESS(received)
                } else {
                    IngressResult.ERROR("No ingress channel available")
                }
            }
            is IngressRoute.CONTROL -> {
                IngressResult.CONTROL(ingressRoute.command)
            }
            is IngressRoute.ERROR -> {
                IngressResult.ERROR(ingressRoute.error)
            }
            is IngressRoute.DEFAULT -> {
                IngressResult.SUCCESS(ingressRoute.data)
            }
        }
    }
    
    /**
     * Process egress data through protocol routing
     */
    suspend fun processEgress(
        data: Indexed<Byte>,
        protocol: String,
        context: CoroutineContext
    ): EgressResult {
        val egressEvent = EgressEvent.DATA(data)
        val egressRoute = routeEgress(egressEvent, context)
        val channelBinding = bindChannels(context)
        
        return when (egressRoute) {
            is EgressRoute.SOCKS -> {
                val egress = channelBinding.egressChannel
                if (egress != null) {
                    val sent = egress.send(data)
                    EgressResult.SUCCESS(sent)
                } else {
                    EgressResult.ERROR("No egress channel available")
                }
            }
            is EgressRoute.CONTROL -> {
                EgressResult.CONTROL(egressRoute.command)
            }
            is EgressRoute.ERROR -> {
                EgressResult.ERROR(egressRoute.error)
            }
            is EgressRoute.DEFAULT -> {
                EgressResult.SUCCESS(egressRoute.data.size)
            }
        }
    }
}

// === INGRESS/EGRESS EVENT TYPES ===

sealed class IngressEvent {
    data class DATA(val data: Indexed<Byte>) : IngressEvent()
    data class CONTROL(val command: String) : IngressEvent()
    data class ERROR(val error: String) : IngressEvent()
}

sealed class EgressEvent {
    data class DATA(val data: Indexed<Byte>) : EgressEvent()
    data class CONTROL(val command: String) : EgressEvent()
    data class ERROR(val error: String) : EgressEvent()
}

// === ROUTING TYPES ===

sealed class IngressRoute(val context: CoroutineContext) {
    data class SOCKS(val context: CoroutineContext, val data: Indexed<Byte>) : IngressRoute(context)
    data class CONTROL(val context: CoroutineContext, val command: String) : IngressRoute(context)
    data class ERROR(val context: CoroutineContext, val error: String) : IngressRoute(context)
    data class DEFAULT(val context: CoroutineContext, val data: Indexed<Byte>) : IngressRoute(context)
}

sealed class EgressRoute(val context: CoroutineContext) {
    data class SOCKS(val context: CoroutineContext, val data: Indexed<Byte>) : EgressRoute(context)
    data class CONTROL(val context: CoroutineContext, val command: String) : EgressRoute(context)
    data class ERROR(val context: CoroutineContext, val error: String) : EgressRoute(context)
    data class DEFAULT(val context: CoroutineContext, val data: Indexed<Byte>) : EgressRoute(context)
}

// === CHANNEL BINDING TYPES ===

sealed class ChannelBinding {
    object NONE : ChannelBinding()
    data class INGRESS_ONLY(val ingressChannel: SocksIngressChannel) : ChannelBinding()
    data class EGRESS_ONLY(val egressChannel: SocksEgressChannel) : ChannelBinding()
    data class BIDIRECTIONAL(
        val ingressChannel: SocksIngressChannel,
        val egressChannel: SocksEgressChannel
    ) : ChannelBinding()
}

// === RESULT TYPES ===

sealed class IngressResult {
    data class SUCCESS(val data: Indexed<Byte>) : IngressResult()
    data class CONTROL(val command: String) : IngressResult()
    data class ERROR(val error: String) : IngressResult()
}

sealed class EgressResult {
    data class SUCCESS(val bytesSent: Int) : EgressResult()
    data class CONTROL(val command: String) : EgressResult()
    data class ERROR(val error: String) : EgressResult()
} 