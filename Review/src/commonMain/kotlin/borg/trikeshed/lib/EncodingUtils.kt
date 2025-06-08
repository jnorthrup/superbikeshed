package borg.trikeshed.lib

/**
 * Encodes a String into a Base64 encoded String.
 * The input string is typically interpreted as UTF-8 bytes.
 */
expect fun base64Encode(input: String): String

/**
 * Encodes a ByteArray into a Base64 encoded String.
 */
expect fun base64Encode(input: ByteArray): String

/**
 * Decodes a Base64 encoded String into a ByteArray.
 * Optional: Add if needed by other parts of the application, not strictly required by Basic Auth encoding.
 */
// expect fun base64Decode(input: String): ByteArray
