@file:JsExport // Export all top-level functions in this file
import kotlin.js.JsExport // Required for @JsExport annotation at top or on specific declarations
// kotlinx.serialization.Serializable should not be needed here unless new serializable types are defined IN THIS FILE.
// Types from types.kt are already serializable.

// Import necessary types from types.kt
// Assuming types.kt is in the same package or an accessible package.
// If types.kt defines a package, e.g., package com.example.types,
// then: import com.example.types.*
import borg.trikeshed.lib.*
// For now, assuming default package visibility or direct access.
// No explicit import statements are needed if types.kt is in the same Kotlin module and default package.

/**
 * Adds a new event to the task history.
 *
 * @param history The current task history (Series of TaskEvents).
 * @param event The new TaskEvent to add.
 * @return A new TaskHistory with the event added.
 */
// @JsExport // Not needed if @file:JsExport is used and this is a top-level function
fun addTaskEvent(history: TaskHistory, event: TaskEvent): TaskHistory {
    // Uses the 'add' method defined in the Series class
    return history.add(event)
}

/**
 * Processes a user request based on the current extension state.
 * This is a placeholder implementation demonstrating the structure
 * with TrikeShed-like operators.
 *
 * @param request The UserRequest containing the user's input.
 * @return A Join object containing the Response and the updated KotlinExtensionState.
 */
// @JsExport // Not needed if @file:JsExport is used
// However, extension functions on types that are themselves exported (@JsExport class KotlinExtensionState)
// might need special handling or might not be directly usable as JS class methods.
// For top-level export, it's better to make it a regular function if issues arise:
// fun processUserRequest(extensionState: KotlinExtensionState, request: UserRequest): Join<Response, KotlinExtensionState>
// Let's try exporting it as an extension function first as per the original structure.
// Note: Exporting extension functions can sometimes be tricky for JS interop depending on how they are compiled.
// It might appear as a static-like function in JS: processUserRequest(receiver, request)
fun KotlinExtensionState.processUserRequest(request: UserRequest): Join<Response, KotlinExtensionState> {
    // Destructure the current state (assuming Join has a split() method as defined in types.kt)
    val (currentSession, currentContext) = this.split()

    // 1. Simulate adding a UserRequest event to history
    val requestEvent = TaskEvent(
        ts = System.currentTimeMillis(),
        type = "ask",
        ask = ClineAskType.command,
        text = request.text
    )
    val updatedHistory = (currentSession.history ?: seriesOf()).add(requestEvent)

    var updatedSession = currentSession.copy(history = updatedHistory)

    // 2. Placeholder for generating multiple potential solutions
    val potentialSolutions = seriesOf("Solution attempt 1 for '${request.text}'", "Solution attempt 2 for '${request.text}'")
    val processedSolutions = potentialSolutions.alpha { solutionText ->
        solutionText // The "Processed: " prefix will be added once to the final response.
    }

    // 3. Placeholder for selecting the "best" solution.
    val bestSolutionText = processedSolutions.items.firstOrNull() ?: "No solution found."
    val bestResponse = Response(bestSolutionText)

    // 4. Simulate adding a Response event to history
    val responseEvent = TaskEvent(
        ts = System.currentTimeMillis(),
        type = "say",
        say = ClineSayType.completion_result,
        text = bestResponse.text
    )
    val finalHistory = updatedSession.history?.add(responseEvent) ?: seriesOf(responseEvent)
    updatedSession = updatedSession.copy(history = finalHistory)

    // 5. Create the new overall state.
    val newExtensionState = updatedSession join currentContext

    return bestResponse join newExtensionState
}

// ActiveSession is a data class, so .copy() is auto-generated.
// This explicit helper is not strictly necessary unless ActiveSession becomes non-data or needs custom copy logic.
// Keeping it for now as it was part of the previous step's output.
// Not exporting this helper to JS unless specifically needed.
internal fun ActiveSession.copy(
    currentTaskId: String? = this.currentTaskId,
    currentMode: ModeConfig? = this.currentMode,
    history: TaskHistory? = this.history
): ActiveSession {
    return ActiveSession(currentTaskId, currentMode, history)
}
