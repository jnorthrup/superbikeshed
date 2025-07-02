package borg.trikeshed.reactor.http

import kotlinx.coroutines.*
import borg.trikeshed.reactor.*
import borg.trikeshed.net.socks.*
import borg.trikeshed.lib.*

/**
 * HTTP Channel implementation for reactor system
 */
class HttpChannel(
    private val ingressChannel: SocksIngressChannel? = null,
    private val egressChannel: SocksEgressChannel? = null
) : borg.trikeshed.reactor.HttpChannel {
    
    override fun createHttpContext(): CoroutineContext {
        var context = Dispatchers.IO
        
        if (ingressChannel != null) {
            context = context.withSocksIngress(ingressChannel)
        }
        
        if (egressChannel != null) {
            context = context.withSocksEgress(egressChannel)
        }
        
        return context
    }
    
    override fun close() {
        // Close HTTP channels
    }
    
    override fun isOpen(): Boolean = true
    
    override fun register(selector: SelectorInterface, interest: Int, attachment: Any?): SelectionKey {
        return selector.register(this, interest, attachment)
    }
}

/**
 * HTTP Channel Factory
 */
object HttpChannelFactory {
    fun createBidirectionalChannel(
        ingressChannel: SocksIngressChannel,
        egressChannel: SocksEgressChannel
    ): HttpChannel {
        return HttpChannel(ingressChannel, egressChannel)
    }
    
    fun createIngressOnlyChannel(ingressChannel: SocksIngressChannel): HttpChannel {
        return HttpChannel(ingressChannel, null)
    }
    
    fun createEgressOnlyChannel(egressChannel: SocksEgressChannel): HttpChannel {
        return HttpChannel(null, egressChannel)
    }
    
    fun createDefaultChannel(): HttpChannel {
        return HttpChannel()
    }
} 