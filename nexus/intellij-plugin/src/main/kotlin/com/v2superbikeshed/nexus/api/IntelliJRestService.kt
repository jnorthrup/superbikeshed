package com.v2superbikeshed.nexus.api

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.v2superbikeshed.nexus.service.IntelliJAccessService
import com.v2superbikeshed.nexus.service.ProjectApiService
import com.v2superbikeshed.nexus.service.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import io.netty.handler.codec.http.QueryStringDecoder
import org.jetbrains.ide.RestService
import java.time.Duration

/**
 * REST Service that exposes IntelliJ functionality via HTTP/WebSocket APIs.
 * Extends IntelliJ's built-in RestService for integration with the IDE's web server.
 */
class IntelliJRestService : RestService() {
    
    override fun getServiceName(): String = "nexus"
    
    override fun execute(urlPath: String, request: QueryStringDecoder, project: Project?, method: String): String? {
        // This method is called by IntelliJ's built-in web server
        // We'll delegate to Ktor for more advanced routing
        return null
    }
    
    companion object {
        // WebSocket channels for real-time updates
        internal val astUpdateFlow = MutableSharedFlow<ASTUpdate>()
        internal val compilationErrorFlow = MutableSharedFlow<CompilationError>()
        
        fun emitASTUpdate(update: ASTUpdate) {
            ApplicationManager.getApplication().invokeLater {
                astUpdateFlow.tryEmit(update)
            }
        }
        
        fun emitCompilationError(error: CompilationError) {
            ApplicationManager.getApplication().invokeLater {
                compilationErrorFlow.tryEmit(error)
            }
        }
    }
}

/**
 * Ktor routing configuration for the IntelliJ API
 */
fun Route.intellijApiRoutes() {
    
    route("/api/intellij") {
        
        // Project discovery
        get("/projects") {
            val projects = ProjectManager.getInstance().openProjects.map { project ->
                ProjectInfo(
                    name = project.name,
                    path = project.basePath ?: "",
                    isOpen = !project.isDisposed
                )
            }
            call.respond(projects)
        }
        
        // PSI Access
        route("/psi/{projectName}") {
            
            post("/find-element") {
                val projectName = call.parameters["projectName"] ?: ""
                val request = call.receive<FindElementRequest>()
                val project = findProject(projectName) ?: run {
                    call.respond(HttpStatusCode.NotFound, "Project not found")
                    return@post
                }
                
                val accessService = IntelliJAccessService.getInstance(project)
                val element = accessService.findPsiElement(request.fqName)
                
                if (element != null) {
                    call.respond(ElementInfo(
                        name = element.text,
                        type = element.javaClass.simpleName,
                        file = element.containingFile?.virtualFile?.path ?: "",
                        offset = element.textRange.startOffset
                    ))
                } else {
                    call.respond(HttpStatusCode.NotFound, "Element not found")
                }
            }
            
            get("/file") {
                val projectName = call.parameters["projectName"] ?: ""
                val filePath = call.request.queryParameters["path"] ?: ""
                val project = findProject(projectName) ?: run {
                    call.respond(HttpStatusCode.NotFound, "Project not found")
                    return@get
                }
                
                val accessService = IntelliJAccessService.getInstance(project)
                val psiFile = accessService.getPsiFile(filePath)
                
                if (psiFile != null) {
                    call.respond(FileInfo(
                        path = filePath,
                        language = psiFile.language.displayName,
                        content = psiFile.text
                    ))
                } else {
                    call.respond(HttpStatusCode.NotFound, "File not found")
                }
            }
        }
        
        // Search and Navigation
        route("/search/{projectName}") {
            
            post("/usages") {
                val projectName = call.parameters["projectName"] ?: ""
                val request = call.receive<FindUsagesRequest>()
                val project = findProject(projectName) ?: run {
                    call.respond(HttpStatusCode.NotFound, "Project not found")
                    return@post
                }
                
                val accessService = IntelliJAccessService.getInstance(project)
                val element = accessService.findPsiElement(request.elementFqName)
                
                if (element != null) {
                    val usages = accessService.findUsages(element)
                    call.respond(UsagesResponse(
                        elementName = request.elementFqName,
                        usages = usages
                    ))
                } else {
                    call.respond(HttpStatusCode.NotFound, "Element not found")
                }
            }
            
            get("/symbols") {
                val projectName = call.parameters["projectName"] ?: ""
                val pattern = call.request.queryParameters["pattern"] ?: ""
                val includeLibraries = call.request.queryParameters["includeLibraries"]?.toBoolean() ?: false
                
                val project = findProject(projectName) ?: run {
                    call.respond(HttpStatusCode.NotFound, "Project not found")
                    return@get
                }
                
                val accessService = IntelliJAccessService.getInstance(project)
                val symbols = accessService.findSymbolsByPattern(pattern, includeLibraries)
                call.respond(SymbolsResponse(symbols))
            }
        }
        
        // Refactoring
        route("/refactor/{projectName}") {
            
            post("/rename") {
                val projectName = call.parameters["projectName"] ?: ""
                val request = call.receive<RenameRequest>()
                val project = findProject(projectName) ?: run {
                    call.respond(HttpStatusCode.NotFound, "Project not found")
                    return@post
                }
                
                val accessService = IntelliJAccessService.getInstance(project)
                val element = accessService.findPsiElement(request.elementFqName)
                
                if (element != null) {
                    val result = accessService.renameSymbol(element, request.newName)
                    call.respond(result)
                } else {
                    call.respond(HttpStatusCode.NotFound, "Element not found")
                }
            }
            
            post("/move") {
                val projectName = call.parameters["projectName"] ?: ""
                val request = call.receive<MoveRequest>()
                val project = findProject(projectName) ?: run {
                    call.respond(HttpStatusCode.NotFound, "Project not found")
                    return@post
                }
                
                val accessService = IntelliJAccessService.getInstance(project)
                val element = accessService.findPsiElement(request.elementFqName)
                
                if (element != null) {
                    val result = accessService.moveElement(element, request.targetPackage)
                    call.respond(result)
                } else {
                    call.respond(HttpStatusCode.NotFound, "Element not found")
                }
            }
        }
        
        // Code Analysis
        route("/analysis/{projectName}") {
            
            get("/inspections") {
                val projectName = call.parameters["projectName"] ?: ""
                val filePath = call.request.queryParameters["file"]
                val project = findProject(projectName) ?: run {
                    call.respond(HttpStatusCode.NotFound, "Project not found")
                    return@get
                }
                
                val accessService = IntelliJAccessService.getInstance(project)
                val inspections = accessService.getCodeInspections(filePath)
                call.respond(InspectionsResponse(inspections))
            }
            
            get("/compilation-errors") {
                val projectName = call.parameters["projectName"] ?: ""
                val project = findProject(projectName) ?: run {
                    call.respond(HttpStatusCode.NotFound, "Project not found")
                    return@get
                }
                
                val accessService = IntelliJAccessService.getInstance(project)
                val errors = accessService.getCompilationErrors()
                call.respond(CompilationErrorsResponse(errors))
            }
        }
        
        // Editor Operations
        route("/editor/{projectName}") {
            
            post("/apply-edits") {
                val projectName = call.parameters["projectName"] ?: ""
                val request = call.receive<ApplyEditsRequest>()
                val project = findProject(projectName) ?: run {
                    call.respond(HttpStatusCode.NotFound, "Project not found")
                    return@post
                }
                
                val accessService = IntelliJAccessService.getInstance(project)
                val success = accessService.applyEdits(request.filePath, request.edits)
                call.respond(ApplyEditsResponse(success))
            }
        }
        
        // Actions
        post("/action/{projectName}/{actionId}") {
            val projectName = call.parameters["projectName"] ?: ""
            val actionId = call.parameters["actionId"] ?: ""
            val project = findProject(projectName) ?: run {
                call.respond(HttpStatusCode.NotFound, "Project not found")
                return@post
            }
            
            val accessService = IntelliJAccessService.getInstance(project)
            val success = accessService.executeAction(actionId)
            call.respond(ActionResult(actionId, success))
        }
        
        // WebSocket endpoints
        webSocket("/ws/{projectName}/ast-updates") {
            val projectName = call.parameters["projectName"] ?: ""
            val project = findProject(projectName) ?: run {
                close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Project not found"))
                return@webSocket
            }
            
            // Send initial connection confirmation
            send(Frame.Text(Json.encodeToString(
                WebSocketMessage("connected", "AST update stream connected")
            )))
            
            // Collect AST updates
            launch {
                IntelliJRestService.astUpdateFlow.asSharedFlow().collect { update ->
                    if (update.projectName == projectName) {
                        send(Frame.Text(Json.encodeToString(update)))
                    }
                }
            }
            
            // Keep connection alive
            for (frame in incoming) {
                when (frame) {
                    is Frame.Text -> {
                        val text = frame.readText()
                        if (text == "ping") {
                            send(Frame.Text("pong"))
                        }
                    }
                    else -> {}
                }
            }
        }
        
        webSocket("/ws/{projectName}/compilation-errors") {
            val projectName = call.parameters["projectName"] ?: ""
            val project = findProject(projectName) ?: run {
                close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Project not found"))
                return@webSocket
            }
            
            // Send initial connection confirmation
            send(Frame.Text(Json.encodeToString(
                WebSocketMessage("connected", "Compilation error stream connected")
            )))
            
            // Collect compilation errors
            launch {
                IntelliJRestService.compilationErrorFlow.asSharedFlow().collect { error ->
                    if (error.projectName == projectName) {
                        send(Frame.Text(Json.encodeToString(error)))
                    }
                }
            }
            
            // Keep connection alive
            for (frame in incoming) {
                when (frame) {
                    is Frame.Text -> {
                        val text = frame.readText()
                        if (text == "ping") {
                            send(Frame.Text("pong"))
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}

// Helper function to find project by name
internal fun findProject(name: String): Project? {
    return ProjectManager.getInstance().openProjects.find { it.name == name }
}

// Request/Response Data Classes

@Serializable
data class ProjectInfo(
    val name: String,
    val path: String,
    val isOpen: Boolean
)

@Serializable
data class FindElementRequest(
    val fqName: String
)

@Serializable
data class ElementInfo(
    val name: String,
    val type: String,
    val file: String,
    val offset: Int
)

@Serializable
data class FileInfo(
    val path: String,
    val language: String,
    val content: String
)

@Serializable
data class FindUsagesRequest(
    val elementFqName: String
)

@Serializable
data class UsagesResponse(
    val elementName: String,
    val usages: List<UsageLocation>
)

@Serializable
data class SymbolsResponse(
    val symbols: List<SymbolInfo>
)

@Serializable
data class RenameRequest(
    val elementFqName: String,
    val newName: String
)

@Serializable
data class MoveRequest(
    val elementFqName: String,
    val targetPackage: String
)

@Serializable
data class ApplyEditsRequest(
    val filePath: String,
    val edits: List<TextEdit>
)

@Serializable
data class ApplyEditsResponse(
    val success: Boolean
)

@Serializable
data class CompilationErrorsResponse(
    val errors: List<CompilationError>
)

@Serializable
data class CompilationError(
    val projectName: String,
    val file: String,
    val line: Int,
    val column: Int,
    val message: String,
    val severity: String
)

// WebSocket message types

@Serializable
data class WebSocketMessage(
    val type: String,
    val message: String
)

@Serializable
data class ASTUpdate(
    val projectName: String,
    val file: String,
    val type: String,
    val change: String,
    val timestamp: Long = System.currentTimeMillis()
)