package borg.trikeshed.io

import platform.posix.off_t as __off_t
import kotlinx.cinterop.convert

actual interface HasSize : HasDescriptor {
    actual val size: Long get() = st.st_size
}
