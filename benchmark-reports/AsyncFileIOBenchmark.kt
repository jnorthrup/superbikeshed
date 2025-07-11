import borg.trikeshed.io.AsyncFiles
import borg.trikeshed.io.Files
import kotlinx.coroutines.runBlocking
import kotlin.system.measureTimeMillis
import java.io.File

fun main() = runBlocking {
    val testFile = "benchmark_test.txt"
    val largeContent = "Benchmark content ".repeat(10000)
    
    // Create test file
    File(testFile).writeText(largeContent)
    
    println("=== Async File I/O Benchmark ===")
    
    // Benchmark sync read
    val syncReadTime = measureTimeMillis {
        repeat(100) {
            Files.readString(testFile)
        }
    }
    
    // Benchmark async read
    val asyncReadTime = measureTimeMillis {
        repeat(100) {
            AsyncFiles.readString(testFile)
        }
    }
    
    // Benchmark sync write
    val syncWriteTime = measureTimeMillis {
        repeat(10) {
            Files.write(testFile, largeContent)
        }
    }
    
    // Benchmark async write
    val asyncWriteTime = measureTimeMillis {
        repeat(10) {
            AsyncFiles.write(testFile, largeContent)
        }
    }
    
    println("Sync Read (100x): ${syncReadTime}ms")
    println("Async Read (100x): ${asyncReadTime}ms")
    println("Sync Write (10x): ${syncWriteTime}ms")
    println("Async Write (10x): ${asyncWriteTime}ms")
    println("Read Speedup: ${syncReadTime.toDouble() / asyncReadTime}")
    println("Write Speedup: ${syncWriteTime.toDouble() / asyncWriteTime}")
    
    // Cleanup
    File(testFile).delete()
} 