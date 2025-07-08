#!/usr/bin/env k2script

// Simple test script for K2Script
println("Hello from K2Script!")
println("Arguments: ${args.joinToString(", ")}")

// Test TrikeShed patterns
val series = 3 j { i -> "item-$i" }
println("TrikeShed series:")
for (i in 0 until series.size) {
    println("  ${i}: ${series[i]}")
}

// Test Join pattern
val joined = "hello" j "world"
println("Joined: ${joined.a} + ${joined.b}")

// Test script arguments
if (args.isNotEmpty()) {
    println("First argument: ${args[0]}")
    println("All arguments: ${args.contentToString()}")
}

println("K2Script test completed successfully!")