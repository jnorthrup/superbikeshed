package borg.trikeshed.json

import kotlin.system.measureNanoTime
import kotlin.random.Random
import kotlinx.serialization.json.*
import borg.trikeshed.core.*

/**
 * Benchmarks demonstrating actual JVM Vector API performance.
 * Run with: --add-modules jdk.incubator.vector
 */
class VectorApiBenchmark {
    
    fun runBenchmarks() {
        println("=== JVM Vector API JSON Benchmarks ===")
        println("Running with Vector API for actual SIMD acceleration\n")
        
        benchmarkStructuralScanning()
        benchmarkStringExtraction()
        benchmarkFullParsing()
        benchmarkBatchOperations()
    }
    
    private fun benchmarkStructuralScanning() {
        println("## Structural Character Scanning")
        
        val sizes = listOf(1_000, 10_000, 100_000, 1_000_000)
        
        for (size in sizes) {
            val json = generateJson(size)
            
            // Warm up
            repeat(10) {
                SimpleJsonScanner(json).scan()
                VectorizedJsonScanner(json).findStructural()
            }
            
            // Benchmark simple scanner
            val simpleTime = measureNanoTime {
                repeat(100) {
                    SimpleJsonScanner(json).scan()
                }
            } / 100
            
            // Benchmark vectorized scanner
            val vectorTime = measureNanoTime {
                repeat(100) {
                    VectorizedJsonScanner(json).findStructural()
                }
            } / 100
            
            val speedup = simpleTime.toDouble() / vectorTime
            val mbps = (size.toDouble() / vectorTime) * 1000
            
            println("Size: ${size / 1000}KB")
            println("  Simple:     ${simpleTime / 1_000_000}ms")
            println("  Vectorized: ${vectorTime / 1_000_000}ms")
            println("  Speedup:    ${String.format("%.2fx", speedup)}")
            println("  Throughput: ${String.format("%.0f MB/s", mbps)}")
            println()
        }
    }
    
    private fun benchmarkStringExtraction() {
        println("## String Extraction with SIMD")
        
        val json = """
            {
                "users": [
                    ${(1..1000).map { """{"id": $it, "name": "User $it", "email": "user$it@example.com"}""" }.joinToString(", ")}
                ]
            }
        """.trimIndent()
        
        val scanner = VectorizedJsonScanner(json)
        val structural = scanner.findStructural()
        
        // Warm up
        repeat(100) {
            scanner.parseStrings(structural)
        }
        
        val time = measureNanoTime {
            repeat(1000) {
                scanner.parseStrings(structural)
            }
        } / 1000
        
        val strings = scanner.parseStrings(structural)
        println("Extracted ${strings.size} strings in ${time / 1_000_000}ms")
        println("Rate: ${(strings.size * 1000.0) / (time / 1_000_000)} strings/ms")
        println()
    }
    
    private fun benchmarkFullParsing() {
        println("## Full JSON Parsing Comparison")
        
        val testCases = listOf(
            "Small object" to """{"name": "test", "value": 123, "active": true}""",
            "Nested object" to """{"user": {"id": 1, "profile": {"name": "John", "age": 30}}}""",
            "Array of objects" to """[${(1..100).map { """{"id": $it}""" }.joinToString(", ")}]""",
            "Mixed content" to generateMixedJson(10_000)
        )
        
        for ((name, json) in testCases) {
            println("### $name (${json.length} bytes)")
            
            // Simple scanner
            val simpleTime = measureNanoTime {
                repeat(100) {
                    val scanner = SimpleJsonScanner(json).scan()
                    // Convert to JsonElement would happen here
                }
            } / 100
            
            // Bitmap scanner  
            val bitmapTime = measureNanoTime {
                repeat(100) {
                    val scanner = JsonScannerCompact(json).scan()
                }
            } / 100
            
            // Fast scanner
            val fastTime = measureNanoTime {
                repeat(100) {
                    FastJsonScanner(json).scan()
                }
            } / 100
            
            // Vectorized scanner
            val vectorTime = measureNanoTime {
                repeat(100) {
                    VectorizedJsonScanner(json).parse()
                }
            } / 100
            
            // kotlinx.serialization baseline
            val kotlinxTime = measureNanoTime {
                repeat(100) {
                    Json.parseToJsonElement(json)
                }
            } / 100
            
            println("  Simple:       ${simpleTime / 1_000}μs")
            println("  Bitmap:       ${bitmapTime / 1_000}μs")
            println("  Fast (O(1)):  ${fastTime / 1_000}μs")
            println("  Vectorized:   ${vectorTime / 1_000}μs (${String.format("%.1fx", kotlinxTime.toDouble() / vectorTime)} vs kotlinx)")
            println("  kotlinx:      ${kotlinxTime / 1_000}μs (baseline)")
            println()
        }
    }
    
    private fun benchmarkBatchOperations() {
        println("## Batch Processing with SIMD")
        
        val batchSizes = listOf(10, 100, 1000)
        val jsonSize = 1000
        
        for (batchSize in batchSizes) {
            val jsons = List(batchSize) { generateJson(jsonSize) }
            
            // Sequential processing
            val sequentialTime = measureNanoTime {
                for (json in jsons) {
                    SimpleJsonScanner(json).scan()
                }
            }
            
            // Vectorized batch processing (simulated)
            val batchTime = measureNanoTime {
                // In real implementation, this would process multiple JSONs in parallel
                for (json in jsons) {
                    VectorizedJsonScanner(json).findStructural()
                }
            }
            
            val speedup = sequentialTime.toDouble() / batchTime
            
            println("Batch size: $batchSize")
            println("  Sequential: ${sequentialTime / 1_000_000}ms")
            println("  Vectorized: ${batchTime / 1_000_000}ms")
            println("  Speedup:    ${String.format("%.2fx", speedup)}")
            println("  Per JSON:   ${batchTime / batchSize / 1_000}μs")
            println()
        }
    }
    
    private fun generateJson(size: Int): String {
        val sb = StringBuilder()
        sb.append("{")
        
        val numFields = size / 50 // Approximate fields to reach target size
        
        for (i in 0 until numFields) {
            if (i > 0) sb.append(", ")
            sb.append("\"field$i\": ")
            
            when (i % 4) {
                0 -> sb.append("\"value$i\"")
                1 -> sb.append(i)
                2 -> sb.append(i % 2 == 0)
                3 -> sb.append("[${(0..5).joinToString(", ")}]")
            }
        }
        
        sb.append("}")
        return sb.toString()
    }
    
    private fun generateMixedJson(size: Int): String {
        return """
        {
            "metadata": {
                "version": "1.0",
                "timestamp": ${System.currentTimeMillis()},
                "size": $size
            },
            "data": [
                ${(1..size/100).map { i ->
                    """{"id": $i, "value": ${Random.nextDouble()}, "tags": [${(1..5).map { "\"tag$it\"" }.joinToString(", ")}]}"""
                }.joinToString(", ")}
            ],
            "summary": {
                "total": ${size/100},
                "active": ${size/200}
            }
        }
        """.trimIndent()
    }
}

fun main() {
    println("Starting Vector API Benchmarks...")
    println("Make sure to run with: --add-modules jdk.incubator.vector")
    println()
    
    try {
        VectorApiBenchmark().runBenchmarks()
    } catch (e: Exception) {
        println("Error: ${e.message}")
        println("\nMake sure you're running with JVM 17+ and Vector API enabled:")
        println("  --add-modules jdk.incubator.vector")
    }
}