package borg.trikeshed.platformlauncher

import kotlinx.coroutines.*
import kotlinx.cinterop.*
import platform.posix.*

/**
 * Native-specific implementations of platform CCEK capabilities
 * Uses direct libc calls for maximum performance and control
 */

/**
 * Native DLL Load Capability Implementation with direct libc
 */
class NativeDllLoadCapability : NativeDllLoadCapability {
    private val loadedLibraries = mutableMapOf<String, COpaquePointer>()
    
    override suspend fun loadLibrary(libraryPath: String): Boolean = withContext(Dispatchers.Default) {
        try {
            // Direct libc dlopen call
            val handle = dlopen(libraryPath, RTLD_LAZY or RTLD_GLOBAL)
            if (handle != null) {
                loadedLibraries[libraryPath] = handle
                true
            } else {
                // Get error message
                val error = dlerror()?.toKString()
                println("Failed to load library $libraryPath: $error")
                false
            }
        } catch (e: Exception) {
            println("Exception loading library $libraryPath: ${e.message}")
            false
        }
    }
    
    override suspend fun unloadLibrary(libraryPath: String): Boolean = withContext(Dispatchers.Default) {
        val handle = loadedLibraries[libraryPath] ?: return@withContext false
        try {
            val result = dlclose(handle)
            if (result == 0) {
                loadedLibraries.remove(libraryPath)
                true
            } else {
                val error = dlerror()?.toKString()
                println("Failed to unload library $libraryPath: $error")
                false
            }
        } catch (e: Exception) {
            println("Exception unloading library $libraryPath: ${e.message}")
            false
        }
    }
    
    override suspend fun callNative(
        libraryPath: String,
        functionName: String,
        args: List<Any?>
    ): Any? = withContext(Dispatchers.Default) {
        val handle = loadedLibraries[libraryPath] ?: return@withContext null
        try {
            // Clear any previous error
            dlerror()
            
            val symbol = dlsym(handle, functionName)
            val error = dlerror()
            if (error != null) {
                println("Failed to get symbol $functionName: ${error.toKString()}")
                return@withContext null
            }
            
            if (symbol != null) {
                // For now, return the symbol pointer
                // In a full implementation, this would use platform-specific FFI
                // to actually call the function with the provided arguments
                symbol.rawValue
            } else {
                null
            }
        } catch (e: Exception) {
            println("Exception calling native function $functionName: ${e.message}")
            null
        }
    }
    
    override suspend fun isLibraryLoaded(libraryPath: String): Boolean {
        return loadedLibraries.containsKey(libraryPath)
    }
    
    /**
     * Get all loaded library handles
     */
    fun getLoadedLibraries(): Map<String, COpaquePointer> = loadedLibraries.toMap()
    
    /**
     * Get library handle by path
     */
    fun getLibraryHandle(libraryPath: String): COpaquePointer? = loadedLibraries[libraryPath]
}

/**
 * Native Platform Control Capability Implementation
 */
class NativePlatformControlCapability : PlatformControlCapability {
    override val platformType: PlatformType = PlatformType.LINUX_X64
    
    private val nativeDllCapability = NativeDllLoadCapability()
    
    override suspend fun <T> orchestrate(
        operation: PlatformOperation<T>,
        scope: CoroutineScope
    ): T = when (operation) {
        is PlatformOperation.LoadNativeLibrary -> {
            nativeDllCapability.loadLibrary(operation.libraryPath) as T
        }
        is PlatformOperation.CallNativeFunction -> {
            nativeDllCapability.callNative(
                operation.libraryPath,
                operation.functionName,
                operation.args
            ) as T
        }
        is PlatformOperation.LaunchVm -> {
            // Native can launch JVM via direct fork/exec
            launchVmViaNative(operation.mainClass, operation.args, operation.jvmArgs) as T
        }
        is PlatformOperation.TerminateVm -> {
            // Native can terminate via direct kill
            terminateVmViaNative(operation.processId, operation.force) as T
        }
    }
    
    override fun getAvailableCapabilities(): Set<Class<*>> {
        return setOf(
            NativeDllLoadCapability::class.java,
            PlatformControlCapability::class.java
        )
    }
    
    private suspend fun launchVmViaNative(
        mainClass: String,
        args: List<String>,
        jvmArgs: List<String>
    ): Int = withContext(Dispatchers.Default) {
        // Use direct libc fork/exec to launch JVM
        val javaHome = getenv("JAVA_HOME")?.toKString() ?: "/usr/lib/jvm/default-java"
        val javaBin = "$javaHome/bin/java"
        
        val command = mutableListOf<String>().apply {
            add(javaBin)
            addAll(jvmArgs)
            add(mainClass)
            addAll(args)
        }
        
        val pid = fork()
        when (pid) {
            -1 -> {
                // Fork failed
                println("Failed to fork process")
                -1
            }
            0 -> {
                // Child process - exec the JVM
                try {
                    val argv = command.map { it.cstr }.toCStringArray()
                    execvp(javaBin, argv)
                    // If we reach here, exec failed
                    println("Failed to exec JVM: $javaBin")
                    exit(1)
                } catch (e: Exception) {
                    println("Exception in child process: ${e.message}")
                    exit(1)
                }
            }
            else -> {
                // Parent process - return child PID
                println("Launched JVM with PID: $pid")
                pid
            }
        }
    }
    
    private suspend fun terminateVmViaNative(processId: Int, force: Boolean): Boolean = withContext(Dispatchers.Default) {
        val signal = if (force) SIGKILL else SIGTERM
        val result = kill(processId.toPid(), signal)
        if (result == 0) {
            println("Sent signal ${if (force) "SIGKILL" else "SIGTERM"} to process $processId")
            true
        } else {
            println("Failed to send signal to process $processId")
            false
        }
    }
}

/**
 * Factory functions for Native capabilities
 */
object NativeCapabilityFactory {
    fun createNativeDllLoadCapability(): NativeDllLoadCapability = NativeDllLoadCapability()
    
    fun createPlatformControlCapability(): PlatformControlCapability = NativePlatformControlCapability()
    
    fun createNativeContext(): kotlin.coroutines.CoroutineContext {
        return NativeDllLoadCapability() + NativePlatformControlCapability()
    }
} 