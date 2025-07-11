#!/usr/bin/env kotlin

/**
 * K2Script Configuration Demo
 * 
 * This script demonstrates the polished configuration keys and use cases
 * for k2script installation and execution.
 * 
 * Usage:
 *   k2script examples/configuration-demo.kts
 *   k2script examples/configuration-demo.kts --install-demo
 *   k2script examples/configuration-demo.kts --validate-config
 */

@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.io.File
import java.nio.file.Paths
import kotlin.system.exitProcess

/**
 * Configuration Demo Runner
 */
class ConfigurationDemo {
    
    private val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    /**
     * Main demo function
     */
    suspend fun run(args: Array<String>) {
        when {
            args.isEmpty() -> showConfigurationSummary()
            args[0] == "--install-demo" -> demonstrateInstallation()
            args[0] == "--validate-config" -> validateConfiguration()
            args[0] == "--generate-env" -> generateEnvironmentScript()
            args[0] == "--show-paths" -> showInstallationPaths()
            else -> showHelp()
        }
    }
    
    /**
     * Show current configuration summary
     */
    private fun showConfigurationSummary() {
        println("🔧 K2Script Configuration Summary")
        println("==================================")
        
        // Core configuration
        println("\n📋 Core Configuration:")
        println("  Install Prefix: ${getEnv("K2SCRIPT_INSTALL_PREFIX") ?: "Not set"}")
        println("  Cache Directory: ${getEnv("K2SCRIPT_CACHE_DIR") ?: "~/.k2script/cache"}")
        println("  Config Directory: ${getEnv("K2SCRIPT_CONFIG_DIR") ?: "~/.k2script/config"}")
        println("  Log Level: ${getEnv("K2SCRIPT_LOG_LEVEL") ?: "INFO"}")
        println("  Verbose Mode: ${getEnv("K2SCRIPT_VERBOSE") ?: "false"}")
        println("  Sandbox Mode: ${getEnv("K2SCRIPT_SANDBOX") ?: "true"}")
        
        // AI configuration
        println("\n🤖 AI Configuration:")
        println("  Provider: ${getEnv("K2SCRIPT_AI_PROVIDER") ?: "openai"}")
        println("  Model: ${getEnv("K2SCRIPT_AI_MODEL") ?: "Not set"}")
        println("  API Key: ${if (getEnv("K2SCRIPT_AI_API_KEY") != null) "Set" else "Not set"}")
        println("  Enabled: ${getEnv("K2SCRIPT_AI_ENABLED") ?: "true"}")
        
        // Installation configuration
        println("\n📦 Installation Configuration:")
        println("  Mode: ${getEnv("K2SCRIPT_INSTALL_MODE") ?: "user"}")
        println("  Create Uninstall: ${getEnv("K2SCRIPT_INSTALL_CREATE_UNINSTALL") ?: "true"}")
        println("  Set Executable: ${getEnv("K2SCRIPT_INSTALL_SET_EXECUTABLE") ?: "true"}")
        
        // Security configuration
        println("\n🔒 Security Configuration:")
        println("  Isolation Level: ${getEnv("K2SCRIPT_SECURITY_ISOLATION_LEVEL") ?: "process"}")
        println("  Restrict FS: ${getEnv("K2SCRIPT_SECURITY_RESTRICT_FS") ?: "true"}")
        println("  Restrict Network: ${getEnv("K2SCRIPT_SECURITY_RESTRICT_NETWORK") ?: "false"}")
        
        // Performance configuration
        println("\n⚡ Performance Configuration:")
        println("  Parallel Dependencies: ${getEnv("K2SCRIPT_PERFORMANCE_PARALLEL_DEPS") ?: "true"}")
        println("  Max Threads: ${getEnv("K2SCRIPT_PERFORMANCE_MAX_THREADS") ?: "4"}")
        println("  Incremental Compilation: ${getEnv("K2SCRIPT_PERFORMANCE_INCREMENTAL") ?: "true"}")
    }
    
    /**
     * Demonstrate installation scenarios
     */
    private suspend fun demonstrateInstallation() {
        println("🚀 K2Script Installation Demonstration")
        println("======================================")
        
        val scenarios = listOf(
            InstallationScenario(
                name = "User Installation",
                prefix = "${getUserHome()}/.local",
                mode = "user",
                description = "Install k2script for current user"
            ),
            InstallationScenario(
                name = "System Installation", 
                prefix = "/usr/local",
                mode = "system",
                description = "Install k2script system-wide"
            ),
            InstallationScenario(
                name = "Development Installation",
                prefix = "./build/install",
                mode = "development", 
                description = "Install k2script for development"
            ),
            InstallationScenario(
                name = "Script Installation",
                prefix = "/opt/scripts",
                mode = "script",
                description = "Install a specific script"
            )
        )
        
        scenarios.forEach { scenario ->
            println("\n📦 ${scenario.name}")
            println("   Prefix: ${scenario.prefix}")
            println("   Mode: ${scenario.mode}")
            println("   Description: ${scenario.description}")
            
            val config = generateInstallationConfig(scenario)
            println("   Configuration:")
            config.forEach { (key, value) ->
                println("     $key=$value")
            }
            
            val paths = calculateInstallationPaths(scenario.prefix)
            println("   Installation Paths:")
            paths.forEach { (type, path) ->
                println("     $type: $path")
            }
        }
    }
    
    /**
     * Validate current configuration
     */
    private fun validateConfiguration() {
        println("✅ K2Script Configuration Validation")
        println("====================================")
        
        val issues = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        // Check required directories
        val cacheDir = getEnv("K2SCRIPT_CACHE_DIR") ?: "${getUserHome()}/.k2script/cache"
        if (!File(cacheDir).exists() && !File(cacheDir).mkdirs()) {
            issues.add("Cannot create cache directory: $cacheDir")
        } else {
            println("✅ Cache directory: $cacheDir")
        }
        
        val configDir = getEnv("K2SCRIPT_CONFIG_DIR") ?: "${getUserHome()}/.k2script/config"
        if (!File(configDir).exists() && !File(configDir).mkdirs()) {
            issues.add("Cannot create config directory: $configDir")
        } else {
            println("✅ Config directory: $configDir")
        }
        
        // Check AI configuration
        val aiEnabled = getEnv("K2SCRIPT_AI_ENABLED")?.toBoolean() ?: true
        if (aiEnabled) {
            val apiKey = getEnv("K2SCRIPT_AI_API_KEY")
            if (apiKey == null) {
                warnings.add("AI is enabled but no API key is configured")
            } else {
                println("✅ AI API key: Configured")
            }
            
            val provider = getEnv("K2SCRIPT_AI_PROVIDER") ?: "openai"
            println("✅ AI provider: $provider")
        } else {
            println("ℹ️  AI features: Disabled")
        }
        
        // Check security configuration
        val isolationLevel = getEnv("K2SCRIPT_SECURITY_ISOLATION_LEVEL") ?: "process"
        println("✅ Security level: $isolationLevel")
        
        // Check performance configuration
        val parallelDeps = getEnv("K2SCRIPT_PERFORMANCE_PARALLEL_DEPS")?.toBoolean() ?: true
        println("✅ Performance: Parallel deps ${if (parallelDeps) "enabled" else "disabled"}")
        
        // Report issues
        if (warnings.isNotEmpty()) {
            println("\n⚠️  Warnings:")
            warnings.forEach { println("   $it") }
        }
        
        if (issues.isNotEmpty()) {
            println("\n❌ Issues:")
            issues.forEach { println("   $it") }
            exitProcess(1)
        }
        
        println("\n🎉 Configuration validation passed!")
    }
    
    /**
     * Generate environment setup script
     */
    private fun generateEnvironmentScript() {
        println("📝 Generating Environment Setup Script")
        println("======================================")
        
        val script = buildString {
            appendLine("#!/bin/bash")
            appendLine("# K2Script Environment Configuration")
            appendLine("# Generated on: ${java.time.LocalDateTime.now()}")
            appendLine()
            
            // Core configuration
            appendLine("# Core Configuration")
            appendLine("export K2SCRIPT_INSTALL_PREFIX=\"${getEnv("K2SCRIPT_INSTALL_PREFIX") ?: "$HOME/.local"}\"")
            appendLine("export K2SCRIPT_CACHE_DIR=\"${getEnv("K2SCRIPT_CACHE_DIR") ?: "$HOME/.k2script/cache"}\"")
            appendLine("export K2SCRIPT_CONFIG_DIR=\"${getEnv("K2SCRIPT_CONFIG_DIR") ?: "$HOME/.k2script/config"}\"")
            appendLine("export K2SCRIPT_LOG_LEVEL=\"${getEnv("K2SCRIPT_LOG_LEVEL") ?: "INFO"}\"")
            appendLine("export K2SCRIPT_VERBOSE=\"${getEnv("K2SCRIPT_VERBOSE") ?: "false"}\"")
            appendLine("export K2SCRIPT_SANDBOX=\"${getEnv("K2SCRIPT_SANDBOX") ?: "true"}\"")
            appendLine()
            
            // AI configuration
            appendLine("# AI Configuration")
            appendLine("export K2SCRIPT_AI_PROVIDER=\"${getEnv("K2SCRIPT_AI_PROVIDER") ?: "openai"}\"")
            appendLine("export K2SCRIPT_AI_ENABLED=\"${getEnv("K2SCRIPT_AI_ENABLED") ?: "true"}\"")
            if (getEnv("K2SCRIPT_AI_API_KEY") != null) {
                appendLine("export K2SCRIPT_AI_API_KEY=\"${getEnv("K2SCRIPT_AI_API_KEY")}\"")
            }
            appendLine()
            
            // Installation configuration
            appendLine("# Installation Configuration")
            appendLine("export K2SCRIPT_INSTALL_MODE=\"${getEnv("K2SCRIPT_INSTALL_MODE") ?: "user"}\"")
            appendLine("export K2SCRIPT_INSTALL_CREATE_UNINSTALL=\"${getEnv("K2SCRIPT_INSTALL_CREATE_UNINSTALL") ?: "true"}\"")
            appendLine()
            
            // Security configuration
            appendLine("# Security Configuration")
            appendLine("export K2SCRIPT_SECURITY_ISOLATION_LEVEL=\"${getEnv("K2SCRIPT_SECURITY_ISOLATION_LEVEL") ?: "process"}\"")
            appendLine()
            
            // Performance configuration
            appendLine("# Performance Configuration")
            appendLine("export K2SCRIPT_PERFORMANCE_PARALLEL_DEPS=\"${getEnv("K2SCRIPT_PERFORMANCE_PARALLEL_DEPS") ?: "true"}\"")
            appendLine()
            
            // Add to PATH
            appendLine("# Add k2script to PATH")
            val prefix = getEnv("K2SCRIPT_INSTALL_PREFIX") ?: "$HOME/.local"
            appendLine("export PATH=\"$prefix/bin:\$PATH\"")
        }
        
        println(script)
        
        // Save to file
        val scriptFile = File("setup-k2script.sh")
        scriptFile.writeText(script)
        scriptFile.setExecutable(true)
        
        println("\n✅ Environment script generated: setup-k2script.sh")
        println("   Run: source setup-k2script.sh")
    }
    
    /**
     * Show installation paths for current configuration
     */
    private fun showInstallationPaths() {
        println("📁 K2Script Installation Paths")
        println("===============================")
        
        val prefix = getEnv("K2SCRIPT_INSTALL_PREFIX") ?: "${getUserHome()}/.local"
        val paths = calculateInstallationPaths(prefix)
        
        paths.forEach { (type, path) ->
            val file = File(path)
            val status = when {
                file.exists() -> "✅ Exists"
                file.parentFile?.canWrite() == true -> "📝 Writable"
                else -> "❌ Not accessible"
            }
            println("$type: $path ($status)")
        }
    }
    
    /**
     * Show help information
     */
    private fun showHelp() {
        println("""
            K2Script Configuration Demo
            
            Usage:
              k2script examples/configuration-demo.kts              # Show configuration summary
              k2script examples/configuration-demo.kts --install-demo    # Demonstrate installation scenarios
              k2script examples/configuration-demo.kts --validate-config # Validate current configuration
              k2script examples/configuration-demo.kts --generate-env    # Generate environment script
              k2script examples/configuration-demo.kts --show-paths      # Show installation paths
            
            Examples:
              # Set configuration and run demo
              export K2SCRIPT_INSTALL_PREFIX="/usr/local"
              export K2SCRIPT_VERBOSE="true"
              k2script examples/configuration-demo.kts --install-demo
              
              # Validate configuration
              k2script examples/configuration-demo.kts --validate-config
              
              # Generate environment script
              k2script examples/configuration-demo.kts --generate-env > setup.sh
              source setup.sh
        """.trimIndent())
    }
    
    // ============================================================================
    // HELPER FUNCTIONS
    // ============================================================================
    
    private fun getEnv(key: String): String? = System.getenv(key)
    
    private fun getUserHome(): String = System.getProperty("user.home") ?: "/tmp"
    
    @Serializable
    data class InstallationScenario(
        val name: String,
        val prefix: String,
        val mode: String,
        val description: String
    )
    
    private fun generateInstallationConfig(scenario: InstallationScenario): Map<String, String> {
        return mapOf(
            "K2SCRIPT_INSTALL_PREFIX" to scenario.prefix,
            "K2SCRIPT_INSTALL_MODE" to scenario.mode,
            "K2SCRIPT_CACHE_DIR" to when (scenario.mode) {
                "user" -> "${getUserHome()}/.k2script/cache"
                "system" -> "/var/cache/k2script"
                "development" -> "./build/cache"
                else -> "${scenario.prefix}/cache"
            },
            "K2SCRIPT_CONFIG_DIR" to when (scenario.mode) {
                "user" -> "${getUserHome()}/.k2script/config"
                "system" -> "/etc/k2script"
                "development" -> "./build/config"
                else -> "${scenario.prefix}/config"
            }
        )
    }
    
    private fun calculateInstallationPaths(prefix: String): Map<String, String> {
        return mapOf(
            "Binary" to "$prefix/bin/k2script",
            "Library" to "$prefix/lib/k2script/k2script.jar",
            "Share" to "$prefix/share/k2script/",
            "Cache" to getEnv("K2SCRIPT_CACHE_DIR") ?: "${getUserHome()}/.k2script/cache",
            "Config" to getEnv("K2SCRIPT_CONFIG_DIR") ?: "${getUserHome()}/.k2script/config"
        )
    }
}

// ============================================================================
// MAIN EXECUTION
// ============================================================================

suspend fun main(args: Array<String>) {
    val demo = ConfigurationDemo()
    demo.run(args)
} 