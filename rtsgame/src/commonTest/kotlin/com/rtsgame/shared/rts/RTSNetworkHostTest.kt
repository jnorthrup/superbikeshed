package com.rtsgame.shared.rts

import com.rtsgame.shared.game.GameState
import com.rtsgame.shared.entity.Entity
import com.rtsgame.shared.map.Position
import com.rtsgame.shared.map.ResourceType
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

class RTSNetworkHostTest {
    private val testScope = TestScope()
    private lateinit var host: RTSNetworkHost

    @BeforeTest
    fun setup() {
        host = RTSNetworkHost(
            scope = testScope,
            gameState = GameState(
                entities = emptyMap(),
                resources = emptyMap(),
                currentTime = 0L
            )
        )
    }

    @Test
    fun testInitialState() = testScope.runTest {
        val state = host.gameStateFlow.value
        assertEquals(0, state.entities.size)
        assertEquals(0, state.resources.size)
        assertEquals(0L, state.currentTime)
    }
} 