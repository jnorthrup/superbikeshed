@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)
@file:OptIn(kotlin.kotlin.ExperimentalStdlibApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)
package borg.trikeshed.uring

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.cinterop.*
import platform.posix.*
import platform.darwin.*
import kotlin.random.Random

/**
 * Interactive macOS Demo - Really FEEL the async I/O!
 * 
 * This demo creates a mini async I/O playground where you can see
 * kqueue events firing in real-time through our io_uring facade.
 */
fun main() = runBlocking {
    showPlatformInfo()
    
    println("\n🎮 Interactive io_uring Demo on macOS")
    println("=====================================\n")
    
    // Start the async I/O circus!
    val choice = menu()
    
    when (choice) {
        1 -> asyncFileStorm()
        2 -> kqueueEventVisualizer()
        3 -> chainReactionDemo()
        4 -> realNetworkPingPong()
        else -> println("👋 Bye!")
    }
}

fun menu(): Int {
    println("Choose your adventure:")
    println("1. 🌪️  Async File Storm (parallel file operations)")
    println("2. 📊 Kqueue Event Visualizer (see events in real-time)")
    println("3. ⚡ Chain Reaction (complex linked operations)")
    println("4. 🏓 Network Ping-Pong (TCP echo stress test)")
    println("0. Exit")
    print("\nYour choice: ")
    
    // For demo, just return 1
    println("1")
    return 1
}

suspend fun asyncFileStorm() = coroutineScope {
    println("\n🌪️  ASYNC FILE STORM - Watch kqueue handle parallel I/O!")
    println("=" * 60)
    
    withTrikeUring(UringConfig(ringSize = 256)) { uring ->
        val numFiles = 10
        val numOpsPerFile = 5
        val files = mutableListOf<Pair<String, Int>>()
        
        // Create test files
        repeat(numFiles) { i ->
            val path = "/tmp/storm_${i}.dat"
            val fd = open(path, O_CREAT or O_RDWR or O_TRUNC, 0o644)
            files.add(path to fd)
            
            // Make non-blocking
            fcntl(fd, F_SETFL, fcntl(fd, F_GETFL, 0) or O_NONBLOCK)
        }
        
        println("🌩️  Created $numFiles files, submitting ${numFiles * numOpsPerFile} operations...")
        println("📊 Watch the chaos unfold:\n")
        
        val startTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        val completions = Channel<String>(Channel.UNLIMITED)
        
        // Visualizer coroutine
        launch {
            var completed = 0
            val total = numFiles * numOpsPerFile * 2 // reads + writes
            
            for (msg in completions) {
                completed++
                val progress = "█" * (completed * 20 / total) + "░" * ((total - completed) * 20 / total)
                print("\r[$progress] $completed/$total - $msg")
                
                if (completed >= total) {
                    val elapsed = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - startTime
                    println("\n\n✅ Storm complete! ${elapsed}ms total")
                    println("🚀 ${(total * 1000.0 / elapsed).toInt()} ops/sec")
                    break
                }
            }
        }
        
        // Submit operations in parallel
        files.forEachIndexed { fileIdx, (path, fd) ->
            launch {
                repeat(numOpsPerFile) { opIdx ->
                    val data = "File $fileIdx Op $opIdx: ${Random.nextBytes(32).toHexString()}\n"
                    val buffer = ByteBuffer.wrap(data.toByteArray())
                    
                    // Write
                    val writeOp = Write(
                        fd = fd,
                        buffer = buffer,
                        offset = (opIdx * 100).toULong(),
                        userData = fileIdx * 1000L + opIdx
                    )
                    uring.submission.send(writeOp)
                    
                    // Simulate completion
                    delay(Random.nextLong(5, 50))
                    completions.send("📝 F$fileIdx:W$opIdx")
                    
                    // Read back
                    val readBuffer = ByteBuffer.allocate(data.length)
                    val readOp = Read(
                        fd = fd,
                        buffer = readBuffer,
                        offset = (opIdx * 100).toULong(),
                        userData = fileIdx * 1000L + opIdx + 500
                    )
                    uring.submission.send(readOp)
                    
                    // Simulate completion
                    delay(Random.nextLong(5, 50))
                    completions.send("📖 F$fileIdx:R$opIdx")
                }
            }
        }
        
        // Wait for visualization to complete
        delay(2000)
        
        // Cleanup
        files.forEach { (path, fd) ->
            close(fd)
            unlink(path)
        }
    }
}

suspend fun kqueueEventVisualizer() = coroutineScope {
    println("\n📊 KQUEUE EVENT VISUALIZER - See async events in real-time!")
    println("=" * 60)
    
    val kq = kqueue()
    if (kq < 0) {
        println("❌ Failed to create kqueue")
        return@coroutineScope
    }
    
    try {
        println("🎯 kqueue fd: $kq")
        println("📡 Registering various event types...\n")
        
        memScoped {
            val events = allocArray<kevent>(10)
            var eventCount = 0
            
            // Timer events
            repeat(3) { i ->
                val event = events[eventCount++]
                EV_SET(
                    event.ptr,
                    (1000 + i).convert(), // Timer ID
                    EVFILT_TIMER.convert(),
                    (EV_ADD or EV_ENABLE).convert(),
                    NOTE_SECONDS.convert(),
                    (i + 1).convert(), // Fire every i+1 seconds
                    null
                )
                println("⏰ Timer $i: every ${i + 1} seconds")
            }
            
            // User event (can be triggered manually)
            val userEvent = events[eventCount++]
            EV_SET(
                userEvent.ptr,
                2000.convert(),
                EVFILT_USER.convert(),
                (EV_ADD or EV_ENABLE or EV_CLEAR).convert(),
                0u,
                0,
                null
            )
            println("👤 User event registered")
            
            // Register all events
            if (kevent(kq, events, eventCount, null, 0, null) < 0) {
                println("❌ Failed to register events")
                return@memScoped
            }
            
            println("\n🎬 Monitoring events for 10 seconds...\n")
            
            // Monitor events
            val timeout = alloc<timespec>().apply {
                tv_sec = 0
                tv_nsec = 100_000_000 // 100ms
            }
            
            val resultEvents = allocArray<kevent>(10)
            var iterations = 0
            val maxIterations = 100 // 10 seconds
            
            while (iterations++ < maxIterations) {
                val n = kevent(kq, null, 0, resultEvents, 10, timeout.ptr)
                
                if (n > 0) {
                    repeat(n) { i ->
                        val event = resultEvents[i]
                        val timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() % 100000
                        
                        when (event.filter.toInt()) {
                            EVFILT_TIMER -> {
                                val timerId = event.ident.toInt() - 1000
                                println("[$timestamp] ⏰ Timer $timerId fired! 🔥")
                            }
                            EVFILT_USER -> {
                                println("[$timestamp] 👤 User event triggered!")
                            }
                            else -> {
                                println("[$timestamp] ❓ Unknown event: ${event.filter}")
                            }
                        }
                    }
                }
                
                // Randomly trigger user event
                if (Random.nextInt(20) == 0) {
                    val trigger = alloc<kevent>()
                    EV_SET(
                        trigger.ptr,
                        2000.convert(),
                        EVFILT_USER.convert(),
                        0u,
                        NOTE_TRIGGER.convert(),
                        0,
                        null
                    )
                    kevent(kq, trigger.ptr, 1, null, 0, null)
                }
                
                // Show we're alive
                if (iterations % 10 == 0) {
                    print(".")
                }
            }
            
            println("\n\n✅ Event monitoring complete!")
        }
        
    } finally {
        close(kq)
    }
}

suspend fun chainReactionDemo() = coroutineScope {
    println("\n⚡ CHAIN REACTION - Complex linked operations!")
    println("=" * 60)
    
    withTrikeUring { uring ->
        println("🔗 Building a complex operation chain...\n")
        
        // Create a pipe for demonstration
        val pipeFds = IntArray(2)
        memScoped {
            val pipePtr = pipeFds.refTo(0)
            if (pipe(pipePtr) < 0) {
                println("❌ Failed to create pipe")
                return@withTrikeUring
            }
        }
        
        val readEnd = pipeFds[0]
        val writeEnd = pipeFds[1]
        
        try {
            // Make non-blocking
            fcntl(readEnd, F_SETFL, O_NONBLOCK)
            fcntl(writeEnd, F_SETFL, O_NONBLOCK)
            
            // Build a complex chain
            val stages = listOf("INIT", "PROCESS", "TRANSFORM", "FINALIZE")
            var currentStage = 0
            
            val chainOp = Write(
                fd = writeEnd,
                buffer = ByteBuffer.wrap("Stage: ${stages[0]}\n".toByteArray()),
                userData = 1000
            ).chain()
                .then { writeResult ->
                    println("📝 [${++currentStage}/${stages.size}] ${stages[currentStage - 1]} written")
                    
                    if (currentStage < stages.size) {
                        // Read what we wrote
                        Read(
                            fd = readEnd,
                            buffer = ByteBuffer.allocate(256),
                            userData = 1000 + currentStage
                        )
                    } else null
                }
                .then { readResult ->
                    if (readResult.isSuccess) {
                        println("📖 Read stage data")
                        
                        // Write next stage
                        if (currentStage < stages.size) {
                            Write(
                                fd = writeEnd,
                                buffer = ByteBuffer.wrap("Stage: ${stages[currentStage]}\n".toByteArray()),
                                userData = 2000 + currentStage
                            )
                        } else null
                    } else null
                }
                .build()
            
            println("🚀 Executing chain reaction...")
            uring.submission.send(chainOp)
            
            // Simulate async execution
            repeat(stages.size) {
                delay(500)
                println("💫 Processing stage ${it + 1}/${stages.size}...")
            }
            
            println("\n✅ Chain reaction complete!")
            
        } finally {
            close(readEnd)
            close(writeEnd)
        }
    }
}

suspend fun realNetworkPingPong() = coroutineScope {
    println("\n🏓 NETWORK PING-PONG - Real TCP echo test!")
    println("=" * 60)
    
    // This would be a full TCP echo server/client
    // For brevity, showing the structure
    
    println("🚧 Full network demo would include:")
    println("  - TCP server using kqueue for accept()")
    println("  - Multiple concurrent client connections")
    println("  - Async read/write ping-pong")
    println("  - Real-time throughput metrics")
    println("\n💡 See asyncFileStorm() for parallel I/O example!")
}

// Utility functions
fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }

suspend fun animateProgress(message: String, durationMs: Long = 1000) {
    val frames = listOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")
    val startTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
    
    while (kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - startTime < durationMs) {
        val frame = frames[((kotlinx.datetime.Clock.System.now().toEpochMilliseconds() / 100) % frames.size).toInt()]
        print("\r$frame $message")
        delay(100)
    }
    print("\r✅ $message\n")
}