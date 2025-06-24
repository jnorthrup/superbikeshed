package borg.trikeshed.parse

import kotlin.test.Test

class TestBashBracePackerDebug {
    @Test
    fun debugPackListOutput() {
        val strings = listOf("alpha", "beta", "gamma")
        val result = BashBracePacker.packList(strings)

        println("=== BashBracePacker.packList Debug ===")
        println("Input: $strings")
        println("Output: '$result'")
        println("Output length: ${result.length}")
        println("\nCharacter by character:")
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

        // Let's also check what the prefix and suffix detection finds
        val prefix = longestCommonPrefix(strings)
        val suffix = longestCommonSuffix(strings)
        val middles = strings.map { it.removePrefix(prefix).removeSuffix(suffix) }

        println("\nPrefix/Suffix analysis:")
        println("Common prefix: '$prefix'")
        println("Common suffix: '$suffix'")
        println("Middles: $middles")
    }

    // Copy the private methods to test them
    private fun longestCommonPrefix(strings: List<String>): String {
        if (strings.isEmpty()) return ""
        val first = strings[0]
        var end = first.length
        for (i in 1 until strings.size) {
            val s = strings[i]
            var j = 0
            while (j < end && j < s.length && first[j] == s[j]) j++
            end = j
        }
        return first.substring(0, end)
    }

    private fun longestCommonSuffix(strings: List<String>): String {
        if (strings.isEmpty()) return ""
        val first = strings[0]
        var end = first.length
        for (i in 1 until strings.size) {
            val s = strings[i]
            var j = 0
            while (j < end && j < s.length && first[first.length - 1 - j] == s[s.length - 1 - j]) j++
            end = j
        }
        return first.substring(first.length - end)
    }
}
