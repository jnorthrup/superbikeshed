@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

import kotlinx.cinterop.*
import platform.posix.*
import platform.darwin.*
import kotlin.test.*

// Test executable that can be built and run
fun main() {
    println("🧪 Test Native MCP Host")
    println("======================")
    
    testKqueue()
    testSocket()
    testSharedMemory()
    
    println("\n✅ All tests passed!")
}

fun testKqueue() {
    print("Testing kqueue... ")
    
    val kq = kqueue()
    assertTrue(kq >= 0, "kqueue creation failed")
    close(kq)
    
    println("✅")
}

fun testSocket() = memScoped {
    print("Testing socket... ")
    
    val sock = socket(AF_INET, SOCK_STREAM, 0)
    assertTrue(sock >= 0, "socket creation failed")
    
    // Set reuse
    val one = alloc<IntVar>()
    one.value = 1
    val ret = setsockopt(sock, SOL_SOCKET, SO_REUSEADDR, one.ptr, sizeOf<IntVar>().convert())
    assertEquals(0, ret, "setsockopt failed")
    
    close(sock)
    
    println("✅")
}

fun testSharedMemory() {
    print("Testing shared memory... ")
    
    val path = "/test_mcp_shm"
    shm_unlink(path) // Clean up
    
    val fd = shm_open(path, O_CREAT or O_RDWR, 0666)
    assertTrue(fd >= 0, "shm_open failed")
    
    val ret = ftruncate(fd, 4096)
    assertEquals(0, ret, "ftruncate failed")
    
    close(fd)
    shm_unlink(path)
    
    println("✅")
}

// Mini executable for testing
class TestHost {
    fun run() = memScoped {
        println("\n🚀 Starting Test Host...")
        
        val kq = kqueue()
        if (kq < 0) {
            println("❌ kqueue failed")
            return
        }
        
        val sock = socket(AF_INET, SOCK_STREAM, 0)
        if (sock < 0) {
            println("❌ socket failed")
            close(kq)
            return
        }
        
        // Bind to random port
        val addr = alloc<sockaddr_in>()
        memset(addr.ptr, 0, sizeOf<sockaddr_in>().convert())
        addr.sin_family = AF_INET.convert()
        addr.sin_port = 0u // Let OS choose
        addr.sin_addr.s_addr = INADDR_ANY
        
        if (bind(sock, addr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) < 0) {
            println("❌ bind failed")
            close(sock)
            close(kq)
            return
        }
        
        // Get actual port
        val addrLen = alloc<socklen_tVar>()
        addrLen.value = sizeOf<sockaddr_in>().convert()
        getsockname(sock, addr.ptr.reinterpret(), addrLen.ptr)
        
        val port = ntohs(addr.sin_port)
        println("✅ Listening on port $port")
        
        listen(sock, 5)
        
        // Register with kqueue
        val ev = alloc<kevent>()
        EV_SET(ev.ptr, sock.convert(), EVFILT_READ.convert(),
            (EV_ADD or EV_ENABLE).convert(), 0u, 0, null)
        
        kevent(kq, ev.ptr, 1, null, 0, null)
        
        println("📡 Waiting 5 seconds for test...")
        
        // Run for 5 seconds
        val events = allocArray<kevent>(10)
        val timeout = alloc<timespec>()
        timeout.tv_sec = 5
        timeout.tv_nsec = 0
        
        val n = kevent(kq, null, 0, events, 10, timeout.ptr)
        
        if (n > 0) {
            println("⚡ Got $n events!")
        } else {
            println("⏱️ Timeout reached")
        }
        
        close(sock)
        close(kq)
        
        println("✅ Test host shutdown")
    }
}

// Network byte order conversion
fun ntohs(value: UShort): UShort {
    val bytes = value.toInt()
    return (((bytes and 0xFF) shl 8) or ((bytes shr 8) and 0xFF)).toUShort()
}