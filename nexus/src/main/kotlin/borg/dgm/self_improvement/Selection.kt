package borg.dgm.self_improvement

import kotlin.random.Random

class Selection(
    private val archive: List<ArchiveEntry>,
    private val balanceWeightScore: Double = 1.0,
    private val balanceWeightChildren: Double = 0.5
) {
    fun chooseRandom(size: Int): List<Pair<String, String>> {
        return archive.shuffled()
            .take(size)
            .map { it.id to it.parentId }
    }

    fun chooseScoreProportional(size: Int): List<Pair<String, String>> {
        val totalScore = archive.sumOf { it.score }
        val probabilities = archive.map { it.score / totalScore }
        
        return (0 until size).map {
            val random = Random.nextDouble()
            var cumulative = 0.0
            val selected = archive.zip(probabilities).find { (_, prob) ->
                cumulative += prob
                random <= cumulative
            }?.first ?: archive.last()
            selected.id to selected.parentId
        }
    }

    fun chooseScoreChildProportional(size: Int): List<Pair<String, String>> {
        val childCounts = archive.groupBy { it.parentId }
            .mapValues { it.value.size }
        
        val scores = archive.map { entry ->
            val childCount = childCounts[entry.id] ?: 0
            entry.score / (1 + childCount)
        }
        
        val totalScore = scores.sum()
        val probabilities = scores.map { it / totalScore }
        
        return (0 until size).map {
            val random = Random.nextDouble()
            var cumulative = 0.0
            val selected = archive.zip(probabilities).find { (_, prob) ->
                cumulative += prob
                random <= cumulative
            }?.first ?: archive.last()
            selected.id to selected.parentId
        }
    }

    fun chooseBest(size: Int): List<Pair<String, String>> {
        return archive.sortedByDescending { it.score }
            .take(size)
            .map { it.id to it.parentId }
    }

    fun chooseBalanced(size: Int): List<Pair<String, String>> {
        val childCounts = archive.groupBy { it.parentId }
            .mapValues { it.value.size }
        
        val scores = archive.map { entry ->
            val childCount = childCounts[entry.id] ?: 0
            balanceWeightScore * entry.score - balanceWeightChildren * childCount
        }
        
        val totalScore = scores.sum()
        val probabilities = scores.map { it / totalScore }
        
        return (0 until size).map {
            val random = Random.nextDouble()
            var cumulative = 0.0
            val selected = archive.zip(probabilities).find { (_, prob) ->
                cumulative += prob
                random <= cumulative
            }?.first ?: archive.last()
            selected.id to selected.parentId
        }
    }
} 