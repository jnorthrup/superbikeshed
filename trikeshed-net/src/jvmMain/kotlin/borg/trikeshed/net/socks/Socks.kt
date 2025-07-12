package borg.trikeshed.net.socks

import borg.trikeshed.lib.*
import kotlinx.coroutines.channels.Channel

/**
 * JVM implementation of SOCKS properties
 */
actual val socksIngress: Channel<Indexed<Byte>>? = null
actual val socksEgress: Channel<Indexed<Byte>>? = null