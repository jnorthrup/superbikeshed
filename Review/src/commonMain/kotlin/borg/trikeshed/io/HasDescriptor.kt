package borg.trikeshed.io

expect class stat {
    val st_size: Long
    val st_mode: UInt
}

expect interface HasDescriptor {
    val st_: stat?
    val st: stat
}