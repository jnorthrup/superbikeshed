@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.io

expect fun readLinesSeq(path: String): Sequence<String>
expect fun readLines(path: String): List<String> 