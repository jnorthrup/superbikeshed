@file:OptIn(kotlin.ExperimentalStdlibApi::class, kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.io

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Manages async file operations with proper handle-to-path mapping
 */
class AsyncFileManager {
    private val mutex = Mutex()
    private val pathToHandle = mutableMapOf<String, Int>()
    private val handleToPath = mutableMapOf<Int, String>()
    private var nextHandle = 1

    suspend fun registerFile(path: String): Int = mutex.withLock {
        pathToHandle.getOrPut(path) { 
            val handle = nextHandle++
            handleToPath[handle] = path
            handle
        }
    }

    suspend fun getPath(handle: Int): String? = mutex.withLock {
        handleToPath[handle]
    }

    suspend fun unregisterFile(handle: Int) = mutex.withLock {
        val path = handleToPath.remove(handle)
        if (path != null) {
            pathToHandle.remove(path)
        }
    }

    suspend fun unregisterFile(path: String) = mutex.withLock {
        val handle = pathToHandle.remove(path)
        if (handle != null) {
            handleToPath.remove(handle)
        }
    }

    companion object {
        val instance = AsyncFileManager()
    }
} 