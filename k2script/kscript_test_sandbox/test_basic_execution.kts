#!/usr/bin/env kscript

println("Hello from basic kscript execution!")
println("Arguments passed: ${args.joinToString(", ")}")

// Test basic Kotlin functionality
val numbers = listOf(1, 2, 3, 4, 5)
val sum = numbers.sum()
println("Sum of numbers 1-5: $sum")

// Test some standard library features
val currentTime = System.currentTimeMillis()
println("Current timestamp: $currentTime")