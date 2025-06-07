package borg.trikeshed.sumo

import borg.trikeshed.lib.Join
import borg.trikeshed.lib.Series
import borg.trikeshed.lib.j
import kotlin.jvm.JvmInline

typealias EntityId = ULong
typealias ProcessId = ULong  
typealias RelationId = ULong
typealias PropertyId = ULong

typealias SUMOEntity = Join<EntityId, String>
typealias SUMOProcess = Join<ProcessId, Join<String, EntityId>>
typealias SUMORelation = Join<RelationId, Join<EntityId, EntityId>>
typealias SUMOProperty = Join<PropertyId, Join<EntityId, String>>

@JvmInline value class SUMOConcept private constructor(
    private val data: Join<EntityId, Join<String, Series<SUMOProperty>>>
) {
    val id: EntityId get() = data.a
    val name: String get() = data.b.a
    val properties: Series<SUMOProperty> get() = data.b.b
    
    companion object {
        fun concept(id: EntityId, name: String, properties: Series<SUMOProperty>): SUMOConcept =
            SUMOConcept(id j (name j properties))
    }
}

@JvmInline value class SUMOClass private constructor(
    private val data: Join<EntityId, Join<String, Series<SUMOConcept>>>
) {
    val id: EntityId get() = data.a
    val name: String get() = data.b.a
    val instances: Series<SUMOConcept> get() = data.b.b
    
    companion object {
        fun sumoClass(id: EntityId, name: String, instances: Series<SUMOConcept>): SUMOClass =
            SUMOClass(id j (name j instances))
    }
}

@JvmInline value class SUMOAxiom private constructor(
    private val data: Join<String, Join<Series<SUMOConcept>, Series<SUMORelation>>>
) {
    val formula: String get() = data.a
    val entities: Series<SUMOConcept> get() = data.b.a
    val relations: Series<SUMORelation> get() = data.b.b
    
    companion object {
        fun axiom(formula: String, entities: Series<SUMOConcept>, relations: Series<SUMORelation>): SUMOAxiom =
            SUMOAxiom(formula j (entities j relations))
    }
}