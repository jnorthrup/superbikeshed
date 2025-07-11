package rtsgame.codec

import rtsgame.core.*

/**
 * Simulation interface for codec tests
 */
interface Simulation {
    fun getCurrentTick(): Long
    fun getEntityCount(): Int
    fun update(deltaTime: Float)
    fun reset()
}

/**
 * Create RTS simulation with request factory
 */
fun createRTSSimulation(): Pair<Simulation, RTSRequestFactory> {
    val simulation = RTSSimulationImpl()
    val requestFactory = RTSRequestFactory(simulation)
    
    return Pair(simulation, requestFactory)
}

/**
 * Implementation of RTS simulation
 */
class RTSSimulationImpl : Simulation {
    private val nextGenSim = NextGenSimulation()
    
    override fun getCurrentTick(): Long = nextGenSim.currentTick
    
    override fun getEntityCount(): Int = nextGenSim.getEntityCount()
    
    override fun update(deltaTime: Float) {
        nextGenSim.update(deltaTime)
    }
    
    override fun reset() {
        nextGenSim.reset()
    }
}