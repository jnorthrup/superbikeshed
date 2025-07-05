package com.rtsgame.shared.entity

import com.rtsgame.shared.map.Position
import kotlinx.serialization.Serializable

interface Entity {
    val id: String
    val position: Position
    val health: Float
    val maxHealth: Float
    val speed: Float
    val team: Int
} 