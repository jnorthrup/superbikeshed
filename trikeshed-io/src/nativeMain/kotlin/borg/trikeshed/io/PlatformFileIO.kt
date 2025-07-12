@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.io

import borg.trikeshed.lib.*

/**
 * macOS ARM64 implementation of PlatformFileIO
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

actual val platformFileIO: PlatformFileIO = PlatformFileIOImpl()

actual class PlatformFileIOImpl : PlatformFileIOInterface {
    
    override suspend fun readFile(path: String): Join<Int, (Int) -> Byte>? {
        println("PlatformFileIO.readFile() not implemented for macosArm64")
        return null
    }
    
    override suspend fun writeFile(path: String, content: Join<Int, (Int) -> Byte>): Boolean {
        println("PlatformFileIO.writeFile() not implemented for macosArm64")
        return false
    }

    override suspend fun deleteFile(path: String): Boolean {
        println("PlatformFileIO.deleteFile() not implemented for macosArm64")
        return false
    }

    override suspend fun exists(path: String): Boolean {
        println("PlatformFileIO.exists() not implemented for macosArm64")
        return false
    }

    override suspend fun asyncReadFile(path: String): ByteArray? {
        println("PlatformFileIO.asyncReadFile() not implemented for macosArm64")
        return null
    }

    override suspend fun asyncWriteFile(path: String, content: ByteArray): Boolean {
        println("PlatformFileIO.asyncWriteFile() not implemented for macosArm64")
        return false
    }
}