package borg.trikeshed.lib

expect fun assert(value: Boolean)
expect fun assert(value: Boolean, lazyMessage: () -> Any)

fun assert(condition: Boolean, message: String = "Assertion failed") {
    if (!condition) throw AssertionError(message)
}

fun assertNotNull(value: Any?, message: String = "Value must not be null") {
    if (value == null) throw AssertionError(message)
}

fun assertTrue(condition: Boolean, message: String = "Condition must be true") {
    if (!condition) throw AssertionError(message)
}

fun assertFalse(condition: Boolean, message: String = "Condition must be false") {
    if (condition) throw AssertionError(message)
}

fun assertEquals(expected: Any?, actual: Any?, message: String = "Values must be equal") {
    if (expected != actual) throw AssertionError("$message: expected $expected but was $actual")
}

fun assertNotEquals(expected: Any?, actual: Any?, message: String = "Values must not be equal") {
    if (expected == actual) throw AssertionError("$message: values should not be equal but both were $actual")
} 