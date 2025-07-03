package borg.trikeshed.reactor.ipc

import kotlinx.coroutines.*
import borg.trikeshed.reactor.*
import borg.trikeshed.net.socks.*
import borg.trikeshed.lib.*

/**
 * IPC Channel implementation for reactor system
 */
class IpcChannel(
    private val ingressChannel: SocksIngressChannel? = null,
    private val egressChannel: SocksEgressChannel? = null
) : borg.trikeshed.reactor.IpcChannel {
    
    override fun createIpcContext(): CoroutineContext {
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
        // Close IPC channels
    }
    
    override fun isOpen(): Boolean = true
    
    override fun register(selector: SelectorInterface, interest: Int, attachment: Any?): SelectionKey {
        return selector.register(this, interest, attachment)
    }
}

/**
 * IPC Channel Factory
 */
object IpcChannelFactory {
    fun createBidirectionalChannel(
        ingressChannel: SocksIngressChannel,
        egressChannel: SocksEgressChannel
    ): IpcChannel {
        return IpcChannel(ingressChannel, egressChannel)
    }
    
    fun createIngressOnlyChannel(ingressChannel: SocksIngressChannel): IpcChannel {
        return IpcChannel(ingressChannel, null)
    }
    
    fun createEgressOnlyChannel(egressChannel: SocksEgressChannel): IpcChannel {
        return IpcChannel(null, egressChannel)
    }
    
    fun createDefaultChannel(): IpcChannel {
        return IpcChannel()
    }
} 