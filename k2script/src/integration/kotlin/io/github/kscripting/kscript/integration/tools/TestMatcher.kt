@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
package io.github.kscripting.kscript.integration.tools

import io.github.kscripting.kscript.integration.tools.TestContext.nl
import org.opentest4j.AssertionFailedError

abstract class TestMatcher<T>(internal val expectedValue: T, internal val expressionName: String) {
    abstract fun matches(value: T): Boolean

    fun checkAssertion(assertionName: String, value: T) {
        if (matches(value)) {
            return
        }

        throw AssertionFailedError(
            "$nl$nl$assertionName: expected that value '${
                whitespaceCharsToSymbols(value.toString())
            }' $expressionName '${
                whitespaceCharsToSymbols(expectedValue.toString())
            }'$nl$nl"
        )
    }
}

class GenericEquals<T : Any>(expectedValue: T) : TestMatcher<T>(expectedValue, "is equal to") {
    override fun matches(value: T): Boolean = (value == expectedValue)
}

class AnyMatch : TestMatcher<String>("", "has any value") {
    override fun matches(value: String): Boolean = true
}

class Equals(internal val expectedString: String, internal val ignoreCase: Boolean) :
    TestMatcher<String>(expectedString, "is equal to") {
    override fun matches(value: String): Boolean = value.equals(normalize(expectedString), ignoreCase)
}

class StartsWith(internal val expectedString: String, internal val ignoreCase: Boolean) :
    TestMatcher<String>(expectedString, "starts with") {
    override fun matches(value: String): Boolean = value.startsWith(normalize(expectedString), ignoreCase)
}

class Contains(internal val expectedString: String, internal val ignoreCase: Boolean) :
    TestMatcher<String>(expectedString, "contains") {
    override fun matches(value: String): Boolean = value.contains(normalize(expectedString), ignoreCase)
}

internal fun normalize(string: String) = string.replace("\n", nl)

internal fun whitespaceCharsToSymbols(string: String): String = string.replace("\\", "[bs]").lines().joinToString("[nl]")
