@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.json

import borg.trikeshed.lib.*
import borg.trikeshed.core.*
import kotlin.system.measureNanoTime
import kotlin.test.Test
import java.io.File

class BigJsonBenchmark {
    
    @Test
    fun benchmarkBigJson() {
        val bigJson = this::class.java.getResource("/big.json")?.readText()
            ?: error("big.json not found in resources")
        
        println("\n=== Big JSON Benchmark ===")
        println("JSON size: ${bigJson.length} characters (${bigJson.length / 1024}KB)")
        
        // Warmup
        repeat(5) {
            bigJson.simpleJson().properties()
            bigJson.json().properties()
        }
        
        // Simple scanner
        val simpleTime = measureNanoTime {
            bigJson.simpleJson().properties()
        } / 1_000_000.0
        
        // Bitmap scanner
        val bitmapTime = measureNanoTime {
            bigJson.json().properties()
        } / 1_000_000.0
        
        println("Simple Scanner: ${String.format("%.2f", simpleTime)}ms")
        println("Bitmap Scanner: ${String.format("%.2f", bitmapTime)}ms")
        
        if (bitmapTime < simpleTime) {
            println("Bitmap is ${String.format("%.2f", simpleTime / bitmapTime)}x faster")
        } else {
            println("Simple is ${String.format("%.2f", bitmapTime / simpleTime)}x faster")
        }
    }
}