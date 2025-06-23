package ecs

fun main() {
    val numPlayers = 2
    val executives = List(numPlayers) { AIExecutive() }
    val maxTicks = 50
    // Track milestone progression for Gantt chart
    val milestoneLog = mutableListOf<Triple<Int, Int, TechMilestone>>() // (tick, playerId, milestone)

    var gameState = GameState(tick = 0, playerTech = (0 until numPlayers).associateWith { TechMilestone.GATHER_RESOURCES })

    repeat(maxTicks) { tick ->
        // Each AI executive decides moves for its player
        val allMoves = executives.flatMapIndexed { playerId, exec ->
            exec.decideMoves(gameState, playerId)
        }
        // (Stub) Apply moves to ECS/game state here
        // For demo, just log milestone for each player
        for (playerId in 0 until numPlayers) {
            val ai = executives[playerId]
            val milestone = ai.javaClass.getDeclaredField("playerMilestones").apply { isAccessible = true }
                .get(ai) as MutableMap<Int, TechMilestone>
            milestoneLog += Triple(tick, playerId, milestone[playerId] ?: TechMilestone.GATHER_RESOURCES)
        }
        gameState = gameState.copy(tick = tick + 1)
    }

    // Output milestone log for Gantt chart (Mermaid syntax)
    println("""\nmermaid\ngantt\n    title Tech Milestone Progression\n    dateFormat  tick\n""")
    for (playerId in 0 until numPlayers) {
        println("    section Player ${playerId + 1}")
        val milestones = milestoneLog.filter { it.second == playerId }
        var lastMilestone: TechMilestone? = null
        var startTick = 0
        for ((tick, _, milestone) in milestones) {
            if (milestone != lastMilestone) {
                if (lastMilestone != null) {
                    val duration = tick - startTick
                    println("    ${lastMilestone.name.replace('_', ' ')} :done, p${playerId}_${lastMilestone.name}, $startTick, $duration")
                }
                lastMilestone = milestone
                startTick = tick
            }
        }
        // Print last milestone
        val lastTick = milestones.lastOrNull()?.first ?: 0
        if (lastMilestone != null) {
            val duration = lastTick - startTick + 1
            println("    ${lastMilestone.name.replace('_', ' ')} :done, p${playerId}_${lastMilestone.name}, $startTick, $duration")
        }
    }
} 