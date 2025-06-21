package borg.trikeshed

import borg.trikeshed.ccek.*
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
    private val httpHandler: CcekHttpHandler = { request, ccek ->
        val (control, context, environment, knowledge) = ccek

        println("Handler executing action '${environment.action}' with Execution ID '${control.executionId}'")

        // 1. Validate the payload using the provided rule.
        if (!knowledge.validator(environment.payload)) {
            throw IllegalArgumentException("Invalid payload for action: ${environment.action}")
        }

        // 2. Transform the payload using the series of rules.
        val finalPayload = knowledge.rules.play.fold(environment.payload) { current, rule ->
            rule(current)
        }

        // 3. Return a success response.
        HttpResponse(
            status = HttpStatusCode(200),
            reasonPhrase = HttpReasonPhrase("OK"),
            headers = (0 j { _ -> HttpHeaderName("Content-Type") j HttpHeaderValue("text/plain") }).play.toList(),
            body = "Action '${environment.action}' completed successfully.".encodeToByteArray()
        )
    }

    // The server instance, configured with our generic handler.
    private val server = HttpServer(httpHandler)

    // The main entry point. This simulates receiving two different requests.
    suspend fun run() {
        println("=== ORCHESTRATOR STARTING ===")

        // --- SCENARIO 1: A request to process a Series of numbers ---
        val request1 = HttpRequest(
            method = HttpMethod("POST"),
            path = HttpRequestPath("/process/series"),
            version = HttpVersion("HTTP/1.1"),
            headers = emptyList()
        )
        // Assemble the CCEK with Series-specific payload and rules.
        val seriesCcek = assembleCcekForSeriesProcessing(request1)
        // Pump the specificity into the server.
        server.processRequest(request1, seriesCcek)

        println("\n" + "=".repeat(40) + "\n")

        // --- SCENARIO 2: A request to process a Cursor of data ---
        val request2 = HttpRequest(
            method = HttpMethod("POST"),
            path = HttpRequestPath("/process/cursor"),
            version = HttpVersion("HTTP/1.1"),
            headers = emptyList()
        )
        // Assemble the CCEK with Cursor-specific payload and rules.
        val cursorCcek = assembleCcekForCursorProcessing(request2)
        // Pump the specificity into the server.
        server.processRequest(request2, cursorCcek)

        println("=== ORCHESTRATOR FINISHED ===")
    }

    /**
     * Assembles a CCEK specifically for a Series processing task.
     */
    private fun assembleCcekForSeriesProcessing(request: HttpRequest): CcekContext {
        println("Orchestrator: Assembling CCEK for a SERIES operation.")
        return CcekContext(
            control = Control("exec_series_123"),
            context = Context(sourceIp = "127.0.0.1", securityToken = "token_valid"),
            environment = Environment(
                action = "DoubleAndSumSeries",
                // THE PAYLOAD IS A SERIES
                payload = (0 j { i -> listOf(1, 2, 3, 4, 5)[i] }).play.toList()
            ),
            knowledge = Knowledge(
                // THE RULES ARE FOR SERIES
                rules = (0 j { _ -> { payload: Any -> 
                    when (payload) {
                        is List<*> -> payload.map { (it as Int) * 2 }
                        else -> payload
                    }
                } }).play.toList(),
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
            context = Context(sourceIp = "127.0.0.1", securityToken = "token_valid"),
            environment = Environment(
                action = "CountCursorRows",
                // THE PAYLOAD IS A CURSOR
                payload = mockCursor
            ),
            knowledge = Knowledge(
                // THE RULES ARE FOR CURSORS
                rules = (0 j { _ -> { payload: Any -> 
                    when (payload) {
                        is List<*> -> payload.size
                        else -> 0
                    }
                } }).play.toList(),
                validator = { payload -> payload is List<*> }
            )
        )
    }
}

fun main() = runBlocking {
    MainOrchestrator.run()
} 