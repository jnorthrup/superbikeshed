@file:JvmName("NvidiaTaskerMain")

package nexus.agent

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import kotlin.system.exitProcess

/**
 * Main entry point for NvidiaTasker executable
 */
fun main(args: Array<String>) = runBlocking {
    if (args.isEmpty()) {
        printUsage()
        exitProcess(1)
    }
    
    val tasker = NvidiaTasker()
    
    try {
        when (args[0].lowercase()) {
            "chat", "ask" -> handleChat(tasker, args)
            "edit" -> handleEdit(tasker, args)
            "analyze", "analyse" -> handleAnalyze(tasker, args)
            "read" -> handleRead(tasker, args)
            "write" -> handleWrite(tasker, args)
            "list", "ls" -> handleList(tasker, args)
            "interactive", "i" -> handleInteractive(tasker)
            "help", "-h", "--help" -> printUsage()
            else -> {
                // Treat entire input as a chat prompt
                val prompt = args.joinToString(" ")
                val result = tasker.execute(NvidiaTasker.chat(prompt))
                printResult(result)
            }
        }
    } catch (e: Exception) {
        System.err.println("Error: ${e.message}")
        exitProcess(1)
    }
}

private suspend fun handleChat(tasker: NvidiaTasker, args: Array<String>) {
    if (args.size < 2) {
        println("Error: chat requires a prompt")
        exitProcess(1)
    }
    
    val prompt = args.drop(1).joinToString(" ")
    val systemPrompt = System.getenv("NVIDIA_SYSTEM_PROMPT")
    
    val result = tasker.execute(NvidiaTasker.chat(prompt, systemPrompt))
    printResult(result)
}

private suspend fun handleEdit(tasker: NvidiaTasker, args: Array<String>) {
    if (args.size < 2) {
        println("Error: edit requires instruction type (insert, replace, delete)")
        println("Example: nvidia-tasker edit insert file.kt 10 'new content'")
        exitProcess(1)
    }
    
    val instruction = when (args[1].lowercase()) {
        "insert" -> {
            if (args.size < 5) {
                println("Error: insert requires: file afterLine content")
                exitProcess(1)
            }
            NvidiaTasker.EditInstruction.Insert(
                filePath = args[2],
                afterLine = args[3].toInt(),
                content = args.drop(4).joinToString(" ")
            )
        }
        "replace" -> {
            if (args.size < 6) {
                println("Error: replace requires: file startLine endLine content")
                exitProcess(1)
            }
            NvidiaTasker.EditInstruction.Replace(
                filePath = args[2],
                startLine = args[3].toInt(),
                endLine = args[4].toInt(),
                content = args.drop(5).joinToString(" ")
            )
        }
        "delete" -> {
            if (args.size < 5) {
                println("Error: delete requires: file startLine endLine")
                exitProcess(1)
            }
            NvidiaTasker.EditInstruction.Delete(
                filePath = args[2],
                startLine = args[3].toInt(),
                endLine = args[4].toInt()
            )
        }
        else -> {
            println("Error: unknown edit instruction: ${args[1]}")
            exitProcess(1)
        }
    }
    
    val result = tasker.execute(NvidiaTasker.edit(instruction))
    printResult(result)
}

private suspend fun handleAnalyze(tasker: NvidiaTasker, args: Array<String>) {
    if (args.size < 2) {
        println("Error: analyze requires a file path")
        exitProcess(1)
    }
    
    val result = tasker.execute(NvidiaTasker.analyze(args[1]))
    printResult(result)
}

private suspend fun handleRead(tasker: NvidiaTasker, args: Array<String>) {
    if (args.size < 2) {
        println("Error: read requires a file path")
        exitProcess(1)
    }
    
    val tool = NvidiaTasker.Tool.ReadFile()
    val jsonArgs = buildJsonObject {
        put("path", args[1])
    }
    
    val result = tasker.execute(NvidiaTasker.Task.ToolExecution(tool, jsonArgs))
    printResult(result)
}

private suspend fun handleWrite(tasker: NvidiaTasker, args: Array<String>) {
    if (args.size < 3) {
        println("Error: write requires: file content")
        exitProcess(1)
    }
    
    val tool = NvidiaTasker.Tool.WriteFile()
    val jsonArgs = buildJsonObject {
        put("path", args[1])
        put("content", args.drop(2).joinToString(" "))
    }
    
    val result = tasker.execute(NvidiaTasker.Task.ToolExecution(tool, jsonArgs))
    printResult(result)
}

private suspend fun handleList(tasker: NvidiaTasker, args: Array<String>) {
    val path = if (args.size > 1) args[1] else "."
    
    val tool = NvidiaTasker.Tool.ListFiles()
    val jsonArgs = buildJsonObject {
        put("path", path)
    }
    
    val result = tasker.execute(NvidiaTasker.Task.ToolExecution(tool, jsonArgs))
    printResult(result)
}

private suspend fun handleInteractive(tasker: NvidiaTasker) {
    println("=== NVIDIA Tasker Interactive Mode ===")
    println("Commands: chat, edit, analyze, read, write, list, exit")
    println("Type 'help' for usage")
    println()
    
    while (true) {
        print("> ")
        val input = readLine()?.trim() ?: continue
        
        if (input.isEmpty()) continue
        if (input == "exit" || input == "quit") break
        
        val parts = input.split(" ")
        val newArgs = parts.toTypedArray()
        
        try {
            when (parts[0].lowercase()) {
                "chat" -> handleChat(tasker, newArgs)
                "edit" -> handleEdit(tasker, newArgs)
                "analyze" -> handleAnalyze(tasker, newArgs)
                "read" -> handleRead(tasker, newArgs)
                "write" -> handleWrite(tasker, newArgs)
                "list" -> handleList(tasker, newArgs)
                "help" -> printInteractiveHelp()
                else -> {
                    // Default to chat
                    val result = tasker.execute(NvidiaTasker.chat(input))
                    printResult(result)
                }
            }
        } catch (e: Exception) {
            println("Error: ${e.message}")
        }
        
        println()
    }
    
    println("Goodbye!")
}

private fun printResult(result: NvidiaTasker.TaskerState) {
    when (result) {
        is NvidiaTasker.TaskerState.Success -> println(result.result)
        is NvidiaTasker.TaskerState.Error -> System.err.println("Error: ${result.message}")
        else -> println("Unexpected state: $result")
    }
}

private fun printUsage() {
    println("""
        NVIDIA Tasker - AI-powered task automation
        
        Usage: nvidia-tasker [command] [options]
        
        Commands:
          chat <prompt>                    Ask the AI a question
          edit insert <file> <line> <text> Insert text after line
          edit replace <file> <start> <end> <text> Replace lines
          edit delete <file> <start> <end> Delete lines
          analyze <file>                   Analyze file dependencies
          read <file>                      Read file contents
          write <file> <content>           Write to file
          list [directory]                 List files
          interactive                      Enter interactive mode
          help                            Show this help
        
        Environment Variables:
          NVIDIA_API_KEY                   Primary API key
          NVIDIA_API_KEY_2                 Secondary API key
          NVIDIA_API_KEY_3                 Tertiary API key
          NVIDIA_SYSTEM_PROMPT             Default system prompt
        
        Examples:
          nvidia-tasker "What is the weather?"
          nvidia-tasker chat "Explain this code"
          nvidia-tasker analyze src/main.kt
          nvidia-tasker edit insert test.kt 10 "// TODO: implement"
          nvidia-tasker interactive
    """.trimIndent())
}

private fun printInteractiveHelp() {
    println("""
        Interactive Mode Commands:
        
        chat <prompt>         Ask a question
        edit <type> ...      Edit files (insert/replace/delete)
        analyze <file>       Analyze file dependencies
        read <file>          Read file contents
        write <file> <text>  Write to file
        list [dir]           List directory contents
        help                 Show this help
        exit                 Exit interactive mode
        
        Or just type a question to chat directly!
    """.trimIndent())
}