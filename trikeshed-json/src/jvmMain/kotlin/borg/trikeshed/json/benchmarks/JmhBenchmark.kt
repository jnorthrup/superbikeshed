package borg.trikeshed.json.benchmarks

import borg.trikeshed.lib.*
import borg.trikeshed.core.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * JMH-style benchmark comparing:
 * A) Simple JSON Scanner
 * B) Bitmap JSON Scanner  
 * C) kotlinx.serialization
 */
object JmhBenchmark {
    
    @JvmStatic
    fun main(args: Array<String>) {
        println("=== TrikeShed JSON vs kotlinx.serialization Benchmarks ===")
        println("JVM: ${System.getProperty("java.version")} ${System.getProperty("java.vm.name")}")
        println()
        
        runPropertyExtractionBenchmark()
        runParsingBenchmark()
        runBigJsonBenchmark()
    }
    
    private fun runPropertyExtractionBenchmark() {
        println("=== Property Extraction Benchmark (ns/op) ===")
        println("Size   | Simple      | Bitmap      | Kotlinx     | Winner")
        println("-------|-------------|-------------|-------------|-------")
        
        for (size in listOf(10, 100, 1000, 10000)) {
            val json = generateJson(size)
            val iterations = when(size) {
                10 -> 10000
                100 -> 1000
                1000 -> 100
                else -> 10
            }
            
            // Warmup
            repeat(100) {
                json.simpleJson().properties()
                json.json().properties()
                Json.parseToJsonElement(json).jsonObject
            }
            
            val simpleTime = benchmark(iterations) {
                json.simpleJson().properties()
            }
            
            val bitmapTime = benchmark(iterations) {
                json.json().properties()
            }
            
            val kotlinxTime = benchmark(iterations) {
                Json.parseToJsonElement(json).jsonObject
            }
            
            val times = mapOf(
                "Simple" to simpleTime,
                "Bitmap" to bitmapTime,
                "Kotlinx" to kotlinxTime
            )
            val winner = times.minBy { it.value }
            
            println("%-6d | %11.2f | %11.2f | %11.2f | %s".format(
                size, simpleTime, bitmapTime, kotlinxTime, winner.key
            ))
        }
    }
    
    private fun runParsingBenchmark() {
        println("\n=== Full Parse Benchmark (μs/op) ===")
        println("Type        | Simple      | Bitmap      | Kotlinx     | Winner")
        println("------------|-------------|-------------|-------------|-------")
        
        val testCases = mapOf(
            "Object" to """{"name":"test","value":42,"active":true}""",
            "Array" to """[1,2,3,4,5,6,7,8,9,10]""",
            "Nested" to """{
                "user": {
                    "name": "John",
                    "contacts": [
                        {"type":"email","value":"john@example.com"},
                        {"type":"phone","value":"+1234567890"}
                    ]
                }
            }"""
        )
        
        for ((type, json) in testCases) {
            val simpleTime = benchmark(1000) {
                json.simpleJson().scan()
            } / 1000.0
            
            val bitmapTime = benchmark(1000) {
                json.json().scan()
            } / 1000.0
            
            val kotlinxTime = benchmark(1000) {
                Json.parseToJsonElement(json)
            } / 1000.0
            
            val times = mapOf(
                "Simple" to simpleTime,
                "Bitmap" to bitmapTime,
                "Kotlinx" to kotlinxTime
            )
            val winner = times.minBy { it.value }
            
            println("%-11s | %11.2f | %11.2f | %11.2f | %s".format(
                type, simpleTime, bitmapTime, kotlinxTime, winner.key
            ))
        }
    }
    
    private fun runBigJsonBenchmark() {
        val bigJsonResource = JmhBenchmark::class.java.getResource("/big.json")
        if (bigJsonResource == null) {
            println("\n=== Big JSON Benchmark ===")
            println("Skipping: big.json not found in resources")
            return
        }
        
        val bigJson = bigJsonResource.readText()
        println("\n=== Big JSON Benchmark ===")
        println("JSON size: ${bigJson.length} characters (${bigJson.length / 1024}KB)")
        
        // Single run for big JSON
        val simpleTime = measureTime {
            bigJson.simpleJson().properties()
        }
        
        val bitmapTime = measureTime {
            bigJson.json().properties()
        }
        
        val kotlinxTime = measureTime {
            Json.parseToJsonElement(bigJson).jsonObject
        }
        
        println("Simple Scanner: ${String.format("%.2f", simpleTime)}ms")
        println("Bitmap Scanner: ${String.format("%.2f", bitmapTime)}ms")
        println("Kotlinx JSON:   ${String.format("%.2f", kotlinxTime)}ms")
        
        val times = mapOf(
            "Simple" to simpleTime,
            "Bitmap" to bitmapTime,
            "Kotlinx" to kotlinxTime
        )
        val winner = times.minBy { it.value }
        println("Winner: ${winner.key} (${String.format("%.2f", winner.value)}ms)")
    }
    
    private inline fun benchmark(iterations: Int, block: () -> Unit): Double {
        val times = DoubleArray(5)
        repeat(5) { run ->
            times[run] = kotlin.system.measureNanoTime {
                repeat(iterations) { block() }
            }.toDouble() / iterations
        }
        return times.average()
    }
    
    private inline fun measureTime(block: () -> Unit): Double {
        return kotlin.system.measureNanoTime(block) / 1_000_000.0
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