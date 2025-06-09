package borg.trikeshed.sumo

import borg.trikeshed.lib.Join
import borg.trikeshed.lib.Series
import borg.trikeshed.lib.j
import kotlin.jvm.JvmInline

interface SUMOOntology {
    suspend fun findConcept(name: String): SUMOConcept?
    suspend fun findClass(name: String): SUMOClass?
    suspend fun getSubclasses(clazz: SUMOClass): Series<SUMOClass>
    suspend fun getInstances(clazz: SUMOClass): Series<SUMOConcept>
    suspend fun getProperties(entity: SUMOConcept): Series<SUMOProperty>
    suspend fun getRelations(entity: SUMOConcept): Series<SUMORelation>
    suspend fun evaluateAxiom(axiom: SUMOAxiom): Boolean
}

@JvmInline value class SUMOQuery private constructor(
    private val data: Join<String, Join<Series<String>, Series<String>>>
) {
    val queryType: String get() = data.a
    val conceptNames: Series<String> get() = data.b.a
    val relationNames: Series<String> get() = data.b.b
    
    companion object {
        fun query(type: String, concepts: Series<String>, relations: Series<String>): SUMOQuery =
            SUMOQuery(type j (concepts j relations))
    }
}

@JvmInline value class SUMOResult private constructor(
    private val data: Join<Boolean, Join<Series<SUMOConcept>, Series<SUMORelation>>>
) {
    val success: Boolean get() = data.a
    val concepts: Series<SUMOConcept> get() = data.b.a
    val relations: Series<SUMORelation> get() = data.b.b
    
    companion object {
        fun result(success: Boolean, concepts: Series<SUMOConcept>, relations: Series<SUMORelation>): SUMOResult =
            SUMOResult(success j (concepts j relations))
    }
}

interface SUMOQueryEngine {
    suspend fun execute(query: SUMOQuery): SUMOResult
    suspend fun findByType(conceptType: String): Series<SUMOConcept>
    suspend fun findByProperty(propertyName: String, value: String): Series<SUMOConcept>
    suspend fun findRelated(concept: SUMOConcept, relationType: String): Series<SUMOConcept>
}