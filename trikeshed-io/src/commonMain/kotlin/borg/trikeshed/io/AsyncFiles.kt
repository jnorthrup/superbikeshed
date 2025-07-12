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
expect object AsyncFiles {
    val platformFileIO: PlatformFileIO
    
    suspend fun readAllBytes(path: String): ByteArray?
    suspend fun readString(path: String): String?
    suspend fun readAllLines(path: String): List<String>?
    suspend fun write(path: String, content: ByteArray): Boolean
    suspend fun write(path: String, string: String): Boolean
    suspend fun write(path: String, lines: List<String>): Boolean
    suspend fun exists(path: String): Boolean
    suspend fun copyFile(source: String, destination: String): Boolean
    suspend fun moveFile(source: String, destination: String): Boolean
    suspend fun streamLines(path: String, bufferSize: Int = 8192): Flow<String>
    suspend fun streamBytes(path: String, chunkSize: Int = 8192): Flow<ByteArray>
    suspend fun writeStream(path: String, contentFlow: Flow<ByteArray>): Boolean
} 