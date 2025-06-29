package borg.trikeshed.reactor

expect class SelectionKey {
    fun isValid(): Boolean
    fun cancel()
    fun interestOps(): Int
    fun interestOps(ops: Int): SelectionKey
    fun readyOps(): Int
}