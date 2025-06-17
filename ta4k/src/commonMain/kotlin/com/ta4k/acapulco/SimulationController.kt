package borg.trikeshed.acapulco

class SimulationController(
    private val dataSource: MarketDataSource,
    private val observationBuilder: ObservationBuilder,
    private val agent: AgentInterface,
    private val executionEngine: ExecutionEngine,
    private val historyProvider: MarketHistoryProvider,
    private val priceOracle: PriceOracle
) {
    suspend fun runSimulation(initialWalletState: WalletState) {
        executionEngine.initialize(initialWalletState)
        while (true) {
            val tick = dataSource.nextTick() ?: break
            historyProvider.addTick(tick)  // Placeholder; implement as needed
            val observation = observationBuilder.buildObservation(tick, historyProvider)
            val action = agent.decideAction(observation)
            val result = executionEngine.processTick(tick, action)
            // Process or log result here, e.g., update metrics
        }
    }
}

interface PriceOracle {
    fun getReferencePrice(asset: String, currentTick: MarketTick): Double
}