package borg.trikeshed.nio

import borg.trikeshed.native.HasPosixErr
import kotlinx.cinterop.*
import platform.posix.*
import borg.trikeshed.lib.Series
import borg.trikeshed.lib.j

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

        actual fun namedDirAndFile(file_path: String): Series<String> {
            val lastSlash = file_path.lastIndexOf('/')
            val dirName = if (lastSlash == -1) "." else file_path.substring(0, lastSlash).ifEmpty { "/" }
            val fileName = file_path.substring(lastSlash + 1)
            return 2 j { index ->
                when (index) {
                    0 -> dirName
                    1 -> fileName
                    else -> throw IndexOutOfBoundsException("Series index out of bounds for namedDirAndFile")
                }
            }
        }
    }
}
