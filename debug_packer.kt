package borg.trikeshed.parse

fun main() {
    val strings = listOf("alpha", "beta", "gamma")
    val result = BashBracePacker.packList(strings)
    println("Input: $strings")
    println("Output: '$result'")
    println("Contains alpha: ${result.contains("alpha")}")
}