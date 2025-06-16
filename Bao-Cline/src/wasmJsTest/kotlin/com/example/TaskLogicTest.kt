package com.example // Test package

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.js.JsExport // Not strictly needed in test file unless exporting from test too

// Assuming types and logic are in the `com.example` package.
import UserRequest
import Response
import TaskEvent
import TaskHistory
import KotlinExtensionState
import ActiveSession
import EnvironmentContext
import borg.trikeshed.lib.Series
import borg.trikeshed.lib.seriesOf
import borg.trikeshed.lib.size
import borg.trikeshed.lib.get
import addTaskEvent
// processUserRequest is an extension function, so it's imported with KotlinExtensionState
// or called directly on an instance if KotlinExtensionState is imported.

// Placeholder for ProviderConfig if needed in EnvironmentContext, assuming nullable for now
// import ProviderConfig

class TaskLogicTest {

    @Test
    fun testAddTaskEvent() {
        val initialHistory = seriesOf<TaskEvent>() // Initialize with an empty Series
        val event = TaskEvent(ts = 123L, type = "ask", text = "Hello")
        val newHistory = addTaskEvent(initialHistory, event)
        
        assertEquals(1, newHistory.size, "History should have one event after adding.")
        assertEquals(event, newHistory[0], "The event was not added correctly.")
    }

    @Test
    fun testProcessUserRequest() {
        // Initial state
        // For Join<A,B>(val first: A, val second: B)
        val initialSession = ActiveSession(currentTaskId = "task1", history = seriesOf())
        // Assuming ProviderConfig is not strictly needed or can be null for this placeholder test
        val initialContext = EnvironmentContext(workspaceRoot = "/test")
        val initialState = KotlinExtensionState(initialSession, initialContext)

        val request = UserRequest("Test request")

        // Call the extension function
        val resultJoin = initialState.processUserRequest(request)

        assertNotNull(resultJoin, "Result should not be null")
        assertNotNull(resultJoin.a, "Response part of Join should not be null")
        assertNotNull(resultJoin.b, "New state part of Join should not be null")

        // Example assertion on the response (depends on placeholder logic in TaskLogic.kt)
        // The fixed logic should return "Solution attempt 1 for 'Test request'"
        assertEquals("Solution attempt 1 for 'Test request'", resultJoin.a.text, "Response text is not as expected.")

        // Example assertion on the new state
        val newExtState = resultJoin.b
        assertNotNull(newExtState, "New KotlinExtensionState should not be null")

        // Check if history was updated (TaskLogic.kt adds 2 events: request and response)
        val newSession = newExtState.a
        assertNotNull(newSession.history, "History in new session should not be null")
        assertEquals(2, newSession.history!!.size, "New history should have two events.")
        assertEquals(request.text, newSession.history!![0].text, "First event should be the user request.")
        assertEquals(resultJoin.a.text, newSession.history!![1].text, "Second event should be the response.")
    }
}
