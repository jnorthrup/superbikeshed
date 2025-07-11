package fiduciary.ingest

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.datetime.*

/**
 * Repeatable Ingest Pipeline with Stations and Queues
 * 
 * Station Architecture:
 * 1. FETCH -> Download documents from sources
 * 2. EXTRACT -> Extract text content
 * 3. TOKENIZE -> Stanford NLP tokenization
 * 4. TAG -> POS tagging and NER
 * 5. LDA -> Topic modeling
 * 6. METRICS -> Calculate readability, complexity
 * 7. GRAPH -> Build knowledge graph
 * 8. STORE -> Persist to Memvid/storage
 */

// === Station Types ===

enum class StationId {
    FETCH,
    EXTRACT,
    TOKENIZE,
    TAG,
    LDA,
    METRICS,
    GRAPH,
    STORE
}

// === Work Items ===

@Serializable
sealed class WorkItem {
    abstract val id: String
    abstract val timestamp: Instant
    abstract val sourceUrl: String?
    abstract val retryCount: Int
}

@Serializable
data class FetchItem(
    override val id: String,
    override val timestamp: Instant = Clock.System.now(),
    override val sourceUrl: String,
    override val retryCount: Int = 0,
    val headers: Map<String, String> = emptyMap()
) : WorkItem()

@Serializable
data class ExtractItem(
    override val id: String,
    override val timestamp: Instant = Clock.System.now(),
    override val sourceUrl: String?,
    override val retryCount: Int = 0,
    val rawContent: ByteArray,
    val mimeType: String
) : WorkItem()

@Serializable
data class TokenizeItem(
    override val id: String,
    override val timestamp: Instant = Clock.System.now(),
    override val sourceUrl: String?,
    override val retryCount: Int = 0,
    val text: String,
    val language: String = "en"
) : WorkItem()

@Serializable
data class TagItem(
    override val id: String,
    override val timestamp: Instant = Clock.System.now(),
    override val sourceUrl: String?,
    override val retryCount: Int = 0,
    val tokens: List<Token>,
    val sentences: List<Sentence>
) : WorkItem()

@Serializable
data class LdaItem(
    override val id: String,
    override val timestamp: Instant = Clock.System.now(),
    override val sourceUrl: String?,
    override val retryCount: Int = 0,
    val taggedDoc: TaggedDocument
) : WorkItem()

@Serializable
data class MetricsItem(
    override val id: String,
    override val timestamp: Instant = Clock.System.now(),
    override val sourceUrl: String?,
    override val retryCount: Int = 0,
    val document: AnalyzedDocument
) : WorkItem()

@Serializable
data class GraphItem(
    override val id: String,
    override val timestamp: Instant = Clock.System.now(),
    override val sourceUrl: String?,
    override val retryCount: Int = 0,
    val enrichedDoc: EnrichedDocument
) : WorkItem()

// === Data Types ===

@Serializable
data class Token(
    val text: String,
    val pos: String? = null,
    val lemma: String? = null,
    val offset: Int
)

@Serializable
data class Sentence(
    val tokens: List<Token>,
    val start: Int,
    val end: Int
)

@Serializable
data class TaggedDocument(
    val id: String,
    val sentences: List<Sentence>,
    val entities: List<NamedEntity>,
    val language: String
)

@Serializable
data class NamedEntity(
    val text: String,
    val type: String,
    val startOffset: Int,
    val endOffset: Int
)

@Serializable
data class TopicAssignment(
    val topicId: Int,
    val probability: Double,
    val keywords: List<String>
)

@Serializable
data class AnalyzedDocument(
    val id: String,
    val tagged: TaggedDocument,
    val topics: List<TopicAssignment>,
    val metrics: DocumentMetrics
)

@Serializable
data class DocumentMetrics(
    val fleschKincaid: Double,
    val avgSentenceLength: Double,
    val lexicalDiversity: Double,
    val complexity: Double
)

@Serializable
data class EnrichedDocument(
    val analyzed: AnalyzedDocument,
    val graphNodes: List<GraphNode>,
    val graphEdges: List<GraphEdge>
)

@Serializable
data class GraphNode(
    val id: String,
    val type: NodeType,
    val label: String,
    val properties: Map<String, String>
)

@Serializable
enum class NodeType {
    DOCUMENT,
    ENTITY,
    TOPIC,
    CONCEPT,
    TERM
}

@Serializable
data class GraphEdge(
    val source: String,
    val target: String,
    val type: EdgeType,
    val weight: Double = 1.0
)

@Serializable
enum class EdgeType {
    CONTAINS,
    MENTIONS,
    RELATES_TO,
    SIMILAR_TO,
    BELONGS_TO_TOPIC
}

// === Station Implementations ===

/**
 * Base station processor
 */
abstract class Station<IN : WorkItem, OUT : WorkItem>(
    val id: StationId,
    val parallelism: Int = 4
) {
    abstract suspend fun process(item: IN): OUT
    
    fun createProcessor(
        input: ReceiveChannel<IN>,
        output: SendChannel<OUT>,
        errors: SendChannel<ProcessingError>
    ) = channelFlow {
        coroutineScope {
            repeat(parallelism) {
                launch {
                    for (item in input) {
                        try {
                            val result = process(item)
                            output.send(result)
                        } catch (e: Exception) {
                            errors.send(ProcessingError(
                                station = id,
                                item = item,
                                error = e,
                                timestamp = Clock.System.now()
                            ))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Fetch documents from various sources
 */
class FetchStation : Station<FetchItem, ExtractItem>(StationId.FETCH, 8) {
    override suspend fun process(item: FetchItem): ExtractItem {
        // Implement actual fetching logic
        // For now, mock implementation
        val content = "Mock content for ${item.sourceUrl}".toByteArray()
        
        return ExtractItem(
            id = item.id,
            sourceUrl = item.sourceUrl,
            rawContent = content,
            mimeType = "text/plain"
        )
    }
}

/**
 * Extract text from various formats
 */
class ExtractStation : Station<ExtractItem, TokenizeItem>(StationId.EXTRACT, 4) {
    override suspend fun process(item: ExtractItem): TokenizeItem {
        val text = when (item.mimeType) {
            "text/plain" -> String(item.rawContent)
            "text/html" -> extractHtml(item.rawContent)
            "application/pdf" -> extractPdf(item.rawContent)
            else -> String(item.rawContent)
        }
        
        return TokenizeItem(
            id = item.id,
            sourceUrl = item.sourceUrl,
            text = text
        )
    }
    
    private fun extractHtml(content: ByteArray): String {
        // Simple HTML extraction
        return String(content).replace(Regex("<[^>]*>"), " ")
    }
    
    private fun extractPdf(content: ByteArray): String {
        // PDF extraction would go here
        return "PDF content extraction not implemented"
    }
}

/**
 * Stanford NLP tokenization
 */
class TokenizeStation : Station<TokenizeItem, TagItem>(StationId.TOKENIZE, 4) {
    override suspend fun process(item: TokenizeItem): TagItem {
        // Stanford NLP tokenization
        val sentences = tokenizeText(item.text)
        
        return TagItem(
            id = item.id,
            sourceUrl = item.sourceUrl,
            tokens = sentences.flatMap { it.tokens },
            sentences = sentences
        )
    }
    
    private fun tokenizeText(text: String): List<Sentence> {
        // Mock tokenization
        val sentences = text.split(Regex("[.!?]+")).filter { it.isNotBlank() }
        var offset = 0
        
        return sentences.map { sentence ->
            val tokens = sentence.trim().split(Regex("\\s+")).mapIndexed { i, word ->
                Token(
                    text = word,
                    offset = offset + i
                )
            }
            offset += tokens.size
            
            Sentence(
                tokens = tokens,
                start = tokens.firstOrNull()?.offset ?: 0,
                end = tokens.lastOrNull()?.offset ?: 0
            )
        }
    }
}

/**
 * POS tagging and NER
 */
class TagStation : Station<TagItem, LdaItem>(StationId.TAG, 4) {
    override suspend fun process(item: TagItem): LdaItem {
        // Stanford NLP POS tagging and NER
        val tagged = performTagging(item)
        
        return LdaItem(
            id = item.id,
            sourceUrl = item.sourceUrl,
            taggedDoc = tagged
        )
    }
    
    private fun performTagging(item: TagItem): TaggedDocument {
        // Mock tagging
        val entities = findEntities(item.tokens)
        
        return TaggedDocument(
            id = item.id,
            sentences = item.sentences,
            entities = entities,
            language = "en"
        )
    }
    
    private fun findEntities(tokens: List<Token>): List<NamedEntity> {
        // Mock NER
        return listOf(
            NamedEntity("Patrick Devine", "PERSON", 0, 2),
            NamedEntity("FDIC", "ORGANIZATION", 10, 11)
        )
    }
}

/**
 * LDA topic modeling
 */
class LdaStation : Station<LdaItem, MetricsItem>(StationId.LDA, 2) {
    private val topicModel = LdaModel(numTopics = 20)
    
    override suspend fun process(item: LdaItem): MetricsItem {
        val topics = topicModel.inferTopics(item.taggedDoc)
        val metrics = calculateMetrics(item.taggedDoc)
        
        val analyzed = AnalyzedDocument(
            id = item.id,
            tagged = item.taggedDoc,
            topics = topics,
            metrics = metrics
        )
        
        return MetricsItem(
            id = item.id,
            sourceUrl = item.sourceUrl,
            document = analyzed
        )
    }
    
    private fun calculateMetrics(doc: TaggedDocument): DocumentMetrics {
        val totalWords = doc.sentences.sumOf { it.tokens.size }
        val totalSentences = doc.sentences.size
        val uniqueWords = doc.sentences.flatMap { it.tokens }.map { it.text.lowercase() }.toSet().size
        
        return DocumentMetrics(
            fleschKincaid = calculateFleschKincaid(totalWords, totalSentences),
            avgSentenceLength = totalWords.toDouble() / totalSentences,
            lexicalDiversity = uniqueWords.toDouble() / totalWords,
            complexity = 0.75 // Mock complexity
        )
    }
    
    private fun calculateFleschKincaid(words: Int, sentences: Int): Double {
        // Simplified Flesch-Kincaid
        return 206.835 - 1.015 * (words.toDouble() / sentences)
    }
}

/**
 * Simple LDA model
 */
class LdaModel(private val numTopics: Int) {
    fun inferTopics(doc: TaggedDocument): List<TopicAssignment> {
        // Mock LDA inference
        return (0 until numTopics).map { topicId ->
            TopicAssignment(
                topicId = topicId,
                probability = 1.0 / numTopics,
                keywords = listOf("keyword$topicId", "term$topicId")
            )
        }.take(3) // Top 3 topics
    }
}

/**
 * Graph building station
 */
class GraphStation : Station<MetricsItem, GraphItem>(StationId.GRAPH, 4) {
    override suspend fun process(item: MetricsItem): GraphItem {
        val nodes = mutableListOf<GraphNode>()
        val edges = mutableListOf<GraphEdge>()
        
        // Document node
        val docNode = GraphNode(
            id = "doc_${item.id}",
            type = NodeType.DOCUMENT,
            label = item.id,
            properties = mapOf(
                "complexity" to item.document.metrics.complexity.toString(),
                "kincaid" to item.document.metrics.fleschKincaid.toString()
            )
        )
        nodes.add(docNode)
        
        // Entity nodes
        item.document.tagged.entities.forEach { entity ->
            val entityNode = GraphNode(
                id = "entity_${entity.text.hashCode()}",
                type = NodeType.ENTITY,
                label = entity.text,
                properties = mapOf("type" to entity.type)
            )
            nodes.add(entityNode)
            
            edges.add(GraphEdge(
                source = docNode.id,
                target = entityNode.id,
                type = EdgeType.MENTIONS
            ))
        }
        
        // Topic nodes
        item.document.topics.forEach { topic ->
            val topicNode = GraphNode(
                id = "topic_${topic.topicId}",
                type = NodeType.TOPIC,
                label = "Topic ${topic.topicId}",
                properties = mapOf(
                    "keywords" to topic.keywords.joinToString(",")
                )
            )
            nodes.add(topicNode)
            
            edges.add(GraphEdge(
                source = docNode.id,
                target = topicNode.id,
                type = EdgeType.BELONGS_TO_TOPIC,
                weight = topic.probability
            ))
        }
        
        return GraphItem(
            id = item.id,
            sourceUrl = item.sourceUrl,
            enrichedDoc = EnrichedDocument(
                analyzed = item.document,
                graphNodes = nodes,
                graphEdges = edges
            )
        )
    }
}

// === Error Handling ===

@Serializable
data class ProcessingError(
    val station: StationId,
    val item: WorkItem,
    val error: Exception,
    val timestamp: Instant
)

// === Pipeline Orchestrator ===

/**
 * Main ingest pipeline orchestrator
 */
class IngestPipeline(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    // Queues between stations
    private val fetchQueue = Channel<FetchItem>(100)
    private val extractQueue = Channel<ExtractItem>(100)
    private val tokenizeQueue = Channel<TokenizeItem>(100)
    private val tagQueue = Channel<TagItem>(100)
    private val ldaQueue = Channel<LdaItem>(100)
    private val metricsQueue = Channel<MetricsItem>(100)
    private val graphQueue = Channel<GraphItem>(100)
    private val errorQueue = Channel<ProcessingError>(1000)
    
    // Stations
    private val stations = listOf(
        FetchStation(),
        ExtractStation(),
        TokenizeStation(),
        TagStation(),
        LdaStation(),
        GraphStation()
    )
    
    // Metrics
    private val metrics = PipelineMetrics()
    
    /**
     * Start the pipeline
     */
    fun start() {
        // Wire up stations
        scope.launch {
            FetchStation().createProcessor(fetchQueue, extractQueue, errorQueue).collect()
        }
        scope.launch {
            ExtractStation().createProcessor(extractQueue, tokenizeQueue, errorQueue).collect()
        }
        scope.launch {
            TokenizeStation().createProcessor(tokenizeQueue, tagQueue, errorQueue).collect()
        }
        scope.launch {
            TagStation().createProcessor(tagQueue, ldaQueue, errorQueue).collect()
        }
        scope.launch {
            LdaStation().createProcessor(ldaQueue, metricsQueue, errorQueue).collect()
        }
        scope.launch {
            GraphStation().createProcessor(metricsQueue, graphQueue, errorQueue).collect()
        }
        
        // Error handler
        scope.launch {
            for (error in errorQueue) {
                handleError(error)
            }
        }
        
        // Final output handler
        scope.launch {
            for (item in graphQueue) {
                storeResult(item)
                metrics.recordCompletion(item.id)
            }
        }
    }
    
    /**
     * Submit documents for processing
     */
    suspend fun ingest(urls: List<String>) {
        urls.forEach { url ->
            val item = FetchItem(
                id = generateId(),
                sourceUrl = url
            )
            fetchQueue.send(item)
            metrics.recordSubmission(item.id)
        }
    }
    
    /**
     * Submit batch of 100 documents
     */
    suspend fun ingestBatch(sources: DocumentSources) {
        val urls = sources.getNextBatch(100)
        ingest(urls)
    }
    
    private fun handleError(error: ProcessingError) {
        println("Error at ${error.station}: ${error.error.message}")
        metrics.recordError(error)
        
        // Retry logic
        if (error.item.retryCount < 3) {
            scope.launch {
                delay(1000L * (error.item.retryCount + 1))
                resubmit(error.item)
            }
        }
    }
    
    private suspend fun resubmit(item: WorkItem) {
        when (item) {
            is FetchItem -> fetchQueue.send(item.copy(retryCount = item.retryCount + 1))
            is ExtractItem -> extractQueue.send(item.copy(retryCount = item.retryCount + 1))
            is TokenizeItem -> tokenizeQueue.send(item.copy(retryCount = item.retryCount + 1))
            is TagItem -> tagQueue.send(item.copy(retryCount = item.retryCount + 1))
            is LdaItem -> ldaQueue.send(item.copy(retryCount = item.retryCount + 1))
            is MetricsItem -> metricsQueue.send(item.copy(retryCount = item.retryCount + 1))
            is GraphItem -> graphQueue.send(item.copy(retryCount = item.retryCount + 1))
        }
    }
    
    private suspend fun storeResult(item: GraphItem) {
        // Store to Memvid or other storage
        println("Storing graph with ${item.enrichedDoc.graphNodes.size} nodes")
    }
    
    fun getMetrics(): PipelineMetrics = metrics
    
    fun stop() {
        scope.cancel()
    }
}

// === Pipeline Metrics ===

class PipelineMetrics {
    private val submitted = mutableMapOf<String, Instant>()
    private val completed = mutableMapOf<String, Instant>()
    private val errors = mutableListOf<ProcessingError>()
    
    fun recordSubmission(id: String) {
        submitted[id] = Clock.System.now()
    }
    
    fun recordCompletion(id: String) {
        completed[id] = Clock.System.now()
    }
    
    fun recordError(error: ProcessingError) {
        errors.add(error)
    }
    
    fun getStats(): PipelineStats {
        val avgProcessingTime = if (completed.isNotEmpty()) {
            completed.entries.mapNotNull { (id, endTime) ->
                submitted[id]?.let { startTime ->
                    (endTime - startTime).inWholeMilliseconds
                }
            }.average()
        } else 0.0
        
        return PipelineStats(
            submitted = submitted.size,
            completed = completed.size,
            inProgress = submitted.size - completed.size,
            errors = errors.size,
            avgProcessingTimeMs = avgProcessingTime,
            errorsByStation = errors.groupBy { it.station }.mapValues { it.value.size }
        )
    }
}

@Serializable
data class PipelineStats(
    val submitted: Int,
    val completed: Int,
    val inProgress: Int,
    val errors: Int,
    val avgProcessingTimeMs: Double,
    val errorsByStation: Map<StationId, Int>
)

// === Document Sources ===

interface DocumentSources {
    suspend fun getNextBatch(size: Int): List<String>
}

class PatrickDevineDocumentSource : DocumentSources {
    private var offset = 0
    
    override suspend fun getNextBatch(size: Int): List<String> {
        // Return URLs to Patrick Devine documents
        val batch = (offset until offset + size).map { i ->
            "https://archive.org/download/patrickdevinefiles/Patrick%20Devine%20files/01-Transcripts/patrick_${i.toString().padStart(4, '0')}.txt"
        }
        offset += size
        return batch
    }
}

// === Utilities ===

private fun generateId(): String {
    return "doc_${System.currentTimeMillis()}_${(0..9999).random()}"
}