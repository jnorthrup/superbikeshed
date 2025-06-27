package com.superbikeshed.trikeshed.native

import com.superbikeshed.trikeshed.native.uring.*
import com.superbikeshed.trikeshed.native.quiche.*
import kotlinx.cinterop.*
import platform.posix.*
import kotlin.concurrent.AtomicReference
import kotlinx.coroutines.*

fun main() = runBlocking {
    println("Trikeshed Native Linux io_uring Host Starting...")
    
    val trikeshedHost = TrikeshedHost()
    trikeshedHost.initialize()
    trikeshedHost.run()
}

class TrikeshedHost {
    private val ring = nativeHeap.alloc<io_uring>()
    private val jvmProcess = AtomicReference<CPointer<FILE>?>(null)
    private val wasmProcess = AtomicReference<CPointer<FILE>?>(null)
    private val ipcSocketPair = IntArray(2)
    
    fun initialize() {
        // Initialize io_uring with 4096 entries
        val ret = trikeshed_io_uring_queue_init(4096u, ring.ptr, 0u)
        if (ret < 0) {
            error("Failed to initialize io_uring: $ret")
        }
        println("io_uring initialized with 4096 entries")
        
        // Create IPC socket pair for JVM/WASM communication
        if (socketpair(AF_UNIX, SOCK_STREAM, 0, ipcSocketPair.refTo(0)) < 0) {
            error("Failed to create IPC socket pair")
        }
        
        // Make sockets non-blocking
        fcntl(ipcSocketPair[0], F_SETFL, O_NONBLOCK)
        fcntl(ipcSocketPair[1], F_SETFL, O_NONBLOCK)
        
        println("IPC socket pair created: ${ipcSocketPair[0]} <-> ${ipcSocketPair[1]}")
    }
    
    suspend fun run() = coroutineScope {
        // Launch JVM subprocess
        launch(Dispatchers.IO) {
            launchJVM()
        }
        
        // Launch WASM VM subprocess
        launch(Dispatchers.IO) {
            launchWASM()
        }
        
        // Launch QUIC server
        launch(Dispatchers.IO) {
            runQUICServer()
        }
        
        // Launch CouchDB protocol handler
        launch(Dispatchers.IO) {
            runCouchDBProtocol()
        }
        
        // Main io_uring event loop
        runIOUringEventLoop()
    }
    
    private fun launchJVM() {
        val jvmCmd = buildString {
            append("java ")
            append("-Xmx4G ")
            append("-XX:+UseZGC ")
            append("-XX:+EnableDynamicAgentLoading ")
            append("-Djava.library.path=/usr/local/lib ")
            append("-Dtrikeshed.ipc.fd=${ipcSocketPair[1]} ")
            append("-jar /app/trikeshed-jvm.jar")
        }
        
        jvmProcess.value = popen(jvmCmd, "r")
        if (jvmProcess.value == null) {
            error("Failed to launch JVM process")
        }
        println("JVM process launched with IPC fd: ${ipcSocketPair[1]}")
    }
    
    private fun launchWASM() {
        val wasmCmd = buildString {
            append("wasmtime ")
            append("--preload liburing=/usr/lib/liburing.so ")
            append("--env TRIKESHED_IPC_FD=${ipcSocketPair[1]} ")
            append("--enable-all ")
            append("/app/trikeshed.wasm")
        }
        
        wasmProcess.value = popen(wasmCmd, "r")
        if (wasmProcess.value == null) {
            error("Failed to launch WASM process")
        }
        println("WASM VM launched with IPC fd: ${ipcSocketPair[1]}")
    }
    
    private suspend fun runQUICServer() {
        memScoped {
            val config = trikeshed_quiche_config_new(QUICHE_PROTOCOL_VERSION)
            
            // Configure QUIC
            trikeshed_quiche_config_set_max_idle_timeout(config, 30000)
            trikeshed_quiche_config_set_max_recv_udp_payload_size(config, 1350)
            trikeshed_quiche_config_set_max_send_udp_payload_size(config, 1350)
            trikeshed_quiche_config_set_initial_max_data(config, 10_000_000)
            trikeshed_quiche_config_set_initial_max_stream_data_bidi_local(config, 1_000_000)
            trikeshed_quiche_config_set_initial_max_stream_data_bidi_remote(config, 1_000_000)
            trikeshed_quiche_config_set_initial_max_streams_bidi(config, 100)
            trikeshed_quiche_config_enable_early_data(config)
            
            println("QUIC server configured and ready")
            
            // Create UDP socket for QUIC
            val sock = socket(AF_INET, SOCK_DGRAM, 0)
            if (sock < 0) {
                error("Failed to create QUIC socket")
            }
            
            // Bind to port 5984 (CouchDB default)
            val addr = alloc<sockaddr_in>()
            addr.sin_family = AF_INET.convert()
            addr.sin_port = htons(5984u)
            addr.sin_addr.s_addr = INADDR_ANY
            
            if (bind(sock, addr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) < 0) {
                error("Failed to bind QUIC socket")
            }
            
            // Make socket non-blocking
            fcntl(sock, F_SETFL, O_NONBLOCK)
            
            println("QUIC server listening on port 5984")
            
            // Register with io_uring for accept
            registerQUICSocket(sock)
        }
    }
    
    private fun registerQUICSocket(sock: Int) {
        memScoped {
            val sqe = trikeshed_io_uring_get_sqe(ring.ptr)
            if (sqe == null) {
                error("Failed to get SQE for QUIC socket")
            }
            
            val buf = nativeHeap.allocArray<ByteVar>(65536)
            val addr = nativeHeap.alloc<sockaddr_storage>()
            val addrlen = nativeHeap.alloc<socklen_tVar>()
            addrlen.value = sizeOf<sockaddr_storage>().convert()
            
            trikeshed_io_uring_prep_recv(sqe, sock, buf, 65536, MSG_DONTWAIT)
            sqe.pointed.user_data = sock.toULong()
            
            trikeshed_io_uring_submit(ring.ptr)
        }
    }
    
    private suspend fun runCouchDBProtocol() {
        // CouchDB protocol handler implementation
        println("CouchDB protocol handler initialized")
        
        // Handle CouchDB endpoints:
        // - Database CRUD operations
        // - Document operations
        // - View queries
        // - Replication
        // - Changes feed
        // - Attachments
    }
    
    private suspend fun runIOUringEventLoop() {
        memScoped {
            val cqe = alloc<CPointerVar<io_uring_cqe>>()
            
            while (true) {
                val ret = trikeshed_io_uring_wait_cqe(ring.ptr, cqe.ptr)
                if (ret < 0) {
                    println("io_uring wait error: $ret")
                    continue
                }
                
                val completion = cqe.value
                if (completion != null) {
                    val res = completion.pointed.res
                    val userData = completion.pointed.user_data
                    
                    when {
                        res < 0 -> println("Operation failed for fd $userData: $res")
                        else -> handleCompletion(userData.toInt(), res)
                    }
                    
                    trikeshed_io_uring_cqe_seen(ring.ptr, completion)
                }
                
                yield() // Allow other coroutines to run
            }
        }
    }
    
    private fun handleCompletion(fd: Int, res: Int) {
        when (fd) {
            ipcSocketPair[0] -> handleIPCMessage(res)
            else -> handleNetworkIO(fd, res)
        }
    }
    
    private fun handleIPCMessage(bytesRead: Int) {
        // Handle IPC messages between JVM and WASM
        println("IPC message received: $bytesRead bytes")
    }
    
    private fun handleNetworkIO(fd: Int, bytesTransferred: Int) {
        // Handle network I/O completions
        println("Network I/O on fd $fd: $bytesTransferred bytes")
    }
    
    fun cleanup() {
        trikeshed_io_uring_queue_exit(ring.ptr)
        close(ipcSocketPair[0])
        close(ipcSocketPair[1])
        
        jvmProcess.value?.let { pclose(it) }
        wasmProcess.value?.let { pclose(it) }
    }
}

private const val QUICHE_PROTOCOL_VERSION = 0x00000001u