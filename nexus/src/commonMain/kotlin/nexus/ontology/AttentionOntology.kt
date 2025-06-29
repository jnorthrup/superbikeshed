package nexus.ontology

import borg.trikeshed.lib.*

/**
 * Attention Feature Ontology - Radiating outward from main()'s start
 * 
 * This typealias ontology defines how attention flows from main() through
 * the system's abstractions, creating a taxonomical structure for
 * attention distribution and causality chains.
 */

// === CORE ATTENTION TYPES ===

typealias AttentionVector = Join<AttentionDirection, AttentionMagnitude>
typealias AttentionDirection = String  // "agent-intelligence", "event-driven", "compositional", "meta"
typealias AttentionMagnitude = Int     // Percentage allocation (0-100)

typealias AttentionDistribution = Indexed<AttentionVector>
typealias AttentionFeedback = Join<AttentionVector, AttentionResult>
typealias AttentionResult = String    // Result of attention allocation

// === MAIN() ONTOLOGY ===

typealias MainIntention = String       // "Universal Development Autonomy through Architectural Artistry"
typealias MainPursuit = String         // "Pursuit of Happiness"
typealias MainRealization = Indexed<AbstractionLayer>

typealias AbstractionLayer = Join<LayerName, LayerAllocation>
typealias LayerName = String          // "Agent Intelligence", "Event-Driven Architecture", etc.
typealias LayerAllocation = Join<AttentionMagnitude, LayerCapability>
typealias LayerCapability = Indexed<String>  // List of capabilities

// === CAUSALITY CHAIN ONTOLOGY ===

typealias CausalityChain = Indexed<CausalLink>
typealias CausalLink = Join<Cause, Effect>
typealias Cause = String              // "main() starts", "attention distributed", etc.
typealias Effect = String             // "abstractions activated", "feedback generated", etc.

typealias ChainInitiator = String     // "main()" - the source of all causality
typealias ChainPropagation = Join<Cause, Indexed<Effect>>
typealias ChainConvergence = Join<Indexed<Effect>, FinalOutcome>
typealias FinalOutcome = String       // "Universal Development Autonomy achieved"

// === ATTENTION FLOW TYPES ===

typealias AttentionSource = String    // Always "main()"
typealias AttentionSink = Indexed<AbstractionLayer>
typealias AttentionFlow = Join<AttentionSource, AttentionSink>

typealias FlowRate = Double           // Attention flow rate (0.0 - 1.0)
typealias FlowDirection = String      // "outward", "feedback", "convergence"
typealias FlowState = Join<FlowRate, FlowDirection>

// === RADIATING ATTENTION PATTERNS ===

typealias RadiationPattern = Join<RadiationCenter, RadiationWaves>
typealias RadiationCenter = String    // "main()"
typealias RadiationWaves = Indexed<AttentionWave>

typealias AttentionWave = Join<WaveAmplitude, WaveTarget>
typealias WaveAmplitude = AttentionMagnitude
typealias WaveTarget = AbstractionLayer

// === ONTOLOGICAL MAPPINGS ===

typealias OntologyMapping = Join<ConceptName, ConceptDefinition>
typealias ConceptName = String
typealias ConceptDefinition = Join<TypeAlias, SemanticMeaning>
typealias TypeAlias = String          // The actual typealias name
typealias SemanticMeaning = String    // Human-readable description

typealias OntologyGraph = Indexed<OntologyMapping>
typealias OntologyPath = Indexed<ConceptName>  // Path through ontology

// === ATTENTION TAXONOMY ===

typealias AttentionTaxonomy = Join<TaxonomyLevel, TaxonomyChildren>
typealias TaxonomyLevel = String      // "attention", "distribution", "feedback", etc.
typealias TaxonomyChildren = Indexed<ConceptName>

typealias TaxonomicStructure = Indexed<AttentionTaxonomy>
typealias TaxonomicPath = Indexed<TaxonomyLevel>

// === RADIATING SEMANTICS ===

/**
 * Create the core attention ontology that radiates from main()
 */
fun createAttentionOntology(): OntologyGraph {
    return 12 j { i ->
        when (i) {
            0 -> "AttentionVector" j ("Join<Direction, Magnitude>" j "Basic unit of attention allocation")
            1 -> "MainIntention" j ("String" j "The original purpose driving main()")
            2 -> "CausalityChain" j ("Indexed<CausalLink>" j "Chain of cause-effect relationships")
            3 -> "AttentionFlow" j ("Join<Source, Sink>" j "Flow of attention from main() outward")
            4 -> "RadiationPattern" j ("Join<Center, Waves>" j "How attention radiates from center")
            5 -> "AbstractionLayer" j ("Join<Name, Allocation>" j "System abstraction receiving attention")
            6 -> "AttentionFeedback" j ("Join<Vector, Result>" j "Feedback from attention allocation")
            7 -> "ChainInitiator" j ("String" j "The starting point of causality (main)")
            8 -> "AttentionTaxonomy" j ("Join<Level, Children>" j "Hierarchical attention structure")
            9 -> "OntologyMapping" j ("Join<Name, Definition>" j "Mapping between concepts")
            10 -> "FlowState" j ("Join<Rate, Direction>" j "State of attention flow")
            11 -> "FinalOutcome" j ("String" j "Ultimate result of attention distribution")
            else -> "Unknown" j ("Unknown" j "Unknown concept")
        }
    }
}

/**
 * Create the standard attention distribution that radiates from main()
 */
fun createStandardAttentionDistribution(): AttentionDistribution {
    return 4 j { i ->
        when (i) {
            0 -> "agent-intelligence" j 40  // 40% to Agent Intelligence Layer
            1 -> "event-driven" j 30        // 30% to Event-Driven Architecture  
            2 -> "compositional" j 20       // 20% to Compositional Foundation
            3 -> "meta-development" j 10    // 10% to Meta-Development
            else -> "unknown" j 0
        }
    }
}

/**
 * Create the causality chain that flows from main()'s start
 */
fun createCausalityChain(): CausalityChain {
    return 6 j { i ->
        when (i) {
            0 -> "main() starts" j "intention declared"
            1 -> "intention declared" j "attention distribution begins"
            2 -> "attention distribution begins" j "abstractions receive allocation"
            3 -> "abstractions receive allocation" j "autonomous operation starts"
            4 -> "autonomous operation starts" j "feedback generated"
            5 -> "feedback generated" j "convergence achieved"
            else -> "unknown cause" j "unknown effect"
        }
    }
}