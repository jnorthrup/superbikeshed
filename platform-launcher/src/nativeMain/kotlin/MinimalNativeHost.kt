@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

import kotlinx.cinterop.*
import platform.posix.*

fun main() {
    println("🚀 Minimal Native MCP Host")
    println("=========================")
    
    memScoped {
        // Create kqueue
        val kq = kqueue()
        if (kq < 0) {
            println("❌ Failed to create kqueue")
            return
        }
        
        println("✅ kqueue fd: $kq")
        
        // Create socket
        val sock = socket(AF_INET, SOCK_STREAM, 0)
        if (sock < 0) {
            println("❌ Failed to create socket")
            return
        }
        
        // Bind to port
        val addr = alloc<sockaddr_in>()
        addr.sin_family = AF_INET.convert()
        addr.sin_port = ((9876 shr 8) or ((9876 and 0xFF) shl 8)).toUShort()
        addr.sin_addr.s_addr = 0x0100007Fu // 127.0.0.1
        
        if (bind(sock, addr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) < 0) {
            println("❌ Bind failed")
            close(sock)
            return
        }
        
        listen(sock, 5)
        println("✅ Listening on port 9876")
        
        // Simple event loop
        val events = allocArray<kevent>(10)
        val ev = alloc<kevent>()
        
        // Register socket
        ev.ident = sock.convert()
        ev.filter = EVFILT_READ.toShort()
        ev.flags = (EV_ADD or EV_ENABLE).toUShort()
        
        kevent(kq, ev.ptr, 1, null, 0, null)
        
        println("📡 Waiting for connections...")
        
        while (true) {
            val n = kevent(kq, null, 0, events, 10, null)
            
            if (n > 0) {
                println("⚡ Event received!")
                
                for (i in 0 until n) {
                    if (events[i].ident.toInt() == sock) {
                        // Accept connection
                        val client = accept(sock, null, null)
                        if (client >= 0) {
                            println("✅ Client connected: fd=$client")
                            
                            // Send hello
                            val msg = "Hello from Native MCP Host!\n"
                            send(client, msg.cstr.ptr, msg.length.convert(), 0)
                            
                            close(client)
                        }
                    }
                }
            }
        }
    }
}