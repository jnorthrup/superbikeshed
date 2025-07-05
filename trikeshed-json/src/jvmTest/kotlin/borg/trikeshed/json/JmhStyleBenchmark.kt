package borg.trikeshed.json

import borg.trikeshed.lib.*
import borg.trikeshed.core.*
import kotlin.system.measureNanoTime
import kotlin.test.Test

/**
 * JMH-style benchmarks without JMH dependency
 */
class JmhStyleBenchmark {
    
    @Test
    fun runBenchmarks() {
        println("\n=== TrikeShed JSON Scanner Performance Benchmarks ===")
        println("JVM: ${System.getProperty("java.version")} ${System.getProperty("java.vm.name")}")
        println("Timestamp: ${System.currentTimeMillis()}")
        
        warmup()
        
        benchmarkScanProperties()
        benchmarkQueryPerformance()
        benchmarkFingerprinting()
    }
    
    private fun warmup() {
        val json = generateJson(1000)
        repeat(10000) {
            json.simpleJson().properties()
            json.json().properties()
        }
    }
    
    private fun benchmarkScanProperties() {
        println("\n=== Benchmark: Property Scanning (ns/op) ===")
        println("Size   | Simple      | Bitmap      | Winner")
        println("-------|-------------|-------------|--------")
        
        for (size in listOf(10, 100, 1000, 10000)) {
            val json = generateJson(size)
            val iterations = when(size) {
                10 -> 10000
                100 -> 1000
                1000 -> 100
                else -> 10
            }
            
            val simpleTime = measure(iterations) {
                json.simpleJson().properties()
            }
            
            val bitmapTime = measure(iterations) {
                json.json().properties()
            }
            
            val winner = if (simpleTime < bitmapTime) {
                "Simple %.2fx".format(bitmapTime / simpleTime)
            } else {
                "Bitmap %.2fx".format(simpleTime / bitmapTime)
            }
            
            println("%-6d | %11.2f | %11.2f | %s".format(size, simpleTime, bitmapTime, winner))
        }
    }
    
    private fun benchmarkQueryPerformance() {
        println("\n=== Benchmark: Query Performance (ns/op) ===")
        println("Size   | Simple      | Bitmap      | Winner")
        println("-------|-------------|-------------|--------")
        
        for (size in listOf(100, 1000, 10000)) {
            val json = generateJson(size)
            val queries = listOf("field1", "field${size/2}", "field${size-1}")
            
            // Pre-scan
            val simpleScanner = json.simpleJson()
            val bitmapScanner = json.json()
            simpleScanner.properties()
            bitmapScanner.properties()
            
            val simpleTime = measure(1000) {
                queries.forEach { simpleScanner.query(it) }
            }
            
            val bitmapTime = measure(1000) {
                queries.forEach { bitmapScanner.query(it) }
            }
            
            val winner = if (simpleTime < bitmapTime) {
                "Simple %.2fx".format(bitmapTime / simpleTime)
            } else {
                "Bitmap %.2fx".format(simpleTime / bitmapTime)
            }
            
            println("%-6d | %11.2f | %11.2f | %s".format(size, simpleTime, bitmapTime, winner))
        }
    }
    
    private fun benchmarkFingerprinting() {
        println("\n=== Benchmark: Fingerprinting (μs/op) ===")
        println("Size   | Simple      | Bitmap      | Winner")
        println("-------|-------------|-------------|--------")
        
        for (size in listOf(100, 1000, 10000)) {
            val json = generateJson(size)
            
            val simpleTime = measure(100) {
                json.simpleJson().fingerprint()
            } / 1000.0 // Convert to microseconds
            
            val bitmapTime = measure(100) {
                json.json().fingerprint()
            } / 1000.0
            
            val winner = if (simpleTime < bitmapTime) {
                "Simple %.2fx".format(bitmapTime / simpleTime)
            } else {
                "Bitmap %.2fx".format(simpleTime / bitmapTime)
            }
            
            println("%-6d | %11.2f | %11.2f | %s".format(size, simpleTime, bitmapTime, winner))
        }
        
        println("\nBenchmark completed: ${System.currentTimeMillis()}")
    }
    
    private inline fun measure(iterations: Int, block: () -> Unit): Double {
        // Warmup
        repeat(iterations / 10) { block() }
        
        // Measure
        val times = DoubleArray(5)
        repeat(5) { run ->
            times[run] = measureNanoTime {
                repeat(iterations) { block() }
            }.toDouble() / iterations
        }
        
        return times.average()
    }
    
    private fun generateJson(count: Int): String = buildString {
        append("{")
        repeat(count) { i ->
            if (i > 0) append(",")
            append("\"field$i\":\"value$i\"")
        }
        append("}")
    }
}