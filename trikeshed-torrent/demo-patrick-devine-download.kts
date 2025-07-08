#!/usr/bin/env kotlin

@file:DependsOn("kotlinx-coroutines-core:1.7.3")
@file:DependsOn("kotlinx-serialization-json:1.6.0")

import borg.trikeshed.torrent.batch.*
import borg.trikeshed.torrent.client.TrikeAriaClient
import borg.trikeshed.torrent.client.TrikeRpcClient
import borg.trikeshed.torrent.rpc.TorrentRpcServer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Duration.Companion.seconds

suspend fun downloadPatrickDevineCorpus(): Map<String, Any> {
    val rpcServer = TorrentRpcServer()
    val rpcClient = TrikeRpcClient("http://localhost:6800/jsonrpc")
    val ariaClient = TrikeAriaClient(rpcClient)
    
    // Patrick Devine archive URLs from fiduciary module
    val patrickDevineArchives = listOf(
        "https://archive.org/download/patrickdevine/patrickdevine.zip",
        "https://archive.org/download/patrickdevinecalls/Patrick%20Devine%20Calls.zip"
    )
    
    // Create high-priority batch for Patrick Devine corpus
    val corpusBatchId = ariaClient.createBatch(
        name = "Patrick Devine Corpus",
        maxConcurrent = 2,
        priority = "CRITICAL",
        mediaPlayerIntegration = false
    )
    
    // Add archives to batch
    val torrentIds = mutableListOf<String>()
    patrickDevineArchives.forEach { archiveUrl ->
        val torrentId = ariaClient.addTorrentToBatch(corpusBatchId, archiveUrl)
        torrentIds.add(torrentId)
    }
    
    // Start batch download
    ariaClient.startBatch(corpusBatchId)
    
    // Monitor download progress
    val downloadStatus = ariaClient.monitorBatch(
        batchId = corpusBatchId,
        intervalMs = 5000,
        maxDuration = 3600000 // 1 hour max
    )
    
    // Wait for completion
    val finalStatus = ariaClient.getBatchStatus(corpusBatchId)
    
    return mapOf(
        "batchId" to corpusBatchId,
        "torrentIds" to torrentIds,
        "archives" to patrickDevineArchives,
        "finalStatus" to finalStatus,
        "downloadHistory" to downloadStatus
    )
}

suspend fun processPatrickDevineWithFiduciary(): Map<String, Any> {
    val downloadResult = downloadPatrickDevineCorpus()
    val batchId = downloadResult["batchId"] as String
    
    // Create fiduciary processing batch
    val rpcClient = TrikeRpcClient("http://localhost:6800/jsonrpc")
    val ariaClient = TrikeAriaClient(rpcClient)
    
    val processingBatchId = ariaClient.createBatch(
        name = "Patrick Devine Processing",
        maxConcurrent = 1,
        priority = "HIGH",
        mediaPlayerIntegration = false
    )
    
    // Add processing tasks
    val processingTasks = listOf(
        "extract_text_documents",
        "extract_audio_transcripts", 
        "extract_legal_documents",
        "build_search_index",
        "extract_concepts"
    )
    
    processingTasks.forEach { task ->
        ariaClient.addTorrentToBatch(processingBatchId, "task://$task")
    }
    
    ariaClient.startBatch(processingBatchId)
    
    return mapOf(
        "downloadResult" to downloadResult,
        "processingBatchId" to processingBatchId,
        "processingTasks" to processingTasks
    )
}

suspend fun createPatrickDevineWorkflow(): Map<String, Any> {
    val rpcClient = TrikeRpcClient("http://localhost:6800/jsonrpc")
    val ariaClient = TrikeAriaClient(rpcClient)
    
    // Create workflow with multiple stages
    val workflowBatches = mutableMapOf<String, String>()
    
    // Stage 1: Download archives
    val downloadBatchId = ariaClient.createBatch(
        name = "Patrick Devine Download",
        maxConcurrent = 2,
        priority = "CRITICAL"
    )
    
    val archives = listOf(
        "https://archive.org/download/patrickdevine/patrickdevine.zip",
        "https://archive.org/download/patrickdevinecalls/Patrick%20Devine%20Calls.zip"
    )
    
    archives.forEach { archive ->
        ariaClient.addTorrentToBatch(downloadBatchId, archive)
    }
    
    workflowBatches["download"] = downloadBatchId
    
    // Stage 2: Extract documents
    val extractBatchId = ariaClient.createBatch(
        name = "Document Extraction",
        maxConcurrent = 3,
        priority = "HIGH"
    )
    
    val documentTypes = listOf(".pdf", ".doc", ".txt", ".html", ".mp3", ".wav")
    documentTypes.forEach { ext ->
        ariaClient.addTorrentToBatch(extractBatchId, "extract://$ext")
    }
    
    workflowBatches["extract"] = extractBatchId
    
    // Stage 3: Process content
    val processBatchId = ariaClient.createBatch(
        name = "Content Processing",
        maxConcurrent = 2,
        priority = "NORMAL"
    )
    
    val processingSteps = listOf(
        "ocr_processing",
        "transcription",
        "nlp_analysis",
        "concept_extraction",
        "index_building"
    )
    
    processingSteps.forEach { step ->
        ariaClient.addTorrentToBatch(processBatchId, "process://$step")
    }
    
    workflowBatches["process"] = processBatchId
    
    // Start workflow in order
    ariaClient.startBatch(downloadBatchId)
    
    return mapOf(
        "workflowBatches" to workflowBatches,
        "archives" to archives,
        "documentTypes" to documentTypes,
        "processingSteps" to processingSteps
    )
}

suspend fun monitorPatrickDevineWorkflow(workflowResult: Map<String, Any>): Map<String, Any> {
    val rpcClient = TrikeRpcClient("http://localhost:6800/jsonrpc")
    val ariaClient = TrikeAriaClient(rpcClient)
    
    val workflowBatches = workflowResult["workflowBatches"] as Map<String, String>
    val monitoringData = mutableMapOf<String, Any>()
    
    // Monitor each batch
    workflowBatches.forEach { (stage, batchId) ->
        val status = ariaClient.getBatchStatus(batchId)
        monitoringData[stage] = status
    }
    
    // Get overall workflow status
    val allBatches = ariaClient.getAllBatchStatuses()
    
    return mapOf(
        "workflowResult" to workflowResult,
        "monitoringData" to monitoringData,
        "allBatches" to allBatches
    )
}

suspend fun main() {
    val workflowResult = createPatrickDevineWorkflow()
    val monitoringResult = monitorPatrickDevineWorkflow(workflowResult)
    
    // Return results for external processing
    mapOf(
        "workflow" to workflowResult,
        "monitoring" to monitoringResult,
        "timestamp" to System.currentTimeMillis()
    )
} 