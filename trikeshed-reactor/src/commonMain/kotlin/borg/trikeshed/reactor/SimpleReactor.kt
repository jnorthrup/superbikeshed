@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.reactor

/**
 * Minimal reactor - one responsibility: event loop
 */
expect class SimpleReactor() {
    suspend fun select(): Int
    suspend fun register(channel: Channel, callback: suspend () -> Unit)
    suspend fun run()
    suspend fun stop()
}