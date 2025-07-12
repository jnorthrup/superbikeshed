package borg.trikeshed.io

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

actual class IODaemon actual constructor(scope: CoroutineScope) {
    override suspend fun initialize(config: IODaemonConfig) {
        println("IODaemon.initialize() not implemented for macosArm64")
    }

    override suspend fun shutdown() {
        println("IODaemon.shutdown() not implemented for macosArm64")
    }

    override suspend fun submit(operation: IODaemonOperation): IODaemonResult {
        println("IODaemon.submit() not implemented for macosArm64")
        return IODaemonResult(operation.id, -1, -1)
    }

    override suspend fun submitBatch(operations: List<IODaemonOperation>): List<IODaemonResult> {
        println("IODaemon.submitBatch() not implemented for macosArm64")
        return operations.map { IODaemonResult(it.id, -1, -1) }
    }

    override fun completedOperations(): Flow<IODaemonResult> {
        throw NotImplementedError("Flow-based completion not implemented yet for macosArm64")
    }

    override suspend fun getStats(): IODaemonStats {
        println("IODaemon.getStats() not implemented for macosArm64")
        return IODaemonStats(0, 0, 0, 0, 0.0, 0, 0)
    }

    actual companion object {
        actual fun create(scope: CoroutineScope): IODaemon = IODaemon(scope)
    }
}