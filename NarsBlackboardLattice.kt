@file:Suppress("FunctionName")

package borg.trikeshed.parse.narsese

import borg.trikeshed.lib.*

/**
 * NARS as a Blackboard Lattice Solver
 * Demonstrates how NARS implements blackboard architecture with lattice structures
 */
object NarsBlackboardLattice {
    
    // Truth value lattice: [frequency, confidence] with partial ordering
    data class TruthValue(val frequency: Double, val confidence: Double) {
        // Lattice join operation (choice between truth values)
        infix fun choice(other: TruthValue): TruthValue = 
            if (confidence > other.confidence) this else other
            
        // Lattice meet operation (revision of truth values)
        infix fun revision(other: TruthValue): TruthValue {
            val c1 = confidence
            val c2 = other.confidence
            val f1 = frequency
            val f2 = other.frequency
            
            val newConfidence = (c1 + c2) / (1 + c1 * c2)
            val newFrequency = (f1 * c1 + f2 * c2) / (c1 + c2)
            
            return TruthValue(newFrequency, newConfidence)
        }
        
        // Partial ordering for lattice
        fun lessOrEqual(other: TruthValue): Boolean = 
            confidence <= other.confidence && 
            kotlin.math.abs(frequency - other.frequency) <= (1 - confidence)
    }
    
    // Budget value lattice: [priority, durability] for resource management
    data class BudgetValue(val priority: Double, val durability: Double) {
        fun decay(): BudgetValue = BudgetValue(
            priority * durability,
            durability * 0.99 // decay rate
        )
        
        fun compete(other: BudgetValue): BudgetValue =
            if (priority > other.priority) this else other
    }
    
    // Term lattice node with inheritance relations
    sealed class Term {
        data class Atom(val name: String) : Term()
        data class Compound(val connector: String, val terms: List<Term>) : Term()
        data class Variable(val type: Char, val name: String) : Term()
        
        // Subsumption relation for term lattice
        fun subsumes(other: Term): Boolean = when {
            this == other -> true
            this is Compound && connector == "&&" -> terms.any { it.subsumes(other) }
            this is Compound && connector == "||" -> terms.all { it.subsumes(other) }
            else -> false
        }
    }
    
    // Statement with copula forming relational lattice
    data class Statement(
        val subject: Term,
        val copula: String,
        val predicate: Term,
        val truth: TruthValue = TruthValue(1.0, 0.9)
    ) {
        // Inheritance chain for lattice structure
        fun inheritsFrom(other: Statement): Boolean =
            copula == "-->" && other.copula == "-->" &&
            predicate == other.subject
    }
    
    // Task with temporal tense forming temporal lattice
    data class Task(
        val statement: Statement,
        val punctuation: Char, // . ! ? &
        val tense: String? = null, // :|: :/: :\\:
        val budget: BudgetValue = BudgetValue(0.5, 0.5)
    ) {
        // Temporal ordering for temporal lattice
        fun temporallyBefore(other: Task): Boolean = when {
            tense == ":\\:" && other.tense == ":|:" -> true // past before present
            tense == ":|:" && other.tense == ":/:" -> true // present before future
            tense == ":\\:" && other.tense == ":/:" -> true // past before future
            else -> false
        }
    }
    
    // Blackboard: shared knowledge base
    class Blackboard {
        internal val beliefs = mutableMapOf<Statement, TruthValue>()
        internal val goals = mutableMapOf<Statement, TruthValue>()
        internal val questions = mutableSetOf<Statement>()
        internal val temporal = mutableListOf<Task>()
        
        // Post belief to blackboard with lattice operations
        fun postBelief(statement: Statement, truth: TruthValue) {
            beliefs[statement] = beliefs[statement]?.revision(truth) ?: truth
            
            // Trigger constraint propagation through inheritance lattice
            propagateInheritance(statement, truth)
        }
        
        // Post goal to blackboard
        fun postGoal(statement: Statement, desire: TruthValue) {
            goals[statement] = goals[statement]?.choice(desire) ?: desire
            
            // Try to derive plans from beliefs
            derivePlans(statement)
        }
        
        // Query blackboard with lattice unification
        fun query(pattern: Statement): List<Join<Statement, TruthValue>> {
            return beliefs.mapNotNull { (stmt, truth) ->
                if (unify(pattern, stmt)) stmt j truth else null
            }
        }
        
        // Constraint propagation through inheritance lattice
        internal fun propagateInheritance(statement: Statement, truth: TruthValue) {
            // Forward inference: A→B, B→C ⊢ A→C (transitivity)
            beliefs.keys.forEach { existing ->
                if (statement.inheritsFrom(existing)) {
                    val derived = Statement(
                        statement.subject,
                        "-->", 
                        existing.predicate,
                        truth.revision(beliefs[existing]!!)
                    )
                    beliefs[derived] = derived.truth
                }
            }
            
            // Backward inference: A→B, A→C ⊢ B↔C (abduction)
            beliefs.keys.forEach { existing ->
                if (statement.subject == existing.subject && 
                    statement.copula == "-->" && existing.copula == "-->") {
                    val derived = Statement(
                        statement.predicate,
                        "<->",
                        existing.predicate,
                        TruthValue(0.5, truth.confidence * beliefs[existing]!!.confidence)
                    )
                    beliefs[derived] = derived.truth
                }
            }
        }
        
        // Plan derivation from goal-belief lattice intersection
        internal fun derivePlans(goal: Statement) {
            beliefs.keys.forEach { belief ->
                if (belief.predicate == goal.subject) {
                    // Found potential plan: belief enables goal
                    val plan = Statement(
                        belief.subject,
                        "=/>", // predictive implication
                        goal.predicate,
                        beliefs[belief]!!.revision(goals[goal]!!)
                    )
                    postBelief(plan, plan.truth)
                }
            }
        }
        
        // Simple unification for pattern matching
        internal fun unify(pattern: Statement, statement: Statement): Boolean {
            return (pattern.subject is Term.Variable || pattern.subject == statement.subject) &&
                   pattern.copula == statement.copula &&
                   (pattern.predicate is Term.Variable || pattern.predicate == statement.predicate)
        }
        
        // Lattice intersection for constraint solving
        fun solve(constraints: List<Statement>): List<Join<Term.Variable, Term>> {
            val solutions = mutableListOf<Join<Term.Variable, Term>>()
            
            // Find variable bindings that satisfy all constraints
            constraints.forEach { constraint ->
                query(constraint).forEach { (solution, truth) ->
                    if (truth.confidence > 0.5) {
                        extractBindings(constraint, solution).forEach { binding ->
                            solutions.add(binding)
                        }
                    }
                }
            }
            
            return solutions.distinctBy { it.a.name }
        }
        
        internal fun extractBindings(pattern: Statement, solution: Statement): List<Join<Term.Variable, Term>> {
            val bindings = mutableListOf<Join<Term.Variable, Term>>()
            
            if (pattern.subject is Term.Variable) {
                bindings.add(pattern.subject j solution.subject)
            }
            if (pattern.predicate is Term.Variable) {
                bindings.add(pattern.predicate j solution.predicate)
            }
            
            return bindings
        }
    }
    
    // Knowledge sources (inference rules) acting on blackboard
    object InferenceEngines {
        
        // Deduction engine: A→B, B→C ⊢ A→C
        fun deduction(blackboard: Blackboard) {
            // Implementation would scan blackboard for deduction opportunities
            // and post derived beliefs back to blackboard
        }
        
        // Induction engine: A→B, A→C ⊢ B→C (tentative)
        fun induction(blackboard: Blackboard) {
            // Implementation would find common subjects and induce relations
        }
        
        // Temporal reasoning engine
        fun temporalReasoning(blackboard: Blackboard) {
            // Implementation would process temporal sequences and predict
        }
        
        // Resource allocation engine
        fun resourceAllocation(blackboard: Blackboard) {
            // Implementation would manage budget values and attention
        }
    }
    
    // Example: Constraint satisfaction using NARS blackboard
    fun solveConstraints() {
        val blackboard = Blackboard()
        
        // Post knowledge to blackboard
        blackboard.postBelief(
            Statement(Term.Atom("bird"), "-->", Term.Atom("animal")),
            TruthValue(1.0, 0.9)
        )
        blackboard.postBelief(
            Statement(Term.Atom("robin"), "-->", Term.Atom("bird")),
            TruthValue(1.0, 0.9)
        )
        blackboard.postBelief(
            Statement(Term.Atom("animal"), "-->", Term.Atom("living")),
            TruthValue(1.0, 0.8)
        )
        
        // Query: What is living?
        val query = Statement(
            Term.Variable('?', "X"),
            "-->",
            Term.Atom("living")
        )
        
        val solutions = blackboard.query(query)
        println("Solutions: ${solutions.map { it.a }}")
        
        // Solve constraint satisfaction problem
        val constraints = listOf(
            Statement(Term.Variable('?', "X"), "-->", Term.Atom("bird")),
            Statement(Term.Variable('?', "X"), "-->", Term.Atom("small"))
        )
        
        val bindings = blackboard.solve(constraints)
        println("Variable bindings: ${bindings.map { "${it.a.name} = ${it.b}" }}")
    }
}

/**
 * Key advantages of NARS as blackboard lattice solver:
 * 
 * 1. **Natural Lattice Structures**:
 *    - Truth values form [0,1]×[0,1] lattice with revision/choice operations
 *    - Terms form subsumption lattice via inheritance relations
 *    - Time forms temporal lattice with past/present/future ordering
 * 
 * 2. **Constraint Propagation**:
 *    - Inference rules propagate constraints through term hierarchies
 *    - Truth values combine constraints via revision function
 *    - Budget values manage resource constraints
 * 
 * 3. **Non-Monotonic Reasoning**:
 *    - Can revise beliefs as new evidence arrives
 *    - Handles contradictory constraints gracefully
 *    - Truth values degrade over time (forgetting)
 * 
 * 4. **Resource-Bounded Search**:
 *    - Budget mechanism prevents exponential blowup
 *    - Priority guides search through solution space
 *    - Attention focuses on most relevant constraints
 * 
 * 5. **Uncertainty Quantification**:
 *    - Confidence values track solution quality
 *    - Can reason about uncertain constraints
 *    - Propagates uncertainty through inference chains
 */