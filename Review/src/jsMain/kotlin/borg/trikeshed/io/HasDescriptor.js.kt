package borg.trikeshed.io

actual class stat {
    actual val st_size: Long
        get() = TODO("Not yet implemented")
    actual val st_mode: UInt
        get() = TODO("Not yet implemented")
}

actual interface HasDescriptor {
    actual val st_: stat?
    actual val st: stat
}