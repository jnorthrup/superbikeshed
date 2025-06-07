package borg.trikeshed.io

actual enum class PosixOpenOpts {
    OpenReadOnly, O_WrOnly, O_Rdwr, O_Append, O_Async, O_Cloexec, O_Creat, O_Directory, O_Dsync, O_Noctty, NoFollowLinks, O_Nonblock, O_Ndelay, OpenSync, O_Trunc;

    actual val posixConst: Int
        get() = TODO("Not yet implemented");

    actual val i: Int
        get() = TODO("Not yet implemented");

    actual val ui: UInt
        get() = TODO("Not yet implemented");

    actual val l: Long
        get() = TODO("Not yet implemented");

    actual val ul: ULong
        get() = TODO("Not yet implemented");

    actual companion object {
        actual fun fromInt(features: UInt): List<Pair<PosixOpenOpts, UInt>> {
            TODO("Not yet implemented")
        }

        actual fun withFlags(vararg opts: PosixOpenOpts): UInt {
            TODO("Not yet implemented")
        }
    }
}