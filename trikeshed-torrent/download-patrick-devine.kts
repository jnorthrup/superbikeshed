#!/usr/bin/env kotlin

@file:DependsOn("kotlinx-coroutines-core:1.7.3")
@file:DependsOn("kotlinx-serialization-json:1.6.0")

import borg.trikeshed.torrent.batch.*
import borg.trikeshed.torrent.client.TrikeAriaClient
import borg.trikeshed.torrent.client.TrikeRpcClient
import borg.trikeshed.torrent.rpc.TorrentRpcServer
import kotlinx.coroutines.*

suspend fun main() {
    val rpcServer = TorrentRpcServer()
    val rpcClient = TrikeRpcClient("http://localhost:6800/jsonrpc")
    val ariaClient = TrikeAriaClient(rpcClient)
    
    val patrickDevineUrls = listOf(
        "https://archive.org/download/patrickdevine/patrickdevine.zip",
        "https://archive.org/download/patrickdevinecalls/Patrick%20Devine%20Calls.zip"
    )
    
    val batchId = ariaClient.createBatch(
        name = "Patrick Devine Archives",
        maxConcurrent = 2,
        priority = "CRITICAL"
    )
    
    patrickDevineUrls.forEach { url ->
        ariaClient.addTorrentToBatch(batchId, url)
    }
    
    ariaClient.startBatch(batchId)
    
    val status = ariaClient.monitorBatch(batchId, intervalMs = 1000, maxDuration = 1800000)
    
    mapOf(
        "batchId" to batchId,
        "urls" to patrickDevineUrls,
        "status" to status
    )
} 