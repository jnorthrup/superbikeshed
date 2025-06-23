package com.example.demo

import com.ta4k.core.model.Kline
import com.ta4k.core.model.BigDecimal // Explicitly from ta4k.core.model
import com.ta4k.indicators.RSIIndicator
import com.ta4k.indicators.SMAIndicator
import borg.trikeshed.lib.Series
import borg.trikeshed.lib.j
import borg.trikeshed.lib.alpha
import borg.trikeshed.lib.`play`

// Data classes and enums are in DemoDataClasses.kt, assumed to be in the same package
// or correctly imported if this file were separate.

/**
 * DSEL Interface for orchestrating Technical Analysis (TA) and Moneyfan concepts.
 * It processes Kline data, applies TA indicators, incorporates simplified Moneyfan logic,
 * and generates a combined series of data points suitable for visualization.
 */
object DselInterfaceAugmented {

    // Configurable TA parameters with default values
    var shortSmaPeriod: Int = 10
    var longSmaPeriod: Int = 20
    var rsiPeriod: Int = 14
    var rsiOverbought: Double = 70.0
    var rsiOversold: Double = 30.0

    // Simplified global Moneyfan states for the demo
    var crashProtectionActiveGlobally: Boolean = false
    val assetConfigs = mutableMapOf<String, DemoMoneyfanAssetConfig>()
    
    // ADZ state transition tracking
    private val adzStateTracking = mutableMapOf<String, ADZStateTracker>()

    /**
     * Generates a DSEL Series of [VisualGraphPointWithMoneyfanOutcome] by processing input Kline data,
     * calculating TA features, and applying Moneyfan logic.
     *
     * @param klineSeries The input DSEL Series of Kline data.
     * @param assetSymbol The symbol for the asset being processed (e.g., "BTC"). Used to manage Moneyfan asset configs.
     * @return A DSEL Series of [VisualGraphPointWithMoneyfanOutcome]. Returns an empty series if input is empty.
     */
    fun generateAugmentedVisualData(
        klineSeries: Indexed<Kline>,
        assetSymbol: String
    ): Indexed<VisualGraphPointWithMoneyfanOutcome> {

        if (klineSeries.size == 0) {
            console.warn("DselInterfaceAugmented: Input klineSeries is empty. Returning empty Series.")
            return 0 j { throw IndexOutOfBoundsException("Accessing empty augmented data series.") }
        }

        val assetConfig = assetConfigs.getOrPut(assetSymbol) {
            // Initialize with a default baseline from the first kline's close price if not already set.
            val firstClose = klineSeries[0].closePrice.toDouble()
            console.log("DselInterfaceAugmented: Initializing default config for $assetSymbol with baseline $firstClose")
            DemoMoneyfanAssetConfig(symbol = assetSymbol, baseline = firstClose)
        }

        // 1. Calculate TA features using DSEL-native indicators from com.ta4k.indicators
        val smaShortIndicator = SMAIndicator(klineSeries, shortSmaPeriod)
        val smaLongIndicator = SMAIndicator(klineSeries, longSmaPeriod)
        val rsiIndicator = RSIIndicator(klineSeries, rsiPeriod)

        val smaShortValues: Indexed<BigDecimal?> = smaShortIndicator.values
        val smaLongValues: Indexed<BigDecimal?> = smaLongIndicator.values
        val rsiValues: Indexed<BigDecimal?> = rsiIndicator.values

        // 2. DSEL Action: Combine Price, TA, and Moneyfan logic
        val augmentedPoints = mutableListOf<VisualGraphPointWithMoneyfanOutcome>()
        val size = klineSeries.size
        // Determine the maximum warmup period required by the indicators
        val warmupPeriod = maxOf(maxOf(shortSmaPeriod, longSmaPeriod), rsiPeriod, 1) -1


        for (i in 0 until size) {
            val kline = klineSeries[i]
            val smaS = smaShortValues[i]
            val smaL = smaLongValues[i]
            val rsi = rsiValues[i]
            var taSignal = TASignalType.NEUTRAL

            // Generate TA Signal (simplified logic)
            if (i > 0 && i > warmupPeriod) { // Ensure previous values are available and indicators are warm
                val prevSmaS = smaShortValues[i - 1]
                val prevSmaL = smaLongValues[i - 1]

                if (prevSmaS != null && prevSmaL != null && smaS != null && smaL != null) {
                    val isGoldenCross = prevSmaS < prevSmaL && smaS > smaL
                    val isDeathCross = prevSmaS > prevSmaL && smaS < smaL

                    if (isGoldenCross) {
                        taSignal = if (rsi != null && rsi.toDouble() < (rsiOverbought - 10.0)) TASignalType.STRONG_BUY else TASignalType.BUY
                    } else if (isDeathCross) {
                        taSignal = if (rsi != null && rsi.toDouble() > (rsiOversold + 10.0)) TASignalType.STRONG_SELL else TASignalType.SELL
                    }
                }
                // Add RSI overbought/oversold signals if no crossover signal
                if (taSignal == TASignalType.NEUTRAL && rsi != null) {
                    if (rsi.toDouble() < rsiOversold) taSignal = TASignalType.BUY
                    else if (rsi.toDouble() > rsiOverbought) taSignal = TASignalType.SELL
                }
            }

            // Moneyfan Logic Simulation
            val currentPrice = kline.closePrice
            val currentValue = currentPrice // Assuming 1 unit of asset for value calculation
            val baseline = assetConfig.baseline
            var deviationPercent: Double? = null

            if (baseline > 0.000001) { // Avoid division by zero or tiny baseline
                deviationPercent = (currentValue.toDouble() - baseline) / baseline
            }

            var determinedMoneyfanAction: MoneyfanActionType

            if (deviationPercent == null) {
                determinedMoneyfanAction = MoneyfanActionType.NO_ACTION_NO_BASELINE
            } else if (crashProtectionActiveGlobally) {
                determinedMoneyfanAction = MoneyfanActionType.HOLD_DUE_TO_CP
            } else {
                val effectiveHarvestTrigger = if (assetConfig.adzActive) assetConfig.adzHarvestTriggerPercent else assetConfig.harvestTriggerPercent
                val effectiveRebalanceTrigger = if (assetConfig.adzActive) assetConfig.adzRebalanceTriggerPercent else assetConfig.rebalanceTriggerPercent

                val basicMoneyfanSuggestion = when {
                    deviationPercent >= effectiveHarvestTrigger -> if (assetConfig.adzActive) MoneyfanActionType.OPPORTUNITY_ADZ_SELL else MoneyfanActionType.SUGGEST_HARVEST
                    deviationPercent <= effectiveRebalanceTrigger -> if (assetConfig.adzActive) MoneyfanActionType.OPPORTUNITY_ADZ_BUY else MoneyfanActionType.SUGGEST_REBALANCE_BUY
                    else -> MoneyfanActionType.HOLD
                }

                // Interaction between TA and Moneyfan suggestion
                determinedMoneyfanAction = when (basicMoneyfanSuggestion) {
                    MoneyfanActionType.SUGGEST_HARVEST, MoneyfanActionType.OPPORTUNITY_ADZ_SELL -> {
                        if (taSignal == TASignalType.STRONG_BUY || taSignal == TASignalType.BUY) MoneyfanActionType.HOLD // TA conflicts, hold
                        else basicMoneyfanSuggestion // TA aligns or is neutral
                    }
                    MoneyfanActionType.SUGGEST_REBALANCE_BUY, MoneyfanActionType.OPPORTUNITY_ADZ_BUY -> {
                        if (taSignal == TASignalType.STRONG_SELL || taSignal == TASignalType.SELL) MoneyfanActionType.HOLD // TA conflicts, hold
                        else basicMoneyfanSuggestion // TA aligns or is neutral
                    }
                    else -> basicMoneyfanSuggestion // HOLD or NO_ACTION
                }
            }
            // ADZ state transition logic implementation
            updateADZStateTransition(assetSymbol, assetConfig, deviationPercent, taSignal, determinedMoneyfanAction)

            augmentedPoints.add(
                VisualGraphPointWithMoneyfanOutcome(
                    index = i, kline = kline,
                    smaShort = smaS, smaLong = smaL, rsi = rsi, taSignal = taSignal,
                    assetSymbol = assetSymbol, baseline = baseline, currentValue = currentValue,
                    deviationPercent = deviationPercent, moneyfanAction = determinedMoneyfanAction,
                    adzActive = assetConfig.adzActive, cpActiveGlobally = crashProtectionActiveGlobally
                )
            )
        }
        // Convert the List to a DSEL Series for output
        return augmentedPoints.toIndexed() // Uses extension from DataHelper.kt
    }

    /**
     * Updates the baseline for a given asset symbol.
     * @param symbol The asset symbol (e.g., "BTC").
     * @param newBaseline The new baseline value.
     */
    fun updateBaseline(symbol: String, newBaseline: Double) {
        assetConfigs.getOrPut(symbol) { DemoMoneyfanAssetConfig(symbol, newBaseline) }.baseline = newBaseline
        console.log("DselInterfaceAugmented: Baseline for $symbol updated to $newBaseline")
    }

    /**
     * Toggles the Adaptive Dead Zone (ADZ) state for a given asset symbol.
     * @param symbol The asset symbol.
     * @return The new ADZ state.
     */
    fun toggleADZ(symbol: String): Boolean {
        val config = assetConfigs.getOrPut(symbol) { DemoMoneyfanAssetConfig(symbol, 0.0) } // Default if not exist
        config.adzActive = !config.adzActive
        console.log("DselInterfaceAugmented: ADZ for $symbol is now ${if(config.adzActive) "ON" else "OFF"}")
        return config.adzActive
    }

    /**
     * Toggles the global Crash Protection (CP) state.
     * @return The new CP state.
     */
    fun toggleGlobalCP(): Boolean {
        crashProtectionActiveGlobally = !crashProtectionActiveGlobally
        console.log("DselInterfaceAugmented: Global Crash Protection is now ${if(crashProtectionActiveGlobally) "ON" else "OFF"}")
        return crashProtectionActiveGlobally
    }
    
    /**
     * Updates ADZ state transitions based on market conditions and time factors
     */
    private fun updateADZStateTransition(
        assetSymbol: String, 
        assetConfig: DemoMoneyfanAssetConfig, 
        deviationPercent: Double?, 
        taSignal: TASignalType,
        moneyfanAction: MoneyfanActionType
    ) {
        val tracker = adzStateTracking.getOrPut(assetSymbol) { ADZStateTracker() }
        
        if (deviationPercent == null) return
        
        val absDeviation = kotlin.math.abs(deviationPercent)
        val deadZoneThreshold = 0.02 // 2% neutral zone
        val inDeadZone = absDeviation < deadZoneThreshold
        
        tracker.updateState(inDeadZone, taSignal, moneyfanAction)
        
        // Transition to ADZ if conditions met
        if (!assetConfig.adzActive && tracker.shouldEnterADZ()) {
            assetConfig.adzActive = true
            console.log("DselInterfaceAugmented: ADZ auto-activated for $assetSymbol after ${tracker.deadZoneCount} periods in dead zone")
        }
        
        // Exit ADZ if significant movement or action taken
        if (assetConfig.adzActive && tracker.shouldExitADZ()) {
            assetConfig.adzActive = false
            console.log("DselInterfaceAugmented: ADZ auto-deactivated for $assetSymbol due to ${tracker.exitReason}")
        }
    }
}

// Helper for maxOf if not available in current Kotlin/JS stdlib scope (e.g. commonMain without platform specifics)
// Should be available in kotlin.math.max in recent Kotlin versions for JS.
// private fun maxOf(a: Int, b: Int, c: Int): Int = kotlin.math.max(kotlin.math.max(a, b), c)
// Using vararg version for simplicity
private fun maxOf(vararg values: Int): Int = values.maxOrNull() ?: 0

/**
 * ADZ State Tracker for automatic transitions
 */
private class ADZStateTracker {
    var deadZoneCount: Int = 0
    var lastAction: MoneyfanActionType = MoneyfanActionType.HOLD
    var consecutiveNeutralSignals: Int = 0
    var exitReason: String = ""
    
    private val deadZoneThresholdForActivation = 10 // 10 periods of dead zone
    private val actionTakenExitThreshold = 3 // Exit after 3 periods with actual actions
    
    fun updateState(inDeadZone: Boolean, taSignal: TASignalType, moneyfanAction: MoneyfanActionType) {
        if (inDeadZone) {
            deadZoneCount++
        } else {
            deadZoneCount = 0
        }
        
        if (taSignal == TASignalType.NEUTRAL) {
            consecutiveNeutralSignals++
        } else {
            consecutiveNeutralSignals = 0
        }
        
        lastAction = moneyfanAction
    }
    
    fun shouldEnterADZ(): Boolean {
        return deadZoneCount >= deadZoneThresholdForActivation && consecutiveNeutralSignals >= 5
    }
    
    fun shouldExitADZ(): Boolean {
        // Exit if significant action taken
        if (lastAction in listOf(MoneyfanActionType.OPPORTUNITY_ADZ_BUY, MoneyfanActionType.OPPORTUNITY_ADZ_SELL)) {
            exitReason = "ADZ opportunity action taken"
            return true
        }
        
        // Exit if out of dead zone for too long
        if (deadZoneCount == 0 && consecutiveNeutralSignals < 3) {
            exitReason = "left dead zone with active signals"
            return true
        }
        
        return false
    }
}
