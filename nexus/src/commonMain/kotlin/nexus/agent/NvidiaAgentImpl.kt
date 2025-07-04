package nexus.agent

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import kotlin.math.min
import nexus.toIdx

/**
 * Implementation details for NvidiaAgent
 * Separated to keep main agent class focused on API
 */

// Extension functions for NvidiaAgent
internal suspend fun NvidiaAgent.callApi(
    apiKey: String,
    requestBody: ChatCompletionRequest
): HttpResponse {
    // This is a placeholder - in real implementation would use HttpClient
    // For now, returning mock response to allow compilation
    return HttpResponse(200, """{"choices":[{"message":{"role":"assistant","content":"Mock response"}}]}""")
}

internal fun NvidiaAgent.buildToolsJson(): JsonArray {
    return buildJsonArray {
        for (i in 0 until availableTools.a) {
            val tool = availableTools.b(i)
            addJsonObject {
                put("type", "function")
                putJsonObject("function") {
                    put("name", tool.name)
                    put("description", tool.description)
                    putJsonObject("parameters") {
                        put("type", "object")
                        putJsonObject("properties") {
                            when (tool) {
                                is NvidiaAgent.Tool.GetCurrentTime -> {}
                                is NvidiaAgent.Tool.ReadFile, 
                                is NvidiaAgent.Tool.ListFiles -> {
                                    putJsonObject("path") {
                                        put("type", "string")
                                        put("description", "The file/directory path")
                                    }
                                }
                                is NvidiaAgent.Tool.WriteFile -> {
                                    putJsonObject("path") {
                                        put("type", "string")
                                        put("description", "The file path to write to")
                                    }
                                    putJsonObject("content") {
                                        put("type", "string")
                                        put("description", "The content to write")
                                    }
                                }
                            }
                        }
                        putJsonArray("required") {
                            when (tool) {
                                is NvidiaAgent.Tool.GetCurrentTime -> {}
                                is NvidiaAgent.Tool.ReadFile, 
                                is NvidiaAgent.Tool.ListFiles -> add("path")
                                is NvidiaAgent.Tool.WriteFile -> {
                                    add("path")
                                    add("content")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun NvidiaAgent.parseEditInstruction(text: String): NvidiaAgent.EditInstruction? {
    val lines = text.trim().lines()
    if (lines.isEmpty()) return null
    
    return when {
        lines[0].startsWith("INSERT:") || text.contains("AFTER-LINE:") -> {
            val fileRegex = """(?:INSERT:|EDIT-FILE:)\s*(.+)""".toRegex()
            val lineRegex = """AFTER-LINE:\s*(\d+)""".toRegex()
            val codeRegex = """<<CODE\n([\s\S]+?)\nCODE""".toRegex()
            
            val filePath = fileRegex.find(text)?.groupValues?.get(1)?.trim() ?: return null
            val afterLine = lineRegex.find(text)?.groupValues?.get(1)?.toInt() ?: return null
            val content = codeRegex.find(text)?.groupValues?.get(1) ?: return null
            
            NvidiaAgent.EditInstruction.Insert(filePath, afterLine, content)
        }
        lines[0].startsWith("REPLACE:") -> {
            val fileRegex = """REPLACE:\s*(.+)""".toRegex()
            val rangeRegex = """LINES:\s*(\d+)-(\d+)""".toRegex()
            val codeRegex = """<<CODE\n([\s\S]+?)\nCODE""".toRegex()
            
            val filePath = fileRegex.find(lines[0])?.groupValues?.get(1)?.trim() ?: return null
            val range = lines.find { it.startsWith("LINES:") }?.let {
                rangeRegex.find(it)?.let { match ->
                    match.groupValues[1].toInt() to match.groupValues[2].toInt()
                }
            } ?: return null
            val content = codeRegex.find(text)?.groupValues?.get(1) ?: return null
            
            NvidiaAgent.EditInstruction.Replace(filePath, range.first, range.second, content)
        }
        lines[0].startsWith("DELETE:") -> {
            val fileRegex = """DELETE:\s*(.+)""".toRegex()
            val rangeRegex = """LINES:\s*(\d+)-(\d+)""".toRegex()
            
            val filePath = fileRegex.find(lines[0])?.groupValues?.get(1)?.trim() ?: return null
            val range = lines.find { it.startsWith("LINES:") }?.let {
                rangeRegex.find(it)?.let { match ->
                    match.groupValues[1].toInt() to match.groupValues[2].toInt()
                }
            } ?: return null
            
            NvidiaAgent.EditInstruction.Delete(filePath, range.first, range.second)
        }
        else -> null
    }
}

internal fun NvidiaAgent.applyEdit(instruction: NvidiaAgent.EditInstruction): String {
    val file = java.io.File(
        when (instruction) {
            is NvidiaAgent.EditInstruction.Insert -> instruction.filePath
            is NvidiaAgent.EditInstruction.Replace -> instruction.filePath
            is NvidiaAgent.EditInstruction.Delete -> instruction.filePath
        }
    )
    
    if (!file.exists()) {
        throw Exception("File not found: ${file.path}")
    }
    
    val lines = file.readLines()
    
    val newLines = when (instruction) {
        is NvidiaAgent.EditInstruction.Insert -> {
            if (instruction.afterLine < 0 || instruction.afterLine > lines.size) {
                throw Exception("Invalid line number: ${instruction.afterLine}. File has ${lines.size} lines.")
            }
            
            buildList {
                addAll(lines.take(instruction.afterLine))
                addAll(instruction.content.lines())
                addAll(lines.drop(instruction.afterLine))
            }
        }
        
        is NvidiaAgent.EditInstruction.Replace -> {
            if (instruction.startLine < 1 || instruction.endLine > lines.size || 
                instruction.startLine > instruction.endLine) {
                throw Exception("Invalid line range: ${instruction.startLine}-${instruction.endLine}. File has ${lines.size} lines.")
            }
            
            buildList {
                addAll(lines.take(instruction.startLine - 1))
                addAll(instruction.content.lines())
                addAll(lines.drop(instruction.endLine))
            }
        }
        
        is NvidiaAgent.EditInstruction.Delete -> {
            if (instruction.startLine < 1 || instruction.endLine > lines.size || 
                instruction.startLine > instruction.endLine) {
                throw Exception("Invalid line range: ${instruction.startLine}-${instruction.endLine}. File has ${lines.size} lines.")
            }
            
            buildList {
                addAll(lines.take(instruction.startLine - 1))
                addAll(lines.drop(instruction.endLine))
            }
        }
    }
    
    file.writeText(newLines.joinToString("\n"))
    
    return when (instruction) {
        is NvidiaAgent.EditInstruction.Insert -> 
            "Inserted ${instruction.content.lines().size} lines after line ${instruction.afterLine}"
        is NvidiaAgent.EditInstruction.Replace -> 
            "Replaced lines ${instruction.startLine}-${instruction.endLine} with ${instruction.content.lines().size} new lines"
        is NvidiaAgent.EditInstruction.Delete -> 
            "Deleted lines ${instruction.startLine}-${instruction.endLine}"
    }
}

internal fun NvidiaAgent.analyzeFileInteractions(filePath: String): NvidiaAgent.FileInteraction {
    val targetFile = java.io.File(filePath)
    if (!targetFile.exists()) {
        return NvidiaAgent.FileInteraction(
            filePath, 
            emptyArray<String>().toIdx(),
            0,
            NvidiaAgent.EditAdvice.UNSAFE
        )
    }
    
    // Simplified analysis - in real implementation would analyze imports/dependencies
    val projectRoot = findProjectRoot(targetFile)
    val interactingFiles = mutableListOf<String>()
    
    // For now, just return a simple analysis
    val interactionCount = interactingFiles.size
    val advice = when (interactionCount) {
        0 -> NvidiaAgent.EditAdvice.TODO
        1 -> NvidiaAgent.EditAdvice.INLINE
        in 2..5 -> NvidiaAgent.EditAdvice.PROPAGATE
        else -> NvidiaAgent.EditAdvice.UNSAFE
    }
    
    return NvidiaAgent.FileInteraction(
        targetFile = filePath,
        interactingFiles = interactingFiles.toTypedArray().toIdx(),
        interactionCount = interactionCount,
        advice = advice
    )
}

private fun findProjectRoot(file: java.io.File): java.io.File {
    var current = file.parentFile
    while (current != null) {
        if (java.io.File(current, ".git").exists() || 
            java.io.File(current, "build.gradle").exists() ||
            java.io.File(current, "build.gradle.kts").exists()) {
            return current
        }
        current = current.parentFile
    }
    return file.parentFile ?: java.io.File(".")
}

// Extension property to access available tools
internal val NvidiaAgent.availableTools: Indexed<NvidiaAgent.Tool>
    get() = arrayOf(
        NvidiaAgent.Tool.GetCurrentTime,
        NvidiaAgent.Tool.ReadFile(),
        NvidiaAgent.Tool.WriteFile(), 
        NvidiaAgent.Tool.ListFiles()
    ).toIdx()