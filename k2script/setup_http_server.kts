// Dependencies
@file:DependsOn("io.ktor:ktor-server-core:3.1.3")
@file:DependsOn("io.ktor:ktor-server-netty:3.1.3")
@file:DependsOn("ch.qos.logback:logback-classic:1.2.11")

// Imports
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.content.*
import io.ktor.server.request.*

fun main() {
    embeddedServer(Netty, port = 8080) {
        routing {
            get("/hello") {
                call.respondText("Hello, Ktor Server!")
            }
            get("/http_version") {
                val version = call.request.httpVersion
                call.respondText("Request HTTP Version: $version")
            }
        }
    }.start(wait = true)
    // This line might only be reached after server stops if wait = true.
    // For the purpose of this script, wait = true is fine.
    // A more robust solution for logging server start would involve events or a different start mechanism.
    println("Ktor server started on port 8080")
}
