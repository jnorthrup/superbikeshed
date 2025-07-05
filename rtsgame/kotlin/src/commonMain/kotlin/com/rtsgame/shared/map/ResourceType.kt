package com.rtsgame.shared.map

import kotlinx.serialization.Serializable

enum class ResourceType {
    // Basic Resources
    GOLD,
    WOOD,
    FOOD,
    STONE,
    IRON,
    OIL,
    URANIUM,
    CRYSTAL,

    // Processed Resources
    REFINED_IRON,
    STEEL,
    ELECTRONICS,
    ADVANCED_ALLOY,
    REFINED_OIL,
    ENRICHED_URANIUM,
    REFINED_CRYSTAL,
    QUANTUM_MATERIAL;

    val baseGatherRate: Float
        get() = when (this) {
            // Basic Resources
            GOLD -> 10f
            WOOD -> 15f
            FOOD -> 20f
            STONE -> 8f
            IRON -> 5f
            OIL -> 3f
            URANIUM -> 2f
            CRYSTAL -> 1f
            // Processed Resources (not gathered)
            else -> 0f
        }

    val baseAmount: Float
        get() = when (this) {
            // Basic Resources
            GOLD -> 1000f
            WOOD -> 500f
            FOOD -> 300f
            STONE -> 800f
            IRON -> 600f
            OIL -> 400f
            URANIUM -> 200f
            CRYSTAL -> 100f
            // Processed Resources (not gathered)
            else -> 0f
        }

    val respawnTime: Float
        get() = when (this) {
            // Basic Resources
            GOLD -> 300f  // 5 minutes
            WOOD -> 180f  // 3 minutes
            FOOD -> 120f  // 2 minutes
            STONE -> 240f // 4 minutes
            IRON -> 360f  // 6 minutes
            OIL -> 480f   // 8 minutes
            URANIUM -> 600f // 10 minutes
            CRYSTAL -> 720f // 12 minutes
            // Processed Resources (not gathered)
            else -> 0f
        }

    val requiredTool: GatheringTool?
        get() = when (this) {
            // Basic Resources
            GOLD -> GatheringTool.PICKAXE
            WOOD -> GatheringTool.AXE
            FOOD -> null
            STONE -> GatheringTool.PICKAXE
            IRON -> GatheringTool.PICKAXE
            OIL -> GatheringTool.DRILL
            URANIUM -> GatheringTool.ADVANCED_DRILL
            CRYSTAL -> GatheringTool.LASER_CUTTER
            // Processed Resources (not gathered)
            else -> null
        }

    val isProcessed: Boolean
        get() = when (this) {
            REFINED_IRON, STEEL, ELECTRONICS, ADVANCED_ALLOY,
            REFINED_OIL, ENRICHED_URANIUM, REFINED_CRYSTAL, QUANTUM_MATERIAL -> true
            else -> false
        }
}

enum class GatheringTool {
    AXE,
    PICKAXE,
    DRILL,
    ADVANCED_DRILL,
    LASER_CUTTER;

    val efficiencyMultiplier: Float
        get() = when (this) {
            AXE -> 1.0f
            PICKAXE -> 1.0f
            DRILL -> 1.5f
            ADVANCED_DRILL -> 2.0f
            LASER_CUTTER -> 3.0f
        }
} 