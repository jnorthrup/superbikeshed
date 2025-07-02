package borg.trikeshed.io

expect class PlatformFile {
    constructor(path: String)
    fun exists(): Boolean
    fun isDirectory(): Boolean
    fun readAllBytes(): ByteArray
}