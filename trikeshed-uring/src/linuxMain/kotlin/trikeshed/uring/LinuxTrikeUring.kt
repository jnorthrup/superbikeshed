package trikeshed.uring

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlin.coroutines.CoroutineContext

actual fun createTrikeUring(
    context: CoroutineContext,
    ringSize: Int,
    flags: Int
): TrikeUring = LinuxTrikeUring(context, ringSize, flags)

class LinuxTrikeUring(
    private val context: CoroutineContext,
    private val ringSize: Int,
    private val flags: Int
) : TrikeUring {
    
    private val scope = CoroutineScope(context + SupervisorJob())
    private val service = createUringService()
    private val uringContext = service.createContext(ringSize, flags)
    
    override val submission = Channel<Sqe>(Channel.UNLIMITED)
    
    override val completion: Flow<Cqe> = flow {
        while (scope.isActive) {
            // Poll for completions
            val completions = service.pollCompletions(uringContext, 16, 10)
            
            completions.forEach { completion ->
                val cqe = when (pendingOps[completion.userData]) {
                    is Read, is ReadV -> ReadResult(completion.userData, completion.result)
                    is Write, is WriteV -> WriteResult(completion.userData, completion.result)
                    is Fsync -> FsyncResult(completion.userData, completion.result)
                    is Accept -> AcceptResult(completion.userData, completion.result)
                    is Connect -> ConnectResult(completion.userData, completion.result)
                    is Send -> SendResult(completion.userData, completion.result)
                    is Receive -> ReceiveResult(completion.userData, completion.result)
                    is Close -> CloseResult(completion.userData, completion.result)
                    else -> null
                }
                
                cqe?.let { emit(it) }
                pendingOps.remove(completion.userData)
            }
            
            delay(1) // Small delay to prevent busy waiting
        }
    }.flowOn(Dispatchers.IO).shareIn(scope, SharingStarted.Lazily)
    
    private val pendingOps = mutableMapOf<Long, Sqe>()
    
    init {
        // Start submission handler
        scope.launch(Dispatchers.IO) {
            for (sqe in submission) {
                when (sqe) {
                    is LinkedSqe -> submitLinkedOperation(sqe)
                    else -> submitSingleOperation(sqe)
                }
            }
        }
    }
    
    private suspend fun submitSingleOperation(sqe: Sqe) {
        pendingOps[sqe.userData] = sqe
        val operations = listOf(sqeToIoOperation(sqe))
        service.submitOperations(uringContext, operations)
    }
    
    private suspend fun submitLinkedOperation(linked: LinkedSqe) {
        // Submit first operation
        submitSingleOperation(linked.first)
        
        // Wait for completion
        completion.first { it.userData == linked.first.userData }.let { firstResult ->
            // Execute continuation
            linked.then(firstResult)?.let { nextOp ->
                submission.send(nextOp)
            }
        }
    }
    
    private fun sqeToIoOperation(sqe: Sqe): IoOperation = when (sqe) {
        is Read -> sqe
        is ReadV -> sqe
        is Write -> sqe
        is WriteV -> sqe
        is Fsync -> sqe
        is Accept -> sqe
        is Connect -> throw NotImplementedError("Connect requires address conversion")
        is Send -> sqe
        is Receive -> sqe
        is Close -> sqe
        is LinkedSqe -> sqeToIoOperation(sqe.first)
    }
    
    override suspend fun registerBuffers(buffers: List<ByteArray>) {
        // TODO: Implement buffer registration
    }
    
    override suspend fun registerFiles(fds: IntArray) {
        // TODO: Implement file registration
    }
    
    override suspend fun submitBatch(operations: List<Sqe>) {
        operations.forEach { submission.send(it) }
    }
    
    override fun close() {
        scope.cancel()
        service.destroyContext(uringContext)
        submission.close()
    }
}

// Linux-specific SocketAddress implementation
actual class SocketAddress(
    val family: Int,
    val address: ByteArray,
    val port: Int
)