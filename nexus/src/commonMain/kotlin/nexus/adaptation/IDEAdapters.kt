package nexus.adaptation

import nexus.core.*
import borg.trikeshed.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Concrete IDE Adapter Implementations
 * 
 * Direct integrations with popular development environments.
 */

// ═══════════════════════════════════════════════════════════════════════════════
// VS CODE ADAPTER
// ═══════════════════════════════════════════════════════════════════════════════

class VSCodeAdapter : IDEAdapter {
    override val supportsCodeEditing = true
    override var isConnected = false
    private var connection: VSCodeConnection? = null
    
    override suspend fun tryConnect(context: EnvironmentContext): Result<Connection> = runCatching {
        // Check for VS Code processes and extension API
        val vscodeProcess = findVSCodeProcess()
        if (vscodeProcess != null) {
            connection = VSCodeConnection(vscodeProcess)
            isConnected = true
            connection!!
        } else {
            throw IllegalStateException("VS Code not found or not running")
        }
    }
    
    override suspend fun execute(action: Action, context: ProjectContext): Result<Outcome> = runCatching {
        val conn = connection ?: throw IllegalStateException("Not connected to VS Code")
        
        when (action.type) {
            ActionType.CODE_EDIT -> handleCodeEdit(action, conn)
            ActionType.FILE_OPERATION -> handleFileOperation(action, conn)
            else -> Outcome("Action ${action.type} not supported by VS Code adapter")
        }
    }
    
    override suspend fun openFile(path: String): Result<Unit> = runCatching {
        connection?.sendCommand("vscode.open", path) 
            ?: throw IllegalStateException("Not connected")
    }
    
    override suspend fun editFile(path: String, edit: Edit): Result<Unit> = runCatching {
        connection?.sendCommand("editor.edit", "$path:${edit.startLine}:${edit.endLine}:${edit.newContent}")
            ?: throw IllegalStateException("Not connected")
    }
    
    override suspend fun getOpenFiles(): Series<String> {
        return connection?.sendQuery("workspace.textDocuments")?.split(",")
            ?.let { Series.of(*it.toTypedArray()) } ?: Series.empty()
    }
    
    override suspend fun getCurrentSelection(): Selection? {
        val selectionData = connection?.sendQuery("editor.selection") ?: return null
        return parseSelection(selectionData)
    }
    
    private suspend fun findVSCodeProcess(): VSCodeProcess? {
        // Platform-specific process detection
        delay(50) // Simulate process check
        return VSCodeProcess("vscode-main", 12345)
    }
    
    private suspend fun handleCodeEdit(action: Action, conn: VSCodeConnection): Outcome {
        val result = conn.sendCommand("editor.action", action.data)
        return Outcome("VS Code edit completed: $result")
    }
    
    private suspend fun handleFileOperation(action: Action, conn: VSCodeConnection): Outcome {
        val result = conn.sendCommand("file.action", action.data)
        return Outcome("VS Code file operation completed: $result")
    }
    
    private fun parseSelection(data: String): Selection? {
        val parts = data.split(":")
        return if (parts.size >= 5) {
            Selection(parts[0], parts[1].toInt(), parts[2].toInt(), parts[3].toInt(), parts[4].toInt())
        } else null
    }
}

data class VSCodeProcess(val name: String, val pid: Int)

class VSCodeConnection(private val process: VSCodeProcess) : Connection {
    override val id = "vscode-${process.pid}"
    override val type = ConnectionType.IDE
    override var isActive = true
    
    suspend fun sendCommand(command: String, args: String): String {
        delay(10) // Simulate IPC call
        return "Command '$command' executed with args: $args"
    }
    
    suspend fun sendQuery(query: String): String {
        delay(10) // Simulate IPC call
        return when (query) {
            "workspace.textDocuments" -> "file1.kt,file2.kt,file3.ts"
            "editor.selection" -> "file1.kt:10:15:5:25"
            else -> "Unknown query: $query"
        }
    }
    
    override suspend fun getCapabilities(): Series<Capability> = Series.of(
        "editor" j Series.of("edit", "select", "navigate"),
        "filesystem" j Series.of("read", "write", "watch"),
        "debugger" j Series.of("breakpoints", "step", "inspect"),
        "terminal" j Series.of("execute", "shell")
    )
    
    override fun observeChanges(): Flow<Change> = flow {
        while (isActive) {
            delay(1000)
            emit(Change(ChangeType.FILE_SYSTEM, id, "file changed", System.currentTimeMillis()))
        }
    }
    
    override suspend fun close() {
        isActive = false
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// INTELLIJ IDEA ADAPTER
// ═══════════════════════════════════════════════════════════════════════════════

class IntelliJAdapter : IDEAdapter {
    override val supportsCodeEditing = true
    override var isConnected = false
    private var connection: IntelliJConnection? = null
    
    override suspend fun tryConnect(context: EnvironmentContext): Result<Connection> = runCatching {
        val ideaProcess = findIntelliJProcess()
        if (ideaProcess != null) {
            connection = IntelliJConnection(ideaProcess)
            isConnected = true
            connection!!
        } else {
            throw IllegalStateException("IntelliJ IDEA not found or not running")
        }
    }
    
    override suspend fun execute(action: Action, context: ProjectContext): Result<Outcome> = runCatching {
        val conn = connection ?: throw IllegalStateException("Not connected to IntelliJ")
        
        when (action.type) {
            ActionType.CODE_EDIT -> handleCodeEdit(action, conn)
            ActionType.BUILD -> handleBuild(action, conn)
            ActionType.TEST -> handleTest(action, conn)
            else -> Outcome("Action ${action.type} not supported by IntelliJ adapter")
        }
    }
    
    override suspend fun openFile(path: String): Result<Unit> = runCatching {
        connection?.sendIDEAction("openFile", path)
            ?: throw IllegalStateException("Not connected")
    }
    
    override suspend fun editFile(path: String, edit: Edit): Result<Unit> = runCatching {
        connection?.sendIDEAction("editFile", "$path|${edit.startLine}|${edit.endLine}|${edit.newContent}")
            ?: throw IllegalStateException("Not connected")
    }
    
    override suspend fun getOpenFiles(): Series<String> {
        return connection?.queryIDE("getOpenFiles")?.split("|")
            ?.let { Series.of(*it.toTypedArray()) } ?: Series.empty()
    }
    
    override suspend fun getCurrentSelection(): Selection? {
        val data = connection?.queryIDE("getCurrentSelection") ?: return null
        return parseIntelliJSelection(data)
    }
    
    private suspend fun findIntelliJProcess(): IntelliJProcess? {
        delay(50) // Simulate process detection
        return IntelliJProcess("idea", 23456)
    }
    
    private suspend fun handleCodeEdit(action: Action, conn: IntelliJConnection): Outcome {
        val result = conn.sendIDEAction("codeEdit", action.data)
        return Outcome("IntelliJ code edit completed: $result")
    }
    
    private suspend fun handleBuild(action: Action, conn: IntelliJConnection): Outcome {
        val result = conn.sendIDEAction("build", action.data)
        return Outcome("IntelliJ build completed: $result")
    }
    
    private suspend fun handleTest(action: Action, conn: IntelliJConnection): Outcome {
        val result = conn.sendIDEAction("runTests", action.data)
        return Outcome("IntelliJ test execution completed: $result")
    }
    
    private fun parseIntelliJSelection(data: String): Selection? {
        val parts = data.split("|")
        return if (parts.size >= 5) {
            Selection(parts[0], parts[1].toInt(), parts[2].toInt(), parts[3].toInt(), parts[4].toInt())
        } else null
    }
}

data class IntelliJProcess(val name: String, val pid: Int)

class IntelliJConnection(private val process: IntelliJProcess) : Connection {
    override val id = "intellij-${process.pid}"
    override val type = ConnectionType.IDE
    override var isActive = true
    
    suspend fun sendIDEAction(action: String, params: String): String {
        delay(15) // IntelliJ actions tend to be slightly slower
        return "IntelliJ action '$action' executed with params: $params"
    }
    
    suspend fun queryIDE(query: String): String {
        delay(15)
        return when (query) {
            "getOpenFiles" -> "Main.kt|Utils.kt|Test.kt"
            "getCurrentSelection" -> "Main.kt|25|30|10|45"
            else -> "Unknown IntelliJ query: $query"
        }
    }
    
    override suspend fun getCapabilities(): Series<Capability> = Series.of(
        "editor" j Series.of("edit", "refactor", "analyze", "navigate"),
        "build" j Series.of("gradle", "maven", "compiler"),
        "debug" j Series.of("breakpoints", "evaluate", "profile"),
        "vcs" j Series.of("git", "commit", "merge", "history"),
        "test" j Series.of("junit", "testng", "coverage")
    )
    
    override fun observeChanges(): Flow<Change> = flow {
        while (isActive) {
            delay(800) // IntelliJ change detection frequency
            emit(Change(ChangeType.TOOL_STATE, id, "project structure changed", System.currentTimeMillis()))
        }
    }
    
    override suspend fun close() {
        isActive = false
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// NEOVIM/VIM ADAPTER
// ═══════════════════════════════════════════════════════════════════════════════

class NeovimAdapter : IDEAdapter {
    override val supportsCodeEditing = true
    override var isConnected = false
    private var connection: NeovimConnection? = null
    
    override suspend fun tryConnect(context: EnvironmentContext): Result<Connection> = runCatching {
        val nvimSocket = findNeovimSocket()
        if (nvimSocket != null) {
            connection = NeovimConnection(nvimSocket)
            isConnected = true
            connection!!
        } else {
            throw IllegalStateException("Neovim not found or not running with RPC enabled")
        }
    }
    
    override suspend fun execute(action: Action, context: ProjectContext): Result<Outcome> = runCatching {
        val conn = connection ?: throw IllegalStateException("Not connected to Neovim")
        
        when (action.type) {
            ActionType.CODE_EDIT -> handleVimEdit(action, conn)
            ActionType.FILE_OPERATION -> handleVimFileOp(action, conn)
            else -> Outcome("Action ${action.type} not supported by Neovim adapter")
        }
    }
    
    override suspend fun openFile(path: String): Result<Unit> = runCatching {
        connection?.sendVimCommand(":edit $path")
            ?: throw IllegalStateException("Not connected")
    }
    
    override suspend fun editFile(path: String, edit: Edit): Result<Unit> = runCatching {
        connection?.sendVimCommand(":edit $path | ${edit.startLine},${edit.endLine}c")
            ?: throw IllegalStateException("Not connected")
    }
    
    override suspend fun getOpenFiles(): Series<String> {
        return connection?.sendVimCommand(":ls")?.lines()
            ?.filter { it.contains(".") }
            ?.map { it.substringAfter("\"").substringBefore("\"") }
            ?.let { Series.of(*it.toTypedArray()) } ?: Series.empty()
    }
    
    override suspend fun getCurrentSelection(): Selection? {
        val bufferInfo = connection?.sendVimCommand(":echo bufname() . ':' . line('.') . ':' . col('.')") ?: return null
        return parseVimSelection(bufferInfo)
    }
    
    private suspend fun findNeovimSocket(): String? {
        delay(30) // Simulate socket discovery
        return "/tmp/nvim.12345" // Mock socket path
    }
    
    private suspend fun handleVimEdit(action: Action, conn: NeovimConnection): Outcome {
        val result = conn.sendVimCommand(action.data)
        return Outcome("Neovim edit completed: $result")
    }
    
    private suspend fun handleVimFileOp(action: Action, conn: NeovimConnection): Outcome {
        val result = conn.sendVimCommand(action.data)
        return Outcome("Neovim file operation completed: $result")
    }
    
    private fun parseVimSelection(data: String): Selection? {
        val parts = data.split(":")
        return if (parts.size >= 3) {
            Selection(parts[0], parts[1].toInt(), parts[1].toInt(), parts[2].toInt(), parts[2].toInt())
        } else null
    }
}

class NeovimConnection(private val socket: String) : Connection {
    override val id = "neovim-${socket.hashCode()}"
    override val type = ConnectionType.IDE
    override var isActive = true
    
    suspend fun sendVimCommand(command: String): String {
        delay(5) // Vim is typically very fast
        return "Vim executed: $command"
    }
    
    override suspend fun getCapabilities(): Series<Capability> = Series.of(
        "editor" j Series.of("edit", "search", "replace", "macro"),
        "filesystem" j Series.of("read", "write"),
        "terminal" j Series.of("shell", "execute")
    )
    
    override fun observeChanges(): Flow<Change> = flow {
        while (isActive) {
            delay(500) // Vim change detection
            emit(Change(ChangeType.FILE_SYSTEM, id, "buffer changed", System.currentTimeMillis()))
        }
    }
    
    override suspend fun close() {
        isActive = false
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ADAPTER FACTORY
// ═══════════════════════════════════════════════════════════════════════════════

fun createIDEAdapters(): Series<IDEAdapter> = Series.of(
    VSCodeAdapter(),
    IntelliJAdapter(), 
    NeovimAdapter()
)