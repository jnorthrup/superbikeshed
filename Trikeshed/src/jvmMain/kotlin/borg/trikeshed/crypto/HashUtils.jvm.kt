package borg.trikeshed.crypto

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.toIndexed
import java.security.MessageDigest

actual object HashUtils {
    actual suspend fun sha256(input: Indexed<Byte>): Indexed<Byte> {
        val digest = MessageDigest.getInstance("SHA-256")
        // Materialize Indexed<Byte> to ByteArray for MessageDigest
        // This could be optimized for very large/lazy Indexed<Byte> by streaming updates to MessageDigest
        val inputArray = ByteArray(input.size) { i -> input[i] }
        val hashBytes = digest.digest(inputArray)
        return hashBytes.toIndexed()
    }
}
