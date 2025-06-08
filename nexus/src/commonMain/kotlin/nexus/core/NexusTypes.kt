package nexus.core

import borg.trikeshed.core.*

// Refined and Placeholder Types for Nexus System

typealias EnvironmentContext = Series<Join<String, String>> // Series of Key-Value pairs
typealias Capability = Join<String, Series<String>> // Capability Name j Series of Parameters
typealias ProjectContext = Series<Join<String, String>> // Key-Value pairs for project context
typealias Problem = Series<String> // A series of text describing the problem aspects
typealias Solution = Series<String> // A proposed solution, e.g., lines of code or steps
typealias Feedback = Join<String, Series<String>> // FeedbackType j Series of Details/Parameters
typealias LearningUpdate = Join<String, String> // UpdateType j UpdateSummary
typealias Action = Join<String, Series<String>> // ActionName j Series of Arguments
typealias Outcome = Series<String> // Lines of output or a status message
typealias Workflow = Series<Action> // A sequence of actions to achieve a goal
typealias AgentConfiguration = Series<Join<String, String>> // Series of Key-Value configuration settings
typealias Request = Problem // Request is synonymous with a problem description
typealias Response = Series<String> // A series of text forming the response

typealias ScoredSuggestion = Join<Double, String> // Score j SuggestionString
typealias PredictedAction = Join<Action, Double> // Action j ConfidenceScore
typealias Change = Join<String, String> // ChangeType j ChangeDetail
typealias SessionId = String
typealias TimestampedContext = Join<Long, ProjectContext>
typealias CompleteNexus = String
typealias WeightedCapability = Join<Double, Capability>
typealias LearningInstance = String
typealias EvolutionStep = String
typealias RequestResponse = Join<Request, Response>
typealias ActionOutcome = Join<Action, Outcome>
typealias WorkflowPath = Series<Action>
typealias WorkflowOutcome = Join<WorkflowPath, Series<Outcome>>
typealias Score = Double
typealias Confidence = Double
typealias Pattern = Series<String> // A series of strings defining a pattern
typealias Environment = Any

// Types for Gossip Functionality
typealias GossipPayload = Series<Join<String, String>> // A series of key-value pairs for the gossip message content
// Topic is handled by the PubSub system directly, not part of this payload type definition.
// No separate GossipMessage typealias needed if the payload is what's published.
