package borg.trikeshed.platformlauncher

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope

/**
 * CCEK Platform Orchestration System
 * 
 * Provides coroutine context element keys for platform-specific capabilities:
 * - VM Launch: JVM process management
 * - Native DLL Load: Dynamic library loading and native function calls
 * - Cross-Platform Control: Orchestration from either VM or native side
 */

// === CORE CCEK CAPABILITIES ===

/**
 * VM Launch Capability - provides JVM process management
 */
interface VmLaunchCapability : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<VmLaunchCapability>
    override val key: CoroutineContext.Key<*> get() = Key
    
    /**
     * Launch a JVM process with the given arguments
     * @param mainClass The main class to execute
     * @param args Command line arguments
     * @param jvmArgs JVM-specific arguments
     * @param workingDir Working directory for the process
     * @return Process ID of the launched VM
     */
    suspend fun launchVm(
        mainClass: String,
        args: List<String> = emptyList(),
        jvmArgs: List<String> = emptyList(),
        workingDir: String? = null
    ): Int
    
    /**
     * Terminate a running VM process
     * @param processId The process ID to terminate
     * @param force Whether to force kill the process
     */
    suspend fun terminateVm(processId: Int, force: Boolean = false): Boolean
    
    /**
     * Check if a VM process is still running
     * @param processId The process ID to check
     */
    suspend fun isVmRunning(processId: Int): Boolean
}

/**
 * Native DLL Load Capability - provides dynamic library loading
 */
interface NativeDllLoadCapability : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<NativeDllLoadCapability>
    override val key: CoroutineContext.Key<*> get() = Key
    
    /**
     * Load a native library
     * @param libraryPath Path to the native library
     * @return True if loaded successfully
     */
    suspend fun loadLibrary(libraryPath: String): Boolean
    
    /**
     * Unload a native library
     * @param libraryPath Path to the native library
     */
    suspend fun unloadLibrary(libraryPath: String): Boolean
    
    /**
     * Call a native function
     * @param libraryPath Path to the library containing the function
     * @param functionName Name of the function to call
     * @param args Arguments to pass to the function
     * @return Result of the function call
     */
    suspend fun callNative(
        libraryPath: String,
        functionName: String,
        args: List<Any?> = emptyList()
    ): Any?
    
    /**
     * Check if a library is loaded
     * @param libraryPath Path to the library
     */
    suspend fun isLibraryLoaded(libraryPath: String): Boolean
}

/**
 * Platform Control Capability - provides cross-platform orchestration
 */
interface PlatformControlCapability : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<PlatformControlCapability>
    override val key: CoroutineContext.Key<*> get() = Key
    
    /**
     * Get the current platform type
     */
    val platformType: PlatformType
    
    /**
     * Orchestrate a cross-platform operation
     * @param operation The operation to perform
     * @param scope Coroutine scope for the operation
     */
    suspend fun <T> orchestrate(
        operation: PlatformOperation<T>,
        scope: CoroutineScope
    ): T
    
    /**
     * Get available capabilities for the current platform
     */
    fun getAvailableCapabilities(): Set<Class<*>>
}

// === PLATFORM TYPES ===

enum class PlatformType {
    JVM,
    LINUX_X64,
    MACOS_ARM64,
    HYBRID
}

// === PLATFORM OPERATIONS ===

sealed class PlatformOperation<out T> {
    data class LaunchVm(
        val mainClass: String,
        val args: List<String> = emptyList(),
        val jvmArgs: List<String> = emptyList()
    ) : PlatformOperation<Int>()
    
    data class LoadNativeLibrary(
        val libraryPath: String
    ) : PlatformOperation<Boolean>()
    
    data class CallNativeFunction(
        val libraryPath: String,
        val functionName: String,
        val args: List<Any?> = emptyList()
    ) : PlatformOperation<Any?>()
    
    data class TerminateVm(
        val processId: Int,
        val force: Boolean = false
    ) : PlatformOperation<Boolean>()
}

// === ORCHESTRATION FUNCTIONS ===

/**
 * Launch a VM using CCEK capability
 */
suspend fun launchVm(
    mainClass: String,
    args: List<String> = emptyList(),
    jvmArgs: List<String> = emptyList(),
    workingDir: String? = null
): Int? {
    val capability = kotlin.coroutines.coroutineContext[VmLaunchCapability.Key]
    return capability?.launchVm(mainClass, args, jvmArgs, workingDir)
}

/**
 * Load a native library using CCEK capability
 */
suspend fun loadNativeLibrary(libraryPath: String): Boolean {
    val capability = kotlin.coroutines.coroutineContext[NativeDllLoadCapability.Key]
    return capability?.loadLibrary(libraryPath) ?: false
}

/**
 * Call a native function using CCEK capability
 */
suspend fun callNativeFunction(
    libraryPath: String,
    functionName: String,
    args: List<Any?> = emptyList()
): Any? {
    val capability = kotlin.coroutines.coroutineContext[NativeDllLoadCapability.Key]
    return capability?.callNative(libraryPath, functionName, args)
}

/**
 * Orchestrate a platform operation using CCEK capability
 */
suspend fun <T> orchestratePlatformOperation(
    operation: PlatformOperation<T>
): T? {
    val capability = kotlin.coroutines.coroutineContext[PlatformControlCapability.Key]
    return capability?.let { cap ->
        kotlinx.coroutines.withContext(cap) {
            cap.orchestrate(operation, this)
        }
    }
} 