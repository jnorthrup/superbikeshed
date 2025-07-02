package nexus.ai

import nexus.data.*
import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Data-Aware AI Agent that combines LLM capabilities with DataFrame operations
 * 
 * This creates an intelligent agent that can:
 * - Analyze data using natural language
 * - Generate code for data transformations
 * - Execute data operations based on prompts
 * - Learn from data patterns
 */
class DataAwareAgent(
    private val llmProvider: LLMProvider,
    private val enableAutoExecution: Boolean = false
) {
    
    /**
     * Analyze a DataFrame using natural language
     */
    suspend fun analyzeData(
        dataFrame: TrikeShedDataFrame,
        query: String
    ): AnalysisResult {
        // Generate data summary for context
        val summary = generateDataSummary(dataFrame)
        
        // Create prompt with data context
        val prompt = """
        You are analyzing a TrikeShed DataFrame with the following structure:
        $summary
        
        User Query: $query
        
        Provide:
        1. Direct answer to the query
        2. Relevant statistics or insights
        3. Suggested further analyses
        4. Code to perform the analysis (using TrikeShed DataFrame API)
        """.trimIndent()
        
        val response = llmProvider.complete(prompt)
        
        // Parse response to extract code
        val code = extractCode(response)
        
        // Optionally execute the generated code
        val executionResult = if (enableAutoExecution && code != null) {
            executeDataCode(dataFrame, code)
        } else null
        
        return AnalysisResult(
            answer = response,
            generatedCode = code,
            executionResult = executionResult,
            dataFrame = dataFrame
        )
    }
    
    /**
     * Generate data transformation based on natural language
     */
    suspend fun transformData(
        dataFrame: TrikeShedDataFrame,
        instruction: String
    ): TransformResult {
        val summary = generateDataSummary(dataFrame)
        
        val prompt = """
        Generate TrikeShed DataFrame code to transform data according to this instruction:
        "$instruction"
        
        Current DataFrame structure:
        $summary
        
        Generate Kotlin code that:
        1. Takes a TrikeShedDataFrame as input
        2. Applies the requested transformation
        3. Returns the transformed TrikeShedDataFrame
        
        Use only these available operations:
        - select(columns...)
        - filter { row -> predicate }
        - map { row -> transformedRow }
        - groupBy(column)
        - sum(column), mean(column), count()
        
        Return ONLY the code, no explanation.
        """.trimIndent()
        
        val code = llmProvider.complete(prompt)
        
        return TransformResult(
            instruction = instruction,
            generatedCode = code,
            transformed = if (enableAutoExecution) {
                executeTransformation(dataFrame, code)
            } else null
        )
    }
    
    /**
     * Interactive data exploration session
     */
    suspend fun exploreInteractively(
        dataFrame: TrikeShedDataFrame
    ): Flow<ExplorationStep> = flow {
        var currentDf = dataFrame
        val history = mutableListOf<ExplorationStep>()
        
        // Initial analysis
        val initial = analyzeData(currentDf, "What are the key characteristics of this dataset?")
        emit(ExplorationStep(
            stepNumber = 0,
            action = "Initial Analysis",
            result = initial,
            dataFrame = currentDf
        ))
        
        // Generate exploration suggestions
        val suggestions = generateExplorationSuggestions(currentDf, history)
        
        suggestions.forEachIndexed { index, suggestion ->
            val result = analyzeData(currentDf, suggestion)
            
            val step = ExplorationStep(
                stepNumber = index + 1,
                action = suggestion,
                result = result,
                dataFrame = currentDf
            )
            
            emit(step)
            history.add(step)
            
            // Update current DataFrame if transformation occurred
            result.executionResult?.let { newDf ->
                if (newDf is TrikeShedDataFrame) {
                    currentDf = newDf
                }
            }
            
            delay(1000) // Pause between steps
        }
    }
    
    /**
     * Generate insights from data patterns
     */
    suspend fun generateInsights(
        dataFrame: TrikeShedDataFrame,
        focusAreas: List<String> = emptyList()
    ): Indexed<Insight> {
        val insights = mutableListOf<Insight>()
        
        // Statistical insights
        val statsPrompt = """
        Analyze this data for statistical patterns:
        ${generateDataSummary(dataFrame)}
        
        Focus on: ${focusAreas.joinToString(", ").ifEmpty { "general patterns" }}
        
        Provide insights about:
        - Distributions
        - Correlations  
        - Anomalies
        - Trends
        """.trimIndent()
        
        val statsInsight = llmProvider.complete(statsPrompt)
        insights.add(Insight(
            type = InsightType.STATISTICAL,
            description = statsInsight,
            confidence = 0.8
        ))
        
        // Business insights
        if (focusAreas.isNotEmpty()) {
            val businessPrompt = """
            What business insights can be derived from this data?
            ${generateDataSummary(dataFrame)}
            
            Consider: ${focusAreas.joinToString(", ")}
            """.trimIndent()
            
            val businessInsight = llmProvider.complete(businessPrompt)
            insights.add(Insight(
                type = InsightType.BUSINESS,
                description = businessInsight,
                confidence = 0.7
            ))
        }
        
        return insights.size j { insights[it] }
    }
    
    /**
     * Learn from data to improve future analyses
     */
    suspend fun learnFromData(
        dataFrame: TrikeShedDataFrame,
        feedback: Indexed<Feedback>
    ): LearningResult {
        // Analyze patterns in successful queries
        val successfulPatterns = feedback.toList()
            .filter { it.wasHelpful }
            .map { it.query }
        
        val prompt = """
        Based on these successful data analysis queries:
        ${successfulPatterns.joinToString("\n")}
        
        And this data structure:
        ${generateDataSummary(dataFrame)}
        
        Generate:
        1. Common query patterns
        2. Useful analysis templates
        3. Performance optimization suggestions
        """.trimIndent()
        
        val learning = llmProvider.complete(prompt)
        
        return LearningResult(
            patterns = extractPatterns(learning),
            templates = extractTemplates(learning),
            optimizations = extractOptimizations(learning)
        )
    }
    
    /**
     * Generate executable data pipeline
     */
    suspend fun generatePipeline(
        dataFrame: TrikeShedDataFrame,
        goal: String
    ): DataPipeline {
        val prompt = """
        Create a data processing pipeline to achieve this goal:
        "$goal"
        
        Starting with this data:
        ${generateDataSummary(dataFrame)}
        
        Generate a sequence of TrikeShed DataFrame operations that:
        1. Processes the data step by step
        2. Includes intermediate validations
        3. Handles edge cases
        4. Produces the desired output
        
        Format each step as:
        STEP N: description
        CODE: dataFrame.operation()
        """.trimIndent()
        
        val pipelineSpec = llmProvider.complete(prompt)
        val steps = parsePipelineSteps(pipelineSpec)
        
        return DataPipeline(
            goal = goal,
            steps = steps,
            inputSchema = dataFrame.schema,
            estimatedRows = dataFrame.count()
        )
    }
    
    // Helper functions
    
    private fun generateDataSummary(df: TrikeShedDataFrame): String {
        return """
        Rows: ${df.count()}
        Columns: ${df.schema.toList().joinToString(", ") { "${it.a}: ${it.b}" }}
        Sample data: [First 3 rows shown]
        """.trimIndent()
    }
    
    private fun extractCode(response: String): String? {
        // Extract code blocks from response
        val codePattern = """```kotlin?\s*\n(.*?)\n```""".toRegex(RegexOption.DOT_MATCHES_ALL)
        return codePattern.find(response)?.groupValues?.get(1)
    }
    
    private suspend fun executeDataCode(df: TrikeShedDataFrame, code: String): Any? {
        // In real implementation, this would safely execute the code
        // For now, return a placeholder
        return "Code execution result"
    }
    
    private suspend fun executeTransformation(df: TrikeShedDataFrame, code: String): TrikeShedDataFrame? {
        // Execute transformation code
        // For now, return the original DataFrame
        return df
    }
    
    private suspend fun generateExplorationSuggestions(
        df: TrikeShedDataFrame,
        history: List<ExplorationStep>
    ): List<String> {
        val historyContext = history.joinToString("\n") { 
            "- ${it.action}: ${it.result.answer.take(100)}..."
        }
        
        val prompt = """
        Based on this data exploration history:
        $historyContext
        
        Suggest 3 next analysis steps that would provide valuable insights.
        Return as a simple list.
        """.trimIndent()
        
        val suggestions = llmProvider.complete(prompt)
        return suggestions.lines()
            .filter { it.trim().isNotEmpty() }
            .take(3)
    }
    
    private fun extractPatterns(learning: String): Indexed<String> {
        // Extract patterns from learning response
        return 3 j { i -> "Pattern $i" }
    }
    
    private fun extractTemplates(learning: String): Indexed<AnalysisTemplate> {
        // Extract templates from learning response
        return 2 j { i -> 
            AnalysisTemplate(
                name = "Template $i",
                description = "Analysis template",
                code = "// Template code"
            )
        }
    }
    
    private fun extractOptimizations(learning: String): Indexed<String> {
        // Extract optimizations from learning response
        return 2 j { i -> "Optimization $i" }
    }
    
    private fun parsePipelineSteps(spec: String): Indexed<PipelineStep> {
        // Parse pipeline specification into steps
        val steps = mutableListOf<PipelineStep>()
        
        val stepPattern = """STEP (\d+): (.+)\nCODE: (.+)""".toRegex()
        stepPattern.findAll(spec).forEach { match ->
            val (_, number, desc, code) = match.groupValues
            steps.add(PipelineStep(
                number = number.toInt(),
                description = desc,
                code = code,
                validation = null
            ))
        }
        
        return steps.size j { steps[it] }
    }
}

// Data classes for results

data class AnalysisResult(
    val answer: String,
    val generatedCode: String?,
    val executionResult: Any?,
    val dataFrame: TrikeShedDataFrame
)

data class TransformResult(
    val instruction: String,
    val generatedCode: String,
    val transformed: TrikeShedDataFrame?
)

data class ExplorationStep(
    val stepNumber: Int,
    val action: String,
    val result: AnalysisResult,
    val dataFrame: TrikeShedDataFrame
)

data class Insight(
    val type: InsightType,
    val description: String,
    val confidence: Double
)

enum class InsightType {
    STATISTICAL,
    BUSINESS,
    ANOMALY,
    TREND,
    CORRELATION
}

data class Feedback(
    val query: String,
    val wasHelpful: Boolean,
    val rating: Int,
    val comment: String?
)

data class LearningResult(
    val patterns: Indexed<String>,
    val templates: Indexed<AnalysisTemplate>,
    val optimizations: Indexed<String>
)

data class AnalysisTemplate(
    val name: String,
    val description: String,
    val code: String
)

data class DataPipeline(
    val goal: String,
    val steps: Indexed<PipelineStep>,
    val inputSchema: Schema,
    val estimatedRows: Int
)

data class PipelineStep(
    val number: Int,
    val description: String,
    val code: String,
    val validation: String?
)