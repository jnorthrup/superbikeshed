package borg.trikeshed.nio

actual class stat {
    actual val st_size: Long
        get() = 0L // Placeholder
    actual val st_mode: UInt
        get() = 0U // Placeholder
}

actual interface HasDescriptor {
    actual val st_: stat?
        get() = null // Placeholder
    actual val st: stat
        get() = stat() // Placeholder
}
