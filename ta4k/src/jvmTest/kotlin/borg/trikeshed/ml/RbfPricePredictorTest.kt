package borg.trikeshed.ml

import borg.trikeshed.util.parseKlineCsv
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import java.io.StringReader

class RbfPricePredictorTest {

    @Test
    fun testFullPredictionPipeline() {
        val sampleCsvData = """
            Open_time,Open,High,Low,Close,Volume,Close_time,Quote_asset_volume,Number_of_trades,Taker_buy_base_asset_volume,Taker_buy_quote_asset_volume
            1672531200000,100.0,105.0,99.0,101.0,1000.0,1672531259999,100500.0,10,500.0,50250.0
            1672531260000,101.0,106.0,100.0,102.0,1200.0,1672531319999,121800.0,12,600.0,60900.0
            1672531320000,102.0,107.0,101.0,103.0,1100.0,1672531379999,112750.0,11,550.0,56375.0
            1672531380000,103.0,108.0,102.0,104.0,1300.0,1672531439999,134550.0,13,650.0,67275.0
            1672531440000,104.0,109.0,103.0,105.0,1400.0,1672531499999,146300.0,14,700.0,73150.0
            1672531500000,105.0,110.0,104.0,106.0,1500.0,1672531559999,158250.0,15,750.0,79125.0
            1672531560000,106.0,111.0,105.0,107.0,1600.0,1672531619999,170400.0,16,800.0,85200.0
            1672531620000,107.0,112.0,106.0,108.0,1700.0,1672531679999,182750.0,17,850.0,91375.0
            1672531680000,108.0,113.0,107.0,109.0,1800.0,1672531739999,195300.0,18,900.0,97650.0
            1672531740000,109.0,114.0,108.0,110.0,1900.0,1672531799999,208050.0,19,950.0,104025.0
            1672531800000,110.0,115.0,109.0,111.0,2000.0,1672531859999,221000.0,20,1000.0,110500.0
            1672531860000,111.0,116.0,110.0,112.0,2100.0,1672531919999,234150.0,21,1050.0,117175.0
        """.trimIndent()

        val stringReader = StringReader(sampleCsvData)
        val klineCursor = parseKlineCsv(stringReader)
        assertNotNull(klineCursor, "Parsed kline cursor should not be null.")
        assertEquals(12, klineCursor.component1().rows, "Should have 12 data rows.")
        assertEquals(11, klineCursor.component2().totalSize, "Should have 11 metadata columns.")


        val closePrices = extractColumnData(klineCursor, "Close")
        assertNotNull(closePrices, "Extracted close prices should not be null.")
        assertEquals(klineCursor.component1().rows, closePrices.size, "Close prices array size should match number of rows.")

        val windowSize = 5
        val featuresAndLabels = createSlidingWindowFeatures(closePrices, windowSize)
        assertNotNull(featuresAndLabels, "Features and labels pair should not be null.")
        assertTrue(featuresAndLabels.first.isNotEmpty(), "Features should not be empty.")
        assertTrue(featuresAndLabels.second.isNotEmpty(), "Labels should not be empty.")
        // Expected: 12 rows - 5 windowSize = 7 feature sets
        assertEquals(klineCursor.component1().rows - windowSize, featuresAndLabels.first.size, "Number of feature sets is incorrect.")
        assertEquals(klineCursor.component1().rows - windowSize, featuresAndLabels.second.size, "Number of labels is incorrect.")


        val trainedModel = trainRbfPredictor(featuresAndLabels.first, featuresAndLabels.second, numberOfNeurons = 5)
        assertNotNull(trainedModel, "Trained RBF model should not be null.")

        val lastWindowFeatures = featuresAndLabels.first.last()
        val prediction = predictNext(trainedModel, lastWindowFeatures)
        // We can't easily assert specific prediction value without knowing the exact model behavior or fixing seeds.
        assertTrue(prediction.isFinite(), "Prediction should be a finite number")

        val cursorWithPredictions = addPredictionsToCursor(klineCursor, trainedModel, "Close", windowSize, "TestPrediction")
        assertNotNull(cursorWithPredictions, "Cursor with predictions should not be null.")
        assertEquals(klineCursor.component2().totalSize + 1, cursorWithPredictions.component2().totalSize, "Metadata should have one additional column for predictions.")
        assertEquals(klineCursor.component1().rows, cursorWithPredictions.component1().rows, "Number of rows should remain the same.")
        assertEquals(klineCursor.component1().cols + 1, cursorWithPredictions.component1().cols, "Data tensor should have one additional column.")

        // Verify that the new column exists in metadata
        val newMeta = cursorWithPredictions.component2()
        var foundPredictionColumn = false
        for (i in 0 until newMeta.totalSize) {
            if (newMeta[i].component1() == "TestPrediction") {
                foundPredictionColumn = true
                break
            }
        }
        assertTrue(foundPredictionColumn, "Prediction column 'TestPrediction' should exist in new metadata.")

        // Verify predictions are properly placed
        val predColIdx = newMeta.totalSize -1
         // The first `windowSize` predictions should be NaN
        for(r in 0 until windowSize) {
            assertTrue(cursorWithPredictions.component1()[r, predColIdx].isNaN(), "Prediction for row $r should be NaN.")
        }
        // The prediction for row `windowSize` should be the first actual number
         assertTrue(!cursorWithPredictions.component1()[windowSize, predColIdx].isNaN(), "Prediction for row $windowSize should not be NaN.")


    }
}
