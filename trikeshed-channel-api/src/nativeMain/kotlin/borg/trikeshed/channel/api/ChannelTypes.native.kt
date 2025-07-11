@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.channel.api

/**
 * Native implementation of ChannelId
 */
actual value class ChannelId(actual val value: String) {
    actual companion object {
        actual fun generate(): ChannelId = ChannelId(kotlin.random.Random.nextLong().toString(36))
    }
}