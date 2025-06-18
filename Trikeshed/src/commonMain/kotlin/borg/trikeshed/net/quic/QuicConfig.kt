package borg.trikeshed.net.quic

data class QuicConfig(
    val enable0RTT: Boolean = false,
    val maxIdleTimeout: Long = 30000,
    val maxStreams: Int = 100,
    val bufferSize: Int = 64 * 1024
)