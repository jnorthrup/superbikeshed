package borg.trikeshed.io

import borg.trikeshed.native.HasPosixErr
import kotlinx.cinterop.*
import platform.posix.*

actual interface IPlatformFile {
    actual companion object {
        actual fun open(path: String, flags: Int): Int {
            val fd = platform.posix.open(path, flags)
            HasPosixErr.posixRequires(fd > 0) { "open $path failed" }
            return fd
        }

        actual fun statk(path: String?, stat1: stat = stat()): stat {
            val __stat1 = stat1.st_ ?: nativeHeap.alloc<__stat>().also { stat1.st_ = stat(it) }
            stat(path, __stat1.__stat.ptr).also {
                HasPosixErr.posixRequires(it.z) { "statx $path" }
            }
            return stat1
        }
    }
}
