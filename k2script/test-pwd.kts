#!/usr/bin/env k2script

@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

import java.io.File

fun main() {
    println("=== K2Script Working Directory Test ===")
    println("Current working directory: ${System.getProperty("user.dir")}")
    println("Current directory contents:")
    
    val currentDir = File(".")
    currentDir.listFiles()?.forEach { file ->
        println("  ${if (file.isDirectory) "📁" else "📄"} ${file.name}")
    }
    
    println("=== Test Complete ===")
} 