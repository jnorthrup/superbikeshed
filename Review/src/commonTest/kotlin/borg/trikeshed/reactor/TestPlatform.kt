package borg.trikeshed.reactor

// ServerChannel and ClientChannel are now correctly identified as common interfaces
// defined in borg.trikeshed.reactor.ReactorTypes.kt
expect class TestPlatform {
    fun createServerSocket(): ServerChannel
    fun createClientSocket(port: Int): ClientChannel
    fun getLocalPort(serverChannel: ServerChannel): Int
    fun writeToChannel(channel: ClientChannel, data: ByteArray)
    fun readFromChannel(channel: ClientChannel): ByteArray
}
