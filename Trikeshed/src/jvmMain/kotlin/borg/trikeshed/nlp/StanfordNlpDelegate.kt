package borg.trikeshed.nlp

import edu.stanford.nlp.pipeline.*
import edu.stanford.nlp.ling.CoreLabel
import edu.stanford.nlp.trees.Tree
import edu.stanford.nlp.semgraph.SemanticGraph
import java.util.*

// Raw NLP data structures for delegate access
data class TokenData(
    val word: String,
    val lemma: String,
    val pos: String,
    val ner: String,
    val index: Int,
    val beginPosition: Int,
    val endPosition: Int
)

data class EntityMention(
    val text: String,
    val type: String,
    val startIndex: Int,
    val endIndex: Int,
    val confidence: Double
)

data class DependencyRelation(
    val governor: String,
    val dependent: String,
    val relation: String,
    val governorIndex: Int,
    val dependentIndex: Int
)

data class CorefChain(
    val representative: String,
    val mentions: List<String>,
    val confidence: Double
)

data class SentenceData(
    val text: String,
    val tokens: List<TokenData>,
    val entities: List<EntityMention>,
    val dependencies: List<DependencyRelation>,
    val parseTree: String? = null
)

data class DocumentData(
    val sentences: List<SentenceData>,
    val corefChains: List<CorefChain>,
    val openieTriples: List<RelationTriple>
)

// Delegate interface for custom processing
interface StanfordNlpDelegate {
    fun processDocument(doc: CoreDocument): DocumentData
    fun processSentence(sent: CoreSentence): SentenceData
    fun extractEntities(sent: CoreSentence): List<EntityMention>
    fun extractDependencies(sent: CoreSentence): List<DependencyRelation>
    fun extractCorefChains(doc: CoreDocument): List<CorefChain>
}

// Default implementation with raw access
object DefaultStanfordNlpDelegate : StanfordNlpDelegate {
    
    private val pipeline: StanfordCoreNLP by lazy {
        val props = Properties()
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,depparse,coref,openie")
        props.setProperty("openie.resolve_coref", "true")
        StanfordCoreNLP(props)
    }

    override fun processDocument(doc: CoreDocument): DocumentData {
        pipeline.annotate(doc)
        
        val sentences = doc.sentences().map { processSentence(it) }
        val corefChains = extractCorefChains(doc)
        val openieTriples = extractOpenieTriples(doc)
        
        return DocumentData(sentences, corefChains, openieTriples)
    }

    override fun processSentence(sent: CoreSentence): SentenceData {
        val tokens = sent.tokens().map { token ->
            TokenData(
                word = token.word(),
                lemma = token.lemma(),
                pos = token.tag(),
                ner = token.ner(),
                index = token.index(),
                beginPosition = token.beginPosition(),
                endPosition = token.endPosition()
            )
        }
        
        val entities = extractEntities(sent)
        val dependencies = extractDependencies(sent)
        val parseTree = sent.constituencyParse()?.toString()
        
        return SentenceData(
            text = sent.text(),
            tokens = tokens,
            entities = entities,
            dependencies = dependencies,
            parseTree = parseTree
        )
    }

    override fun extractEntities(sent: CoreSentence): List<EntityMention> {
        val entities = mutableListOf<EntityMention>()
        val tokens = sent.tokens()
        
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            if (token.ner() != "O") {
                val entityType = token.ner()
                val startIndex = i
                val entityText = mutableListOf<String>()
                
                // Collect consecutive tokens with same NER type
                while (i < tokens.size && tokens[i].ner() == entityType) {
                    entityText.add(tokens[i].word())
                    i++
                }
                
                entities.add(EntityMention(
                    text = entityText.joinToString(" "),
                    type = entityType,
                    startIndex = startIndex,
                    endIndex = i - 1,
                    confidence = 1.0 // Stanford doesn't provide confidence for NER
                ))
            } else {
                i++
            }
        }
        
        return entities
    }

    override fun extractDependencies(sent: CoreSentence): List<DependencyRelation> {
        val deps = mutableListOf<DependencyRelation>()
        val depParse = sent.dependencyParse()
        
        for (edge in depParse.edgeListSorted()) {
            deps.add(DependencyRelation(
                governor = edge.governor().word(),
                dependent = edge.dependent().word(),
                relation = edge.relation().toString(),
                governorIndex = edge.governor().index(),
                dependentIndex = edge.dependent().index()
            ))
        }
        
        return deps
    }

    override fun extractCorefChains(doc: CoreDocument): List<CorefChain> {
        val chains = mutableListOf<CorefChain>()
        val corefMap = doc.coref()
        
        for (chain in corefMap.values) {
            val representative = chain.representativeMention().spanToString()
            val mentions = chain.mentionsInTextualOrder().map { it.spanToString() }
            
            chains.add(CorefChain(
                representative = representative,
                mentions = mentions,
                confidence = 1.0 // Stanford doesn't provide confidence for coref
            ))
        }
        
        return chains
    }

    private fun extractOpenieTriples(doc: CoreDocument): List<RelationTriple> {
        val triples = mutableListOf<RelationTriple>()
        
        for (sent in doc.sentences()) {
            for (triple in sent.openieTriples()) {
                triples.add(RelationTriple(
                    subject = triple.subjectLemma().get(),
                    relation = triple.relationLemma().get(),
                    obj = triple.objectLemma().get(),
                    confidence = triple.confidence()
                ))
            }
        }
        
        return triples
    }
}

// Convenience function for easy access
fun processTextWithDelegate(text: String, delegate: StanfordNlpDelegate = DefaultStanfordNlpDelegate): DocumentData {
    val doc = CoreDocument(text)
    return delegate.processDocument(doc)
} 