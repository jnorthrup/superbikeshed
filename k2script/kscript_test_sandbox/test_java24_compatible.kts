#!/usr/bin/env kscript

@file:CompilerOptions("-Xuse-fir-lt=false")
@file:KotlinOptions("--enable-native-access=ALL-UNNAMED")

println("=== Testing kscript with Java 24 and Kotlin 2.1.20 ===")
println("Hello from kscript!")
println("Arguments received: ${args.joinToString(", ")}")

// Test basic Kotlin functionality
val numbers = listOf(1, 2, 3, 4, 5)
val sum = numbers.sum()
println("Sum of 1-5: $sum")

// Test lambda expressions
val doubled = numbers.map { it * 2 }
println("Doubled numbers: $doubled")

// Test string templates and stdlib
val javaVersion = System.getProperty("java.version")
val kotlinVersion = KotlinVersion.CURRENT
println("Running on Java: $javaVersion")
println("Kotlin version: $kotlinVersion")

// Test some more advanced features
data class Person(val name: String, val age: Int)
val people = listOf(
    Person("Alice", 25),
    Person("Bob", 30),
    Person("Charlie", 35)
)

val adults = people.filter { it.age >= 18 }
println("Adults: ${adults.map { it.name }}")

println("=== kscript test completed successfully! ===")