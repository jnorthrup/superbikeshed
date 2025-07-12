package borg.trikeshed.io

import kotlinx.coroutines.flow.Flow

actual class AsyncIOEngineImpl : AsyncIOEngine {
    override suspend fun initialize() {
        println("AsyncIOEngine.initialize() not implemented for macosArm64")
    }

    override suspend fun cleanup() {
        println("AsyncIOEngine.cleanup() not implemented for macosArm64")
    }

    override suspend fun read(handle: Int, buffer: ByteArray, offset: Long): Int {
        println("AsyncIOEngine.read() not implemented for macosArm64")
        return -1
    }

    override suspend fun write(handle: Int, data: ByteArray, offset: Long): Int {
        println("AsyncIOEngine.write() not implemented for macosArm64")
        return -1
    }

    override fun completedOperations(): Flow<IOResult> {
        throw NotImplementedError("Flow-based completion not implemented yet for macosArm64")
    }
}

actual fun createAsyncIOEngine(): AsyncIOEngine = AsyncIOEngineImpl()