@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.io

expect class PlatformFile {
    constructor(path: String)
    fun exists(): Boolean
    fun isDirectory(): Boolean
    fun readAllBytes(): ByteArray
}