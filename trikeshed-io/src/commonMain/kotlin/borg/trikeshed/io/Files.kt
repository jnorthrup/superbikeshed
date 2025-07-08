@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.io

import borg.trikeshed.lib.Join
import borg.trikeshed.lib.Indexed

expect object Files {
    fun readAllLines(path: String): List<String>
    fun readAllBytes(path: String): ByteArray
    fun readString(path: String): String
    fun write(path: String, content: ByteArray)
    fun write(path: String, lines: List<String>)
    fun write(path: String, string: String)
    fun exists(path: String): Boolean
    fun cwd(): String
    fun streamLines(fileName: String, bufsize: Int): Sequence<Join<Long, ByteArray>>
    fun iterateLines(fileName: String, bufsize: Int): Iterable<Join<Long, Indexed<Byte>>>
    fun delete(path: String)
    fun readLinesSeq(path: String): Sequence<String>
    fun readLines(path: String): List<String>
} 