package borg.trikeshed.core // Assuming it's in the same package for easy access to Series/Tensor internals if needed, or adjust imports.

import kotlin.test.assertEquals // For assertEquals under the hood
import kotlin.test.assertTrue // For assertTrue under the hood
import kotlin.test.fail // For custom failure messages

// Imports for Series and Tensor from TrikeShedCore.kt
// These might need to be adjusted based on the actual package structure if borg.trikeshed.core is not directly accessible
// For now, assume they are accessible directly or via borg.trikeshed.core.*

fun <T> Series<T>.shouldHaveSize(expectedSize: Int) {
    assertEquals(expectedSize, size, "Expected size $expectedSize but was $size.")
}

fun <T> Series<T>.shouldBeEmpty() {
    assertTrue(isEmpty(), "Expected series to be empty but it was not.")
}

fun <T> Series<T>.shouldNotBeEmpty() {
    assertTrue(!isEmpty(), "Expected series not to be empty but it was.")
}

fun <T> Series<T>.elementAtShouldBe(index: Int, expectedElement: T) {
    assertEquals(expectedElement, get(index), "Expected element $expectedElement at index $index but was ${get(index)}.")
}

fun <T> Series<T>.shouldBe(expectedSeries: Series<T>, elementCompare: (T, T) -> Boolean = { a, b -> a == b }) {
    shouldHaveSize(expectedSeries.size)
    for (i in 0 until size) {
        if (!elementCompare(get(i), expectedSeries.get(i))) {
            fail("Series elements differ at index $i. Expected ${expectedSeries.get(i)} but was ${get(i)}.")
        }
    }
}

fun <T> Tensor<T>.shouldHaveShape(vararg expectedShape: Int) {
    val actualShape = shape
    assertEquals(expectedShape.size, actualShape.size, "Expected shape dimension count ${expectedShape.size} but was ${actualShape.size}.")
    expectedShape.forEachIndexed { index, expectedDim ->
        assertEquals(expectedDim, actualShape[index], "Expected dimension $index to be $expectedDim but was ${actualShape[index]}.")
    }
}

fun <T> Tensor<T>.elementAtShouldBe(vararg coords: Int, expectedElement: T) {
    val actualElement = get(*coords)
    assertEquals(expectedElement, actualElement, "Expected element $expectedElement at coordinates ${coords.joinToString()} but was $actualElement.")
}
