@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.channel.api

import borg.trikeshed.lib.*

/**
 * Unique identifier for a channel instance.
 */
@JvmInline
value class ChannelId(val value: String) {
    companion object {
        fun generate(): ChannelId = ChannelId(kotlin.random.Random.nextLong().toString(36))
    }
}

/**
 * Configuration for channel behavior and properties.
 */
data class ChannelConfig(
    val type: ChannelType,
    val mode: ChannelMode,
    val bufferSize: Int = 8192,
    val timeout: Long = 30_000,
    val keepAlive: Boolean = true,
    val properties: Map<String, Any> = emptyMap()
)

/**
 * Type of channel transport mechanism.
 */
enum class ChannelType {
    TCP,
    UDP,
    UNIX_SOCKET,
    FILE,
    MEMORY,
    PIPE,
    QUIC,
    SCTP,
    CUSTOM
}

/**
 * Channel operation mode.
 */
enum class ChannelMode {
    READ_ONLY,
    WRITE_ONLY,
    READ_WRITE,
    APPEND
}

/**
 * Network or file system address for channel connections.
 */
sealed class ChannelAddress {
    data class InetAddress(val host: String, val port: Int) : ChannelAddress()
    data class UnixAddress(val path: String) : ChannelAddress()
    data class FileAddress(val path: String) : ChannelAddress()
    data class MemoryAddress(val region: String) : ChannelAddress()
    data class CustomAddress(val scheme: String, val address: String) : ChannelAddress()
}

/**
 * Metadata about channel state and statistics.
 */
data class ChannelMetadata(
    val createdAt: Long,
    val lastActivity: Long,
    val bytesRead: Long,
    val bytesWritten: Long,
    val errors: Int,
    val attributes: Map<String, Any> = emptyMap()
)

/**
 * Channel lifecycle state management.
 */
sealed class ChannelLifecycle {
    object Created : ChannelLifecycle()
    object Opening : ChannelLifecycle()
    object Open : ChannelLifecycle()
    object Connected : ChannelLifecycle()
    object Closing : ChannelLifecycle()
    object Closed : ChannelLifecycle()
    data class Error(val cause: Throwable) : ChannelLifecycle()
}

/**
 * Channel health status.
 */
data class ChannelHealth(
    val status: HealthStatus,
    val lastCheck: Long,
    val latency: Long? = null,
    val throughput: Double? = null,
    val details: Map<String, Any> = emptyMap()
)

enum class HealthStatus {
    HEALTHY,
    DEGRADED,
    UNHEALTHY,
    UNKNOWN
}

/**
 * Exception types for channel operations.
 */
sealed class ChannelException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class ConnectionFailed(message: String, cause: Throwable? = null) : ChannelException(message, cause)
    class ReadTimeout(message: String) : ChannelException(message)
    class WriteTimeout(message: String) : ChannelException(message)
    class ChannelClosed(message: String) : ChannelException(message)
    class InvalidOperation(message: String) : ChannelException(message)
    class ConfigurationError(message: String, cause: Throwable? = null) : ChannelException(message, cause)
}