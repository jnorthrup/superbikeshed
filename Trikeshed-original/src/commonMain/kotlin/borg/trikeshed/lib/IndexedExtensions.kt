package borg.trikeshed.lib

fun Indexed<Byte>.toByteArray(): ByteArray {
    val array = ByteArray(this.a)
    for (i in 0 until this.a) {
        array[i] = this.b(i)
    }
    return array
}