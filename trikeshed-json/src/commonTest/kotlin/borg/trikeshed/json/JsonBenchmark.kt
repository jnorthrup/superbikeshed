@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.json

import borg.trikeshed.lib.BBCursiveNatural
import borg.trikeshed.lib.json.JsonBbcursive
import kotlin.test.Test
import kotlin.time.measureTime

class JsonBenchmark {

    internal val largeJsonString = generateLargeJsonString(10000)

    @Test
    fun benchmarkJsonBbcursiveParse() {
        val time = measureTime {
            val result = JsonBbcursive.parse(largeJsonString)
            // Assert that parsing was successful, but don't process the value further
            kotlin.test.assertTrue(result != null)
        }
        println("JsonBbcursive.parse (validation only): $time")
    }

    @Test
    fun benchmarkNaturalBBCursiveParseJson() {
        val time = measureTime {
            val result = BBCursiveNatural.parseJson(largeJsonString.encodeToByteArray())
            // Assert that parsing and extraction was successful
            kotlin.test.assertTrue(result != null)
        }
        println("NaturalBBCursive.parseJson (value extraction): $time")
    }

    internal fun generateLargeJsonString(numObjects: Int): String {
        val sb = StringBuilder("[")
        for (i in 0 until numObjects) {
            sb.append("{\"id\":$i,\"name\":\"Item $i\",\"value\":${i * 10.5},\"active\":${i % 2 == 0}}")
            if (i < numObjects - 1) {
                sb.append(",")
            }
        }
        sb.append("]")
        return sb.toString()
    }
}
