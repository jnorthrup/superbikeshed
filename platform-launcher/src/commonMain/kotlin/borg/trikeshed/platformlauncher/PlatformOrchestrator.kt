package borg.trikeshed.platformlauncher

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.CoroutineContext

/**
 * Platform Orchestrator Demo
 * 
 * Demonstrates how to use CCEK capabilities for cross-platform orchestration
 * where control can originate from either VM or native side
 */

/**
 * Main orchestrator that uses CCEK context for all operations
 */
class PlatformOrchestrator(private val context: CoroutineContext) {
    
    /**
     * Launch a JVM application using CCEK VM launch capability
     */
    suspend fun launchJvmApplication(
        mainClass: String,
        args: List<String> = emptyList(),
        jvmArgs: List<String> = emptyList()
    ): Int? = withContext(context) {
        val vmCapability = coroutineContext[VmLaunchCapability.Key]
        vmCapability?.launchVm(mainClass, args, jvmArgs)
    }
    
    /**
     * Load a native library using CCEK native DLL capability
     */
    suspend fun loadNativeLibrary(libraryPath: String): Boolean = withContext(context) {
        val nativeCapability = coroutineContext[NativeDllLoadCapability.Key]
        nativeCapability?.loadLibrary(libraryPath) ?: false
    }
    
    /**
     * Call a native function using CCEK native DLL capability
     */
    suspend fun callNativeFunction(
        libraryPath: String,
        functionName: String,
        args: List<Any?> = emptyList()
    ): Any? = withContext(context) {
        val nativeCapability = coroutineContext[NativeDllLoadCapability.Key]
        nativeCapability?.callNative(libraryPath, functionName, args)
    }
    
    /**
     * Orchestrate a cross-platform operation using CCEK platform control
     */
    suspend fun <T> orchestrateOperation(operation: PlatformOperation<T>): T? = withContext(context) {
        val controlCapability = coroutineContext[PlatformControlCapability.Key]
        controlCapability?.let { cap ->
            cap.orchestrate(operation, this)
        }
    }
    
    /**
     * Get available capabilities for the current platform
     */
    fun getAvailableCapabilities(): Set<Class<*>> {
        val controlCapability = context[PlatformControlCapability.Key]
        return controlCapability?.getAvailableCapabilities() ?: emptySet()
    }
    
    /**
     * Get the current platform type
     */
    fun getPlatformType(): PlatformType? {
        val controlCapability = context[PlatformControlCapability.Key]
        return controlCapability?.platformType
    }
}

/**
 * Demo application that showcases CCEK orchestration
 */
class PlatformOrchestratorDemo {
    
    /**
     * Demo: Launch JVM and load native library
     */
    suspend fun demoJvmWithNativeLibrary(context: CoroutineContext) {
        println("=== JVM with Native Library Demo ===")
        
        val orchestrator = PlatformOrchestrator(context)
        
        // Launch a JVM application
        val processId = orchestrator.launchJvmApplication(
            mainClass = "org.example.MainClass",
            args = listOf("--config", "config.json"),
            jvmArgs = listOf("-Xmx2g", "-Djava.library.path=/usr/local/lib")
        )
        
        println("Launched JVM with PID: $processId")
        
        // Load a native library
        val libraryLoaded = orchestrator.loadNativeLibrary("/usr/local/lib/libexample.so")
        println("Native library loaded: $libraryLoaded")
        
        if (libraryLoaded) {
            // Call a native function
            val result = orchestrator.callNativeFunction(
                libraryPath = "/usr/local/lib/libexample.so",
                functionName = "process_data",
                args = listOf("test_data", 42)
            )
            println("Native function result: $result")
        }
        
        // Terminate the JVM process
        if (processId != null) {
            val terminated = orchestrator.orchestrateOperation(
                PlatformOperation.TerminateVm(processId, force = false)
            )
            println("JVM terminated: $terminated")
        }
    }
    
    /**
     * Demo: Native-only orchestration
     */
    suspend fun demoNativeOnlyOrchestration(context: CoroutineContext) {
        println("=== Native-Only Orchestration Demo ===")
        
        val orchestrator = PlatformOrchestrator(context)
        
        // Load multiple native libraries
        val libraries = listOf(
            "/usr/local/lib/libcrypto.so",
            "/usr/local/lib/libssl.so",
            "/usr/local/lib/libz.so"
        )
        
        libraries.forEach { libraryPath ->
            val loaded = orchestrator.loadNativeLibrary(libraryPath)
            println("Loaded $libraryPath: $loaded")
        }
        
        // Call functions from loaded libraries
        val cryptoResult = orchestrator.callNativeFunction(
            libraryPath = "/usr/local/lib/libcrypto.so",
            functionName = "EVP_MD_CTX_new"
        )
        println("Crypto function result: $cryptoResult")
    }
    
    /**
     * Demo: Cross-platform capability detection
     */
    fun demoCapabilityDetection(context: CoroutineContext) {
        println("=== Capability Detection Demo ===")
        
        val orchestrator = PlatformOrchestrator(context)
        
        val platformType = orchestrator.getPlatformType()
        println("Platform type: $platformType")
        
        val capabilities = orchestrator.getAvailableCapabilities()
        println("Available capabilities:")
        capabilities.forEach { capability ->
            println("  - ${capability.simpleName}")
        }
    }
}

/**
 * Main demo function that can be called from any platform
 */
suspend fun runPlatformOrchestratorDemo(context: CoroutineContext) {
    val demo = PlatformOrchestratorDemo()
    
    // Detect capabilities
    demo.demoCapabilityDetection(context)
    
    // Run platform-specific demos based on available capabilities
    val orchestrator = PlatformOrchestrator(context)
    val capabilities = orchestrator.getAvailableCapabilities()
    
    if (capabilities.contains(VmLaunchCapability::class.java)) {
        demo.demoJvmWithNativeLibrary(context)
    }
    
    if (capabilities.contains(NativeDllLoadCapability::class.java)) {
        demo.demoNativeOnlyOrchestration(context)
    }
} 