package borg.trikeshed.lib.bitmap

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.launch
// import kotlinx.coroutines.test.runTest

class BitmapTests {

    // NOTE: Coroutine tests disabled - kotlinx.coroutines.test not available
    /*
    @Test
    fun `test set and get`() = runTest {
        val bitmap = Bitmap(10, 10)
        assertFalse(bitmap.get(5, 5))
        bitmap.set(5, 5)
        assertTrue(bitmap.get(5, 5))
    }

    @Test
    fun `test concurrent set`() = runTest {
        val bitmap = Bitmap(100, 100)
        val jobs = List(100) {
            launch {
                for (i in 0 until 100) {
                    bitmap.set(it, i)
                }
            }
        }
        jobs.forEach { it.join() }

        for (x in 0 until 100) {
            for (y in 0 until 100) {
                assertTrue(bitmap.get(x, y))
            }
        }
    }
    */
}
