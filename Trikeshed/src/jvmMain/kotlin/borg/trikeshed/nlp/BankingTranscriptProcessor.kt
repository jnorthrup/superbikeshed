package borg.trikeshed.nlp

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.max

// Banking-specific entity types and canonicalization
data class BankingEntity(
    val canonicalName: String,
    val aliases: MutableSet<String>,
    val entityType: BankingEntityType,
    var confidence: Double,
    val firstMentioned: String, // source file
    val evidence: MutableList<String> // source files
)

enum class BankingEntityType {
    PERSON, ORGANIZATION, ACCOUNT, DOCUMENT, BOND, PROCESS, REGULATION
}

data class BankingRelation(
    val subject: String,
    val relation: String,
    val obj: String,
    var confidence: Double,
    val evidence: MutableList<String>,
    val temporalContext: String? = null
)

data class BankingGraph(
    val entities: List<BankingEntity>,
    val relations: List<BankingRelation>,
    val evidenceMap: Map<String, List<String>>,
    val metadata: Map<String, Any>
)

data class TranscriptMetadata(
    val sourceFile: String,
    val timestamp: LocalDateTime?,
    val speakers: List<String>,
    val duration: String?,
    val callId: String?
)

object BankingTranscriptProcessor {
    
    // Banking-specific entity canonicalization rules
    private val canonicalizationRules = mapOf(
        "FDIC" to "Federal Deposit Insurance Corporation",
        "FR Bank" to "Federal Reserve Bank", 
        "FRB" to "Federal Reserve Bank",
        "EIN" to "Employer Identification Number",
        "SF30" to "Standard Form 30",
        "SF1414" to "Standard Form 1414",
        "SF16" to "Standard Form 16",
        "SF1047" to "Standard Form 1047",
        "UCC" to "Uniform Commercial Code"
    )
    
    private val bankingEntityPatterns = mapOf(
        BankingEntityType.ACCOUNT to Regex("\\b\\d{4}-\\d{4}-\\d{4}-\\d{4}\\b"), // Account numbers
        BankingEntityType.BOND to Regex("\\b(perf\\.?|pay\\.?)\\s*bond\\b", RegexOption.IGNORE_CASE),
        BankingEntityType.DOCUMENT to Regex("\\b(SF\\d+|UCC\\s+\\d+[a-z]-\\d+)\\b", RegexOption.IGNORE_CASE)
    )

    /**
     * Process entire transcript corpus
     */
    fun processTranscriptCorpus(transcriptFiles: List<String>): List<DocumentData> {
        return transcriptFiles.mapNotNull { file ->
            try {
                val text = readTranscriptFile(file)
                val docData = processTextWithDelegate(text)
                val metadata = extractTranscriptMetadata(file)
                
                // Add metadata to document
                docData.copy(metadata = mapOf(
                    "source" to file,
                    "timestamp" to metadata.timestamp?.toString(),
                    "speakers" to metadata.speakers,
                    "duration" to metadata.duration,
                    "callId" to metadata.callId
                ))
            } catch (e: Exception) {
                println("Error processing $file: ${e.message}")
                null
            }
        }
    }

    /**
     * Process entire transcript corpus and return with metadata.
     */
    fun processTranscriptCorpusWithMetadata(transcriptFiles: List<String>): List<Pair<DocumentData, TranscriptMetadata>> {
        return transcriptFiles.mapNotNull { file ->
            try {
                val text = readTranscriptFile(file)
                val docData = processTextWithDelegate(text)
                val metadata = extractTranscriptMetadata(file)
                docData to metadata
            } catch (e: Exception) {
                println("Error processing $file: ${e.message}")
                null
            }
        }
    }

    /**
     * Build comprehensive banking knowledge graph from a transcript corpus with metadata.
     */
    fun buildBankingKnowledgeGraph(corpusWithMetadata: List<Pair<DocumentData, TranscriptMetadata>>): BankingGraph {
        val entityMap = mutableMapOf<String, BankingEntity>()
        val relationMap = mutableMapOf<String, BankingRelation>()
        val evidenceMap = mutableMapOf<String, MutableList<String>>()
        
        corpusWithMetadata.forEach { (doc, metadata) ->
            val sourceFile = metadata.sourceFile
            
            // Extract and canonicalize entities
            doc.sentences.forEach { sent ->
                sent.entities.forEach { entity ->
                    val canonicalName = canonicalizeEntity(entity.text)
                    val entityType = classifyBankingEntity(entity.text, sent.text)
                    
                    entityMap.getOrPut(canonicalName) {
                        BankingEntity(
                            canonicalName = canonicalName,
                            aliases = mutableSetOf(entity.text),
                            entityType = entityType,
                            confidence = entity.confidence,
                            firstMentioned = sourceFile,
                            evidence = mutableListOf(sourceFile)
                        )
                    }.apply {
                        aliases.add(entity.text)
                        if (!evidence.contains(sourceFile)) evidence.add(sourceFile)
                        confidence = max(confidence, entity.confidence)
                    }
                }
            }
            
            // Extract relations with evidence
            doc.openieTriples.forEach { triple ->
                val canonicalSubject = canonicalizeEntity(triple.subject)
                val canonicalObj = canonicalizeEntity(triple.obj)
                val relationKey = "${canonicalSubject}-${triple.relation}-${canonicalObj}"
                
                relationMap.getOrPut(relationKey) {
                    BankingRelation(
                        subject = canonicalSubject,
                        relation = triple.relation,
                        obj = canonicalObj,
                        confidence = triple.confidence,
                        evidence = mutableListOf(sourceFile),
                        temporalContext = metadata.timestamp?.toString()
                    )
                }.apply {
                    if (!evidence.contains(sourceFile)) evidence.add(sourceFile)
                    confidence = max(confidence, triple.confidence)
                }
                
                // Build evidence map
                evidenceMap.getOrPut(relationKey) { mutableListOf() }.add(sourceFile)
            }
        }
        
        return BankingGraph(
            entities = entityMap.values.toList(),
            relations = relationMap.values.toList(),
            evidenceMap = evidenceMap.mapValues { it.value.distinct() },
            metadata = mapOf(
                "totalTranscripts" to corpusWithMetadata.size,
                "totalEntities" to entityMap.size,
                "totalRelations" to relationMap.size,
                "processingDate" to LocalDateTime.now().toString()
            )
        )
    }

    /**
     * Generate word clouds and topic modeling for the corpus
     */
    fun generateTopicAnalysis(corpus: List<DocumentData>): Map<String, Any> {
        val allText = corpus.joinToString(" ") { doc ->
            doc.sentences.joinToString(" ") { it.text }
        }
        
        // Simple word frequency analysis (a placeholder for a real LDA implementation)
        val words = allText.lowercase()
            .split(Regex("\\s+"))
            .filter { it.length > 3 && !it.matches(Regex("\\d+")) } // basic stopword/number removal
            .groupBy { it }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(50)
        
        return mapOf(
            "topWords" to words,
            "totalWords" to allText.split(Regex("\\s+")).size
        )
    }

    /**
     * Find contradictions or inconsistencies in the corpus
     */
    fun findContradictions(bankingGraph: BankingGraph): List<String> {
        val contradictions = mutableListOf<String>()
        
        // Group relations by subject-object pairs
        val relationGroups = bankingGraph.relations.groupBy { "${it.subject}-${it.obj}" }
        
        relationGroups.forEach { (key, relations) ->
            if (relations.size > 1) {
                val uniqueRelations = relations.map { it.relation }.distinct()
                if (uniqueRelations.size > 1) {
                    contradictions.add("Potential conflict for '$key': Relations found are [${uniqueRelations.joinToString(" vs ")}]")
                }
            }
        }
        
        return contradictions
    }

    // Helper functions
    private fun readTranscriptFile(filePath: String): String {
        return File(filePath).readText()
    }
    
    private fun extractTranscriptMetadata(filePath: String): TranscriptMetadata {
        val fileName = File(filePath).name
        val timestamp = extractTimestampFromFilename(fileName)
        val speakers = extractSpeakersFromFilename(fileName)
        val callId = extractCallIdFromFilename(fileName)
        
        return TranscriptMetadata(
            sourceFile = filePath,
            timestamp = timestamp,
            speakers = speakers,
            duration = null,
            callId = callId
        )
    }
    
    private fun canonicalizeEntity(entity: String): String {
        return canonicalizationRules.entries.find { it.key.equals(entity, ignoreCase = true) }?.value ?: entity
    }
    
    private fun classifyBankingEntity(text: String, context: String): BankingEntityType {
        // Use patterns and context to classify entities
        bankingEntityPatterns.forEach { (type, pattern) ->
            if (pattern.containsMatchIn(text) || pattern.containsMatchIn(context)) {
                return type
            }
        }
        
        // Default classification based on NER or simple patterns
        return when {
            text.matches(Regex(".*\\b(Co\\.|Corp|Inc|LLC)\\b.*", RegexOption.IGNORE_CASE)) -> BankingEntityType.ORGANIZATION
            text.matches(Regex("\\b[A-Z][a-z]+\\s+[A-Z][a-z]+\\b")) -> BankingEntityType.PERSON
            else -> BankingEntityType.ORGANIZATION // Default to organization for banking contexts
        }
    }
    
    private fun extractTimestampFromFilename(filename: String): LocalDateTime? {
        // Flexible timestamp extraction from filename like "call_2024-06-01_19-00.txt" or "20240601-1900_call.txt"
        val pattern = Regex("(\\d{4})-?(\\d{2})-?(\\d{2})[_-](\\d{2})-?(\\d{2})")
        val match = pattern.find(filename)
        return match?.let {
            try {
                val (year, month, day, hour, minute) = it.destructured
                LocalDateTime.of(year.toInt(), month.toInt(), day.toInt(), hour.toInt(), minute.toInt())
            } catch (e: Exception) {
                null
            }
        }
    }
    
    private fun extractSpeakersFromFilename(filename: String): List<String> {
        // Placeholder for speaker extraction logic
        return emptyList()
    }
    
    private fun extractCallIdFromFilename(filename: String): String? {
        // Extract call ID from filename like "call_12345.txt"
        val pattern = Regex("[_-]call[_-]?(\\d+)", RegexOption.IGNORE_CASE)
        return pattern.find(filename)?.groupValues?.get(1)
    }
} 