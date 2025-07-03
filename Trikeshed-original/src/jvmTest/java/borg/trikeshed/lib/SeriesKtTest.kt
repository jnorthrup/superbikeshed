package borg.trikeshed.lib

import borg.trikeshed.lib.Series as Indexed
import kotlin.test.Test


class SeriesKtTest {

    @Test
    fun testStringToIndexed() {
        val s = "hello"
        val series = s.toIndexed()
        val s2 = series.asString()
        assert(s == s2)


    }
}