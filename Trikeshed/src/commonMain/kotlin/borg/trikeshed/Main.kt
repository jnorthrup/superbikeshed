package borg.trikeshed

import borg.trikeshed.ccek.*
import borg.trikeshed.rl.Environment as RLEnvironment
import borg.trikeshed.lib.*
import borg.trikeshed.net.http.*
import kotlinx.coroutines.runBlocking

/**
 * The Main Orchestrator.
 * Its only job is to "pump specificity" into the system by assembling
 * and injecting the correct CCEK context for any given request.
 */
object MainOrchestrator {

    // The handler is defined once. It's generic.
    // It blindly executes the rules and uses the payload from the CCEK.
    private val httpHandler: CcekHttpHandler = { request: HttpRequest, ccek: CcekContext ->
        val (control: Control, context: Context, environment: Environment, knowledge: Knowledge) = ccek

        println("Handler executing action '${environment.action}' with Execution ID '${control.executionId}'")

        // 1. Validate the payload using the provided rule.
        if (!knowledge.validator(environment.payload)) {
            throw IllegalArgumentException("Invalid payload for action: ${environment.action}")
        }

        // 2. Transform the payload using the series of rules.
        val rulesList = (0 until knowledge.rules.a).map { knowledge.rules.b(it) }
        val finalPayload = rulesList.fold(environment.payload) { current: Any, rule: TransformationRule ->
            // Apply transformation rule logic here
            current
        }

        // 3. Return a success response.
        HttpResponse(
            status = HttpStatusCode(200),
            reasonPhrase = HttpReasonPhrase("OK"),
            headers = (0 j { _: Int -> HttpHeaderName("Content-Type") j HttpHeaderValue("text/plain") }),
            body = "Action '${environment.action}' completed successfully.".encodeToByteArray()
        )
    }

    // The server instance would be configured with our generic handler
    // private val server = HttpServer(httpHandler)

    // The main entry point. This simulates receiving two different requests.
    suspend fun run() {
        println("=== ORCHESTRATOR STARTING ===")

        // --- SCENARIO 1: A request to process a Indexed of numbers ---
        val request1 = HttpRequest(
            method = HttpMethod.POST,
            path = HttpRequestPath("/process/series"),
            version = HttpVersion("HTTP/1.1"),
            headers = 0 j { _: Int -> HttpHeaderName("Content-Type") j HttpHeaderValue("text/plain") }
        )
        // Assemble the CCEK with Indexed-specific payload and rules.
        val seriesCcek = assembleCcekForIndexedProcessing(request1)
        // Would pump the specificity into the server
        // server.processRequest(request1, seriesCcek)

        println("\n" + "=".repeat(40) + "\n")

        // --- SCENARIO 2: A request to process a Cursor of data ---
        val request2 = HttpRequest(
            method = HttpMethod.POST,
            path = HttpRequestPath("/process/cursor"),
            version = HttpVersion("HTTP/1.1"),
            headers = 0 j { _: Int -> HttpHeaderName("Content-Type") j HttpHeaderValue("text/plain") }
        )
        // Assemble the CCEK with Cursor-specific payload and rules.
        val cursorCcek = assembleCcekForCursorProcessing(request2)
        // Would pump the specificity into the server
        // server.processRequest(request2, cursorCcek)

        println("=== ORCHESTRATOR FINISHED ===")
    }

    /**
     * Assembles a CCEK specifically for a Indexed processing task.
     */
    private fun assembleCcekForIndexedProcessing(request: HttpRequest): CcekContext {
        println("Orchestrator: Assembling CCEK for a SERIES operation.")
        return CcekContext(
            control = Control("exec_series_123"),
            context = Context(sessionId = "session_series_123"),
            environment = Environment(
                action = "DoubleAndSumIndexed",
                // THE PAYLOAD IS A SERIES
                payload = listOf(1, 2, 3, 4, 5)
            ),
            knowledge = Knowledge(
                // THE RULES ARE FOR SERIES
                rules = 0 j { _: Int -> TransformationRule("noop", RuleCondition.FieldEquals("", ""), TransformationAction.SetField("", "")) },
                constraints = 0 j { _: Int -> Constraint("noop", "No-op constraint", ConstraintValidation.FieldRequired("")) },
                validator = { payload -> payload is List<*> && payload.isNotEmpty() }
            )
        )
    }

    /**
     * Assembles a CCEK specifically for a Cursor processing task.
     */
    private fun assembleCcekForCursorProcessing(request: HttpRequest): CcekContext {
        // This function would build a real cursor from a database or file.
        // We'll mock it for this example.
        val mockCursor = emptyList<Any>()

        println("Orchestrator: Assembling CCEK for a CURSOR operation.")
        return CcekContext(
            control = Control("exec_cursor_456"),
            context = Context(sessionId = "session_series_123"),
            environment = Environment(
                action = "CountCursorRows",
                // THE PAYLOAD IS A CURSOR
                payload = mockCursor
            ),
            knowledge = Knowledge(
                // THE RULES ARE FOR CURSORS
                rules = 0 j { _: Int -> TransformationRule("noop", RuleCondition.FieldEquals("", ""), TransformationAction.SetField("", "")) },
                constraints = 0 j { _: Int -> Constraint("noop", "No-op constraint", ConstraintValidation.FieldRequired("")) },
                validator = { payload -> payload is List<*> }
            )
        )
    }
}

 