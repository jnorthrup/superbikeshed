@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.reactor

interface AsyncChannel {
    suspend fun close()
    val isOpen: Boolean
}