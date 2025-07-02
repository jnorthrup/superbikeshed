package borg.trikeshed.ljson

import borg.trikeshed.lib.*

/**
 * Simple JSON Lines Parser for Spansh galaxy data
 */
object JsonLinesParser {
    
    /**
     * Parse a galaxy system from a JSON line
     */
    fun parseGalaxySystem(jsonLine: String): GalaxySystem? {
        return try {
            // Simple manual parsing for now
            // In a real implementation, would use proper JSON parser
            if (jsonLine.isBlank()) return null
            
            // Extract basic fields (simplified)
            val id = extractLongField(jsonLine, "id") ?: 0L
            val name = extractStringField(jsonLine, "name") ?: "Unknown"
            val x = extractDoubleField(jsonLine, "coords_x") ?: 0.0
            val y = extractDoubleField(jsonLine, "coords_y") ?: 0.0
            val z = extractDoubleField(jsonLine, "coords_z") ?: 0.0
            
            GalaxySystem(
                id = id,
                name = name,
                coords = x j (y j z),
                primaryStar = null,
                bodies = null,
                stations = null
            )
        } catch (e: Exception) {
            null
        }
    }
    
    private fun extractLongField(json: String, fieldName: String): Long? {
        val pattern = "\"$fieldName\"\\s*:\\s*(\\d+)"
        val regex = Regex(pattern)
        val match = regex.find(json)
        return match?.groupValues?.get(1)?.toLongOrNull()
    }
    
    private fun extractStringField(json: String, fieldName: String): String? {
        val pattern = "\"$fieldName\"\\s*:\\s*\"([^\"]*)\""
        val regex = Regex(pattern)
        val match = regex.find(json)
        return match?.groupValues?.get(1)
    }
    
    private fun extractDoubleField(json: String, fieldName: String): Double? {
        val pattern = "\"$fieldName\"\\s*:\\s*([+-]?\\d*\\.?\\d+)"
        val regex = Regex(pattern)
        val match = regex.find(json)
        return match?.groupValues?.get(1)?.toDoubleOrNull()
    }
} 