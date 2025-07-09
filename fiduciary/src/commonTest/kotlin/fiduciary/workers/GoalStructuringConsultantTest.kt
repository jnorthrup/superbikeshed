package fiduciary.workers

import fiduciary.agents.FiduciaryAgentSystem
import borg.trikeshed.dht.kademlia.id.NUID
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.days

class GoalStructuringConsultantTest {

    private val mockAgentSystem = object : FiduciaryAgentSystem(NUID("mockAgentSystem")) {}
    private val consultant = GoalStructuringConsultant(NUID("testConsultant"), mockAgentSystem)

    @Test
    fun testDecomposeSimpleGoal() = runTest {
        val simpleGoal = Goal(
            id = "G1",
            title = "Complete Task A",
            description = "A simple task",
            type = GoalStructuringConsultant.GoalType.PERFORMANCE,
            priority = GoalStructuringConsultant.GoalPriority.LOW,
            complexity = GoalStructuringConsultant.GoalComplexity.SIMPLE,
            targetValue = 100.0,
            unit = "%",
            deadline = Clock.System.now().plus(10.days)
        )
        val request = GoalStructuringConsultant.GoalStructuringRequest(
            requestId = "R1",
            primaryGoal = simpleGoal,
            capabilities = setOf(GoalStructuringConsultant.GoalStructuringCapability.GOAL_DECOMPOSITION),
            constraints = emptySet(),
            timeframe = 10.days
        )

        val result = consultant.structureGoals(request)
        assertEquals(1, result.decomposedGoals.size)
        assertEquals(simpleGoal.id, result.decomposedGoals[0].id)
    }

    @Test
    fun testDecomposeModeratePerformanceGoal() = runTest {
        val moderateGoal = Goal(
            id = "G2",
            title = "Improve Performance",
            description = "Moderate performance improvement",
            type = GoalStructuringConsultant.GoalType.PERFORMANCE,
            priority = GoalStructuringConsultant.GoalPriority.MEDIUM,
            complexity = GoalStructuringConsultant.GoalComplexity.MODERATE,
            targetValue = 100.0,
            unit = "%",
            deadline = Clock.System.now().plus(30.days)
        )
        val request = GoalStructuringConsultant.GoalStructuringRequest(
            requestId = "R2",
            primaryGoal = moderateGoal,
            capabilities = setOf(GoalStructuringConsultant.GoalStructuringCapability.GOAL_DECOMPOSITION),
            constraints = emptySet(),
            timeframe = 30.days
        )

        val result = consultant.structureGoals(request)
        assertEquals(2, result.decomposedGoals.size)
        assertNotNull(result.decomposedGoals.find { it.id == "G2_analysis" })
        assertNotNull(result.decomposedGoals.find { it.id == "G2_optimization" })
    }

    @Test
    fun testAnalyzePriorities() = runTest {
        val highPriorityGoal1 = Goal(
            id = "HP1", title = "High Priority 1", description = "", type = GoalStructuringConsultant.GoalType.PERFORMANCE,
            priority = GoalStructuringConsultant.GoalPriority.HIGH, complexity = GoalStructuringConsultant.GoalComplexity.SIMPLE,
            targetValue = 0.0, unit = "", deadline = null
        )
        val highPriorityGoal2 = highPriorityGoal1.copy(id = "HP2")
        val highPriorityGoal3 = highPriorityGoal1.copy(id = "HP3")
        val highPriorityGoal4 = highPriorityGoal1.copy(id = "HP4")

        val request = GoalStructuringConsultant.GoalStructuringRequest(
            requestId = "R3",
            primaryGoal = highPriorityGoal1, // Primary goal doesn't matter for this test
            capabilities = emptySet(),
            constraints = emptySet(),
            timeframe = 1.days
        )

        // Directly call the internal analyzer for testing purposes
        val analyzer = consultant.StructureAnalyzer()
        val recommendations = analyzer.analyzePriorities(listOf(highPriorityGoal1, highPriorityGoal2, highPriorityGoal3, highPriorityGoal4))

        assertEquals(1, recommendations.size)
        assertEquals(GoalStructuringConsultant.RecommendationType.PRIORITY_ADJUSTMENT, recommendations[0].type)
    }

    @Test
    fun testCircularDependencyDetection() = runTest {
        val goalA = Goal(
            id = "A", title = "Goal A", description = "", type = GoalStructuringConsultant.GoalType.PERFORMANCE,
            priority = GoalStructuringConsultant.GoalPriority.MEDIUM, complexity = GoalStructuringConsultant.GoalComplexity.SIMPLE,
            targetValue = 0.0, unit = "", deadline = null, dependencies = setOf("B")
        )
        val goalB = Goal(
            id = "B", title = "Goal B", description = "", type = GoalStructuringConsultant.GoalType.PERFORMANCE,
            priority = GoalStructuringConsultant.GoalPriority.MEDIUM, complexity = GoalStructuringConsultant.GoalComplexity.SIMPLE,
            targetValue = 0.0, unit = "", deadline = null, dependencies = setOf("A")
        )

        val analyzer = consultant.StructureAnalyzer()
        val recommendations = analyzer.analyzeDependencies(listOf(goalA, goalB))

        assertEquals(1, recommendations.size)
        assertEquals(GoalStructuringConsultant.RecommendationType.DEPENDENCY_OPTIMIZATION, recommendations[0].type)
        assert(recommendations[0].description.contains("Circular dependency detected"))
    }

    @Test
    fun testProgressTracking() = runTest {
        val goal = Goal(
            id = "P1", title = "Progress Goal", description = "", type = GoalStructuringConsultant.GoalType.PERFORMANCE,
            priority = GoalStructuringConsultant.GoalPriority.MEDIUM, complexity = GoalStructuringConsultant.GoalComplexity.SIMPLE,
            targetValue = 100.0, unit = "%", deadline = null, currentValue = 50.0
        )
        val tracker = consultant.ProgressTracker()
        val progress = tracker.trackProgress(listOf(goal))
        assertEquals(0.5, progress["P1"])
    }

    @Test
    fun testProgressTrackingWithSubgoals() = runTest {
        val subgoal1 = Goal(
            id = "S1", title = "Subgoal 1", description = "", type = GoalStructuringConsultant.GoalType.PERFORMANCE,
            priority = GoalStructuringConsultant.GoalPriority.LOW, complexity = GoalStructuringConsultant.GoalComplexity.SIMPLE,
            targetValue = 100.0, unit = "%", deadline = null, currentValue = 50.0
        )
        val subgoal2 = Goal(
            id = "S2", title = "Subgoal 2", description = "", type = GoalStructuringConsultant.GoalType.PERFORMANCE,
            priority = GoalStructuringConsultant.GoalPriority.LOW, complexity = GoalStructuringConsultant.GoalComplexity.SIMPLE,
            targetValue = 100.0, unit = "%", deadline = null, currentValue = 100.0
        )
        val parentGoal = Goal(
            id = "P2", title = "Parent Goal", description = "", type = GoalStructuringConsultant.GoalType.PERFORMANCE,
            priority = GoalStructuringConsultant.GoalPriority.MEDIUM, complexity = GoalStructuringConsultant.GoalComplexity.MODERATE,
            targetValue = 100.0, unit = "%", deadline = null, currentValue = 20.0,
            subgoals = mutableSetOf(subgoal1, subgoal2)
        )

        val tracker = consultant.ProgressTracker()
        val progress = tracker.trackProgress(listOf(parentGoal))
        // Expected: (parent current 20% * 0.3) + (subgoal avg (50+100)/2 = 75% * 0.7) = 6.0 + 52.5 = 58.5
        assertEquals(0.585, progress["P2"], 0.001)
    }
}
