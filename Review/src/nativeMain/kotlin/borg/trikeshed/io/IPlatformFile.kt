package borg.trikeshed.io

actual interface IPlatformFile : HasDescriptor, HasSize {
    actual fun read64(buf: ByteArray): ULong
    actual fun write64(buf: ByteArray): ULong
    actual fun seek(offset: Long, whence: Int): ULong
    actual fun close(): Int

    actual companion object {
        actual fun open(path: String?, O_FLAGS: Int): Int {
            // Minimal implementation to satisfy expect
            println("IPlatformFile.open($path, $O_FLAGS) (Native placeholder)")
            return -1
        }

        actual fun statk(path: String?, stat1: stat): stat {
            // Minimal implementation to satisfy expect
            println("IPlatformFile.statk($path) (Native placeholder)")
            return stat1
        }

        actual val page_size: Long
            get() = 4096 // Common page size for Linux

        actual fun namedDirAndFile(file_path: String): List<String> {
            val lastSlash = file_path.lastIndexOf('/')
            return if (lastSlash == -1) {
                listOf("", file_path)
            } else {
                listOf(file_path.substring(0, lastSlash), file_path.substring(lastSlash + 1))
            }
        }
    }
}
