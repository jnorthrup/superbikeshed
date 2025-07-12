package borg.trikeshed.net.socks

import borg.trikeshed.lib.Indexed
import kotlinx.coroutines.channels.Channel

expect val socksIngress: Channel<Indexed<Byte>>?
expect val socksEgress: Channel<Indexed<Byte>>?
