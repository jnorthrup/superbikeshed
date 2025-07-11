package fiduciary.protocol

import kotlin.test.*
import borg.trikeshed.lib.*
import kotlinx.coroutines.test.runTest

/**
 * TDD Test Suite for CRDT Protocol and WaveCRDT
 * 
 * Based on Fiduciary Omnibus Architecture:
 * - CRDT Protocol inherits basic CRDT functionality
 * - WaveCRDT inherits from CRDT Protocol
 * - EntityID key relationship management
 * - Concurrent conflict-free operations
 */
class CRDTProtocolTest {
    
    @Test
    fun `CRDT Protocol should support basic operations`() = runTest {
        // Given
        val crdt = CRDTProtocol<String>()
        val entityId = "entity-123"
        
        // When
        crdt.add(entityId, "value1")
        crdt.add(entityId, "value2")
        
        // Then
        val values = crdt.get(entityId)
        assertEquals(2, values.size)
        assertTrue(values.contains("value1"))
        assertTrue(values.contains("value2"))
    }
    
    @Test
    fun `CRDT Protocol should handle concurrent additions`() = runTest {
        // Given
        val crdt1 = CRDTProtocol<String>()
        val crdt2 = CRDTProtocol<String>()
        val entityId = "concurrent-entity"
        
        // When
        crdt1.add(entityId, "value-from-crdt1")
        crdt2.add(entityId, "value-from-crdt2")
        
        // Merge the CRDTs
        val merged = crdt1.merge(crdt2)
        
        // Then
        val values = merged.get(entityId)
        assertEquals(2, values.size)
        assertTrue(values.contains("value-from-crdt1"))
        assertTrue(values.contains("value-from-crdt2"))
    }
    
    @Test
    fun `CRDT Protocol should support removals`() = runTest {
        // Given
        val crdt = CRDTProtocol<String>()
        val entityId = "removal-entity"
        
        // When
        crdt.add(entityId, "value1")
        crdt.add(entityId, "value2")
        crdt.remove(entityId, "value1")
        
        // Then
        val values = crdt.get(entityId)
        assertEquals(1, values.size)
        assertTrue(values.contains("value2"))
        assertFalse(values.contains("value1"))
    }
    
    @Test
    fun `CRDT Protocol should handle add-wins semantics`() = runTest {
        // Given
        val crdt1 = CRDTProtocol<String>()
        val crdt2 = CRDTProtocol<String>()
        val entityId = "conflict-entity"
        val value = "conflicted-value"
        
        // When
        crdt1.add(entityId, value)
        crdt2.remove(entityId, value) // Remove without add
        
        val merged = crdt1.merge(crdt2)
        
        // Then
        val values = merged.get(entityId)
        assertTrue(values.contains(value), "Add should win over remove")
    }
    
    @Test
    fun `WaveCRDT should inherit from CRDT Protocol`() = runTest {
        // Given
        val waveCrdt = WaveCRDT()
        
        // When
        val isCRDTProtocol = waveCrdt is CRDTProtocol<*>
        
        // Then
        assertTrue(isCRDTProtocol, "WaveCRDT should inherit from CRDT Protocol")
    }
    
    @Test
    fun `WaveCRDT should support wave operations`() = runTest {
        // Given
        val waveCrdt = WaveCRDT()
        val waveId = "wave-123"
        val operation = WaveOperation(
            id = "op-1",
            type = OperationType.INSERT,
            position = 0,
            content = "Hello Wave",
            timestamp = System.currentTimeMillis()
        )
        
        // When
        waveCrdt.applyOperation(waveId, operation)
        val content = waveCrdt.getWaveContent(waveId)
        
        // Then
        assertEquals("Hello Wave", content)
    }
    
    @Test
    fun `WaveCRDT should handle concurrent wave operations`() = runTest {
        // Given
        val waveCrdt1 = WaveCRDT()
        val waveCrdt2 = WaveCRDT()
        val waveId = "concurrent-wave"
        
        val op1 = WaveOperation(
            id = "op-1",
            type = OperationType.INSERT,
            position = 0,
            content = "Hello ",
            timestamp = 1000L
        )
        
        val op2 = WaveOperation(
            id = "op-2",
            type = OperationType.INSERT,
            position = 0,
            content = "World",
            timestamp = 2000L
        )
        
        // When
        waveCrdt1.applyOperation(waveId, op1)
        waveCrdt2.applyOperation(waveId, op2)
        
        val merged = waveCrdt1.mergeWave(waveCrdt2)
        val content = merged.getWaveContent(waveId)
        
        // Then
        // Operations should be ordered by timestamp
        assertTrue(content.contains("Hello"))
        assertTrue(content.contains("World"))
    }
    
    @Test
    fun `WaveCRDT should support operation transformation`() = runTest {
        // Given
        val waveCrdt = WaveCRDT()
        val waveId = "transform-wave"
        
        val insertOp = WaveOperation(
            id = "op-1",
            type = OperationType.INSERT,
            position = 0,
            content = "Hello World",
            timestamp = 1000L
        )
        
        val deleteOp = WaveOperation(
            id = "op-2",
            type = OperationType.DELETE,
            position = 6,
            content = "World",
            timestamp = 2000L
        )
        
        // When
        waveCrdt.applyOperation(waveId, insertOp)
        waveCrdt.applyOperation(waveId, deleteOp)
        
        val content = waveCrdt.getWaveContent(waveId)
        
        // Then
        assertEquals("Hello ", content)
    }
    
    @Test
    fun `WaveCRDT should maintain operation history`() = runTest {
        // Given
        val waveCrdt = WaveCRDT()
        val waveId = "history-wave"
        val operations = listOf(
            WaveOperation("op-1", OperationType.INSERT, 0, "A", 1000L),
            WaveOperation("op-2", OperationType.INSERT, 1, "B", 2000L),
            WaveOperation("op-3", OperationType.DELETE, 0, "A", 3000L)
        )
        
        // When
        operations.forEach { waveCrdt.applyOperation(waveId, it) }
        val history = waveCrdt.getOperationHistory(waveId)
        
        // Then
        assertEquals(3, history.size)
        assertEquals("op-1", history[0].id)
        assertEquals("op-3", history[2].id)
    }
    
    @Test
    fun `WaveCRDT should support participant management`() = runTest {
        // Given
        val waveCrdt = WaveCRDT()
        val waveId = "participant-wave"
        val participant1 = "user-1"
        val participant2 = "user-2"
        
        // When
        waveCrdt.addParticipant(waveId, participant1)
        waveCrdt.addParticipant(waveId, participant2)
        val participants = waveCrdt.getParticipants(waveId)
        
        // Then
        assertEquals(2, participants.size)
        assertTrue(participants.contains(participant1))
        assertTrue(participants.contains(participant2))
    }
    
    @Test
    fun `WaveCRDT should handle wave merging with conflicts`() = runTest {
        // Given
        val waveCrdt1 = WaveCRDT()
        val waveCrdt2 = WaveCRDT()
        val waveId = "merge-wave"
        
        // Both CRDTs start with same content
        val baseOp = WaveOperation("base", OperationType.INSERT, 0, "Base", 1000L)
        waveCrdt1.applyOperation(waveId, baseOp)
        waveCrdt2.applyOperation(waveId, baseOp)
        
        // Concurrent conflicting operations
        val op1 = WaveOperation("op-1", OperationType.INSERT, 4, " Text", 2000L)
        val op2 = WaveOperation("op-2", OperationType.INSERT, 4, " Content", 2000L)
        
        waveCrdt1.applyOperation(waveId, op1)
        waveCrdt2.applyOperation(waveId, op2)
        
        // When
        val merged = waveCrdt1.mergeWave(waveCrdt2)
        val content = merged.getWaveContent(waveId)
        
        // Then
        assertTrue(content.startsWith("Base"))
        assertTrue(content.contains("Text") || content.contains("Content"))
    }
    
    @Test
    fun `CRDT Protocol should support vector clocks`() = runTest {
        // Given
        val crdt = CRDTProtocol<String>()
        val entityId = "clock-entity"
        
        // When
        crdt.add(entityId, "value1")
        crdt.add(entityId, "value2")
        
        val vectorClock = crdt.getVectorClock(entityId)
        
        // Then
        assertNotNull(vectorClock)
        assertTrue(vectorClock.getVersion() > 0)
    }
    
    @Test
    fun `WaveCRDT should support blip operations`() = runTest {
        // Given
        val waveCrdt = WaveCRDT()
        val waveId = "blip-wave"
        val blipId = "blip-123"
        
        // When
        waveCrdt.createBlip(waveId, blipId, "Initial blip content")
        waveCrdt.updateBlip(waveId, blipId, "Updated blip content")
        
        val blipContent = waveCrdt.getBlipContent(waveId, blipId)
        
        // Then
        assertEquals("Updated blip content", blipContent)
    }
    
    @Test
    fun `WaveCRDT should handle blip threading`() = runTest {
        // Given
        val waveCrdt = WaveCRDT()
        val waveId = "thread-wave"
        val parentBlipId = "parent-blip"
        val childBlipId = "child-blip"
        
        // When
        waveCrdt.createBlip(waveId, parentBlipId, "Parent content")
        waveCrdt.createBlip(waveId, childBlipId, "Child content", parentBlipId)
        
        val children = waveCrdt.getChildBlips(waveId, parentBlipId)
        
        // Then
        assertEquals(1, children.size)
        assertEquals(childBlipId, children[0])
    }
    
    @Test
    fun `CRDT Protocol should support entity key management`() = runTest {
        // Given
        val crdt = CRDTProtocol<String>()
        val entityKey = EntityKey("entity-456", "TEST_ENTITY")
        
        // When
        crdt.registerEntityKey(entityKey)
        val retrievedKey = crdt.getEntityKey(entityKey.entityId)
        
        // Then
        assertNotNull(retrievedKey)
        assertEquals(entityKey.entityId, retrievedKey.entityId)
        assertEquals(entityKey.keyType, retrievedKey.keyType)
    }
}