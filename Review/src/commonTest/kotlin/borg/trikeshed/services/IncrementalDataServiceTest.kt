package borg.trikeshed.services

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull // if needed for some checks, though maybe not for this one
import borg.trikeshed.core.toList // Using the toList extension from IncrementalDataService.kt

class IncrementalDataServiceTest {

    @Test
    fun testGetIncrementalData_initialFetch() {
        IncrementalDataService.resetSampleData()
        IncrementalDataService.addSampleData("item1", 10.0) // ts 1
        IncrementalDataService.addSampleData("item2", 20.0) // ts 2

        val request = IncrementalDataRequest(dataSource = "testSource", sinceTimestamp = null)
        val response = IncrementalDataService.getIncrementalData(request)

        assertEquals(2, response.newData.size, "Should fetch all 2 items for initial fetch")
        assertEquals(2L, response.latestTimestamp, "Latest timestamp should be 2")
        val items = response.newData.toList()
        assertEquals("item1", items[0].id)
        assertEquals("item2", items[1].id)
    }

    @Test
    fun testGetIncrementalData_subsequentFetch() {
        IncrementalDataService.resetSampleData()
        IncrementalDataService.addSampleData("item1", 10.0) // ts 1
        IncrementalDataService.addSampleData("item2", 20.0) // ts 2

        // First fetch (simulated)
        val initialRequest = IncrementalDataRequest(dataSource = "testSource", sinceTimestamp = null)
        val initialResponse = IncrementalDataService.getIncrementalData(initialRequest)
        val timestampAfterFirstFetch = initialResponse.latestTimestamp // Should be 2L

        IncrementalDataService.addSampleData("item3", 30.0) // ts 3
        IncrementalDataService.addSampleData("item4", 40.0) // ts 4

        val subsequentRequest = IncrementalDataRequest(dataSource = "testSource", sinceTimestamp = timestampAfterFirstFetch)
        val subsequentResponse = IncrementalDataService.getIncrementalData(subsequentRequest)

        assertEquals(2, subsequentResponse.newData.size, "Should fetch only the 2 new items")
        assertEquals(4L, subsequentResponse.latestTimestamp, "Latest timestamp should be 4")
        val newItems = subsequentResponse.newData.toList()
        assertEquals("item3", newItems[0].id)
        assertEquals("item4", newItems[1].id)
    }

    @Test
    fun testGetIncrementalData_noNewItems() {
        IncrementalDataService.resetSampleData()
        IncrementalDataService.addSampleData("item1", 10.0) // ts 1
        IncrementalDataService.addSampleData("item2", 20.0) // ts 2

        val request = IncrementalDataRequest(dataSource = "testSource", sinceTimestamp = 2L)
        val response = IncrementalDataService.getIncrementalData(request)

        assertEquals(0, response.newData.size, "Should fetch no new items")
        assertEquals(2L, response.latestTimestamp, "Latest timestamp should remain the same as sinceTimestamp")
    }

    @Test
    fun testGetIncrementalData_emptySource_initialFetch() {
        IncrementalDataService.resetSampleData() // Ensure source is empty

        val request = IncrementalDataRequest(dataSource = "testSource", sinceTimestamp = null)
        val response = IncrementalDataService.getIncrementalData(request)

        assertEquals(0, response.newData.size, "Should fetch no items from an empty source")
        assertEquals(0L, response.latestTimestamp, "Latest timestamp should be 0 for empty source and null sinceTimestamp")
    }

    @Test
    fun testGetIncrementalData_emptySource_withSinceTimestamp() {
        IncrementalDataService.resetSampleData() // Ensure source is empty

        val request = IncrementalDataRequest(dataSource = "testSource", sinceTimestamp = 5L)
        val response = IncrementalDataService.getIncrementalData(request)

        assertEquals(0, response.newData.size, "Should fetch no items from an empty source")
        assertEquals(5L, response.latestTimestamp, "Latest timestamp should be the provided sinceTimestamp for empty source")
    }

    @Test
    fun testLatestTimestampCalculation_noNewItems() {
        IncrementalDataService.resetSampleData()
        IncrementalDataService.addSampleData("item1", 10.0) // ts = 1
        val lastKnownTimestamp = 1L

        val request = IncrementalDataRequest(dataSource = "testSource", sinceTimestamp = lastKnownTimestamp)
        val response = IncrementalDataService.getIncrementalData(request)

        assertEquals(0, response.newData.size)
        assertEquals(lastKnownTimestamp, response.latestTimestamp, "If no new data, latestTimestamp should be sinceTimestamp")
    }

    @Test
    fun testLatestTimestampCalculation_newItems() {
        IncrementalDataService.resetSampleData()
        IncrementalDataService.addSampleData("item1", 10.0) // ts = 1
        IncrementalDataService.addSampleData("item2", 20.0) // ts = 2 (latest)

        val request = IncrementalDataRequest(dataSource = "testSource", sinceTimestamp = 0L)
        val response = IncrementalDataService.getIncrementalData(request)

        assertEquals(2, response.newData.size)
        assertEquals(2L, response.latestTimestamp, "latestTimestamp should be the timestamp of the newest item")
    }
}
