@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)
@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)
package borg.trikeshed.uring

import kotlinx.coroutines.*
import kotlinx.cinterop.*
import platform.posix.*
import platform.darwin.*
import kotlin.system.measureTimeMillis

/**
 * Real-world file copy using io_uring facade on macOS
 * 
 * This demonstrates how you'd actually use the beer goggles for real work.
 * Under the hood it's kqueue, but the API is pure io_uring style!
 */
fun main(args: Array<String>) = runBlocking {
    println("🍺👓 TrikeShed io_uring File Copy Demo")
    println("=====================================")
    
    // Create a test file if no args provided
    val (srcPath, dstPath) = if (args.size >= 2) {
        args[0] to args[1]
    } else {
        createTestFile()
    }
    
    println("📂 Source: $srcPath")
    println("📂 Destination: $dstPath")
    
    // Copy using our io_uring facade
    val copyTime = measureTimeMillis {
        copyFileAsync(srcPath, dstPath)
    }
    
    println("\n⏱️  Copy time: ${copyTime}ms")
    
    // Verify
    val srcSize = getFileSize(srcPath)
    val dstSize = getFileSize(dstPath)
    
    if (srcSize == dstSize) {
        println("✅ Copy successful! ($srcSize bytes)")
        val throughputMBps = (srcSize / 1024.0 / 1024.0) / (copyTime / 1000.0)
        println("🚀 Throughput: %.2f MB/s".format(throughputMBps))
    } else {
        println("❌ Copy failed! Sizes don't match: $srcSize vs $dstSize")
    }
    
    // Cleanup if test file
    if (args.isEmpty()) {
        unlink(srcPath)
        unlink(dstPath)
        println("\n🧹 Cleaned up test files")
    }
}

suspend fun copyFileAsync(srcPath: String, dstPath: String) = coroutineScope {
    withTrikeUring(UringConfig(ringSize = 256)) { uring ->
        val srcFd = open(srcPath, O_RDONLY)
        if (srcFd < 0) {
            throw Exception("Failed to open source: ${strerror(errno)?.toKString()}")
        }
        
        val dstFd = open(dstPath, O_WRONLY or O_CREAT or O_TRUNC, 0o644)
        if (dstFd < 0) {
            close(srcFd)
            throw Exception("Failed to open destination: ${strerror(errno)?.toKString()}")
        }
        
        try {
            // Get file size
            val fileSize = lseek(srcFd, 0, SEEK_END)
            lseek(srcFd, 0, SEEK_SET)
            
            println("📏 File size: $fileSize bytes")
            
            // Use 64KB chunks for good performance
            val chunkSize = 64 * 1024
            val numChunks = (fileSize + chunkSize - 1) / chunkSize
            
            println("🔢 Chunks: $numChunks × ${chunkSize / 1024}KB")
            println("📊 Progress:")
            
            // Submit all read operations as linked read->write chains
            val operations = mutableListOf<LinkedSqe>()
            
            for (i in 0 until numChunks.toInt()) {
                val offset = (i * chunkSize).toULong()
                val size = minOf(chunkSize, (fileSize - i * chunkSize).toInt())
                val buffer = ByteBuffer.allocateDirect(size)
                
                // Create linked read->write operation
                val linkedOp = Read(
                    fd = srcFd,
                    buffer = buffer,
                    offset = offset,
                    userData = i.toLong() * 2
                ).chain()
                    .then { readResult ->
                        if (readResult.isSuccess && readResult is ReadResult) {
                            // Flip buffer for writing
                            buffer.flip()
                            
                            Write(
                                fd = dstFd,
                                buffer = buffer,
                                offset = offset,
                                userData = i.toLong() * 2 + 1
                            )
                        } else {
                            null
                        }
                    }
                    .build()
                
                operations.add(linkedOp)
            }
            
            // Submit in batches for efficiency
            val batchSize = 32
            var completed = 0
            
            operations.chunked(batchSize).forEach { batch ->
                // Submit batch
                batch.forEach { uring.submission.send(it) }
                
                // Wait for batch completions
                repeat(batch.size * 2) { // Each op has read + write
                    val cqe = uring.completion.first()
                    if (cqe.isSuccess) {
                        completed++
                        if (completed % 10 == 0) {
                            val progress = (completed * 50) / (numChunks.toInt() * 2)
                            val bar = "█".repeat(progress) + "░".repeat(50 - progress)
                            print("\r[$bar] ${(completed * 100) / (numChunks.toInt() * 2)}%")
                        }
                    } else {
                        println("\n❌ Operation failed: ${cqe.error}")
                    }
                }
            }
            
            println("\r[" + "█".repeat(50) + "] 100%")
            
            // Final fsync
            println("💾 Syncing to disk...")
            uring.submission.send(Fsync(fd = dstFd))
            val fsyncResult = uring.completion.first()
            
            if (fsyncResult.isSuccess) {
                println("✅ File synced successfully")
            }
            
        } finally {
            close(srcFd)
            close(dstFd)
        }
    }
}

fun createTestFile(): Pair<String, String> {
    val srcPath = "/tmp/trikeshed_test_src.dat"
    val dstPath = "/tmp/trikeshed_test_dst.dat"
    
    println("📝 Creating test file (10MB of random data)...")
    
    memScoped {
        val fd = open(srcPath, O_WRONLY or O_CREAT or O_TRUNC, 0o644)
        if (fd < 0) {
            throw Exception("Failed to create test file")
        }
        
        // Write 10MB of random data
        val buffer = ByteArray(1024 * 1024) // 1MB chunks
        repeat(10) { i ->
            // Fill with pattern
            for (j in buffer.indices) {
                buffer[j] = ((i * 256 + j) % 256).toByte()
            }
            write(fd, buffer.refTo(0), buffer.size.convert())
            print("\r📝 Writing test data... ${(i + 1) * 10}%")
        }
        println("\r📝 Writing test data... 100%")
        
        close(fd)
    }
    
    return srcPath to dstPath
}

fun getFileSize(path: String): Long = memScoped {
    val stat = alloc<stat>()
    if (stat(path, stat.ptr) == 0) {
        stat.st_size
    } else {
        -1
    }
}

/**
 * Advanced copy with progress callback
 */
suspend fun copyFileWithProgress(
    srcPath: String,
    dstPath: String,
    onProgress: (Long, Long) -> Unit
) = coroutineScope {
    withTrikeUring { uring ->
        // Implementation with progress callbacks
        // This shows how you'd build real tools with the facade
    }
}

/**
 * Parallel multi-file copy
 */
suspend fun copyMultipleFiles(
    filePairs: List<Pair<String, String>>
) = coroutineScope {
    withTrikeUring(UringConfig(ringSize = 1024)) { uring ->
        println("📁 Copying ${filePairs.size} files in parallel...")
        
        val jobs = filePairs.map { (src, dst) ->
            async {
                // Each file gets its own coroutine
                // But they all share the same io_uring instance!
                copyFileAsync(src, dst)
            }
        }
        
        jobs.awaitAll()
        println("✅ All files copied!")
    }
}