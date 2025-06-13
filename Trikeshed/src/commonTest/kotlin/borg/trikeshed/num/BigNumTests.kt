package borg.trikeshed.num

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.DecimalMode
import com.ionspin.kotlin.bignum.decimal.RoundingMode
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BigNumTests {

    @Test
    fun testBigIntegerConstruction() {
        val fromString = BigInteger.parseString("12345678901234567890")
        val fromLong = BigInteger.fromLong(1234567890L)
        val fromInt = BigInteger.fromInt(12345)

        assertEquals("12345678901234567890", fromString.toString())
        assertEquals("1234567890", fromLong.toString())
        assertEquals("12345", fromInt.toString())
    }

    @Test
    fun testBigIntegerArithmetic() {
        val a = BigInteger.parseString("10000000000000000000") // 10^19
        val b = BigInteger.parseString("20000000000000000000") // 2 * 10^19
        val c = BigInteger.fromInt(2)

        assertEquals(BigInteger.parseString("30000000000000000000"), a + b)
        assertEquals(BigInteger.parseString("-10000000000000000000"), a - b)
        assertEquals(BigInteger.parseString("200000000000000000000000000000000000000"), a * b) // 2 * 10^38
        assertEquals(BigInteger.parseString("5000000000000000000"), a / c) // 0.5 * 10^19, but integer division
    }

    @Test
    fun testBigIntegerComparison() {
        val a = BigInteger.fromInt(100)
        val b = BigInteger.fromInt(200)
        val c = BigInteger.fromInt(100)

        assertTrue(a < b)
        assertTrue(b > a)
        assertTrue(a == c)
        assertTrue(a <= c)
        assertTrue(a >= c)
    }

    @Test
    fun testBigDecimalConstruction() {
        val fromString = BigDecimal.parseString("12345.6789")
        val fromDouble = BigDecimal.fromDouble(123.45)
        // Note: Precision can be tricky with Double direct conversion. For exactness, String is better.
        // Let's check string representation for stability in test

        assertEquals("1.23456789E+4", fromString.toString()) // Default scientific notation
        assertEquals("12345.6789", fromString.toStringExpanded())

        // Comparing double conversion can be tricky due to inherent double precision issues.
        // We'll check it's roughly correct.
        assertTrue(BigDecimal.fromDouble(123.45).compareTo(BigDecimal.parseString("123.45")) == 0)
    }

    @Test
    fun testBigDecimalArithmetic() {
        val a = BigDecimal.parseString("100.50")
        val b = BigDecimal.parseString("2.5")
        // Using default DecimalMode (infinite precision, ROUND_HALF_AWAY_FROM_ZERO for division if not specified)
        // However, for tests, it's better to be explicit with DecimalMode for division.
        val preciseDivMode = DecimalMode(precision = 10, roundingMode = RoundingMode.ROUND_HALF_UP)

        assertEquals(BigDecimal.parseString("103.00"), a + b)
        assertEquals(BigDecimal.parseString("98.00"), a - b)
        assertEquals(BigDecimal.parseString("251.250"), a * b) // 100.50 * 2.5 = 251.25

        val expectedDivision = BigDecimal.parseString("40.2")
        assertEquals(expectedDivision, a.divide(b, preciseDivMode))
    }

    @Test
    fun testBigDecimalComparison() {
        val a = BigDecimal.parseString("100.123")
        val b = BigDecimal.parseString("200.456")
        val c = BigDecimal.parseString("100.123")

        assertTrue(a < b)
        assertTrue(b > a)
        assertTrue(a.compareTo(c) == 0) // For BigDecimal, use compareTo for equality check regarding scale
        assertTrue(a <= c)
        assertTrue(a >= c)
    }

    @Test
    fun testBigDecimalScaleAndPrecision() {
        val numStr = "123.4567"
        val bd = BigDecimal.parseString(numStr)
        assertEquals(4, bd.scale()) // Number of digits after decimal point
        assertEquals(8, bd.precision()) // Total number of significant digits

        val dm = DecimalMode(decimalPrecision = 5, roundingMode = RoundingMode.ROUND_HALF_UP)
        val rounded = bd.round(dm)
        // 123.4567 rounded to 5 significant digits with ROUND_HALF_UP -> 123.46
        assertEquals("1.2346E+2", rounded.toString())
        assertEquals("123.46", rounded.toStringExpanded())
    }
}
