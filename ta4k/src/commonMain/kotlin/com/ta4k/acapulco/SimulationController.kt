package borg.trikeshed.acapulco

class SimulationController(
    internal val dataSource: MarketDataSource,
    internal val observationBuilder: ObservationBuilder,
    internal val agent: AgentInterface,
    internal val executionEngine: ExecutionEngine,
    internal val historyProvider: MarketHistoryProvider,
    internal val priceOracle: PriceOracle
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