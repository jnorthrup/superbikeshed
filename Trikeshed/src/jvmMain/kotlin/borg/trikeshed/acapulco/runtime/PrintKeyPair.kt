package borg.trikeshed.acapulco.runtime

import org.bereft.runtime.PrintKeyPair.printEncoded

object PrintKeyPair {

    fun printEncoded(encoded: ByteArray): String =
        String(java.util.Base64.getEncoder().encode(encoded)).apply { System.out.println(this) }

}

fun main(args: Array<String>) {
    val keyHandle = KeyHandle()

    keyHandle.pair.private.encoded

    printEncoded(keyHandle.pair.private.encoded)
    printEncoded(
        keyHandle.pair.public.encoded)

}
