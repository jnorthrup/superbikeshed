package borg.trikeshed.net.socks

import borg.trikeshed.lib.Indexed
import kotlinx.coroutines.channels.Channel

actual val socksIngress: Channel<Indexed<Byte>>? = null
actual val socksEgress: Channel<Indexed<Byte>>? = null
