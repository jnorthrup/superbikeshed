@file:OptIn(kotlin.ExperimentalStdlibApi::class, kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.io

import borg.trikeshed.lib.Join
import borg.trikeshed.lib.Indexed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Platform-specific file IO instance
 */
expect val platformFileIO: PlatformFileIO

/**
 * High-level async file operations
 */
object AsyncFiles {
    private val fileIO = platformFileIO
    
    suspend fun readAllBytes(path: String): ByteArray? {
        return fileIO.asyncReadFile(path)
    }
    
    suspend fun readString(path: String): String? {
        return fileIO.asyncReadFile(path)?.decodeToString()
    }
    
    suspend fun readAllLines(path: String): List<String>? {
        return readString(path)?.split("\n")
    }
    
    suspend fun write(path: String, content: ByteArray): Boolean {
        return fileIO.asyncWriteFile(path, content)
    }
    
    suspend fun write(path: String, string: String): Boolean {
        return fileIO.asyncWriteFile(path, string.encodeToByteArray())
    }
    
    suspend fun write(path: String, lines: List<String>): Boolean {
        return write(path, lines.joinToString("\n"))
    }
    
    suspend fun exists(path: String): Boolean {
        return fileIO.exists(path)
    }
    
    suspend fun copyFile(source: String, destination: String): Boolean {
        val content = readAllBytes(source) ?: return false
        return write(destination, content)
    }
    
    suspend fun moveFile(source: String, destination: String): Boolean {
        if (!copyFile(source, destination)) return false
        return fileIO.deleteFile(source)
    }

    suspend fun streamLines(path: String, bufferSize: Int = 8192): Flow<String> = flow {
        val content = readString(path) ?: return@flow
        content.split("\n").forEach { line ->
            emit(line)
        }
    }

    suspend fun streamBytes(path: String, chunkSize: Int = 8192): Flow<ByteArray> = flow {
        val content = readAllBytes(path) ?: return@flow
        var offset = 0
        while (offset < content.size) {
            val chunk = content.copyOfRange(offset, minOf(offset + chunkSize, content.size))
            emit(chunk)
            offset += chunkSize
        }
    }

    suspend fun writeStream(path: String, contentFlow: Flow<ByteArray>): Boolean {
        val chunks = mutableListOf<ByteArray>()
        contentFlow.collect { chunk ->
            chunks.add(chunk)
        }
        val combined = chunks.reduce { acc, chunk -> acc + chunk }
        return write(path, combined)
    }

    // Platform-specific compression support moved to expect/actual

} 