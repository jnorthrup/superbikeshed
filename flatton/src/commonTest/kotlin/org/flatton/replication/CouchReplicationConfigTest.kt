package org.flatton.replication

import org.flatton.types.*
import org.flatton.client.CouchClient
import borg.trikeshed.lib.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class CouchReplicationConfigTest {
    private lateinit var client: MockCouchClient
    private lateinit var sourceDb: DatabaseName
    private lateinit var targetDb: DatabaseName

    @BeforeTest
    fun setup() {
        client = MockCouchClient()
        sourceDb = DatabaseName("source")
        targetDb = DatabaseName("target")
    }

    @Test
    fun testStartReplication() = runTest {
        val config = ReplicationConfig(
            source = sourceDb,
            target = targetDb,
            continuous = true
        )
        val response = client.startReplication(config)
        assertTrue(response.ok)
        assertNotNull(response.id)
    }

    @Test
    fun testStopReplication() = runTest {
        val config = ReplicationConfig(
            source = sourceDb,
            target = targetDb,
            continuous = true
        )
        val startResponse = client.startReplication(config)
        val stopResponse = client.stopReplication(startResponse.id)
        assertTrue(stopResponse.ok)
    }

    @Test
    fun testGetReplicationStatus() = runTest {
        val config = ReplicationConfig(
            source = sourceDb,
            target = targetDb,
            continuous = true
        )
        val startResponse = client.startReplication(config)
        val status = client.getReplicationStatus(startResponse.id)
        assertTrue(status.ok)
        assertNotNull(status.id)
        assertNotNull(status.history)
    }

    @Test
    fun testFilteredReplication() = runTest {
        val config = ReplicationConfig(
            source = sourceDb,
            target = targetDb,
            continuous = true,
            filter = "app/by_type",
            queryParams = mapOf("type" to "user")
        )
        val response = client.startReplication(config)
        assertTrue(response.ok)
        assertNotNull(response.id)
    }

    @Test
    fun testDocIdsReplication() = runTest {
        val config = ReplicationConfig(
            source = sourceDb,
            target = targetDb,
            continuous = true,
            docIds = Indexed.of(DocumentId("doc1"), DocumentId("doc2"))
        )
        val response = client.startReplication(config)
        assertTrue(response.ok)
        assertNotNull(response.id)
    }

    @Test
    fun testUserContextReplication() = runTest {
        val config = ReplicationConfig(
            source = sourceDb,
            target = targetDb,
            continuous = true,
            userContext = ReplicationUserContext(
                name = "testuser",
                roles = Indexed.of("replicator")
            )
        )
        val response = client.startReplication(config)
        assertTrue(response.ok)
        assertNotNull(response.id)
    }
} 