@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.json

import borg.trikeshed.lib.*
import borg.trikeshed.core.*
import kotlinx.serialization.json.*
import kotlin.system.*
import kotlin.test.Test

/**
 * Kotlin timing benchmarks comparing:
 * A) Simple JSON Scanner
 * B) Bitmap JSON Scanner
 * C) kotlinx.serialization
 */
class KotlinBenchmark {
    
    @Test
    fun runAllBenchmarks() {
        println("\n=== TrikeShed JSON Kotlin Benchmarks ===")
        println("Platform: JVM ${System.getProperty("java.version")}")
        println("Timestamp: ${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}")
        
        warmup()
        
        benchmarkParsing()
        benchmarkQueries()
        benchmarkBigJson()
    }
    
    internal fun warmup() {
        print("Warming up...")
        val json = generateJson(1000)
        repeat(1000) {
            json.simpleJson().properties()
            json.json().properties()
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
            val simpleTimes = LongArray(10)
            repeat(10) { run ->
                simpleTimes[run] = measureNanoTime {
                    repeat(iterations) {
                        json.simpleJson().properties()
                    }
                } / iterations
            }
            val simpleAvg = simpleTimes.average()
            
            // Bitmap scanner
            val bitmapTimes = LongArray(10)
            repeat(10) { run ->
                bitmapTimes[run] = measureNanoTime {
                    repeat(iterations) {
                        json.json().properties()
                    }
                } / iterations
            }
            val bitmapAvg = bitmapTimes.average()
            
            // Kotlinx
            val kotlinxTimes = LongArray(10)
            repeat(10) { run ->
                kotlinxTimes[run] = measureNanoTime {
                    repeat(iterations) {
                        Json.parseToJsonElement(json).jsonObject
                    }
                } / iterations
            }
            val kotlinxAvg = kotlinxTimes.average()
            
            val times = mapOf(
                "Simple" to simpleAvg,
                "Bitmap" to bitmapAvg,
                "Kotlinx" to kotlinxAvg
            )
            val winner = times.minBy { it.value }
            
            println("%-6d | %11.2f | %11.2f | %11.2f | %s".format(
                size, simpleAvg, bitmapAvg, kotlinxAvg, winner.key
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
            
            // Pre-parse
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
    
    internal fun benchmarkBigJson() {
        val bigJsonResource = this::class.java.getResource("/big.json")
        if (bigJsonResource == null) {
            println("\n=== Big JSON Benchmark ===")
            println("Generating 100K element JSON...")
            val bigJson = generateBigJson(100000)
            runBigJsonTest(bigJson)
        } else {
            val bigJson = bigJsonResource.readText()
            println("\n=== Big JSON Benchmark ===")
            println("Using big.json from resources")
            runBigJsonTest(bigJson)
        }
    }
    
    internal fun runBigJsonTest(bigJson: String) {
        println("JSON size: ${bigJson.length} chars (${bigJson.length / 1024}KB)")
        
        // Warmup
        repeat(3) {
            bigJson.simpleJson().properties()
            bigJson.json().properties()
            Json.parseToJsonElement(bigJson)
        }
        
        // Measure
        val simpleTime = measureTimeMillis {
            bigJson.simpleJson().properties()
        }
        
        val bitmapTime = measureTimeMillis {
            bigJson.json().properties()
        }
        
        val kotlinxTime = measureTimeMillis {
            Json.parseToJsonElement(bigJson)
        }
        
        println("\nResults:")
        println("Simple Scanner: ${simpleTime}ms")
        println("Bitmap Scanner: ${bitmapTime}ms")
        println("Kotlinx JSON:   ${kotlinxTime}ms")
        
        val fastest = minOf(simpleTime, bitmapTime, kotlinxTime)
        when (fastest) {
            simpleTime -> {
                if (bitmapTime > 0) println("Simple is ${bitmapTime.toDouble()/simpleTime}x faster than Bitmap")
                if (kotlinxTime > 0) println("Simple is ${kotlinxTime.toDouble()/simpleTime}x faster than Kotlinx")
            }
            bitmapTime -> {
                if (simpleTime > 0) println("Bitmap is ${simpleTime.toDouble()/bitmapTime}x faster than Simple")
                if (kotlinxTime > 0) println("Bitmap is ${kotlinxTime.toDouble()/bitmapTime}x faster than Kotlinx")
            }
            kotlinxTime -> {
                if (simpleTime > 0) println("Kotlinx is ${simpleTime.toDouble()/kotlinxTime}x faster than Simple")
                if (bitmapTime > 0) println("Kotlinx is ${bitmapTime.toDouble()/kotlinxTime}x faster than Bitmap")
            }
        }
    }
    
    internal fun generateJson(count: Int): String = buildString {
        append("{")
        repeat(count) { i ->
            if (i > 0) append(",")
            append("\"field$i\":\"value$i\"")
        }
        append("}")
    }
    
    internal fun generateBigJson(count: Int): String = buildString {
        append("{\"data\":[")
        repeat(count) { i ->
            if (i > 0) append(",")
            append("{\"id\":$i,\"timestamp\":${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()},\"value\":${Math.random()}}")
        }
        append("]}")
    }
}