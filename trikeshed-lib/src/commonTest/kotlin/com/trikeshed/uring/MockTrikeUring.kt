package com.trikeshed.uring

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

/**
 * A mock implementation of [TrikeUring] for testing purposes.
 * It allows direct control over submitted SQEs and emitted CQEs.
 */
class MockTrikeUring(override val coroutineContext: CoroutineScope) : TrikeUring {

    private val _submissionChannel = Channel<Sqe>(Channel.UNLIMITED)
    override val submission: SendChannel<Sqe> = _submissionChannel

    private val _completionChannel = Channel<Cqe>(Channel.UNLIMITED)
    override val completion: Flow<Cqe> = _completionChannel.receiveAsFlow()

    val submittedSqe: MutableList<Sqe> = mutableListOf()

    init {
        coroutineContext.launch {
            for (sqe in _submissionChannel) {
                submittedSqe.add(sqe)
                // In a real mock, you might process the SQE and emit a CQE here.
                // For now, we just collect them.
            }
        }
    }

    override suspend fun registerBuffers(buffers: List<ByteBuffer>) {
        // No-op for mock
    }

    override suspend fun registerFiles(fds: IntArray) {
        // No-op for mock
    }

    /**
     * Emits a [Cqe] to the completion flow, simulating a completed operation.
     */
    suspend fun emitCqe(cqe: Cqe) {
        _completionChannel.send(cqe)
    }

    override fun close() {
        _submissionChannel.close()
        _completionChannel.close()
    }
}
