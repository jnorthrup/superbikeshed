@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.io

import borg.trikeshed.lib.*
import java.io.File

/**
 * JVM implementation of PlatformFileIO
 */
actual typealias PlatformFileIO = PlatformFileIOInterface

interface PlatformFileIOInterface {
    suspend fun readFile(path: String): Join<Int, (Int) -> Byte>?
    suspend fun writeFile(path: String, content: Join<Int, (Int) -> Byte>): Boolean
    suspend fun deleteFile(path: String): Boolean
    suspend fun exists(path: String): Boolean
    suspend fun asyncReadFile(path: String): ByteArray?
    suspend fun asyncWriteFile(path: String, content: ByteArray): Boolean
}

actual fun getPlatformFileIO(): PlatformFileIO = PlatformFileIOImpl()

actual class PlatformFileIOImpl : PlatformFileIOInterface {
    
    override suspend fun readFile(path: String): Join<Int, (Int) -> Byte>? {
        return try {
            val bytes = File(path).readBytes()
            bytes.size j { i: Int -> bytes[i] }
        } catch (e: Exception) {
            null
        }
    }
    
    override suspend fun writeFile(path: String, content: Join<Int, (Int) -> Byte>): Boolean {
        return try {
            val size = content.a
            val bytes = ByteArray(size) { i -> content.b(i) }
            File(path).writeBytes(bytes)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun deleteFile(path: String): Boolean {
        return try {
            File(path).delete()
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun exists(path: String): Boolean {
        return try {
            File(path).exists()
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun asyncReadFile(path: String): ByteArray? {
        return try {
            File(path).readBytes()
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun asyncWriteFile(path: String, content: ByteArray): Boolean {
        return try {
            File(path).writeBytes(content)
            true
        } catch (e: Exception) {
            false
        }
    }
}