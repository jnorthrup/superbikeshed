package borg.trikeshed.lib

import borg.trikeshed.lib.Indexed

typealias ByteIndexed = Indexed<Byte>
typealias IntIndexed = Indexed<Int>

fun ByteArray.toIndexed(): Indexed<Byte> = this.size j { this[it] }
fun IntArray.toIndexed(): Indexed<Int> = this.size j { this[it] } 