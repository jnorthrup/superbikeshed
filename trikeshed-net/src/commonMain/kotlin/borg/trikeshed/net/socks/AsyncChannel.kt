package borg.trikeshed.net.socks

import borg.trikeshed.lib.Indexed
import kotlinx.coroutines.channels.Channel

/**
 * AsyncChannel stub for trikeshed-ccek compatibility
 */
interface AsyncChannel {
    val completions: Channel<Any>
    val fd: Int
    val isOpen: Boolean
    val localAddress: String
    val remoteAddress: String
    
    suspend fun readBatch(buffers: Indexed<ByteArray>): Indexed<Int>
    suspend fun writeBatch(buffers: Indexed<ByteArray>): Indexed<Int>
    fun close()
    suspend fun submitAndWait(sqeOps: Indexed<SqeOp>): Indexed<Int>
}

/**
 * SqeOp stub
 */
data class SqeOp(
    val opcode: Int,
    val fd: Int,
    val buffer: ByteArray? = null,
    val length: Int = 0,
    val offset: Long = 0
) 