#!/usr/bin/env kscript

@file:ProjectCoordinates(group="com.example", artifact="simple-app", version="1.0.0")

println("Hello from a simple kscript!")
println("Arguments: ${args.joinToString(", ")}")

val data = listOf("apple", "banana", "cherry")
data.forEach { println("Fruit: $it") }