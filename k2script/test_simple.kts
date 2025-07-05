#!/usr/bin/env k2script

// Simple test script to check basic k2script functionality
println("=== k2script Basic Test ===")

// Test basic Kotlin functionality
val numbers = listOf(1, 2, 3, 4, 5)
println("Numbers: $numbers")
println("Sum: ${numbers.sum()}")
println("Average: ${numbers.average()}")

// Test string operations
val message = "Hello from k2script!"
println("Message: $message")
println("Length: ${message.length}")
println("Uppercase: ${message.uppercase()}")

// Test data class
data class Person(val name: String, val age: Int)
val person = Person("Alice", 30)
println("Person: $person")

// Test collections
val map = mapOf("a" to 1, "b" to 2, "c" to 3)
println("Map: $map")
println("Keys: ${map.keys}")
println("Values: ${map.values}")

println("=== Test completed successfully! ===") 