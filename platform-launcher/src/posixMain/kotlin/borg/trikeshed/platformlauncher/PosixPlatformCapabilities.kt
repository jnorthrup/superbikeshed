package borg.trikeshed.platformlauncher

import kotlinx.coroutines.*
import platform.posix.*
import kotlinx.cinterop.*
import platform.darwin.*

/**
 * POSIX-specific implementations of platform CCEK capabilities
 * Uses portable POSIX APIs via kotlinx.cinterop
 */

/**
 * POSIX Native DLL Load Capability Implementation
 */
class PosixNativeDllLoadCapability : NativeDllLoadCapability {
    private val loadedLibraries = mutableMapOf<String, COpaquePointer>()
    
    override suspend fun loadLibrary(libraryPath: String): Boolean = withContext(Dispatchers.Default) {
        try {
            val handle = dlopen(libraryPath, RTLD_LAZY)
            if (handle != null) {
                loadedLibraries[libraryPath] = handle
                true
            } else {
                false
            }
        } catch (e: Exception) {
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
                false
            }
        } catch (e: Exception) {
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
            val symbol = dlsym(handle, functionName)
            if (symbol != null) {
                // For POSIX, we'll return the symbol pointer
                // Actual function calling would require platform-specific FFI
                symbol.rawValue
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    override suspend fun isLibraryLoaded(libraryPath: String): Boolean {
        return loadedLibraries.containsKey(libraryPath)
    }
}

/**
 * POSIX Platform Control Capability Implementation
 */
class PosixPlatformControlCapability : PlatformControlCapability {
    override val platformType: PlatformType = PlatformType.LINUX_X64 // Will be detected at runtime
    
    private val nativeDllCapability = PosixNativeDllLoadCapability()
    
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
            // POSIX can launch JVM via fork/exec
            launchVmViaPosix(operation.mainClass, operation.args, operation.jvmArgs) as T
        }
        is PlatformOperation.TerminateVm -> {
            // POSIX can terminate via kill
            terminateVmViaPosix(operation.processId, operation.force) as T
        }
    }
    
    override fun getAvailableCapabilities(): Set<Class<*>> {
        return setOf(
            NativeDllLoadCapability::class.java,
            PlatformControlCapability::class.java
        )
    }
    
    private suspend fun launchVmViaPosix(
        mainClass: String,
        args: List<String>,
        jvmArgs: List<String>
    ): Int = withContext(Dispatchers.Default) {
        // Use POSIX fork/exec to launch JVM
        val javaHome = getenv("JAVA_HOME")?.toKString() ?: when {
            // Detect platform at runtime
            platform.darwin.getpid() > 0 -> "/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home"
            else -> "/usr/lib/jvm/default-java"
        }
        val javaBin = "$javaHome/bin/java"
        
        val command = mutableListOf<String>().apply {
            add(javaBin)
            addAll(jvmArgs)
            add(mainClass)
            addAll(args)
        }
        
        val pid = fork()
        when (pid) {
            -1 -> -1 // Fork failed
            0 -> {
                // Child process - exec the JVM
                val argv = command.map { it.cstr }.toCStringArray()
                execvp(javaBin, argv)
                exit(1) // Should not reach here
            }
            else -> pid // Parent process - return child PID
        }
    }
    
    private suspend fun terminateVmViaPosix(processId: Int, force: Boolean): Boolean = withContext(Dispatchers.Default) {
        val signal = if (force) SIGKILL else SIGTERM
        val result = kill(processId.toPid(), signal)
        result == 0
    }
}

/**
 * Factory functions for POSIX capabilities
 */
object PosixCapabilityFactory {
    fun createNativeDllLoadCapability(): NativeDllLoadCapability = PosixNativeDllLoadCapability()
    
    fun createPlatformControlCapability(): PlatformControlCapability = PosixPlatformControlCapability()
    
    fun createPosixContext(): kotlin.coroutines.CoroutineContext {
        return PosixNativeDllLoadCapability() + PosixPlatformControlCapability()
    }
} 