package com.example.boingdemo.model

import spacegraph.spacegraph_kmp.model.NodeId
import spacegraph.spacegraph_kmp.model.NodeLabel
import spacegraph.spacegraph_kmp.model.NodeProperties
import spacegraph.spacegraph_kmp.model.Series

// Using Float for Vec2D components as per typical graphics applications
// Using @JvmInline value class for potential performance benefits by avoiding object allocation
@JvmInline
value class Vec2D(val data: Pair<Float, Float>) {
    val x: Float get() = data.first
    val y: Float get() = data.second

    operator fun plus(other: Vec2D) = Vec2D(x + other.x to y + other.y)
    operator fun minus(other: Vec2D) = Vec2D(x - other.x to y - other.y)
    operator fun times(scalar: Float) = Vec2D(x * scalar to y * scalar)
    override fun toString(): String = "Vec2D(x=$x, y=$y)"
}

data class BoingBall(
    val id: NodeId,
    val label: NodeLabel = NodeLabel("BoingBall"),
    var position: Vec2D,
    var velocity: Vec2D,
    var colorProperties: NodeProperties, // Stores color and potentially other appearance-related properties
    var radius: Float
) {
    /**
     * Converts the BoingBall's state (position, velocity, radius) and its existing
     * colorProperties into a single NodeProperties object.
     */
    fun toNodeProperties(): NodeProperties {
        val dynamicProps = listOf(
            "pos_x:${position.x}",
            "pos_y:${position.y}",
            "vel_x:${velocity.x}",
            "vel_y:${velocity.y}",
            "radius:$radius"
        )
        // Combine dynamic properties with existing color/appearance properties
        val allProps = dynamicProps + colorProperties.series.elements
        return NodeProperties(Series(allProps.distinct())) // distinct to avoid duplicates if keys overlap
    }

    /**
     * Updates the BoingBall's state (position, velocity, radius) and its colorProperties
     * from a given NodeProperties object.
     */
    fun updateFromNodeProperties(properties: NodeProperties) {
        val propsMap = properties.series.elements.associate {
            val parts = it.split(":", limit = 2)
            parts[0] to parts.getOrElse(1) { "" }
        }

        position = Vec2D(
            propsMap["pos_x"]?.toFloatOrNull() ?: position.x to
            (propsMap["pos_y"]?.toFloatOrNull() ?: position.y)
        )
        velocity = Vec2D(
            propsMap["vel_x"]?.toFloatOrNull() ?: velocity.x to
            (propsMap["vel_y"]?.toFloatOrNull() ?: velocity.y)
        )
        radius = propsMap["radius"]?.toFloatOrNull() ?: radius

        // Update colorProperties:
        // Extract any properties that are not the dynamic ones defined above.
        // This assumes that any other properties are meant for color/appearance.
        // A more robust approach might involve specific prefixes for color properties.
        val dynamicKeys = setOf("pos_x", "pos_y", "vel_x", "vel_y", "radius")
        val newColorPropsElements = properties.series.elements.filterNot { prop ->
            dynamicKeys.contains(prop.substringBefore(":"))
        }
        if (newColorPropsElements.isNotEmpty()) {
            colorProperties = NodeProperties(Series(newColorPropsElements))
        }
        // If only dynamic properties are present, colorProperties remain unchanged.
    }
}

data class Wall(
    val id: NodeId,
    val label: NodeLabel = NodeLabel("Wall"),
    val start: Vec2D,
    val end: Vec2D,
    var appearanceProperties: NodeProperties // Example: Series(listOf("color:gray", "thickness:5"))
) {
    /**
     * Converts the Wall's defining geometry (start, end) and its existing
     * appearanceProperties into a single NodeProperties object.
     */
    fun toNodeProperties(): NodeProperties {
        val geometryProps = listOf(
            "start_x:${start.x}",
            "start_y:${start.y}",
            "end_x:${end.x}",
            "end_y:${end.y}"
        )
        val allProps = geometryProps + appearanceProperties.series.elements
        return NodeProperties(Series(allProps.distinct()))
    }

    /**
     * Updates the Wall's state (start, end) and its appearanceProperties
     * from a given NodeProperties object. Note: Wall geometry is often static.
     */
    fun updateFromNodeProperties(properties: NodeProperties) {
        val propsMap = properties.series.elements.associate {
            val parts = it.split(":", limit = 2)
            parts[0] to parts.getOrElse(1) { "" }
        }

        // Typically, wall geometry (start, end) might be considered immutable after creation.
        // If mutable, uncomment the following:
        /*
        start = Vec2D(
            propsMap["start_x"]?.toFloatOrNull() ?: start.x to
            (propsMap["start_y"]?.toFloatOrNull() ?: start.y)
        )
        end = Vec2D(
            propsMap["end_x"]?.toFloatOrNull() ?: end.x to
            (propsMap["end_y"]?.toFloatOrNull() ?: end.y)
        )
        */

        val geometryKeys = setOf("start_x", "start_y", "end_x", "end_y")
        val newAppearancePropsElements = properties.series.elements.filterNot { prop ->
            geometryKeys.contains(prop.substringBefore(":"))
        }
        if (newAppearancePropsElements.isNotEmpty()) {
            appearanceProperties = NodeProperties(Series(newAppearancePropsElements))
        }
    }
}
