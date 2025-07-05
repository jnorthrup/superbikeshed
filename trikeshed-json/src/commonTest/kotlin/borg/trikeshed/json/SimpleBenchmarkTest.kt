package borg.trikeshed.json

import borg.trikeshed.lib.*
import borg.trikeshed.core.*
import kotlin.test.*
import kotlin.time.*

/**
 * Simple benchmark tests to compare JSON scanner performance
 */
@OptIn(ExperimentalTime::class)
class SimpleBenchmarkTest {
    
    // Test data generators
    private fun generateSimpleJson(count: Int): String = buildString {
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
            append("""{"id":$i,"name":"item$i","value":${i * 3.14}}""")
        }
        append("]")
    }
    
    @Test
    fun benchmarkSimpleVsBitmap() {
        val json = generateSimpleJson(100)
        
        println("\n=== Simple vs Bitmap Scanner Benchmark ===")
        println("JSON size: ${json.length} characters")
        
        // Warm up
        repeat(10) {
            json.simpleJson().properties()
            json.json().properties()
        }
        
        // Benchmark simple scanner
        val simpleTime = measureTime {
            repeat(1000) {
                val scanner = json.simpleJson()
                scanner.properties()
            }
        }
        
        // Benchmark bitmap scanner
        val bitmapTime = measureTime {
            repeat(1000) {
                val scanner = json.json()
                scanner.properties()
            }
        }
        
        println("Simple Scanner: ${simpleTime.inWholeMilliseconds}ms for 1000 iterations")
        println("Bitmap Scanner: ${bitmapTime.inWholeMilliseconds}ms for 1000 iterations")
        println("Simple is ${String.format("%.2f", simpleTime.inWholeNanoseconds.toDouble() / bitmapTime.inWholeNanoseconds)}x the time of Bitmap")
    }
    
    @Test
    fun benchmarkPropertyExtraction() {
        val sizes = listOf(10, 100, 1000)
        
        println("\n=== Property Extraction Benchmark ===")
        
        for (size in sizes) {
            val json = generateSimpleJson(size)
            println("\nJSON with $size properties (${json.length} chars):")
            
            // Simple scanner
            val simpleTime = measureTime {
                repeat(100) {
                    json.simpleJson().properties()
                }
            }
            
            // Bitmap scanner  
            val bitmapTime = measureTime {
                repeat(100) {
                    json.json().properties()
                }
            }
            
            println("  Simple: ${simpleTime.inWholeMilliseconds}ms")
            println("  Bitmap: ${bitmapTime.inWholeMilliseconds}ms")
        }
    }
    
    @Test
    fun benchmarkRepeatedQueries() {
        val json = generateSimpleJson(50)
        
        println("\n=== Repeated Query Benchmark ===")
        
        // Parse once
        val simpleScanner = json.simpleJson()
        val bitmapScanner = json.json()
        
        // Force initial scan
        simpleScanner.properties()
        bitmapScanner.properties()
        
        // Benchmark repeated queries
        val queries = listOf("field10", "field25", "field40")
        
        val simpleQueryTime = measureTime {
            repeat(1000) {
                for (query in queries) {
                    simpleScanner.query(query)
                }
            }
        }
        
        val bitmapQueryTime = measureTime {
            repeat(1000) {
                for (query in queries) {
                    bitmapScanner.query(query)
                }
            }
        }
        
        println("3000 queries (3 fields x 1000 iterations):")
        println("  Simple: ${simpleQueryTime.inWholeMilliseconds}ms")
        println("  Bitmap: ${bitmapQueryTime.inWholeMilliseconds}ms")
    }
    
    @Test
    fun benchmarkFingerprinting() {
        val json1 = generateSimpleJson(100)
        val json2 = generateArrayJson(50)
        
        println("\n=== Fingerprinting Benchmark ===")
        
        val simpleFingerprintTime = measureTime {
            repeat(1000) {
                json1.simpleJson().fingerprint()
                json2.simpleJson().fingerprint()
            }
        }
        
        val bitmapFingerprintTime = measureTime {
            repeat(1000) {
                json1.json().fingerprint()
                json2.json().fingerprint()
            }
        }
        
        println("2000 fingerprint operations:")
        println("  Simple: ${simpleFingerprintTime.inWholeMilliseconds}ms")
        println("  Bitmap: ${bitmapFingerprintTime.inWholeMilliseconds}ms")
    }
    
    @Test
    fun benchmarkLargeJson() {
        // Generate increasingly large JSON
        val sizes = listOf(100, 1000, 10000)
        
        println("\n=== Large JSON Benchmark ===")
        
        for (size in sizes) {
            val json = generateArrayJson(size)
            println("\nArray with $size elements (${json.length} chars):")
            
            val simpleTime = measureTime {
                json.simpleJson().properties()
            }
            
            val bitmapTime = measureTime {
                json.json().properties()
            }
            
            println("  Simple: ${simpleTime.inWholeMilliseconds}ms")
            println("  Bitmap: ${bitmapTime.inWholeMilliseconds}ms")
            println("  Bitmap is ${String.format("%.2f", simpleTime.inWholeNanoseconds.toDouble() / bitmapTime.inWholeNanoseconds)}x faster")
        }
    }
    
    @Test
    fun benchmarkSerializationIntegration() {
        val data = SimpleTestData("benchmark", 42, true)
        
        println("\n=== Serialization Integration Benchmark ===")
        
        // Encoding
        val simpleEncodeTime = measureTime {
            repeat(1000) {
                TrikeShedJson.Simple.encodeToString(SimpleTestData.serializer(), data)
            }
        }
        
        val bitmapEncodeTime = measureTime {
            repeat(1000) {
                TrikeShedJson.Default.encodeToString(SimpleTestData.serializer(), data)
            }
        }
        
        println("Encoding (1000 iterations):")
        println("  Simple: ${simpleEncodeTime.inWholeMilliseconds}ms")
        println("  Bitmap: ${bitmapEncodeTime.inWholeMilliseconds}ms")
        
        // Decoding
        val json = """{"name":"benchmark","count":42,"active":true}"""
        
        val simpleDecodeTime = measureTime {
            repeat(1000) {
                TrikeShedJson.Simple.decodeFromString(SimpleTestData.serializer(), json)
            }
        }
        
        val bitmapDecodeTime = measureTime {
            repeat(1000) {
                TrikeShedJson.Default.decodeFromString(SimpleTestData.serializer(), json)
            }
        }
        
        println("\nDecoding (1000 iterations):")
        println("  Simple: ${simpleDecodeTime.inWholeMilliseconds}ms")
        println("  Bitmap: ${bitmapDecodeTime.inWholeMilliseconds}ms")
    }
}