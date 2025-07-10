@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.channel.api

import kotlin.coroutines.CoroutineContext

/**
 * ChannelService stub for trikeshed-ccek compatibility
 */
class ChannelService(
    val provider: ChannelProvider
) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<ChannelService>
    override val key = Key
}

/**
 * ChannelProvider stub
 */
interface ChannelProvider {
    fun getProvider(): String
}

/**
 * MemoryChannelProvider stub
 */
class MemoryChannelProvider : ChannelProvider {
    override fun getProvider(): String = "memory"
}