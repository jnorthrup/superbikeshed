package borg.trikeshed.io

actual enum class PosixOpenOpts(actual val posixConst: Int) {
    OpenReadOnly(0x0000), // O_RDONLY equivalent
    O_WrOnly(0x0001),     // O_WRONLY equivalent
    O_Rdwr(0x0002),       // O_RDWR equivalent
    O_Append(0x0008),     // O_APPEND equivalent
    O_Async(0x2000),      // O_ASYNC equivalent
    O_Cloexec(0x80000),   // O_CLOEXEC equivalent
    O_Creat(0x0040),      // O_CREAT equivalent
    O_Directory(0x10000), // O_DIRECTORY equivalent
    O_Dsync(0x1000),      // O_DSYNC equivalent
    O_Noctty(0x0100),     // O_NOCTTY equivalent
    NoFollowLinks(0x20000), // O_NOFOLLOW equivalent
    O_Nonblock(0x0800),   // O_NONBLOCK equivalent
    O_Ndelay(0x0800),     // O_NDELAY equivalent (same as O_NONBLOCK)
    OpenSync(0x0010),     // O_SYNC equivalent
    O_Trunc(0x0200),      // O_TRUNC equivalent
    ;

    actual val i: Int get() = posixConst
    actual val ui: UInt get() = posixConst.toUInt()
    actual val l: Long get() = posixConst.toLong()
    actual val ul: ULong get() = posixConst.toULong()

    actual companion object {
        actual fun fromInt(features: UInt): List<Pair<PosixOpenOpts, UInt>> {
            return values().mapNotNull { opt ->
                if ((features and opt.ui) != 0U) {
                    opt to opt.ui
                } else null
            }
        }

        actual fun withFlags(vararg opts: PosixOpenOpts): UInt {
            return opts.fold(0u) { acc, opt -> acc or opt.ui }
        }
    }
}
