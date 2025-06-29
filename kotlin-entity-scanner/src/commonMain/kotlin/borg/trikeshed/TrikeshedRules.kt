package borg.trikeshed

import borg.entityscanner.*
import borg.trikeshed.lib.*

/**
 * Trikeshed-specific Forward and Backward Chaining Rules (Additive)
 *
 * Allows multiple Trikeshed rule chains to be composed and aggregated, similar to ccek.
 */

object TrikeshedForwardRules {
    private val forwardChains = mutableListOf<() -> ForwardRuleSeries>()

    /**
     * Register a new forward rule chain (additive)
     */
    fun register(chain: () -> ForwardRuleSeries) {
        forwardChains.add(chain)
    }

    /**
     * Aggregate all registered forward rule chains
     */
    fun all(): ForwardRuleSeries =
        forwardChains.flatMap { it() }.toSeries()

    /**
     * Example: Built-in Trikeshed annotation rule
     */
    fun trikeshedAnnotationChain(): ForwardRuleSeries = listOf(
        createForwardRule(
            id = "trikeshed_annotation_detect",
            weight = 0.95,
            window = 5,
            condition = { context, pos ->
                lookAhead(context, pos, 2) == "@Trikeshed"
            },
            action = { context, pos ->
                markEntity(context, pos, 99u)
            }
        )
    ).toSeries()

    init {
        // Register built-in rules by default
        register(::trikeshedAnnotationChain)
    }
}

object TrikeshedBackwardRules {
    private val backwardChains = mutableListOf<() -> BackwardRuleSeries>()

    /**
     * Register a new backward rule chain (additive)
     */
    fun register(chain: () -> BackwardRuleSeries) {
        backwardChains.add(chain)
    }

    /**
     * Aggregate all registered backward rule chains
     */
    fun all(): BackwardRuleSeries =
        backwardChains.flatMap { it() }.toSeries()

    /**
     * Example: Built-in Trikeshed usage validation rule
     */
    fun trikeshedUsageValidationChain(): BackwardRuleSeries = listOf(
        createBackwardRule(
            id = "trikeshed_usage_validate",
            weight = 0.9,
            window = 10,
            condition = { context, pos ->
                lookBehind(context, pos, 10).contains("@Trikeshed")
            },
            action = { context, pos ->
                validateTrikeshedUsage(context, pos)
            }
        )
    ).toSeries()

    init {
        // Register built-in rules by default
        register(::trikeshedUsageValidationChain)
    }
}

// --- Helper stubs for Trikeshed-specific actions ---
private fun markEntity(context: ParseContext, pos: Int, entity: UByte): ParseContext = context
private fun validateTrikeshedUsage(context: ParseContext, pos: Int): ParseContext = context 