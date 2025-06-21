package borg.trikeshed.wireproto

interface WireProto {
    fun pack(data: ByteArray): ByteArray
    fun unpack(data: ByteArray): ByteArray
} 