package rtsgame

import borg.trikeshed.lib.*
import rtsgame.core.*
import rtsgame.webgpu.*
import rtsgame.spacegraph.*
import kotlin.test.*

/**
 * Integration tests for WebGPU rendering pipeline
 */
class WebGPUIntegrationTest {
    
    internal lateinit var renderer: CommonWebGPUSpaceGraph
    internal lateinit var gameState: GameState
    
    @BeforeTest
    fun setup() {
        renderer = CommonWebGPUSpaceGraph()
        
        // Create test game state
        val entities = listOf(
            Entity(
                id = EntityId("test_commander"),
                position = Position(100f, 100f),
                health = Health(100f),
                playerId = PlayerId(1)
            ),
            Entity(
                id = EntityId("test_unit"),
                position = Position(200f, 150f),
                health = Health(75f),
                playerId = PlayerId(2)
            )
        )
        
        gameState = GameState(
            entities = Indexed.of(entities.size) { i -> entities[i] },
            tick = GameTick(1)
        )
    }
    
    @Test
    fun `webgpu renderer initializes`() {
        // Note: In a real test environment, this would need actual WebGPU context
        // For CI, we test the API structure
        assertNotNull(renderer, "Renderer should be created")
    }
    
    @Test
    fun `camera state validates correctly`() {
        val camera = CameraState(
            position = Vector3D(100.0, 200.0, 300.0),
            target = Vector3D(0.0, 0.0, 0.0),
            fov = 60f,
            aspect = 16f / 9f
        )
        
        assertEquals(100.0, camera.position.x)
        assertEquals(200.0, camera.position.y)
        assertEquals(300.0, camera.position.z)
        assertEquals(60f, camera.fov)
        assertTrue(camera.aspect > 0f, "Aspect ratio should be positive")
    }
    
    @Test
    fun `render data structures are valid`() {
        val spaceGraphRenderer = RTSSpaceGraphRenderer()
        val result = spaceGraphRenderer.renderGameState(gameState)
        
        assertNotNull(result.nodes, "Nodes should be generated")
        assertNotNull(result.edges, "Edges should be generated")
        assertNotNull(result.metadata, "Metadata should be present")
        
        assertEquals(gameState.entities.`play`.size, result.metadata.entityCount)
        assertEquals(gameState.tick, result.metadata.tick)
    }
    
    @Test
    fun `spacegraph nodes have correct data`() {
        val spaceGraphRenderer = RTSSpaceGraphRenderer()
        val result = spaceGraphRenderer.renderGameState(gameState)
        
        val nodes = result.nodes.play
        assertEquals(2, nodes.size, "Should have 2 nodes for 2 entities")
        
        val commanderNode = nodes.find { it.id.value.contains("test_commander") }
        assertNotNull(commanderNode, "Commander node should exist")
        assertEquals(EntityType.COMMANDER, commanderNode.data.entityType)
        
        val unitNode = nodes.find { it.id.value.contains("test_unit") }
        assertNotNull(unitNode, "Unit node should exist")
        assertEquals(EntityType.UNIT, unitNode.data.entityType)
    }
    
    @Test
    fun `vector3d operations work correctly`() {
        val v1 = Vector3D(1.0, 2.0, 3.0)
        val v2 = Vector3D(4.0, 5.0, 6.0)
        
        assertEquals(1.0, v1.x)
        assertEquals(2.0, v1.y)
        assertEquals(3.0, v1.z)
        
        assertNotEquals(v1, v2, "Different vectors should not be equal")
    }
    
    @Test
    fun `matrix4 identity works`() {
        val identity = Matrix4.identity()
        assertNotNull(identity, "Identity matrix should be created")
        assertNotNull(identity.data, "Matrix data should exist")
    }
    
    @Test
    fun `webgpu value classes are type safe`() {
        val bufferId = BufferId(1)
        val pipelineId = PipelineId(2)
        val textureId = TextureId(3)
        
        assertEquals(1, bufferId.value)
        assertEquals(2, pipelineId.value)
        assertEquals(3, textureId.value)
        
        // Type safety - these should be different types even with same value
        assertNotEquals(bufferId.value, pipelineId.value)
    }
}

/**
 * SpaceGraph integration tests
 */
class SpaceGraphIntegrationTest {
    
    @Test
    fun `spacegraph renderer creates nodes for entities`() {
        val entities = listOf(
            Entity(
                id = EntityId("test_1"),
                position = Position(0f, 0f),
                health = Health(100f),
                playerId = PlayerId(1)
            ),
            Entity(
                id = EntityId("test_2"),
                position = Position(50f, 50f),
                health = Health(50f),
                playerId = PlayerId(1)
            )
        )
        
        val gameState = GameState(
            entities = Indexed.of(entities.size) { i -> entities[i] },
            tick = GameTick(1)
        )
        
        val renderer = RTSSpaceGraphRenderer()
        val result = renderer.renderGameState(gameState)
        
        assertEquals(2, result.nodes.play.size, "Should create 2 nodes")
        assertTrue(result.edges.play.isNotEmpty(), "Should create edges between same-player entities")
    }
    
    @Test
    fun `spacegraph handles different entity types`() {
        val entities = listOf(
            Entity(
                id = EntityId("commander_1"),
                position = Position(0f, 0f),
                health = Health(100f),
                playerId = PlayerId(1)
            ),
            Entity(
                id = EntityId("scout_1"),
                position = Position(50f, 50f),
                health = Health(30f),
                playerId = PlayerId(1)
            ),
            Entity(
                id = EntityId("building_1"),
                position = Position(100f, 100f),
                health = Health(200f),
                playerId = PlayerId(0)
            )
        )
        
        val gameState = GameState(
            entities = Indexed.of(entities.size) { i -> entities[i] },
            tick = GameTick(1)
        )
        
        val renderer = RTSSpaceGraphRenderer()
        val result = renderer.renderGameState(gameState)
        
        val nodes = result.nodes.play
        
        val commanderNode = nodes.find { it.data.entityType == EntityType.COMMANDER }
        assertNotNull(commanderNode, "Should have commander node")
        
        val scoutNode = nodes.find { it.data.entityType == EntityType.SCOUT }
        assertNotNull(scoutNode, "Should have scout node")
        
        val buildingNode = nodes.find { it.data.entityType == EntityType.BUILDING }
        assertNotNull(buildingNode, "Should have building node")
    }
    
    @Test
    fun `spacegraph edges connect related entities`() {
        val entities = listOf(
            Entity(
                id = EntityId("unit_1"),
                position = Position(0f, 0f),
                health = Health(100f),
                playerId = PlayerId(1)
            ),
            Entity(
                id = EntityId("unit_2"),
                position = Position(10f, 10f), // Close to unit_1
                health = Health(100f),
                playerId = PlayerId(1)
            ),
            Entity(
                id = EntityId("enemy_1"),
                position = Position(1000f, 1000f), // Far away
                health = Health(100f),
                playerId = PlayerId(2)
            )
        )
        
        val gameState = GameState(
            entities = Indexed.of(entities.size) { i -> entities[i] },
            tick = GameTick(1)
        )
        
        val renderer = RTSSpaceGraphRenderer()
        val result = renderer.renderGameState(gameState)
        
        // Should have edges between close same-player units
        val edges = result.edges.play
        assertTrue(edges.isNotEmpty(), "Should have edges")
        
        // Check for ally connection between unit_1 and unit_2
        val allyEdge = edges.find { edge ->
            edge.data.connectionType == ConnectionType.ALLY &&
            ((edge.from.value.contains("unit_1") && edge.to.value.contains("unit_2")) ||
             (edge.from.value.contains("unit_2") && edge.to.value.contains("unit_1")))
        }
        
        assertNotNull(allyEdge, "Should have ally edge between close same-player units")
    }
}