@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.net.socks

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import borg.trikeshed.lib.Indexed

/**
 * CoroutineContext.Element that provides SOCKS ingress (incoming data) channel.
 * When present in the CoroutineContext, network operations should route incoming data
 * through this channel.
 */
interface SocksIngressChannel {
    /**
     * Receives data through the SOCKS ingress.
     * @return The received data as an Indexed<Byte>.
     */
    suspend fun receive(): Indexed<Byte>
}

/**
 * CoroutineContext.Element that provides SOCKS egress (outgoing data) channel.
 * When present in the CoroutineContext, network operations should route outgoing data
 * through this channel.
 */
interface SocksEgressChannel {
    /**
     * Sends data through the SOCKS egress.
     * @param data The data to send as an Indexed<Byte>.
     * @return The number of bytes sent.
     */
    suspend fun send(data: Indexed<Byte>): Int
}

/**
 * CoroutineContext.Key for SocksIngressChannel.
 */
object SocksIngressChannelKey : CoroutineContext.Key<SocksIngressChannelElement>

/**
 * CoroutineContext.Key for SocksEgressChannel.
 */
object SocksEgressChannelKey : CoroutineContext.Key<SocksEgressChannelElement>

/**
 * CoroutineContext.Element implementation for SocksIngressChannel.
 */
data class SocksIngressChannelElement(val channel: SocksIngressChannel) :
    AbstractCoroutineContextElement(SocksIngressChannelKey), SocksIngressChannel {
    override suspend fun receive(): Indexed<Byte> = channel.receive()
}

/**
 * CoroutineContext.Element implementation for SocksEgressChannel.
 */
data class SocksEgressChannelElement(val channel: SocksEgressChannel) :
    AbstractCoroutineContextElement(SocksEgressChannelKey), SocksEgressChannel {
    override suspend fun send(data: Indexed<Byte>): Int = channel.send(data)
}

/**
 * Extension function to easily add a SocksIngressChannel to a CoroutineContext.
 */
fun CoroutineContext.withSocksIngress(channel: SocksIngressChannel): CoroutineContext =
    this + SocksIngressChannelElement(channel)

/**
 * Extension function to easily add a SocksEgressChannel to a CoroutineContext.
 */
fun CoroutineContext.withSocksEgress(channel: SocksEgressChannel): CoroutineContext =
    this + SocksEgressChannelElement(channel)

/**
 * Extension property to retrieve the SocksIngressChannel from a CoroutineContext.
 */
val CoroutineContext.socksIngress: SocksIngressChannel?
    get() = this[SocksIngressChannelKey]?.channel

/**
 * Extension property to retrieve the SocksEgressChannel from a CoroutineContext.
 */
val CoroutineContext.socksEgress: SocksEgressChannel?
    get() = this[SocksEgressChannelKey]?.channel
