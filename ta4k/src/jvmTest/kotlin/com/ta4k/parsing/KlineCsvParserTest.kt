package com.ta4k.parsing

import com.ta4k.core.model.Kline // Assuming Kline is accessible
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.StringReader
import java.math.BigDecimal

class KlineCsvParserTest {

    internal val validHeader = "Open_time,Open,High,Low,Close,Volume,Close_time,Quote_asset_volume,Number_of_trades,Taker_buy_base_asset_volume,Taker_buy_quote_asset_volume,Ignore"
    internal val validDataLine1 = "1609459200000,100.0,110.0,90.0,105.0,1000.0,1609459259999,100500.0,50,600.0,60300.0,0"
    internal val validDataLine2 = "1609459260000,105.0,115.0,95.0,110.0,1200.0,1609459319999,126000.0,60,700.0,73500.0,0"

    @Test
    fun `parse valid CSV with header and multiple data lines`() {
        val csvContent = listOf(validHeader, validDataLine1, validDataLine2).joinToString("\n")
        val reader = StringReader(csvContent)
        val result = KlineCsvParser.parse(reader)

        assertTrue(result is KlineCsvParser.ParseResult.Success, "Parsing should be successful. Errors: ${(result as? KlineCsvParser.ParseResult.Failure)?.errors}")
        val klines = (result as KlineCsvParser.ParseResult.Success).klines
        assertEquals(2, klines.size, "Should parse 2 kline entries")

        // Verify first kline
        assertEquals(1609459200000L, klines[0].openTimeMillis)
        assertEquals(BigDecimal("100.0"), klines[0].openPrice)
        assertEquals(BigDecimal("110.0"), klines[0].highPrice)
        assertEquals(BigDecimal("90.0"), klines[0].lowPrice)
        assertEquals(BigDecimal("105.0"), klines[0].closePrice)
        assertEquals(BigDecimal("1000.0"), klines[0].volume)
        assertEquals(1609459259999L, klines[0].closeTimeMillis)
        assertEquals(BigDecimal("100500.0"), klines[0].quoteAssetVolume)
        assertEquals(50, klines[0].numberOfTrades)
        assertEquals(BigDecimal("600.0"), klines[0].takerBuyBaseAssetVolume)
        assertEquals(BigDecimal("60300.0"), klines[0].takerBuyQuoteAssetVolume)

        // Verify second kline
        assertEquals(1609459260000L, klines[1].openTimeMillis)
        assertEquals(BigDecimal("110.0"), klines[1].closePrice)
    }

    @Test
    fun `parse CSV with only header`() {
        val reader = StringReader(validHeader)
        val result = KlineCsvParser.parse(reader)

        assertTrue(result is KlineCsvParser.ParseResult.Success, "Parsing header-only should be successful. Errors: ${(result as? KlineCsvParser.ParseResult.Failure)?.errors}")
        val klines = (result as KlineCsvParser.ParseResult.Success).klines
        assertTrue(klines.isEmpty(), "Klines list should be empty for header-only CSV")
    }

    @Test
    fun `parse empty CSV`() {
        val reader = StringReader("")
        val result = KlineCsvParser.parse(reader)

        assertTrue(result is KlineCsvParser.ParseResult.Failure, "Parsing empty string should fail")
        val errors = (result as KlineCsvParser.ParseResult.Failure).errors
        assertFalse(errors.isEmpty(), "Errors list should not be empty for empty CSV")
        assertEquals("CSV is empty or could not be read.", errors[0])
    }

    @Test
    fun `parse CSV with blank lines`() {
        val csvContent = listOf(validHeader, validDataLine1, "", validDataLine2).joinToString("\n")
        val reader = StringReader(csvContent)
        val result = KlineCsvParser.parse(reader)
        assertTrue(result is KlineCsvParser.ParseResult.Success, "Parsing should be successful. Errors: ${(result as? KlineCsvParser.ParseResult.Failure)?.errors}")
        assertEquals(2, (result as KlineCsvParser.ParseResult.Success).klines.size)
    }


    @Test
    fun `parse CSV with incorrect header`() {
        val csvContent = listOf("Time,Open,High,Low,Close,Volume", validDataLine1).joinToString("\n")
        val reader = StringReader(csvContent)
        val result = KlineCsvParser.parse(reader, strict = false) // Non-strict

        assertTrue(result is KlineCsvParser.ParseResult.Success, "Parsing should succeed in non-strict mode despite header mismatch. Errors: ${(result as? KlineCsvParser.ParseResult.Failure)?.errors}")
        assertEquals(1, (result as KlineCsvParser.ParseResult.Success).klines.size, "Should parse data line even with header mismatch in non-strict")

        val resultStrict = KlineCsvParser.parse(StringReader(csvContent), strict = true)
        assertTrue(resultStrict is KlineCsvParser.ParseResult.Failure, "Parsing should fail in strict mode with header mismatch")
    }

    @Test
    fun `parse CSV with incorrect header and no data`() {
        val csvContent = "Time,Open,High,Low,Close,Volume"
        val reader = StringReader(csvContent)
        val result = KlineCsvParser.parse(reader, strict = false)

        assertTrue(result is KlineCsvParser.ParseResult.Failure, "Parsing should fail if only incorrect header is present")
        val errors = (result as KlineCsvParser.ParseResult.Failure).errors
        assertTrue(errors.any { it.contains("CSV header does not match expected") })
    }


    @Test
    fun `parse CSV with malformed line - too few fields`() {
        val malformedLine = "1609459200000,100.0,110.0,90.0,105.0" // Missing fields
        val csvContent = listOf(validHeader, malformedLine, validDataLine1).joinToString("\n")
        val reader = StringReader(csvContent)

        val resultNonStrict = KlineCsvParser.parse(reader, strict = false)
        assertTrue(resultNonStrict is KlineCsvParser.ParseResult.Success, "Should be Success in non-strict mode. Errors: ${(resultNonStrict as? KlineCsvParser.ParseResult.Failure)?.errors}")
        assertEquals(1, (resultNonStrict as KlineCsvParser.ParseResult.Success).klines.size, "Should parse valid line, skip malformed")

        val resultStrict = KlineCsvParser.parse(StringReader(csvContent), strict = true)
        assertTrue(resultStrict is KlineCsvParser.ParseResult.Failure, "Should fail in strict mode")
        val errors = (resultStrict as KlineCsvParser.ParseResult.Failure).errors
        assertTrue(errors.any { it.contains("Incorrect number of fields") })
    }

    @Test
    fun `parse CSV with malformed line - non-numeric value`() {
        val malformedLine = "1609459200000,ABC,110.0,90.0,105.0,1000.0,1609459259999,100500.0,50,600.0,60300.0,0"
        val csvContent = listOf(validHeader, malformedLine, validDataLine1).joinToString("\n")
        val reader = StringReader(csvContent)

        val resultNonStrict = KlineCsvParser.parse(reader, strict = false)
        assertTrue(resultNonStrict is KlineCsvParser.ParseResult.Success, "Should be Success in non-strict mode. Errors: ${(resultNonStrict as? KlineCsvParser.ParseResult.Failure)?.errors}")
        assertEquals(1, (resultNonStrict as KlineCsvParser.ParseResult.Success).klines.size, "Should parse valid line, skip malformed")

        val resultStrict = KlineCsvParser.parse(StringReader(csvContent), strict = true)
        assertTrue(resultStrict is KlineCsvParser.ParseResult.Failure, "Should fail in strict mode")
        val errors = (resultStrict as KlineCsvParser.ParseResult.Failure).errors
        assertTrue(errors.any { it.contains("Error parsing number") })
    }

    @Test
    fun `parse CSV with all lines malformed after header`() {
        val malformedLine1 = "1,a,b,c,d,e,f,g,h,i,j,k"
        val malformedLine2 = "2,x,y,z,1,2,3,4,5,6,7,8"
        val csvContent = listOf(validHeader, malformedLine1, malformedLine2).joinToString("\n")
        val reader = StringReader(csvContent)

        val resultNonStrict = KlineCsvParser.parse(reader, strict = false)
        assertTrue(resultNonStrict is KlineCsvParser.ParseResult.Failure, "Should be Failure in non-strict if no valid klines parsed")
        val errorsNonStrict = (resultNonStrict as KlineCsvParser.ParseResult.Failure).errors
        assertEquals(2, errorsNonStrict.size) // Expect errors for line1 and line2

        val resultStrict = KlineCsvParser.parse(StringReader(csvContent), strict = true)
        assertTrue(resultStrict is KlineCsvParser.ParseResult.Failure, "Should be Failure in strict mode")
        val errorsStrict = (resultStrict as KlineCsvParser.ParseResult.Failure).errors
        assertTrue(errorsStrict.isNotEmpty()) // At least one error before stopping
    }
}
