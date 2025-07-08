@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.channel.api

import borg.trikeshed.lib.*
import kotlin.coroutines.CoroutineContext

/**
 * CCEK (Coroutine Context Element Key) service for channel operations.
 * Provides unified channel access through the service context.
 */
class ChannelService(
    internal val provider: ChannelProvider
) : CoroutineContext.Element {
    
    companion object Key : CoroutineContext.Key<ChannelService>
    override val key = Key
    
    /**
     * Open a channel with the given configuration.
     */
    suspend fun openChannel(config: ChannelConfig): Channel {
        return provider.createChannel(config)
    }
    
    /**
     * Create a connected channel to the specified address.
     */
    suspend fun connect(config: ChannelConfig, address: ChannelAddress): ConnectedChannel {
        return provider.createConnectedChannel(config, address)
    }
    
    /**
     * Create a server channel that accepts connections.
     */
    suspend fun createServer(config: ChannelConfig, bindAddress: ChannelAddress): ServerChannel {
        return provider.createServerChannel(config, bindAddress)
    }
    
    /**
     * Get the underlying channel provider.
     */
    fun getProvider(): ChannelProvider = provider
}

/**
 * Recording service for channel operations - enables recording/replay/mock.
 */
class RecordingService(
    internal val recorder: ChannelRecorder
) : CoroutineContext.Element {
    
    companion object Key : CoroutineContext.Key<RecordingService>
    override val key = Key
    
    /**
     * Wrap a channel for recording operations.
     */
    fun wrapForRecording(channel: Channel): Channel {
        return recorder.wrapChannel(channel)
    }
    
    /**
     * Start recording session.
     */
    suspend fun startRecording(sessionId: String) {
        recorder.startSession(sessionId)
    }
    
    /**
     * Stop recording session.
     */
    suspend fun stopRecording() {
        recorder.stopSession()
    }
    
    /**
     * Replay recorded session.
     */
    suspend fun replaySession(sessionId: String): List<ChannelEvent> {
        return recorder.replaySession(sessionId)
    }
}

/**
 * Service extensions for context-based channel access.
 */
suspend inline fun <reified T : CoroutineContext.Element> requireService(): T {
    return kotlin.coroutines.coroutineContext[T::class.java as CoroutineContext.Key<T>]
        ?: error("Required service ${T::class.simpleName} not found in context")
}

suspend inline fun <reified T : CoroutineContext.Element> currentService(): T? {
    return kotlin.coroutines.coroutineContext[T::class.java as CoroutineContext.Key<T>]
}

/**
 * Channel operation recording interfaces.
 */
interface ChannelRecorder {
    suspend fun startSession(sessionId: String)
    suspend fun stopSession()
    fun wrapChannel(channel: Channel): Channel
    suspend fun replaySession(sessionId: String): List<ChannelEvent>
}

/**
 * Recorded channel events for replay.
 */
sealed class ChannelEvent {
    data class Read(val channelId: ChannelId, val data: ByteArray, val timestamp: Long) : ChannelEvent()
    data class Write(val channelId: ChannelId, val data: ByteArray, val timestamp: Long) : ChannelEvent()
    data class Connect(val channelId: ChannelId, val address: ChannelAddress, val timestamp: Long) : ChannelEvent()
    data class Close(val channelId: ChannelId, val timestamp: Long) : ChannelEvent()
    data class Error(val channelId: ChannelId, val error: Throwable, val timestamp: Long) : ChannelEvent()
}