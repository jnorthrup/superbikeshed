package com.v2superbikeshed.nexus.service

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.ProjectManager
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.websocket.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.v2superbikeshed.nexus.api.intellijApiRoutes
import com.v2superbikeshed.nexus.rpc.*
import java.time.Duration

@Service(Service.Level.APP)
class NexusApiServer : Disposable {
    companion object {
        internal val LOG = Logger.getInstance(NexusApiServer::class.java)
        
        @JvmStatic
        fun getInstance(): NexusApiServer {
            return ApplicationManager.getApplication().getService(NexusApiServer::class.java)
        }
    }
    
    internal var server: ApplicationEngine? = null
    internal var port: Int = 63343 // Different from built-in server
    internal lateinit var mcpBridge: TrikeshedMcpBridge
    internal lateinit var unifiedService: UnifiedService
    
    fun start(port: Int = this.port) {
        if (server != null) {
            LOG.info("Nexus API Server already running on port $port")
            return
        }
        
        this.port = port
        
        server = embeddedServer(Netty, port = port) {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
            
            install(WebSockets) {
                pingPeriod = Duration.ofSeconds(15)
                timeout = Duration.ofSeconds(15)
                maxFrameSize = Long.MAX_VALUE
                masking = false
            }
            
            routing {
                // Initialize bridge and unified service for each project
                val projects = ProjectManager.getInstance().openProjects
                if (projects.isNotEmpty()) {
                    val project = projects.first()
                    mcpBridge = project.getService(TrikeshedMcpBridge::class.java)
                    val intelliJAccess = IntelliJAccessService.getInstance(project)
                    unifiedService = UnifiedService(mcpBridge, intelliJAccess)
                    unifiedService.initialize()
                }
                
                // Include all route types
                intellijApiRoutes()
                mcpBridgeRoutes(mcpBridge)
                
                // Health check
                get("/api/health") {
                    call.respond(HealthResponse("ok", "Nexus API Server"))
                }
                
                // IDE info
                get("/api/ide/info") {
                    val app = ApplicationManager.getApplication()
                    call.respond(IdeInfo(
                        name = app.name,
                        version = app.versionName,
                        build = app.buildNumber
                    ))
                }
                
                // Project endpoints
                route("/api/project/{projectName}") {
                    // Refactoring
                    post("/refactor/rename") {
                        val projectName = call.parameters["projectName"] ?: ""
                        val request = call.receive<RenameRequest>()
                        
                        val service = ProjectApiService.getInstance(projectName)
                        val result = service?.rename(request) ?: RefactorResult(
                            success = false,
                            error = "Project not found"
                        )
                        
                        call.respond(result)
                    }
                    
                    // Code inspection
                    get("/inspect") {
                        val projectName = call.parameters["projectName"] ?: ""
                        val service = ProjectApiService.getInstance(projectName)
                        
                        val inspections = service?.getInspections() ?: emptyList()
                        call.respond(InspectionsResponse(inspections))
                    }
                    
                    // Find usages
                    get("/usages/{symbol}") {
                        val projectName = call.parameters["projectName"] ?: ""
                        val symbol = call.parameters["symbol"] ?: ""
                        
                        val service = ProjectApiService.getInstance(projectName)
                        val usages = service?.findUsages(symbol) ?: emptyList()
                        
                        call.respond(UsagesResponse(symbol, usages))
                    }
                }
                
                // Actions endpoint
                post("/api/action/{actionId}") {
                    val actionId = call.parameters["actionId"] ?: ""
                    val result = ActionExecutor.execute(actionId)
                    call.respond(ActionResult(actionId, result))
                }
            }
        }
        
        server?.start(wait = false)
        LOG.info("Nexus API Server started on port $port")
    }
    
    fun stop() {
        server?.stop(1000, 2000)
        server = null
        LOG.info("Nexus API Server stopped")
    }
    
    fun isRunning(): Boolean = server != null
    
    fun getPort(): Int = port
    
    override fun dispose() {
        stop()
    }
}

// Data classes
@Serializable
data class HealthResponse(val status: String, val service: String)

@Serializable
data class IdeInfo(val name: String, val version: String, val build: String)

@Serializable
data class RenameRequest(
    val oldName: String,
    val newName: String,
    val scope: String = "project" // project, module, file
)

@Serializable
data class RefactorResult(
    val success: Boolean,
    val filesChanged: Int = 0,
    val error: String? = null
)

@Serializable
data class InspectionInfo(
    val id: String,
    val severity: String,
    val message: String,
    val file: String,
    val line: Int
)

@Serializable
data class InspectionsResponse(val inspections: List<InspectionInfo>)

@Serializable
data class UsageInfo(val file: String, val line: Int, val text: String)

@Serializable
data class UsagesResponse(val symbol: String, val usages: List<UsageInfo>)

@Serializable
data class ActionResult(val actionId: String, val success: Boolean)