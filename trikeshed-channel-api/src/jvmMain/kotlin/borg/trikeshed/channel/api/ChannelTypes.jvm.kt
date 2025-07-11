@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.channel.api

/**
 * JVM implementation of ChannelId using @JvmInline
 */
@JvmInline
actual value class ChannelId(actual val value: String) {
    actual companion object {
        actual fun generate(): ChannelId = ChannelId(kotlin.random.Random.nextLong().toString(36))
    }
}