package borg.trikeshed.io

import borg.trikeshed.lib.LongIndexed
import borg.trikeshed.lib.Usable

interface FileBuffer : LongIndexed, Usable {
    val path: String
    val size: Long
    
    fun open()
    fun close()
}

expect class FileBuffer(
    filename: String,
    initialOffset: Long,
    blkSize: Long,
    readOnly: Boolean
) : LongIndexed<Byte>, Usable {
    val filename: String
    val initialOffset: Long
    val blkSize: Long
    val readOnly: Boolean
    
    override val a: Long
    override val b: (Long) -> Byte
    
    override fun close()
    override fun open()
    
    fun isOpen(): Boolean
    fun size(): Long
    fun get(index: Long): Byte
    fun put(index: Long, value: Byte)
} 