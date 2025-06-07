@file:Suppress("EnumEntryName", "unused")

package borg.trikeshed.io

import borg.trikeshed.lib.CZero.nz
import borg.trikeshed.lib.* // For Series, Join, j
import platform.posix.*
import platform.posix.uint32_t as __u32
import kotlin.jvm.*

actual enum class PosixOpenOpts(actual val posixConst: Int) {
    OpenReadOnly(O_RDONLY),
    O_WrOnly(O_WRONLY),
    O_Rdwr(O_RDWR),
    O_Append(O_APPEND),
    O_Async(O_ASYNC),
    O_Cloexec(O_CLOEXEC),
    O_Creat(O_CREAT),
    O_Directory(O_DIRECTORY),
    O_Dsync(O_DSYNC),
    O_Noctty(O_NOCTTY),
    NoFollowLinks(O_NOFOLLOW),
    O_Nonblock(O_NONBLOCK),
    O_Ndelay(O_NDELAY),
    OpenSync(O_SYNC),
    O_Trunc(O_TRUNC),
    ;

    actual val i: Int get() = posixConst
    actual val ui: UInt get() = posixConst.toUInt()
    actual val l: Long get() = posixConst.toLong()
    actual val ul: ULong get() = posixConst.toULong()

    actual companion object {
        actual fun fromInt(features: UInt): Series<Join<PosixOpenOpts, UInt>> = values().mapNotNull {
            it.takeIf { (features and it.ui).nz }?.let { opt -> opt j opt.ui }
        }.toSeries() // Assuming .toSeries() extension for List or Array

        actual fun withFlags(vararg opts: PosixOpenOpts): UInt = opts.map(PosixOpenOpts::ui).fold(0u, UInt::or)
    }
}
