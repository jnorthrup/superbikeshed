#!/usr/bin/env k2script

@file:Import("org.jetbrains.kotlin:kotlin-stdlib:1.9.0")
@file:Import("org.jetbrains.kotlin:kotlin-reflect:1.9.0")

import kotlin.reflect.full.*

/**
 * Example k2script with @file:Import annotations
 * 
 * This script demonstrates how to use Maven dependencies in k2script
 * without needing a build file or complex dependency management.
 */

data class User(
    val name: String,
    val age: Int,
    val email: String
)

fun main() {
    println("🚀 k2script with @file:Import example")
    println("=====================================")
    
    // Use reflection to inspect the User class
    val userClass = User::class
    val properties = userClass.memberProperties
    
    println("User class properties:")
    properties.forEach { prop ->
        println("  - ${prop.name}: ${prop.returnType}")
    }
    
    // Create and use a User instance
    val user = User("John Doe", 30, "john@example.com")
    println("\nCreated user: $user")
    
    // Demonstrate reflection usage
    val nameProperty = properties.find { it.name == "name" }
    if (nameProperty != null) {
        val name = nameProperty.get(user)
        println("User's name (via reflection): $name")
    }
    
    println("\n✅ Script executed successfully!")
}

// === Additional utility functions ===

fun <T : Any> T.toMap(): Map<String, Any?> {
    return this::class.memberProperties.associate { prop ->
        prop.name to prop.get(this)
    }
}

fun printObjectDetails(obj: Any) {
    val objClass = obj::class
    println("Object: ${objClass.simpleName}")
    println("Properties:")
    
    objClass.memberProperties.forEach { prop ->
        val value = try {
            prop.get(obj)
        } catch (e: Exception) {
            "<error: ${e.message}>"
        }
        println("  ${prop.name}: $value")
    }
} 