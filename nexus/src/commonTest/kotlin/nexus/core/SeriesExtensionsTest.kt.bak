package nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking // Not strictly needed if extensions are not suspend, but good for consistency

// TrikeShed core types and helpers
import borg.trikeshed.core.Series
import borg.trikeshed.core.Join
import borg.trikeshed.core.j // For Join infix constructor
import borg.trikeshed.core.seriesOf
import borg.trikeshed.core.emptySeries
import borg.trikeshed.core.materialize

// TrikeShed Assertions (assuming they are in this package or accessible)
import borg.trikeshed.core.shouldHaveSize
import borg.trikeshed.core.shouldBe
import borg.trikeshed.core.elementAtShouldBe // May not be needed if using shouldBe

// Types from nexus.core (for Score, Pattern, Confidence if used directly in tests)
import nexus.core.Score

// The extension functions being tested (auto-imported by package)
// No explicit import needed if test file is in the same package `nexus.core`
// or if using fully qualified names (e.g. nexus.core.toSeries())

class SeriesExtensionsTest {

    @Test
    fun `list toSeries conversion`() {
        val list = listOf(1, 2, 3)
        val series = list.toSeries()

        series.shouldHaveSize(3)
        assertEquals(1, series.elementAt(0))
        assertEquals(2, series.elementAt(1))
        assertEquals(3, series.elementAt(2))

        val emptyList = emptyList<Int>()
        val emptySeriesConverted = emptyList.toSeries()
        emptySeriesConverted.shouldHaveSize(0)
    }

    @Test
    fun `best element in series`() {
        val series1 = seriesOf(3, 1, 4, 1, 5, 9, 2, 6)
        assertEquals(9, series1.best())

        val series2 = seriesOf(-1, -5, -2)
        assertEquals(-1, series2.best())

        val seriesStr = seriesOf("apple", "zebra", "banana")
        assertEquals("zebra", seriesStr.best())

        val emptyIntSeries = emptySeries<Int>()
        assertNull(emptyIntSeries.best(), "Best of empty series should be null")
    }

    @Test
    fun `scoreWith and rankByScore`() {
        val items = seriesOf("apple", "banana", "cherry", "date")

        // Scorer: length of the string as score
        val scorer: (String) -> Score = { it.length.toDouble() }
        val scoredSeries = items.scoreWith(scorer)

        scoredSeries.shouldHaveSize(4)
        // Example: "apple" score is 5.0. So, 5.0 j "apple"
        val expectedScoredApple = 5.0 j "apple"
        // This check is a bit fragile due to potential floating point issues if scores were complex.
        // And it relies on the order from map, which is usually preserved.
        assertEquals(expectedScoredApple, scoredSeries.elementAt(0))
        assertEquals(6.0 j "banana", scoredSeries.elementAt(1))
        assertEquals(6.0 j "cherry", scoredSeries.elementAt(2)) // Note: banana and cherry have same score
        assertEquals(4.0 j "date", scoredSeries.elementAt(3))

        val rankedSeries = scoredSeries.rankByScore()
        rankedSeries.shouldHaveSize(4)

        // Expected order after ranking (descending by score)
        // banana (6.0), cherry (6.0) can be in any order relative to each other.
        // apple (5.0)
        // date (4.0)

        val firstRanked = rankedSeries.elementAt(0)
        val secondRanked = rankedSeries.elementAt(1)

        assertTrue(
            (firstRanked == (6.0 j "banana") && secondRanked == (6.0 j "cherry")) ||
            (firstRanked == (6.0 j "cherry") && secondRanked == (6.0 j "banana")),
            "Top two elements are not banana and cherry with score 6.0"
        )
        assertEquals(5.0 j "apple", rankedSeries.elementAt(2))
        assertEquals(4.0 j "date", rankedSeries.elementAt(3))

        // Test with empty series
        val emptyItems = emptySeries<String>()
        val emptyScored = emptyItems.scoreWith(scorer)
        emptyScored.shouldHaveSize(0)
        val emptyRanked = emptyScored.rankByScore()
        emptyRanked.shouldHaveSize(0)
    }

    @Test
    fun `take elements from series`() {
        val series = seriesOf(10, 20, 30, 40, 50)

        val take2 = series.take(2)
        take2.shouldHaveSize(2)
        take2.shouldBe(seriesOf(10, 20))

        val take0 = series.take(0)
        take0.shouldHaveSize(0)
        assertTrue(take0.materialize().toList().isEmpty(), "Taking 0 should result in an empty series")


        val takeNegative = series.take(-1)
        takeNegative.shouldHaveSize(0)
        assertTrue(takeNegative.materialize().toList().isEmpty(), "Taking negative should result in an empty series")


        val takeMoreThanSize = series.take(10)
        takeMoreThanSize.shouldHaveSize(5)
        takeMoreThanSize.shouldBe(series) // Should be all original elements

        val emptyS = emptySeries<Int>()
        emptyS.take(5).shouldHaveSize(0)
    }

    // Minimal test for evolveWith to ensure it compiles and runs
    @Test
    fun `evolveWith applies transformation`() {
        val series = seriesOf(1, 2, 3)
        val evolved = series.evolveWith { it * 2 }
        evolved.shouldHaveSize(3)
        evolved.shouldBe(seriesOf(2, 4, 6))
    }

    // Minimal test for selectTop
    @Test
    fun `selectTop selects correct ratio of elements`() {
        val series = seriesOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10) // size 10
        // selectTop implies order matters or it's pre-sorted. Our take is from the start.
        val top20percent = series.selectTop(0.2) // Should take 2 elements
        top20percent.shouldHaveSize(2)
        top20percent.shouldBe(seriesOf(1, 2))

        val top0percent = series.selectTop(0.0)
        top0percent.shouldHaveSize(0)

        val top100percent = series.selectTop(1.0)
        top100percent.shouldHaveSize(10)
        top100percent.shouldBe(series)
    }
}
