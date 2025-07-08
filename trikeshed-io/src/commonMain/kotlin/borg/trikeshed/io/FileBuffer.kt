@file:OptIn(RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class, kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.io

import borg.trikeshed.lib.*
import kotlin.Function1

interface FileBuffer : LongIndexed<Byte> {
    val path: String
    val size: Long
    
    fun open()
    fun close()
}

// TODO: Platform-specific FileBuffer implementation
/*
expect class FileBuffer(
    filename: String,
    initialOffset: Long,
    blkSize: Long,
    readOnly: Boolean
) : LongIndexed<Byte> {
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
*/ 