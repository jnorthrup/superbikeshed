@file:Suppress("EnumEntryName", "unused")

package borg.trikeshed.nio

import borg.trikeshed.lib.*
import kotlin.jvm.*

expect enum class PosixOpenOpts {
    OpenReadOnly,
    O_WrOnly,
    O_Rdwr,
    O_Append,
    O_Async,
    O_Cloexec,
    O_Creat,
    O_Directory,
    O_Dsync,
    O_Noctty,
    NoFollowLinks,
    O_Nonblock,
    O_Ndelay,
    OpenSync,
    O_Trunc,
    ;

    val posixConst: Int
    val i: Int
    val ui: UInt
    val l: Long
    val ul: ULong

    companion object {
        fun fromInt(features: UInt): List<Pair<PosixOpenOpts, UInt>>
        fun withFlags(vararg opts: PosixOpenOpts): UInt
    }
}