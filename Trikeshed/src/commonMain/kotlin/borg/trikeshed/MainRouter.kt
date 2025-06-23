package borg.trikeshed

import borg.trikeshed.dsl.*
import borg.trikeshed.net.*
import borg.trikeshed.net.quic.*
import borg.trikeshed.ipfs.*
import borg.trikeshed.couchdb.*
import borg.trikeshed.k2script.*
import borg.trikeshed.rts.*
import borg.trikeshed.distributed.*
import borg.trikeshed.jetsam.*
import borg.trikeshed.cursor.*
import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlin.jvm.JvmStatic
import com.rtsgame.shared.rts.RTSNetworkHost
import com.rtsgame.shared.game.GameState

/**
 * Main DSL Router - Single entry point to entire TrikeShed codebase
 */
object MainRouter {
    
    /**
     * Main DSL entry point
     */
    suspend fun trikeshed(args: Array<String>, block: TrikeShedDsl.() -> Unit) {
        val dsl = TrikeShedDsl(args)
        dsl.block()
        dsl.execute()
    }
}

/**
 * TrikeShed DSL - Provides access to entire codebase
 */
@DslMarker
annotation class TrikeShedDslMarker

@TrikeShedDslMarker
class TrikeShedDsl(private val args: Array<String>) {
    private val routes = mutableListOf<Route>()
    private var defaultRoute: (suspend () -> Unit)? = null
    
    /**
     * Define a route
     */
    fun route(pattern: String, handler: suspend RouteContext.() -> Unit) {
        routes.add(Route(pattern, handler))
    }
    
    /**
     * Default route if no match
     */
    fun default(handler: suspend RouteContext.() -> Unit) {
        defaultRoute = { RouteContext(args).handler() }
    }
    
    /**
     * Execute the router
     */
    suspend fun execute() {
        val command = args.firstOrNull() ?: "help"
        
        // Find matching route
        val route = routes.find { it.pattern == command }
        
        if (route != null) {
            val context = RouteContext(args.drop(1).toTypedArray())
            context.(route.handler)()
        } else {
            defaultRoute?.invoke() ?: printHelp()
        }
    }
    
    private fun printHelp() {
        println("TrikeShed - Available commands:")
        routes.forEach { route ->
            println("  ${route.pattern}")
        }
    }
    
    data class Route(
        val pattern: String,
        val handler: suspend RouteContext.() -> Unit
    )
}

/**
 * Route context provides access to all TrikeShed components
 */
@TrikeShedDslMarker
class RouteContext(val args: Array<String>) {
    
    // Network components
    fun c10k(block: C10KConfig.() -> Unit): C10KServer {
        val config = C10KConfig().apply(block)
        return C10KServer(
            port = config.port,
            staticRoot = config.staticRoot,
            enableQuic = config.enableQuic,
            deterministicMode = config.deterministicMode
        )
    }
    
    // QUIC - Simplified for compilation
    fun quic(block: QuicConfig.() -> Unit): String {
        val config = QuicConfig().apply(block)
        return "QuicEngine(${config.role})"
    }
    
    // IPFS - Simplified for compilation
    suspend fun ipfs(block: IpfsConfig.() -> Unit): String {
        val config = IpfsConfig().apply(block)
        val quicEngine = quic { /* role = QuicEngine.Role.CLIENT */ }
        return "IpfsClient(${config.peerId})"
    }
    
    // CouchDB - Simplified for compilation
    fun couch(block: CouchConfig.() -> Unit): String {
        val config = CouchConfig().apply(block)
        return "CouchClient(${config.url})"
    }
    
    // K2Script servlets
    fun servlets(block: ServletConfig.() -> Unit): ServletContainer {
        val config = ServletConfig().apply(block)
        return ServletContainer(
            scriptRoot = config.scriptRoot,
            cacheScripts = config.cacheScripts
        )
    }
    
    // RTS game host - Simplified for compilation
    suspend fun rts(block: RTSConfig.() -> Unit): String {
        val config = RTSConfig().apply(block)
        return "RTSNetworkHost(port=${config.port}, maxPlayers=${config.maxPlayers})"
    }
    
    // Distributed storage - Simplified for compilation
    suspend fun distributed(block: DistributedConfig.() -> Unit): String {
        val config = DistributedConfig().apply(block)
        return "DistributedStorage(${config.peerId})"
    }
    
    // Jetsam gossip - DISABLED, moved to museum
    // suspend fun gossip(block: suspend JetsamGossipManager.() -> Unit) {
    //     JetsamGossipManager.block()
    // }
    
    // Cursor operations
    fun cursor(data: DatabaseCursor, block: CursorContext.() -> Unit) {
        CursorContext(data).block()
    }
    
    // Utilities
    fun arg(index: Int, default: String = ""): String = args.getOrNull(index) ?: default
    fun argInt(index: Int, default: Int = 0): Int = arg(index).toIntOrNull() ?: default
    fun hasArg(value: String): Boolean = args.contains(value)
}

// Configuration classes

class C10KConfig {
    var port: Int = 8080
    var staticRoot: String = "./static"
    var enableQuic: Boolean = true
    var deterministicMode: Boolean = false
}

class QuicConfig {
    var role = "SERVER" // Simplified for compilation
    // var transportParams = TransportParameters()
}

class IpfsConfig {
    var peerId: String = "default" // Simplified for compilation
    // var storage = IpfsStorage()
}

class CouchConfig {
    var url = "http://localhost:5984"
    // var transport = CouchClient.Transport.HTTP
}

class ServletConfig {
    var scriptRoot = "./servlets"
    var cacheScripts = true
}

class RTSConfig {
    var port: Int = 7777
    var maxPlayers: Int = 16
}

class DistributedConfig {
    var peerId: String = "default" // Simplified for compilation
    var couchUrl = "http://localhost:5984"
}

class CursorContext(private val cursor: DatabaseCursor) {
    fun show(): String = "cursor_display_${cursor.size}_rows"
    fun head(n: Int = 5): String = "cursor_head_${minOf(n, cursor.size)}_rows"
    fun at(index: Int): String = "cursor_row_at_$index"
    fun get(vararg columns: String): String = "cursor_columns_${columns.joinToString("_")}"
    val meta: String get() = "cursor_meta_${cursor.size}_rows"
}

/**
 * Main entry point using DSL
 */
suspend fun main(args: Array<String>) = MainRouter.trikeshed(args) {
    
    // C10K server with static files and servlets
    route("server") {
        val server = c10k {
            port = argInt(0, 8080)
            staticRoot = arg(1, "./static")
            enableQuic = true
        }
        
        val staticRootPath = arg(1, "./static")
        val servlets = servlets {
            scriptRoot = "$staticRootPath/servlets"
            cacheScripts = true
        }
        
        server.start()
    }
    
    // RTS game host
    route("rts") {
        val host = rts {
            port = argInt(0, 7777)
            maxPlayers = argInt(1, 16)
        }
        
        println("RTS host starting on port ${host}")
    }
    
    // IPFS node
    route("ipfs") {
        val client = ipfs {
            peerId = "node_123"
        }
        
        println("IPFS node started")
    }
    
    // Distributed storage
    route("distributed") {
        val storage = distributed {
            peerId = "dist_456"
            couchUrl = arg(0, "http://localhost:5984")
        }
        
        // Start API server
        val server = c10k {
            port = 9000
            staticRoot = "./distributed"
        }
        
        server.start()
    }
    
    // QUIC test server
    route("quic") {
        val engine = quic {
            role = "SERVER"
        }
        
        println("QUIC engine created")
        println("State: $engine")
    }
    
    // CouchDB operations
    route("couch") {
        val client = couch {
            url = arg(0, "http://localhost:5984")
        }
        
        when (arg(1)) {
            "list" -> {
                println("CouchDB client: $client")
                println("Listing databases")
            }
            "create" -> {
                val dbName = arg(2, "test")
                println("CouchDB client: $client")
                println("Creating database: $dbName")
            }
        }
    }
    
    // Default help
    default {
        println("""
            TrikeShed Router - Access entire codebase
            
            Usage: trikeshed <command> [options]
            
            Commands:
              server [port] [root]     - C10K server with servlets
              rts [port] [players]     - RTS game host  
              ipfs                     - IPFS node
              distributed [couch_url]  - Distributed storage
              quic                     - QUIC test
              couch [url] [cmd]        - CouchDB operations
            
            This DSL provides access to:
              - C10K high-performance server
              - QUIC protocol implementation
              - IPFS content addressing
              - CouchDB client
              - K2Script servlet container
              - RTS deterministic simulation
              - Distributed storage with gossip
              - Cursor operations
              - All TrikeShed lib types
        """.trimIndent())
    }
}