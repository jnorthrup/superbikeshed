package borg.trikeshed.nio

import platform.posix.stat as __stat
import kotlinx.cinterop.CPointer // Corrected import for CPointer
import kotlinx.cinterop.convert

actual class stat(val __stat: CPointer<__stat>) {
    actual val st_size: Long get() = __stat.pointed.st_size
    actual val st_mode: UInt get() = __stat.pointed.st_mode.convert()
}

actual interface HasDescriptor {
    actual val st_: CPointer<__stat>?
    actual val st: stat
}