package fiduciary.hexagon

import kotlin.test.*
import borg.trikeshed.lib.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.take
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * TDD Test Suite for Ingestion Pipeline Components
 * 
 * Based on Fiduciary Omnibus Architecture updated diagram:
 * - Ingester -> Router -> CouchDB Storage
 * - Router -> Dashboard/Analytics
 * - Router -> Attention -> Dashboard
 * - Storage -> CRDT -> Storage
 * - CRDT Stream -> CRDT/Storage/Dashboard
 */
class IngestionRackTest {
    
    @Test
    fun `Ingester should process ingested blobs`() = runTest {
        // Given
        val ingester = IngesterHexagon()
        val blobData = "Test blob content".toByteArray()
        
        // When
        val result = ingester.ingest(blobData, "text/plain")
        
        // Then
        assertNotNull(result)
        assertTrue(result.id.isNotEmpty())
        assertEquals("text/plain", result.contentType)
        assertTrue(result.data.contentEquals(blobData))
        assertEquals(BlobStatus.INGESTED, result.status)
    }
    
    @Test
    fun `Ingester should handle batch ingestion`() = runTest {
        // Given
        val ingester = IngesterHexagon()
        val blobs = listOf(
            "Blob 1".toByteArray(),
            "Blob 2".toByteArray(),
            "Blob 3".toByteArray()
        )
        
        // When
        val results = ingester.ingestBatch(blobs.map { 
            IngestableBlob(it, "text/plain")
        })
        
        // Then
        assertEquals(3, results.size)
        assertTrue(results.all { it.status == BlobStatus.INGESTED })
        assertTrue(results.all { it.id.isNotEmpty() })
    }
    
    @Test
    fun `Router should route ingested blobs`() = runTest {
        // Given
        val router = RouterHexagon()
        val blob = IngestedBlob(
            id = "blob-123",
            data = "Test content".toByteArray(),
            contentType = "text/plain",
            status = BlobStatus.INGESTED
        )
        
        // When
        val routed = router.route(blob)
        
        // Then
        assertNotNull(routed)
        assertEquals(blob.id, routed.blobId)
        assertEquals(RoutingDecision.STORE, routed.decision)
        assertTrue(routed.destinations.isNotEmpty())
    }
    
    @Test
    fun `Router Hexagon should handle routing rules`() = runTest {
        // Given
        val router = RouterHexagon()
        val rule = RoutingRule(
            name = "Large Files",
            condition = { blob -> blob.data.size > 1024 },
            decision = RoutingDecision.ATTENTION,
            priority = 1
        )
        
        // When
        router.addRoutingRule(rule)
        val largeBlobData = ByteArray(2048) { 0x42 }
        val largeBlob = IngestedBlob(
            id = "large-blob",
            data = largeBlobData,
            contentType = "application/octet-stream",
            status = BlobStatus.INGESTED
        )
        
        val routed = router.route(largeBlob)
        
        // Then
        assertEquals(RoutingDecision.ATTENTION, routed.decision)
        assertTrue(routed.destinations.contains("attention"))
    }
    
    @Test
    fun `CouchDB Storage Hexagon should store routed blobs`() = runTest {
        // Given
        val storage = CouchDBStorageHexagon("test-storage")
        val routedBlob = RoutedBlob(
            blobId = "routed-123",
            decision = RoutingDecision.STORE,
            destinations = setOf("storage"),
            metadata = mapOf("priority" to "high")
        )
        
        // When
        val stored = storage.store(routedBlob)
        
        // Then
        assertNotNull(stored)
        assertEquals(routedBlob.blobId, stored.blobId)
        assertEquals(StorageStatus.STORED, stored.status)
        assertTrue(stored.documentId.isNotEmpty())
    }
    
    @Test
    fun `CouchDB Storage Hexagon should support change feeds`() = runTest {
        // Given
        val storage = CouchDBStorageHexagon("changefeed-test")
        val changes = mutableListOf<StorageChange>()
        
        // When
        storage.subscribeToChanges { change ->
            changes.add(change)
        }
        
        val routedBlob = RoutedBlob(
            blobId = "change-blob",
            decision = RoutingDecision.STORE,
            destinations = setOf("storage")
        )
        
        storage.store(routedBlob)
        
        // Then
        assertEquals(1, changes.size)
        assertEquals("change-blob", changes[0].blobId)
        assertEquals(ChangeType.CREATED, changes[0].changeType)
    }
    
    @Test
    fun `Dashboard Analytics Hexagon should process queries`() = runTest {
        // Given
        val dashboard = DashboardAnalyticsHexagon()
        val query = AnalyticsQuery(
            type = QueryType.BLOB_COUNT,
            filters = mapOf("contentType" to "text/plain"),
            timeRange = TimeRange.LAST_24_HOURS
        )
        
        // When
        val result = dashboard.processQuery(query)
        
        // Then
        assertNotNull(result)
        assertEquals(query.type, result.queryType)
        assertTrue(result.data.containsKey("count"))
    }
    
    @Test
    fun `Dashboard Analytics Hexagon should handle user queries`() = runTest {
        // Given
        val dashboard = DashboardAnalyticsHexagon()
        val userQuery = UserQuery(
            userId = "user-123",
            query = "show me all blobs from today",
            context = QueryContext.DASHBOARD
        )
        
        // When
        val response = dashboard.handleUserQuery(userQuery)
        
        // Then
        assertNotNull(response)
        assertEquals(userQuery.userId, response.userId)
        assertEquals(QueryStatus.PROCESSED, response.status)
        assertTrue(response.results.isNotEmpty())
    }
    
    @Test
    fun `Attention Hexagon should track attention events`() = runTest {
        // Given
        val attention = AttentionHexagon()
        val routedBlob = RoutedBlob(
            blobId = "attention-blob",
            decision = RoutingDecision.ATTENTION,
            destinations = setOf("attention")
        )
        
        // When
        attention.processAttention(routedBlob)
        val attentionEvents = attention.getAttentionEvents("attention-blob")
        
        // Then
        assertEquals(1, attentionEvents.size)
        assertEquals("attention-blob", attentionEvents[0].entityId)
        assertTrue(attentionEvents[0].intensity > 0.0)
    }
    
    @Test
    fun `Attention Hexagon should emit attention events to dashboard`() = runTest {
        // Given
        val attention = AttentionHexagon()
        val dashboard = DashboardAnalyticsHexagon()
        val events = mutableListOf<AttentionEvent>()
        
        // When
        attention.subscribeToAttentionEvents { event ->
            events.add(event)
        }
        
        val routedBlob = RoutedBlob(
            blobId = "event-blob",
            decision = RoutingDecision.ATTENTION,
            destinations = setOf("attention")
        )
        
        attention.processAttention(routedBlob)
        
        // Then
        assertEquals(1, events.size)
        assertEquals("event-blob", events[0].entityId)
    }
    
    @Test
    fun `CRDT Hexagon should sync with storage`() = runTest {
        // Given
        val crdt = CRDTHexagon()
        val storage = CouchDBStorageHexagon("crdt-sync")
        
        // When
        crdt.connectToStorage(storage)
        
        val crdtData = CRDTData(
            entityId = "crdt-entity",
            operation = CRDTOperation.ADD,
            value = "test-value"
        )
        
        crdt.applyCRDTOperation(crdtData)
        
        // Then
        val storedDoc = storage.getDocument("crdt-entity")
        assertNotNull(storedDoc)
        assertTrue(storedDoc.data.toString().contains("test-value"))
    }
    
    @Test
    fun `CRDT Stream Hexagon should distribute updates`() = runTest {
        // Given
        val crdtStream = CRDTStreamHexagon()
        val updates = mutableListOf<CRDTUpdate>()
        
        // When
        crdtStream.subscribeToUpdates { update ->
            updates.add(update)
        }
        
        val crdtUpdate = CRDTUpdate(
            entityId = "stream-entity",
            operation = CRDTOperation.ADD,
            value = "stream-value",
            timestamp = System.currentTimeMillis()
        )
        
        crdtStream.publishUpdate(crdtUpdate)
        
        // Then
        assertEquals(1, updates.size)
        assertEquals("stream-entity", updates[0].entityId)
    }
    
    @Test
    fun `CRDT Stream Hexagon should sync to multiple destinations`() = runTest {
        // Given
        val crdtStream = CRDTStreamHexagon()
        val crdt = CRDTHexagon()
        val storage = CouchDBStorageHexagon("multi-sync")
        val dashboard = DashboardAnalyticsHexagon()
        
        // When
        crdtStream.addDestination("crdt", crdt)
        crdtStream.addDestination("storage", storage)
        crdtStream.addDestination("dashboard", dashboard)
        
        val update = CRDTUpdate(
            entityId = "multi-entity",
            operation = CRDTOperation.ADD,
            value = "multi-value",
            timestamp = System.currentTimeMillis()
        )
        
        crdtStream.publishUpdate(update)
        
        // Then
        // Verify update was distributed to all destinations
        val destinations = crdtStream.getActiveDestinations()
        assertEquals(3, destinations.size)
        assertTrue(destinations.contains("crdt"))
        assertTrue(destinations.contains("storage"))
        assertTrue(destinations.contains("dashboard"))
    }
    
    @Test
    fun `Ingestion Rack should support parallel ingestion`() = runTest {
        // Given
        val ingester1 = IngesterHexagon()
        val ingester2 = IngesterHexagon()
        val router = RouterHexagon()
        
        // When
        val batch1 = listOf(
            IngestableBlob("Batch 1 Item 1".toByteArray(), "text/plain"),
            IngestableBlob("Batch 1 Item 2".toByteArray(), "text/plain")
        )
        
        val batch2 = listOf(
            IngestableBlob("Batch 2 Item 1".toByteArray(), "text/plain"),
            IngestableBlob("Batch 2 Item 2".toByteArray(), "text/plain")
        )
        
        val results1 = ingester1.ingestBatch(batch1)
        val results2 = ingester2.ingestBatch(batch2)
        
        // Route all results
        val routed1 = results1.map { router.route(it) }
        val routed2 = results2.map { router.route(it) }
        
        // Then
        assertEquals(2, results1.size)
        assertEquals(2, results2.size)
        assertEquals(2, routed1.size)
        assertEquals(2, routed2.size)
        
        assertTrue(routed1.all { it.decision == RoutingDecision.STORE })
        assertTrue(routed2.all { it.decision == RoutingDecision.STORE })
    }
    
    @Test
    fun `Full Ingestion Rack flow should work end-to-end`() = runTest {
        // Given
        val ingester = IngesterHexagon()
        val router = RouterHexagon()
        val storage = CouchDBStorageHexagon("e2e-test")
        val dashboard = DashboardAnalyticsHexagon()
        val attention = AttentionHexagon()
        val crdt = CRDTHexagon()
        
        val changes = mutableListOf<StorageChange>()
        val attentionEvents = mutableListOf<AttentionEvent>()
        
        // When
        storage.subscribeToChanges { changes.add(it) }
        attention.subscribeToAttentionEvents { attentionEvents.add(it) }
        crdt.connectToStorage(storage)
        
        // Add routing rule for attention
        router.addRoutingRule(RoutingRule(
            name = "Important Content",
            condition = { blob -> blob.data.toString().contains("important") },
            decision = RoutingDecision.ATTENTION,
            priority = 1
        ))
        
        // Ingest blob
        val blob = ingester.ingest("This is important content".toByteArray(), "text/plain")
        
        // Route blob
        val routed = router.route(blob)
        
        // Store blob
        val stored = storage.store(routed)
        
        // Process attention
        if (routed.decision == RoutingDecision.ATTENTION) {
            attention.processAttention(routed)
        }
        
        // Query dashboard
        val query = AnalyticsQuery(
            type = QueryType.BLOB_COUNT,
            filters = mapOf("status" to "STORED")
        )
        val analytics = dashboard.processQuery(query)
        
        // Then
        assertEquals(BlobStatus.INGESTED, blob.status)
        assertEquals(RoutingDecision.ATTENTION, routed.decision)
        assertEquals(StorageStatus.STORED, stored.status)
        assertEquals(1, changes.size)
        assertEquals(1, attentionEvents.size)
        assertTrue(analytics.data.containsKey("count"))
    }
}