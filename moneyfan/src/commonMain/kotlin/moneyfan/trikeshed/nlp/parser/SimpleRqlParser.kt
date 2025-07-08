package moneyfan.trikeshed.nlp.parser

import moneyfan.trikeshed.nlp.rql.*

/**
 * A **Proof-of-Concept (PoC)** parser for a very simple, predefined structured query language
 * that aims to mimic some natural language phrasing for conditions. This parser translates
 * specific string patterns into a structured [RqlRootQuery] object.
 *
 * **Key Features & Approach:**
 * - Uses regular expressions to match predefined query patterns.
 * - Supports basic conditions on attributes (e.g., "price > 100", "name contains 'widget'").
 * - Supports a "BETWEEN ... AND ..." clause for numeric ranges.
 * - Handles simple, non-nested "AND" / "OR" conjunctions by splitting the query string.
 * - Operator keywords (e.g., "is greater than", "equals", ">", "==") are mapped to [RqlOperator] enums.
 * - Values are parsed into `Double`, `Int`, or kept as `String` (after unquoting).
 *
 * **Significant Limitations (PoC Nature):**
 * - **Limited Grammar:** Only understands a very restricted set of patterns. It does not perform
 *   true linguistic analysis, understand synonyms, resolve complex grammar, or handle ambiguity.
 * - **No True NLP:** This is a pattern-matching system, not an NLP engine.
 * - **Single-Level Conjunctions:** "AND"/"OR" are handled by splitting the string once; nested logical
 *   expressions (e.g., `(A AND B) OR C`) are not supported.
 * - **Fixed Operator Precedence:** The order of attempting to parse (e.g., compound before simple)
 *   defines a rudimentary precedence. No complex operator precedence is handled.
 * - **Basic Value Parsing:** Type inference for values is minimal.
 * - **No Entity/Attribute Validation:** Assumes attribute names are valid and does not link them to
 *   specific [RqlEntity] types during parsing.
 *
 * This parser's primary goal is to serve as a testbed for the RQL data model and to demonstrate
 * the transformation of query-like strings into this structured representation. For robust,
 * flexible natural language query understanding, a proper NLP library and more sophisticated
 * parsing techniques (e.g., generating an Abstract Syntax Tree) would be required.
 *
 * **Example Supported Queries:**
 * - "price > 100"
 * - "volume is less than 50000"
 * - "name contains 'energy'"
 * - "pe_ratio between 10 and 20"
 * - "market_cap > 1000000000 and sector equals 'technology'"
 */
class SimpleRqlParser {

    // Case-insensitive map of operator strings to RqlOperator enums.
    // Longer phrases are implicitly preferred by the regex construction if they appear first or are more specific.
    internal val operatorMap: Map<String, RqlOperator> = mapOf(
        "is greater than or equals" to RqlOperator.GREATER_THAN_OR_EQUALS,
        "is greater than or equal to" to RqlOperator.GREATER_THAN_OR_EQUALS,
        "is less than or equals" to RqlOperator.LESS_THAN_OR_EQUALS,
        "is less than or equal to" to RqlOperator.LESS_THAN_OR_EQUALS,
        "is not equal to" to RqlOperator.NOT_EQUALS,
        "not equals" to RqlOperator.NOT_EQUALS,
        "is greater than" to RqlOperator.GREATER_THAN,
        "is less than" to RqlOperator.LESS_THAN,
        "is equal to" to RqlOperator.EQUALS,
        "equals" to RqlOperator.EQUALS,
        "is null" to RqlOperator.IS_NULL,
        "is not null" to RqlOperator.IS_NOT_NULL,
        "is" to RqlOperator.EQUALS, // "is" often implies equality for simple values
        "!=" to RqlOperator.NOT_EQUALS,
        "==" to RqlOperator.EQUALS,
        ">=" to RqlOperator.GREATER_THAN_OR_EQUALS,
        "<=" to RqlOperator.LESS_THAN_OR_EQUALS,
        ">" to RqlOperator.GREATER_THAN,
        "<" to RqlOperator.LESS_THAN,
        "contains" to RqlOperator.CONTAINS,
        "does not contain" to RqlOperator.NOT_CONTAINS,
        "not contains" to RqlOperator.NOT_CONTAINS,
        "between" to RqlOperator.BETWEEN, // Specifically handled by betweenConditionPattern
        "in" to RqlOperator.IN, // Basic support; value parsing for lists is simple for PoC
        "not in" to RqlOperator.NOT_IN
    )

    // Dynamically builds a regex for "ATTRIBUTE OPERATOR VALUE" pattern.
    // Operators are sorted by length (descending) to ensure longer phrases (e.g., "is greater than")
    // are matched before shorter, potentially ambiguous ones (e.g., "is").
    internal val generalConditionPattern = run {
        val sortedOperatorStrings = operatorMap.keys
            .filterNot { it == "between" } // "between" is handled by a more specific regex.
            .sortedByDescending { it.length }
            .joinToString("|") { Regex.escape(it) }
        Regex("""^(.+?)\s+($sortedOperatorStrings)\s+(.+)$""", RegexOption.IGNORE_CASE)
    }

    // Regex for "ATTRIBUTE between VALUE1 and VALUE2"
    internal val betweenConditionPattern =
        Regex("""^(.+?)\s+between\s+(.+?)\s+and\s+(.+)$""", RegexOption.IGNORE_CASE)

    /**
     * Attempts to parse a natural language-like query string into a structured [RqlRootQuery].
     *
     * The parsing strategy is:
     * 1. Trim whitespace from the query.
     * 2. Attempt to split the query by " or " (case-insensitive). If successful and two parts result,
     *    parse each part as a potential condition and combine them into an [RqlCompoundQuery] with `OR`.
     * 3. If not an "OR" query, attempt to split by " and " (case-insensitive). If successful and two parts result,
     *    parse each part and combine into an [RqlCompoundQuery] with `AND`.
     *    (This implies "AND" has higher precedence if a query contains both "AND" and "OR" at the same conceptual level,
     *     e.g. "X or Y and Z" would be parsed as "X or (Y and Z)" if "Y and Z" is parsable as a unit by `parseSingleOrBetween`).
     *     However, the current split `limit=2` means it's very basic.
     * 4. If not a compound query, attempt to parse the entire string as a single condition
     *    (either a "BETWEEN" clause or a general "ATTRIBUTE OPERATOR VALUE" pattern).
     *
     * If any parsing step is successful, the resulting [RqlQueryNode] is wrapped in an [RqlRootQuery].
     * If no pattern matches, returns `null`.
     *
     * @param query The query string to parse.
     * @return An [RqlRootQuery] object if parsing is successful, or `null` if the query string
     *         does not match any of the predefined PoC patterns.
     */
    fun parse(query: String): RqlRootQuery? {
        val trimmedQuery = query.trim()

        // PoC: Simple handling for one level of OR/AND. "OR" is checked first.
        // A query like "A or B and C" would be (A) or (B and C) due to split limit and order.
        // A query like "A and B or C" would be (A and B) or (C).
        // This is not robust operator precedence but a consequence of simple string splitting.
        val orParts = trimmedQuery.split(" or ", ignoreCase = true, limit = 2)
        if (orParts.size == 2) {
            val q1Node = parseSingleOrCompound(orParts[0]) // Try to parse left part, could be simple or already compound if "and" is there
            val q2Node = parseSingleOrCompound(orParts[1]) // Try to parse right part
            if (q1Node != null && q2Node != null) {
                return RqlRootQuery(RqlCompoundQuery(RqlLogicalOperator.OR, listOf(q1Node, q2Node)))
            }
        }
        // If not OR, or if OR parsing failed, try AND for the whole string
        return parseSingleOrCompound(trimmedQuery)?.let { RqlRootQuery(it) }
    }

    /**
     * Parses a query part that could be a single condition or an AND-compound condition.
     * This is used by `parse` to handle parts of an OR split, or the whole query if no OR is found.
     */
    internal fun parseSingleOrCompound(queryPart: String): RqlQueryNode? {
        val trimmedQueryPart = queryPart.trim()
        val andParts = trimmedQueryPart.split(" and ", ignoreCase = true, limit = 2)
        if (andParts.size == 2) {
            val q1 = parseAtomicCondition(andParts[0]) // Atomic conditions for AND parts
            val q2 = parseAtomicCondition(andParts[1])
            if (q1 != null && q2 != null) {
                // Both q1 and q2 must be RqlSimpleQuery as parseAtomicCondition returns that or null
                return RqlCompoundQuery(RqlLogicalOperator.AND, listOf(q1, q2))
            }
        }
        // If not an AND, or if AND parsing failed, parse as a single atomic condition
        return parseAtomicCondition(trimmedQueryPart)
    }


    /**
     * Parses a string that is expected to be a single, non-compound condition
     * (either a "BETWEEN" clause or a general "ATTRIBUTE OPERATOR VALUE" pattern).
     *
     * @param queryPart The string segment representing a single condition.
     * @return An [RqlSimpleQuery] if parsing is successful, or `null` otherwise.
     */
    internal fun parseAtomicCondition(queryPart: String): RqlSimpleQuery? {
        val trimmedQueryPart = queryPart.trim()
        // Try "BETWEEN" pattern first due to its specific structure
        betweenConditionPattern.matchEntire(trimmedQueryPart)?.let { match ->
            val attrName = match.groupValues[1].trim()
            val value1Str = match.groupValues[2].trim()
            val value2Str = match.groupValues[3].trim()

            val val1 = parseNumericValue(value1Str) // BETWEEN typically implies numeric/date range
            val val2 = parseNumericValue(value2Str)

            if (val1 != null && val2 != null) {
                val attribute = RqlAttribute(attrName)
                val condition = RqlCondition(attribute, RqlOperator.BETWEEN, Pair(val1, val2))
                return RqlSimpleQuery(condition)
            }
        }

        // Try general "ATTRIBUTE OPERATOR VALUE" pattern
        generalConditionPattern.matchEntire(trimmedQueryPart)?.let { match ->
            val attrName = match.groupValues[1].trim()
            val operatorStr = match.groupValues[2].trim().lowercase() // Normalize for map lookup
            val valueStr = match.groupValues[3].trim()

            operatorMap[operatorStr]?.let { operator ->
                val parsedValue = if (operator == RqlOperator.IS_NULL || operator == RqlOperator.IS_NOT_NULL) {
                    null // Value is not used for IS (NOT) NULL operators
                } else {
                    parseValue(valueStr, operator)
                }

                // Check if a value was mandatory but couldn't be parsed (and wasn't the string "null")
                if (parsedValue == null &&
                    !(operator == RqlOperator.IS_NULL || operator == RqlOperator.IS_NOT_NULL ||
                      (valueStr.equals("null", ignoreCase = true) && (operator == RqlOperator.EQUALS || operator == RqlOperator.NOT_EQUALS)))
                ) {
                    // This indicates a value string that parseValue couldn't convert to a number,
                    // and it wasn't a literal "null" for an equality check.
                    // For PoC, if parseValue returned the string itself, it's "parsed".
                    // This condition might be too strict if parseValue correctly returns a string for non-numeric types.
                    // For now, the RqlCondition's init block is the primary validator of value presence/type.
                }

                val attribute = RqlAttribute(attrName)
                // Let RqlCondition's init block validate the value against the operator.
                try {
                    val condition = RqlCondition(attribute, operator, parsedValue)
                    return RqlSimpleQuery(condition)
                } catch (e: IllegalArgumentException) {
                    // RqlCondition validation failed (e.g., wrong value type for operator)
                    // Optionally log e.message
                    return null
                }
            }
        }
        return null // No pattern matched
    }

    /**
     * Parses a string value from a query, attempting to convert it to Double, then Int,
     * or falling back to a cleaned (unquoted) String.
     * Handles the literal string "null" for equality checks.
     *
     * @param valueStr The raw string value extracted from the query.
     * @param operator The [RqlOperator] being used with this value, to guide parsing.
     * @return The parsed value ([Double], [Int], [String]), or `null` if the string was "null"
     *         (for equality checks) or if the operator is `IS_NULL`/`IS_NOT_NULL`.
     */
    internal fun parseValue(valueStr: String, operator: RqlOperator): Any? {
        val cleanedValueStr = valueStr.removeQuotes().trim()

        if (operator == RqlOperator.IS_NULL || operator == RqlOperator.IS_NOT_NULL) {
            return null // Value is not applicable for these operators.
        }
        // For EQUALS or NOT_EQUALS, if the string is "null", treat it as a literal null value.
        if (cleanedValueStr.equals("null", ignoreCase = true) &&
            (operator == RqlOperator.EQUALS || operator == RqlOperator.NOT_EQUALS)) {
            return null
        }

        // Attempt numeric parsing for relevant operators or if it looks like a number
        // For PoC, always try numeric conversion first.
        cleanedValueStr.toDoubleOrNull()?.let { return it }
        cleanedValueStr.toIntOrNull()?.let { return it }

        // Default to the cleaned string for operators like CONTAINS, or if no numeric conversion matched.
        return cleanedValueStr
    }

    /**
     * Parses a string value expected to be numeric (Double or Int) for contexts like BETWEEN.
     *
     * @param valueStr The raw string value.
     * @return A [Number] ([Double] or [Int]) if parsing is successful, otherwise `null`.
     */
    internal fun parseNumericValue(valueStr: String): Number? {
        val cleanedValueStr = valueStr.removeQuotes().trim()
        // Prioritize Double for wider range, then Int.
        cleanedValueStr.toDoubleOrNull()?.let { return it }
        cleanedValueStr.toIntOrNull()?.let { return it }
        return null
    }

    /**
     * Removes leading/trailing single or double quotes from a string.
     */
    internal fun String.removeQuotes(): String {
        return this.removeSurrounding("\"").removeSurrounding("'")
    }
}
