#!/usr/bin/env k2script

// Simple K2Script test
println("🎱 Hello from K2Script - The Cue Ball!")
println("K2Script is running on: ${System.getProperty("java.version")}")

// Test with arguments
if (args.isNotEmpty()) {
    println("Arguments received: ${args.joinToString(", ")}")
} else {
    println("No arguments provided")
}

// Simple demonstration
println("\nK2Script can execute Kotlin scripts standalone!")
println("Ready to strike nexus (the 8 ball) in the causality chain.")