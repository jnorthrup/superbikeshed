package borg.trikeshed.core

import kotlin.test.Test
import kotlin.test.assertFailsWith

// Assuming Series and Tensor are in borg.trikeshed.core package
// and assertion functions are also in borg.trikeshed.core
// No specific import for j, α, ▶, TensorSeries, TensorCursor needed if they are top-level in borg.trikeshed.core
// or accessible via borg.trikeshed.core.* which is implicitly available for files in the same package.
// If they are in a subpackage, specific imports would be needed.
// For now, we rely on them being available in borg.trikeshed.core.
// We will need to import the assertion functions we just created.
import borg.trikeshed.core.shouldHaveSize
import borg.trikeshed.core.shouldBeEmpty
import borg.trikeshed.core.shouldNotBeEmpty
import borg.trikeshed.core.elementAtShouldBe
import borg.trikeshed.core.shouldBe
import borg.trikeshed.core.shouldHaveShape
// elementAtShouldBe for Tensor is already imported via the Series import due to the same name,
// but it's good practice to be explicit if needed, though Kotlin might handle it with overloads.
// Let's assume Kotlin's overload resolution will work as expected.
// If not, we might need to alias one of the imports or use fully qualified names.

class TrikeShedAssertionsTest {

    // Dummy Series implementations for testing
    // Replace with actual Series implementations (e.g., TensorSeries) when available
    // For now, using simple list-backed series for testing assertion logic.
    // NOTE: The prompt implies that `TensorSeries` and other core types are available.
    // We will assume `TensorSeries` can be used. If TrikeShedCore.kt is not provided, these tests would fail.
    // Let's try to use `TensorSeries` as per the instructions.
    // We need to ensure `j`, `α`, `▶` are available if used by TensorSeries constructors or operations.
    // The prompt mentions importing `borg.trikeshed.core.*` for these,
    // which should make them available if they are top-level declarations in that package.

    @Test
    fun `series shouldHaveSize`() {
        val series = TensorSeries(3) { it } // Creates a series of size 3: [0, 1, 2]
        series.shouldHaveSize(3)

        assertFailsWith<AssertionError>("Expected test to fail for wrong size") {
            series.shouldHaveSize(2)
        }
    }

    @Test
    fun `series shouldBeEmpty`() {
        val emptySeries = TensorSeries(0) { it }
        emptySeries.shouldBeEmpty()

        val nonEmptySeries = TensorSeries(1) { it }
        assertFailsWith<AssertionError>("Expected test to fail for non-empty series") {
            nonEmptySeries.shouldBeEmpty()
        }
    }

    @Test
    fun `series shouldNotBeEmpty`() {
        val nonEmptySeries = TensorSeries(1) { it }
        nonEmptySeries.shouldNotBeEmpty()

        val emptySeries = TensorSeries(0) { it }
        assertFailsWith<AssertionError>("Expected test to fail for empty series") {
            emptySeries.shouldNotBeEmpty()
        }
    }

    @Test
    fun `series elementAtShouldBe`() {
        val series = TensorSeries(3) { it * 2 } // Series: [0, 2, 4]
        series.elementAtShouldBe(0, 0)
        series.elementAtShouldBe(1, 2)
        series.elementAtShouldBe(2, 4)

        assertFailsWith<AssertionError>("Expected test to fail for wrong element") {
            series.elementAtShouldBe(1, 3) // Expected 2, but checking for 3
        }

        assertFailsWith<IndexOutOfBoundsException>("Expected test to fail for out of bounds index") {
            // Note: The assertion function itself will throw an AssertionError for wrong element.
            // If the underlying get() method throws IndexOutOfBoundsException, that's what we catch.
            // The current implementation of elementAtShouldBe will report an AssertionError
            // if the element doesn't match, not if the index is bad before comparison.
            // However, if series.get(index) itself throws IOBE, then that's what gets propagated.
            // Let's assume series.get() is robust or the test targets element comparison failure.
            // For a direct IOBE, one would call series.get(3) directly in assertFailsWith.
            // The current `elementAtShouldBe` would fail with an AssertionError if the element is wrong,
            // or potentially an IOBE if `get(index)` is called and fails before `assertEquals`.
            // Let's clarify: the primary check of `elementAtShouldBe` is the element value.
            // If `get(index)` throws IOBE, that's a valid failure for this test too.
            series.elementAtShouldBe(3, 0) // Index out of bounds for a series of size 3
        }
    }

    @Test
    fun `series shouldBe`() {
        val series1 = TensorSeries(3) { it } // [0, 1, 2]
        val series2 = TensorSeries(3) { it } // [0, 1, 2]
        val series3 = TensorSeries(3) { it * 2 } // [0, 2, 4]
        val series4 = TensorSeries(2) { it } // [0, 1]

        series1.shouldBe(series2)

        assertFailsWith<AssertionError>("Expected test to fail for different elements") {
            series1.shouldBe(series3)
        }

        assertFailsWith<AssertionError>("Expected test to fail for different sizes") {
            series1.shouldBe(series4)
        }

        // Test with custom comparator
        val seriesA = TensorSeries(2) { it + 1 } // [1, 2]
        // val seriesB = TensorSeries(2) { (it + 1) * 1.0 } // [1.0, 2.0]
        // This won't compile as types are different (Int vs Double) for T in Series<T>
        // For a meaningful custom comparator test, Series types should be compatible or the comparator handles <Any, Any>
        // Let's assume a custom comparison for series of same type but different notion of equality
        val seriesStr1 = TensorSeries(2) { "a$it" } // ["a0", "a1"]
        val seriesStr2 = TensorSeries(2) { "A$it" } // ["A0", "A1"]

        seriesStr1.shouldBe(seriesStr2) { s1, s2 -> s1.equals(s2, ignoreCase = true) }

        assertFailsWith<AssertionError>("Expected custom comparison to fail") {
            seriesStr1.shouldBe(seriesStr2) // Default comparator will fail due to case difference
        }
    }

    // Tensor tests
    @Test
    fun `tensor shouldHaveShape`() {
        // 1D Tensor (effectively a Series from TensorSeries perspective)
        val tensor1D = TensorSeries(3) { it } // Shape [3]
        tensor1D.shouldHaveShape(3)

        assertFailsWith<AssertionError>("Expected test to fail for wrong 1D shape") {
            tensor1D.shouldHaveShape(4)
        }
        assertFailsWith<AssertionError>("Expected test to fail for wrong dimension count") {
            tensor1D.shouldHaveShape(3, 1)
        }

        // 2D Tensor
        val tensor2D = TensorCursor(2, 3) { r, c -> r * 10 + c } // Shape [2, 3]
        tensor2D.shouldHaveShape(2, 3)

        assertFailsWith<AssertionError>("Expected test to fail for wrong 2D shape (dim 0)") {
            tensor2D.shouldHaveShape(3, 3)
        }
        assertFailsWith<AssertionError>("Expected test to fail for wrong 2D shape (dim 1)") {
            tensor2D.shouldHaveShape(2, 4)
        }
        assertFailsWith<AssertionError>("Expected test to fail for wrong dimension count for 2D tensor") {
            tensor2D.shouldHaveShape(2)
        }
    }

    @Test
    fun `tensor elementAtShouldBe`() {
        // 1D Tensor
        val tensor1D = TensorSeries(3) { it * 2 } // [0, 2, 4]
        // For TensorSeries which is a Tensor, elementAtShouldBe uses vararg coords
        tensor1D.elementAtShouldBe(0, expectedElement = 0)
        tensor1D.elementAtShouldBe(1, expectedElement = 2)
        tensor1D.elementAtShouldBe(2, expectedElement = 4)

        assertFailsWith<AssertionError>("Expected 1D tensor test to fail for wrong element") {
            tensor1D.elementAtShouldBe(1, expectedElement = 3)
        }
        assertFailsWith<IndexOutOfBoundsException>("Expected 1D tensor test to fail for out of bounds index") {
            tensor1D.elementAtShouldBe(3, expectedElement = 0)
        }

        // 2D Tensor
        val tensor2D = TensorCursor(2, 2) { r, c -> r * 10 + c } // [[0, 1], [10, 11]]
        tensor2D.elementAtShouldBe(0, 0, expectedElement = 0)
        tensor2D.elementAtShouldBe(0, 1, expectedElement = 1)
        tensor2D.elementAtShouldBe(1, 0, expectedElement = 10)
        tensor2D.elementAtShouldBe(1, 1, expectedElement = 11)

        assertFailsWith<AssertionError>("Expected 2D tensor test to fail for wrong element") {
            tensor2D.elementAtShouldBe(1, 1, expectedElement = 12)
        }
        assertFailsWith<IndexOutOfBoundsException>("Expected 2D tensor test to fail for out of bounds (row)") {
            tensor2D.elementAtShouldBe(2, 0, expectedElement = 0)
        }
        assertFailsWith<IndexOutOfBoundsException>("Expected 2D tensor test to fail for out of bounds (col)") {
            tensor2D.elementAtShouldBe(0, 2, expectedElement = 0)
        }
    }
}
