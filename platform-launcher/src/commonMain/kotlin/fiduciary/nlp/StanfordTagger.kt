package fiduciary.nlp

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.serialization.*

/**
 * Stanford English Tagger - Rapid NLP processing for patrick0720.txt
 * Embedded English tagger without external dependencies - work quickly with bandwidth constraints
 */

@Serializable
data class StanfordTag(
    val word: String,
    val tag: String,
    val position: Int
)

@Serializable
data class TaggedSentence(
    val sentence: String,
    val tags: List<StanfordTag>,
    val entities: List<NamedEntity>
)

@Serializable
data class NamedEntity(
    val text: String,
    val type: EntityType,
    val startPos: Int,
    val endPos: Int
)

@Serializable
enum class EntityType {
    PERSON, ORGANIZATION, LOCATION, DATE, MONEY, PERCENT, TIME
}

/**
 * Embedded English tagger - fast processing without external models
 */
class StanfordTagger {
    
    companion object {
        // Embedded POS tag patterns - quick and dirty but functional
        private val NOUN_PATTERNS = setOf(
            "analysis", "market", "investment", "strategy", "portfolio", "risk", "management",
            "attention", "system", "algorithm", "data", "model", "framework", "approach"
        )
        
        private val VERB_PATTERNS = setOf(
            "requires", "needs", "implements", "provides", "analyzes", "processes", "manages",
            "optimizes", "distributes", "coordinates", "monitors", "tracks", "measures"
        )
        
        private val ADJ_PATTERNS = setOf(
            "adaptive", "real-time", "distributed", "automated", "complex", "systematic",
            "algorithmic", "attention-based", "consensus-based", "multi-agent"
        )
        
        private val PERSON_INDICATORS = setOf(
            "patrick", "devine", "analyst", "manager", "advisor", "director", "expert"
        )
        
        private val ORG_INDICATORS = setOf(
            "corp", "inc", "llc", "company", "fund", "trust", "foundation", "institute"
        )
        
        private val MONEY_PATTERNS = Regex("""\$[\d,]+(\.\d{2})?""")
        private val PERCENT_PATTERNS = Regex("""\d+(\.\d+)?%""")
        private val DATE_PATTERNS = Regex("""\d{1,2}/\d{1,2}/\d{4}|\d{4}-\d{2}-\d{2}""")
    }
    
    /**
     * Tag text rapidly - optimized for speed with bandwidth constraints
     */
    fun tagText(text: String): List<TaggedSentence> {
        val sentences = splitSentences(text)
        return sentences.map { sentence ->
            val words = tokenize(sentence)
            val tags = tagWords(words)
            val entities = extractEntities(sentence, words)
            
            TaggedSentence(
                sentence = sentence,
                tags = tags,
                entities = entities
            )
        }
    }
    
    /**
     * Split text into sentences - fast regex approach
     */
    private fun splitSentences(text: String): List<String> {
        return text.split(Regex("[.!?]+\\s*"))
            .filter { it.trim().isNotEmpty() }
            .map { it.trim() }
    }
    
    /**
     * Tokenize sentence into words
     */
    private fun tokenize(sentence: String): List<String> {
        return sentence.split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .map { it.replace(Regex("[^\\w]"), "").lowercase() }
    }
    
    /**
     * Tag words with POS tags - embedded patterns for speed
     */
    private fun tagWords(words: List<String>): List<StanfordTag> {
        return words.mapIndexed { index, word ->
            val tag = when {
                word in NOUN_PATTERNS -> "NN"
                word in VERB_PATTERNS -> "VB"
                word in ADJ_PATTERNS -> "JJ"
                word.endsWith("ing") -> "VBG"
                word.endsWith("ed") -> "VBD"
                word.endsWith("ly") -> "RB"
                word.endsWith("s") && word.length > 3 -> "NNS"
                word.matches(Regex("\\d+")) -> "CD"
                word.length == 1 -> "DT"
                else -> "NN" // Default to noun
            }
            
            StanfordTag(
                word = word,
                tag = tag,
                position = index
            )
        }
    }
    
    /**
     * Extract named entities - pattern-based for speed
     */
    private fun extractEntities(sentence: String, words: List<String>): List<NamedEntity> {
        val entities = mutableListOf<NamedEntity>()
        val lowerSentence = sentence.lowercase()
        
        // Person entities
        words.forEach { word ->
            if (word in PERSON_INDICATORS) {
                val startPos = lowerSentence.indexOf(word)
                if (startPos >= 0) {
                    entities.add(NamedEntity(
                        text = word,
                        type = EntityType.PERSON,
                        startPos = startPos,
                        endPos = startPos + word.length
                    ))
                }
            }
        }
        
        // Organization entities
        words.forEach { word ->
            if (word in ORG_INDICATORS) {
                val startPos = lowerSentence.indexOf(word)
                if (startPos >= 0) {
                    entities.add(NamedEntity(
                        text = word,
                        type = EntityType.ORGANIZATION,
                        startPos = startPos,
                        endPos = startPos + word.length
                    ))
                }
            }
        }
        
        // Money entities
        MONEY_PATTERNS.findAll(sentence).forEach { match ->
            entities.add(NamedEntity(
                text = match.value,
                type = EntityType.MONEY,
                startPos = match.range.first,
                endPos = match.range.last + 1
            ))
        }
        
        // Percent entities
        PERCENT_PATTERNS.findAll(sentence).forEach { match ->
            entities.add(NamedEntity(
                text = match.value,
                type = EntityType.PERCENT,
                startPos = match.range.first,
                endPos = match.range.last + 1
            ))
        }
        
        // Date entities
        DATE_PATTERNS.findAll(sentence).forEach { match ->
            entities.add(NamedEntity(
                text = match.value,
                type = EntityType.DATE,
                startPos = match.range.first,
                endPos = match.range.last + 1
            ))
        }
        
        return entities
    }
    
    /**
     * Quick analysis for patrick0720.txt content
     */
    fun quickAnalyze(patrickContent: String): PatrickAnalysis {
        val tagged = tagText(patrickContent)
        
        val totalWords = tagged.sumOf { it.tags.size }
        val totalSentences = tagged.size
        val totalEntities = tagged.sumOf { it.entities.size }
        
        val entityTypes = tagged.flatMap { it.entities }
            .groupBy { it.type }
            .mapValues { it.value.size }
        
        val posFrequency = tagged.flatMap { it.tags }
            .groupBy { it.tag }
            .mapValues { it.value.size }
        
        val keyTerms = tagged.flatMap { it.tags }
            .filter { it.tag in setOf("NN", "NNS", "JJ") }
            .groupBy { it.word }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(20)
        
        return PatrickAnalysis(
            totalWords = totalWords,
            totalSentences = totalSentences,
            totalEntities = totalEntities,
            entityTypes = entityTypes,
            posFrequency = posFrequency,
            keyTerms = keyTerms.toMap(),
            complexity = calculateComplexity(tagged),
            readability = calculateReadability(totalWords, totalSentences)
        )
    }
    
    private fun calculateComplexity(tagged: List<TaggedSentence>): Double {
        val avgWordsPerSentence = if (tagged.isNotEmpty()) {
            tagged.sumOf { it.tags.size }.toDouble() / tagged.size
        } else 0.0
        
        val complexWords = tagged.flatMap { it.tags }
            .count { it.word.length > 6 }
        
        val totalWords = tagged.sumOf { it.tags.size }
        val complexityRatio = if (totalWords > 0) complexWords.toDouble() / totalWords else 0.0
        
        return (avgWordsPerSentence * 0.4) + (complexityRatio * 60)
    }
    
    private fun calculateReadability(totalWords: Int, totalSentences: Int): Double {
        if (totalSentences == 0 || totalWords == 0) return 0.0
        
        val avgWordsPerSentence = totalWords.toDouble() / totalSentences
        // Simplified Flesch-Kincaid approximation
        return 206.835 - (1.015 * avgWordsPerSentence) - (84.6 * 1.5) // Assume 1.5 syllables per word
    }
}

@Serializable
data class PatrickAnalysis(
    val totalWords: Int,
    val totalSentences: Int,
    val totalEntities: Int,
    val entityTypes: Map<EntityType, Int>,
    val posFrequency: Map<String, Int>,
    val keyTerms: Map<String, Int>,
    val complexity: Double,
    val readability: Double
)