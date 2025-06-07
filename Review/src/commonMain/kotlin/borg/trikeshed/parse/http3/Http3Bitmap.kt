package borg.trikeshed.parse.http3


import borg.trikeshed.lib.*
import borg.trikeshed.parse.DocumentBitmap

typealias Http3FrameBitmap = Join<ULong, Join<ULong, ByteArray>>
typealias QpackHeaderBitmap = Join<ULong, Join<ULong, ByteArray>>
typealias Http3StreamBitmap = Join<ULong, Join<ULong, ByteArray>>