package borg.trikeshed.lib

import kotlin.test.Test


class SeriesKtTest {

    @Test
    fun testStringToSeries() {
        val s = "hello"
        val series = s.toIdx()
        val s2 = series.asString()
        assert(s == s2)


    }
}