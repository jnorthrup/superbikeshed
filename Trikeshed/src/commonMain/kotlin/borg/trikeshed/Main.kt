package borg.trikeshed

import borg.trikeshed.ccek.*
import borg.trikeshed.db.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

@Serializable
data class MyDoc(val name: String, val wheels: Int)

object MainOrchestrator {

    suspend fun run() = coroutineScope {
        println("=== CCEK RelaxFactory Reboot Demo ===")

        // 1. Instantiate all services
        val jsonService = JsonServiceImpl()
        val httpClient = FakeHttpClient() // Use our fake client for the demo
        val relaxFactory = RelaxFactoryImpl()
        
        // 2. Define the context for a specific database
        val myCouchDbContext = CouchDbContext(
            baseUrl = "http://127.0.0.1:5984",
            dbName = "trikeshed-db"
        )

        // 3. Compose the complete application context using the '+' operator.
        // This context now contains everything needed to talk to CouchDB.
        val applicationContext = jsonService + httpClient + relaxFactory + myCouchDbContext

        // 4. Launch a coroutine with the fully composed context to run an operation.
        launch(applicationContext) {
            println("\n--- Simulating a Database Operation ---")
            
            // Get the factory directly from the context.
            val db = coroutineContext[RelaxFactory.Key] ?: error("RelaxFactory not found!")

            // Use the clean, direct API. No request builders needed.
            println("Putting a document into CouchDB...")
            val docToSave = MyDoc("Trike", 3)
            val putResponse = db.put("test-doc", docToSave, serializer<MyDoc>())
            println("CouchDB Response: $putResponse")

            println("\nFetching the document back...")
            val fetchedDoc = db.get("test-doc", serializer<MyDoc>())
            
            if (fetchedDoc != null) {
                println("Successfully fetched: ${fetchedDoc.name} with ${fetchedDoc.wheels} wheels.")
            } else {
                println("Document not found.")
            }

            println("\nChecking if document exists...")
            val exists = db.exists("test-doc")
            println("Document exists: $exists")

            println("\nGetting database info...")
            val info = db.info()
            println("Database info: $info")

        }.join() // Wait for the demo operation to complete

        this.cancel() // Clean up the main scope
    }
}

fun main() {
    println("CCEK RelaxFactory Demo - Use platform-specific main functions to run the demo")
} 