@file:Suppress("NOTHING_TO_INLINE")
package borg.trikeshed

import borg.trikeshed.net.http.*
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.system.exitProcess
import borg.trikeshed.lib.*
import borg.trikeshed.reactor.Reactor
import borg.trikeshed.reactor.http.createRequestFactoryHandler
import borg.trikeshed.services.*

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
            val reactor = Reactor()
            val config = HttpServerConfig(
                port = HttpServerPort(port),
                host = HttpServerHost("0.0.0.0")
            )

            val dealService = createDealService()
            val requestFactoryService = RequestFactoryService.create()

            val router = createRouter(rootDir, dealService, requestFactoryService)

            val server = HttpServer(config, reactor, router)
            server.start()
            println("HTTP/$version server started successfully.")
            println("- Static files served from: $rootDir")
            println("- Batch API endpoint: http://localhost:$port/api/batch")
            
            kotlinx.coroutines.delay(Long.MAX_VALUE) 
        } catch (e: Exception) {
            println("HTTP server failed: ${e.message}")
            e.printStackTrace()
        }
    }
}

private fun createDealService(): DealService = object : DealService {
    private val deals = mutableMapOf<String, DealProxy>()
    private val vendors = listOf(
        VendorProxy("v1" j "Acme Corp"),
        VendorProxy("v2" j "Widget Co")
    )

    override suspend fun findDeal(id: String): DealProxy? = deals[id]

    override suspend fun findDealsByProduct(query: String): Series<DealProxy> {
        val matching = deals.values.filter { 
            it.product.contains(query, ignoreCase = true) 
        }
        return matching.toSeries()
    }

    override suspend fun persistDeal(deal: DealProxy): CouchTxProxy {
        val id = deal.id.ifEmpty { System.currentTimeMillis().toString() }
        deals[id] = deal
        return CouchTxProxy(id j mapOf(
            "ok" to "true",
            "rev" to "1-${System.currentTimeMillis()}"
        ))
    }

    override suspend fun getVendors(): Series<VendorProxy> =
        vendors.toSeries()
}

fun showUsage() {
    println("USAGE: trikeshed httpd [--port=8080] [--root=.]")
}

fun showVersion() {
    println("TrikeShed 1.0-SNAPSHOT")
}
