package borg.trikeshed.nio

import borg.trikeshed.lib.Series

expect interface IPlatformFile : HasDescriptor, HasSize {
    fun read64(buf: ByteArray): ULong
    fun write64(buf: ByteArray): ULong
    fun seek(offset: Long, whence: Int = 0): ULong // SEEK_SET is platform-specific, use 0 for expect

    fun close(): Int

    companion object {
        fun open(path: String?, O_FLAGS: Int): Int
        fun statk(path: String?, stat1: stat = TODO("implement in actual")): stat
        val page_size: Long
        fun namedDirAndFile(file_path: String): List<String>
    }
}