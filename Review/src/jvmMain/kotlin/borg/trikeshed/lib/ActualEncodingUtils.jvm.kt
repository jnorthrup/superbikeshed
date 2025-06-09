package borg.trikeshed.lib

import java.util.Base64
import java.nio.charset.StandardCharsets

actual fun base64Encode(input: String): String {
    return Base64.getEncoder().encodeToString(input.toByteArray(StandardCharsets.UTF_8))
}

actual fun base64Encode(input: ByteArray): String {
    return Base64.getEncoder().encodeToString(input)
}
