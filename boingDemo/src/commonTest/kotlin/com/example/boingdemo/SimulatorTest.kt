package com.example.boingdemo

import com.example.boingdemo.model.BoingBall // Ensure this is accessible
import com.example.boingdemo.model.NodeId
import com.example.boingdemo.model.NodeLabel
import com.example.boingdemo.model.Vec2D
import com.example.boingdemo.model.toNodeProperties // Import the extension function
import spacegraph.spacegraph_kmp.model.Series // For NodeProperties creation if needed directly
import kotlin.test.*

class SimulatorTest {
    @Test
    fun testBallMovesAndPropertiesUpdate() {
        val simulator = BoingDemoSimulator(640f, 480f)
        simulator.initialize() // This sets up the boingBall instance

        val initialPositionX = simulator.boingBall.position.x
        val initialPositionY = simulator.boingBall.position.y
        val initialVelocityX = simulator.boingBall.velocity.x // Store initial velocity for good measure

        // Simulate a few steps
        // With initial velocity (100,100) and dt=0.016, expect movement
        for (i in 0..10) {
            simulator.update(0.016f) // Simulate ~60 FPS
        }

        val finalPositionX = simulator.boingBall.position.x
        val finalPositionY = simulator.boingBall.position.y

        // Assert that the ball has moved.
        // Given the initial velocity is non-zero and no immediate collision is guaranteed at center,
        // position should change.
        assertTrue(initialPositionX != finalPositionX || initialPositionY != finalPositionY,
            "Ball should have moved from ($initialPositionX, $initialPositionY). Final: ($finalPositionX, $finalPositionY)")

        // Check NodeProperties update
        // The toNodeProperties() method in BoingBall combines position, velocity, radius, and colorProperties.
        // We expect pos_x, pos_y, vel_x, vel_y to reflect the simulator's state.
        val props = simulator.boingBall.toNodeProperties()

        // Check that pos_x in NodeProperties reflects the new position
        val posXProp = props.series.elements.find { it.startsWith("pos_x:") }
        assertNotNull(posXProp, "pos_x property should exist.")
        assertEquals("pos_x:$finalPositionX", posXProp, "pos_x in NodeProperties should reflect the final X position.")

        val posYProp = props.series.elements.find { it.startsWith("pos_y:") }
        assertNotNull(posYProp, "pos_y property should exist.")
        assertEquals("pos_y:$finalPositionY", posYProp, "pos_y in NodeProperties should reflect the final Y position.")

        // Also check velocity, as it might change upon wall collision (though not guaranteed in 10 steps)
        // If it didn't hit a wall, velocity should be constant. If it did, it should be different.
        // For this test, we're mostly ensuring it's present and correctly formatted.
        val finalVelocityX = simulator.boingBall.velocity.x
        val velXProp = props.series.elements.find { it.startsWith("vel_x:") }
        assertNotNull(velXProp, "vel_x property should exist.")
        assertEquals("vel_x:$finalVelocityX", velXProp, "vel_x in NodeProperties should reflect the final X velocity.")

        // Example of how initial properties might look for comparison (simplified)
        // This isn't strictly necessary for the test as written but shows how one might compare states.
        // val initialProps = BoingBall(
        //     NodeId("test"),
        //     position = Vec2D(initialPositionX to initialPositionY),
        //     velocity = Vec2D(initialVelocityX to simulator.boingBall.velocity.y), // Assuming Y vel might change
        //     colorProperties = simulator.boingBall.colorProperties, // keep same color
        //     radius = simulator.boingBall.radius
        // ).toNodeProperties()
        // val initialPosXProp = initialProps.series.elements.find { it.startsWith("pos_x:") }
        // assertNotEquals(initialPosXProp, posXProp, "pos_x in NodeProperties string should have changed from initial.")
    }
}
