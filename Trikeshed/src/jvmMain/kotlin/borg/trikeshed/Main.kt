@file:Suppress("NOTHING_TO_INLINE")
package borg.trikeshed

import borg.trikeshed.net.http.*
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.system.exitProcess
import borg.trikeshed.lib.*
import borg.trikeshed.reactor.Reactor
import borg.trikeshed.services.*
import borg.trikeshed.reactor.quic.quicd

fun main(args: Array<String>) {
    val executableName = getExecutableName(args)
    val command = parseCommand(executableName, args)
    
    try {
        routeCommand(command, args.drop(1).toTypedArray())
    } catch (e: NotImplementedError) {
        System.err.println("ERROR: ${e.message}")
        exitProcess(1)
    } catch (e: Exception) {
        System.err.println("FATAL: ${e.message}")
        e.printStackTrace()
        exitProcess(2)
    }
}

fun getExecutableName(args: Array<String>): String {
    return System.getProperty("sun.java.command")?.split(" ")?.first()
        ?: args.getOrNull(0) 
        ?: "trikeshed"
}

fun parseCommand(executableName: String, args: Array<String>): String {
    val basename = File(executableName).name
    
    return when {
        basename.startsWith("ts-") -> basename.substring(3)
        basename == "trikeshed" -> args.getOrNull(0) ?: "help"
        else -> basename
    }
}

fun routeCommand(command: String, args: Array<String>) {
    when (command) {
        "httpd" -> handleHttpdCommands(args)
        "quicd" -> handleQuicdCommands(args)
        "server" -> ProductionMain.main(arrayOf("server") + args)
        "rts" -> ProductionMain.main(arrayOf("rts") + args)
        "ipfs" -> ProductionMain.main(arrayOf("ipfs") + args)
        "distributed" -> ProductionMain.main(arrayOf("distributed") + args)
        "help" -> showUsage()
        "version" -> showVersion()
        else -> TODO("Unknown command: $command - use 'trikeshed help' for usage")
    }
}

fun handleHttpdCommands(args: Array<String>) {
    val port = args.find { it.startsWith("--port=") }?.substringAfter("=")?.toIntOrNull() ?: 8080
    val rootDir = args.find { it.startsWith("--root=") }?.substringAfter("=") ?: "."
    startHttpServer("1.1", arrayOf(port.toString(), rootDir))
}

fun startHttpServer(version: String, args: Array<String>) {
    val port = args.getOrNull(0)?.toIntOrNull() ?: 8080
    val rootDir = args.getOrNull(1) ?: "."
    println("Starting HTTP/$version server on port $port...")
    
    runBlocking {
        try {
            // Simplified server startup
            println("HTTP/$version server would start on port $port")
            println("- Static files would be served from: $rootDir")
            println("- Batch API endpoint would be: http://localhost:$port/api/batch")
            
            kotlinx.coroutines.delay(Long.MAX_VALUE) 
        } catch (e: Exception) {
            println("HTTP server failed: ${e.message}")
            e.printStackTrace()
        }
    }
}

fun handleQuicdCommands(args: Array<String>) {
    val port = args.find { it.startsWith("--port=") }?.substringAfter("=")?.toIntOrNull() ?: 4433
    
    runBlocking {
        println("QUIC server would start on port $port")
        // Simplified QUIC server
        kotlinx.coroutines.delay(Long.MAX_VALUE)
    }
}

private fun createDealService(): DealService = object : DealService {
    override fun process(data: ByteArray): ByteArray {
        // Simplified implementation
        return "Deal processed".encodeToByteArray()
    }
}

fun showUsage() {
    println("USAGE: trikeshed <command> [options]")
    println("")
    println("Legacy commands:")
    println("  httpd [--port=8080] [--root=.]      - HTTP/1.1 server")
    println("  quicd [--port=4433]                  - QUIC daemon")
    println("")
    println("Production commands:")
    println("  server [port] [static_root]          - C10K server with K2Script servlets")
    println("  rts [port] [max_players]             - RTS network host for games")
    println("  ipfs                                 - IPFS node with content addressing")
    println("  distributed [mode]                   - Distributed storage (QUIC+IPFS+CouchDB)")
    println("")
    println("Other commands:")
    println("  help                                 - Show this help")
    println("  version                              - Show version")
}

fun showVersion() {
    println("TrikeShed 1.0-SNAPSHOT")
}
