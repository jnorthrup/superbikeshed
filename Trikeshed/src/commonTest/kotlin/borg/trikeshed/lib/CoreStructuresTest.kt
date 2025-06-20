package borg.trikeshed.lib

import kotlin.test.*

class CoreStructuresTest {

    @Test
    fun seriesCreationFromList() {
        val list = listOf(10, 20, 30)
        val series = list.toSeries()
        assertEquals(3, series.a, "Series size should be 3")
        assertEquals(10, series[0], "Element at index 0 should be 10")
        assertEquals(20, series[1], "Element at index 1 should be 20")
        assertEquals(30, series[2], "Element at index 2 should be 30")
    }

    @Test
    fun seriesCreationFromVarargs() {
        val series = _S(1, 2, 3, 4) // Assuming _S is a factory function for Series
        assertEquals(4, series.a, "Series size should be 4")
        assertEquals(1, series[0], "Element at index 0 should be 1")
        assertEquals(4, series[3], "Element at index 3 should be 4")
    }

    @Test
    fun seriesIndexing() {
        val series = _S("a", "b", "c", "d", "e")
        assertEquals("a", series[0])
        assertEquals("c", series[2])
        assertEquals("e", series[4])
    }

    @Test
    fun seriesSize() {
        val series1 = _S(10, 20)
        assertEquals(2, series1.a)

        val series2 = listOf<String>().toSeries()
        assertEquals(0, series2.a)
    }

    // Placeholder for more tests
    // @Test
    // fun seriesMap() { ... }

    // @Test
    // fun seriesFilter() { ... }
}
