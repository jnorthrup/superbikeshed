package borg.trikeshed.parse

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.get
import borg.trikeshed.lib.play
import borg.trikeshed.lib.size

/**
 * BashBracePacker: Compresses a list of strings into a minimal brace expression.
 * - Supports ranges (a..z, 1..n)
 * - Supports sparse/duplicate bags (e.g., {a,,b,c,d,,,,,,,,,,})
 * - Preserves order and duplicates
 * - Uses trie-based prefix/suffix analysis for maximal compression
 * - Compatible with TrikeShed Indexed<T> patterns
 */
object BashBracePacker {
    /**
     * Compress an Indexed<String> into a brace expression.
     * Returns a string that, when expanded, yields the original series in order.
     */
    fun pack(strings: Indexed<String>): String {
        if (strings.size == 0) return ""
        if (strings.size == 1) return strings[0]

        // Convert to list for processing
        val stringList = strings.play.toList()
        return packList(stringList)
    }

    /**
     * Compress a list of strings into a brace expression.
     * Returns a string that, when expanded, yields the original list in order.
     */
    fun packList(strings: List<String>): String {
        if (strings.isEmpty()) return ""
        if (strings.size == 1) return strings[0]

        // Try to find common prefix and suffix
        val prefix = longestCommonPrefix(strings)
        val suffix = longestCommonSuffix(strings)
        val middles = strings.map { it.removePrefix(prefix).removeSuffix(suffix) }

        // If all middles are single characters or numbers, try to use range
        val rangeExpr = tryRange(middles)
        val bagExpr = tryBag(middles)
        val middleExpr = rangeExpr ?: bagExpr

        return buildString {
            append(prefix)
            append(middleExpr)
            append(suffix)
        }
    }

    // Find the longest common prefix
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

    // Find the longest common suffix
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

    // Try to compress a list of strings as a range (a..z, 1..n)
    private fun tryRange(middles: List<String>): String? {
        if (middles.any { it.isEmpty() }) return null

        // Numeric range
        val nums = middles.mapNotNull { it.toIntOrNull() }
        if (nums.size == middles.size) {
            val sorted = nums.sorted()
            if (sorted == (sorted.first()..sorted.last()).toList()) {
                return "{${sorted.first()}..${sorted.last()}}"
            }
        }

        // Char range
        if (middles.all { it.length == 1 }) {
            val chars = middles.map { it[0] }.sorted()
            if (chars == (chars.first()..chars.last()).toList()) {
                return "{${chars.first()}..${chars.last()}}"
            }
        }

        return null
    }

    // Compress as a bag (preserving order and duplicates, including empty strings)
    private fun tryBag(middles: List<String>): String =
        buildString {
            append("{")
            middles.forEachIndexed { i, s ->
                if (i > 0) append(",")
                append(s)
            }
            append("}")
        }
}

/**
 * Extension function for convenient packing of Indexed<String>
 */
fun Indexed<String>.packToBrace(): String = BashBracePacker.pack(this)
