package borg.trikeshed.io

import borg.trikeshed.lib.Series
import borg.trikeshed.io.Usable
// Removed due to unresolved reference in commonMain; handle in actual implementations

typealias PosixOffset = Long
typealias PosixStat = Any

expect class PosixFile : Usable {
    val path: String?
    
    fun read(buf: ByteArray): UInt
    fun read64(buf: ByteArray): ULong
    fun write(buf: ByteArray): UInt
    fun write64(buf: ByteArray): ULong
    fun seek(offset: PosixOffset, whence: Int = SEEK_SET_CONSTANT): ULong
    val size: PosixOffset
    fun stat(): PosixStat
    fun mmap(len: ULong, prot: Int, flags: Int, offset: PosixOffset): MappedPointer
    
    companion object {
        fun open(path: String?, flags: Int, mode: Int? = null): PosixFile
    }
}

expect val SEEK_SET_CONSTANT: Int
expect val O_RDONLY_FLAG: Int

expect class MappedPointer {
    fun getByte(index: Long): Byte
    fun putByte(index: Long, value: Byte)
    fun unmap()
}