import borg.trikeshed.ccek.*
import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

suspend fun main() = runBlocking {
    println("🎯 CCEK (Coroutine Context Element Key) Demo")
    println("=" * 50)
    println()
    
    // Create sample data
    val sampleData = mapOf(
        "name" to "CCEK Demo Data",
        "version" to "1.0",
        "timestamp" to System.currentTimeMillis(),
        "payload" to mapOf(
            "items" to listOf("item1", "item2", "item3"),
            "metadata" to mapOf("source" to "demo")
        )
    )
    
    // Create CCEK context
    val ccekContext = CcekContext(
        executionId = "demo_exec_001",
        sessionId = "demo_session_001",
        action = "data_processing_demo"
    )
    
    println("📋 Initial Data:")
    println(Json.encodeToString(JsonObject.serializer(), buildJsonObject {
        sampleData.forEach { (key, value) ->
            when (value) {
                is String -> put(key, value)
                is Number -> put(key, value.toLong())
                is Map<*, *> -> put(key, buildJsonObject {
                    @Suppress("UNCHECKED_CAST")
                    (value as Map<String, Any>).forEach { (k, v) ->
                        when (v) {
                            is String -> put(k, v)
                            is List<*> -> put(k, buildJsonArray {
                                v.forEach { item -> add(item.toString()) }
                            })
                            is Map<*, *> -> put(k, buildJsonObject {
                                @Suppress("UNCHECKED_CAST")
                                (v as Map<String, String>).forEach { (mk, mv) ->
                                    put(mk, mv)
                                }
                            })
                        }
                    }
                })
            }
        }
    }))
    println()
    
    // Create transformation pipeline
    val pipeline = ccekPipeline("demo_pipeline") {
        validate("required_fields", "data_integrity")
        transform("normalize_data", "add_processing_metadata")
        serialize(SerializationFormat.JSON)
        metadata("version", "1.0")
        metadata("author", "CCEK Demo")
    }
    
    println("🔄 Pipeline Configuration:")
    println("  Name: ${pipeline.name}")
    println("  Steps: ${pipeline.steps.size}")
    pipeline.steps.forEachIndexed { index, step ->
        println("    ${index + 1}. ${step::class.simpleName} (Phase: ${step.phase})")
    }
    println("  Metadata: ${pipeline.metadata}")
    println()
    
    // Execute with CCEK context
    println("⚡ Executing CCEK Pipeline...")
    withContext(ccekContext) {
        // Show context information
        val currentContext = ccekContext()
        println("  Context ID: ${currentContext?.executionId}")
        println("  Session ID: ${currentContext?.sessionId}")
        println("  Current Phase: ${currentContext?.phase}")
        println("  Action: ${currentContext?.action}")
        println()
        
        // Execute the pipeline
        val result = executeCCEK(sampleData, pipeline, ccekContext)
        
        when (result) {
            is ExecutionResult.Success -> {
                println("✅ Pipeline executed successfully!")
                println("  Final Phase: ${result.context.phase}")
                println("  Result: ${result.data}")
            }
            is ExecutionResult.Error -> {
                println("❌ Pipeline execution failed!")
                println("  Error Phase: ${result.context.phase}")
                println("  Error Message: ${result.message}")
            }
        }
    }
    
    println()
    println("🔧 Advanced CCEK Features Demo:")
    
    // Demo io_uring batch context
    val uringContext = UringBatchContext(
        batchSize = 16,
        ringFd = -1,
        sqeDepth = 2048,
        cqeDepth = 4096
    )
    
    println("  ⚡ io_uring Batch Context:")
    println("    Batch Size: ${uringContext.batchSize}")
    println("    SQE Depth: ${uringContext.sqeDepth}")
    println("    CQE Depth: ${uringContext.cqeDepth}")
    
    // Demo channel chain context
    val channel1 = AsyncChannelContext(
        channelId = "tcp_server_001",
        fd = 3,
        type = AsyncChannelContext.ChannelType.TCP_SERVER,
        localAddr = "127.0.0.1:8080",
        remoteAddr = "0.0.0.0:0"
    )
    
    val channel2 = AsyncChannelContext(
        channelId = "tcp_client_001", 
        fd = 4,
        type = AsyncChannelContext.ChannelType.TCP_CLIENT,
        localAddr = "127.0.0.1:0",
        remoteAddr = "127.0.0.1:8080"
    )
    
    val channelChain = ChannelChainContext(
        arrayOf(channel1).size j { arrayOf(channel1)::get }
    ) chain channel2
    
    println("  🔗 Channel Chain Context:")
    println("    Chain Length: ${channelChain.channels.a}")
    for (i in 0 until channelChain.channels.a) {
        val channel = channelChain.channels.b(i)
        println("    Channel ${i + 1}: ${channel.channelId} (${channel.type})")
        println("      Local: ${channel.localAddr}")
        println("      Remote: ${channel.remoteAddr}")
    }
    
    // Execute with combined contexts
    println()
    println("🚀 Executing with Combined CCEK + io_uring Context...")
    
    withCCEKUring(ccekContext.copy(action = "advanced_processing"), uringContext) {
        val context = ccekContext()
        val uring = uringBatch()
        val chain = channelChain()
        
        println("  ✅ Combined context active:")
        println("    CCEK Action: ${context?.action}")
        println("    io_uring Batch Size: ${uring?.batchSize}")
        if (chain != null) {
            println("    Channel Chain: ${chain.channels.a} channels")
        }
    }
    
    println()
    println("🎉 CCEK Demo Complete!")
    println("   Ready for fiduciary system integration")
}

operator fun String.times(n: Int): String = repeat(n)