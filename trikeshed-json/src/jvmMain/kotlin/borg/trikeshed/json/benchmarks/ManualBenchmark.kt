package borg.trikeshed.json.benchmarks

import borg.trikeshed.lib.*
import borg.trikeshed.core.*
import kotlin.system.measureNanoTime
import kotlin.time.*

/**
 * Manual JMH-style benchmarks for JSON scanners
 */
class ManualBenchmark {
    
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            println("=== TrikeShed JSON Scanner JMH-Style Benchmarks ===")
            println("JVM: ${System.getProperty("java.version")} ${System.getProperty("java.vm.name")}")
            println("Cores: ${Runtime.getRuntime().availableProcessors()}")
            println()
            
            val benchmark = ManualBenchmark()
            benchmark.runAllBenchmarks()
        }
    }
    
    private fun runAllBenchmarks() {
        warmup()
        
        println("=== Benchmark: scanProperties ===")
        benchmarkScanProperties()
        
        println("\n=== Benchmark: scanAndQuery ===")
        benchmarkScanAndQuery()
        
        println("\n=== Benchmark: fingerprint ===")
        benchmarkFingerprint()
        
        println("\n=== Benchmark: largeArrayParsing ===")
        benchmarkLargeArray()
        
        println("\n=== Benchmark: repeatedQueries ===")
        benchmarkRepeatedQueries()
    }
    
    private fun warmup() {
        print("Warming up JVM...")
        val json = generateJson(1000)
        repeat(10000) {
            SimpleJsonScanner(json).properties()
            JsonScannerCompact(json).properties()
        }
        println(" done")
        println()
    }
    
    private fun benchmarkScanProperties() {
        val sizes = listOf(10, 100, 1000, 10000)
        val iterations = 1000
        
        println("Property Count | Simple (ns/op) | Bitmap (ns/op) | Speedup")
        println("---------------|----------------|----------------|--------")
        
        for (size in sizes) {
            val json = generateJson(size)
            
            // Benchmark simple scanner
            val simpleTime = benchmarkOperation(iterations) {
                SimpleJsonScanner(json).properties()
            }
            
            // Benchmark bitmap scanner
            val bitmapTime = benchmarkOperation(iterations) {
                JsonScannerCompact(json).properties()
            }
            
            val speedup = simpleTime.toDouble() / bitmapTime
            println("%-14d | %-14.2f | %-14.2f | %.2fx".format(
                size, simpleTime, bitmapTime, speedup
            ))
        }
    }
    
    private fun benchmarkScanAndQuery() {
        val sizes = listOf(100, 1000, 10000)
        val iterations = 1000
        
        println("Property Count | Simple (ns/op) | Bitmap (ns/op) | Speedup")
        println("---------------|----------------|----------------|--------")
        
        for (size in sizes) {
            val json = generateJson(size)
            val queryKey = "field${size/2}"
            
            val simpleTime = benchmarkOperation(iterations) {
                val scanner = SimpleJsonScanner(json)
                scanner.properties()
                scanner.query(queryKey)
            }
            
            val bitmapTime = benchmarkOperation(iterations) {
                val scanner = JsonScannerCompact(json)
                scanner.properties()
                scanner.query(queryKey)
            }
            
            val speedup = simpleTime.toDouble() / bitmapTime
            println("%-14d | %-14.2f | %-14.2f | %.2fx".format(
                size, simpleTime, bitmapTime, speedup
            ))
        }
    }
    
    private fun benchmarkFingerprint() {
        val sizes = listOf(100, 1000, 10000)
        val iterations = 1000
        
        println("Property Count | Simple (ns/op) | Bitmap (ns/op) | Speedup")
        println("---------------|----------------|----------------|--------")
        
        for (size in sizes) {
            val json = generateJson(size)
            
            // Pre-create scanners
            val simpleScanners = Array(iterations) { SimpleJsonScanner(json) }
            val bitmapScanners = Array(iterations) { JsonScannerCompact(json) }
            
            val simpleTime = benchmarkOperation(iterations) { i ->
                simpleScanners[i].fingerprint()
            }
            
            val bitmapTime = benchmarkOperation(iterations) { i ->
                bitmapScanners[i].fingerprint()
            }
            
            val speedup = simpleTime.toDouble() / bitmapTime
            println("%-14d | %-14.2f | %-14.2f | %.2fx".format(
                size, simpleTime, bitmapTime, speedup
            ))
        }
    }
    
    private fun benchmarkLargeArray() {
        val sizes = listOf(100, 1000, 10000)
        val iterations = 100
        
        println("Array Size | Simple (μs/op) | Bitmap (μs/op) | Speedup")
        println("-----------|----------------|----------------|--------")
        
        for (size in sizes) {
            val json = generateArrayJson(size)
            
            val simpleTime = benchmarkOperation(iterations) {
                SimpleJsonScanner(json).properties()
            } / 1000.0 // Convert to microseconds
            
            val bitmapTime = benchmarkOperation(iterations) {
                JsonScannerCompact(json).properties()
            } / 1000.0
            
            val speedup = simpleTime / bitmapTime
            println("%-10d | %-14.2f | %-14.2f | %.2fx".format(
                size, simpleTime, bitmapTime, speedup
            ))
        }
    }
    
    private fun benchmarkRepeatedQueries() {
        val json = """{
            "user": {
                "name": "John Doe",
                "age": 30,
                "email": "john@example.com",
                "address": {
                    "street": "123 Main St",
                    "city": "Anytown",
                    "country": "USA"
                }
            },
            "metadata": {
                "version": "1.0",
                "timestamp": 1234567890
            }
        }"""
        
        val queries = listOf("name", "email", "street", "version")
        val iterations = 10000
        
        // Setup scanners
        val simpleScanner = SimpleJsonScanner(json)
        val bitmapScanner = JsonScannerCompact(json)
        simpleScanner.properties()
        bitmapScanner.properties()
        
        println("Query Pattern  | Simple (ns/op) | Bitmap (ns/op) | Speedup")
        println("---------------|----------------|----------------|--------")
        
        val simpleTime = benchmarkOperation(iterations) {
            for (query in queries) {
                simpleScanner.query(query)
            }
        }
        
        val bitmapTime = benchmarkOperation(iterations) {
            for (query in queries) {
                bitmapScanner.query(query)
            }
        }
        
        val speedup = simpleTime.toDouble() / bitmapTime
        println("%-14s | %-14.2f | %-14.2f | %.2fx".format(
            "4 queries", simpleTime, bitmapTime, speedup
        ))
    }
    
    private inline fun benchmarkOperation(iterations: Int, operation: (Int) -> Unit): Double {
        // Warmup
        repeat(iterations / 10) { operation(it) }
        
        // Measure
        val times = DoubleArray(10)
        repeat(10) { run ->
            times[run] = measureNanoTime {
                repeat(iterations / 10) { operation(it) }
            }.toDouble() / (iterations / 10)
        }
        
        // Return average, excluding outliers
        times.sort()
        return times.slice(2..7).average()
    }
    
    private fun generateJson(count: Int): String = buildString {
        append("{")
        repeat(count) { i ->
            if (i > 0) append(",")
            append("\"field$i\":\"value$i\"")
        }
        append("}")
    }
    
    private fun generateArrayJson(count: Int): String = buildString {
        append("[")
        repeat(count) { i ->
            if (i > 0) append(",")
            append("""{"id":$i,"name":"item$i","value":${i * 3.14},"active":${i % 2 == 0}}""")
        }
        append("]")
    }
}