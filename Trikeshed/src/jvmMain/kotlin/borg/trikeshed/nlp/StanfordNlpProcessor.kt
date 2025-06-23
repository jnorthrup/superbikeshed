package borg.trikeshed.nlp

import edu.stanford.nlp.pipeline.*
import java.util.*

data class RelationTriple(
    val subject: String,
    val relation: String,
    val obj: String,
    val confidence: Double
)

object StanfordNlpProcessor {

    private val pipeline: StanfordCoreNLP by lazy {
        val props = Properties()
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,depparse,coref,openie")
        props.setProperty("openie.resolve_coref", "true")
        StanfordCoreNLP(props)
    }

    fun extractRelations(text: String): List<RelationTriple> {
        val doc = CoreDocument(text)
        pipeline.annotate(doc)

        val triples = mutableListOf<RelationTriple>()
        for (sent in doc.sentences()) {
            for (triple in sent.openieTriples()) {
                triples.add(
                    RelationTriple(
                        subject = triple.subjectLemma().get(),
                        relation = triple.relationLemma().get(),
                        obj = triple.objectLemma().get(),
                        confidence = triple.confidence()
                    )
                )
            }
        }
        return triples
    }
} 