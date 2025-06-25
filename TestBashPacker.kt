import borg.trikeshed.parse.BashBracePacker

fun main() {
    val test1 = listOf("alpha", "beta", "gamma")
    val result1 = BashBracePacker.packList(test1)
    println("Test 1:")
    println("  Input: $test1")
    println("  Output: '$result1'")
    println("  Length: ${result1.length}")

    // Check each character
    result1.forEachIndexed { i, c ->
        println("  [$i] = '$c' (${c.code})")
    }

    println("\nTest 2:")
    val test2 = listOf("pre_a_post", "pre_b_post", "pre_c_post")
    val result2 = BashBracePacker.packList(test2)
    println("  Input: $test2")
    println("  Output: '$result2'")
}
