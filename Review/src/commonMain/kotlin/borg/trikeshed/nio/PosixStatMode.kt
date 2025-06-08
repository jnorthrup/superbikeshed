package borg.trikeshed.nio

expect enum class PosixStatMode {
    S_ISREG,
    S_ISDIR,
    S_ISCHR,
    S_ISBLK,
    S_ISFIFO,
    S_ISLNK,
    S_ISSOCK
    ;

    val state: UInt
    operator fun invoke(mode: UInt): Boolean

    companion object {
        fun __S_ISTYPE(mode: UInt, mask: UInt): Boolean
    }
}