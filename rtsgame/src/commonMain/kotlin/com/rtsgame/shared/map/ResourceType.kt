package com.rtsgame.shared.map

/**
 * Represents different types of resources in the game.
 */
enum class ResourceType {
    GOLD,
    WOOD,
    FOOD,
    STONE,
    METAL;
    
    companion object {
        val BASIC_RESOURCES = setOf(GOLD, WOOD, FOOD)
        val ADVANCED_RESOURCES = setOf(STONE, METAL)
        val ALL_RESOURCES = values().toSet()
    }
} 