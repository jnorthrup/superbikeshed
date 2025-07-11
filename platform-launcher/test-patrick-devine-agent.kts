#!/usr/bin/env kotlin

@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")

import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.datetime.*
import java.io.File
import java.nio.file.Paths

// Import our agent classes
import fiduciary.agents.*

/**
 * Simple test runner for Patrick Devine Agent
 * 
 * This script tests the core functionality of the Patrick Devine agent
 * without requiring the full Gradle build system.
 */
fun main() = runBlocking {
    println("=== Patrick Devine Agent Test Runner ===")
    
    val agent = PatrickDevineAgent()
    
    // Test 1: Conversation text preprocessing
    println("\n1. Testing conversation text preprocessing...")
    val rawText = """
        [00:00:01.000 --> 00:00:05.000] Hello, this is Patrick Devine speaking.
        [00:00:05.000 --> 00:00:08.000] Hello, this is Patrick Devine speaking about legal matters.
        [00:00:08.000 --> 00:00:12.000] Hello, this is Patrick Devine speaking about legal matters and fiduciary duty.
        
        [SPEAKER_TURN]
        
        [00:00:12.000 --> 00:00:15.000] Thank you for that clarification.
        [00:00:15.000 --> 00:00:18.000] Thank you for that clarification, Patrick.
    """.trimIndent()
    
    val cleanedText = agent.preprocessConversationText(rawText)
    println("✓ Text preprocessing completed")
    println("Original length: ${rawText.length}")
    println("Cleaned length: ${cleanedText.length}")
    println("Sample cleaned text: ${cleanedText.take(100)}...")
    
    // Test 2: Conversation collection
    println("\n2. Testing conversation collection...")
    val sources = listOf(
        ConversationSource(
            id = "test_archive",
            type = SourceType.LOCAL_FILES,
            path = "/tmp/test_conversations",
            metadata = mapOf("test" to "true")
        )
    )
    
    val collectionResult = agent.collectConversationTexts(sources)
    if (collectionResult.isSuccess) {
        val conversations = collectionResult.getOrThrow()
        println("✓ Conversation collection completed")
        println("Collected ${conversations.size} conversations")
        conversations.forEach { conv ->
            println("  - ${conv.id}: ${conv.content.take(50)}...")
        }
    } else {
        println("✗ Conversation collection failed: ${collectionResult.exceptionOrNull()}")
    }
    
    // Test 3: Whisper model management
    println("\n3. Testing Whisper model management...")
    val modelResult = agent.ensureWhisperModel("ggml-small.en-tdrz.bin", "")
    if (modelResult.isSuccess) {
        val modelPath = modelResult.getOrThrow()
        println("✓ Whisper model management completed")
        println("Model path: $modelPath")
    } else {
        println("✗ Whisper model management failed: ${modelResult.exceptionOrNull()}")
    }
    
    // Test 4: CouchDB operations (mock)
    println("\n4. Testing CouchDB operations...")
    val testConversation = ConversationText(
        id = "test_conv_001",
        sourceId = "test_source",
        content = "This is a test conversation about fiduciary duty.",
        timestamp = Clock.System.now(),
        metadata = mapOf("test" to "true")
    )
    
    val couchConfig = CouchDBConfig(
        url = "http://localhost:5984",
        database = "patrick_devine_test",
        username = "admin",
        password = "password"
    )
    
    val storeResult = agent.storeConversationInCache(testConversation, couchConfig)
    if (storeResult.isSuccess) {
        val storedId = storeResult.getOrThrow()
        println("✓ CouchDB store completed")
        println("Stored document ID: $storedId")
        
        val retrieveResult = agent.retrieveConversationFromCache(storedId, couchConfig)
        if (retrieveResult.isSuccess) {
            val retrieved = retrieveResult.getOrThrow()
            println("✓ CouchDB retrieve completed")
            println("Retrieved conversation: ${retrieved.id}")
        } else {
            println("✗ CouchDB retrieve failed: ${retrieveResult.exceptionOrNull()}")
        }
    } else {
        println("✗ CouchDB store failed: ${storeResult.exceptionOrNull()}")
    }
    
    // Test 5: Git operations
    println("\n5. Testing Git operations...")
    val gitConfig = GitConfig(
        repositoryPath = "/tmp/patrick_test_repo",
        branch = "main",
        authorName = "Patrick Devine Agent",
        authorEmail = "agent@patrickdevine.com",
        autoCommit = true,
        initializeIfNotExists = true
    )
    
    val conversationData = ConversationData(
        id = "test_batch_001",
        conversations = listOf(testConversation),
        metadata = mapOf("test" to "true", "timestamp" to Clock.System.now().toString())
    )
    
    val gitResult = agent.commitConversationData(conversationData, gitConfig)
    if (gitResult.isSuccess) {
        val commitInfo = gitResult.getOrThrow()
        println("✓ Git commit completed")
        println("Commit hash: ${commitInfo.commitHash}")
        println("Commit message: ${commitInfo.message}")
    } else {
        println("✗ Git commit failed: ${gitResult.exceptionOrNull()}")
    }
    
    // Test 6: K2Script sandbox operations
    println("\n6. Testing K2Script sandbox operations...")
    val k2scriptConfig = K2ScriptConfig(
        sandboxDir = "/tmp/patrick_sandboxes",
        isolationLevel = IsolationLevel.PROCESS,
        resourceLimits = ResourceLimits(
            maxMemory = "1GB",
            maxCpu = 1,
            maxDisk = "5GB"
        )
    )
    
    val operation = AgentOperation(
        id = "test_op_001",
        type = OperationType.TEXT_CLEANING,
        input = mapOf("text_file" to "/tmp/test.txt"),
        metadata = mapOf("test" to "true")
    )
    
    val sandboxResult = agent.executeInK2ScriptSandbox(operation, k2scriptConfig)
    if (sandboxResult.isSuccess) {
        val result = sandboxResult.getOrThrow()
        println("✓ K2Script sandbox execution completed")
        println("Operation ID: ${result.operationId}")
        println("Sandbox ID: ${result.sandboxId}")
        println("Execution time: ${result.executionTime}ms")
    } else {
        println("✗ K2Script sandbox execution failed: ${sandboxResult.exceptionOrNull()}")
    }
    
    // Test 7: Complete pipeline
    println("\n7. Testing complete pipeline...")
    val pipelineConfig = PatrickDevinePipelineConfig(
        sources = sources,
        whisperConfig = WhisperConfig(
            model = "ggml-small.en-tdrz.bin",
            language = "en",
            diarization = true
        ),
        couchConfig = couchConfig,
        k2scriptConfig = k2scriptConfig,
        gitConfig = gitConfig
    )
    
    val pipelineResult = agent.runCompletePipeline(pipelineConfig)
    if (pipelineResult.isSuccess) {
        val result = pipelineResult.getOrThrow()
        println("✓ Complete pipeline executed successfully")
        println("Conversations collected: ${result.collectionResult.conversationsCollected}")
        println("Transcripts generated: ${result.transcriptionResult.transcriptsGenerated}")
        println("Documents stored: ${result.cacheResult.documentsStored}")
        println("Git commit: ${result.gitResult.commitHash}")
    } else {
        println("✗ Complete pipeline failed: ${pipelineResult.exceptionOrNull()}")
    }
    
    println("\n=== Test Runner Completed ===")
    println("All core functionality has been tested.")
    println("The Patrick Devine agent is ready for TDD development!")
} 