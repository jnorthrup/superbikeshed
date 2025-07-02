package borg.trikeshed.reactor

expect class ClientChannel {
    fun isConnected(): Boolean
    fun close()
}