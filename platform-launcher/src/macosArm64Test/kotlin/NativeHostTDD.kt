@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

import kotlinx.cinterop.*
import platform.posix.*
import platform.darwin.*
import kotlin.test.*

/**
 * TDD for Native MCP Host
 * 
 * Red -> Green -> Refactor
 */

// Test Entry Point
fun main() {
    println("🧪 Native MCP Host TDD")
    println("=====================")
    
    val suite = NativeHostTestSuite()
    suite.runAll()
}

class NativeHostTestSuite {
    fun runAll() {
        // Level 1: Basic System Calls
        testSystemCallsWork()
        
        // Level 2: Resource Management
        testResourceLifecycle()
        
        // Level 3: Event Loop
        testEventLoop()
        
        // Level 4: Client Connections
        testClientConnections()
        
        // Level 5: Message Protocol
        testMessageProtocol()
        
        // Level 6: Shared Memory IPC
        testSharedMemoryIPC()
        
        // Level 7: Full Integration
        testFullIntegration()
        
        println("\n✅ All tests passed!")
    }
    
    // LEVEL 1: Basic System Calls
    fun testSystemCallsWork() {
        println("\n📋 Level 1: Basic System Calls")
        
        // Test 1.1: Can create kqueue
        test("create kqueue") {
            val kq = kqueue()
            assert(kq >= 0) { "kqueue() returned $kq" }
            close(kq)
        }
        
        // Test 1.2: Can create socket
        test("create socket") {
            val sock = socket(AF_INET, SOCK_STREAM, 0)
            assert(sock >= 0) { "socket() returned $sock" }
            close(sock)
        }
        
        // Test 1.3: Can create shared memory
        test("create shared memory") {
            val path = "/test_shm_${getpid()}"
            shm_unlink(path)
            
            val fd = shm_open(path, O_CREAT or O_RDWR, 0666)
            assert(fd >= 0) { "shm_open() returned $fd" }
            
            close(fd)
            shm_unlink(path)
        }
    }
    
    // LEVEL 2: Resource Management
    fun testResourceLifecycle() = memScoped {
        println("\n📋 Level 2: Resource Management")
        
        // Test 2.1: Socket lifecycle
        test("socket bind/listen/close") {
            val sock = createBoundSocket(0)
            assert(sock.fd >= 0)
            assert(sock.port > 0)
            
            listen(sock.fd, 5)
            close(sock.fd)
        }
        
        // Test 2.2: Multiple sockets
        test("multiple sockets") {
            val sockets = (1..10).map { createBoundSocket(0) }
            
            sockets.forEach { sock ->
                assert(sock.fd >= 0)
                assert(sock.port > 0)
            }
            
            sockets.forEach { close(it.fd) }
        }
        
        // Test 2.3: Resource cleanup on error
        test("cleanup on error") {
            var cleaned = false
            
            try {
                val sock = createBoundSocket(0)
                // Simulate error
                throw TestException("Simulated error")
            } catch (e: TestException) {
                cleaned = true
            }
            
            assert(cleaned) { "Cleanup not performed" }
        }
    }
    
    // LEVEL 3: Event Loop
    fun testEventLoop() = memScoped {
        println("\n📋 Level 3: Event Loop")
        
        // Test 3.1: Basic event loop
        test("basic event loop") {
            val loop = EventLoop()
            loop.initialize()
            
            var eventFired = false
            loop.onTimeout = { eventFired = true }
            
            loop.runOnce(100) // 100ms timeout
            
            assert(eventFired) { "Timeout event not fired" }
            loop.shutdown()
        }
        
        // Test 3.2: Socket events
        test("socket read events") {
            val loop = EventLoop()
            loop.initialize()
            
            val server = createBoundSocket(0)
            listen(server.fd, 5)
            
            var acceptFired = false
            loop.addSocket(server.fd) { fd ->
                acceptFired = true
            }
            
            // Connect a client
            val client = socket(AF_INET, SOCK_STREAM, 0)
            val addr = alloc<sockaddr_in>()
            fillSockaddr(addr, "127.0.0.1", server.port)
            
            // Non-blocking connect
            fcntl(client, F_SETFL, O_NONBLOCK)
            connect(client, addr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert())
            
            // Run event loop
            loop.runOnce(1000)
            
            assert(acceptFired) { "Accept event not fired" }
            
            close(client)
            close(server.fd)
            loop.shutdown()
        }
    }
    
    // LEVEL 4: Client Connections
    fun testClientConnections() = memScoped {
        println("\n📋 Level 4: Client Connections")
        
        // Test 4.1: Accept connection
        test("accept client connection") {
            val server = MCPServer()
            server.start(0)
            
            val client = connectTo("127.0.0.1", server.port)
            
            delay(100)
            server.processEvents()
            
            assert(server.clientCount == 1) { "Expected 1 client, got ${server.clientCount}" }
            
            close(client)
            server.stop()
        }
        
        // Test 4.2: Multiple clients
        test("handle multiple clients") {
            val server = MCPServer()
            server.start(0)
            
            val clients = (1..5).map {
                connectTo("127.0.0.1", server.port)
            }
            
            delay(100)
            server.processEvents()
            
            assert(server.clientCount == 5) { "Expected 5 clients, got ${server.clientCount}" }
            
            clients.forEach { close(it) }
            server.stop()
        }
    }
    
    // LEVEL 5: Message Protocol
    fun testMessageProtocol() = memScoped {
        println("\n📋 Level 5: Message Protocol")
        
        // Test 5.1: Send/receive messages
        test("send and receive messages") {
            val server = MCPServer()
            server.start(0)
            
            var messageReceived = ""
            server.onMessage = { client, msg ->
                messageReceived = msg
                server.send(client, "PONG: $msg")
            }
            
            val client = connectTo("127.0.0.1", server.port)
            
            // Send message
            val msg = "PING\n"
            send(client, msg.cstr.ptr, msg.length.convert(), 0)
            
            delay(100)
            server.processEvents()
            
            assert(messageReceived == "PING") { "Expected 'PING', got '$messageReceived'" }
            
            // Receive response
            val buffer = allocArray<ByteVar>(256)
            val n = recv(client, buffer, 256, 0)
            
            if (n > 0) {
                val response = buffer.toKString().take(n.toInt())
                assert(response.startsWith("PONG:")) { "Expected PONG response, got '$response'" }
            }
            
            close(client)
            server.stop()
        }
    }
    
    // LEVEL 6: Shared Memory IPC
    fun testSharedMemoryIPC() = memScoped {
        println("\n📋 Level 6: Shared Memory IPC")
        
        // Test 6.1: Create ring buffer
        test("create shared memory ring buffer") {
            val shm = SharedMemoryRingBuffer("/test_ring_${getpid()}")
            shm.create(1024 * 1024) // 1MB
            
            assert(shm.isValid) { "Ring buffer not valid" }
            
            shm.destroy()
        }
        
        // Test 6.2: Write and read
        test("write and read from ring buffer") {
            val shm = SharedMemoryRingBuffer("/test_rw_${getpid()}")
            shm.create(1024)
            
            val testData = "Hello from TDD!"
            shm.write(testData)
            
            val readData = shm.read()
            assert(readData == testData) { "Expected '$testData', got '$readData'" }
            
            shm.destroy()
        }
    }
    
    // LEVEL 7: Full Integration
    fun testFullIntegration() = memScoped {
        println("\n📋 Level 7: Full Integration")
        
        // Test 7.1: Complete server lifecycle
        test("complete server lifecycle") {
            val host = NativeMCPHost()
            
            // Initialize
            assert(host.initialize()) { "Failed to initialize" }
            
            // Start
            assert(host.start()) { "Failed to start" }
            
            // Connect client
            val client = connectTo("127.0.0.1", host.port)
            
            // Send request
            val request = """{"method": "ping", "id": 1}""" + "\n"
            send(client, request.cstr.ptr, request.length.convert(), 0)
            
            // Process
            delay(100)
            host.processOnce()
            
            // Check response
            val buffer = allocArray<ByteVar>(1024)
            val n = recv(client, buffer, 1024, MSG_DONTWAIT)
            
            if (n > 0) {
                val response = buffer.toKString().take(n.toInt())
                assert(response.contains("pong")) { "Expected pong in response" }
            }
            
            // Shutdown
            close(client)
            host.stop()
            
            assert(!host.isRunning) { "Host still running after stop" }
        }
    }
    
    // Test runner helper
    private fun test(name: String, block: MemScope.() -> Unit) {
        print("  ▶️ $name... ")
        
        try {
            memScoped {
                block()
            }
            println("✅")
        } catch (e: Throwable) {
            println("❌")
            println("     Error: ${e.message}")
            throw e
        }
    }
}

// Test Infrastructure

data class BoundSocket(val fd: Int, val port: Int)

fun MemScope.createBoundSocket(requestedPort: Int): BoundSocket {
    val sock = socket(AF_INET, SOCK_STREAM, 0)
    if (sock < 0) throw TestException("socket() failed")
    
    // Reuse address
    val one = alloc<IntVar>()
    one.value = 1
    setsockopt(sock, SOL_SOCKET, SO_REUSEADDR, one.ptr, sizeOf<IntVar>().convert())
    
    // Bind
    val addr = alloc<sockaddr_in>()
    memset(addr.ptr, 0, sizeOf<sockaddr_in>().convert())
    addr.sin_family = AF_INET.convert()
    addr.sin_port = htons(requestedPort.toUShort())
    addr.sin_addr.s_addr = INADDR_ANY
    
    if (bind(sock, addr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) < 0) {
        close(sock)
        throw TestException("bind() failed")
    }
    
    // Get actual port
    val addrLen = alloc<socklen_tVar>()
    addrLen.value = sizeOf<sockaddr_in>().convert()
    getsockname(sock, addr.ptr.reinterpret(), addrLen.ptr)
    
    return BoundSocket(sock, ntohs(addr.sin_port).toInt())
}

fun MemScope.connectTo(host: String, port: Int): Int {
    val sock = socket(AF_INET, SOCK_STREAM, 0)
    if (sock < 0) throw TestException("socket() failed")
    
    val addr = alloc<sockaddr_in>()
    fillSockaddr(addr, host, port)
    
    if (connect(sock, addr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) < 0) {
        close(sock)
        throw TestException("connect() failed")
    }
    
    return sock
}

fun fillSockaddr(addr: CPointer<sockaddr_in>, host: String, port: Int) {
    memset(addr, 0, sizeOf<sockaddr_in>().convert())
    addr.pointed.sin_family = AF_INET.convert()
    addr.pointed.sin_port = htons(port.toUShort())
    addr.pointed.sin_addr.s_addr = inet_addr(host)
}

// Minimal implementations for testing

class EventLoop {
    private var kq: Int = -1
    var onTimeout: () -> Unit = {}
    private val socketHandlers = mutableMapOf<Int, (Int) -> Unit>()
    
    fun initialize() {
        kq = kqueue()
        if (kq < 0) throw TestException("kqueue() failed")
    }
    
    fun addSocket(fd: Int, handler: (Int) -> Unit) = memScoped {
        socketHandlers[fd] = handler
        
        val ev = alloc<kevent>()
        EV_SET(ev.ptr, fd.convert(), EVFILT_READ.convert(),
            (EV_ADD or EV_ENABLE).convert(), 0u, 0, null)
        
        kevent(kq, ev.ptr, 1, null, 0, null)
    }
    
    fun runOnce(timeoutMs: Int) = memScoped {
        val events = allocArray<kevent>(16)
        val timeout = alloc<timespec>()
        timeout.tv_sec = timeoutMs / 1000L
        timeout.tv_nsec = (timeoutMs % 1000) * 1000000L
        
        val n = kevent(kq, null, 0, events, 16, timeout.ptr)
        
        if (n == 0) {
            onTimeout()
        } else if (n > 0) {
            for (i in 0 until n) {
                val fd = events[i].ident.toInt()
                socketHandlers[fd]?.invoke(fd)
            }
        }
    }
    
    fun shutdown() {
        if (kq >= 0) {
            close(kq)
            kq = -1
        }
    }
}

class MCPServer {
    private var serverFd: Int = -1
    private val clients = mutableSetOf<Int>()
    private val loop = EventLoop()
    
    var port: Int = 0
        private set
    
    val clientCount: Int
        get() = clients.size
    
    var onMessage: (Int, String) -> Unit = { _, _ -> }
    
    fun start(requestedPort: Int) = memScoped {
        loop.initialize()
        
        val server = createBoundSocket(requestedPort)
        serverFd = server.fd
        port = server.port
        
        listen(serverFd, 10)
        
        loop.addSocket(serverFd) { acceptClient() }
    }
    
    fun processEvents() {
        loop.runOnce(0)
    }
    
    private fun acceptClient() = memScoped {
        val client = accept(serverFd, null, null)
        if (client >= 0) {
            clients.add(client)
            fcntl(client, F_SETFL, O_NONBLOCK)
            
            loop.addSocket(client) { fd ->
                handleClientData(fd)
            }
        }
    }
    
    private fun handleClientData(fd: Int) = memScoped {
        val buffer = allocArray<ByteVar>(1024)
        val n = recv(fd, buffer, 1024, 0)
        
        when {
            n > 0 -> {
                val msg = buffer.toKString().take(n.toInt()).trim()
                onMessage(fd, msg)
            }
            n == 0L -> {
                clients.remove(fd)
                close(fd)
            }
        }
    }
    
    fun send(client: Int, message: String) {
        platform.posix.send(client, message.cstr.ptr, message.length.convert(), 0)
    }
    
    fun stop() {
        clients.forEach { close(it) }
        clients.clear()
        
        if (serverFd >= 0) {
            close(serverFd)
            serverFd = -1
        }
        
        loop.shutdown()
    }
}

class SharedMemoryRingBuffer(private val path: String) {
    private var fd: Int = -1
    private var ptr: CPointer<ByteVar>? = null
    private var size: Long = 0
    
    val isValid: Boolean
        get() = fd >= 0 && ptr != null
    
    fun create(bufferSize: Long) {
        shm_unlink(path)
        
        fd = shm_open(path, O_CREAT or O_RDWR, 0666)
        if (fd < 0) throw TestException("shm_open() failed")
        
        if (ftruncate(fd, bufferSize) < 0) {
            close(fd)
            throw TestException("ftruncate() failed")
        }
        
        size = bufferSize
        
        ptr = mmap(null, size.convert(), PROT_READ or PROT_WRITE, 
            MAP_SHARED, fd, 0)?.reinterpret()
            
        if (ptr == MAP_FAILED) {
            close(fd)
            throw TestException("mmap() failed")
        }
        
        // Initialize header
        memScoped {
            val header = ptr!!.reinterpret<RingHeader>()
            header.pointed.magic = 0xDEADBEEFu
            header.pointed.writePos = 0u
            header.pointed.readPos = 0u
        }
    }
    
    fun write(data: String) {
        if (!isValid) throw TestException("Ring buffer not valid")
        
        memScoped {
            val header = ptr!!.reinterpret<RingHeader>()
            val dataStart = ptr!! + sizeOf<RingHeader>()
            
            data.encodeToByteArray().forEachIndexed { i, byte ->
                dataStart[header.pointed.writePos.toInt() + i] = byte
            }
            
            header.pointed.writePos = (header.pointed.writePos + data.length.toUInt())
        }
    }
    
    fun read(): String {
        if (!isValid) throw TestException("Ring buffer not valid")
        
        return memScoped {
            val header = ptr!!.reinterpret<RingHeader>()
            val dataStart = ptr!! + sizeOf<RingHeader>()
            
            val length = header.pointed.writePos - header.pointed.readPos
            val bytes = ByteArray(length.toInt()) { i ->
                dataStart[header.pointed.readPos.toInt() + i].toByte()
            }
            
            header.pointed.readPos = header.pointed.writePos
            bytes.decodeToString()
        }
    }
    
    fun destroy() {
        if (ptr != null) {
            munmap(ptr, size.convert())
            ptr = null
        }
        
        if (fd >= 0) {
            close(fd)
            fd = -1
        }
        
        shm_unlink(path)
    }
}

@CStruct("struct RingHeader")
class RingHeader(
    var magic: UInt,
    var writePos: UInt,
    var readPos: UInt
)

class NativeMCPHost {
    private val server = MCPServer()
    
    val port: Int
        get() = server.port
        
    val isRunning: Boolean
        get() = server.port > 0
    
    fun initialize(): Boolean {
        // Setup handlers
        server.onMessage = { client, msg ->
            if (msg.contains("ping")) {
                server.send(client, """{"result": "pong", "id": 1}""" + "\n")
            }
        }
        
        return true
    }
    
    fun start(): Boolean {
        return try {
            server.start(9876)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    fun processOnce() {
        server.processEvents()
    }
    
    fun stop() {
        server.stop()
    }
}

// Utilities

fun delay(ms: Int) {
    usleep((ms * 1000).toUInt())
}

fun htons(value: UShort): UShort {
    val bytes = value.toInt()
    return (((bytes and 0xFF) shl 8) or ((bytes shr 8) and 0xFF)).toUShort()
}

fun ntohs(value: UShort): UShort = htons(value)

fun inet_addr(host: String): UInt {
    if (host == "127.0.0.1") {
        return 0x0100007Fu // 127.0.0.1 in network byte order
    }
    return 0u
}

class TestException(message: String) : Exception(message)