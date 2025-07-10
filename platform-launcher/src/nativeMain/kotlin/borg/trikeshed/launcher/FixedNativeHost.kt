@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)
@file:OptIn(kotlin.ExperimentalStdlibApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)
package borg.trikeshed.launcher

import kotlinx.cinterop.*
import platform.posix.*
import kotlin.system.exitProcess

/**
 * Fixed Native Host with proper Darwin/macOS support
 */
fun main() {
    println("🚀 Native io_uring MCP Server Host (Fixed)")
    println("=========================================")
    
    try {
        NativeHostFixed().run()
    } catch (e: Exception) {
        println("❌ Fatal error: ${e.message}")
        exitProcess(1)
    }
}

class NativeHostFixed {
    private var kqueueFd: Int = -1
    private var controlSocket: Int = -1
    private var shmFd: Int = -1
    private var shmPtr: CPointer<ByteVar>? = null
    
    fun run() = memScoped {
        initialize()
        eventLoop()
    }
    
    private fun initialize() = memScoped {
        println("📦 Initializing native host...")
        
        // Create kqueue
        kqueueFd = kqueue()
        if (kqueueFd < 0) {
            error("Failed to create kqueue: ${strerror(errno)?.toKString()}")
        }
        println("✅ kqueue created (fd=$kqueueFd)")
        
        // Create shared memory
        createSharedMemory()
        
        // Create control socket
        createControlSocket()
        
        println("✅ Native host initialized")
    }
    
    private fun createSharedMemory() = memScoped {
        println("📝 Creating shared memory...")
        
        // macOS specific: use shm_open
        val shmName = "/uring_mcp_ccek"
        
        // Remove existing
        shm_unlink(shmName)
        
        // Create new
        shmFd = shm_open(shmName, O_CREAT or O_RDWR, 0666)
        if (shmFd < 0) {
            error("shm_open failed: ${strerror(errno)?.toKString()}")
        }
        
        // Set size to 16MB
        val shmSize = 16L * 1024 * 1024
        if (ftruncate(shmFd, shmSize) < 0) {
            error("ftruncate failed: ${strerror(errno)?.toKString()}")
        }
        
        // Map it
        shmPtr = mmap(null, shmSize.convert(), 
            PROT_READ or PROT_WRITE, MAP_SHARED, shmFd, 0)?.reinterpret()
        
        if (shmPtr == MAP_FAILED) {
            error("mmap failed: ${strerror(errno)?.toKString()}")
        }
        
        // Initialize ring buffer header
        initializeRingBuffer()
        
        println("✅ Shared memory created: ${shmSize / 1024 / 1024}MB")
    }
    
    private fun initializeRingBuffer() {
        val header = shmPtr!!.reinterpret<RingBufferHeader>()
        header.pointed.writeIndex = 0u
        header.pointed.readIndex = 0u
        header.pointed.capacity = (16u * 1024u * 1024u) - sizeOf<RingBufferHeader>().toUInt()
        header.pointed.magic = 0xDEADBEEFu
    }
    
    private fun createControlSocket() = memScoped {
        println("🔌 Creating control socket...")
        
        controlSocket = socket(AF_INET, SOCK_STREAM, 0)
        if (controlSocket < 0) {
            error("socket failed: ${strerror(errno)?.toKString()}")
        }
        
        // Reuse address
        val one = alloc<IntVar>()
        one.value = 1
        setsockopt(controlSocket, SOL_SOCKET, SO_REUSEADDR, 
            one.ptr, sizeOf<IntVar>().convert())
        
        // Non-blocking
        val flags = fcntl(controlSocket, F_GETFL, 0)
        fcntl(controlSocket, F_SETFL, flags or O_NONBLOCK)
        
        // Bind
        val addr = alloc<sockaddr_in>()
        memset(addr.ptr, 0, sizeOf<sockaddr_in>().convert())
        addr.sin_family = AF_INET.convert()
        addr.sin_port = htons(9876u)
        addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK)
        
        if (bind(controlSocket, addr.ptr.reinterpret(), 
                sizeOf<sockaddr_in>().convert()) < 0) {
            error("bind failed: ${strerror(errno)?.toKString()}")
        }
        
        if (listen(controlSocket, 10) < 0) {
            error("listen failed: ${strerror(errno)?.toKString()}")
        }
        
        // Register with kqueue
        val event = alloc<kevent>()
        memset(event.ptr, 0, sizeOf<kevent>().convert())
        
        event.ident = controlSocket.convert()
        event.filter = EVFILT_READ.toShort()
        event.flags = (EV_ADD or EV_ENABLE).toUShort()
        
        if (kevent(kqueueFd, event.ptr, 1, null, 0, null) < 0) {
            error("kevent failed: ${strerror(errno)?.toKString()}")
        }
        
        println("✅ Control socket listening on port 9876")
    }
    
    private fun eventLoop() = memScoped {
        println("\n🔄 Starting event loop...")
        
        val events = allocArray<kevent>(64)
        val timeout = alloc<timespec>()
        timeout.tv_sec = 1
        timeout.tv_nsec = 0
        
        var running = true
        var iteration = 0
        
        while (running) {
            val nEvents = kevent(kqueueFd, null, 0, events, 64, timeout.ptr)
            
            if (nEvents < 0) {
                if (errno == EINTR) continue
                error("kevent wait failed: ${strerror(errno)?.toKString()}")
            }
            
            if (nEvents > 0) {
                for (i in 0 until nEvents) {
                    handleEvent(events[i])
                }
            }
            
            // Periodic tasks
            if (++iteration % 10 == 0) {
                checkSharedMemory()
            }
            
            // Check for shutdown signal
            if (checkShutdown()) {
                running = false
            }
        }
        
        println("🛑 Shutting down...")
        cleanup()
    }
    
    private fun handleEvent(event: kevent) = memScoped {
        when (event.ident.toInt()) {
            controlSocket -> handleControlSocket()
            else -> handleClientSocket(event.ident.toInt())
        }
    }
    
    private fun handleControlSocket() = memScoped {
        val addr = alloc<sockaddr_in>()
        val addrLen = alloc<socklen_tVar>()
        addrLen.value = sizeOf<sockaddr_in>().convert()
        
        val clientFd = accept(controlSocket, addr.ptr.reinterpret(), addrLen.ptr)
        if (clientFd < 0) {
            if (errno != EAGAIN && errno != EWOULDBLOCK) {
                println("⚠️ accept failed: ${strerror(errno)?.toKString()}")
            }
            return
        }
        
        // Make non-blocking
        val flags = fcntl(clientFd, F_GETFL, 0)
        fcntl(clientFd, F_SETFL, flags or O_NONBLOCK)
        
        // Register with kqueue
        val event = alloc<kevent>()
        memset(event.ptr, 0, sizeOf<kevent>().convert())
        
        event.ident = clientFd.convert()
        event.filter = EVFILT_READ.toShort()
        event.flags = (EV_ADD or EV_ENABLE).toUShort()
        
        kevent(kqueueFd, event.ptr, 1, null, 0, null)
        
        println("✅ New connection: fd=$clientFd")
    }
    
    private fun handleClientSocket(fd: Int) = memScoped {
        val buffer = allocArray<ByteVar>(4096)
        val bytesRead = read(fd, buffer, 4096u)
        
        when {
            bytesRead > 0 -> {
                println("📥 Received $bytesRead bytes from fd=$fd")
                // Echo back for testing
                write(fd, buffer, bytesRead.convert())
            }
            bytesRead == 0L -> {
                println("🔌 Connection closed: fd=$fd")
                close(fd)
            }
            else -> {
                if (errno != EAGAIN && errno != EWOULDBLOCK) {
                    println("⚠️ read error: ${strerror(errno)?.toKString()}")
                    close(fd)
                }
            }
        }
    }
    
    private fun checkSharedMemory() {
        val header = shmPtr!!.reinterpret<RingBufferHeader>().pointed
        
        if (header.magic != 0xDEADBEEFu) {
            println("⚠️ Shared memory corruption detected!")
            return
        }
        
        // Check for new messages
        if (header.writeIndex != header.readIndex) {
            println("📨 New messages in shared memory")
            // Process messages...
        }
    }
    
    private fun checkShutdown(): Boolean {
        // Check for shutdown file
        return access("/tmp/mcp_shutdown", F_OK) == 0
    }
    
    private fun cleanup() {
        if (controlSocket >= 0) close(controlSocket)
        if (kqueueFd >= 0) close(kqueueFd)
        
        if (shmPtr != null) {
            munmap(shmPtr, 16L * 1024 * 1024)
        }
        
        if (shmFd >= 0) {
            close(shmFd)
            shm_unlink("/uring_mcp_ccek")
        }
        
        println("✅ Cleanup complete")
    }
}

// Data structures
@CStruct("struct RingBufferHeader")
class RingBufferHeader(
    var magic: UInt,
    var writeIndex: UInt,
    var readIndex: UInt,
    var capacity: UInt
)

// Platform functions - these need to be provided by cinterop or expect/actual

@OptIn(ExperimentalForeignApi::class)
external fun shm_open(name: String, oflag: Int, mode: mode_t): Int

@OptIn(ExperimentalForeignApi::class)
external fun shm_unlink(name: String): Int

@OptIn(ExperimentalForeignApi::class)
fun htons(value: UShort): UShort = ((value.toInt() and 0xFF) shl 8 or (value.toInt() shr 8)).toUShort()

@OptIn(ExperimentalForeignApi::class)
fun htonl(value: UInt): UInt = (
    ((value and 0xFFu) shl 24) or
    ((value and 0xFF00u) shl 8) or
    ((value and 0xFF0000u) shr 8) or
    ((value and 0xFF000000u) shr 24)
)

// Constants
const val INADDR_LOOPBACK = 0x7f000001u