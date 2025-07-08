@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.reactor

expect class SelectionKey {
    fun isValid(): Boolean
    fun cancel(): Unit
    fun interestOps(): Int
    fun interestOps(ops: Int): SelectionKey
    fun readyOps(): Int
    fun channel(): Any
    fun selector(): Any
    fun isReadable(): Boolean
    fun isWritable(): Boolean
    fun isConnectable(): Boolean
    fun isAcceptable(): Boolean
    fun attachment(): Any?
    fun attach(ob: Any?): Any?
}