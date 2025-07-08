package borg.trikeshed.channel.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel

/**
 * Represents a bidirectional channelized connection for raw byte array data.
 * This interface serves as a base for various channelized services, ensuring
 * adherence to non-shunned types (ByteArray).
 */
interface ChannelizedConnection {
    /**
     * Provides a channel to send outgoing byte array data.
     */
    val output: SendChannel<ByteArray>

    /**
     * Provides a flow to receive incoming byte array data.
     */
    val input: Flow<ByteArray>
}

/**
 * A trait for channelized connections that support real-time updates.
 */
interface RealtimeChannel : ChannelizedConnection {
    /**
     * Emits real-time updates as byte arrays.
     */
    fun realTimeUpdates(): Flow<ByteArray>
}

/**
 * A trait for channelized connections that incorporate security features.
 * The specific security mechanisms (e.g., encryption, authentication) would be
 * handled internally by implementations.
 */
interface SecureChannel : ChannelizedConnection {
    /**
     * Performs a security handshake or negotiation.
     * Returns true if the handshake is successful, false otherwise.
     */
    suspend fun performSecurityHandshake(): Boolean
}

/**
 * A trait for channelized connections that provide data views.
 * The view definition and data format are expected to be handled as byte arrays.
 */
interface ViewChannel : ChannelizedConnection {
    /**
     * Requests a specific view of data and returns it as a flow of byte arrays.
     * The 'viewRequest' byte array would contain the view definition/parameters.
     */
    fun requestView(viewRequest: ByteArray): Flow<ByteArray>
}

/**
 * A trait for channelized connections that support long-polling mechanisms.
 */
interface LongPollChannel : ChannelizedConnection {
    /**
     * Initiates a long-poll request and returns a flow of responses.
     * The 'longPollRequest' byte array would contain the request parameters.
     */
    fun longPoll(longPollRequest: ByteArray): Flow<ByteArray>
}
