@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.json

import borg.trikeshed.lib.*
import borg.trikeshed.core.*
import kotlinx.serialization.json.*
import kotlin.system.*
import kotlin.test.Test

/**
 * Fixed Kotlin timing benchmarks comparing:
 * A) Simple JSON Scanner
 * B) Bitmap JSON Scanner
 * C) Fast JSON Scanner (LinkedHashMap)
 * D) kotlinx.serialization
 */
class KotlinBenchmarkFixed {
    
    @Test
    fun runAllBenchmarks() {
        println("\n=== TrikeShed JSON Kotlin Benchmarks (Fixed) ===")
        println("Platform: JVM ${System.getProperty("java.version")}")
        println("Timestamp: ${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}")
        
        warmup()
        
        benchmarkParsing()
        benchmarkQueries()
    }
    
    internal fun warmup() {
        print("Warming up...")
        val json = generateJson(1000)
        repeat(1000) {
            json.simpleJson().properties()
            json.json().properties()
            json.fastJson().properties()
            Json.parseToJsonElement(json)
        }
        println(" done")
    }
    
    internal fun benchmarkParsing() {
        println("\n=== Parsing Benchmark (avg ns/op) ===")
        println("Size   | Simple      | Bitmap      | Fast        | Kotlinx     | Winner")
        println("-------|-------------|-------------|-------------|-------------|-------")
        
        for (size in listOf(10, 100, 1000, 10000)) {
            val json = generateJson(size)
            val iterations = when(size) {
                10 -> 10000
                100 -> 1000
                1000 -> 100
                else -> 10
            }
            
            // Simple scanner
            val simpleAvg = benchmark(iterations) {
                json.simpleJson().properties()
            }
            
            // Bitmap scanner
            val bitmapAvg = benchmark(iterations) {
                json.json().properties()
            }
            
            // Fast scanner
            val fastAvg = benchmark(iterations) {
                json.fastJson().properties()
            }
            
            // Kotlinx
            val kotlinxAvg = benchmark(iterations) {
                Json.parseToJsonElement(json).jsonObject
            }
            
            val times = mapOf(
                "Simple" to simpleAvg,
                "Bitmap" to bitmapAvg,
                "Fast" to fastAvg,
                "Kotlinx" to kotlinxAvg
            )
            val winner = times.minBy { it.value }
            
            println("%-6d | %11.2f | %11.2f | %11.2f | %11.2f | %s".format(
                size, simpleAvg, bitmapAvg, fastAvg, kotlinxAvg, winner.key
            ))
        }
    }
    
    internal fun benchmarkQueries() {
        println("\n=== Query Benchmark (avg ns/op) ===")
        println("Size   | Simple      | Bitmap      | Fast        | Kotlinx     | Winner")
        println("-------|-------------|-------------|-------------|-------------|-------")
        
        for (size in listOf(100, 1000, 10000)) {
            val json = generateJson(size)
            val queries = listOf("field1", "field${size/2}", "field${size-1}")
            
            // Pre-parse all scanners
            val simpleScanner = json.simpleJson()
            simpleScanner.properties()
            
            val bitmapScanner = json.json()
            bitmapScanner.properties()
            
            val fastScanner = json.fastJson()
            fastScanner.properties()
            
            val kotlinxObj = Json.parseToJsonElement(json).jsonObject
            
            val iterations = 1000
            
            // Benchmark queries
            val simpleTime = measureNanoTime {
                repeat(iterations) {
                    queries.forEach { simpleScanner.query(it) }
                }
            }.toDouble() / iterations
            
            val bitmapTime = measureNanoTime {
                repeat(iterations) {
                    queries.forEach { bitmapScanner.query(it) }
                }
            }.toDouble() / iterations
            
            val fastTime = measureNanoTime {
                repeat(iterations) {
                    queries.forEach { fastScanner.query(it) }
                }
            }.toDouble() / iterations
            
            val kotlinxTime = measureNanoTime {
                repeat(iterations) {
                    queries.forEach { kotlinxObj[it] }
                }
            }.toDouble() / iterations
            
            val times = mapOf(
                "Simple" to simpleTime,
                "Bitmap" to bitmapTime,
                "Fast" to fastTime,
                "Kotlinx" to kotlinxTime
            )
            val winner = times.minBy { it.value }
            
            println("%-6d | %11.2f | %11.2f | %11.2f | %11.2f | %s".format(
                size, simpleTime, bitmapTime, fastTime, kotlinxTime, winner.key
            ))
        }
    }
    
    internal inline fun benchmark(iterations: Int, block: () -> Unit): Double {
        val times = DoubleArray(5)
        repeat(5) { run ->
            times[run] = measureNanoTime {
                repeat(iterations) { block() }
            }.toDouble() / iterations
        }
        return times.average()
    }
    
    internal fun generateJson(count: Int): String = buildString {
        append("{")
        repeat(count) { i ->
            if (i > 0) append(",")
            append("\"field$i\":\"value$i\"")
        }
        append("}")
    }
}