#!/usr/bin/env k2script

/**
 * Example demonstrating how the DefaultScriptEngine IS the DSL
 * and creates its own vines (dependencies)
 */

import k2script.engine.DefaultScriptEngine
import k2script.api.DependencyResolver

fun main() {
    println("=== K2Script DSL Engine Example ===")
    println("The implementation IS the DSL and creates its own vines")
    println()
    
    // Create an engine using the DSL-style builder
    val engine = DefaultScriptEngine.build {
        // The engine IS the DSL - it knows what it needs and creates it
        // No external configuration needed - the implementation creates its own vines
    }
    
    // Show what components the engine created for itself
    println("Engine Components (DSL Vines):")
    val components = engine.listComponents()
    components.forEach { component ->
        println("  ✓ $component")
    }
    
    // Show what dependency resolvers the engine created
    println("\nDependency Resolvers:")
    val resolvers = engine.getResolvers()
    resolvers.forEach { resolver ->
        println("  ✓ ${resolver.name}")
    }
    
    // Demonstrate DSL-style component access
    println("\nDSL-Style Component Access:")
    val mavenResolver = engine.getComponent<DependencyResolver>("mavenResolver")
    if (mavenResolver != null) {
        println("  ✓ Found Maven resolver: ${mavenResolver.name}")
        
        // Test the resolver
        val resolved = mavenResolver.resolve("org.jetbrains.kotlin:kotlin-stdlib:1.9.0")
        println("  ✓ Resolved dependency: $resolved")
    }
    
    println("\n=== Key Principles Demonstrated ===")
    println("1. The implementation IS the DSL")
    println("2. The engine creates its own vines (dependencies)")
    println("3. No external configuration needed")
    println("4. Self-contained and self-registering")
    println("5. DSL-style access to internal components")
    
    println("\n✓ Example completed successfully!")
} 