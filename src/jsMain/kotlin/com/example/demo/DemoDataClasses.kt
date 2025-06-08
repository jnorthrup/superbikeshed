package com.example.demo

import com.ta4k.core.model.Kline // Assuming Kline is in this package from ta4k module
import com.ta4k.core.model.BigDecimal // Assuming BigDecimal is in this package from ta4k module

/**
 * Data class representing a single point in the visual graph, combining kline data,
 * technical analysis indicators/signals, and moneyfan-derived outcomes.
 *
 * @property index The sequential index of this data point (e.g., kline index).
 * @property kline The original Kline data.
 * @property smaShort The value of the short-period Simple Moving Average.
 * @property smaLong The value of the long-period Simple Moving Average.
 * @property rsi The value of the Relative Strength Index.
 * @property taSignal The derived technical analysis signal (e.g., BUY, SELL, NEUTRAL).
 * @property assetSymbol The symbol of the asset being analyzed (e.g., "BTC").
 * @property baseline The current moneyfan baseline value for this asset.
 * @property currentValue The current market value of one unit of the asset (typically kline.closePrice).
 * @property deviationPercent The percentage deviation of the currentValue from the baseline.
 * @property moneyfanAction The suggested action or state derived from moneyfan logic combined with TA.
 * @property adzActive True if Adaptive Dead Zone is active for this asset at this point.
 * @property cpActiveGlobally True if global Crash Protection is active at this point.
 */
data class VisualGraphPointWithMoneyfanOutcome(
    val index: Int,
    val kline: Kline,
    // TA Features
    val smaShort: BigDecimal?,
    val smaLong: BigDecimal?,
    val rsi: BigDecimal?,
    val taSignal: TASignalType = TASignalType.NEUTRAL,
    // Moneyfan Outcomes
    val assetSymbol: String,
    val baseline: Double?, // Using Double for baseline as in moneyfan script; could be BigDecimal
    val currentValue: BigDecimal?,
    val deviationPercent: Double?,
    val moneyfanAction: MoneyfanActionType = MoneyfanActionType.HOLD,
    val adzActive: Boolean = false,
    val cpActiveGlobally: Boolean = false
)

/**
 * Enum representing derived technical analysis signals.
 */
enum class TASignalType {
    NEUTRAL,
    STRONG_BUY,
    BUY,
    STRONG_SELL,
    SELL
}

/**
 * Enum representing suggested actions or states derived from the combination of
 * moneyfan portfolio logic and technical analysis signals.
 */
enum class MoneyfanActionType {
    HOLD,                   // General hold recommendation
    SUGGEST_HARVEST,        // Deviation suggests harvesting profit
    SUGGEST_REBALANCE_BUY,  // Deviation suggests buying to rebalance
    OPPORTUNITY_ADZ_BUY,    // Tighter ADZ thresholds suggest a buy opportunity
    OPPORTUNITY_ADZ_SELL,   // Tighter ADZ thresholds suggest a sell/harvest opportunity
    HOLD_DUE_TO_CP,         // Crash Protection overrides other actions to HOLD
    NO_ACTION_NO_BASELINE   // No action can be determined as baseline is not set
}

/**
 * Simplified configuration for an asset within the moneyfan logic for demo purposes.
 *
 * @property symbol The asset symbol (e.g., "BTC").
 * @property baseline The current baseline value in USD against which deviations are calculated.
 * @property adzActive True if Adaptive Dead Zone logic is currently active for this asset.
 * @property harvestTriggerPercent Positive deviation percentage to trigger normal harvest.
 * @property rebalanceTriggerPercent Negative deviation percentage to trigger normal rebalance.
 * @property adzHarvestTriggerPercent Positive deviation percentage for ADZ harvest.
 * @property adzRebalanceTriggerPercent Negative deviation percentage for ADZ rebalance.
 */
data class DemoMoneyfanAssetConfig(
    val symbol: String,
    var baseline: Double, // Mutable to allow updates from UI in the demo
    var adzActive: Boolean = false, // Mutable for UI toggle
    // Default trigger percentages (can be overridden if needed per asset)
    val harvestTriggerPercent: Double = 0.05,       // +5%
    val rebalanceTriggerPercent: Double = -0.03,    // -3%
    val adzHarvestTriggerPercent: Double = 0.02,     // +2% (ADZ)
    val adzRebalanceTriggerPercent: Double = -0.015  // -1.5% (ADZ)
)
