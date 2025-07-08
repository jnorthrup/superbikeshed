package debug

import borg.trikeshed.parse.BashBracePacker

fun main() {
    val strings = listOf("alpha", "beta", "gamma")
    val result = BashBracePacker.packList(strings)

    println("Input: $strings")
    println("Output: '$result'")
    println("Output length: ${result.length}")
    println("Character by character:")
    result.forEachIndexed { index, char ->
        println("  [$index] = '$char' (code: ${char.code})")
    }

    println("\nChecking for expected content:")
    println("Contains '{': ${result.contains("{")}")
    println("Contains '}': ${result.contains("}")}")
    println("Contains ',': ${result.contains(",")}")
    println("Contains 'alpha': ${result.contains("alpha")}")
    println("Contains 'beta': ${result.contains("beta")}")
    println("Contains 'gamma': ${result.contains("gamma")}")
}
