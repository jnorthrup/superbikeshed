package borg.trikeshed.reactor.quic

import kotlinx.coroutines.*
import borg.trikeshed.reactor.*
import borg.trikeshed.net.socks.*
import borg.trikeshed.lib.*

/**
 * QUIC Channel implementation for reactor system
 */
class QuicChannel(
    private val ingressChannel: SocksIngressChannel? = null,
    private val egressChannel: SocksEgressChannel? = null
) : borg.trikeshed.reactor.QuicChannel {
    
    override fun createQuicContext(): CoroutineContext {
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
        // Close QUIC channels
    }
    
    override fun isOpen(): Boolean = true
    
    override fun register(selector: SelectorInterface, interest: Int, attachment: Any?): SelectionKey {
        return selector.register(this, interest, attachment)
    }
}

/**
 * QUIC Channel Factory
 */
object QuicChannelFactory {
    fun createBidirectionalChannel(
        ingressChannel: SocksIngressChannel,
        egressChannel: SocksEgressChannel
    ): QuicChannel {
        return QuicChannel(ingressChannel, egressChannel)
    }
    
    fun createIngressOnlyChannel(ingressChannel: SocksIngressChannel): QuicChannel {
        return QuicChannel(ingressChannel, null)
    }
    
    fun createEgressOnlyChannel(egressChannel: SocksEgressChannel): QuicChannel {
        return QuicChannel(null, egressChannel)
    }
    
    fun createDefaultChannel(): QuicChannel {
        return QuicChannel()
    }
} 