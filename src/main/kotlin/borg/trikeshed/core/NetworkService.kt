package borg.trikeshed.core

import kotlin.coroutines.CoroutineContext

/**
 * Service for network communication.
 * This service provides utilities for sending and receiving Series data
 * over the network.
 */
expect class NetworkService : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<NetworkService>
    override val key: CoroutineContext.Key<*>

    /**
     * Sends a Series to a remote endpoint.
     * @param endpoint The target endpoint
     * @param series The Series to send
     * @return True if the send was successful
     */
    suspend fun <T> sendSeries(endpoint: String, series: Series<T>): Boolean

    /**
     * Receives a Series from a remote endpoint.
     * @param endpoint The source endpoint
     * @return The received Series
     */
    suspend fun <T> receiveSeries(endpoint: String): Series<T>

    /**
     * Establishes a connection to a remote endpoint.
     * @param endpoint The target endpoint
     * @return True if the connection was successful
     */
    suspend fun connect(endpoint: String): Boolean

    /**
     * Closes a connection to a remote endpoint.
     * @param endpoint The target endpoint
     */
    suspend fun disconnect(endpoint: String)

    /**
     * Checks if a connection to an endpoint is active.
     * @param endpoint The target endpoint
     * @return True if the connection is active
     */
    fun isConnected(endpoint: String): Boolean
} 