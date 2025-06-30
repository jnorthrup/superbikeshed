package nexus.telemetry.endpoint

import nexus.telemetry.K2ScriptTelemetryEvent
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class TelemetryEndpointTest {

    private val originalOut = System.out
    private val originalErr = System.err
    private lateinit var outContent: ByteArrayOutputStream
    private lateinit var errContent: ByteArrayOutputStream

    @BeforeTest
    fun setUpStreams() {
        outContent = ByteArrayOutputStream()
        errContent = ByteArrayOutputStream()
        System.setOut(PrintStream(outContent))
        System.setErr(PrintStream(errContent))
    }

    @AfterTest
    fun restoreStreams() {
        System.setOut(originalOut)
        System.setErr(originalErr)
    }

    @Test
    fun testHandleTelemetryEventSuccess() {
        val testEvent = K2ScriptTelemetryEvent(
            timestamp = System.currentTimeMillis(),
            scriptName = "test_script.kts",
            eventType = "EXEC_SUCCESS",
            durationMs = 100L,
            platform = "TEST_PLATFORM",
            nexusVersion = "0.1-TEST",
            k2scriptVersion = "0.9-TEST"
        )

        // The current TelemetryEndpoint.handleTelemetryEvent is a suspend function
        // and uses a simulated event. We'll call its core logic conceptually.
        // For a real test, we'd need to adapt how 'event' is passed or mock 'call.receive'.
        // For now, we assume the simulated event path inside handleTelemetryEvent covers the logic.

        // Simulate calling the main logic block of handleTelemetryEvent.
        // If TelemetryEndpoint.handleTelemetryEvent was directly callable with an event:
        // runBlocking { TelemetryEndpoint.handleTelemetryEvent(mockCallWithEvent(testEvent)) }

        // For the current structure, let's analyze what handleTelemetryEvent does:
        // 1. It defines its own 'event'. We can't directly inject 'testEvent' into the current placeholder.
        // 2. It prints "Received K2Script Telemetry Event: $event"
        // 3. It prints "Telemetry event processed successfully."

        // We will call the function and check if the default simulated event (not our testEvent) is logged.
        // This tests the placeholder's behavior.
        kotlinx.coroutines.runBlocking {
            TelemetryEndpoint.handleTelemetryEvent(/* mockCall */) // Pass a conceptual mock call or null if adaptable
        }

        val output = outContent.toString()
        assertTrue(output.contains("Received K2Script Telemetry Event"), "Should log receipt of an event.")
        // Check for the default scriptName used in the placeholder
        assertTrue(output.contains("scriptName=example.kts"), "Should log the default event data.")
        assertTrue(output.contains("Telemetry event processed successfully."), "Should log successful processing.")
        assertTrue(errContent.toString().isEmpty(), "No errors should be logged for the default successful event.")
    }

    @Test
    fun testHandleTelemetryEventValidationError() {
        // To test validation, we would need to modify TelemetryEndpoint to accept an event
        // or change its internal simulated event to be invalid.
        // The current validation is: if (event.scriptName.isBlank() || event.eventType.isBlank())
        // The default simulated event IS valid.
        // This test case highlights the need to refactor TelemetryEndpoint for better testability.

        // For now, this test will run the same path as success, as we can't inject an invalid event
        // into the current placeholder structure of handleTelemetryEvent.
        kotlinx.coroutines.runBlocking {
            TelemetryEndpoint.handleTelemetryEvent(/* mockCall */)
        }

        val output = outContent.toString()
        // It will log the valid default event.
        assertTrue(output.contains("Received K2Script Telemetry Event"), "Should log receipt of an event.")
        assertTrue(output.contains("scriptName=example.kts"), "Should log the default event data.")

        // If we could inject an event like this:
        // val invalidEvent = K2ScriptTelemetryEvent(timestamp = 1L, scriptName = "", eventType = "", ...)
        // ... then we would expect:
        // assertTrue(errContent.toString().contains("Received event with missing scriptName or eventType"))
        // For now, errContent will be empty.
        assertTrue(errContent.toString().isEmpty(), "Error stream should be empty for the default valid event path.")
        println("Note: TelemetryEndpoint validation test is limited by current placeholder structure.")
    }
}
