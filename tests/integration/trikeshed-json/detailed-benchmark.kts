#!/usr/bin/env kotlin

import kotlin.time.*
import kotlin.system.*

// Minimal JSON scanner implementations
class SimpleJsonScanner(val input: String) {
    fun scanProperties(): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        var i = 0
        
        while (i < input.length) {
            while (i < input.length && input[i].isWhitespace()) i++
            
            if (i < input.length && input[i] == '"') {
                i++
                val keyStart = i
                while (i < input.length && input[i] != '"') i++
                val key = input.substring(keyStart, i)
                i++
                
                while (i < input.length && (input[i].isWhitespace() || input[i] == ':')) i++
                
                if (i < input.length && input[i] == '"') {
                    i++
                    val valueStart = i
                    while (i < input.length && input[i] != '"') i++
                    val value = input.substring(valueStart, i)
                    i++
                    
                    result.add(key to value)
                }
            }
            i++
        }
        
        return result
    }
}

class BitmapJsonScanner(val input: String) {
    private val bitmap = BooleanArray(input.length)
    private val properties = mutableListOf<Pair<Int, Int>>()
    
    fun scan() {
        for (i in input.indices) {
            bitmap[i] = when (input[i]) {
                '{', '}', '[', ']', '"', ':', ',' -> true
                else -> false
            }
        }
        
        var i = 0
        while (i < input.length) {
            if (bitmap[i] && input[i] == '"') {
                val start = i
                i++
                while (i < input.length && !bitmap[i]) i++
                if (i < input.length && input[i] == '"') {
                    properties.add(start to i)
                }
            }
            i++
        }
    }
    
    fun getProperties(): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        var i = 0
        
        while (i < properties.size - 1) {
            val (keyStart, keyEnd) = properties[i]
            val key = input.substring(keyStart + 1, keyEnd)
            val (valueStart, valueEnd) = properties[i + 1]
            val value = input.substring(valueStart + 1, valueEnd)
            result.add(key to value)
            i += 2
        }
        
        return result
    }
}

fun generateJson(count: Int): String = buildString {
    append("{")
    repeat(count) { i ->
        if (i > 0) append(",")
        append("\"field$i\":\"value$i\"")
    }
    append("}")
}

@OptIn(ExperimentalTime::class)
fun main() {
    println("=== Detailed JSON Scanner Performance Benchmarks ===")
    println("Timestamp: ${System.currentTimeMillis()}")
    println("Platform: ${System.getProperty("os.name")} ${System.getProperty("os.arch")}")
    println()
    
    // Test correctness first
    val testJson = """{"name":"test","value":"42","active":"true"}"""
    val simpleResult = SimpleJsonScanner(testJson).scanProperties()
    val testBitmapScanner = BitmapJsonScanner(testJson)
    testBitmapScanner.scan()
    val bitmapResult = testBitmapScanner.getProperties()
    
    println("Correctness check:")
    println("  Simple found: ${simpleResult.size} properties")
    println("  Bitmap found: ${bitmapResult.size} properties")
    println()
    
    // Benchmark different sizes
    val testCases = listOf(
        10 to 10000,
        100 to 1000,
        1000 to 100,
        10000 to 10
    )
    
    for ((size, iterations) in testCases) {
        val json = generateJson(size)
        println("=== Test: $size properties, $iterations iterations ===")
        println("JSON size: ${json.length} characters")
        
        // Warm up
        repeat(100) {
            SimpleJsonScanner(json).scanProperties()
            val scanner = BitmapJsonScanner(json)
            scanner.scan()
            scanner.getProperties()
        }
        
        // Measure simple scanner
        val simpleStart = System.nanoTime()
        repeat(iterations) {
            SimpleJsonScanner(json).scanProperties()
        }
        val simpleEnd = System.nanoTime()
        val simpleTime = (simpleEnd - simpleStart) / 1_000_000.0
        
        // Measure bitmap scanner
        val bitmapStart = System.nanoTime()
        repeat(iterations) {
            val scanner = BitmapJsonScanner(json)
            scanner.scan()
            scanner.getProperties()
        }
        val bitmapEnd = System.nanoTime()
        val bitmapTime = (bitmapEnd - bitmapStart) / 1_000_000.0
        
        println("Simple Scanner: ${String.format("%.2f", simpleTime)}ms total")
        println("  Per iteration: ${String.format("%.4f", simpleTime / iterations)}ms")
        println("Bitmap Scanner: ${String.format("%.2f", bitmapTime)}ms total")
        println("  Per iteration: ${String.format("%.4f", bitmapTime / iterations)}ms")
        
        val speedup = simpleTime / bitmapTime
        if (speedup > 1) {
            println("Result: Bitmap is ${String.format("%.2f", speedup)}x faster")
        } else {
            println("Result: Simple is ${String.format("%.2f", 1/speedup)}x faster")
        }
        println()
    }
    
    // Memory usage test
    println("=== Memory Usage Test ===")
    val runtime = Runtime.getRuntime()
    runtime.gc()
    Thread.sleep(100)
    
    val beforeMemory = runtime.totalMemory() - runtime.freeMemory()
    val largeJson = generateJson(50000)
    
    // Simple scanner memory
    runtime.gc()
    Thread.sleep(100)
    val simpleBeforeMemory = runtime.totalMemory() - runtime.freeMemory()
    val simpleScanner = SimpleJsonScanner(largeJson)
    val simpleProps = simpleScanner.scanProperties()
    val simpleAfterMemory = runtime.totalMemory() - runtime.freeMemory()
    
    // Bitmap scanner memory
    runtime.gc()
    Thread.sleep(100)
    val bitmapBeforeMemory = runtime.totalMemory() - runtime.freeMemory()
    val largeBitmapScanner = BitmapJsonScanner(largeJson)
    largeBitmapScanner.scan()
    val bitmapProps = largeBitmapScanner.getProperties()
    val bitmapAfterMemory = runtime.totalMemory() - runtime.freeMemory()
    
    println("JSON size: ${largeJson.length} chars (50000 properties)")
    println("Simple scanner memory: ${(simpleAfterMemory - simpleBeforeMemory) / 1024}KB")
    println("Bitmap scanner memory: ${(bitmapAfterMemory - bitmapBeforeMemory) / 1024}KB")
    println()
    
    println("=== Summary ===")
    println("Benchmarks completed at: ${System.currentTimeMillis()}")
}

main()