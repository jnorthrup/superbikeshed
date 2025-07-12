package borg.trikeshed.platformlauncher

import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * Standalone Platform Launcher Demo
 * 
 * Demonstrates the CCEK platform orchestration system working
 * without depending on the broken trikeshed-lib module.
 */

fun main() = runBlocking {
    println("🚀 Platform Launcher CCEK Demo")
    println("=" * 50)
    
    // Create JVM context with capabilities
    val jvmContext = JvmCapabilityFactory.createJvmContext()
    
    // Run the demo
    runPlatformOrchestratorDemo(jvmContext)
    
    println("\n✅ Demo completed successfully!")
}

/**
 * Extension function for string repetition
 */
operator fun String.times(count: Int): String = repeat(count)

/**
 * Demo function that works with our CCEK system
 */
suspend fun runPlatformOrchestratorDemo(context: CoroutineContext) {
    println("\n🔍 Detecting Platform Capabilities...")
    
    val orchestrator = PlatformOrchestrator(context)
    
    // Detect capabilities
    val platformType = orchestrator.getPlatformType()
    println("Platform type: $platformType")
    
    val capabilities = orchestrator.getAvailableCapabilities()
    println("Available capabilities:")
    capabilities.forEach { capability ->
        println("  - ${capability.simpleName}")
    }
    
    println("\n🎯 Testing VM Launch Capability...")
    
    // Test VM launch (this would launch a real JVM in production)
    val processId = orchestrator.launchJvmApplication(
        mainClass = "org.example.MainClass",
        args = listOf("--config", "config.json"),
        jvmArgs = listOf("-Xmx2g", "-Djava.library.path=/usr/local/lib")
    )
    
    println("Launched JVM with PID: $processId")
    
    println("\n📚 Testing Native Library Loading...")
    
    // Test native library loading
    val libraryLoaded = orchestrator.loadNativeLibrary("/usr/local/lib/libexample.so")
    println("Native library loaded: $libraryLoaded")
    
    if (libraryLoaded) {
        // Test native function call
        val result = orchestrator.callNativeFunction(
            libraryPath = "/usr/local/lib/libexample.so",
            functionName = "process_data",
            args = listOf("test_data", 42)
        )
        println("Native function result: $result")
    }
    
    println("\n🎮 Testing Platform Operation Orchestration...")
    
    // Test orchestration operations
    val loadOp = PlatformOperation.LoadNativeLibrary("/usr/local/lib/libcrypto.so")
    val loadResult = orchestrator.orchestrateOperation(loadOp)
    println("Orchestrated library load: $loadResult")
    
    if (processId != null) {
        val terminateOp = PlatformOperation.TerminateVm(processId, force = false)
        val terminateResult = orchestrator.orchestrateOperation(terminateOp)
        println("Orchestrated VM termination: $terminateResult")
    }
    
    println("\n🔧 CCEK Context Composition Demo...")
    
    // Demonstrate CCEK context composition
    val vmCapability = context[VmLaunchCapability.Key]
    val nativeCapability = context[NativeDllLoadCapability.Key]
    val controlCapability = context[PlatformControlCapability.Key]
    
    println("VM Launch Capability: ${vmCapability != null}")
    println("Native DLL Load Capability: ${nativeCapability != null}")
    println("Platform Control Capability: ${controlCapability != null}")
    
    // Test direct capability usage
    vmCapability?.let { vm ->
        println("Direct VM capability test:")
        val testPid = vm.launchVm(
            mainClass = "org.example.TestClass",
            args = listOf("--test"),
            jvmArgs = listOf("-Xmx512m")
        )
        println("  Test JVM launched with PID: $testPid")
        
        if (testPid != null) {
            val terminated = vm.terminateVm(testPid, force = false)
            println("  Test JVM terminated: $terminated")
        }
    }
    
    nativeCapability?.let { native ->
        println("Direct Native capability test:")
        val loaded = native.loadLibrary("/usr/local/lib/libz.so")
        println("  Library loaded: $loaded")
        
        if (loaded) {
            val symbol = native.callNative("/usr/local/lib/libz.so", "zlibVersion", emptyList())
            println("  zlibVersion symbol: $symbol")
        }
    }
    
    println("\n🎉 CCEK Platform Orchestration System Working!")
    println("Key Features Demonstrated:")
    println("  ✅ Coroutine Context Element Keys (CCEK)")
    println("  ✅ Platform-specific capability detection")
    println("  ✅ VM launch orchestration")
    println("  ✅ Native library loading")
    println("  ✅ Cross-platform operation composition")
    println("  ✅ Context-driven execution")
} 