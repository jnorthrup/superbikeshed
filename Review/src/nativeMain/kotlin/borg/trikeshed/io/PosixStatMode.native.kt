package borg.trikeshed.io

actual enum class PosixStatMode {
    S_ISREG, S_ISDIR, S_ISCHR, S_ISBLK, S_ISFIFO, S_ISLNK, S_ISSOCK;

    actual val state: UInt
        get() = TODO("Not yet implemented");

    actual operator fun invoke(mode: UInt): Boolean {
        TODO("Not yet implemented")
    };

    actual companion object {
        actual fun __S_ISTYPE(mode: UInt, mask: UInt): Boolean {
            TODO("Not yet implemented")
        }
    }
}