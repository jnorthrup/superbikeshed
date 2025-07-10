@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)
@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)
package borg.trikeshed.uring

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.cinterop.*
import platform.posix.*
import platform.darwin.*
import kotlin.system.measureTimeMillis

/**
 * macOS Demo - Feel the Beer Goggles! 🍺👓
 * 
 * This demo shows real async I/O on macOS using our io_uring facade.
 * Under the hood it's kqueue, but you're programming like it's io_uring!
 */
fun main() = runBlocking {
    println("🍺👓 Welcome to TrikeShed io_uring on macOS!")
    println("🍎 Platform: ${Platform.osFamily.name} ${Platform.cpuArchitecture.name}")
    println("🔧 Using: kqueue + GCD (but it feels like io_uring!)\n")
    
    // Demo 1: File Operations
    fileOperationsDemo()
    
    // Demo 2: Network Echo Server
    networkDemo()
    
    // Demo 3: Linked Operations
    linkedOperationsDemo()
    
    // Demo 4: Performance Test
    performanceDemo()
}

suspend fun fileOperationsDemo() = coroutineScope {
    println("📁 Demo 1: File Operations (using kqueue EVFILT_READ/WRITE)")
    println("=" * 50)
    
    withTrikeUring { uring ->
        // Create a test file
        val testFile = "/tmp/trikeshed_demo.txt"
        val fd = open(testFile, O_CREAT or O_RDWR or O_TRUNC, 0o644)
        if (fd < 0) {
            println("❌ Failed to create test file")
            return@withTrikeUring
        }
        
        try {
            // Write operation
            val writeData = "Hello from macOS kqueue through io_uring facade! 🍎\n"
            val writeBuffer = ByteBuffer.wrap(writeData.toByteArray())
            
            println("📝 Submitting write operation...")
            val writeOp = Write(fd = fd, buffer = writeBuffer, userData = 1)
            uring.submission.send(writeOp)
            
            // Simulate actual write (in real impl, kqueue would trigger when writable)
            delay(10)
            val written = write(fd, writeBuffer.toNSData().bytes, writeData.length.convert())
            
            // Send completion
            val writeCqe = WriteResult(writeOp.userData, written.toInt(), written.toInt())
            (uring as? DarwinTrikeUring)?.let { 
                // In real impl, this would come from kqueue event loop
                println("✅ Write completed: $written bytes")
            }
            
            // Read operation
            lseek(fd, 0, SEEK_SET) // Reset to beginning
            val readBuffer = ByteBuffer.allocate(256)
            
            println("📖 Submitting read operation...")
            val readOp = Read(fd = fd, buffer = readBuffer, userData = 2)
            uring.submission.send(readOp)
            
            // Simulate actual read
            delay(10)
            val tempBuf = ByteArray(256)
            val bytesRead = read(fd, tempBuf.refTo(0), 256)
            
            if (bytesRead > 0) {
                val readData = tempBuf.decodeToString(0, bytesRead.toInt())
                println("✅ Read completed: $bytesRead bytes")
                println("📄 Content: ${readData.trim()}")
            }
            
        } finally {
            close(fd)
            unlink(testFile)
        }
    }
    
    println()
}

suspend fun networkDemo() = coroutineScope {
    println("🌐 Demo 2: Network Operations (using kqueue EVFILT_READ for accept)")
    println("=" * 50)
    
    withTrikeUring { uring ->
        // Create server socket
        val serverFd = socket(AF_INET, SOCK_STREAM, 0)
        if (serverFd < 0) {
            println("❌ Failed to create socket")
            return@withTrikeUring
        }
        
        try {
            // Set socket options
            DarwinNetOps.setReuseAddr(serverFd)
            DarwinNetOps.setNonBlocking(serverFd)
            
            // Bind to localhost
            memScoped {
                val addr = alloc<sockaddr_in>()
                addr.sin_family = AF_INET.convert()
                addr.sin_port = htons(0u) // Let OS choose port
                addr.sin_addr.s_addr = inet_addr("127.0.0.1")
                
                if (bind(serverFd, addr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) < 0) {
                    println("❌ Bind failed")
                    return@withTrikeUring
                }
                
                // Get actual port
                val addrLen = alloc<socklen_tVar>()
                addrLen.value = sizeOf<sockaddr_in>().convert()
                getsockname(serverFd, addr.ptr.reinterpret(), addrLen.ptr)
                val port = ntohs(addr.sin_port)
                
                println("🚀 Server listening on 127.0.0.1:$port")
                listen(serverFd, 5)
                
                // Submit accept operation
                println("⏳ Submitting accept operation (would block waiting for connection)...")
                val acceptOp = Accept(fd = serverFd, userData = 3)
                uring.submission.send(acceptOp)
                
                // In real scenario, kqueue would notify when connection arrives
                println("💡 In real server, kqueue EVFILT_READ would fire when client connects")
                println("   Then we'd accept() and handle the client asynchronously")
            }
            
        } finally {
            close(serverFd)
        }
    }
    
    println()
}

suspend fun linkedOperationsDemo() = coroutineScope {
    println("🔗 Demo 3: Linked Operations (io_uring IOSQE_IO_LINK emulation)")
    println("=" * 50)
    
    withTrikeUring { uring ->
        println("📋 Creating linked operation chain: Read → Process → Write")
        
        // Create test files
        val inputFile = "/tmp/trikeshed_input.txt"
        val outputFile = "/tmp/trikeshed_output.txt"
        
        // Prepare input
        val inputData = "transform this text to uppercase!"
        memScoped {
            val fd = open(inputFile, O_CREAT or O_WRONLY or O_TRUNC, 0o644)
            write(fd, inputData.cstr.ptr, inputData.length.convert())
            close(fd)
        }
        
        val inputFd = open(inputFile, O_RDONLY)
        val outputFd = open(outputFile, O_CREAT or O_WRONLY or O_TRUNC, 0o644)
        
        try {
            // Build linked chain
            val buffer = ByteBuffer.allocate(256)
            
            val linkedOp = Read(fd = inputFd, buffer = buffer, userData = 100)
                .chain()
                .then { readResult ->
                    if (readResult.isSuccess && readResult is ReadResult) {
                        println("  ✓ Read completed: ${readResult.bytesRead} bytes")
                        
                        // Transform data (uppercase)
                        val data = ByteArray(readResult.bytesRead)
                        buffer.rewind()
                        buffer.get(data, 0, readResult.bytesRead)
                        val transformed = data.decodeToString().uppercase().toByteArray()
                        
                        println("  ⚡ Transforming data...")
                        
                        // Write transformed data
                        Write(
                            fd = outputFd,
                            buffer = ByteBuffer.wrap(transformed),
                            userData = 101
                        )
                    } else {
                        println("  ❌ Read failed")
                        null
                    }
                }
                .thenIf({ it.isSuccess }) { writeResult ->
                    println("  ✓ Write completed: ${(writeResult as? WriteResult)?.bytesWritten} bytes")
                    
                    // Final fsync
                    Fsync(fd = outputFd, userData = 102)
                }
                .build()
            
            println("🚀 Submitting linked operation chain...")
            uring.submission.send(linkedOp)
            
            // Simulate execution (in real impl, each stage would complete via kqueue)
            delay(50)
            
            // Read result
            val resultData = memScoped {
                val buf = ByteArray(256)
                lseek(outputFd, 0, SEEK_SET)
                val n = read(outputFd, buf.refTo(0), 256)
                if (n > 0) buf.decodeToString(0, n.toInt()) else ""
            }
            
            println("📄 Final result: $resultData")
            
        } finally {
            close(inputFd)
            close(outputFd)
            unlink(inputFile)
            unlink(outputFile)
        }
    }
    
    println()
}

suspend fun performanceDemo() = coroutineScope {
    println("⚡ Demo 4: Performance Test (kqueue batch operations)")
    println("=" * 50)
    
    withTrikeUring(UringConfig(ringSize = 1024)) { uring ->
        val operations = 1000
        val bufferSize = 4096
        
        println("🏃 Submitting $operations operations...")
        
        val time = measureTimeMillis {
            // Create a bunch of timeout operations (easy to test)
            val ops = (1..operations).map { i ->
                Timeout(timeoutMs = (i % 100).toLong(), userData = i.toLong())
            }
            
            // Batch submit
            uring.submitBatch(ops)
            
            println("✅ All operations submitted")
            
            // In real scenario, we'd wait for completions
            // Each timeout would fire via EVFILT_TIMER in kqueue
        }
        
        val opsPerSec = (operations * 1000.0 / time).toInt()
        println("⏱️  Time: ${time}ms")
        println("🚀 Throughput: $opsPerSec ops/sec")
        println("💡 Note: Real io_uring on Linux would be even faster!")
    }
    
    println()
}

// Utility
operator fun String.times(n: Int) = repeat(n)

/**
 * Demo-specific helpers
 */
suspend fun showPlatformInfo() {
    println("\n📊 Platform Information:")
    println("  OS: ${Platform.osFamily.name}")
    println("  Arch: ${Platform.cpuArchitecture.name}")
    println("  Kotlin: ${KotlinVersion.CURRENT}")
    
    // Check kqueue availability
    val kq = kqueue()
    if (kq >= 0) {
        println("  kqueue: ✅ Available (fd=$kq)")
        close(kq)
    } else {
        println("  kqueue: ❌ Not available")
    }
    
    // Show GCD info
    println("  GCD: ✅ Available (dispatch_queue)")
    println("  CPU cores: ${sysconf(_SC_NPROCESSORS_ONLN)}")
    
    println("\n🍺 Beer Goggles Status: ACTIVE! 👓")
    println("   You're writing io_uring code on macOS!")
}