package borg.trikeshed.platformlauncher

import kotlinx.coroutines.*
import java.io.File
import java.lang.ProcessBuilder
import java.lang.Process
import java.lang.Runtime
import java.lang.management.ManagementFactory

/**
 * JVM-specific implementations of platform CCEK capabilities
 */

/**
 * JVM VM Launch Capability Implementation
 */
class JvmVmLaunchCapability : VmLaunchCapability {
    private val runningProcesses = mutableMapOf<Int, Process>()
    
    override suspend fun launchVm(
        mainClass: String,
        args: List<String>,
        jvmArgs: List<String>,
        workingDir: String?
    ): Int = withContext(Dispatchers.IO) {
        val javaHome = System.getProperty("java.home")
        val javaBin = "$javaHome/bin/java"
        
        val command = mutableListOf<String>().apply {
            add(javaBin)
            addAll(jvmArgs)
            add(mainClass)
            addAll(args)
        }
        
        val processBuilder = ProcessBuilder(command).apply {
            if (workingDir != null) {
                directory(File(workingDir))
            }
            inheritIO() // Redirect I/O to parent process
        }
        
        val process = processBuilder.start()
        val processId = getProcessId(process)
        
        runningProcesses[processId] = process
        
        // Launch a coroutine to monitor process completion
        launch {
            process.waitFor()
            runningProcesses.remove(processId)
        }
        
        processId
    }
    
    override suspend fun terminateVm(processId: Int, force: Boolean): Boolean = withContext(Dispatchers.IO) {
        val process = runningProcesses[processId] ?: return@withContext false
        
        if (force) {
            process.destroyForcibly()
        } else {
            process.destroy()
        }
        
        val terminated = process.waitFor() >= 0
        if (terminated) {
            runningProcesses.remove(processId)
        }
        
        terminated
    }
    
    override suspend fun isVmRunning(processId: Int): Boolean {
        val process = runningProcesses[processId] ?: return false
        return process.isAlive
    }
    
    private fun getProcessId(process: Process): Int {
        return try {
            val pidField = process.javaClass.getDeclaredField("pid")
            pidField.isAccessible = true
            pidField.getInt(process)
        } catch (e: Exception) {
            // Fallback to a hash-based ID if we can't get the real PID
            process.hashCode()
        }
    }
}

/**
 * JVM Platform Control Capability Implementation
 */
class JvmPlatformControlCapability : PlatformControlCapability {
    override val platformType: PlatformType = PlatformType.JVM
    
    private val vmLaunchCapability = JvmVmLaunchCapability()
    
    override suspend fun <T> orchestrate(
        operation: PlatformOperation<T>,
        scope: CoroutineScope
    ): T = when (operation) {
        is PlatformOperation.LaunchVm -> {
            vmLaunchCapability.launchVm(
                operation.mainClass,
                operation.args,
                operation.jvmArgs
            ) as T
        }
        is PlatformOperation.TerminateVm -> {
            vmLaunchCapability.terminateVm(
                operation.processId,
                operation.force
            ) as T
        }
        is PlatformOperation.LoadNativeLibrary -> {
            // JVM can load native libraries via System.loadLibrary
            try {
                System.loadLibrary(operation.libraryPath)
                true as T
            } catch (e: UnsatisfiedLinkError) {
                false as T
            }
        }
        is PlatformOperation.CallNativeFunction -> {
            // JVM native calls would require JNI setup
            // For now, return null to indicate not supported
            null as T
        }
    }
    
    override fun getAvailableCapabilities(): Set<Class<*>> {
        return setOf(
            VmLaunchCapability::class.java,
            PlatformControlCapability::class.java
        )
    }
}

/**
 * Factory functions for JVM capabilities
 */
object JvmCapabilityFactory {
    fun createVmLaunchCapability(): VmLaunchCapability = JvmVmLaunchCapability()
    
    fun createPlatformControlCapability(): PlatformControlCapability = JvmPlatformControlCapability()
    
    fun createJvmContext(): kotlin.coroutines.CoroutineContext {
        return JvmVmLaunchCapability() + JvmPlatformControlCapability()
    }
} 