import borg.trikeshed.parse.BashBracePacker

fun main() {
    val strings = listOf("alpha", "beta", "gamma")
    val result = BashBracePacker.packList(strings)
    println("Result: '$result'")
    println("Contains {: ${result.contains("{")}")
    println("Contains }: ${result.contains("}")}")
    println("Contains ,: ${result.contains(",")}")
    println("Contains alpha: ${result.contains("alpha")}")
    println("Contains beta: ${result.contains("beta")}")
    println("Contains gamma: ${result.contains("gamma")}")
}
