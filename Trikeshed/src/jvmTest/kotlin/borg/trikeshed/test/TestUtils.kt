package borg.trikeshed.test

import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

object TestUtils {
    inline fun <reified T : Any> relaxedMock(): T = mock(defaultAnswer = Mockito.RETURNS_DEFAULTS)
    
    fun <T> wheneverBlocking(block: suspend () -> T) = whenever(runBlocking { block() })
}