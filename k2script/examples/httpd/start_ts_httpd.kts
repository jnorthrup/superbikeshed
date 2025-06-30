#!/usr/bin/env k2script

/**
 * Start TrikeShed's native HTTP server (ts-httpd)
 * This uses the relaxfactory/1xio implementation in TrikeShed's Main.kt
 * No external dependencies - pure TrikeShed architecture
 */

@file:CompilerOpts("-cp", "/Users/jim/work/superbikeshed/Trikeshed/build/libs/*")

// Import TrikeShed's main function
import borg.trikeshed.main

fun main(args: Array<String>) {
    println("🚀 Starting TrikeShed native HTTP server (ts-httpd)")
    println("📡 Architecture: 1xio → relaxfactory → TrikeShed CCEK")
    println("🌐 Pure TrikeShed implementation - no external dependencies")
    println()
    
    val port = args.getOrNull(0) ?: "8080"
    val httpArgs = arrayOf("httpd", "--port", port)
    
    println("⚡ Executing: ts-httpd --port $port")
    println("🎮 BoingDemo will be available at: http://localhost:$port")
    println()
    
    // Call TrikeShed's main function with httpd arguments
    borg.trikeshed.main(httpArgs)
}

main(args)