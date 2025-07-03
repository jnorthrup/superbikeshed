package borg.trikeshed.reactor.quic

import kotlinx.coroutines.runBlocking

data class QuicServerConfig(
    var port: Int = 4433,
    var host: String = "0.0.0.0",
    var mtu: Int = 1500,
    var onStreamHandler: (QuicStream) -> Unit = {}
)

class QuicDsl {
    private val config = QuicServerConfig()

    infix fun listen(on: Int) {
        config.port = on
    }
    
    fun onStream(handler: (QuicStream) -> Unit) {
        config.onStreamHandler = handler
    }

    fun build(): QuicServerConfig = config
}

fun quicd(block: QuicDsl.() -> Unit) {
    val dsl = QuicDsl().apply(block)
    val config = dsl.build()
    
    runBlocking {
        QuicServer(config).start()
    }
} 