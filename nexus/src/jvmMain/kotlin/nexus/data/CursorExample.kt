package nexus.data

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Example usage of TrikeShed DataFrame with cursor-based operations
 * and big data integrations
 */
object CursorExample {
    
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        
        // Create sample data
        val df = TrikeShedDataFrame.fromColumns(
            "id" to (100 j { it }),
            "name" to (100 j { "User$it" }),
            "age" to (100 j { 20 + (it % 50) }),
            "score" to (100 j { (it * 1.5).toInt() })
        )
        
        println("=== TrikeShed DataFrame Cursor Demo ===\n")
        
        // 1. Basic cursor operations
        println("1. Cursor Navigation:")
        df.show(5)
        
        println("\n2. Window-based processing:")
        val windowed = df.window(10).seek(20)
        println("Window at position 20:")
        windowed.currentWindow().let { window ->
            println("Window size: ${window.size}")
        }
        
        // 2. DataFrame operations
        println("\n3. Select and filter:")
        val filtered = df
            .select("name", "age", "score")
            .filter { row -> 
                (row[1].a as Int) > 30  // age > 30
            }
        
        // 3. Aggregations
        println("\n4. Aggregations:")
        println("Count: ${df.count()}")
        println("Average age: ${df.mean("age")}")
        println("Total score: ${df.sum("score")}")
        
        // 4. Pandas integration
        println("\n5. Pandas Integration:")
        val pandasScript = df.select("age", "score").toPandasScript()
        println("Generated Pandas script:")
        println(pandasScript.lines().take(10).joinToString("\n"))
        println("...")
        
        // 5. Spark integration
        println("\n6. Spark RDD Generation:")
        val sparkCode = df.select("name", "age").toSparkRDD()
        println(sparkCode.lines().take(5).joinToString("\n"))
        println("...")
        
        // 6. Distributed cursor simulation
        println("\n7. Distributed Cursor:")
        val distributedSource = object : DataSource {
            override suspend fun fetchPartition(partitionId: Int, size: Int): DataFrame {
                delay(100) // Simulate network delay
                return size j { i ->
                    3 j { j ->
                        when (j) {
                            0 -> (partitionId * size + i) j { "id" }
                            1 -> "Partition$partitionId-Item$i" j { "name" }
                            2 -> (Math.random() * 100).toInt() j { "value" }
                            else -> null j { "null" }
                        }
                    }
                }
            }
            
            override suspend fun getPartitionCount(): Int = 10
        }
        
        val cursor = BigDataIntegration.DistributedCursor(distributedSource, 5)
        println("Fetching distributed partitions:")
        repeat(3) { i ->
            cursor.next()?.let { partition ->
                println("  Partition $i: ${partition.size} rows")
            }
        }
        
        // 7. Stream processing
        println("\n8. Stream Processing:")
        val streamSource = flow {
            repeat(25) { i ->
                emit(3 j { j ->
                    when (j) {
                        0 -> i j { "$i" }
                        1 -> "Stream$i" j { "stream" }
                        2 -> (i * 2) j { "${i * 2}" }
                        else -> null j { "null" }
                    }
                })
                delay(50)
            }
        }
        
        val schema: Schema = 3 j { i ->
            when (i) {
                0 -> "id" j "int"
                1 -> "event" j "string"
                2 -> "value" j "int"
                else -> "unknown" j "null"
            }
        }
        
        val processor = BigDataIntegration.StreamProcessor(batchSize = 5)
        
        processor.processStream(streamSource, schema) { batch ->
            println("  Processing batch with ${batch.count()} events")
            batch
        }.take(3).collect()
        
        // 8. Complex integration example
        println("\n9. Complex Big Data Pipeline:")
        val pipeline = df
            .select("age", "score")
            .filter { row -> (row[0].a as Int) > 25 }
        
        // Would execute on Spark
        val sparkTransform = """
            .groupBy("age")
            .agg(
                avg("score").alias("avg_score"),
                count("score").alias("count")
            )
            .orderBy(desc("avg_score"))
        """.trimIndent()
        
        println("Pipeline would execute:")
        println("1. Filter in TrikeShed (age > 25)")
        println("2. Send to Spark for aggregation")
        println("3. Return results to TrikeShed")
        println("\nSpark transformation: $sparkTransform")
        
        // 9. Governance through JVM Python
        println("\n10. JVM Python Governance:")
        val governanceCommands = listOf(
            "set_resource_limit" to mapOf("memory" to "2GB", "cores" to 4),
            "enable_caching" to mapOf("size" to "100MB"),
            "set_partition_size" to mapOf("size" to 10000)
        )
        
        governanceCommands.forEach { (cmd, params) ->
            println("  Governance: $cmd -> $params")
        }
        
        println("\n=== Demo Complete ===")
    }
    
    /**
     * Example of a real-world use case: Log analysis pipeline
     */
    suspend fun logAnalysisPipeline() {
        // Define schema for log data
        val logSchema: Schema = 5 j { i ->
            when (i) {
                0 -> "timestamp" j "long"
                1 -> "level" j "string"
                2 -> "service" j "string"
                3 -> "message" j "string"
                4 -> "latency_ms" j "int"
                else -> "unknown" j "null"
            }
        }
        
        // Create distributed cursor for reading large log files
        val logSource = object : DataSource {
            override suspend fun fetchPartition(partitionId: Int, size: Int): DataFrame {
                // In real implementation, this would read from HDFS/S3
                return size j { i ->
                    5 j { j ->
                        when (j) {
                            0 -> System.currentTimeMillis() j { "timestamp" }
                            1 -> listOf("INFO", "WARN", "ERROR").random() j { "level" }
                            2 -> listOf("auth", "api", "db").random() j { "service" }
                            3 -> "Event $i in partition $partitionId" j { "message" }
                            4 -> (Math.random() * 1000).toInt() j { "latency" }
                            else -> null j { "null" }
                        }
                    }
                }
            }
            
            override suspend fun getPartitionCount(): Int = 100
        }
        
        val cursor = BigDataIntegration.DistributedCursor(logSource)
        val spark = BigDataIntegration.SparkBridge()
        
        // Process in batches
        repeat(5) {
            cursor.next()?.let { partition ->
                val df = TrikeShedDataFrame.fromRows(logSchema, partition)
                
                // Local filtering in TrikeShed
                val errors = df.filter { row ->
                    row[1].a == "ERROR"
                }
                
                // Distributed aggregation in Spark
                val aggregated = spark.distributedAgg(
                    errors,
                    listOf("service"),
                    mapOf("latency_ms" to "avg", "*" to "count")
                )
                
                // Results back in TrikeShed for further processing
                println("Error analysis for partition $it complete")
            }
        }
    }
}