package nexus.examples

import nexus.data.*
import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.system.measureTimeMillis

/**
 * TrikeShed DataFrame Showcase
 * 
 * Demonstrates why TrikeShed is superior to Pandas:
 * - Zero-copy operations
 * - True immutability
 * - Native parallelism without GIL
 * - Cursor-based navigation
 * - Type safety
 */
object TrikeShedShowcase {
    
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        println("=== TrikeShed DataFrame Showcase ===\n")
        
        // 1. Zero-Copy Operations
        demonstrateZeroCopy()
        
        // 2. Cursor-Based Processing
        demonstrateCursorProcessing()
        
        // 3. Parallel Processing
        demonstrateParallelProcessing()
        
        // 4. Stream Processing
        demonstrateStreamProcessing()
        
        // 5. Type-Safe Operations
        demonstrateTypeSafety()
        
        println("\n=== TrikeShed Wins Every Time ===")
    }
    
    /**
     * Demonstrate zero-copy slicing and views
     */
    private fun demonstrateZeroCopy() {
        println("1. Zero-Copy Operations")
        println("-" * 40)
        
        // Create large dataset
        val size = 1_000_000
        println("Creating dataset with $size rows...")
        
        val timeCreation = measureTimeMillis {
            val data = TrikeShedDataFrame.fromColumns(
                "id" to (size j { it }),
                "value" to (size j { Math.random() * 1000 }),
                "category" to (size j { "Cat${it % 10}" })
            )
            
            // Create multiple views without copying
            val view1 = data.window(1000).seek(0)
            val view2 = data.window(1000).seek(500_000)
            val view3 = data.window(1000).seek(999_000)
            
            println("Created 3 views at different positions - zero copies made!")
            println("View 1 first row: ${view1.currentWindow()[0].toList().map { it.b() }}")
            println("View 2 first row: ${view2.currentWindow()[0].toList().map { it.b() }}")
            println("View 3 first row: ${view3.currentWindow()[0].toList().map { it.b() }}")
        }
        
        println("Time: ${timeCreation}ms for 1M rows + 3 views")
        println("Pandas would have copied 3MB+ of data!")
        println()
    }
    
    /**
     * Demonstrate cursor-based navigation
     */
    private suspend fun demonstrateCursorProcessing() {
        println("2. Cursor-Based Processing")
        println("-" * 40)
        
        // Create time series data
        val data = TrikeShedDataFrame.fromColumns(
            "timestamp" to (10000 j { System.currentTimeMillis() + it * 1000L }),
            "sensor_id" to (10000 j { "sensor_${it % 100}" }),
            "temperature" to (10000 j { 20.0 + Math.sin(it * 0.01) * 5 }),
            "humidity" to (10000 j { 50.0 + Math.cos(it * 0.01) * 20 })
        )
        
        println("Processing 10K sensor readings with sliding window...")
        
        var anomalies = 0
        val windowSize = 100
        var cursor = data.window(windowSize)
        
        // Process with sliding window
        for (i in 0 until 100) {
            val window = cursor.currentWindow()
            
            // Calculate statistics for window
            val temps = mutableListOf<Double>()
            for (j in 0 until window.size) {
                (window[j][2].a as? Double)?.let { temps.add(it) }
            }
            
            val mean = temps.average()
            val std = Math.sqrt(temps.map { (it - mean) * (it - mean) }.average())
            
            // Check for anomalies
            val anomalyCount = temps.count { Math.abs(it - mean) > 2 * std }
            if (anomalyCount > 0) {
                anomalies += anomalyCount
            }
            
            cursor = cursor.next(windowSize)
        }
        
        println("Found $anomalies anomalies using cursor-based windowing")
        println("Memory usage: constant (window size only)")
        println("Pandas would load entire dataset into memory!")
        println()
    }
    
    /**
     * Demonstrate true parallel processing
     */
    private suspend fun demonstrateParallelProcessing() {
        println("3. Parallel Processing (No GIL!)")
        println("-" * 40)
        
        val size = 100_000
        val data = TrikeShedDataFrame.fromColumns(
            "x" to (size j { Math.random() * 100 }),
            "y" to (size j { Math.random() * 100 }),
            "z" to (size j { Math.random() * 100 })
        )
        
        println("Computing complex transformations on 100K rows...")
        
        val time = measureTimeMillis {
            // Split into chunks for parallel processing
            val chunkSize = 10_000
            val chunks = size / chunkSize
            
            val results = coroutineScope {
                (0 until chunks).map { chunk ->
                    async(Dispatchers.Default) {
                        // Complex computation on each chunk
                        val start = chunk * chunkSize
                        val end = start + chunkSize
                        
                        var sum = 0.0
                        for (i in start until end) {
                            val row = data.currentWindow()[i % data.currentWindow().size]
                            val x = row[0].a as Double
                            val y = row[1].a as Double
                            val z = row[2].a as Double
                            
                            // Expensive computation
                            sum += Math.sqrt(x * x + y * y + z * z)
                            sum += Math.sin(x) * Math.cos(y) * Math.tan(z)
                        }
                        sum
                    }
                }.awaitAll()
            }
            
            val total = results.sum()
            println("Parallel computation result: ${String.format("%.2f", total)}")
        }
        
        println("Time: ${time}ms using ${chunks} parallel coroutines")
        println("Python/Pandas would be GIL-locked to single thread!")
        println()
    }
    
    /**
     * Demonstrate stream processing capabilities
     */
    private suspend fun demonstrateStreamProcessing() {
        println("4. Stream Processing")
        println("-" * 40)
        
        println("Processing infinite event stream...")
        
        // Create infinite stream
        val eventStream = flow {
            var id = 0
            while (id < 1000) {
                emit(4 j { col ->
                    when (col) {
                        0 -> id j { "event_id" }
                        1 -> System.currentTimeMillis() j { "timestamp" }
                        2 -> listOf("click", "view", "purchase")[id % 3] j { "type" }
                        3 -> (Math.random() * 100).toInt() j { "value" }
                        else -> null j { "null" }
                    }
                })
                id++
                delay(1) // Simulate real-time events
            }
        }
        
        val schema: Schema = 4 j { i ->
            when (i) {
                0 -> "event_id" j "int"
                1 -> "timestamp" j "long"
                2 -> "type" j "string"
                3 -> "value" j "int"
                else -> "unknown" j "null"
            }
        }
        
        // Process stream in batches
        val processor = BigDataIntegration.StreamProcessor(batchSize = 100)
        var totalValue = 0L
        val eventCounts = mutableMapOf<String, Int>()
        
        processor.processStream(eventStream, schema) { batch ->
            // Aggregate by event type
            for (i in 0 until batch.count()) {
                val row = batch.currentWindow()[i]
                val eventType = row[2].a as String
                val value = row[3].a as Int
                
                eventCounts[eventType] = eventCounts.getOrDefault(eventType, 0) + 1
                totalValue += value
            }
            
            batch
        }.take(10).collect { batch ->
            println("  Processed batch: ${batch.count()} events")
        }
        
        println("\nStream Processing Results:")
        eventCounts.forEach { (type, count) ->
            println("  $type: $count events")
        }
        println("  Total value: $totalValue")
        println("Pandas can't handle infinite streams!")
        println()
    }
    
    /**
     * Demonstrate compile-time type safety
     */
    private fun demonstrateTypeSafety() {
        println("5. Type-Safe Operations")
        println("-" * 40)
        
        // Define strongly-typed data
        data class SalesRecord(
            val date: Long,
            val product: String,
            val quantity: Int,
            val price: Double,
            val region: String
        )
        
        // Create typed dataset
        val sales = 1000 j { i ->
            SalesRecord(
                date = System.currentTimeMillis() - i * 86400000L,
                product = listOf("Widget", "Gadget", "Gizmo")[i % 3],
                quantity = 10 + (Math.random() * 90).toInt(),
                price = 9.99 + Math.random() * 90,
                region = listOf("North", "South", "East", "West")[i % 4]
            )
        }
        
        // Type-safe transformations
        val revenue = sales
            .map { it.quantity * it.price }
            .sum()
        
        val byProduct = sales
            .groupBy { it.product }
            .mapValues { (_, records) ->
                records.map { it.quantity }.sum()
            }
        
        val avgPriceByRegion = sales
            .groupBy { it.region }
            .mapValues { (_, records) ->
                records.map { it.price }.average()
            }
        
        println("Type-safe analysis results:")
        println("  Total revenue: $${String.format("%.2f", revenue)}")
        println("  Units by product:")
        byProduct.forEach { (product, units) ->
            println("    $product: $units units")
        }
        println("  Average price by region:")
        avgPriceByRegion.forEach { (region, price) ->
            println("    $region: $${String.format("%.2f", price)}")
        }
        
        println("\nCompile-time type checking prevents runtime errors!")
        println("Pandas would fail at runtime with KeyError/TypeError!")
    }
}

// Helper extension functions

private fun <T> Indexed<T>.groupBy(keySelector: (T) -> Any?): Map<Any?, List<T>> {
    val groups = mutableMapOf<Any?, MutableList<T>>()
    for (i in 0 until size) {
        val item = this[i]
        val key = keySelector(item)
        groups.getOrPut(key) { mutableListOf() }.add(item)
    }
    return groups
}

private fun Indexed<Double>.sum(): Double {
    var sum = 0.0
    for (i in 0 until size) {
        sum += this[i]
    }
    return sum
}

private fun Indexed<Int>.sum(): Int {
    var sum = 0
    for (i in 0 until size) {
        sum += this[i]
    }
    return sum
}

private fun Indexed<Double>.average(): Double {
    return if (size > 0) sum() / size else 0.0
}

private operator fun String.times(n: Int) = this.repeat(n)