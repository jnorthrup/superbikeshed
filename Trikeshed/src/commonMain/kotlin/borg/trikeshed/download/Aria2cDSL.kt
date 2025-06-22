package borg.trikeshed.download

import kotlinx.coroutines.runBlocking
import borg.trikeshed.lib.Series
import borg.trikeshed.lib.j
import kotlin.system.exitProcess

object Aria2cDSL {
    data class DownloadRequest(
        val uris: MutableList<String> = mutableListOf(),
        val outputDir: String = ".",
        val outputFile: String? = null,
        val connections: Int = 16,
        val split: Int = 16,
        val maxSpeed: String? = null,
        val userAgent: String = "trikeshed-aria2c/1.0",
        val referer: String? = null,
        val headers: MutableMap<String, String> = mutableMapOf(),
        val torrent: String? = null,
        val metalink: String? = null,
        val checkIntegrity: Boolean = false,
        val `continue`: Boolean = true,
        val daemon: Boolean = false,
        val rpcPort: Int = 6800,
        val rpcSecret: String? = null,
        val couchdb: String? = null,
        val trikeshedCoord: String? = null
    )

    infix fun String.download(block: DownloadRequest.() -> Unit): Int {
        val request = DownloadRequest().apply {
            uris.add(this@download)
            block()
        }
        return executeAria2c(request)
    }

    infix fun Series<String>.downloadAll(block: DownloadRequest.() -> Unit): Int {
        val request = DownloadRequest().apply {
            this@downloadAll.`▶`.forEach { uris.add(it) }
            block()
        }
        return executeAria2c(request)
    }

    infix fun DownloadRequest.to(dir: String): DownloadRequest = copy(outputDir = dir)
    infix fun DownloadRequest.`as`(filename: String): DownloadRequest = copy(outputFile = filename)
    infix fun DownloadRequest.connections(n: Int): DownloadRequest = copy(connections = n)
    infix fun DownloadRequest.split(n: Int): DownloadRequest = copy(split = n)
    infix fun DownloadRequest.maxSpeed(speed: String): DownloadRequest = copy(maxSpeed = speed)
    infix fun DownloadRequest.torrent(file: String): DownloadRequest = copy(torrent = file)
    infix fun DownloadRequest.couchdb(coord: String): DownloadRequest = copy(couchdb = coord)
    infix fun DownloadRequest.trikeshed(coord: String): DownloadRequest = copy(trikeshedCoord = coord)

    fun executeAria2c(request: DownloadRequest): Int = runBlocking {
        // TODO: Implement download logic with QUIC/IPFS/HTTP
        // If couchdb is set, upload to CouchDB
        // If trikeshedCoord is set, route to TrikeShed storage/attention system
        // Otherwise, save to file/dir
        println("[Aria2cDSL] Download request: $request")
        if (request.couchdb != null) {
            println("[Aria2cDSL] Uploading to CouchDB: ${'$'}{request.couchdb}")
            // TODO: Integrate with CouchDB client
        } else if (request.trikeshedCoord != null) {
            println("[Aria2cDSL] Routing to TrikeShed coordinate: ${'$'}{request.trikeshedCoord}")
            // TODO: Integrate with TrikeShed storage/attention system
        } else {
            println("[Aria2cDSL] Saving to file/dir: ${'$'}{request.outputDir}/${'$'}{request.outputFile}")
            // TODO: Implement file/dir save
        }
        0 // Success
    }
}

// CLI handler for aria2c-compatible arguments
fun handleAria2c(args: List<String>): Int {
    val uris = mutableListOf<String>()
    var outputDir = "."
    var daemon = false
    var rpcPort = 6800
    var couchdb: String? = null
    var trikeshedCoord: String? = null
    var i = 0
    while (i < args.size) {
        when (val arg = args[i]) {
            "-d", "--dir" -> { i++; outputDir = args.getOrNull(i) ?: outputDir }
            "--enable-rpc" -> daemon = true
            "--rpc-listen-port" -> { i++; rpcPort = args.getOrNull(i)?.toIntOrNull() ?: rpcPort }
            "--couchdb" -> { i++; couchdb = args.getOrNull(i) }
            "--trikeshed-coord" -> { i++; trikeshedCoord = args.getOrNull(i) }
            else -> if (!arg.startsWith("-")) uris.add(arg)
        }
        i++
    }
    return with(Aria2cDSL) {
        Series.of(uris) downloadAll {
            this.outputDir = outputDir
            this.daemon = daemon
            this.rpcPort = rpcPort
            this.couchdb = couchdb
            this.trikeshedCoord = trikeshedCoord
        }
    }
}