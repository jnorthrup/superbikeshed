package moneyfan.trikeshed.nlp

import moneyfan.trikeshed.Indexed
import moneyfan.trikeshed.nlp.rql.RqlRootQuery

/**
 * Represents the structured output from an [NlpAgent] after it processes a natural language query.
 * This class is designed to be a comprehensive container for various forms of NLP analysis results.
 *
 * @property relevanceScores A [Indexed<Double>] containing relevance scores. These scores typically correspond
 *                           to the `itemsAsStrings` passed to the [NlpAgent.processQuery] method,
 *                           with each score indicating the relevance of the corresponding item string to the query.
 *                           Scores should ideally be normalized (e.g., between 0.0 for no relevance and 1.0 for high relevance).
 *                           The size of this series **must** match the size of the `itemsAsStrings` series that was processed.
 * @property structuredQuery An optional [RqlRootQuery] which is the result of attempting to parse the
 *                           `originalQuery` into a formal, structured query language (RQL).
 *                           This will be `null` if the agent could not parse the query into RQL,
 *                           or if the query was purely semantic and had no structural interpretation.
 * @property originalQuery The exact natural language query string that was submitted to the agent for processing.
 *                         This is useful for context and logging.
 * @property errors An optional list of error messages (strings) generated during the NLP processing.
 *                  This can include syntax errors from RQL parsing, warnings about ambiguity,
 *                  or notifications about parts of the query that could not be understood.
 *                  An empty list or `null` indicates that no errors were reported.
 */
data class NlpAgentResult(
    val relevanceScores: Indexed<Double>,
    val structuredQuery: RqlRootQuery?,
    val originalQuery: String,
    val errors: List<String>? = null
)

/**
 * Defines the contract for a Natural Language Processing (NLP) agent within the TrikeShed ecosystem.
 *
 * An `NlpAgent` is responsible for interpreting a natural language query string. It can operate
 * purely on the query itself to attempt structural parsing (e.g., into RQL) and/or it can
 * evaluate the query against a series of items (provided as strings) to determine their semantic relevance.
 * The agent then returns a consolidated [NlpAgentResult].
 *
 * **Note on Suspendability (Proof-of-Concept):**
 * For the current Proof-of-Concept (PoC) phase, the [processQuery] method is defined as
 * non-suspend (blocking). In a production system involving potentially long-running NLP model
 * interactions or I/O, this method would typically be a `suspend` function to allow for
 * asynchronous execution without blocking the calling thread.
 */
interface NlpAgent {
    /**
     * Processes a given natural language `query` string.
     * This function may also score the relevance of the query against each textual item provided in `itemsAsStrings`.
     *
     * Implementations are expected to:
     * 1.  Analyze the `query` for its semantic meaning and structural components.
     * 2.  If `itemsAsStrings` is provided and applicable, calculate a relevance score for each item string
     *     relative to the `query`. The resulting `relevanceScores` series in [NlpAgentResult]
     *     must align index-wise with `itemsAsStrings`.
     * 3.  Attempt to parse the `query` into a structured [RqlRootQuery].
     * 4.  Compile any errors, warnings, or informational messages encountered during processing.
     *
     * @param query The natural language query string to be processed by the agent.
     * @param itemsAsStrings A [Indexed<String>] where each element is a textual representation of an item.
     *                       The agent may use these to calculate context-sensitive relevance scores.
     *                       If the query is purely for structural parsing and relevance scores for items
     *                       are not needed or not applicable, an empty series might be passed.
     *                       The agent must handle this gracefully, typically by returning a `relevanceScores`
     *                       series of the same size as `itemsAsStrings`.
     * @return An [NlpAgentResult] object encapsulating the outcomes of the NLP analysis,
     *         including the series of relevance scores, an optional structured (RQL) query,
     *         the original query string, and a list of any errors.
     */
    fun processQuery(query: String, itemsAsStrings: Indexed<String>): NlpAgentResult // PoC: non-suspend
}
