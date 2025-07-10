@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

import kotlinx.cinterop.*
import platform.posix.*
import platform.darwin.*

fun main() {
    println("🚀 Native MCP Host Starting...")
    println("==============================")
    
    memScoped {
        // Create kqueue
        val kq = kqueue()
        if (kq < 0) {
            println("❌ Failed to create kqueue: ${strerror(errno)?.toKString()}")
            return
        }
        println("✅ kqueue created: fd=$kq")
        
        // Create shared memory
        val shmPath = "/mcp_uring_shm"
        shm_unlink(shmPath) // Clean up any existing
        
        val shmFd = shm_open(shmPath, O_CREAT or O_RDWR, 0666)
        if (shmFd < 0) {
            println("❌ Failed to create shared memory: ${strerror(errno)?.toKString()}")
            return
        }
        
        // Set size to 1MB
        if (ftruncate(shmFd, 1024 * 1024) < 0) {
            println("❌ Failed to set shared memory size")
            close(shmFd)
            return
        }
        
        println("✅ Shared memory created: $shmPath (1MB)")
        
        // Create control socket
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
        memset(addr.ptr, 0, sizeOf<sockaddr_in>().convert())
        addr.sin_family = AF_INET.convert()
        addr.sin_port = htons(9876u)
        addr.sin_addr.s_addr = INADDR_ANY
        
        if (bind(sock, addr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) < 0) {
            println("❌ Bind failed: ${strerror(errno)?.toKString()}")
            close(sock)
            return
        }
        
        listen(sock, 10)
        println("✅ Listening on port 9876")
        
        // Make non-blocking
        fcntl(sock, F_SETFL, O_NONBLOCK)
        
        // Register with kqueue
        val ev = alloc<kevent>()
        EV_SET(ev.ptr, sock.convert(), EVFILT_READ.convert(), 
            (EV_ADD or EV_ENABLE).convert(), 0u, 0, null)
        
        if (kevent(kq, ev.ptr, 1, null, 0, null) < 0) {
            println("❌ Failed to register socket with kqueue")
            return
        }
        
        println("📡 Ready for connections...")
        println("")
        
        // Event loop
        val events = allocArray<kevent>(16)
        val timeout = alloc<timespec>()
        timeout.tv_sec = 1
        timeout.tv_nsec = 0
        
        var running = true
        while (running) {
            val n = kevent(kq, null, 0, events, 16, timeout.ptr)
            
            if (n > 0) {
                for (i in 0 until n) {
                    val event = events[i]
                    
                    if (event.ident.toInt() == sock) {
                        // Accept new connection
                        val client = accept(sock, null, null)
                        if (client >= 0) {
                            println("✅ Client connected: fd=$client")
                            
                            // Send welcome message
                            val msg = "MCP Native Host Ready\\r\\n"
                            send(client, msg.cstr.ptr, msg.length.convert(), 0)
                            
                            // Make non-blocking
                            fcntl(client, F_SETFL, O_NONBLOCK)
                            
                            // Add to kqueue
                            EV_SET(ev.ptr, client.convert(), EVFILT_READ.convert(),
                                (EV_ADD or EV_ENABLE).convert(), 0u, 0, null)
                            kevent(kq, ev.ptr, 1, null, 0, null)
                        }
                    } else {
                        // Handle client data
                        val fd = event.ident.toInt()
                        val buffer = allocArray<ByteVar>(1024)
                        val n = recv(fd, buffer, 1024, 0)
                        
                        when {
                            n > 0 -> {
                                println("📥 Received $n bytes from fd=$fd")
                                // Echo back
                                send(fd, buffer, n.convert(), 0)
                            }
                            n == 0L -> {
                                println("🔌 Client disconnected: fd=$fd")
                                close(fd)
                            }
                            else -> {
                                if (errno != EAGAIN && errno != EWOULDBLOCK) {
                                    println("❌ Read error: ${strerror(errno)?.toKString()}")
                                    close(fd)
                                }
                            }
                        }
                    }
                }
            }
            
            // Check for shutdown
            if (access("/tmp/mcp_shutdown", F_OK) == 0) {
                println("\\n🛑 Shutdown signal received")
                running = false
            }
        }
        
        // Cleanup
        close(sock)
        close(kq)
        close(shmFd)
        shm_unlink(shmPath)
        
        println("✅ Shutdown complete")
    }
}

// Helper to convert host to network byte order
fun htons(value: UShort): UShort {
    val bytes = value.toInt()
    return (((bytes and 0xFF) shl 8) or ((bytes shr 8) and 0xFF)).toUShort()
}