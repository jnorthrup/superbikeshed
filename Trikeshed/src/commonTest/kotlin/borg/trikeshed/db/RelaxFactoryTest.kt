package borg.trikeshed.db

import borg.trikeshed.ccek.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@Serializable
data class TestDoc(val name: String, val value: Int)

class RelaxFactoryTest {

    @Test
    fun testRelaxFactoryCCEK() = runTest {
        // 1. Set up the CCEK services
        val jsonService = JsonServiceImpl()
        val httpClient = FakeHttpClient()
        val relaxFactory = RelaxFactoryImpl()
        val dbContext = CouchDbContext(
            baseUrl = "http://localhost:5984",
            dbName = "test-db"
        )

        // 2. Compose the context
        val context = jsonService + httpClient + relaxFactory + dbContext

        // 3. Test the RelaxFactory operations
        context.run {
            val db = coroutineContext[RelaxFactory.Key]!!
            
            // Test PUT operation
            val testDoc = TestDoc("test", 42)
            val putResponse = db.put("test-doc", testDoc, serializer<TestDoc>())
            assertEquals(true, putResponse.ok)
            assertEquals("test-doc", putResponse.id)
            
            // Test GET operation
            val fetchedDoc = db.get("test-doc", serializer<TestDoc>())
            assertNotNull(fetchedDoc)
            assertEquals("Trike", fetchedDoc.name) // FakeHttpClient returns this
            assertEquals(3, fetchedDoc.value) // FakeHttpClient returns value=3
            
            // Test EXISTS operation
            val exists = db.exists("test-doc")
            assertEquals(true, exists)
            
            // Test INFO operation
            val info = db.info()
            assertNotNull(info)
        }
    }
} 