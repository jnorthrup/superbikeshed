package k2script.cli

import k2script.wagon.*
import kotlinx.coroutines.runBlocking

/**
 * CLI command for generating classpaths from Maven coordinates
 * Usage: k2script classpath <coordinate1> <coordinate2> ...
 * Usage: k2script classpath --file <imports.txt>
 */

object ClasspathCommand {
    
    fun execute(args: Array<String>) {
        if (args.isEmpty()) {
            printUsage()
            return
        }
        
        when {
            args[0] == "--file" || args[0] == "-f" -> {
                if (args.size < 2) {
                    println("Error: File path required")
                    printUsage()
                    return
                }
                generateClasspathFromFile(args[1])
            }
            args[0] == "--help" || args[0] == "-h" -> {
                printUsage()
            }
            else -> {
                generateClasspathFromCoordinates(args)
            }
        }
    }
    
    private fun generateClasspathFromCoordinates(coordinates: Array<String>) {
        runBlocking {
            try {
                val classpath = ClasspathGenerator.generateClasspath(coordinates.toList())
                println(classpath)
            } catch (e: Exception) {
                println("Error generating classpath: ${e.message}")
                System.exit(1)
            }
        }
    }
    
    private fun generateClasspathFromFile(filePath: String) {
        runBlocking {
            try {
                val classpath = ClasspathGenerator.generateClasspathFromFile(filePath)
                println(classpath)
            } catch (e: Exception) {
                println("Error reading file $filePath: ${e.message}")
                System.exit(1)
            }
        }
    }
    
    private fun printUsage() {
        println("""
            k2script classpath - Generate classpath from Maven coordinates
            
            Usage:
              k2script classpath <coordinate1> <coordinate2> ...
              k2script classpath --file <imports.txt>
              k2script classpath --help
            
            Examples:
              k2script classpath org.jetbrains.kotlin:kotlin-stdlib:1.9.0
              k2script classpath org.jetbrains.kotlin:kotlin-stdlib:1.9.0 org.jetbrains.kotlin:kotlin-reflect:1.9.0
              k2script classpath --file dependencies.txt
            
            File format (dependencies.txt):
              # One coordinate per line
              org.jetbrains.kotlin:kotlin-stdlib:1.9.0
              org.jetbrains.kotlin:kotlin-reflect:1.9.0
              com.fasterxml.jackson.core:jackson-databind:2.15.0
        """.trimIndent())
    }
}

// === Main entry point for classpath command ===

fun main(args: Array<String>) {
    ClasspathCommand.execute(args)
} 