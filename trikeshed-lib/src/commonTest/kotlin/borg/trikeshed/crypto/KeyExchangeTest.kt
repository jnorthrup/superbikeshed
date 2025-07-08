package borg.trikeshed.crypto

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals

class KeyExchangeTest {

    @Test
    fun testDHKeyExchange() = runTest {
        val (aliceSecret, bobSecret) = performDHKeyExchange()
        assertContentEquals(aliceSecret, bobSecret)
    }

    @Test
    fun testECDHKeyExchange() = runTest {
        val (aliceSecret, bobSecret) = performECDHKeyExchange()
        assertContentEquals(aliceSecret, bobSecret)
    }
}