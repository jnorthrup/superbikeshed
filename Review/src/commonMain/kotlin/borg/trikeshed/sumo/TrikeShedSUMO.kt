package borg.trikeshed.sumo

import borg.trikeshed.lib.Join
import borg.trikeshed.lib.Series
import borg.trikeshed.reactor.AsyncReaction
import borg.trikeshed.cursor.Cursor
import borg.trikeshed.lib.j
import kotlin.jvm.JvmInline

typealias ReactorEntity = Join<EntityId, Join<String, AsyncReaction>>
typealias CursorEntity = Join<EntityId, Join<String, Cursor>>
typealias BufferEntity = Join<EntityId, Join<String, Int>>
typealias SeriesEntity = Join<EntityId, Join<String, Series<*>>>

@JvmInline value class TrikeShedProcess private constructor(
    private val data: Join<ProcessId, Join<String, Join<Series<ReactorEntity>, Series<CursorEntity>>>>
) {
    val id: ProcessId get() = data.a
    val name: String get() = data.b.a
    val reactors: Series<ReactorEntity> get() = data.b.b.a
    val cursors: Series<CursorEntity> get() = data.b.b.b
    
    companion object {
        fun process(id: ProcessId, name: String, reactors: Series<ReactorEntity>, cursors: Series<CursorEntity>): TrikeShedProcess =
            TrikeShedProcess(id j (name j (reactors j cursors)))
    }
}

@JvmInline value class TrikeShedDataFlow private constructor(
    private val data: Join<EntityId, Join<SeriesEntity, Join<SeriesEntity, String>>>
) {
    val id: EntityId get() = data.a
    val source: SeriesEntity get() = data.b.a
    val sink: SeriesEntity get() = data.b.b.a
    val transformType: String get() = data.b.b.b
    
    companion object {
        fun dataFlow(id: EntityId, source: SeriesEntity, sink: SeriesEntity, transformType: String): TrikeShedDataFlow =
            TrikeShedDataFlow(id j (source j (sink j transformType)))
    }
}

interface TrikeShedSUMOMapper {
    suspend fun mapReactorToSUMO(reaction: AsyncReaction): SUMOConcept
    suspend fun mapCursorToSUMO(cursor: Cursor): SUMOConcept
    suspend fun mapSeriesToSUMO(series: Series<*>): SUMOConcept
    suspend fun mapProcessToSUMO(process: TrikeShedProcess): SUMOConcept
    suspend fun mapDataFlowToSUMO(dataFlow: TrikeShedDataFlow): SUMORelation
}

@JvmInline value class SUMOTrikeShedOntology private constructor(
    private val data: Join<SUMOOntology, Join<Series<TrikeShedProcess>, Series<TrikeShedDataFlow>>>
) {
    val baseOntology: SUMOOntology get() = data.a
    val processes: Series<TrikeShedProcess> get() = data.b.a
    val dataFlows: Series<TrikeShedDataFlow> get() = data.b.b
    
    companion object {
        fun ontology(base: SUMOOntology, processes: Series<TrikeShedProcess>, flows: Series<TrikeShedDataFlow>): SUMOTrikeShedOntology =
            SUMOTrikeShedOntology(base j (processes j flows))
    }
}