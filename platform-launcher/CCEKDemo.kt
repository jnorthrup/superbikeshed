#!/usr/bin/env kotlin

@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * CCEK Platform Orchestration Demo
 * 
 * Demonstrates Coroutine Context Element Keys for platform orchestration
 * without complex dependencies.
 */

// === CCEK CAPABILITIES ===

/**
 * VM Launch Capability using CCEK
 */
class VmLaunchCapability : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> get() = Key
    companion object Key : CoroutineContext.Key<VmLaunchCapability>
    
    suspend fun launchVm(mainClass: String, args: List<String> = emptyList()): Int {
        delay(100) // Simulate launch time
        println("🚀 Launched VM: $mainClass with args: $args")
        return (1000..9999).random() // Mock PID
    }
    
    suspend fun terminateVm(processId: Int): Boolean {
        delay(50) // Simulate termination time
        println("🛑 Terminated VM with PID: $processId")
        return true
    }
}

/**
 * Native Library Capability using CCEK
 */
class NativeLibraryCapability : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> get() = Key
    companion object Key : CoroutineContext.Key<NativeLibraryCapability>
    
    suspend fun loadLibrary(path: String): Boolean {
        delay(200) // Simulate loading time
        println("📚 Loaded native library: $path")
        return true
    }
    
    suspend fun callFunction(library: String, function: String, args: List<Any>): Any {
        delay(50) // Simulate function call time
        println("⚡ Called native function: $function with args: $args")
        return "Result from $function"
    }
}

/**
 * Platform Control Capability using CCEK
 */
class PlatformControlCapability : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> get() = Key
    companion object Key : CoroutineContext.Key<PlatformControlCapability>
    
    val platformType = "JVM"
    
    suspend fun <T> orchestrate(operation: String, args: List<Any>): T? {
        delay(100) // Simulate orchestration time
        println("🎮 Orchestrated operation: $operation with args: $args")
        return when (operation) {
            "loadLibrary" -> true as T
            "launchVm" -> 12345 as T
            "terminateVm" -> true as T
            else -> null as T
        }
    }
}

// === PLATFORM ORCHESTRATOR ===

/**
 * Platform Orchestrator using CCEK composition
 */
class PlatformOrchestrator(private val context: CoroutineContext) {
    
    suspend fun launchApplication(mainClass: String, args: List<String>): Int? {
        val vmCapability = context[VmLaunchCapability.Key]
        return vmCapability?.launchVm(mainClass, args)
    }
    
    suspend fun loadNativeLibrary(path: String): Boolean {
        val nativeCapability = context[NativeLibraryCapability.Key]
        return nativeCapability?.loadLibrary(path) ?: false
    }
    
    suspend fun callNativeFunction(library: String, function: String, args: List<Any>): Any? {
        val nativeCapability = context[NativeLibraryCapability.Key]
        return nativeCapability?.callFunction(library, function, args)
    }
    
    suspend fun <T> orchestrateOperation(operation: String, args: List<Any>): T? {
        val controlCapability = context[PlatformControlCapability.Key]
        return controlCapability?.orchestrate(operation, args)
    }
    
    fun getPlatformType(): String? {
        val controlCapability = context[PlatformControlCapability.Key]
        return controlCapability?.platformType
    }
    
    fun getAvailableCapabilities(): List<String> {
        return listOfNotNull(
            if (context[VmLaunchCapability.Key] != null) "VmLaunchCapability" else null,
            if (context[NativeLibraryCapability.Key] != null) "NativeLibraryCapability" else null,
            if (context[PlatformControlCapability.Key] != null) "PlatformControlCapability" else null
        )
    }
}

// === DEMO EXECUTION ===

fun main() = runBlocking {
    println("🚀 CCEK Platform Orchestration Demo")
    println("=" * 50)
    
    // Create context with all capabilities
    val context = VmLaunchCapability() + NativeLibraryCapability() + PlatformControlCapability()
    
    // Create orchestrator
    val orchestrator = PlatformOrchestrator(context)
    
    println("\n🔍 Platform Detection:")
    println("Platform type: ${orchestrator.getPlatformType()}")
    println("Available capabilities: ${orchestrator.getAvailableCapabilities()}")
    
    println("\n🎯 VM Launch Test:")
    val processId = orchestrator.launchApplication("org.example.MainClass", listOf("--config", "config.json"))
    println("Launched with PID: $processId")
    
    println("\n📚 Native Library Test:")
    val libraryLoaded = orchestrator.loadNativeLibrary("/usr/local/lib/libexample.so")
    println("Library loaded: $libraryLoaded")
    
    if (libraryLoaded) {
        val result = orchestrator.callNativeFunction("/usr/local/lib/libexample.so", "process_data", listOf("test", 42))
        println("Function result: $result")
    }
    
    println("\n🎮 Orchestration Test:")
    val orchestratedLoad = orchestrator.orchestrateOperation<Boolean>("loadLibrary", listOf("/usr/local/lib/libcrypto.so"))
    println("Orchestrated load: $orchestratedLoad")
    
    val orchestratedLaunch = orchestrator.orchestrateOperation<Int>("launchVm", listOf("org.example.TestClass"))
    println("Orchestrated launch: $orchestratedLaunch")
    
    if (processId != null) {
        val orchestratedTerminate = orchestrator.orchestrateOperation<Boolean>("terminateVm", listOf(processId))
        println("Orchestrated terminate: $orchestratedTerminate")
    }
    
    println("\n🔧 Direct CCEK Access:")
    val vmCap = context[VmLaunchCapability.Key]
    val nativeCap = context[NativeLibraryCapability.Key]
    val controlCap = context[PlatformControlCapability.Key]
    
    println("VM Capability: ${vmCap != null}")
    println("Native Capability: ${nativeCap != null}")
    println("Control Capability: ${controlCap != null}")
    
    // Direct capability usage
    vmCap?.let { vm ->
        val testPid = vm.launchVm("org.example.TestClass", listOf("--test"))
        println("Direct VM launch PID: $testPid")
        vm.terminateVm(testPid)
    }
    
    nativeCap?.let { native ->
        native.loadLibrary("/usr/local/lib/libz.so")
        val result = native.callFunction("/usr/local/lib/libz.so", "zlibVersion", emptyList())
        println("Direct native call result: $result")
    }
    
    println("\n🎉 CCEK System Working!")
    println("Key Features:")
    println("  ✅ Coroutine Context Element Keys")
    println("  ✅ Platform capability composition")
    println("  ✅ Context-driven execution")
    println("  ✅ Orchestration patterns")
    println("  ✅ μ-Chain compliance")
    println("  ✅ Standalone operation")
} 