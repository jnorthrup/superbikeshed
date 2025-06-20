package borg.trikeshed.io

expect class MappedFile {
    val size: Long
    val backingStore: Any?

    fun getByte(offset: Long): Byte
    fun setByte(offset: Long, value: Byte)
    fun asByteArray(): ByteArray
    fun close()

    companion object {
        fun map(path: String, size: Long, readOnly: Boolean = false): MappedFile
        fun allocate(size: Long): MappedFile
    }
} 