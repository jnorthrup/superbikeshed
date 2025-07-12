package borg.trikeshed.net.quic

import borg.trikeshed.lib.Indexed

expect interface QuicConnection {
    suspend fun createStream(): QuicStream?
    suspend fun close()
}
