package borg.dgm.self_improvement

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Serializable
data class ArchiveEntry(
    val id: String,
    val parentId: String,
    val score: Double,
    val metadata: Map<String, String>,
    val timestamp: String = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
)

@Serializable
data class EvolutionConfig(
    val maxGenerations: Int = 80,
    val selfImproveSize: Int = 2,
    val selfImproveWorkers: Int = 2,
    val chooseMethod: String = "score_child_prop",
    val balanceWeightScore: Double = 1.0,
    val balanceWeightChildren: Double = 0.5,
    val updateArchiveMethod: String = "keep_all",
    val evalNoise: Double = 0.1
)

@Serializable
data class EvolutionState(
    val generation: Int,
    val archive: List<ArchiveEntry>
)

class Evolution(
    private val config: EvolutionConfig,
    private val outputDir: String = "./output_dgm"
) {
    private val archive = mutableListOf<ArchiveEntry>()
    private val selfImprovement = SelfImprovement(
        outputDir = outputDir,
        maxWorkers = config.selfImproveWorkers
    )

    init {
        File(outputDir).mkdirs()
    }

    fun runImprovementCycle() {
        // Choose self-improve attempts
        val selfImproveEntries = chooseSelfImproves()
        
        // Run self-improvement processes
        val selfImproveIds = runBlocking {
            selfImprovement.runImprovements(selfImproveEntries)
        }
        
        // Update archive with results
        updateArchive(selfImproveIds)
        
        // Save DGM state
        saveState()
    }

    private fun chooseSelfImproves(): List<Pair<String, String>> {
        val selection = Selection(
            archive = archive.toList(),
            balanceWeightScore = config.balanceWeightScore,
            balanceWeightChildren = config.balanceWeightChildren
        )

        return when (config.chooseMethod) {
            "random" -> selection.chooseRandom(config.selfImproveSize)
            "score_prop" -> selection.chooseScoreProportional(config.selfImproveSize)
            "score_child_prop" -> selection.chooseScoreChildProportional(config.selfImproveSize)
            "best" -> selection.chooseBest(config.selfImproveSize)
            "balanced_selection" -> selection.chooseBalanced(config.selfImproveSize)
            else -> throw IllegalArgumentException("Unknown choose method: ${config.chooseMethod}")
        }
    }

    private fun updateArchive(ids: List<String>) {
        val results = selfImprovement.getResults()
        
        ids.forEach { id ->
            results[id]?.let { result ->
                if (result.success) {
                    archive.add(
                        ArchiveEntry(
                            id = result.id,
                            parentId = result.parentId,
                            score = result.score,
                            metadata = result.metadata
                        )
                    )
                }
            }
        }

        // Apply archive update method
        when (config.updateArchiveMethod) {
            "keep_better" -> {
                // Keep only the best performing entries
                val bestEntries = archive.groupBy { it.parentId }
                    .mapValues { (_, entries) -> entries.maxByOrNull { it.score } }
                    .values
                    .filterNotNull()
                archive.clear()
                archive.addAll(bestEntries)
            }
            "keep_all" -> {
                // Keep all entries (already done above)
            }
            else -> throw IllegalArgumentException("Unknown archive update method: ${config.updateArchiveMethod}")
        }
    }

    private fun saveState() {
        val state = EvolutionState(
            generation = archive.size,
            archive = archive.toList()
        )
        
        File("$outputDir/dgm_metadata.jsonl").appendText(
            json.encodeToString(state) + "\n"
        )
    }

    fun shutdown() {
        selfImprovement.shutdown()
    }

    companion object {
        private val json = Json { 
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun loadFromDirectory(dir: String): Evolution {
            val configFile = File("$dir/config.json")
            val config = if (configFile.exists()) {
                json.decodeFromString<EvolutionConfig>(configFile.readText())
            } else {
                EvolutionConfig()
            }
            return Evolution(config, dir)
        }
    }
} 