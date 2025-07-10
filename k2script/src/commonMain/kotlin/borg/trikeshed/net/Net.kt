@file:OptIn(kotlin.kotlin.ExperimentalStdlibApi::class)
package borg.trikeshed.net

import borg.trikeshed.lib.*

/**
 * Network utilities for TrikeShed
 * Stub implementation for k2script
 */
object Net {
    fun resolve(host: String, port: Int): Join<String, Int> = host j port
}

data class NetworkAddress(
    val host: String,
    val port: Int
)