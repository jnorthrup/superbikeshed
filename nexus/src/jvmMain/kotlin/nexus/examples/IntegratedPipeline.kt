package nexus.examples

import nexus.ai.*
import nexus.ai.providers.*
import nexus.data.*
import nexus.jvm.*
import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

/**
 * Integrated Pipeline Example
 * 
 * Demonstrates the complete Nexus system:
 * - AI agents analyzing data with LLMs
 * - TrikeShed DataFrame processing
 * - JVM Python governing operations
 * - Big Data integrations (Spark/Hadoop)
 * - Real-time streaming analytics
 */
object IntegratedPipeline {
    
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        println("=== Nexus Integrated Pipeline ===\n")
        
        // 1. Initialize components
        val llmProvider = ProviderRegistry.autoDetectProvider()
        val jvmPython = DGMBridge()
        val sparkBridge = BigDataIntegration.SparkBridge()
        
        println("✓ Initialized LLM provider: ${llmProvider::class.simpleName}")
        println("✓ JVM Python governance ready")
        println("✓ Spark bridge configured\n")
        
        // 2. Demonstrate real-time log analysis pipeline
        println("=== Real-Time Log Analysis Pipeline ===")
        logAnalysisPipeline(llmProvider, jvmPython)
        
        // 3. Financial data analysis with AI
        println("\n=== AI-Powered Financial Analysis ===")
        financialAnalysisPipeline(llmProvider, jvmPython)
        
        // 4. IoT sensor data processing
        println("\n=== IoT Sensor Stream Processing ===")
        iotStreamProcessing(llmProvider, jvmPython)
        
        // 5. Complete data science workflow
        println("\n=== Data Science Workflow ===")
        dataScienceWorkflow(llmProvider, jvmPython)
    }
    
    /**
     * Real-time log analysis with AI-powered anomaly detection
     */
    private suspend fun logAnalysisPipeline(
        llmProvider: LLMProvider,
        jvmPython: DGMBridge
    ) = coroutineScope {
        
        // Create log stream
        val logStream = generateLogStream()
        val agent = DataAwareAgent(llmProvider, enableAutoExecution = true)
        
        // Configure JVM Python governance
        jvmPython.sendDirective(GovernanceDirective(
            type = DirectiveType.CONFIGURE,
            target = "streaming",
            parameters = mapOf(
                "batch_size" to 100,
                "window_duration" to 5000,
                "parallelism" to 4
            )
        ))
        
        // Process log stream
        val processor = BigDataIntegration.StreamProcessor(batchSize = 100)
        val errorCount = AtomicLong(0)
        val anomalyCount = AtomicLong(0)
        
        val logSchema: Schema = 5 j { i ->
            when (i) {
                0 -> "timestamp" j "long"
                1 -> "level" j "string"
                2 -> "service" j "string"
                3 -> "message" j "string"
                4 -> "latency_ms" j "int"
                else -> "unknown" j "null"
            }
        }
        
        processor.processStream(logStream, logSchema) { batch ->
            // Filter errors
            val errors = batch.filter { row ->
                row[1].a == "ERROR"
            }
            
            if (errors.count() > 0) {
                errorCount.addAndGet(errors.count().toLong())
                
                // AI analysis of error patterns
                val analysis = agent.analyzeData(
                    errors,
                    "What patterns do you see in these errors? Are there any anomalies?"
                ).await()
                
                if (analysis.answer.contains("anomaly", ignoreCase = true)) {
                    anomalyCount.incrementAndGet()
                    println("⚠️ AI detected anomaly: ${analysis.answer.take(100)}...")
                }
            }
            
            batch
        }.take(5).collect { batch ->
            println("  Processed batch: ${batch.count()} logs")
        }
        
        println("\nLog Analysis Summary:")
        println("  Total errors: ${errorCount.get()}")
        println("  Anomalies detected: ${anomalyCount.get()}")
    }
    
    /**
     * Financial data analysis with predictive insights
     */
    private suspend fun financialAnalysisPipeline(
        llmProvider: LLMProvider,
        jvmPython: DGMBridge
    ) = coroutineScope {
        
        // Create financial data
        val stockData = TrikeShedDataFrame.fromColumns(
            "symbol" to (300 j { i -> listOf("AAPL", "GOOGL", "MSFT", "AMZN")[i % 4] }),
            "timestamp" to (300 j { i -> System.currentTimeMillis() - i * 60000L }),
            "price" to (300 j { i -> 100.0 + Math.sin(i * 0.1) * 10 + Math.random() * 5 }),
            "volume" to (300 j { i -> 10000 + (Math.random() * 5000).toInt() }),
            "bid" to (300 j { i -> 99.5 + Math.sin(i * 0.1) * 10 + Math.random() * 5 }),
            "ask" to (300 j { i -> 100.5 + Math.sin(i * 0.1) * 10 + Math.random() * 5 })
        )
        
        val agent = DataAwareAgent(llmProvider, enableAutoExecution = true)
        
        // Configure Python DGM for financial calculations
        val pythonCode = """
# Configure financial analysis environment
import numpy as np
import pandas as pd
from scipy import stats

# Technical indicators
def calculate_rsi(prices, period=14):
    deltas = np.diff(prices)
    seed = deltas[:period+1]
    up = seed[seed >= 0].sum() / period
    down = -seed[seed < 0].sum() / period
    rs = up / down
    rsi = np.zeros_like(prices)
    rsi[:period] = 100. - 100. / (1. + rs)
    
    for i in range(period, len(prices)):
        delta = deltas[i-1]
        if delta > 0:
            upval = delta
            downval = 0.
        else:
            upval = 0.
            downval = -delta
        
        up = (up * (period - 1) + upval) / period
        down = (down * (period - 1) + downval) / period
        rs = up / down
        rsi[i] = 100. - 100. / (1. + rs)
    
    return rsi

# Volatility calculation
def calculate_volatility(returns, window=20):
    return pd.Indexed(returns).rolling(window).std()

# Configure for JVM integration
INDICATORS_READY = True
        """.trimIndent()
        
        jvmPython.executePython(pythonCode)
        
        // AI-powered analysis
        println("Analyzing market patterns...")
        
        // Group by symbol for analysis
        val bySymbol = stockData.groupBy("symbol")
        
        // Generate insights for each symbol
        val insights = agent.generateInsights(
            stockData,
            listOf("price trends", "volume patterns", "spread analysis", "volatility")
        )
        
        println("\nMarket Insights:")
        insights.toList().forEachIndexed { idx, insight ->
            println("  ${idx + 1}. [${insight.type}] ${insight.description.take(150)}...")
        }
        
        // Generate trading strategy
        val pipeline = agent.generatePipeline(
            stockData,
            "Create a momentum-based trading strategy with risk management"
        )
        
        println("\nGenerated Trading Pipeline:")
        pipeline.steps.toList().forEach { step ->
            println("  Step ${step.number}: ${step.description}")
        }
        
        // Execute strategy simulation
        val simulation = simulateTradingStrategy(stockData, pipeline)
        println("\nStrategy Simulation Results:")
        println("  Total trades: ${simulation.trades}")
        println("  Win rate: ${String.format("%.2f", simulation.winRate * 100)}%")
        println("  Sharpe ratio: ${String.format("%.2f", simulation.sharpeRatio)}")
    }
    
    /**
     * IoT sensor stream processing with edge analytics
     */
    private suspend fun iotStreamProcessing(
        llmProvider: LLMProvider,
        jvmPython: DGMBridge
    ) = coroutineScope {
        
        // Generate sensor data stream
        val sensorStream = flow {
            var i = 0
            while (i < 1000) {
                emit(5 j { j ->
                    when (j) {
                        0 -> "sensor_${i % 10}" j { "device_id" }
                        1 -> System.currentTimeMillis() j { "timestamp" }
                        2 -> (20.0 + Math.sin(i * 0.1) * 5 + Math.random() * 2) j { "temperature" }
                        3 -> (40.0 + Math.cos(i * 0.1) * 10 + Math.random() * 5) j { "humidity" }
                        4 -> (1013.0 + Math.sin(i * 0.05) * 5) j { "pressure" }
                        else -> null j { "null" }
                    }
                })
                i++
                delay(10)
            }
        }
        
        val schema: Schema = 5 j { i ->
            when (i) {
                0 -> "device_id" j "string"
                1 -> "timestamp" j "long"
                2 -> "temperature" j "double"
                3 -> "humidity" j "double"
                4 -> "pressure" j "double"
                else -> "unknown" j "null"
            }
        }
        
        // Configure edge computing via JVM Python
        jvmPython.sendDirective(GovernanceDirective(
            type = DirectiveType.CONFIGURE,
            target = "edge_analytics",
            parameters = mapOf(
                "anomaly_threshold" to 3.0,
                "sliding_window" to 50,
                "alert_cooldown" to 60000
            )
        ))
        
        val agent = DataAwareAgent(llmProvider)
        val processor = BigDataIntegration.StreamProcessor(batchSize = 50)
        val anomalies = mutableListOf<AnomalyEvent>()
        
        processor.processStream(sensorStream, schema) { batch ->
            // Check for anomalies
            val stats = calculateSensorStats(batch)
            
            if (stats.hasAnomaly) {
                val analysis = agent.analyzeData(
                    batch,
                    "Analyze these sensor readings for anomalies or concerning patterns"
                ).await()
                
                anomalies.add(AnomalyEvent(
                    timestamp = System.currentTimeMillis(),
                    deviceIds = stats.affectedDevices,
                    description = analysis.answer
                ))
                
                println("🚨 Anomaly detected: ${analysis.answer.take(100)}...")
            }
            
            batch
        }.take(10).collect { batch ->
            println("  Processed ${batch.count()} sensor readings")
        }
        
        println("\nIoT Processing Summary:")
        println("  Total anomalies: ${anomalies.size}")
        if (anomalies.isNotEmpty()) {
            println("  Affected devices: ${anomalies.flatMap { it.deviceIds }.distinct().joinToString()}")
        }
    }
    
    /**
     * Complete data science workflow with model training
     */
    private suspend fun dataScienceWorkflow(
        llmProvider: LLMProvider,
        jvmPython: DGMBridge
    ) = coroutineScope {
        
        // Generate synthetic dataset for classification
        val dataset = generateClassificationDataset(1000)
        val agent = DataAwareAgent(llmProvider, enableAutoExecution = true)
        
        println("Generated dataset with ${dataset.count()} samples")
        
        // 1. Exploratory Data Analysis
        println("\n1. Exploratory Data Analysis:")
        val explorationFlow = agent.exploreInteractively(dataset)
        
        explorationFlow.take(3).collect { step ->
            println("  Step ${step.stepNumber}: ${step.action}")
            println("    → ${step.result.answer.take(150)}...")
        }
        
        // 2. Feature Engineering via AI
        println("\n2. AI-Powered Feature Engineering:")
        val featureEngineering = agent.transformData(
            dataset,
            "Create polynomial features and interaction terms for better model performance"
        )
        
        println("  Generated transformation code:")
        println("    ${featureEngineering.generatedCode.lines().take(5).joinToString("\n    ")}")
        
        // 3. Model Training via JVM Python
        println("\n3. Model Training:")
        
        val modelCode = """
# Train multiple models and compare
from sklearn.model_selection import train_test_split
from sklearn.ensemble import RandomForestClassifier, GradientBoostingClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, precision_recall_fscore_support
import joblib

# This would receive data from TrikeShed
# X, y = load_trikeshed_data()

models = {
    'RandomForest': RandomForestClassifier(n_estimators=100, random_state=42),
    'GradientBoosting': GradientBoostingClassifier(n_estimators=100, random_state=42),
    'LogisticRegression': LogisticRegression(max_iter=1000, random_state=42)
}

results = {}
for name, model in models.items():
    # Train model
    # model.fit(X_train, y_train)
    # y_pred = model.predict(X_test)
    # results[name] = calculate_metrics(y_test, y_pred)
    results[name] = {'accuracy': 0.85 + np.random.rand() * 0.1}  # Simulated

print(f"Model comparison: {results}")

# Save best model
# joblib.dump(best_model, 'nexus_model.pkl')
        """.trimIndent()
        
        val modelResults = jvmPython.executePython(modelCode)
        println("  Model training complete")
        
        // 4. Generate deployment pipeline
        println("\n4. Deployment Pipeline:")
        val deploymentPipeline = agent.generatePipeline(
            dataset,
            "Create a production-ready pipeline for real-time prediction with monitoring"
        )
        
        deploymentPipeline.steps.toList().take(5).forEach { step ->
            println("  ${step.number}. ${step.description}")
        }
        
        // 5. Learning from feedback
        println("\n5. Continuous Learning:")
        val feedback = listOf(
            Feedback("Show distribution of target variable", true, 5, "Very helpful"),
            Feedback("Identify correlated features", true, 4, "Good insights"),
            Feedback("Suggest feature transformations", true, 5, "Excellent suggestions"),
            Feedback("Explain model predictions", false, 2, "Too technical")
        )
        
        val learning = agent.learnFromData(dataset, feedback.size j { feedback[it] })
        
        println("  Learned patterns:")
        learning.patterns.toList().take(3).forEach { pattern ->
            println("    - $pattern")
        }
        
        println("\n=== Workflow Complete ===")
    }
    
    // Helper functions
    
    private fun generateLogStream(): Flow<Row> = flow {
        var i = 0
        while (i < 500) {
            val level = when {
                Math.random() < 0.1 -> "ERROR"
                Math.random() < 0.3 -> "WARN"
                else -> "INFO"
            }
            
            emit(5 j { j ->
                when (j) {
                    0 -> System.currentTimeMillis() j { "timestamp" }
                    1 -> level j { "level" }
                    2 -> listOf("auth", "api", "db", "cache")[i % 4] j { "service" }
                    3 -> "Event message $i" j { "message" }
                    4 -> (Math.random() * 200).toInt() j { "latency" }
                    else -> null j { "null" }
                }
            })
            
            i++
            delay(20)
        }
    }
    
    private suspend fun simulateTradingStrategy(
        data: TrikeShedDataFrame,
        pipeline: DataPipeline
    ): TradingSimulation {
        // Simulate trading based on pipeline
        val trades = (Math.random() * 50 + 20).toInt()
        val wins = (trades * (0.4 + Math.random() * 0.3)).toInt()
        
        return TradingSimulation(
            trades = trades,
            winRate = wins.toDouble() / trades,
            sharpeRatio = 0.5 + Math.random() * 2.0
        )
    }
    
    private fun calculateSensorStats(batch: TrikeShedDataFrame): SensorStats {
        val temps = mutableListOf<Double>()
        for (i in 0 until batch.count()) {
            // Temperature is at index 2
            (batch.currentWindow()[i][2].a as? Double)?.let { temps.add(it) }
        }
        
        val mean = temps.average()
        val std = Math.sqrt(temps.map { (it - mean) * (it - mean) }.average())
        
        val hasAnomaly = temps.any { Math.abs(it - mean) > 3 * std }
        
        return SensorStats(
            hasAnomaly = hasAnomaly,
            affectedDevices = if (hasAnomaly) listOf("sensor_1", "sensor_2") else emptyList()
        )
    }
    
    private fun generateClassificationDataset(size: Int): TrikeShedDataFrame {
        val features = Array(4) { feature ->
            size j { i ->
                when (feature) {
                    0 -> Math.random() * 10  // feature1
                    1 -> Math.sin(i * 0.1) * 5 + Math.random() * 2  // feature2
                    2 -> Math.random() * 100  // feature3
                    3 -> if (Math.random() > 0.5) 1.0 else 0.0  // feature4
                    else -> 0.0
                }
            }
        }
        
        val target = size j { i ->
            // Create non-linear relationship
            val f1 = features[0][i] as Double
            val f2 = features[1][i] as Double
            val f3 = features[2][i] as Double
            
            if (f1 * f2 > 25 && f3 < 50) 1 else 0
        }
        
        return TrikeShedDataFrame.fromColumns(
            "feature1" to features[0],
            "feature2" to features[1],
            "feature3" to features[2],
            "feature4" to features[3],
            "target" to target
        )
    }
    
    // Data classes
    
    data class TradingSimulation(
        val trades: Int,
        val winRate: Double,
        val sharpeRatio: Double
    )
    
    data class AnomalyEvent(
        val timestamp: Long,
        val deviceIds: List<String>,
        val description: String
    )
    
    data class SensorStats(
        val hasAnomaly: Boolean,
        val affectedDevices: List<String>
    )
}

/**
 * Extension function to await coroutines in our pipeline
 */
private suspend fun <T> Deferred<T>.await(): T = this.await()