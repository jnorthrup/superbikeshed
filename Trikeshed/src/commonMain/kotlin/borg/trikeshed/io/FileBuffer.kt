package borg.trikeshed.io

import borg.trikeshed.lib.LongSeries
import borg.trikeshed.lib.Usable
import kotlin.Function1

interface FileBuffer : LongSeries, Usable {
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
) : LongSeries<Byte>, Usable {
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