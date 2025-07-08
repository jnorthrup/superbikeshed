package fiduciary
// ... existing code from moneyfan.fiduciary.FiduciaryCore.kt, with package and imports updated as needed ... 

object FiduciaryEncryption {
    fun rot13(input: String): String = input.map {
        when (it) {
            in 'A'..'Z' -> 'A' + (it - 'A' + 13) % 26
            in 'a'..'z' -> 'a' + (it - 'a' + 13) % 26
            else -> it
        }
    }.joinToString("")

    fun encrypt(input: String): String = rot13(input)
    fun decrypt(input: String): String = rot13(input)
} 