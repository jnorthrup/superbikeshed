package borg.trikeshed.nio

expect class PlatformInetSocketAddress(host: String, port: Int) {
    val host: String
    val port: Int
} 