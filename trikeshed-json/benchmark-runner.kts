#!/usr/bin/env kotlin

import kotlin.time.*

// Minimal JSON scanner implementations for benchmarking
class SimpleJsonScanner(val input: String) {
    fun scanProperties(): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        var i = 0
        
        while (i < input.length) {
            // Skip whitespace
            while (i < input.length && input[i].isWhitespace()) i++
            
            // Look for property name
            if (i < input.length && input[i] == '"') {
                i++ // skip opening quote
                val keyStart = i
                while (i < input.length && input[i] != '"') i++
                val key = input.substring(keyStart, i)
                i++ // skip closing quote
                
                // Skip : and whitespace
                while (i < input.length && (input[i].isWhitespace() || input[i] == ':')) i++
                
                // Get value
                if (i < input.length && input[i] == '"') {
                    i++ // skip opening quote
                    val valueStart = i
                    while (i < input.length && input[i] != '"') i++
                    val value = input.substring(valueStart, i)
                    i++ // skip closing quote
                    
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
    private val properties = mutableListOf<Pair<Int, Int>>() // start,end indices
    
    fun scan() {
        // Mark structural characters
        for (i in input.indices) {
            bitmap[i] = when (input[i]) {
                '{', '}', '[', ']', '"', ':', ',' -> true
                else -> false
            }
        }
        
        // Extract properties using bitmap
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
            
            // Find corresponding value
            val (valueStart, valueEnd) = properties[i + 1]
            val value = input.substring(valueStart + 1, valueEnd)
            
            result.add(key to value)
            i += 2
        }
        
        return result
    }
}

// Benchmark functions
fun generateJson(count: Int): String = buildString {
    append("{")
    repeat(count) { i ->
        if (i > 0) append(",")
        append("\"field$i\":\"value$i\"")
    }
    append("}")
}

@OptIn(ExperimentalTime::class)
fun runBenchmarks() {
    println("=== JSON Scanner Performance Benchmarks ===")
    println("Running on: ${System.getProperty("os.name")} ${System.getProperty("os.arch")}")
    println()
    
    val sizes = listOf(10, 100, 1000)
    val iterations = 1000
    
    for (size in sizes) {
        val json = generateJson(size)
        println("JSON with $size properties (${json.length} chars):")
        
        // Warm up
        repeat(100) {
            SimpleJsonScanner(json).scanProperties()
            val bitmap = BitmapJsonScanner(json)
            bitmap.scan()
            bitmap.getProperties()
        }
        
        // Simple scanner benchmark
        val simpleTime = measureTime {
            repeat(iterations) {
                SimpleJsonScanner(json).scanProperties()
            }
        }
        
        // Bitmap scanner benchmark
        val bitmapTime = measureTime {
            repeat(iterations) {
                val scanner = BitmapJsonScanner(json)
                scanner.scan()
                scanner.getProperties()
            }
        }
        
        println("  Simple Scanner: ${simpleTime.inWholeMilliseconds}ms for $iterations iterations")
        println("  Bitmap Scanner: ${bitmapTime.inWholeMilliseconds}ms for $iterations iterations")
        println("  Simple is ${String.format("%.2f", simpleTime.inWholeNanoseconds.toDouble() / bitmapTime.inWholeNanoseconds)}x the time of Bitmap")
        println()
    }
    
    // Large JSON benchmark
    println("=== Large JSON Benchmark ===")
    val largeJson = generateJson(10000)
    println("JSON with 10000 properties (${largeJson.length} chars):")
    
    val largeSimpleTime = measureTime {
        SimpleJsonScanner(largeJson).scanProperties()
    }
    
    val largeBitmapTime = measureTime {
        val scanner = BitmapJsonScanner(largeJson)
        scanner.scan()
        scanner.getProperties()
    }
    
    println("  Simple Scanner: ${largeSimpleTime.inWholeMilliseconds}ms")
    println("  Bitmap Scanner: ${largeBitmapTime.inWholeMilliseconds}ms")
    println("  Speedup: ${String.format("%.2f", largeSimpleTime.inWholeNanoseconds.toDouble() / largeBitmapTime.inWholeNanoseconds)}x")
}

// Run the benchmarks
runBenchmarks()