package borg.trikeshed.reactor

import borg.trikeshed.nio.platformCurrentTimeMillis

typealias Interest = Int

expect fun currentTimeMillis(): Long

sealed class AsyncReaction {
    object Continue : AsyncReaction()
    class Change(val ops: List<Operation>) : AsyncReaction()
    object Close : AsyncReaction()
}

class Operation(val interest: Interest, val action: () -> AsyncReaction?)


fun OP_READ(action: () -> AsyncReaction?): Operation = Operation(1 shl 0, action)
fun OP_WRITE(action: () -> AsyncReaction?): Operation = Operation(1 shl 2, action)
fun OP_CONNECT(action: () -> AsyncReaction?): Operation = Operation(1 shl 3, action)
fun OP_ACCEPT(action: () -> AsyncReaction?): Operation = Operation(1 shl 4, action)