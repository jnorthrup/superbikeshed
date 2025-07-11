@file:JvmName("SuperPercolator")
package borg.trikeshed.launcher

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import kotlinx.datetime.Clock
import fiduciary.clean.FiduciaryPercolator
import fiduciary.clean.FiduciaryData
import fiduciary.fetch.ZipRangeFetcher
import borg.trikeshed.net.http.HttpClient
import borg.trikeshed.dht.kademlia.id.NUID
import fiduciary.concentric.*

/**
 * SUPER PERCOLATOR - The One True Launcher
 * 
 * No mocks. No bullshit. Just percolation.
 */
fun main() = runBlocking {
    println("""
    ╔═══════════════════════════════════════════════════════╗
    ║          🔥 SUPER PERCOLATOR 🔥                       ║
    ║                                                       ║
    ║     REAL DATA → REAL PERCOLATION → REAL RESULTS      ║
    ╚═══════════════════════════════════════════════════════╝
    """.trimIndent())
    
    // 1. START THE FUCKING PERCOLATOR
    val percolator = FiduciaryPercolator
    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob() + percolator)
    percolator.startPercolation(scope)
    println("✅ Percolator: RUNNING")
    
    // 2. MONITOR THE FLOW
    launch {
        percolator.getPercolationFlow().collect { data ->
            val emoji = when (data.stage) {
                "raw" -> "📥"
                "normalized" -> "🔄"
                "enriched" -> "⚡"
                "classified" -> "🏷️"
                "stored" -> "💾"
                else -> "📡"
            }
            println("$emoji ${data.stage.uppercase()}: ${data.id} [${data.source}]")
        }
    }
    
    // 3. LAUNCH PLATFORM
    val launcher = PlatformLauncher()
    launcher.initialize()
    println("✅ Platform: INITIALIZED")
    
    // 4. START COUCHDB SERVER
    val couchServer = UringCouchDBServer(
        port = 5984,
        quicPort = 5985,
        ipfsPort = 5986,
        launcher = launcher
    )
    couchServer.initialize()
    couchServer.start()
    println("✅ CouchDB: RUNNING on :5984")
    println("✅ io_uring: ${if (couchServer.isUringActive()) "ACTIVE" else "FALLBACK"}")
    
    // 5. CREATE DATABASES
    listOf("fiduciary", "percolator", "archives").forEach { db ->
        couchServer.handleRestRequest("PUT", "/$db", null)
    }
    println("✅ Databases: CREATED")
    
    // 6. INITIALIZE AGENTS
    val agents = couchServer.initializeAgentNetwork()
    println("✅ Agents: ${agents.values.sumOf { it.size }} ACTIVE")
    
    // 7. FETCH REAL ARCHIVES
    println("\n🎯 FETCHING PATRICK DEVINE ARCHIVES")
    val httpClient = HttpClient()
    val fetcher = ZipRangeFetcher(httpClient)
    
    launch {
        try {
            val centralDirs = fetcher.fetchZipCentralDirs()
            
            centralDirs.forEach { dir ->
                println("\n📦 ${dir.archiveName}")
                println("   Total Size: ${dir.totalSize / 1024 / 1024}MB")
                println("   Fetched: ${dir.totalBytes / 1024}KB")
                println("   Efficiency: ${(dir.totalBytes * 100.0 / dir.totalSize).format(2)}%")
                println("   Entries: ${dir.entries.size}")
                
                // PERCOLATE ARCHIVE DATA
                percolator.ingest(FiduciaryData(
                    id = "archive_${dir.archiveName.hashCode()}",
                    source = "zip_range_fetcher",
                    content = mapOf(
                        "url" to dir.archiveUrl,
                        "name" to dir.archiveName,
                        "total_size" to dir.totalSize,
                        "entries" to dir.entries.size,
                        "bytes_fetched" to dir.totalBytes
                    )
                ))
                
                // PERCOLATE MP3 FILES
                val mp3s = dir.entries.filter { it.name.endsWith(".mp3") }
                println("   MP3 Files: ${mp3s.size}")
                
                mp3s.take(10).forEach { mp3 ->
                    percolator.ingest(FiduciaryData(
                        id = "mp3_${mp3.name.hashCode()}",
                        source = "patrick_devine_mp3",
                        content = mapOf(
                            "filename" to mp3.name,
                            "size" to mp3.uncompressedSize,
                            "offset" to mp3.offset,
                            "archive" to dir.archiveUrl
                        )
                    ))
                }
                
                // STORE IN COUCHDB
                val doc = buildJsonObject {
                    put("_id", "archive_${dir.archiveName}")
                    put("type", "zip_archive")
                    put("url", dir.archiveUrl)
                    put("size", dir.totalSize)
                    put("entries", dir.entries.size)
                    put("mp3_count", mp3s.size)
                    put("fetched_at", Clock.System.now().toString())
                }
                
                couchServer.handleRestRequest(
                    "PUT",
                    "/archives/${dir.archiveName}",
                    doc.toString()
                )
            }
            
        } catch (e: Exception) {
            println("\n❌ ERROR: ${e.message}")
            e.printStackTrace()
        }
    }
    
    // 8. STATUS MONITOR
    launch {
        while (isActive) {
            delay(30000)
            val storage = percolator.getStorage()
            println("\n📊 STATUS: ${storage.size} documents percolated")
        }
    }
    
    // 9. KEEP RUNNING
    println("""
    
    ╔═══════════════════════════════════════════════════════╗
    ║   🟢 SUPER PERCOLATOR IS RUNNING                      ║
    ║                                                       ║
    ║   CouchDB: http://localhost:5984                      ║
    ║   QUIC: quic://localhost:5985                         ║
    ║   IPFS: http://localhost:5986                         ║
    ║                                                       ║
    ║   Press Ctrl+C to shutdown                            ║
    ╚═══════════════════════════════════════════════════════╝
    """.trimIndent())
    
    awaitCancellation()
}

private fun Double.format(digits: Int) = "%.${digits}f".format(this)