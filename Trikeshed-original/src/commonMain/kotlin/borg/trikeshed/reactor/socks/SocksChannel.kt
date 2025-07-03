package borg.trikeshed.reactor.socks

import kotlinx.coroutines.*
import borg.trikeshed.reactor.*
import borg.trikeshed.net.socks.*
import borg.trikeshed.lib.*

/**
 * SOCKS Channel implementation for reactor system
 */
class SocksChannel(
    private val ingressChannel: SocksIngressChannel,
    private val egressChannel: SocksEgressChannel
) : borg.trikeshed.reactor.SocksChannel {
    
    override fun createSocksContext(): CoroutineContext {
        return Dispatchers.IO
            .withSocksIngress(ingressChannel)
            .withSocksEgress(egressChannel)
    }
    
    override fun close() {
        // Close SOCKS channels
    }
    
    override fun isOpen(): Boolean = true
    
    override fun register(selector: SelectorInterface, interest: Int, attachment: Any?): SelectionKey {
        return selector.register(this, interest, attachment)
    }
}

/**
 * SOCKS Channel Factory
 */
object SocksChannelFactory {
    fun createBidirectionalChannel(
        ingressChannel: SocksIngressChannel,
        egressChannel: SocksEgressChannel
    ): SocksChannel {
        return SocksChannel(ingressChannel, egressChannel)
    }
    
    fun createIngressOnlyChannel(ingressChannel: SocksIngressChannel): SocksChannel {
        return SocksChannel(ingressChannel, object : SocksEgressChannel {
            override suspend fun send(data: Indexed<Byte>): Int = 0
        })
    }
    
    fun createEgressOnlyChannel(egressChannel: SocksEgressChannel): SocksChannel {
        return SocksChannel(object : SocksIngressChannel {
            override suspend fun receive(): Indexed<Byte> = Indexed()
        }, egressChannel)
    }
} 