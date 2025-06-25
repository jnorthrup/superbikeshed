package borg.trikeshed.net.quic

import borg.trikeshed.lib.*

/**
 * Minimal placeholder QUIC config for compilation
 */
data class QuicConfig(
    val host: String = "localhost",
    val port: Int = 8080,
    val maxConnections: Int = 1000,
    val enableTls: Boolean = true
) 