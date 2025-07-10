@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

import kotlinx.cinterop.*
import platform.posix.*

fun main() {
    println("🚀 MCP Native Host v1.0")
    println("=======================")
    
    val server = SimpleServer()
    server.start(9876)
}

class SimpleServer {
    fun start(port: Int) = memScoped {
        // Create socket
        val sock = socket(AF_INET, SOCK_STREAM, 0)
        if (sock < 0) {
            println("❌ Failed to create socket")
            return
        }
        
        // Allow reuse
        val one = alloc<IntVar>()
        one.value = 1
        setsockopt(sock, SOL_SOCKET, SO_REUSEADDR, one.ptr, sizeOf<IntVar>().convert())
        
        // Bind
        val addr = alloc<sockaddr_in>()
        addr.sin_family = AF_INET.convert()
        addr.sin_port = htons(port.toUShort())
        addr.sin_addr.s_addr = 0u
        
        if (bind(sock, addr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) < 0) {
            println("❌ Failed to bind to port $port")
            close(sock)
            return
        }
        
        listen(sock, 5)
        println("✅ Listening on port $port")
        
        // Simple blocking accept loop
        while (true) {
            val client = accept(sock, null, null)
            if (client >= 0) {
                println("✅ Client connected")
                
                val msg = "MCP Native Host Ready\n"
                send(client, msg.cstr.ptr, msg.length.convert(), 0)
                
                close(client)
            }
        }
    }
}

fun htons(v: UShort): UShort = ((v.toInt() and 0xFF) shl 8 or (v.toInt() shr 8)).toUShort()