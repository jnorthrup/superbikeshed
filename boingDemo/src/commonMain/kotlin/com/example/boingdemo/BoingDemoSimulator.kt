package com.example.boingdemo

import com.example.boingdemo.model.BoingBall
import com.example.boingdemo.model.Vec2D
import korlibs.logger.Logger
import korlibs.math.geom.Vector2
import korlibs.physics.box2d.*
import spacegraph.spacegraph_kmp.model.NodeId
import spacegraph.spacegraph_kmp.model.NodeLabel
import spacegraph.spacegraph_kmp.model.NodeProperties
import spacegraph.spacegraph_kmp.model.Series

class BoingDemoSimulator(
    val worldWidth: Float,
    val worldHeight: Float
) {
    companion object {
        val logger = Logger("BoingDemoSimulator")
        const val BALL_RADIUS = 10f // Example radius
        const val WALL_THICKNESS = 10f // Example thickness
    }

    lateinit var world: WorldView
    lateinit var ballBody: Body
    lateinit var boingBall: BoingBall

    var ballBouncedThisFrame: Boolean = false

    // Wall bodies
    private lateinit var topWallBody: Body
    private lateinit var bottomWallBody: Body
    private lateinit var leftWallBody: Body
    private lateinit var rightWallBody: Body

    fun initialize() {
        world = WorldView(Vector2(0f, 0f)) // No gravity

        // Create BoingBall data instance
        val initialBallPosition = Vec2D(worldWidth / 2f to worldHeight / 2f)
        val initialBallVelocity = Vec2D(100f to 100f) // pixels/sec
        boingBall = BoingBall(
            id = NodeId("boingBall"),
            label = NodeLabel("BoingBall"),
            position = initialBallPosition,
            velocity = initialBallVelocity,
            colorProperties = NodeProperties(Series(listOf("color:blue"))), // Example color
            radius = BALL_RADIUS
        )

        // Create Walls
        val wallProperties = NodeProperties(Series(listOf("color:gray"))) // Example wall color

        // Top Wall
        topWallBody = world.createBody {
            type = BodyType.STATIC
            setPosition(worldWidth / 2f, worldHeight - WALL_THICKNESS / 2f)
        }.fixture {
            shape = BoxShape(worldWidth / 1f, WALL_THICKNESS / 1f) // KorGE BoxShape takes half extents
        }.body.apply { userData = "TopWall" }

        // Bottom Wall
        bottomWallBody = world.createBody {
            type = BodyType.STATIC
            setPosition(worldWidth / 2f, WALL_THICKNESS / 2f)
        }.fixture {
            shape = BoxShape(worldWidth/1f, WALL_THICKNESS/1f)
        }.body.apply { userData = "BottomWall" }

        // Left Wall
        leftWallBody = world.createBody {
            type = BodyType.STATIC
            setPosition(WALL_THICKNESS / 2f, worldHeight / 2f)
        }.fixture {
            shape = BoxShape(WALL_THICKNESS/1f, worldHeight/1f)
        }.body.apply { userData = "LeftWall" }

        // Right Wall
        rightWallBody = world.createBody {
            type = BodyType.STATIC
            setPosition(worldWidth - WALL_THICKNESS / 2f, worldHeight / 2f)
        }.fixture {
            shape = BoxShape(WALL_THICKNESS/1f, worldHeight/1f)
        }.body.apply { userData = "RightWall" }

        // Create Ball Body
        ballBody = world.createBody {
            type = BodyType.DYNAMIC
            setPosition(boingBall.position.x, boingBall.position.y)
            fixedRotation = true // No rotation for simplicity
            linearVelocity = Vector2(boingBall.velocity.x, boingBall.velocity.y)
        }.fixture {
            shape = CircleShape { radius = boingBall.radius }
            density = 1f
            friction = 0f
            restitution = 1f // Perfect bounce
        }.body.apply { userData = "Ball" }

        // Collision Listener
        world.contactListener = object : ContactListener {
            override fun beginContact(contact: Contact) {
                val fixtureA = contact.fixtureA
                val fixtureB = contact.fixtureB
                val bodyA = fixtureA.body
                val bodyB = fixtureB.body

                val isBallA = bodyA == ballBody
                val isBallB = bodyB == ballBody

                val isWallA = bodyA == topWallBody || bodyA == bottomWallBody || bodyA == leftWallBody || bodyA == rightWallBody
                val isWallB = bodyB == topWallBody || bodyB == bottomWallBody || bodyB == leftWallBody || bodyB == rightWallBody

                if ((isBallA && isWallB) || (isBallB && isWallA)) {
                    ballBouncedThisFrame = true
                    logger.info { "Bounce detected between ball and a wall. Ball UserData: ${if(isBallA) bodyA.userData else bodyB.userData}, Wall UserData: ${if(isWallA) bodyA.userData else bodyB.userData}" }
                }
            }

            override fun endContact(contact: Contact) {}
            override fun preSolve(contact: Contact, oldManifold: Manifold) {}
            override fun postSolve(contact: Contact, impulse: ContactImpulse) {}
        }
    }

    fun update(deltaTime: Float) {
        ballBouncedThisFrame = false
        world.step(deltaTime, 8, 3) // velocityIterations, positionIterations

        // Update BoingBall data from Box2D body
        val newB2Position = ballBody.position
        boingBall.position = Vec2D(newB2Position.x.toFloat() to newB2Position.y.toFloat())

        val newB2Velocity = ballBody.linearVelocity
        boingBall.velocity = Vec2D(newB2Velocity.x.toFloat() to newB2Velocity.y.toFloat())

        // The boingBall's internal state (position, velocity, radius) is now updated.
        // If these changes need to be reflected in a NodeProperties object for external use
        // (e.g., updating a graph database or UI), one would call:
        // val currentBallProperties = boingBall.toNodeProperties()
        // This line is removed as it was incorrectly re-assigning the full properties set
        // back to just the colorProperties field.
    }

    // Helper to convert Vec2D to Box2D Vector2 (if needed, though KorGE often handles Floats directly)
    private fun Vec2D.toB2Vec(): Vector2 = Vector2(this.x.toDouble(), this.y.toDouble())
}
