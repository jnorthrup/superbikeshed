package org.trikeshed.http3

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress

fun main() {
    val server = HttpServer.create(InetSocketAddress(8080), 0)
    server.createContext("/", { exchange ->
        exchange.sendResponseHeaders(200, "Hello from Trikeshed HTTP/3 Server!".length.toLong())
        exchange.responseBody.write("Hello from Trikeshed HTTP/3 Server!".toByteArray())
        exchange.close()
    })
    server.start()
    println("Trikeshed HTTP/3 Server running on port 8080")
}