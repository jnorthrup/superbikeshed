package fiduciary.attention

import kotlin.test.*
import kotlinx.coroutines.runBlocking
import borg.trikeshed.lib.*

/**
 * TDD Tests for Memvid Attention Bridge
 * 
 * Tests the integration between fiduciary attention and memvid video-memory side-pipe.
 * Following TDD principle: write tests first, then implement functionality.
 */

class MemvidAttentionBridgeTest {
    
    // === Test 1: Attention Event Conversion ===
    
    @Test
    fun testAttentionEventConversion() = runBlocking {
        // Given: A fiduciary attention context
        val metadata = object {
            internal val map = mutableMapOf<String, String>()
            fun add(key: String, value: String) { map[key] = value }
            fun getProperty(key: String, default: String): String = map[key] ?: default
        }
        metadata.add("intensity", "0.8")
        
        val parseContext = Any()
        val ioContext = Any()
        val fidContext = FiduciaryContext(ioContext j (metadata j parseContext))
        
        // And: A document attention
        val docAttention = DocumentAttention(100L j 500L, "application/pdf")
        val fiduciaryAttention = FiduciaryAttention(docAttention j fidContext)
        
        // And: A memvid bridge with mock pipe
        val mockPipe = object : MemvidAttentionPipe {
            var registeredEvents = mutableListOf<AttentionEvent>()
            
            override suspend fun registerEvent(event: AttentionEvent): Boolean {
                registeredEvents.add(event)
                return true
            }
            
            override suspend fun getAttentionMemory(): AttentionMemory {
                return AttentionMemory(
                    memoryId = "test-memory",
                    timestamp = System.currentTimeMillis(),
                    attentionMap = emptyMap(),
                    focusHistory = Indexed(0) { AttentionEvent.FiduciaryAction("", "", "", 0L) }
                )
            }
            
            override suspend fun clearMemory(): Boolean = true
            override suspend fun exportMemory(): ByteArray = ByteArray(0)
            override suspend fun importMemory(data: ByteArray): Boolean = true
        }
        
        val bridge = FiduciaryMemvidBridge(mockPipe)
        
        // When: Converting attention to event
        val event = bridge.attentionToEvent(fiduciaryAttention)
        
        // Then: Event should be DocumentFocus type
        assertTrue(event is AttentionEvent.DocumentFocus)
        val docEvent = event as AttentionEvent.DocumentFocus
        
        // And: Event should have correct properties
        assertEquals("100", docEvent.docId)
        assertEquals(100L j 500L, docEvent.range)
        assertEquals(1000L, docEvent.duration) // Default duration
        assertEquals(0.8, docEvent.intensity, 0.01) // From metadata
    }
    
    // === Test 2: Memvid Pipe Integration ===
    
    @Test
    fun testMemvidPipeIntegration() = runBlocking {
        // Given: A mock memvid pipe that tracks calls
        var registerEventCalled = false
        var getMemoryCalled = false
        
        val mockPipe = object : MemvidAttentionPipe {
            override suspend fun registerEvent(event: AttentionEvent): Boolean {
                registerEventCalled = true
                return true
            }
            
            override suspend fun getAttentionMemory(): AttentionMemory {
                getMemoryCalled = true
                return AttentionMemory(
                    memoryId = "test",
                    timestamp = System.currentTimeMillis(),
                    attentionMap = mapOf("doc1" to 0.8),
                    focusHistory = Indexed(0) { AttentionEvent.FiduciaryAction("", "", "", 0L) }
                )
            }
            
            override suspend fun clearMemory(): Boolean = true
            override suspend fun exportMemory(): ByteArray = ByteArray(0)
            override suspend fun importMemory(data: ByteArray): Boolean = true
        }
        
        val bridge = FiduciaryMemvidBridge(mockPipe)
        
        // And: A fiduciary attention
        val metadata = object {
            fun getProperty(key: String, default: String): String = default
        }
        val parseContext = Any()
        val ioContext = Any()
        val fidContext = FiduciaryContext(ioContext j (metadata j parseContext))
        val docAttention = DocumentAttention(0L j 1000L, "text/plain")
        val fiduciaryAttention = FiduciaryAttention(docAttention j fidContext)
        
        // When: Processing attention through bridge
        val result = bridge.processAttention(fiduciaryAttention)
        
        // Then: Should return success
        assertTrue(result)
        
        // And: Should have called memvid pipe methods
        assertTrue(registerEventCalled)
        
        // When: Getting attention memory
        val memory = bridge.getAttentionMemory()
        
        // Then: Should have called get memory
        assertTrue(getMemoryCalled)
        
        // And: Should return valid memory
        assertEquals("test", memory.memoryId)
        assertTrue(memory.attentionMap.containsKey("doc1"))
        assertEquals(0.8, memory.attentionMap["doc1"])
    }
    
    // === Test 3: Attention Stream Creation ===
    
    @Test
    fun testAttentionStreamCreation() = runBlocking {
        // Given: Multiple fiduciary attentions
        val metadata = object {
            fun getProperty(key: String, default: String): String = default
        }
        val parseContext = Any()
        val ioContext = Any()
        val fidContext = FiduciaryContext(ioContext j (metadata j parseContext))
        
        val attentions = arrayOf(
            FiduciaryAttention(DocumentAttention(0L j 100L, "text/plain") j fidContext),
            FiduciaryAttention(DocumentAttention(100L j 200L, "text/plain") j fidContext),
            FiduciaryAttention(DocumentAttention(200L j 300L, "text/plain") j fidContext)
        )
        
        val indexedAttentions = attentions.size j attentions::get
        
        // And: A memvid bridge
        val mockPipe = object : MemvidAttentionPipe {
            override suspend fun registerEvent(event: AttentionEvent): Boolean = true
            override suspend fun getAttentionMemory(): AttentionMemory {
                return AttentionMemory("", 0L, emptyMap(), Indexed(0) { AttentionEvent.FiduciaryAction("", "", "", 0L) })
            }
            override suspend fun clearMemory(): Boolean = true
            override suspend fun exportMemory(): ByteArray = ByteArray(0)
            override suspend fun importMemory(data: ByteArray): Boolean = true
        }
        
        val bridge = FiduciaryMemvidBridge(mockPipe)
        
        // When: Creating attention stream
        val stream = createAttentionStream(indexedAttentions, bridge)
        
        // Then: Stream should have correct properties
        assertTrue(stream.streamId.startsWith("fiduciary-"))
        assertEquals(3, stream.events.component1())
        assertEquals("fiduciary_attention", stream.metadata["source"])
        
        // And: All events should be DocumentFocus type
        for (i in 0 until stream.events.component1()) {
            val event = stream.events.component2()(i)
            assertTrue(event is AttentionEvent.DocumentFocus)
        }
    }
    
    // === Test 4: Attention Memory Persistence ===
    
    @Test
    fun testAttentionMemoryPersistence() = runBlocking {
        // Given: A memvid pipe with persistence
        var exportedData: ByteArray? = null
        var importedData: ByteArray? = null
        
        val mockPipe = object : MemvidAttentionPipe {
            override suspend fun registerEvent(event: AttentionEvent): Boolean = true
            override suspend fun getAttentionMemory(): AttentionMemory {
                return AttentionMemory(
                    memoryId = "persistent-memory",
                    timestamp = System.currentTimeMillis(),
                    attentionMap = mapOf("doc1" to 0.9, "doc2" to 0.7),
                    focusHistory = Indexed(0) { AttentionEvent.FiduciaryAction("", "", "", 0L) }
                )
            }
            override suspend fun clearMemory(): Boolean = true
            override suspend fun exportMemory(): ByteArray {
                exportedData = "test-memory-data".toByteArray()
                return exportedData!!
            }
            override suspend fun importMemory(data: ByteArray): Boolean {
                importedData = data
                return true
            }
        }
        
        val bridge = FiduciaryMemvidBridge(mockPipe)
        
        // When: Exporting memory
        val exportResult = mockPipe.exportMemory()
        
        // Then: Should return data
        assertNotNull(exportResult)
        assertTrue(exportResult.isNotEmpty())
        assertEquals(exportedData, exportResult)
        
        // When: Importing memory
        val importData = "imported-memory-data".toByteArray()
        val importResult = mockPipe.importMemory(importData)
        
        // Then: Should succeed
        assertTrue(importResult)
        assertEquals(importData, importedData)
    }
    
    // === Test 5: Visual Representation Generation ===
    
    @Test
    fun testVisualRepresentationGeneration() = runBlocking {
        // Given: A memvid pipe that generates visual data
        val mockPipe = object : MemvidAttentionPipe {
            override suspend fun registerEvent(event: AttentionEvent): Boolean = true
            override suspend fun getAttentionMemory(): AttentionMemory {
                return AttentionMemory(
                    memoryId = "visual-memory",
                    timestamp = System.currentTimeMillis(),
                    attentionMap = mapOf("doc1" to 0.8, "doc2" to 0.6),
                    focusHistory = Indexed(0) { AttentionEvent.FiduciaryAction("", "", "", 0L) },
                    visualRepresentation = "video-data".toByteArray()
                )
            }
            override suspend fun clearMemory(): Boolean = true
            override suspend fun exportMemory(): ByteArray = ByteArray(0)
            override suspend fun importMemory(data: ByteArray): Boolean = true
        }
        
        val bridge = FiduciaryMemvidBridge(mockPipe)
        
        // When: Getting attention memory with visual representation
        val memory = bridge.getAttentionMemory()
        
        // Then: Should have visual representation
        assertNotNull(memory.visualRepresentation)
        assertTrue(memory.visualRepresentation!!.isNotEmpty())
        
        // And: Should contain expected data
        val visualData = String(memory.visualRepresentation!!)
        assertEquals("video-data", visualData)
    }
    
    // === Test 5: Webbing Between Attention and Memvid ===
    
    @Test
    fun testWebbingBetweenAttentionAndMemvid() = runBlocking {
        // Given: A mock memvid pipe that records all registered events
        val recordedEvents = mutableListOf<AttentionEvent>()
        val mockPipe = object : MemvidAttentionPipe {
            override suspend fun registerEvent(event: AttentionEvent): Boolean {
                recordedEvents.add(event)
                return true
            }
            override suspend fun getAttentionMemory(): AttentionMemory {
                return AttentionMemory(
                    memoryId = "webbing-memory",
                    timestamp = System.currentTimeMillis(),
                    attentionMap = mapOf(
                        "docA" to 0.9,
                        "corpusX" to 0.7
                    ),
                    focusHistory = recordedEvents.size j recordedEvents::get
                )
            }
            override suspend fun clearMemory(): Boolean = true
            override suspend fun exportMemory(): ByteArray = ByteArray(0)
            override suspend fun importMemory(data: ByteArray): Boolean = true
        }
        val bridge = FiduciaryMemvidBridge(mockPipe)

        // And: A sequence of mixed fiduciary attentions
        val metadata = object {
            fun getProperty(key: String, default: String): String = default
        }
        val parseContext = Any()
        val ioContext = Any()
        val fidContext = FiduciaryContext(ioContext j (metadata j parseContext))
        val docAttention = DocumentAttention(0L j 100L, "application/pdf")
        val corpusAttention = CorpusAttention("corpusX" j (0L j 1000L))
        val fidAttn1 = FiduciaryAttention(docAttention j fidContext)
        val fidAttn2 = FiduciaryAttention(corpusAttention j fidContext)
        // Simulate concept extraction and action
        val conceptEvent = AttentionEvent.ConceptExtraction(
            conceptId = "concept42",
            sourceDoc = "docA",
            confidence = 0.95,
            relatedConcepts = 1 j { "related1" }
        )
        val actionEvent = AttentionEvent.FiduciaryAction(
            actionType = "audit",
            targetId = "docA",
            obligation = "compliance",
            timestamp = System.currentTimeMillis()
        )

        // When: Registering events via the bridge
        bridge.processAttention(fidAttn1)
        bridge.processAttention(fidAttn2)
        mockPipe.registerEvent(conceptEvent)
        mockPipe.registerEvent(actionEvent)

        // Then: Attention memory should reflect all events
        val memory = bridge.getAttentionMemory()
        assertEquals("webbing-memory", memory.memoryId)
        assertEquals(4, memory.focusHistory.component1())
        assertTrue(memory.attentionMap.containsKey("docA"))
        assertTrue(memory.attentionMap.containsKey("corpusX"))
        // Check event types in order
        val types = (0 until memory.focusHistory.component1()).map { i -> memory.focusHistory.component2()(i)::class.simpleName }
        assertEquals(listOf("DocumentFocus", "CorpusScan", "ConceptExtraction", "FiduciaryAction"), types)
    }
    
    // === Test 5: Event-Driven Provenance Recording (BoingDemo-Inspired) ===
    @Test
    fun testEventDrivenProvenanceRecording() = runBlocking {
        // Given: A mock MemvidAttentionPipe that records events
        val events = mutableListOf<AttentionEvent>()
        val mockPipe = object : MemvidAttentionPipe {
            override suspend fun registerEvent(event: AttentionEvent): Boolean {
                events.add(event)
                return true
            }
            override suspend fun getAttentionMemory(): AttentionMemory {
                return AttentionMemory(
                    memoryId = "event-driven-memory",
                    timestamp = System.currentTimeMillis(),
                    attentionMap = mapOf("boingball" to 1.0),
                    focusHistory = Indexed(events.size) { i -> events[i] }
                )
            }
            override suspend fun clearMemory(): Boolean = true
            override suspend fun exportMemory(): ByteArray = ByteArray(0)
            override suspend fun importMemory(data: ByteArray): Boolean = true
        }
        val bridge = FiduciaryMemvidBridge(mockPipe)

        // When: Simulating a BoingBall bounce event as provenance
        val bounceEvent = AttentionEvent.FiduciaryAction(
            actionType = "bounce",
            targetId = "boingball-1",
            obligation = "physics",
            timestamp = System.currentTimeMillis()
        )
        val registered = mockPipe.registerEvent(bounceEvent)

        // Then: The event should be recorded in provenance
        assertTrue(registered)
        assertTrue(events.any { it is AttentionEvent.FiduciaryAction && (it as AttentionEvent.FiduciaryAction).actionType == "bounce" })

        // And: Provenance memory should include the bounce event
        val memory = mockPipe.getAttentionMemory()
        assertTrue(memory.focusHistory.any { it is AttentionEvent.FiduciaryAction && (it as AttentionEvent.FiduciaryAction).actionType == "bounce" })
    }
    
    // === Test 6: Provenance Document Encryption for CouchDB ===
    @Test
    fun testProvenanceDocumentEncryption() = runBlocking {
        // Given: A mock AES key and encryption logic
        val key = javax.crypto.KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12) { it.toByte() } // Example IV

        // Helper: Encrypt
        fun encrypt(plaintext: ByteArray): Pair<ByteArray, ByteArray> {
            val spec = javax.crypto.spec.GCMParameterSpec(128, iv)
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key, spec)
            val ciphertext = cipher.doFinal(plaintext)
            return Pair(ciphertext, iv)
        }
        // Helper: Decrypt
        fun decrypt(ciphertext: ByteArray, iv: ByteArray, useKey: javax.crypto.SecretKey = key): ByteArray {
            val spec = javax.crypto.spec.GCMParameterSpec(128, iv)
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, useKey, spec)
            return cipher.doFinal(ciphertext)
        }

        // And: A provenance document
        val doc = ProvenanceEventDoc(
            event_id = "event-enc-test",
            timestamp = System.currentTimeMillis(),
            source_file = "file.txt",
            parent_file = "parent.zip",
            entity_id = "unit99",
            action_type = "test_action",
            details = "test details",
            links = listOf("parent-event")
        )
        val json = kotlinx.serialization.json.Json.encodeToString(ProvenanceEventDoc.serializer(), doc).toByteArray()

        // When: Encrypting before storage
        val (ciphertext, usedIv) = encrypt(json)
        // Simulate storing only ciphertext and IV in CouchDB
        val stored = mapOf("ciphertext" to ciphertext, "iv" to usedIv)

        // Then: No plaintext is stored
        assertFalse(stored.toString().contains("file.txt"))
        assertFalse(stored.toString().contains("unit99"))
        assertFalse(stored.toString().contains("test details"))

        // When: Decrypting with correct key
        val decrypted = decrypt(stored["ciphertext"]!!, stored["iv"]!!)
        val decoded = kotlinx.serialization.json.Json.decodeFromString(ProvenanceEventDoc.serializer(), decrypted.toString(Charsets.UTF_8))

        // Then: Decrypted doc matches original
        assertEquals(doc, decoded)

        // When: Decrypting with wrong key
        val wrongKey = javax.crypto.KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        try {
            decrypt(stored["ciphertext"]!!, stored["iv"]!!, wrongKey)
            fail("Decryption with wrong key should fail")
        } catch (e: Exception) {
            // Expected
        }
    }
} 