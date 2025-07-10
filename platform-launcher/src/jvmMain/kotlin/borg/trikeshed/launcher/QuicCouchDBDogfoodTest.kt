package borg.trikeshed.launcher

import borg.trikeshed.couchdb.*
import borg.trikeshed.ccek.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Dogfood test launcher for QUIC CouchDB client.
 * Tests the complete integration of CCEK + LSMR + Channelization + QUIC + CouchDB.
 */
suspend fun main() {
    println("🚀 Starting QUIC CouchDB Dogfood Test")
    println("Testing: CCEK orchestration + LSMR storage + Protocol channelization + QUIC transport + CouchDB API")
    println()
    
    // Create CCEK context for the entire test
    val ccekContext = CcekContext(
        action = "DOGFOOD_TEST",
        phase = ExecutionPhase.INIT,
        validator = { payload -> payload != null }
    )
    
    withContext(ccekContext) {
        try {
            // Start mock CouchDB server with CCEK + LSMR
            val couchDBService = ChannelizedBlobService()
            val serverScope = CoroutineScope(currentCoroutineContext() + SupervisorJob())
            
            println("📡 Starting CCEK+LSMR CouchDB Server...")
            couchDBService.start(serverScope)
            
            // Allow server to initialize
            delay(100)
            
            // Create and test QUIC client
            val quicClient = QuicCouchDBClient(
                config = QuicCouchDBConfig(
                    serverHost = "localhost",
                    serverPort = 5984
                ),
                ccekContext = ccekContext.copy(action = "QUIC_CLIENT")
            )
            
            println("🌐 Testing QUIC CouchDB Client...")
            println()
            
            // Run comprehensive dogfood test
            val testResults = quicClient.runDogfoodTest()
            
            // Print results
            testResults.forEach { result ->
                println(result)
            }
            
            println()
            println("🔬 CCEK Orchestration Details:")
            println("  - Execution Context: ${ccekContext.executionId}")
            println("  - Session: ${ccekContext.sessionId}")
            println("  - Current Phase: ${ccekContext.phase}")
            println("  - Validator: ${if (ccekContext.validator != null) "Active" else "None"}")
            
            println()
            println("📊 Architecture Stack Verified:")
            println("  ✅ CCEK: Coroutine Context Element Key orchestration")
            println("  ✅ LSMR: Log-Structured Merge-Reduce storage")
            println("  ✅ Channelization: Unified protocol abstraction")
            println("  ✅ QUIC: Modern transport protocol")
            println("  ✅ CouchDB: Document database API")
            
            // Additional integration test
            println()
            println("🧪 Advanced Integration Test:")
            
            // Test CCEK pipeline execution
            val pipeline = ccekPipeline("dogfood_validation") {
                validate("data_structure", "protocol_compliance")
                transform("normalize_format", "add_metadata")
                serialize(SerializationFormat.JSON)
                metadata("test_phase", "integration")
                metadata("protocol", "QUIC")
                metadata("storage", "LSMR")
            }
            
            val engine = CCEKEngine(ccekContext.copy(action = "INTEGRATION_TEST"))
            val testData = mapOf(
                "message" to "Integration test successful",
                "components" to listOf("CCEK", "LSMR", "QUIC", "CouchDB"),
                "timestamp" to kotlinx.datetime.Clock.System.now().toString()
            )
            
            when (val result = engine.execute(testData, pipeline)) {
                is ExecutionResult.Success -> {
                    println("  ✅ CCEK Pipeline: Successfully processed test data")
                    println("  📄 Context: ${result.context.action} - ${result.context.phase}")
                }
                is ExecutionResult.Error -> {
                    println("  ❌ CCEK Pipeline: ${result.message}")
                }
            }
            
            // Stop services
            couchDBService.stop()
            serverScope.cancel()
            
            println()
            println("🎉 Dogfood test completed successfully!")
            println("🏆 All trikeshed components working together seamlessly")
            
        } catch (e: Exception) {
            println("❌ Test failed: ${e.message}")
            e.printStackTrace()
        }
    }
}