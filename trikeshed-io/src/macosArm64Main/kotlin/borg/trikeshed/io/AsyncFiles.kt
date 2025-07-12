package borg.trikeshed.io

import borg.trikeshed.lib.Join
import borg.trikeshed.lib.Indexed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

actual object AsyncFiles {
    actual val platformFileIO: PlatformFileIO = PlatformFileIOImpl()
    
    actual suspend fun readAllBytes(path: String): ByteArray? {
        return platformFileIO.asyncReadFile(path)
    }
    
    actual suspend fun readString(path: String): String? {
        return platformFileIO.asyncReadFile(path)?.decodeToString()
    }
    
    actual suspend fun readAllLines(path: String): List<String>? {
        return readString(path)?.split("\n")
    }
    
    actual suspend fun write(path: String, content: ByteArray): Boolean {
        return platformFileIO.asyncWriteFile(path, content)
    }
    
    actual suspend fun write(path: String, string: String): Boolean {
        return platformFileIO.asyncWriteFile(path, string.encodeToByteArray())
    }
    
    actual suspend fun write(path: String, lines: List<String>): Boolean {
        return write(path, lines.joinToString("\n"))
    }
    
    actual suspend fun exists(path: String): Boolean {
        return platformFileIO.exists(path)
    }
    
    actual suspend fun copyFile(source: String, destination: String): Boolean {
        val content = readAllBytes(source) ?: return false
        return write(destination, content)
    }
    
    actual suspend fun moveFile(source: String, destination: String): Boolean {
        if (!copyFile(source, destination)) return false
        return platformFileIO.deleteFile(source)
    }

    actual suspend fun streamLines(path: String, bufferSize: Int): Flow<String> = flow {
        val content = readString(path) ?: return@flow
        content.split("\n").forEach { line ->
            emit(line)
        }
    }

    actual suspend fun streamBytes(path: String, chunkSize: Int): Flow<ByteArray> = flow {
        val content = readAllBytes(path) ?: return@flow
        var offset = 0
        while (offset < content.size) {
            val chunk = content.copyOfRange(offset, minOf(offset + chunkSize, content.size))
            emit(chunk)
            offset += chunkSize
        }
    }

    actual suspend fun writeStream(path: String, contentFlow: Flow<ByteArray>): Boolean {
        val chunks = mutableListOf<ByteArray>()
        contentFlow.collect { chunk ->
            chunks.add(chunk)
        }
        val combined = chunks.reduce { acc, chunk -> acc + chunk }
        return write(path, combined)
    }
}