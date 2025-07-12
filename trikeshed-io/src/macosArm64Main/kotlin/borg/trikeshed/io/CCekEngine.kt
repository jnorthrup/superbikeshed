package borg.trikeshed.io

import kotlinx.coroutines.flow.Flow

actual class CCekEngine {
    actual fun initialize(compressionLevel: Int, bufferSize: Int) {
        println("CCekEngine.initialize() not implemented for macosArm64")
    }

    actual fun cleanup() {
        println("CCekEngine.cleanup() not implemented for macosArm64")
    }

    actual suspend fun send(target: String, data: ByteArray): Int {
        println("CCekEngine.send() not implemented for macosArm64")
        return -1
    }

    actual suspend fun <T> sendObject(target: String, obj: T): Int {
        println("CCekEngine.sendObject() not implemented for macosArm64")
        return -1
    }

    actual suspend fun receive(source: String, buffer: ByteArray): Int {
        println("CCekEngine.receive() not implemented for macosArm64")
        return -1
    }

    actual suspend fun <T> receiveObject(source: String): T? {
        println("CCekEngine.receiveObject() not implemented for macosArm64")
        return null
    }

    actual suspend fun broadcast(targets: List<String>, data: ByteArray): List<Int> {
        println("CCekEngine.broadcast() not implemented for macosArm64")
        return emptyList()
    }

    actual fun incomingMessages(): Flow<CCekMessage> {
        throw NotImplementedError("CCekEngine.incomingMessages() not implemented for macosArm64")
    }

    actual fun outgoingMessages(): Flow<CCekMessage> {
        throw NotImplementedError("CCekEngine.outgoingMessages() not implemented for macosArm64")
    }

    actual fun getCompressionStats(): CompressionStats {
        println("CCekEngine.getCompressionStats() not implemented for macosArm64")
        return CompressionStats()
    }

    actual fun setCompressionAlgorithm(algorithm: CompressionAlgorithm) {
        println("CCekEngine.setCompressionAlgorithm() not implemented for macosArm64")
    }

    actual companion object {
        actual fun create(): CCekEngine = CCekEngine()
    }
}