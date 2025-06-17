package moneyfan.examples

import moneyfan.trikeshed.Series
import moneyfan.trikeshed.toSeries
import moneyfan.trikeshed.scope.HumanLanguageAgentScope
import moneyfan.trikeshed.nlp.HybridNlpAgentPoc
import moneyfan.trikeshed.nlp.NlpAgentResult // For casting or direct use if needed
import moneyfan.trikeshed.nlp.rql.* // RQL model classes
import moneyfan.trikeshed.focus // Series.focus extension

/**
 * Represents a sample data record for demonstration purposes with [HumanLanguageAgentScope].
 *
 * @property id A unique identifier.
 * @property name The name of the record/item.
 * @property category The category to which the item belongs.
 * @property value A numerical value associated with the item.
 * @property description A textual description of the item.
 */
data class DemoDataRecord(
    val id: Int,
    val name: String,
    val category: String,
    val value: Double,
    val description: String
) {
    override fun toString(): String {
        return "ID: $id, Name: '$name', Category: '$category', Value: $value, Desc: '$description'"
    }
}

/**
 * Demonstrates the functionality of the enhanced [HumanLanguageAgentScope]
 * using the [HybridNlpAgentPoc].
 *
 * This demo showcases how a natural language query can be processed to extract
 * both a structured RQL query and semantic relevance scores, and how these
 * are combined to filter a `Series` of data records.
 */
fun runAdvancedScopeDemo() {
    println("======== AdvancedScope (HumanLanguageAgentScope PoC) Demo Start ========")

    // 1. Create Sample Data
    val sampleList = listOf(
        DemoDataRecord(1, "Smart Thermostat", "electronics", 120.0, "An intelligent thermostat for home energy saving."),
        DemoDataRecord(2, "LED Bulb Pack", "lighting", 25.0, "Pack of 4 energy efficient LED bulbs."),
        DemoDataRecord(3, "Gaming Console X", "electronics", 499.0, "Next generation powerful gaming console with exclusive titles."),
        DemoDataRecord(4, "Organic Cotton T-Shirt", "apparel", 30.0, "Comfortable and eco-friendly t-shirt."),
        DemoDataRecord(5, "Advanced Drone", "gadgets", 850.0, "High-performance drone with 4K camera, a great gadget."),
        DemoDataRecord(6, "Smart Speaker Mini", "electronics", 49.0, "Compact smart speaker with voice assistant. A nice gadget."),
        DemoDataRecord(7, "Yoga Mat Premium", "fitness", 75.0, "High-density yoga mat for comfort and stability."),
        DemoDataRecord(8, "Noise Cancelling Headphones", "electronics", 250.0, "Premium headphones for immersive sound, an essential gadget for focus."),
        DemoDataRecord(9, "Kitchen Mixer Pro", "appliances", 150.0, "Powerful stand mixer for all your baking needs."),
        DemoDataRecord(10, "Portable SSD 1TB", "electronics", 120.0, "Fast and reliable portable storage gadget for your files.")
    )
    val dataSeries: Series<DemoDataRecord> = sampleList.toSeries()

    println("\n--- Original Data (${dataSeries.a} items) ---")
    dataSeries.`▶`.forEach { println(it) }

    // 2. Setup Scope Components
    val agent = HybridNlpAgentPoc() // Using the PoC agent

    val itemToStringConverter: (DemoDataRecord) -> String = { record ->
        // For semantic scoring, combine fields that might contain relevant keywords
        "${record.name} ${record.category} ${record.description}"
    }

    val itemToFieldsExtractor: (DemoDataRecord) -> Map<String, Any?> = { record ->
        // For RQL filtering, map record properties to field names RQL might use
        mapOf(
            "id" to record.id,
            "name" to record.name,
            "category" to record.category,
            "value" to record.value,
            "description" to record.description // RQL could also query description
        )
    }

    // 3. Define a Hybrid Query
    // This query has parts that can be parsed into RQL ("value > 50", "category is 'electronics'")
    // and parts that are more semantic ("important gadgets").
    val query = "show important gadgets with value > 50 and category is 'electronics'"
    println("\n--- Processing Query ---")
    println("Query: \"$query\"")

    // 4. Demonstrate what the HybridNlpAgentPoc produces (for transparency in the demo)
    // First, convert all items to strings for the agent
    val allItemsAsStrings = dataSeries.a j { itemToStringConverter(dataSeries.b(it)) }
    val nlpAnalysisResult = agent.processQuery(query, allItemsAsStrings)

    println("\n--- NLP Agent Analysis Result (PoC) ---")
    println("  Original Query: ${nlpAnalysisResult.originalQuery}")
    if (nlpAnalysisResult.structuredQuery != null) {
        println("  Parsed RQL Query: ${nlpAnalysisResult.structuredQuery}") // Basic toString of RqlRootQuery
    } else {
        println("  Parsed RQL Query: None (or parsing failed)")
    }
    if (nlpAnalysisResult.errors != null && nlpAnalysisResult.errors.isNotEmpty()) {
        println("  NLP Agent Errors: ${nlpAnalysisResult.errors}")
    }

    println("\n  Semantic Scores for ALL original items (Query keywords: 'important', 'gadgets'):")
    for (i in 0 until nlpAnalysisResult.relevanceScores.a) {
        val record = dataSeries.b(i)
        val score = nlpAnalysisResult.relevanceScores.b(i)
        println("    Item ID ${record.id} ('${record.name}'): Score = ${score.format(2)}")
    }


    // 5. Create and Apply HumanLanguageAgentScope
    // The scope will internally use the agent again.
    // We set a semanticScoreThreshold, e.g., 0.1, to see some results from semantic part.
    // Query keywords "important", "gadgets".
    // "gadgets" in desc of item 5 (Drone), item 6 (Speaker), item 8 (Headphones), item 10 (SSD)
    // "important" is not in any item, so max semantic score will be 0.5 if only "gadgets" matches.
    val semanticThreshold = 0.2 // If an item matches "gadgets", score is 0.5 (1 out of 2 query keywords)

    println("\n--- Applying HumanLanguageAgentScope (Semantic Threshold: $semanticThreshold) ---")
    println("The scope will first apply RQL (if parsed), then filter by semantic score.")

    val scope = HumanLanguageAgentScope(
        nlpQuery = query,
        itemToStringConverter = itemToStringConverter,
        itemToFieldsExtractor = itemToFieldsExtractor,
        semanticScoreThreshold = semanticThreshold, // Use the defined threshold
        agent = agent // Using the same agent instance
    )

    val focusedSeries = dataSeries.focus(scope)

    // 6. Print Final Focused Output
    println("\n--- Final Focused Series (${focusedSeries.a} items) ---")
    if (focusedSeries.isEmpty()) {
        println("No items matched both RQL (if any) and semantic score threshold.")
    } else {
        focusedSeries.`▶`.forEach { println(it) }
    }

    println("\n--- Explanation of Results ---")
    println("1. RQL Parsing: The query \"$query\" was parsed by `SimpleRqlParser`.")
    println("   - The RQL part extracted should be approximately: (value > 50 AND category IS 'electronics').")
    println("2. Semantic Scoring: All items were scored against 'important gadgets'. Keywords: 'important', 'gadgets'.")
    println("   - Items containing 'gadget' in their name/desc got a score (e.g., Drone, Speaker Mini, Headphones, SSD might get ~0.5).")
    println("3. Filtering Steps by HumanLanguageAgentScope:")
    println("   a. RQL Filter: Items were first filtered by the RQL query (value > 50 AND category 'electronics').")
    println("      - Expected to pass RQL: Smart Thermostat (120), Gaming Console X (499), Headphones (250), Portable SSD (120).")
    println("   b. Semantic Filter: The RQL-filtered items were then evaluated against their semantic scores.")
    println("      - From the RQL-passers, those with score >= $semanticThreshold were kept.")
    println("      - E.g., Headphones (score ~0.5 if 'gadget' matches), Portable SSD (score ~0.5 if 'gadget' matches) should pass.")
    println("      - Smart Thermostat, Gaming Console X might not pass semantic if they don't contain 'gadget' or 'important'.")


    println("\n======== AdvancedScope Demo End ========")
}

/*
// Conceptual main
fun main() {
    runAdvancedScopeDemo()
}
*/

// Helper for formatting doubles, assuming it's not available elsewhere easily
// Moved from DemoPortfolioRow as it's more general.
internal fun Double.format(digits: Int): String = this.asDynamic().toFixed(digits)
