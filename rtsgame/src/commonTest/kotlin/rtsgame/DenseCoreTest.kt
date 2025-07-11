package rtsgame

import kotlin.test.*
import rtsgame.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class DenseCoreTest {
    @Test
    fun `entity creation works`() {
        val entity = entityOf(
            "type" to "unit",
            "pos" to Pos(Vec3(0f, 0f, 0f)),
            "hp" to HP(100f to 100f)
        )
        
        assertEquals("unit", entity["type"])
        assertNotNull(entity.get<Pos>("pos"))
        assertEquals(100f, entity.get<HP>("hp")?.value?.first)
    }
    
    @Test
    fun `command interpretation works`() = runBlocking {
        val world = mapOf(
            0 to entityOf(
                "pos" to Pos(Vec3(0f, 0f, 0f)),
                "hp" to HP(100f to 100f)
            )
        )
        
        val moveCmd = Cmd.Move(0, Vec3(10f, 10f, 0f))
        val (newWorld, _) = Game.interpret(moveCmd)(world)
        
        val movedPos = newWorld[0]?.get<Pos>("pos")?.vec
        assertEquals(10f, movedPos?.first)
        assertEquals(10f, movedPos?.second)
    }
    
    @Test
    fun `damage calculation works`() = runBlocking {
        val world = mapOf(
            0 to entityOf(
                "dmg" to Dmg(25f),
                "team" to Team(1)
            ),
            1 to entityOf(
                "hp" to HP(100f to 100f),
                "team" to Team(2)
            )
        )
        
        val attackCmd = Cmd.Attack(0, 1)
        val (newWorld, _) = Game.interpret(attackCmd)(world)
        
        val targetHp = newWorld[1]?.get<HP>("hp")?.value?.first
        assertEquals(75f, targetHp)
    }
    
    @Test
    fun `binary codec round trip`() {
        val original = Cmd.Move(42, Vec3(1.5f, 2.5f, 3.5f))
        val encoded = DenseCodec.run { original.encode() }
        val decoded = DenseCodec.run { encoded.decodeCmd() }
        
        assertTrue(decoded is Cmd.Move)
        assertEquals(42, decoded.id)
        assertEquals(1.5f, decoded.pos.first)
        assertEquals(2.5f, decoded.pos.second)
        assertEquals(3.5f, decoded.pos.third)
    }
    
    @Test
    fun `world hashing is deterministic`() {
        val world1 = mapOf(
            0 to entityOf("pos" to Pos(Vec3(1f, 2f, 3f))),
            1 to entityOf("hp" to HP(50f to 100f))
        )
        
        val world2 = mapOf(
            0 to entityOf("pos" to Pos(Vec3(1f, 2f, 3f))),
            1 to entityOf("hp" to HP(50f to 100f))
        )
        
        assertEquals(world1.hash(), world2.hash())
    }
    
    @Test
    fun `AI perception works`() = runBlocking {
        val world = mapOf(
            0 to entityOf(
                "pos" to Pos(Vec3(0f, 0f, 0f)),
                "team" to Team(1)
            ),
            1 to entityOf(
                "pos" to Pos(Vec3(50f, 0f, 0f)),
                "team" to Team(1)
            ),
            2 to entityOf(
                "pos" to Pos(Vec3(75f, 0f, 0f)),
                "team" to Team(2)
            )
        )
        
        val perception = Tactics.spatial(world, 0)
        val allies = perception["allies"] as? List<Pair<EntityId, Float>>
        val enemies = perception["enemies"] as? List<Pair<EntityId, Float>>
        
        assertEquals(1, allies?.size)
        assertEquals(1, enemies?.size)
        assertTrue(allies?.first()?.second ?: Float.MAX_VALUE < 60f)
    }
}